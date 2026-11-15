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
import androidx.compose.ui.viewinterop.AndroidView

private val ink = Color(0xFF080B13)
private val surface = Color(0xFF111827)
private val surface2 = Color(0xFF182235)
private val line = Color(0xFF29364D)
private val violet = Color(0xFF9B7BFF)
private val cyan = Color(0xFF66D9E8)
private val textMain = Color(0xFFF4F6FF)
private val textDim = Color(0xFF8996AD)
private val gradient = Brush.linearGradient(listOf(Color(0xFF34266F), Color(0xFF174B68), Color(0xFF171E39)))

private data class ClipItem(val id: Int, val title: String, val kind: String, val tint: Color)
private data class NodeItem(val id: Int, val title: String, val detail: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 11, 19)
        window.navigationBarColor = android.graphics.Color.rgb(8, 11, 19)
        setContent { UnnamedEditor() }
    }
}

@Composable
private fun UnnamedEditor() {
    var page by remember { mutableStateOf("Home") }
    var projectName by remember { mutableStateOf("Untitled Project") }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var videoName by remember { mutableStateOf("No media imported") }
    var playing by remember { mutableStateOf(false) }
    var selectedClip by remember { mutableIntStateOf(1) }
    var nextId by remember { mutableIntStateOf(5) }
    var notice by remember { mutableStateOf("Ready to create") }
    var showNewProject by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("My first edit") }
    var showAddLayer by remember { mutableStateOf(false) }
    var clips by remember { mutableStateOf(listOf(
        ClipItem(1, "Video 01", "VIDEO", Color(0xFF526BC2)),
        ClipItem(2, "Audio track", "AUDIO", Color(0xFF238E91)),
        ClipItem(3, "Title layer", "TEXT", Color(0xFF9866C8)),
        ClipItem(4, "Effects", "FX", Color(0xFFB47754))
    )) }
    var nodes by remember { mutableStateOf(listOf(
        NodeItem(1, "Media Input", "Source"),
        NodeItem(2, "Color Grade", "Adjust"),
        NodeItem(3, "Glow", "Stylize"),
        NodeItem(4, "Composite", "Output")
    )) }
    var activeEffects by remember { mutableStateOf(setOf<String>()) }
    var resolution by remember { mutableStateOf("1080p") }
    var frameRate by remember { mutableStateOf("30 FPS") }
    var quality by remember { mutableStateOf("High") }
    var speed by remember { mutableStateOf("1.0×") }
    var showExportInfo by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            videoUri = uri
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
            AnimatedContent(targetState = page, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "page") { current ->
                when (current) {
                    "Home" -> HomeScreen(
                        projectName = projectName,
                        onNew = { showNewProject = true },
                        onOpen = { page = "Editor" },
                        onImport = { picker.launch("video/*") },
                        onEffects = { page = "Effects" }
                    )
                    "Editor" -> EditorScreen(
                        projectName, videoUri, videoName, playing, clips, selectedClip, speed, notice,
                        onBack = { page = "Home" },
                        onImport = { picker.launch("video/*") },
                        onPlay = {
                            playing = !playing
                            notice = if (playing) "Playback paused" else "Playback started"
                        },
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
                        onExport = { page = "Export" }
                    )
                    "Effects" -> EffectsScreen(activeEffects, onToggle = {
                        activeEffects = if (activeEffects.contains(it)) activeEffects - it else activeEffects + it
                        notice = if (activeEffects.contains(it)) it + " enabled" else it + " removed"
                    }, onBack = { page = "Editor" })
                    "Nodes" -> NodesScreen(nodes,
                        onAdd = { title -> nodes = nodes + NodeItem(nextId++, title, "Effect node"); notice = title + " node added" },
                        onRemove = { id -> nodes = nodes.filterNot { it.id == id }; notice = "Node removed" },
                        onBack = { page = "Editor" })
                    else -> ExportScreen(resolution, frameRate, quality,
                        onResolution = { resolution = it }, onFrameRate = { frameRate = it }, onQuality = { quality = it },
                        onBack = { page = "Editor" }, onExport = { showExportInfo = true })
                }
            }
        }
    }

    if (showNewProject) {
        AlertDialog(
            onDismissRequest = { showNewProject = false },
            title = { Text("Create project", color = textMain) },
            text = { OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text("Project name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { projectName = newName.ifBlank { "Untitled Project" }; clips = emptyList(); videoUri = null; videoName = "No media imported"; page = "Editor"; showNewProject = false }) { Text("Create") } },
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
            title = { Text("Export engine not connected yet", color = textMain) },
            text = { Text("Your export settings are saved in this session, but rendering the edited timeline into a video file still needs the media-processing engine. I won't show a fake export-complete message.", color = textDim) },
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
        Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp)).background(gradient).clickable { onNew() }.padding(22.dp)) {
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
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(surface).clickable { onOpen() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp, 54.dp).clip(RoundedCornerShape(12.dp)).background(gradient), contentAlignment = Alignment.Center) { Text("▶", color = Color.White, fontSize = 20.sp) }
            Column(Modifier.weight(1f).padding(start = 13.dp)) {
                Text(projectName, color = textMain, fontWeight = FontWeight.SemiBold)
                Text("Continue editing  ·  Draft", color = textDim, fontSize = 11.sp)
            }
            Text("›", color = violet, fontSize = 28.sp)
        }
        Spacer(Modifier.height(26.dp))
        Text("A timeline for speed. A node graph for control.", color = textDim, fontSize = 11.sp)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun QuickAction(title: String, icon: String, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.height(62.dp).clip(RoundedCornerShape(16.dp)).background(surface).clickable { onClick() }.padding(horizontal = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(icon, color = violet, fontSize = 23.sp)
        Text(title, color = textMain, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 9.dp))
    }
}

@Composable
private fun EditorScreen(
    project: String, videoUri: Uri?, videoName: String, playing: Boolean, clips: List<ClipItem>,
    selectedClip: Int, speed: String, notice: String, onBack: () -> Unit, onImport: () -> Unit,
    onPlay: () -> Unit, onSelectClip: (Int) -> Unit, onSplit: () -> Unit, onDelete: () -> Unit,
    onSpeed: () -> Unit, onAddLayer: () -> Unit, onEffects: () -> Unit, onNodes: () -> Unit, onExport: () -> Unit
) {
    val videoView = remember { mutableStateOf<VideoView?>(null) }
    LaunchedEffect(playing, videoUri) {
        val view = videoView.value
        if (view != null && videoUri != null) {
            if (playing) view.start() else view.pause()
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
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF05070C)), contentAlignment = Alignment.Center) {
            if (videoUri != null) {
                AndroidView(factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(videoUri)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            val rate = speed.removeSuffix("×").toFloatOrNull() ?: 1f
                            mp.setPlaybackParams(android.media.PlaybackParams().setSpeed(rate))
                            if (playing) start()
                        }
                        videoView.value = this
                    }
                }, update = { view ->
                    if (view.tag != videoUri.toString()) {
                        view.tag = videoUri.toString()
                        view.setVideoURI(videoUri)
                    }
                }, modifier = Modifier.fillMaxSize())
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
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("00:00:00", color = textMain, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).clip(CircleShape).background(violet).clickable { onPlay() }, contentAlignment = Alignment.Center) { Text(if (playing) "Ⅱ" else "▶", color = Color.White, fontSize = 19.sp) }
            Text("00:15:00", color = textDim, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
        Spacer(Modifier.height(15.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TIMELINE", color = textMain, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.weight(1f))
            Text(notice, color = cyan, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            ToolPill("＋ Layer", onAddLayer)
        }
        Spacer(Modifier.height(8.dp))
        Column(Modifier.fillMaxWidth().heightIn(min = 142.dp, max = 205.dp).clip(RoundedCornerShape(16.dp)).background(surface).padding(10.dp).verticalScroll(rememberScrollState())) {
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
                        .clickable { onSelectClip(clip.id) }.padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) {
                        Text(clip.title, color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (clips.isEmpty()) Text("Add a layer to begin building your timeline.", color = textDim, fontSize = 10.sp, modifier = Modifier.padding(10.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditorTool("Split", Icons.Outlined.ContentCut, onSplit)
            EditorTool("Delete", Icons.Outlined.DeleteOutline, onDelete)
            EditorTool("Speed " + speed, Icons.Outlined.Speed, onSpeed)
            EditorTool("Effects", Icons.Outlined.AutoFixHigh, onEffects)
            EditorTool("Nodes", Icons.Outlined.AccountTree, onNodes)
            EditorTool("Import", Icons.Outlined.FileUpload, onImport)
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun EditorTool(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.widthIn(min = 68.dp), shape = RoundedCornerShape(14.dp), color = surface2, border = androidx.compose.foundation.BorderStroke(1.dp, line)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = label, tint = violet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(5.dp))
            Text(label, color = textMain, fontSize = 9.sp, fontWeight = FontWeight.Medium, maxLines = 1)
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
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(surface).clickable { onToggle(name) }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
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
        Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(surface).padding(14.dp)) {
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
            OutlinedButton(onClick = { showMenu = true }, modifier = Modifier.fillMaxWidth()) { Text("＋ Add node") }
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
private fun ExportScreen(resolution: String, frameRate: String, quality: String, onResolution: (String) -> Unit, onFrameRate: (String) -> Unit, onQuality: (String) -> Unit, onBack: () -> Unit, onExport: () -> Unit) {
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
        Text("Settings are interactive. Video rendering is not connected yet.", color = textDim, fontSize = 10.sp)
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
            OutlinedButton(onClick = { expanded = true }) { Text(value) }
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
    Surface(onClick = onClick, modifier = Modifier.size(42.dp), shape = CircleShape, color = surface2, border = androidx.compose.foundation.BorderStroke(1.dp, line)) {
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
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp), elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 2.dp), colors = ButtonDefaults.buttonColors(containerColor = violet, contentColor = Color.White)) {
        Text(label, color = Color.White, fontSize = 13.sp, letterSpacing = .2.sp, fontWeight = FontWeight.Bold)
    }
}
