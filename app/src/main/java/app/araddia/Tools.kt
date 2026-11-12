package app.araddia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }.addOnFailureListener { c.resumeWithException(it) }
}

/** Anexos: imagem (OCR), PDF (texto; se escaneado, OCR) e arquivos de texto/código. */
object Attach {
    private const val LIMIT = 12000

    suspend fun read(ctx: Context, uri: Uri): Pair<String, String> = withContext(Dispatchers.IO) {
        val cr = ctx.contentResolver
        val name = cr.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) c.getString(i) else null
        } ?: "anexo"
        val mime = cr.getType(uri) ?: ""
        val text = when {
            mime.startsWith("image/") -> ocr(decode(ctx, uri))
            mime == "application/pdf" || name.endsWith(".pdf", true) -> pdf(ctx, uri)
            else -> cr.openInputStream(uri)?.use { it.bufferedReader().readText() } ?: ""
        }.trim()
        if (text.isBlank()) error("nada legível no arquivo")
        name to (if (text.length > LIMIT) text.take(LIMIT) + "\n[…cortado]" else text)
    }

    private fun decode(ctx: Context, uri: Uri): Bitmap {
        val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, b) }
        var s = 1; while (b.outWidth / s > 2400) s *= 2
        val o = BitmapFactory.Options().apply { inSampleSize = s }
        return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) } ?: error("imagem inválida")
    }

    private suspend fun ocr(bmp: Bitmap): String {
        val r = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try { return r.process(InputImage.fromBitmap(bmp, 0)).await().text } finally { r.close() }
    }

    private suspend fun pdf(ctx: Context, uri: Uri): String {
        PDFBoxResourceLoader.init(ctx)
        val t = try {
            ctx.contentResolver.openInputStream(uri)?.use { PDDocument.load(it).use { d -> PDFTextStripper().getText(d) } } ?: ""
        } catch (_: Throwable) { "" }
        if (t.isNotBlank()) return t
        // PDF escaneado: renderiza as 3 primeiras páginas e faz OCR
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return ""
        val sb = StringBuilder()
        PdfRenderer(pfd).use { r ->
            for (i in 0 until minOf(3, r.pageCount)) {
                r.openPage(i).use { pg ->
                    val bmp = Bitmap.createBitmap(pg.width * 2, pg.height * 2, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    pg.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    sb.append(ocr(bmp)).append('\n')
                }
            }
        }
        return sb.toString()
    }
}

/** Busca na web: Tavily (se houver chave) ou Wikipédia pt (sem chave). */
object Search {
    private fun http(url: String, body: String? = null, headers: Map<String, String> = emptyMap()): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "Araddia/2.1")
        headers.forEach { c.setRequestProperty(it.key, it.value) }
        if (body != null) { c.requestMethod = "POST"; c.doOutput = true; c.outputStream.use { it.write(body.toByteArray()) } }
        return if (c.responseCode in 200..299) c.inputStream.bufferedReader().readText() else ""
    }

    suspend fun run(raw: String, tavilyKey: String): String = withContext(Dispatchers.IO) {
        val q = raw.replace(Regex("(?i)^\\s*(busca|pesquisa|procura)(r)?\\s*(por|sobre)?\\s*"), "").take(150).ifBlank { raw.take(150) }
        runCatching { if (tavilyKey.isNotBlank()) tavily(q, tavilyKey) else wiki(q) }.getOrDefault("")
    }

    private fun tavily(q: String, key: String): String {
        val body = JSONObject().put("api_key", key).put("query", q).put("max_results", 4).put("include_answer", true).toString()
        val j = JSONObject(http("https://api.tavily.com/search", body, mapOf("Content-Type" to "application/json", "Authorization" to "Bearer $key")))
        val sb = StringBuilder()
        j.optString("answer").takeIf { it.isNotBlank() && it != "null" }?.let { sb.append("Resumo: $it\n") }
        j.optJSONArray("results")?.let { a ->
            for (i in 0 until a.length()) a.getJSONObject(i).let { sb.append("- ${it.optString("title")} (${it.optString("url")}): ${it.optString("content").take(400)}\n") }
        }
        return sb.toString()
    }

    private fun wiki(q: String): String {
        val u = "https://pt.wikipedia.org/w/api.php?action=query&generator=search&gsrsearch=${URLEncoder.encode(q, "UTF-8")}" +
            "&gsrlimit=3&prop=extracts&exintro=1&explaintext=1&exchars=800&format=json&utf8=1"
        val pages = JSONObject(http(u)).optJSONObject("query")?.optJSONObject("pages") ?: return ""
        return pages.keys().asSequence().map { pages.getJSONObject(it) }.sortedBy { it.optInt("index") }
            .joinToString("\n") { "- Wikipédia — ${it.optString("title")}: ${it.optString("extract")}" }
    }
}
