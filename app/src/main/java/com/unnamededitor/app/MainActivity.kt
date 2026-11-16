package com.unnamededitor.app

import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.viewinterop.AndroidView

private val ink = Color(0xFF080D1A)
private val surface = Color(0xCC18243A)
private val surface2 = Color(0xB3263652)
private val line = Color(0x36D8E8FF)
private val violet = Color(0xFF9B91FF)
private val cyan = Color(0xFF79E6D3)
private val textMain = Color(0xFFF3F7FF)
private val textDim = Color(0xFFA1B0C8)
private val gradient = Brush.linearGradient(listOf(Color(0xFF5546A8), Color(0xFF236F83), Color(0xFF172944)))
private val glassGradient = Brush.linearGradient(listOf(Color(0xCCFFFFFF), Color(0x14FFFFFF), Color(0x221A9CBB)))

private data class ClipItem(val id: Int, val title: String, val kind: String, val tint: Color)
private data class NodeItem(val id: Int, val title: String, val detail: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContextHolder.context = applicationContext
        window.statusBarColor = android.graphics.Color.rgb(8, 11, 19)
        window.navigationBarColor = android.graphics.Color.rgb(8, 11, 19)
        setContent { UnnamedEditor() }
    }
}


private fun contextPrefs(key: String, fallback: String): String = appPrefs().getString(key, fallback) ?: fallback
private fun contextPrefsName(): String = contextPrefs("projectName", "Untitled Project")
private fun contextPrefsUri(): Uri? = appPrefs().getString("videoUri", null)?.let { runCatching { Uri.parse(it) }.getOrNull() }
private fun contextPrefsVideoName(): String = contextPrefs("videoName", "No media imported")
private fun appPrefs() = AppContextHolder.context.getSharedPreferences("unnamed_editor_projects", android.content.Context.MODE_PRIVATE)
private fun loadSavedClips(): List<ClipItem> = runCatching {
    val arr = JSONArray(appPrefs().getString("clips", "[]"))
    (0 until arr.length()).map { i -> arr.getJSONObject(i).let { ClipItem(it.getInt("id"), it.getString("title"), it.getString("kind"), Color(it.getLong("tint").toLong())) } }
}.getOrElse { emptyList() }
private fun loadSavedEffects(): Set<String> = runCatching {
    val arr = JSONArray(appPrefs().getString("effects", "[]")); (0 until arr.length()).map { arr.getString(it) }.toSet()
}.getOrElse { emptySet() }
private fun saveEditorState(context: android.content.Context, project: String, uri: Uri?, video: String, clips: List<ClipItem>, effects: Set<String>, resolution: String, fps: String, quality: String) {
    AppContextHolder.context = context.applicationContext
    val clipArray = JSONArray().apply { clips.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("kind", it.kind).put("tint", it.tint.value.toLong())) } }
    val effectArray = JSONArray().apply { effects.forEach { put(it) } }
    appPrefs().edit().putString("projectName", project).putString("videoUri", uri?.toString()).putString("videoName", video).putString("clips", clipArray.toString()).putString("effects", effectArray.toString()).putString("resolution", resolution).putString("frameRate", fps).putString("quality", quality).apply()
}
private object AppContextHolder { lateinit var context: android.content.Context }

@Composable
private fun UnnamedEditor() {
    var page by remember { mutableStateOf("Home") }
    var projectName by remember { mutableStateOf(contextPrefsName()) }
    var videoUri by remember { mutableStateOf(contextPrefsUri()) }
    var videoName by remember { mutableStateOf(contextPrefsVideoName()) }
    var playing by remember { mutableStateOf(false) }
    var playheadMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var selectedClip by remember { mutableIntStateOf(1) }
    var nextId by remember { mutableIntStateOf((loadSavedClips().maxOfOrNull { it.id } ?: 4) + 1) }
    var notice by remember { mutableStateOf("Ready to create") }
    var showNewProject by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("My first edit") }
    var showAddLayer by remember { mutableStateOf(false) }
    var clips by remember { mutableStateOf(loadSavedClips()) }
    var nodes by remember { mutableStateOf(listOf(
        NodeItem(1, "Media Input", "Source"),
        NodeItem(2, "Color Grade", "Adjust"),
        NodeItem(3, "Glow", "Stylize"),
        NodeItem(4, "Composite", "Output")
    )) }
    var activeEffects by remember { mutableStateOf(loadSavedEffects()) }
    var resolution by remember { mutableStateOf(contextPrefs("resolution", "1080p")) }
    var frameRate by remember { mutableStateOf(contextPrefs("frameRate", "30 FPS")) }
    var quality by remember { mutableStateOf(contextPrefs("quality", "High")) }
    var speed by remember { mutableStateOf("1.0×") }
    var showExportInfo by remember { mutableStateOf(false) }
    var previewX by remember { mutableFloatStateOf(contextPrefs("previewX", "0").toFloatOrNull() ?: 0f) }
    var previewY by remember { mutableFloatStateOf(contextPrefs("previewY", "0").toFloatOrNull() ?: 0f) }
    var timelineOffsetMs by remember { mutableLongStateOf(contextPrefs("timelineOffset", "0").toLongOrNull() ?: 0L) }
    var trimStartMs by remember { mutableLongStateOf(contextPrefs("trimStart", "0").toLongOrNull() ?: 0L) }
    var trimEndMs by remember { mutableLongStateOf(contextPrefs("trimEnd", "0").toLongOrNull() ?: 0L) }
    var exportProgress by remember { mutableIntStateOf(0) }
    var exportMessage by remember { mutableStateOf("Ready to export") }
    var exporting by remember { mutableStateOf(false) }
    var editorCategory by remember { mutableStateOf("Edit") }
    val context = LocalContext.current
    LaunchedEffect(projectName, videoUri, videoName, clips, activeEffects, resolution, frameRate, quality, previewX, previewY, timelineOffsetMs, trimStartMs, trimEndMs) {
        saveEditorState(context, projectName, videoUri, videoName, clips, activeEffects, resolution, frameRate, quality)
        appPrefs().edit().putString("previewX", previewX.toString()).putString("previewY", previewY.toString())
            .putString("timelineOffset", timelineOffsetMs.toString()).putString("trimStart", trimStartMs.toString()).putString("trimEnd", trimEndMs.toString()).apply()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            videoUri = uri
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            videoName = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported video"
            playing = false
            if (clips.none { it.kind == "VIDEO" }) clips = clips + ClipItem(nextId++, videoName, "VIDEO", Color(0xFF526BC2))
            notice = "Media imported successfully"
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = violet, secondary = cyan, background = ink, surface = surface,
        onPrimary = Color.White, onSurface = textMain
    )) {
        Surface(Modifier.fillMaxSize(), color = ink) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0A1020), Color(0xFF101C32), Color(0xFF0B1727))))) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x333E83A5), Color.Transparent), radius = 900f))) {
            AnimatedContent(targetState = page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "page") { current ->
                when (current) {
                    "Home" -> HomeScreen(
                        projectName = projectName,
                        onNew = { showNewProject = true },
                        onOpen = { page = "Editor" },
                        onImport = { picker.launch(arrayOf("video/*")) },
                        onEffects = { page = "Effects" }
                    )
                    "Editor" -> EditorScreen(
                        projectName, videoUri, videoName, playing, clips, selectedClip, speed, notice, playheadMs, durationMs,
                        onBack = { page = "Home" },
                        onImport = { picker.launch(arrayOf("video/*")) },
                        onPlay = {
                            playing = !playing
                            notice = if (playing) "Playback paused" else "Playback started"
                        },
                        onPosition = { playheadMs = it },
                        onDuration = { durationMs = it },
                        onSeek = { playheadMs = it },
                        onSelectClip = { selectedClip = it },
                        onSplit = {
                            val chosen = clips.firstOrNull { it.id == selectedClip }
                            if (chosen != null) {
                                clips = clips.flatMap { if (it.id == selectedClip) listOf(it.copy(title = it.title + " A"), it.copy(id = nextId++, title = it.title + " B")) else listOf(it) }
                                selectedClip = clips.first { it.title.endsWith(" A") }.id
                                notice = "Selected clip split at playhead"
                            } else notice = "Select a clip to split"
                        },
                        onDelete = {
                            clips = clips.filterNot { it.id == selectedClip }
                            selectedClip = clips.firstOrNull()?.id ?: -1
                            notice = "Selected layer removed"
                        },
                        onSpeed = { speed = if (speed == "1.0×") "1.5×" else if (speed == "1.5×") "2.0×" else "1.0×"; notice = "Preview speed set to " + speed },
                        onAddLayer = { showAddLayer = true },
                        onEffects = { page = "Effects" },
                        onNodes = { page = "Nodes" },
                        onExport = { page = "Export" },
                        previewX = previewX, previewY = previewY, timelineOffsetMs = timelineOffsetMs, trimStartMs = trimStartMs, trimEndMs = trimEndMs, editorCategory = editorCategory, onCategory = { editorCategory = it },
                        onPreviewX = { previewX = it }, onPreviewY = { previewY = it }, onTimelineOffset = { timelineOffsetMs = it },
                        onTrimStart = { trimStartMs = it }, onTrimEnd = { trimEndMs = it }
                    )
                    "Effects" -> EffectsScreen(activeEffects, onToggle = {
                        activeEffects = if (activeEffects.contains(it)) activeEffects - it else activeEffects + it
                        notice = if (activeEffects.contains(it)) it + " enabled" else it + " removed"
                    }, onBack = { page = "Editor" })
                    "Nodes" -> NodesScreen(nodes,
                        onAdd = { title -> nodes = nodes + NodeItem(nextId++, title, "Effect node"); notice = title + " node added" },
                        onRemove = { id -> nodes = nodes.filterNot { it.id == id }; notice = "Node removed" },
                        onBack = { page = "Editor" })
                    else -> ExportScreen(resolution, frameRate, quality, exportProgress, exportMessage, exporting,
                        onResolution = { resolution = it }, onFrameRate = { frameRate = it }, onQuality = { quality = it },
                        onBack = { page = "Editor" }, onExport = {
                            if (videoUri == null) { exportMessage = "Import a video first"; showExportInfo = true }
                            else {
                                exporting = true; exportProgress = 0; exportMessage = "Preparing video…"
                                MediaExportEngine.export(context, videoUri!!, trimStartMs, trimEndMs.takeIf { it > 0L },
                                    onProgress = { exportProgress = it; exportMessage = "Rendering… $it%" },
                                    onComplete = { file -> exporting = false; exportProgress = 100; exportMessage = "Export complete: " + file.absolutePath; showExportInfo = true },
                                    onError = { error -> exporting = false; exportMessage = error; showExportInfo = true })
                            }
                        })
                }
            }
            }
            }
        }
    }

    if (showNewProject) {
        AlertDialog(
            onDismissRequest = { showNewProject = false },
            title = { Text("Create project", color = textMain) },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Project name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { projectName = newName.ifBlank { "Untitled Project" }; clips = emptyList(); activeEffects = emptySet(); videoUri = null; videoName = "No media imported"; page = "Editor"; showNewProject = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { showNewProject = false }) { Text("Cancel") } },
            containerColor = surface
        )
    }
    if (showAddLayer) {
        AlertDialog(
            onDismissRequest = { showAddLayer = false },
            title = { Text("Add timeline layer", color = textMain) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("VIDEO", "AUDIO", "TEXT", "FX").forEach { kind ->
                        OutlinedButton(onClick = {
                            val tint = when (kind) { "VIDEO" -> Color(0xFF526BC2); "AUDIO" -> Color(0xFF238E91); "TEXT" -> Color(0xFF9866C8); else -> Color(0xFFB47754) }
                            clips = clips + ClipItem(nextId++, kind.lowercase().replaceFirstChar { it.uppercase() } + " layer", kind, tint)
                            showAddLayer = false
                            notice = kind + " layer added"
                        }, modifier = Modifier.fillMaxWidth()) { Text("＋  " + kind + " layer") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAddLayer = false }) { Text("Close") } },
            containerColor = surface
        )
    }
    if (showExportInfo) {
        AlertDialog(
            onDismissRequest = { showExportInfo = false },
            title = { Text(if (exporting) "Exporting video…" else "Export status", color = textMain) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(exportMessage, color = textDim)
                if (exporting) LinearProgressIndicator(progress = { exportProgress / 100f }, modifier = Modifier.fillMaxWidth())
            } },
            confirmButton = { TextButton(onClick = { showExportInfo = false }) { Text("Got it") } },
            containerColor = surface
        )
    }
}

@Composable
private fun HomeScreen(projectName: String, onNew: () -> Unit, onOpen: () -> Unit, onImport: () -> Unit, onEffects: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("UNNAMED STUDIO", color = cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Text("Make it move.", color = textMain, fontSize = 29.sp, fontWeight = FontWeight.Bold)
                Text("Your creative space", color = textDim, fontSize = 13.sp)
            }
            Box(Modifier.size(46.dp).clip(CircleShape).background(surface2), contentAlignment = Alignment.Center) {
                Text("U", color = violet, fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp)).background(gradient).border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(28.dp)).clickable { onNew() }.padding(22.dp)) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Surface(color = Color.White.copy(alpha = .12f), shape = RoundedCornerShape(50)) {
                    Text("NEW WORKSPACE", color = textMain, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                }
                Spacer(Modifier.height(14.dp))
                Text("Start a new\ncreation.", color = Color.White, fontSize = 25.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Build something worth watching  ↗", color = Color.White.copy(alpha = .75f), fontSize = 11.sp)
            }
            Text("✦", color = Color.White.copy(alpha = .22f), fontSize = 90.sp, modifier = Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            QuickAction("Import video", "＋", Modifier.weight(1f), onImport)
            QuickAction("Explore FX", "✧", Modifier.weight(1f), onEffects)
        }
        Spacer(Modifier.height(26.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent projects", color = textMain, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("VIEW ALL", color = cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(glassGradient).border(1.dp, line, RoundedCornerShape(18.dp)).clickable { onOpen() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp, 54.dp).clip(RoundedCornerShape(12.dp)).background(gradient), contentAlignment = Alignment.Center) { Text("▶", color = Color.White, fontSize = 20.sp) }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(projectName, color = textMain, fontWeight = FontWeight.SemiBold)
                Text("Continue editing  ·  Draft", color = textDim, fontSize = 11.sp)
            }
            Text("›", color = violet, fontSize = 28.sp)
        }
        Spacer(Modifier.height(26.dp))
        Text("Every frame, your way.", color = textDim, fontSize = 11.sp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QuickAction(title: String, icon: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(92.dp).clip(RoundedCornerShape(20.dp)).background(glassGradient).border(1.dp, line, RoundedCornerShape(20.dp)).clickable { onClick() }.padding(13.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(violet.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
            Text(icon, color = cyan, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = textMain, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text("↗", color = cyan, fontSize = 15.sp)
        }
    }
}

@Composable
private fun EditorScreen(
    project: String, videoUri: Uri?, videoName: String, playing: Boolean, clips: List<ClipItem>,
    selectedClip: Int, speed: String, notice: String, playheadMs: Long, durationMs: Long,
    previewX: Float, previewY: Float, timelineOffsetMs: Long, trimStartMs: Long, trimEndMs: Long, editorCategory: String, onCategory: (String) -> Unit,
    onPreviewX: (Float) -> Unit, onPreviewY: (Float) -> Unit, onTimelineOffset: (Long) -> Unit, onTrimStart: (Long) -> Unit, onTrimEnd: (Long) -> Unit,
    onBack: () -> Unit, onImport: () -> Unit,
    onPlay: () -> Unit, onPosition: (Long) -> Unit, onDuration: (Long) -> Unit, onSeek: (Long) -> Unit,
    onSelectClip: (Int) -> Unit, onSplit: () -> Unit, onDelete: () -> Unit,
    onSpeed: () -> Unit, onAddLayer: () -> Unit, onEffects: () -> Unit, onNodes: () -> Unit, onExport: () -> Unit
) {
    val videoView = remember { mutableStateOf<VideoView?>(null) }
    LaunchedEffect(playing, videoUri) {
        val view = videoView.value
        if (view != null && videoUri != null) {
            if (playing) view.start() else view.pause()
        }
    }
    LaunchedEffect(playing, videoUri) {
        while (playing && videoUri != null) {
            videoView.value?.let { onPosition(it.currentPosition.toLong()) }
            delay(200)
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolText("‹", onBack, 30)
            Column(Modifier.weight(1f).padding(start = 10.dp)) {
                Text(project, color = textMain, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("EDITING WORKSPACE", color = textDim, fontSize = 9.sp, letterSpacing = 1.2.sp)
            }
            ToolPill("1080p", onExport)
            Spacer(Modifier.width(7.dp))
            ToolPill("Export ↗", onExport, true)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(Color(0xFF17263A), Color(0xFF080E1B)))).border(1.dp, line, RoundedCornerShape(22.dp)), contentAlignment = Alignment.Center) {
            if (videoUri != null) {
                AndroidView(factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(videoUri)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            val rate = speed.removeSuffix("×").toFloatOrNull() ?: 1f
                            mp.setPlaybackParams(android.media.PlaybackParams().setSpeed(rate))
                            onDuration(mp.duration.toLong())
                            if (playing) start()
                        }
                        videoView.value = this
                    }
                }, update = { view ->
                    if (view.tag != videoUri.toString()) {
                        view.tag = videoUri.toString()
                        view.setVideoURI(videoUri)
                    }
                }, modifier = Modifier.fillMaxSize().offset { IntOffset(previewX.dp.roundToPx(), previewY.dp.roundToPx()) })
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(66.dp).clip(CircleShape).background(surface2), contentAlignment = Alignment.Center) { Text("▶", color = violet, fontSize = 26.sp) }
                    Spacer(Modifier.height(12.dp))
                    Text("Your preview lives here", color = textMain, fontWeight = FontWeight.SemiBold)
                    Text("Import a video to start editing", color = textDim, fontSize = 11.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onImport, colors = ButtonDefaults.buttonColors(containerColor = violet)) { Text("＋ Import media") }
                }
            }
        }
        if (videoUri != null) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(surface2).padding(horizontal = 10.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("X", color = cyan, fontSize = 10.sp, modifier = Modifier.width(18.dp))
                Slider(value = previewX.coerceIn(-140f, 140f), onValueChange = onPreviewX, valueRange = -140f..140f, modifier = Modifier.weight(1f).height(28.dp))
                Text("Y", color = cyan, fontSize = 10.sp, modifier = Modifier.width(18.dp))
                Slider(value = previewY.coerceIn(-90f, 90f), onValueChange = onPreviewY, valueRange = -90f..90f, modifier = Modifier.weight(1f).height(28.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(playheadMs), color = textMain, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Surface(onClick = onPlay, modifier = Modifier.size(46.dp), shape = CircleShape, color = violet) { Box(contentAlignment = Alignment.Center) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Color.White) } }
            Text(formatTime(durationMs), color = textDim, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        if (durationMs > 0L) {
            Slider(value = playheadMs.coerceIn(0L, durationMs).toFloat(), onValueChange = { onSeek(it.toLong()); videoView.value?.seekTo(it.toInt()) }, valueRange = 0f..durationMs.toFloat(), colors = SliderDefaults.colors(thumbColor = cyan, activeTrackColor = violet, inactiveTrackColor = line), modifier = Modifier.fillMaxWidth().height(24.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TIMELINE", color = textMain, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text(notice, color = cyan, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            ToolPill("＋ Layer", onAddLayer)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().heightIn(min = 142.dp, max = 205.dp).clip(RoundedCornerShape(16.dp)).background(glassGradient).border(1.dp, line, RoundedCornerShape(16.dp)).padding(10.dp).verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("00:00", color = textDim, fontSize = 9.sp)
                Text("00:05", color = textDim, fontSize = 9.sp)
                Text("00:10", color = textDim, fontSize = 9.sp)
                Text("00:15", color = textDim, fontSize = 9.sp)
            }
            Spacer(Modifier.height(7.dp))
            clips.forEach { clip ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(clip.kind, color = textDim, fontSize = 8.sp, modifier = Modifier.width(43.dp))
                    Box(Modifier.weight(1f).height(31.dp).clip(RoundedCornerShape(8.dp))
                        .background(clip.tint.copy(alpha = if (selectedClip == clip.id) 1f else .55f))
                        .then(if (selectedClip == clip.id) Modifier.border(1.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
                        .clickable { onSelectClip(clip.id) }.offset(x = if (selectedClip == clip.id) (timelineOffsetMs / 100L).toInt().coerceAtMost(120).dp else 0.dp).padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
                        Text(clip.title, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (clips.isEmpty()) Text("Add a layer to begin building your timeline.", color = textDim, fontSize = 10.sp, modifier = Modifier.padding(10.dp))
        }
        if (clips.isNotEmpty()) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(surface2).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CLIP POS", color = cyan, fontSize = 8.sp, modifier = Modifier.width(52.dp))
                    Slider(value = timelineOffsetMs.coerceIn(0L, 15000L).toFloat(), onValueChange = { onTimelineOffset(it.toLong()) }, valueRange = 0f..15000f, modifier = Modifier.weight(1f).height(25.dp))
                    Text(formatTime(timelineOffsetMs), color = textDim, fontSize = 9.sp)
                }
                if (durationMs > 0L && videoUri != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("IN", color = cyan, fontSize = 8.sp, modifier = Modifier.width(52.dp))
                        Slider(value = trimStartMs.coerceIn(0L, durationMs).toFloat(), onValueChange = { value -> onTrimStart(value.toLong().coerceAtMost((trimEndMs.takeIf { it > 0L } ?: durationMs) - 1L)) }, valueRange = 0f..durationMs.toFloat(), modifier = Modifier.weight(1f).height(25.dp))
                        Text(formatTime(trimStartMs), color = textDim, fontSize = 9.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("OUT", color = cyan, fontSize = 8.sp, modifier = Modifier.width(52.dp))
                        Slider(value = (trimEndMs.takeIf { it > 0L } ?: durationMs).coerceIn(1L, durationMs).toFloat(), onValueChange = { value -> onTrimEnd(value.toLong().coerceAtLeast(trimStartMs + 1L)) }, valueRange = 1f..durationMs.toFloat(), modifier = Modifier.weight(1f).height(25.dp))
                        Text(formatTime(trimEndMs.takeIf { it > 0L } ?: durationMs), color = textDim, fontSize = 9.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // CapCut-inspired contextual tool dock: categories first, tools below.
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("Edit", "Audio", "Text", "Overlay", "Effects", "Filters", "Adjust").forEach { category ->
                Surface(onClick = { onCategory(category) }, shape = RoundedCornerShape(12.dp),
                    color = if (editorCategory == category) violet.copy(alpha = .24f) else surface2,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (editorCategory == category) violet else line)) {
                    Text(category, color = if (editorCategory == category) cyan else textDim, fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (editorCategory) {
                "Edit" -> {
                    EditorTool("Split", Icons.Outlined.ContentCut, Modifier.width(76.dp), onSplit)
                    EditorTool("Delete", Icons.Outlined.DeleteOutline, Modifier.width(76.dp), onDelete)
                    EditorTool("Speed", Icons.Outlined.Speed, Modifier.width(76.dp), onSpeed)
                    EditorTool("Transform", Icons.Outlined.AutoFixHigh, Modifier.width(76.dp)) { onCategory("Adjust") }
                }
                "Audio" -> {
                    EditorTool("Add audio", Icons.Outlined.FileUpload, Modifier.width(86.dp), onAddLayer)
                    EditorTool("Extract", Icons.Outlined.Speed, Modifier.width(86.dp), onAddLayer)
                    EditorTool("Volume", Icons.Outlined.AutoFixHigh, Modifier.width(86.dp), onAddLayer)
                }
                "Text" -> {
                    EditorTool("Add text", Icons.Outlined.AutoFixHigh, Modifier.width(86.dp), onAddLayer)
                    EditorTool("Captions", Icons.Outlined.FileUpload, Modifier.width(86.dp), onAddLayer)
                }
                "Overlay" -> EditorTool("Add overlay", Icons.Outlined.FileUpload, Modifier.width(100.dp), onAddLayer)
                "Effects" -> EditorTool("Video effects", Icons.Outlined.AutoFixHigh, Modifier.width(100.dp), onEffects)
                "Filters" -> EditorTool("Browse filters", Icons.Outlined.AutoFixHigh, Modifier.width(100.dp), onEffects)
                else -> {
                    EditorTool("Position", Icons.Outlined.AutoFixHigh, Modifier.width(86.dp)) { onCategory("Edit") }
                    EditorTool("Keyframes", Icons.Outlined.Speed, Modifier.width(86.dp)) { onCategory("Edit") }
                    EditorTool("Graph", Icons.Outlined.AccountTree, Modifier.width(86.dp), onNodes)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun EditorTool(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(72.dp), shape = RoundedCornerShape(19.dp), color = surface2, border = androidx.compose.foundation.BorderStroke(1.dp, line), tonalElevation = 7.dp, shadowElevation = 4.dp) {
        Column(Modifier.fillMaxSize().padding(vertical = 9.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(10.dp)).background(violet.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = cyan, modifier = Modifier.size(17.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(label, color = textMain, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EffectsScreen(active: Set<String>, onToggle: (String) -> Unit, onBack: () -> Unit) {
    val effects = listOf("Color Grade" to "Tone, contrast and saturation", "Curves" to "Fine control over tonal range", "Gaussian Blur" to "Soft focus and depth", "Motion Blur" to "Directional movement blur", "Glow" to "Bloom highlights", "Chromatic Aberration" to "RGB channel separation", "Vignette" to "Darken the frame edges", "Sharpen" to "Enhance fine detail")
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        PageHeader("Effects Lab", "LIVE EFFECT STACK", onBack)
        Text("Tap an effect to add or remove it from the active stack.", color = textDim, fontSize = 12.sp)
        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("ACTIVE", color = cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(active.size.toString() + " enabled", color = textDim, fontSize = 10.sp)
        }
        Spacer(Modifier.height(8.dp))
        if (active.isEmpty()) Text("No effects applied yet.", color = textDim, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
        else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            active.forEach { AssistChip(onClick = { onToggle(it) }, label = { Text(it) }, leadingIcon = { Text("✕") })
            }
        }
        Spacer(Modifier.height(10.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            effects.forEach { (name, detail) ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(glassGradient).border(1.dp, line, RoundedCornerShape(16.dp)).clickable { onToggle(name) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(surface2), contentAlignment = Alignment.Center) { Text("✧", color = violet, fontSize = 21.sp) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(name, color = textMain, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(detail, color = textDim, fontSize = 10.sp)
                    }
                    Text(if (active.contains(name)) "ON" else "＋", color = if (active.contains(name)) cyan else violet, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
        PrimaryButton("Done", onBack)
    }
}

@Composable
private fun NodesScreen(nodes: List<NodeItem>, onAdd: (String) -> Unit, onRemove: (Int) -> Unit, onBack: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        PageHeader("Node workspace", "COMPOSITING GRAPH", onBack)
        Text("Manage the processing chain. Nodes can be added and removed.", color = textDim, fontSize = 11.sp)
        Spacer(Modifier.height(15.dp))
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(glassGradient).border(1.dp, line, RoundedCornerShape(20.dp)).padding(14.dp)) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                nodes.forEachIndexed { index, node ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, violet.copy(alpha = .6f), RoundedCornerShape(14.dp)).background(surface2).padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(violet.copy(alpha = .18f)), contentAlignment = Alignment.Center) { Text("◇", color = violet) }
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text(node.title, color = textMain, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            Text(node.detail, color = textDim, fontSize = 9.sp)
                        }
                        Text("×", color = textDim, fontSize = 20.sp, modifier = Modifier.clickable { onRemove(node.id) }.padding(horizontal = 5.dp))
                    }
                    if (index != nodes.lastIndex) Text("↓", color = cyan, fontSize = 22.sp, modifier = Modifier.padding(3.dp))
                }
                if (nodes.isEmpty()) Text("Graph is empty. Add a node below.", color = textDim, modifier = Modifier.padding(24.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Box {
            Surface(onClick = { showMenu = true }, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(17.dp), color = surface2, border = androidx.compose.foundation.BorderStroke(1.dp, line)) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("＋", color = cyan, fontSize = 22.sp)
                    Text("Add a node", color = textMain, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                    Text("⌄", color = textDim, fontSize = 18.sp)
                }
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                listOf("Color Adjust", "Blur", "Glow", "Transform", "Mask", "Blend").forEach { name ->
                    DropdownMenuItem(text = { Text(name) }, onClick = { onAdd(name); showMenu = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Back to editor", onBack)
    }
}

@Composable
private fun ExportScreen(resolution: String, frameRate: String, quality: String, progress: Int, message: String, exporting: Boolean, onResolution: (String) -> Unit, onFrameRate: (String) -> Unit, onQuality: (String) -> Unit, onBack: () -> Unit, onExport: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        PageHeader("Export settings", "OUTPUT CONFIGURATION", onBack)
        Box(Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(22.dp)).background(gradient), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✦", color = Color.White, fontSize = 35.sp)
                Text("Your final frame starts here", color = Color.White, fontWeight = FontWeight.SemiBold)
                Text("Configure the output quality", color = Color.White.copy(alpha = .7f), fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        SettingRow("Resolution", resolution, listOf("720p", "1080p", "1440p", "4K"), onResolution)
        SettingRow("Frame rate", frameRate, listOf("24 FPS", "30 FPS", "60 FPS"), onFrameRate)
        SettingRow("Quality", quality, listOf("Standard", "High", "Maximum"), onQuality)
        Spacer(Modifier.weight(1f))
        Text("MP4 render · Source trim · H.264", color = textDim, fontSize = 10.sp)
        if (exporting) LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
        Text(message, color = textDim, fontSize = 10.sp, maxLines = 2)
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Prepare export", onExport)
    }
}

@Composable
private fun SettingRow(title: String, value: String, options: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = textMain, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Box {
            Surface(onClick = { expanded = true }, shape = RoundedCornerShape(14.dp), color = surface2, border = androidx.compose.foundation.BorderStroke(1.dp, line)) {
                Row(Modifier.widthIn(min = 112.dp).padding(horizontal = 13.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(value, color = textMain, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("⌄", color = cyan, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onChange(option); expanded = false }) }
            }
        }
    }
}

@Composable
private fun PageHeader(title: String, eyebrow: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolText("‹", onBack, 30)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(eyebrow, color = cyan, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text(title, color = textMain, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ToolText(label: String, onClick: () -> Unit, size: Int) {
    Surface(onClick = onClick, modifier = Modifier.size(44.dp), shape = RoundedCornerShape(15.dp), color = surface2, border = androidx.compose.foundation.BorderStroke(1.dp, line)) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = textMain) }
    }
}

@Composable
private fun ToolPill(label: String, onClick: () -> Unit, highlighted: Boolean = false) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = if (highlighted) violet else surface2, border = if (highlighted) null else androidx.compose.foundation.BorderStroke(1.dp, line)) {
        Text(label, color = textMain, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp))
    }
}

@Composable
private fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L).toInt()
    return "%02d:%02d".format(total / 60, total % 60)
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(18.dp), color = violet, shadowElevation = 6.dp) {
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF7769D8), Color(0xFF368F9D)))), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color.White, fontSize = 13.sp, letterSpacing = .3.sp, fontWeight = FontWeight.Bold)
                Text("  ↗", color = Color.White.copy(alpha = .85f), fontSize = 15.sp)
            }
        }
    }
}
