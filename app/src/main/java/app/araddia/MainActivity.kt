@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package app.araddia

import android.app.ActivityManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.launch

private val Orange = Color(0xFFE8743B)
private val Cream = Color(0xFFF3DFC1)
private val Dim = Color(0xFFB9A58E)
private val Ink = Color(0xFF050304)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Orange, onPrimary = Ink, background = Ink, surface = Color(0xFF120A0A), onSurface = Cream,
                    onSurfaceVariant = Dim, secondaryContainer = Color(0xFF5A2A1A), onSecondaryContainer = Cream
                )
            ) { Surface(color = Ink, modifier = Modifier.fillMaxSize()) { AraddiaApp() } }
        }
    }
}

/** Vídeo em loop, mudo, preenchendo a tela, com véu escuro para dar leitura ao texto. */
@Composable
fun VideoBackground() {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val player = remember {
        ExoPlayer.Builder(ctx).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse("android.resource://${ctx.packageName}/${R.raw.bg_araddia}")))
            repeatMode = Player.REPEAT_MODE_ALL; volume = 0f; playWhenReady = true; prepare()
        }
    }
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_PAUSE) player.pause() else if (e == Lifecycle.Event.ON_RESUME) player.play()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs); player.release() }
    }
    AndroidView(
        factory = { c -> PlayerView(c).apply { this.player = player; useController = false; resizeMode = 4 /* ZOOM */; setShutterBackgroundColor(android.graphics.Color.BLACK) } },
        modifier = Modifier.fillMaxSize()
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.42f)))
}

@Composable
fun Ico(t: String, dim: Boolean = false, onClick: () -> Unit) =
    Text(t, Modifier.clip(CircleShape).clickable(onClick = onClick).padding(9.dp).alpha(if (dim) 0.4f else 1f), fontSize = 20.sp)

fun inline(s: String): AnnotatedString = buildAnnotatedString {
    val re = Regex("\\*\\*(.+?)\\*\\*|`([^`]+)`")
    var last = 0
    for (m in re.findAll(s)) {
        append(s.substring(last, m.range.first))
        if (m.groupValues[1].isNotEmpty()) withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = Color(0xFFFFE6C7))) { append(m.groupValues[1]) }
        else withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x55000000), color = Color(0xFFFFB27A))) { append(m.groupValues[2]) }
        last = m.range.last + 1
    }
    append(s.substring(last))
}

/** Markdown leve: blocos ``` com botão copiar, **negrito**, `código`, títulos. */
@Composable
fun Rich(text: String) {
    val clip = LocalClipboardManager.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        text.split("```").forEachIndexed { i, p ->
            if (i % 2 == 1) {
                val code = p.substringAfter('\n', p).trimEnd()
                Surface(color = Color.Black, shape = RoundedCornerShape(10.dp)) {
                    Column(Modifier.padding(8.dp)) {
                        Text(code, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFFFFD9B0), modifier = Modifier.horizontalScroll(rememberScrollState()))
                        TextButton(onClick = { clip.setText(AnnotatedString(code)) }) { Text("copiar", fontSize = 12.sp, color = Orange) }
                    }
                }
            } else if (p.isNotBlank()) {
                Text(inline(p.trim('\n').replace(Regex("(?m)^#{1,6}\\s*"), "")), color = Cream, fontSize = 15.sp, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
fun AraddiaApp(vm: ChatVM = viewModel()) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(false) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> u?.let { vm.attach(it) } }
    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { vm.send(it) }
    }
    LaunchedEffect(vm.messages.size, vm.messages.lastOrNull()?.text) {
        if (vm.messages.isNotEmpty()) list.scrollToItem(vm.messages.lastIndex)
    }
    Box(Modifier.fillMaxSize()) {
        VideoBackground()
        ModalNavigationDrawer(
            drawerState = drawer,
            drawerContent = {
                ModalDrawerSheet(drawerContainerColor = Color(0xF2080405)) {
                    Image(painterResource(R.drawable.araddia_banner), null, Modifier.fillMaxWidth().height(150.dp), contentScale = ContentScale.Crop)
                    TextButton(onClick = { vm.newChat(); scope.launch { drawer.close() } }, modifier = Modifier.padding(8.dp)) { Text("＋ Nova conversa", color = Orange) }
                    LazyColumn {
                        items(vm.index, key = { it.first }) { (id, title) ->
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.open(id); scope.launch { drawer.close() } }.padding(horizontal = 16.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(title, Modifier.weight(1f), color = if (id == vm.curId) Orange else Cream, maxLines = 1)
                                Text("✕", Modifier.clickable { vm.delete(id) }.padding(10.dp), color = Dim)
                            }
                        }
                    }
                }
            }
        ) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico("☰") { scope.launch { drawer.open() } }
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text("Araddia", fontSize = 26.sp, color = Cream, fontFamily = FontFamily.Serif)
                        Text(vm.status.label, fontSize = 12.sp, color = Orange, fontStyle = FontStyle.Italic)
                    }
                    Ico("🔊", dim = !vm.speak) { vm.setSpeak(!vm.speak) }
                    Ico("⚙") { settings = true }
                }
                LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(vm.messages) { m ->
                        val mine = m.role == "user"
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Surface(
                                shape = RoundedCornerShape(18.dp, 18.dp, if (mine) 18.dp else 4.dp, if (mine) 4.dp else 18.dp),
                                color = if (mine) Color(0xCC4A1A10) else Color(0xB3070304),
                                border = BorderStroke(0.5.dp, Color(0x33F3DFC1)),
                                modifier = Modifier.widthIn(max = 330.dp)
                            ) { Box(Modifier.padding(12.dp)) { Rich(m.text.ifEmpty { "…" }) } }
                        }
                    }
                }
                vm.pending?.let { a ->
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📎 ${a.first}", color = Cream, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                        Text("✕", Modifier.clickable { vm.pending = null }.padding(8.dp), color = Dim)
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Ico("📎") { pick.launch(arrayOf("*/*")) }
                    Ico("🎤") {
                        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR").putExtra(RecognizerIntent.EXTRA_PROMPT, "Fala, mortal")
                        try { voice.launch(i) } catch (_: Exception) { Toast.makeText(ctx, "Sem reconhecimento de voz neste aparelho", Toast.LENGTH_SHORT).show() }
                    }
                    Ico("🌐", dim = !vm.web) { vm.setWeb(!vm.web) }
                    OutlinedTextField(
                        input, { input = it }, Modifier.weight(1f), placeholder = { Text("Ouse falar…") }, maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Orange, unfocusedBorderColor = Color(0x66F3DFC1), focusedTextColor = Cream, unfocusedTextColor = Cream,
                            cursorColor = Orange, focusedContainerColor = Color(0x99000000), unfocusedContainerColor = Color(0x99000000)
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    if (vm.busyState) Button(onClick = { vm.stop() }) { Text("■") }
                    else Button(onClick = { vm.send(input); input = "" }) { Text("➤") }
                }
            }
        }
    }
    if (settings) SettingsDialog(vm) { settings = false; vm.refreshStatus() }
}

@Composable
fun SettingsDialog(vm: ChatVM, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val st = vm.store
    var mode by remember { mutableStateOf(st.mode) }
    var url by remember { mutableStateOf(st.modelUrl) }
    var tpl by remember { mutableStateOf(st.template) }
    var hf by remember { mutableStateOf(st.hfToken) }
    var rb by remember { mutableStateOf(st.remoteBase) }
    var rk by remember { mutableStateOf(st.remoteKey) }
    var rm by remember { mutableStateOf(st.remoteModel) }
    var tv by remember { mutableStateOf(st.tavilyKey) }
    val mem = remember { mutableStateListOf<String>().apply { addAll(st.memories()) } }
    val ram = remember {
        val mi = ActivityManager.MemoryInfo(); ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(mi); mi.totalMem / 1073741824.0
    }
    fun save() { st.mode = mode; st.modelUrl = url; st.template = tpl; st.hfToken = hf; st.remoteBase = rb; st.remoteKey = rk; st.remoteModel = rm; st.tavilyKey = tv }
    AlertDialog(
        containerColor = Color(0xF0120A0A),
        onDismissRequest = { save(); onClose() },
        confirmButton = { TextButton(onClick = { save(); onClose() }) { Text("Salvar", color = Orange) } },
        title = { Text("Cérebro da Araddia", fontFamily = FontFamily.Serif) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(mode == "local", { mode = "local" }, { Text("Local") })
                    FilterChip(mode == "remote", { mode = "remote" }, { Text("API") })
                    FilterChip(mode == "auto", { mode = "auto" }, { Text("Híbrido") })
                }
                Text("Híbrido: usa a API quando há internet e cai para o modelo local se falhar.", fontSize = 12.sp, color = Dim)
                if (mode != "remote") {
                    Text("RAM do aparelho: %.1f GB — %s".format(ram, if (ram < 6) "prefira o E2B ou a API." else "o E4B deve rodar."), fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton({ url = Store.E4B }) { Text("Gemma 3n E4B") }
                        OutlinedButton({ url = Store.E2B }) { Text("E2B leve") }
                    }
                    OutlinedTextField(url, { url = it }, label = { Text("URL do modelo (.litertlm/.task)") })
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Template:", fontSize = 12.sp)
                        FilterChip(tpl == "gemma", { tpl = "gemma" }, { Text("Gemma") })
                        FilterChip(tpl == "chatml", { tpl = "chatml" }, { Text("ChatML/Qwen") })
                    }
                    OutlinedTextField(hf, { hf = it }, label = { Text("Token Hugging Face (Gemma exige)") }, visualTransformation = PasswordVisualTransformation())
                    Button(onClick = { save(); vm.download() }, enabled = vm.dlPct !in 0..99) {
                        Text(if (vm.modelFile().exists()) "Baixar de novo" else "Baixar modelo")
                    }
                    if (vm.dlPct in 0..99) LinearProgressIndicator(progress = { vm.dlPct / 100f }, Modifier.fillMaxWidth())
                    if (vm.dlError.isNotEmpty()) Text(vm.dlError, color = Color(0xFFFF8A80), fontSize = 12.sp)
                    if (vm.modelFile().exists()) Text("Instalado: ${vm.modelFile().name}", fontSize = 12.sp)
                }
                if (mode != "local") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton({ rb = "https://openrouter.ai/api/v1" }) { Text("OpenRouter", fontSize = 11.sp) }
                        OutlinedButton({ rb = "https://api.groq.com/openai/v1" }) { Text("Groq", fontSize = 11.sp) }
                        OutlinedButton({ rb = "https://generativelanguage.googleapis.com/v1beta/openai" }) { Text("Gemini", fontSize = 11.sp) }
                    }
                    OutlinedTextField(rb, { rb = it }, label = { Text("Base URL") })
                    OutlinedTextField(rk, { rk = it }, label = { Text("API key") }, visualTransformation = PasswordVisualTransformation())
                    OutlinedTextField(rm, { rm = it }, label = { Text("Modelo (id do provedor)") })
                }
                HorizontalDivider()
                OutlinedTextField(tv, { tv = it }, label = { Text("Chave Tavily (busca web; opcional)") }, visualTransformation = PasswordVisualTransformation())
                Text("Sem chave, a busca 🌐 usa a Wikipédia.", fontSize = 12.sp, color = Dim)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ler respostas em voz alta", Modifier.weight(1f)); Switch(vm.speak, { vm.setSpeak(it) })
                }
                HorizontalDivider()
                Text("Memória (diga \"lembra que…\")", fontSize = 13.sp)
                if (mem.isEmpty()) Text("vazia", fontSize = 12.sp, color = Dim)
                mem.toList().forEachIndexed { i, m ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m, Modifier.weight(1f), fontSize = 12.sp)
                        Text("✕", Modifier.clickable { mem.removeAt(i); st.setMemories(mem.toList()) }.padding(8.dp), color = Dim)
                    }
                }
            }
        }
    )
}
