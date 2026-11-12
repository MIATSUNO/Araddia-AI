package app.araddia

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** extra = contexto oculto (anexo, resultados da web) reenviado ao modelo nas próximas voltas. */
data class Msg(val role: String, val text: String, val extra: String = "")
class Chat(val id: String, var title: String, val msgs: MutableList<Msg>)

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("araddia", Context.MODE_PRIVATE)
    private fun g(k: String, d: String) = p.getString(k, d) ?: d
    private fun s(k: String, v: String) = p.edit().putString(k, v).apply()
    private fun sg(k: String) = p.getString(k, null)?.let { Crypto.dec(it) } ?: ""
    private fun ss(k: String, v: String) = s(k, if (v.isEmpty()) "" else Crypto.enc(v))

    var mode: String get() = g("mode", "local"); set(v) = s("mode", v) // local | remote | auto
    var modelUrl: String get() = g("modelUrl", E4B); set(v) = s("modelUrl", v.trim())
    var template: String get() = g("tpl", "gemma"); set(v) = s("tpl", v)  // gemma | chatml
    var hfToken: String get() = sg("hf"); set(v) = ss("hf", v.trim())
    var remoteBase: String get() = g("rb", "https://openrouter.ai/api/v1"); set(v) = s("rb", v.trim())
    var remoteKey: String get() = sg("rk"); set(v) = ss("rk", v.trim())
    var remoteModel: String get() = g("rm", ""); set(v) = s("rm", v.trim())
    var tavilyKey: String get() = sg("tv"); set(v) = ss("tv", v.trim())
    var web: Boolean get() = p.getBoolean("web", false); set(v) = p.edit().putBoolean("web", v).apply()
    var tts: Boolean get() = p.getBoolean("tts", false); set(v) = p.edit().putBoolean("tts", v).apply()

    fun loadChats(): List<Chat> = try {
        val a = JSONArray(Crypto.dec(g("chats", "")).ifEmpty { "[]" })
        (0 until a.length()).map { i ->
            val o = a.getJSONObject(i); val m = o.getJSONArray("m")
            Chat(o.getString("id"), o.getString("title"), (0 until m.length()).map { j ->
                m.getJSONObject(j).let { Msg(it.getString("r"), it.getString("t"), it.optString("x", "")) }
            }.toMutableList())
        }
    } catch (_: Exception) { emptyList() }

    fun saveChats(c: List<Chat>) {
        val a = JSONArray()
        c.take(60).forEach { ch ->
            val m = JSONArray()
            ch.msgs.takeLast(300).forEach { m.put(JSONObject().put("r", it.role).put("t", it.text).put("x", it.extra)) }
            a.put(JSONObject().put("id", ch.id).put("title", ch.title).put("m", m))
        }
        s("chats", Crypto.enc(a.toString()))
    }

    fun memories(): List<String> = try {
        val a = JSONArray(Crypto.dec(g("mem", "")).ifEmpty { "[]" }); (0 until a.length()).map { a.getString(it) }
    } catch (_: Exception) { emptyList() }
    fun setMemories(l: List<String>) = s("mem", Crypto.enc(JSONArray(l.takeLast(60)).toString()))
    fun addMemory(t: String) = setMemories((memories() + t.trim()).distinct())

    companion object {
        const val E4B = "https://huggingface.co/google/gemma-3n-E4B-it-litert-lm/resolve/main/gemma-3n-E4B-it-int4.litertlm"
        const val E2B = "https://huggingface.co/google/gemma-3n-E2B-it-litert-lm/resolve/main/gemma-3n-E2B-it-int4.litertlm"
    }
}
