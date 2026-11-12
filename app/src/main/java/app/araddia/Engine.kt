package app.araddia

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class Turn(val role: String, val text: String) // role: "user" | "model"

interface Engine {
    val loaded: Boolean
    val budget: Int // caracteres de histórico enviados
    fun generate(system: String, turns: List<Turn>): Flow<String> // emite deltas
    fun close() {}
}

/** Corta o histórico por orçamento de caracteres (não por nº de falas) e garante que começa em "user". */
fun trimTurns(turns: List<Turn>, budget: Int): List<Turn> {
    val out = ArrayDeque<Turn>(); var used = 0
    for (t in turns.asReversed()) {
        if (out.isEmpty() && t.text.length > budget) { out.addFirst(t.copy(text = t.text.take(budget))); break }
        if (used + t.text.length > budget) break
        used += t.text.length; out.addFirst(t)
    }
    while (out.isNotEmpty() && out.first().role != "user") out.removeFirst()
    return out.toList()
}

/** Motor local via MediaPipe (.litertlm/.task). template: "gemma" ou "chatml" (Qwen e similares). */
class LocalEngine(private val ctx: Context, private val modelPath: String, var template: String = "gemma") : Engine {
    private var llm: LlmInference? = null
    override val loaded get() = llm != null
    override val budget = 5500

    private fun create(backend: LlmInference.Backend) = LlmInference.createFromOptions(
        ctx,
        LlmInference.LlmInferenceOptions.builder().setModelPath(modelPath)
            .setMaxTokens(4096).setPreferredBackend(backend).build()
    )

    private fun load(): LlmInference = llm ?: (try { create(LlmInference.Backend.GPU) } catch (_: Throwable) { create(LlmInference.Backend.CPU) }).also { llm = it }

    private fun prompt(system: String, turns: List<Turn>): String {
        val sb = StringBuilder()
        if (template == "chatml") {
            sb.append("<|im_start|>system\n$system<|im_end|>\n")
            turns.forEach { sb.append("<|im_start|>${if (it.role == "user") "user" else "assistant"}\n${it.text}<|im_end|>\n") }
            sb.append("<|im_start|>assistant\n")
        } else {
            turns.forEachIndexed { i, x ->
                val role = if (x.role == "user") "user" else "model"
                val body = if (i == 0) "$system\n\n---\n${x.text}" else x.text
                sb.append("<start_of_turn>$role\n$body<end_of_turn>\n")
            }
            sb.append("<start_of_turn>model\n")
        }
        return sb.toString()
    }

    override fun generate(system: String, turns: List<Turn>): Flow<String> = callbackFlow {
        val session = LlmInferenceSession.createFromOptions(
            load(),
            LlmInferenceSession.LlmInferenceSessionOptions.builder().setTopK(40).setTopP(0.92f).setTemperature(0.85f).build()
        )
        session.addQueryChunk(prompt(system, trimTurns(turns, budget)))
        session.generateResponseAsync { part, done -> trySend(part); if (done) close() }
        awaitClose {
            try { session.cancelGenerateResponseAsync() } catch (_: Throwable) {}
            try { session.close() } catch (_: Throwable) {}
        }
    }.flowOn(Dispatchers.Default)

    override fun close() { try { llm?.close() } catch (_: Throwable) {}; llm = null }
}

/** API compatível com OpenAI (OpenRouter, Groq, Gemini-OpenAI etc.). */
class RemoteEngine(private val base: String, private val key: String, private val model: String) : Engine {
    override val loaded = true
    override val budget = 24000
    override fun generate(system: String, turns: List<Turn>): Flow<String> = flow {
        val msgs = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        trimTurns(turns, budget).forEach {
            msgs.put(JSONObject().put("role", if (it.role == "user") "user" else "assistant").put("content", it.text))
        }
        val body = JSONObject().put("model", model).put("messages", msgs)
            .put("stream", true).put("temperature", 0.85).put("max_tokens", 1800)
        val c = URL(base.trimEnd('/') + "/chat/completions").openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true
        c.connectTimeout = 15000; c.readTimeout = 90000
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("Authorization", "Bearer $key")
        c.outputStream.use { it.write(body.toString().toByteArray()) }
        if (c.responseCode !in 200..299) {
            error("HTTP ${c.responseCode} ${c.errorStream?.bufferedReader()?.readText()?.take(160) ?: ""}")
        }
        c.inputStream.bufferedReader().useLines { lines ->
            for (l in lines) {
                if (!l.startsWith("data:")) continue
                val d = l.removePrefix("data:").trim()
                if (d == "[DONE]") break
                val delta = JSONObject(d).optJSONArray("choices")?.optJSONObject(0)
                    ?.optJSONObject("delta")?.optString("content", "") ?: ""
                if (delta.isNotEmpty()) emit(delta)
            }
        }
    }.flowOn(Dispatchers.IO)
}

/** Híbrido: tenta a API; se falhar antes de qualquer texto, cai para o motor local. */
class FallbackEngine(private val a: Engine, private val b: Engine?) : Engine {
    override val loaded get() = a.loaded
    override val budget get() = a.budget
    override fun generate(system: String, turns: List<Turn>): Flow<String> = flow {
        var got = false
        try {
            a.generate(system, turns).collect { got = true; emit(it) }
        } catch (e: Throwable) {
            if (got || b == null || e is CancellationException) throw e
            b.generate(system, turns).collect { emit(it) }
        }
    }
}

object Downloader {
    /** Baixa com retomada. Emite progresso 0..100. */
    fun download(url: String, dest: File, token: String): Flow<Int> = flow {
        val part = File(dest.path + ".part")
        var cur = url
        var conn: HttpURLConnection? = null
        val host = URL(url).host
        for (i in 0 until 6) {
            val c = URL(cur).openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 20000; c.readTimeout = 60000
            if (token.isNotBlank() && URL(cur).host == host) c.setRequestProperty("Authorization", "Bearer $token")
            if (part.exists()) c.setRequestProperty("Range", "bytes=${part.length()}-")
            if (c.responseCode in 300..399) { cur = URL(URL(cur), c.getHeaderField("Location")).toString(); continue }
            conn = c; break
        }
        val c = conn ?: error("Redirecionamento demais")
        if (c.responseCode == 401 || c.responseCode == 403) error("Acesso negado (${c.responseCode}). Aceite a licença do modelo no Hugging Face e informe seu token.")
        if (c.responseCode !in 200..299) error("HTTP ${c.responseCode}")
        val resumed = c.responseCode == 206
        val start = if (resumed) part.length() else 0L
        val total = start + c.contentLengthLong
        java.io.FileOutputStream(part, resumed).use { out ->
            c.inputStream.use { inp ->
                val buf = ByteArray(1 shl 16); var done = start; var last = -1
                while (true) {
                    val n = inp.read(buf); if (n < 0) break
                    out.write(buf, 0, n); done += n
                    val p = if (total > 0) (done * 100 / total).toInt() else 0
                    if (p != last) { last = p; emit(p) }
                }
            }
        }
        part.renameTo(dest); emit(100)
    }.flowOn(Dispatchers.IO)
}
