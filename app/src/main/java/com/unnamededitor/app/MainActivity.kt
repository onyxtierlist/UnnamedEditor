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
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.material.icons.outlined.Redo
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay
import androidx.compose.runtime.withFrameNanos
import org.json.JSONArray
import org.json.JSONObject
import androidx.compose.ui.viewinterop.AndroidView

private val ink = Color(0xFF10120F)
private val surface = Color(0xFF1B1F19)
private val surface2 = Color(0xFF272D24)
private val line = Color(0xFF3A4335)
private val violet = Color(0xFFB7F36B)
private val lime = Color(0xFFB7F36B)
private val cyan = Color(0xFFD5FF9B)
private val textMain = Color(0xFFF4F5EE)
private val textDim = Color(0xFFA4AA9A)
private val gradient = Brush.linearGradient(listOf(Color(0xFF343D2A), Color(0xFF20261B), Color(0xFF171B15)))
private val glassGradient = Brush.linearGradient(listOf(Color(0xFF252A22), Color(0xFF1B2019), Color(0xFF171B15)))

private data class ClipItem(val id: Int, val title: String, val kind: String, val tint: Color)
private data class EditorSnapshot(
    val clips: List<ClipItem>,
    val effects: Set<String>,
    val speed: String,
    val trimStartMs: Long,
    val trimEndMs: Long,
    val previewX: Float,
    val previewY: Float,
    val videoUri: Uri?,
    val videoName: String,
    val playheadMs: Long,
    val selectedClip: Int
)
private data class NodeItem(val id: Int, val title: String, val detail: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppContextHolder.context = applicationContext
        window.statusBarColor = android.graphics.Color.rgb(16, 18, 15)
        window.navigationBarColor = android.graphics.Color.rgb(16, 18, 15)
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
    var selectedClip by remember { mutableIntStateOf(-1) }
    var nextId by remember { mutableIntStateOf((loadSavedClips().maxOfOrNull { it.id } ?: 4) + 1) }
    var notice by remember { mutableStateOf("Ready to create") }
    var showNewProject by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("My first edit") }
    var showAddLayer by remember { mutableStateOf(false) }
    var clips by remember { mutableStateOf(loadSavedClips()) }
    var undoHistory by remember { mutableStateOf(emptyList<EditorSnapshot>()) }
    var redoHistory by remember { mutableStateOf(emptyList<EditorSnapshot>()) }
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
    val makeSnapshot = {
        EditorSnapshot(clips.toList(), activeEffects.toSet(), speed, trimStartMs, trimEndMs, previewX, previewY, videoUri, videoName, playheadMs, selectedClip)
    }
    fun pushUndo() { undoHistory = (undoHistory + makeSnapshot()).takeLast(50); redoHistory = emptyList() }
    fun restoreSnapshot(snapshot: EditorSnapshot) {
        clips = snapshot.clips; activeEffects = snapshot.effects; speed = snapshot.speed
        trimStartMs = snapshot.trimStartMs; trimEndMs = snapshot.trimEndMs
        previewX = snapshot.previewX; previewY = snapshot.previewY
        videoUri = snapshot.videoUri; videoName = snapshot.videoName; playheadMs = snapshot.playheadMs
        selectedClip = snapshot.selectedClip
        playing = false
    }
    fun undoEdit() {
        val previous = undoHistory.lastOrNull() ?: return
        redoHistory = (redoHistory + makeSnapshot()).takeLast(50)
        undoHistory = undoHistory.dropLast(1); restoreSnapshot(previous); notice = "Undo complete"
    }
    fun redoEdit() {
        val next = redoHistory.lastOrNull() ?: return
        undoHistory = (undoHistory + makeSnapshot()).takeLast(50)
        redoHistory = redoHistory.dropLast(1); restoreSnapshot(next); notice = "Redo complete"
    }
    val context = LocalContext.current
    LaunchedEffect(projectName, videoUri, videoName, clips, activeEffects, resolution, frameRate, quality, previewX, previewY, timelineOffsetMs, trimStartMs, trimEndMs) {
        saveEditorState(context, projectName, videoUri, videoName, clips, activeEffects, resolution, frameRate, quality)
        appPrefs().edit().putString("previewX", previewX.toString()).putString("previewY", previewY.toString())
            .putString("timelineOffset", timelineOffsetMs.toString()).putString("trimStart", trimStartMs.toString()).putString("trimEnd", trimEndMs.toString()).apply()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            pushUndo()
            videoUri = uri
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            videoName = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported video"
            playing = false
            playheadMs = 0L
            durationMs = 0L
            timelineOffsetMs = 0L
            trimStartMs = 0L
            trimEndMs = 0L
            val sourceClip = clips.firstOrNull { it.kind == "VIDEO" }
            clips = if (sourceClip == null) {
                clips + ClipItem(nextId++, videoName, "VIDEO", Color(0xFF6B8E42))
            } else {
                clips.map { if (it.id == sourceClip.id) it.copy(title = videoName) else it }
            }
            notice = "Media imported successfully"
        }
    }

    MaterialTheme(colorScheme = darkColorScheme(
        primary = violet, secondary = cyan, background = ink, surface = surface,
        onPrimary = Color.White, onSurface = textMain
    )) {
        Surface(Modifier.fillMaxSize(), color = ink) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF10120F), Color(0xFF171B15), Color(0xFF10120F))))) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0x223D542D), Color.Transparent), radius = 900f))) {
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
                            if (videoUri == null) {
                                notice = "Import a video before playback"
                            } else {
                                playing = !playing
                                notice = if (playing) "Playback started" else "Playback paused"
                            }
                        },
                        onPosition = { playheadMs = it },
                        onDuration = { durationMs = it },
                        onSeek = { playheadMs = it },
                        onSelectClip = { selectedClip = it },
                        canUndo = undoHistory.isNotEmpty(), canRedo = redoHistory.isNotEmpty(),
                        onUndo = { undoEdit() }, onRedo = { redoEdit() },
                        onSplit = {
                            val chosen = clips.firstOrNull { it.id == selectedClip }
                            if (chosen == null) {
                                notice = "Select a clip to duplicate"
                            } else {
                                pushUndo()
                                val duplicate = chosen.copy(id = nextId++, title = chosen.title + " copy")
                                clips = clips.flatMap { if (it.id == selectedClip) listOf(it, duplicate) else listOf(it) }
                                selectedClip = duplicate.id
                                notice = "Clip duplicated"
                            }
                        },
                        onDelete = {
                            val selected = clips.firstOrNull { it.id == selectedClip }
                            if (selected == null) {
                                notice = "Select a clip first"
                            } else {
                                pushUndo()
                                clips = clips.filterNot { it.id == selectedClip }
                                // Duplicated video entries share the imported source.
                                // Keep the preview while at least one VIDEO entry remains.
                                val hasRemainingVideo = clips.any { it.kind == "VIDEO" }
                                if (selected.kind == "VIDEO" && !hasRemainingVideo) {
                                    videoUri = null
                                    videoName = "No media imported"
                                    playing = false
                                    playheadMs = 0L
                                    durationMs = 0L
                                    trimStartMs = 0L
                                    trimEndMs = 0L
                                    timelineOffsetMs = 0L
                                }
                                selectedClip = clips.firstOrNull()?.id ?: -1
                                notice = "Selected layer removed"
                            }
                        },
                        onSpeed = { pushUndo(); speed = if (speed == "1.0×") "1.5×" else if (speed == "1.5×") "2.0×" else "1.0×"; notice = "Preview speed set to " + speed },
                        onAddLayer = { showAddLayer = true },
                        onEffects = { page = "Effects" },
                        onNodes = { page = "Nodes" },
                        onExport = { page = "Export" },
                        previewX = previewX, previewY = previewY, timelineOffsetMs = timelineOffsetMs, trimStartMs = trimStartMs, trimEndMs = trimEndMs, editorCategory = editorCategory, onCategory = { editorCategory = it },
                        onPreviewX = { previewX = it }, onPreviewY = { previewY = it }, onTimelineOffset = { timelineOffsetMs = it },
                        onTrimStart = {
                            pushUndo()
                            trimStartMs = it.coerceAtLeast(0L)
                            if (trimEndMs > 0L && trimEndMs <= trimStartMs) trimEndMs = 0L
                            notice = "Trim start updated"
                        },
                        onTrimEnd = {
                            pushUndo()
                            trimEndMs = it.coerceAtLeast(trimStartMs + 1L).let { end -> if (durationMs > 0L) end.coerceAtMost(durationMs) else end }
                            notice = "Trim end updated"
                        },
                        onResetTrim = { pushUndo(); trimStartMs = 0L; trimEndMs = 0L; notice = "Trim reset" }
                    )
                    "Effects" -> EffectsScreen(activeEffects, onToggle = {
                        pushUndo()
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
                                MediaExportEngine.export(context, videoUri!!, trimStartMs, trimEndMs.takeIf { it > 0L }, resolution, quality, activeEffects,
                                    onProgress = { exportProgress = it; exportMessage = "Rendering… $it%" },
                                    onComplete = { location -> exporting = false; exportProgress = 100; exportMessage = "Export complete. Saved to " + location; showExportInfo = true },
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
            confirmButton = { TextButton(onClick = {
                projectName = newName.ifBlank { "Untitled Project" }
                clips = emptyList()
                activeEffects = emptySet()
                videoUri = null
                videoName = "No media imported"
                selectedClip = -1
                undoHistory = emptyList()
                redoHistory = emptyList()
                playheadMs = 0L
                durationMs = 0L
                trimStartMs = 0L
                trimEndMs = 0L
                speed = "1.0×"
                previewX = 0f
                previewY = 0f
                timelineOffsetMs = 0L
                editorCategory = "Edit"
                playing = false
                page = "Editor"
                showNewProject = false
            }) { Text("Create") } },
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
                            pushUndo()
                            val tint = when (kind) { "VIDEO" -> Color(0xFF526BC2); "AUDIO" -> Color(0xFF4D9974); "TEXT" -> Color(0xFF9A8B58); else -> Color(0xFFB7774E) }
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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("UNNAMED  /  STUDIO", color = lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(8.dp))
                Text("Create something\nthat hits.", color = textMain, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Black)
                Text("A mobile editing space.", color = textDim, fontSize = 13.sp)
            }
            Box(Modifier.size(54.dp).clip(RoundedCornerShape(18.dp)).background(lime), contentAlignment = Alignment.Center) {
                Text("U.", color = ink, fontSize = 24.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(26.dp))
        Box(Modifier.fillMaxWidth().height(205.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFF20251E)).border(1.dp, Color(0xFF424B37), RoundedCornerShape(26.dp)).clickable { onNew() }.padding(22.dp)) {
            Column(Modifier.align(Alignment.CenterStart)) {
                Text("✦  NEW PROJECT", color = lime, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(16.dp))
                Text("Your next\nmasterpiece.", color = textMain, fontSize = 27.sp, lineHeight = 31.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Text("Start with a blank timeline  →", color = textDim, fontSize = 12.sp)
            }
            Text("✳", color = lime.copy(alpha = .14f), fontSize = 110.sp, modifier = Modifier.align(Alignment.BottomEnd))
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HomeAction("Import media", "＋", Modifier.weight(1f), onImport)
            HomeAction("Effects lab", "✳", Modifier.weight(1f), onEffects)
        }
        Spacer(Modifier.height(30.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pick up where you left off", color = textMain, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("RECENT", color = lime, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(surface).border(1.dp, line, RoundedCornerShape(20.dp)).clickable { onOpen() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(68.dp, 58.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFF30372B)), contentAlignment = Alignment.Center) { Text("▶", color = lime, fontSize = 22.sp) }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(projectName, color = textMain, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("Draft  ·  Tap to continue", color = textDim, fontSize = 11.sp)
            }
            Text("↗", color = lime, fontSize = 22.sp)
        }
        Spacer(Modifier.height(26.dp))
        Text("CUT  /  COLOR  /  CREATE", color = textDim, fontSize = 10.sp, letterSpacing = 2.sp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HomeAction(title: String, icon: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(68.dp).clip(RoundedCornerShape(18.dp)).background(surface).border(1.dp, line, RoundedCornerShape(18.dp)).clickable { onClick() }.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(lime.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Text(icon, color = lime, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        }
        Text(title, color = textMain, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp).weight(1f))
    }
}

@Composable
private fun EditorScreen(
    project: String, videoUri: Uri?, videoName: String, playing: Boolean, clips: List<ClipItem>,
    selectedClip: Int, speed: String, notice: String, playheadMs: Long, durationMs: Long,
    previewX: Float, previewY: Float, timelineOffsetMs: Long, trimStartMs: Long, trimEndMs: Long, editorCategory: String, onCategory: (String) -> Unit,
    onPreviewX: (Float) -> Unit, onPreviewY: (Float) -> Unit, onTimelineOffset: (Long) -> Unit, onTrimStart: (Long) -> Unit, onTrimEnd: (Long) -> Unit, onResetTrim: () -> Unit,
    onBack: () -> Unit, onImport: () -> Unit,
    onPlay: () -> Unit, onPosition: (Long) -> Unit, onDuration: (Long) -> Unit, onSeek: (Long) -> Unit,
    onSelectClip: (Int) -> Unit, canUndo: Boolean, canRedo: Boolean, onUndo: () -> Unit, onRedo: () -> Unit, onSplit: () -> Unit, onDelete: () -> Unit,
    onSpeed: () -> Unit, onAddLayer: () -> Unit, onEffects: () -> Unit, onNodes: () -> Unit, onExport: () -> Unit
) {
    val videoView = remember { mutableStateOf<VideoView?>(null) }
    val preparedPlayer = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val timelineScroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    LaunchedEffect(playing, videoUri, speed) {
        val view = videoView.value
        if (view != null && videoUri != null) {
            runCatching { preparedPlayer.value?.setPlaybackParams(android.media.PlaybackParams().setSpeed(speed.removeSuffix("×").toFloatOrNull() ?: 1f)) }
            if (playing) view.start() else view.pause()
        }
    }
    // When undo restores a removed/replaced source, restore its saved preview position too.
    LaunchedEffect(videoUri) {
        if (videoUri != null) {
            delay(350)
            videoView.value?.seekTo(playheadMs.toInt().coerceAtLeast(0))
        }
    }
    // One frame-paced playback ticker drives both the UI position and timeline follow.
    // Keep this effect independent of playheadMs so position updates do not restart it.
    LaunchedEffect(playing, videoUri, trimStartMs, trimEndMs, durationMs) {
        if (playing && videoUri != null) {
            var lastUiUpdateNanos = 0L
            while (true) {
                val frameTimeNanos = withFrameNanos { it }
                val view = videoView.value ?: continue
                val position = view.currentPosition.toLong()
                val start = trimStartMs.coerceAtLeast(0L)
                val end = trimEndMs
                val effectivePosition = if (position < start || (end > start && position >= end)) {
                    view.seekTo(start.toInt())
                    start
                } else position

                // 30 Hz UI updates are smooth without forcing a full recomposition at 60 Hz.
                if (frameTimeNanos - lastUiUpdateNanos >= 33_000_000L) {
                    onPosition(effectivePosition)
                    lastUiUpdateNanos = frameTimeNanos
                }

                // Follow the actual player clock at display-frame cadence.
                if (durationMs > 0L) {
                    val targetOffset = (effectivePosition.coerceIn(0L, durationMs) / 1000f * 48f * density)
                        .toInt().coerceIn(0, timelineScroll.maxValue)
                    if (kotlin.math.abs(timelineScroll.value - targetOffset) > 1) {
                        timelineScroll.scrollTo(targetOffset)
                    }
                }
            }
        }
    }
    val categories = listOf("Edit", "Audio", "Text", "Stickers", "Overlay", "Effects", "Filters", "Adjust")
    Column(Modifier.fillMaxSize().background(Color(0xFF090A0B))) {
        // CapCut-style compact project bar.
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Outlined.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column(Modifier.weight(1f).padding(start = 5.dp)) {
                Text(project, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Saved locally", color = Color(0xFF92969B), fontSize = 9.sp)
            }
            Surface(onClick = onExport, color = Color(0xFFB7F36B), shape = RoundedCornerShape(7.dp)) {
                Text("Export", color = Color(0xFF10120F), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp))
            }
        }

        // Preview takes the main upper area, like the mobile editing workspace.
        Box(
            Modifier.fillMaxWidth().weight(1f).padding(horizontal = 30.dp, vertical = 7.dp)
                .clip(RoundedCornerShape(5.dp)).background(Color(0xFF17191B)),
            contentAlignment = Alignment.Center
        ) {
            if (videoUri != null) {
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoURI(videoUri)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                preparedPlayer.value = mp
                                val rate = speed.removeSuffix("×").toFloatOrNull() ?: 1f
                                mp.setPlaybackParams(android.media.PlaybackParams().setSpeed(rate))
                                onDuration(mp.duration.toLong())
                                if (playing) start()
                            }
                            videoView.value = this
                        }
                    },
                    update = { view ->
                        if (view.tag != videoUri.toString()) {
                            view.tag = videoUri.toString()
                            view.setVideoURI(videoUri)
                        }
                    },
                    modifier = Modifier.fillMaxSize().offset { IntOffset(previewX.dp.roundToPx(), previewY.dp.roundToPx()) }
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = Color(0xFFB7F36B), modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(9.dp))
                    Text("Preview", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("Add a video to start your edit", color = Color(0xFF92969B), fontSize = 10.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("＋  Add video", color = Color(0xFFB7F36B), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onImport() }.padding(8.dp))
                }
            }
        }

        // Playback controls and timecode.
        Row(
            Modifier.fillMaxWidth().height(43.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatTime(playheadMs), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Text(" / " + formatTime(durationMs), color = Color(0xFF85898D), fontSize = 11.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Undo, contentDescription = "Undo", tint = if (canUndo) Color.White else Color(0xFF555A5D), modifier = Modifier.size(19.dp)) }
            IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(34.dp)) { Icon(Icons.Outlined.Redo, contentDescription = "Redo", tint = if (canRedo) Color.White else Color(0xFF555A5D), modifier = Modifier.size(19.dp)) }
            IconButton(onClick = onPlay, modifier = Modifier.size(38.dp)) {
                Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, contentDescription = if (playing) "Pause" else "Play", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = onImport, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.FileUpload, contentDescription = "Import media", tint = Color(0xFFB8BBC0), modifier = Modifier.size(18.dp))
            }
        }

        // Scrollable, synchronized timeline: ruler and every track move as one canvas.
        // 48 dp represents one second. Keep a useful scrollable canvas even before
        // duration metadata arrives; side gutters center time zero under the playhead.
        val timelineContentWidth = maxOf(900.dp, ((durationMs.coerceAtLeast(30_000L) / 1000f) * 48f).dp)
        LaunchedEffect(Unit) {
            timelineScroll.scrollTo(timelineOffsetMs.toInt().coerceAtLeast(0))
        }
        LaunchedEffect(timelineScroll.value) {
            onTimelineOffset(timelineScroll.value.toLong())
            if (durationMs > 0L) {
                // The canvas has a half-viewport leading gutter, so scroll offset
                // maps directly to the time under the centered playhead.
                val target = ((timelineScroll.value / density / 48f) * 1000f).toLong().coerceIn(0L, durationMs)
                // While playing, the scroll is following the live playhead. Seeking
                // on every auto-scroll tick would continually interrupt playback.
                if (!playing) {
                    onSeek(target)
                    videoView.value?.seekTo(target.toInt())
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF111315)).padding(top = 7.dp, bottom = 5.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 112.dp, max = 148.dp)) {
                Column(
                    Modifier.width(57.dp).padding(start = 9.dp, top = 1.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Spacer(Modifier.height(19.dp))
                    Text("Video", color = Color(0xFFD6D8DA), fontSize = 9.sp, modifier = Modifier.height(34.dp), maxLines = 1)
                    Text("Audio", color = Color(0xFFD6D8DA), fontSize = 9.sp, modifier = Modifier.height(29.dp), maxLines = 1)
                    Text("Text", color = Color(0xFFD6D8DA), fontSize = 9.sp, modifier = Modifier.height(29.dp), maxLines = 1)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val timelineViewportWidth = maxWidth
                    Row(Modifier.fillMaxSize().horizontalScroll(timelineScroll)) {
                        // Leading and trailing gutters let the fixed playhead reach both ends.
                        Spacer(Modifier.width(timelineViewportWidth / 2))
                        Column(Modifier.width(timelineContentWidth)) {
                            val tickCount = (timelineContentWidth.value / 120f).toInt() + 1
                            Row(Modifier.fillMaxWidth().height(19.dp), verticalAlignment = Alignment.CenterVertically) {
                                repeat(tickCount) { index ->
                                    Text(
                                        formatTime(index * 2_500L),
                                        color = Color(0xFFB9BDC1),
                                        fontSize = 9.sp,
                                        modifier = Modifier.width(120.dp)
                                    )
                                }
                            }
                            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    val videoClips = clips.filter { it.kind == "VIDEO" || it.kind == "OVERLAY" }
                                    if (videoClips.isEmpty()) {
                                        Box(Modifier.width(210.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color(0xFF292D30)).clickable { onImport() }, contentAlignment = Alignment.Center) {
                                            Text("＋  Add video", color = Color(0xFFB7F36B), fontSize = 10.sp)
                                        }
                                    } else videoClips.forEach { clip ->
                                        val clipWidth = if (clip.kind == "VIDEO" && durationMs > 0L) {
                                            (durationMs / 1000f * 48f).dp.coerceAtLeast(72.dp)
                                        } else 145.dp
                                        Box(
                                            Modifier.width(clipWidth).fillMaxHeight().clip(RoundedCornerShape(4.dp))
                                                .background(if (selectedClip == clip.id) Color(0xFF607B3D) else Color(0xFF384A2D))
                                                .then(if (selectedClip == clip.id) Modifier.border(1.dp, Color(0xFFB7F36B), RoundedCornerShape(4.dp)) else Modifier)
                                                .clickable { onSelectClip(clip.id) }.padding(horizontal = 7.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            val clipLabel = if (clip.kind == "VIDEO" && durationMs > 0L) {
                                                clip.title + "  ·  " + formatTime(durationMs)
                                            } else clip.title
                                            Text(clipLabel, color = Color.White, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Surface(onClick = onImport, color = Color(0xFF292D30), shape = RoundedCornerShape(4.dp), modifier = Modifier.width(34.dp).height(34.dp)) {
                                        Box(contentAlignment = Alignment.Center) { Text("+", color = Color.White, fontSize = 20.sp) }
                                    }
                                }
                                Row(Modifier.fillMaxWidth().height(29.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    val audioClips = clips.filter { it.kind == "AUDIO" }
                                    if (audioClips.isEmpty()) {
                                        Box(Modifier.width(145.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color(0xFF22272A)).clickable { onAddLayer() }, contentAlignment = Alignment.CenterStart) {
                                            Text("＋ Add audio", color = Color(0xFF9BCB9A), fontSize = 9.sp, modifier = Modifier.padding(start = 8.dp))
                                        }
                                    } else audioClips.forEach { clip ->
                                        Box(Modifier.width(145.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color(0xFF315D49)).clickable { onSelectClip(clip.id) }.padding(horizontal = 7.dp), contentAlignment = Alignment.CenterStart) {
                                            Text(clip.title, color = Color.White, fontSize = 9.sp, maxLines = 1)
                                        }
                                    }
                                }
                                Row(Modifier.fillMaxWidth().height(29.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                    val textClips = clips.filter { it.kind == "TEXT" }
                                    if (textClips.isEmpty()) {
                                        Box(Modifier.width(145.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color(0xFF22272A)).clickable { onAddLayer() }, contentAlignment = Alignment.CenterStart) {
                                            Text("＋ Add text", color = Color(0xFFE1C27B), fontSize = 9.sp, modifier = Modifier.padding(start = 8.dp))
                                        }
                                    } else textClips.forEach { clip ->
                                        Box(Modifier.width(145.dp).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Color(0xFF78613A)).clickable { onSelectClip(clip.id) }.padding(horizontal = 7.dp), contentAlignment = Alignment.CenterStart) {
                                            Text(clip.title, color = Color.White, fontSize = 9.sp, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.width(timelineViewportWidth / 2))
                    }
                    // Stationary playhead: moving the timeline underneath it scrubs the preview.
                    Box(Modifier.align(Alignment.TopCenter).width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = .95f)))
                    Box(Modifier.align(Alignment.TopCenter).offset(y = 17.dp).size(8.dp).clip(CircleShape).background(Color(0xFFB7F36B)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Swipe timeline left/right to move • Tap a clip to select", color = Color(0xFF92969B), fontSize = 9.sp, modifier = Modifier.weight(1f))
                Text("＋ Add track", color = Color(0xFFB7F36B), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onAddLayer() }.padding(5.dp))
            }
        }

        // Main category strip, followed by tools for the selected category.
        Column(Modifier.fillMaxWidth().background(Color(0xFF090A0B)).padding(top = 5.dp, bottom = 7.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = { onCategory(categories[(categories.indexOf(editorCategory).coerceAtLeast(0) + 1) % categories.size]) },
                    color = Color(0xFF252B22), shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF566447))
                ) {
                    Text("$editorCategory  ›", color = Color(0xFFB7F36B), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
                }
                when (editorCategory) {
                    "Edit" -> {
                        EditorTool("Duplicate", Icons.Outlined.ContentCut, Modifier.width(78.dp), onSplit)
                        EditorTool("Delete", Icons.Outlined.DeleteOutline, Modifier.width(66.dp), onDelete)
                        EditorTool("Preview speed", Icons.Outlined.Speed, Modifier.width(104.dp), onSpeed)
                        EditorTool("Set start", Icons.Outlined.ContentCut, Modifier.width(78.dp)) { onTrimStart(playheadMs) }
                        EditorTool("Set end", Icons.Outlined.ContentCut, Modifier.width(72.dp)) { onTrimEnd(playheadMs) }
                        EditorTool("Reset trim", Icons.Outlined.ContentCut, Modifier.width(82.dp), onResetTrim)
                    }
                    "Audio" -> {
                        EditorTool("Add audio", Icons.Outlined.FileUpload, Modifier.width(82.dp), onAddLayer)

                    }
                    "Text" -> {
                        EditorTool("Add text", Icons.Outlined.AutoFixHigh, Modifier.width(76.dp), onAddLayer)
                        EditorTool("Text layer", Icons.Outlined.FileUpload, Modifier.width(76.dp), onAddLayer)
                    }
                    "Stickers" -> EditorTool("Add sticker", Icons.Outlined.AutoFixHigh, Modifier.width(90.dp), onAddLayer)
                    "Overlay" -> EditorTool("Add overlay", Icons.Outlined.FileUpload, Modifier.width(90.dp), onAddLayer)
                    "Effects" -> EditorTool("Video effects", Icons.Outlined.AutoFixHigh, Modifier.width(95.dp), onEffects)
                    "Filters" -> EditorTool("Filters", Icons.Outlined.AutoFixHigh, Modifier.width(80.dp), onEffects)
                    else -> {
                        EditorTool("Position", Icons.Outlined.AutoFixHigh, Modifier.width(78.dp)) { onCategory("Edit") }
                        EditorTool("Keyframes", Icons.Outlined.Speed, Modifier.width(78.dp)) { onCategory("Edit") }
                        EditorTool("Graph", Icons.Outlined.AccountTree, Modifier.width(72.dp), onNodes)
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorTool(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = modifier.height(48.dp), shape = RoundedCornerShape(12.dp), color = Color(0xFF20251E), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF343D2F))) {
        Row(Modifier.fillMaxSize().padding(horizontal = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = label, tint = Color(0xFFB7F36B), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(5.dp))
            Text(label, color = textMain, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun EffectsScreen(active: Set<String>, onToggle: (String) -> Unit, onBack: () -> Unit) {
    val effects = listOf("Color Grade" to "Adjust color intensity and tone · rendered on export", "Contrast" to "Increase or soften tonal contrast · rendered on export", "Gaussian Blur" to "Apply a soft-focus blur · rendered on export", "Brightness" to "Lift or darken the image · rendered on export")
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
    Column(Modifier.fillMaxSize().background(Color(0xFF090A0B)).verticalScroll(rememberScrollState()).padding(18.dp)) {
        PageHeader("Export", "FINAL RENDER", onBack)
        Surface(shape = RoundedCornerShape(22.dp), color = Color(0xFF1B2118), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3A4930))) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("READY TO RENDER", color = Color(0xFFB7F36B), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                Spacer(Modifier.height(8.dp))
                Text("Your video. Your quality.", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Choose an output size and file quality, then render an MP4.", color = Color(0xFFA4AA9A), fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("VIDEO OUTPUT", color = Color(0xFFB7F36B), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        SettingRow("Resolution", resolution, listOf("720p", "1080p", "1440p", "4K"), onResolution)
        SettingRow("Frame rate", "Source FPS", listOf("Source FPS"), onFrameRate)
        SettingRow("Quality", quality, listOf("Standard", "High", "Maximum"), onQuality)
        Spacer(Modifier.height(10.dp))
        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF151916)) {
            Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("FORMAT", color = Color(0xFFB7F36B), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text("MP4  ·  H.264 video  ·  AAC audio", color = Color.White, fontSize = 11.sp)
                Text("Resolution and quality are applied during rendering. Source frame rate is preserved. On Android 10+, exports are saved to Gallery → Movies/UnnamedEditor.", color = Color(0xFFA4AA9A), fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        if (exporting) {
            Text("RENDERING  $progress%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(7.dp))
            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth(), color = Color(0xFFB7F36B), trackColor = Color(0xFF30362D))
        }
        Text(message, color = textDim, fontSize = 10.sp, maxLines = 4)
        Spacer(Modifier.height(14.dp))
        PrimaryButton(if (exporting) "Rendering…" else "Export video", onExport)
        Spacer(Modifier.height(8.dp))
        Text("Keep the app open while rendering. Export time depends on video length and device speed.", color = textDim, fontSize = 9.sp)
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
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFFB7F36B), Color(0xFF8FCB48)))), contentAlignment = Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = Color(0xFF10120F), fontSize = 13.sp, letterSpacing = .3.sp, fontWeight = FontWeight.Bold)
                Text("  ↗", color = Color(0xFF10120F).copy(alpha = .85f), fontSize = 15.sp)
            }
        }
    }
}
