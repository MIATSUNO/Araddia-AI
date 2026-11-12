package app.araddia

import android.app.Application
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/** Estados de trabalho específicos, cada um com a voz do personagem. */
sealed class Status(val label: String) {
    object Idle : Status("existindo, infelizmente acordado")
    object NoModel : Status("sem cérebro instalado")
    object Loading : Status("acordando o motor… não me apressa")
    object Thinking : Status("julgando sua pergunta em silêncio")
    object Writing : Status("escrevendo. respira.")
    object Reading : Status("destrinchando seu anexo…")
    object Searching : Status("vasculhando a internet por você…")
    object Stopped : Status("interrompido. covarde.")
    object Error : Status("quebrou. a culpa é sua, claro")
    object Ready : Status("cérebro instalado. tremei")
    class Downloading(pct: Int) : Status("baixando um cérebro novo… $pct%")
}

class ChatVM(private val app: Application) : AndroidViewModel(app) {
    val store = Store(app)
    private val chats = store.loadChats().toMutableList()
    val index = mutableStateListOf<Pair<String, String>>()
    var curId by mutableStateOf("")
    val messages = mutableStateListOf<Msg>()
    var status by mutableStateOf<Status>(Status.Idle)
    var busyState by mutableStateOf(false)
    var dlPct by mutableIntStateOf(-1)
    var dlError by mutableStateOf("")
    var pending by mutableStateOf<Pair<String, String>?>(null)
    var web by mutableStateOf(store.web)
    var speak by mutableStateOf(store.tts)
    private var localEngine: LocalEngine? = null
    private var localPath = ""
    private var job: Job? = null
    private var tts: TextToSpeech? = null

    private val greetings = listOf(
        "Então você ousou me invocar. Fala logo, mortal. 🌑",
        "Mais um mortal à minha porta. Entre, mas não toque em nada.",
        "Eu estava ótima no vazio. Mas já que veio, diga."
    )

    init {
        if (chats.isEmpty()) newChat() else open(chats[0].id)
        refreshIndex()
        tts = TextToSpeech(app) { st ->
            if (st == TextToSpeech.SUCCESS) tts?.let { it.language = Locale("pt", "BR"); it.setPitch(0.8f); it.setSpeechRate(0.95f) }
        }
        refreshStatus()
    }

    // ---------- conversas ----------
    private fun refreshIndex() { index.clear(); index.addAll(chats.map { it.id to it.title }) }
    private fun persist() {
        chats.firstOrNull { it.id == curId }?.let { it.msgs.clear(); it.msgs.addAll(messages) }
        store.saveChats(chats)
    }
    fun open(id: String) {
        persist(); curId = id; messages.clear()
        chats.firstOrNull { it.id == id }?.let { messages.addAll(it.msgs) }
    }
    fun newChat() {
        persist()
        val c = Chat(UUID.randomUUID().toString(), "Nova conversa", mutableListOf(Msg("model", greetings.random())))
        chats.add(0, c); open(c.id); refreshIndex()
    }
    fun delete(id: String) {
        job?.cancel()
        chats.removeAll { it.id == id }
        if (chats.isEmpty()) { curId = ""; newChat() } else if (id == curId) { curId = ""; open(chats[0].id) }
        refreshIndex(); store.saveChats(chats)
    }

    // ---------- motores ----------
    fun modelFile() = File(app.filesDir, store.modelUrl.substringBefore('?').substringAfterLast('/'))
    private fun local(): Engine? {
        if (!modelFile().exists()) return null
        val p = modelFile().path
        if (localEngine == null || localPath != p) { localEngine?.close(); localEngine = LocalEngine(app, p, store.template); localPath = p }
        localEngine?.template = store.template
        return localEngine
    }
    private fun remote(): Engine? =
        if (store.remoteKey.isNotBlank() && store.remoteModel.isNotBlank()) RemoteEngine(store.remoteBase, store.remoteKey, store.remoteModel) else null
    private fun online(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val n = cm.activeNetwork ?: return false
        return cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
    private fun pickEngine(): Engine? = when (store.mode) {
        "remote" -> remote()
        "auto" -> { val r = if (online()) remote() else null; val l = local(); if (r != null) FallbackEngine(r, l) else l ?: remote() }
        else -> local()
    }
    private fun hasBrain() = when (store.mode) {
        "remote" -> remote() != null
        "auto" -> remote() != null || modelFile().exists()
        else -> modelFile().exists()
    }

    // ---------- memória ----------
    private val memRe = Regex("(?i)^\\s*(lembra|lembre|anota|guarda)(\\s+(que|disso:?))?\\s+(.{3,})")
    private val autoMem = listOf(
        Regex("(?i)\\bmeu nome é ([\\p{L}]{2,20})") to "O nome do usuário é %s",
        Regex("(?i)\\beu (?:moro|vivo) (?:em|na|no) ([^.,!?\\n]{2,40})") to "O usuário mora em %s",
        Regex("(?i)\\b(?:eu )?trabalho (?:com|como|em|na|no) ([^.,!?\\n]{2,40})") to "O usuário trabalha com/como/em %s",
        Regex("(?i)\\btenho (\\d{1,2}) anos") to "O usuário tem %s anos"
    )
    private fun captureMemory(t: String) {
        memRe.find(t)?.let { store.addMemory(it.groupValues[4]) }
        autoMem.forEach { (re, fmt) -> re.find(t)?.let { store.addMemory(fmt.format(it.groupValues[1].trim())) } }
    }

    private fun system(crisis: Boolean): String {
        val mem = store.memories()
        val now = SimpleDateFormat("EEEE, dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date())
        return Persona.SYSTEM + "\n\nAGORA: $now" +
            (if (mem.isNotEmpty()) "\nFATOS SOBRE O USUÁRIO (use com naturalidade, sem listar):\n" + mem.joinToString("\n") { "- $it" } else "") +
            "\nSe houver [ANEXO] ou [RESULTADOS DA WEB] na conversa, baseie-se neles e diga quando não bastarem." +
            (if (crisis) Persona.CRISIS_NOTE else "")
    }

    private fun clean(s: String) = s.substringBefore("<end_of_turn>").substringBefore("<start_of_turn>").substringBefore("<|im_end|>")
        .substringBefore("\nUsuário:").replace(Regex("^\\s*Araddia:\\s*"), "").trimStart()

    private val webRe = Regex("(?i)^\\s*(busca|pesquisa|procura)(r)?\\b")

    // ---------- envio ----------
    fun send(text: String) {
        val t = text.trim(); val att = pending
        if ((t.isEmpty() && att == null) || busyState) return
        pending = null
        val userText = (if (att != null) "📎 ${att.first}" + (if (t.isNotEmpty()) "\n" else "") else "") + t
        messages.add(Msg("user", userText, att?.let { "[ANEXO: ${it.first}]\n${it.second}" } ?: ""))
        val uIdx = messages.lastIndex
        chats.firstOrNull { it.id == curId }?.let {
            if (it.title == "Nova conversa") { it.title = t.ifEmpty { att?.first ?: "Anexo" }.take(30); refreshIndex() }
        }
        captureMemory(t)
        val eng = pickEngine()
        if (eng == null) {
            messages.add(Msg("model", "Sem cérebro configurado, e você quer conversa? Abre o ⚙: baixa um modelo ou configura uma API. Eu espero. Eternamente, aliás."))
            status = Status.NoModel; persist(); return
        }
        messages.add(Msg("model", ""))
        val idx = messages.lastIndex
        busyState = true
        job = viewModelScope.launch {
            var acc = ""
            try {
                if ((web || webRe.containsMatchIn(t)) && t.isNotEmpty()) {
                    status = Status.Searching
                    val r = Search.run(t, store.tavilyKey)
                    if (r.isNotBlank()) messages[uIdx] = messages[uIdx].let { it.copy(extra = (it.extra + "\n\n[RESULTADOS DA WEB — cite a fonte]\n" + r).trim()) }
                }
                status = if (!eng.loaded) Status.Loading else Status.Thinking
                val crisis = Persona.isCrisis(t)
                val turns = messages.take(idx).mapIndexed { i, m ->
                    var x = if (m.extra.isNotBlank()) m.text + "\n\n" + m.extra else m.text
                    if (i == idx - 1 && !crisis) x += Persona.REMINDER
                    Turn(m.role, x)
                }
                eng.generate(system(crisis), turns).collect { d ->
                    if (acc.isEmpty()) status = Status.Writing
                    acc += d
                    messages[idx] = Msg("model", clean(acc))
                }
                if (clean(acc).isBlank()) messages[idx] = Msg("model", "…silêncio. Até eu me surpreendi. Pergunta de novo.")
                else if (speak) say(clean(acc))
                status = Status.Idle
            } catch (e: CancellationException) {
                status = Status.Stopped
                if (acc.isBlank()) messages[idx] = Msg("model", "…")
            } catch (e: Throwable) {
                messages[idx] = Msg("model", "Algo quebrou: ${e.message?.take(160)}. Culpa sua. Provavelmente.")
                status = Status.Error
            } finally {
                busyState = false; persist()
            }
        }
    }

    fun stop() { job?.cancel(); tts?.stop() }

    private fun say(s: String) {
        val plain = s.replace(Regex("```[\\s\\S]*?```"), " ").replace(Regex("[*`#_]"), "")
        tts?.speak(plain.take(1500), TextToSpeech.QUEUE_FLUSH, null, "araddia")
    }
    fun setSpeak(v: Boolean) { speak = v; store.tts = v; if (!v) tts?.stop() }
    fun setWeb(v: Boolean) { web = v; store.web = v }

    // ---------- anexos ----------
    fun attach(uri: Uri) {
        viewModelScope.launch {
            status = Status.Reading
            try { pending = Attach.read(app, uri); status = Status.Idle }
            catch (e: Throwable) {
                status = Status.Error
                messages.add(Msg("model", "Não consegui digerir esse anexo: ${e.message?.take(120)}. Culpa do arquivo, claro."))
            }
        }
    }

    // ---------- download ----------
    fun download() {
        if (dlPct in 0..99) return
        dlError = ""; dlPct = 0
        viewModelScope.launch {
            try {
                Downloader.download(store.modelUrl, modelFile(), store.hfToken).collect { p -> dlPct = p; status = Status.Downloading(p) }
                dlPct = -1; status = Status.Ready
            } catch (e: Throwable) { dlPct = -1; dlError = e.message ?: "falhou"; status = Status.Error }
        }
    }

    fun refreshStatus() { if (!busyState && dlPct < 0) status = if (hasBrain()) Status.Idle else Status.NoModel }

    override fun onCleared() { tts?.shutdown(); localEngine?.close(); super.onCleared() }
}
