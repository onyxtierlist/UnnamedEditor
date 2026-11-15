package com.unnamededitor.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val bg = Color(0xFF090B14)
private val panel = Color(0xFF171C2B)
private val purple = Color(0xFF9560FF)
private val white = Color(0xFFF4F2FF)
private val muted = Color(0xFF9CA4BD)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EditorApp() }
    }
}

@Composable
fun EditorApp() {
    var page by remember { mutableStateOf("Home") }
    var project by remember { mutableStateOf("Untitled project") }
    MaterialTheme(colorScheme = darkColorScheme(primary = purple, background = bg, surface = panel)) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            when (page) {
                "Home" -> Home(
                    onNew = { project = "Untitled project"; page = "Edit" },
                    onOpen = { project = "Project_12"; page = "Edit" },
                    onEffects = { page = "Effects" }
                )
                "Edit" -> Edit(project, { page = "Nodes" }, { page = "Effects" }, { page = "Export" }, { page = "Home" })
                "Nodes" -> SimplePage("Node Editor", "VIDEO INPUT  →  COLOR ADJUST  →  BLUR / GLOW  →  COMPOSITE", listOf("Video Input", "Color Adjust", "Blur", "Glow", "Composite"), { page = "Edit" })
                "Effects" -> SimplePage("Effects", "Choose an effect to add to your composition.", listOf("Color Adjust", "Curves", "Gaussian Blur", "Motion Blur", "Glow", "Chromatic Aberration", "Displacement", "Mask", "3D Transform"), { page = "Edit" })
                else -> ExportPage { page = "Edit" }
            }
        }
    }
}

@Composable
private fun Header(title: String, action: String = "", onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = white, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        if (action.isNotEmpty()) Text(action, color = purple, modifier = Modifier.clickable { onAction() })
    }
}

@Composable
private fun Home(onNew: () -> Unit, onOpen: () -> Unit, onEffects: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Header("UnnamedEditor", "⚙")
        Box(Modifier.fillMaxWidth().padding(horizontal = 18.dp).height(170.dp).background(Brush.linearGradient(listOf(Color(0xFF3920A0), Color(0xFF175B9C), Color(0xFF171A3D))), RoundedCornerShape(22.dp)).clickable { onNew() }, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("＋", color = white, fontSize = 42.sp)
                Text("New Project", color = white, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Start creating something", color = Color.LightGray, fontSize = 12.sp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Auto Cut", Modifier.weight(1f), onNew)
            ActionTile("Templates", Modifier.weight(1f), onNew)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile("Projects", Modifier.weight(1f), onOpen)
            ActionTile("Explore Effects", Modifier.weight(1f), onEffects)
        }
        Header("Recent projects", "See all", onOpen)
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf("Project_12", "Edit_11", "Project_10").forEach { name ->
                Column(Modifier.width(145.dp).clickable { onOpen() }) {
                    Box(Modifier.fillMaxWidth().height(85.dp).background(Brush.linearGradient(listOf(Color(0xFF47328E), Color(0xFF236E8A))), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Text("▶", color = white, fontSize = 25.sp) }
                    Text(name, color = white, modifier = Modifier.padding(top = 6.dp), fontWeight = FontWeight.SemiBold)
                    Text("00:00:32", color = muted, fontSize = 11.sp)
                }
            }
        }
        Text("CapCut-like simplicity. Node Video-level power.", color = muted, modifier = Modifier.padding(20.dp), fontSize = 12.sp)
    }
}

@Composable
private fun ActionTile(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.height(55.dp).background(panel, RoundedCornerShape(14.dp)).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Text(label, color = white, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Edit(project: String, nodes: () -> Unit, effects: () -> Unit, export: () -> Unit, home: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("‹", color = white, fontSize = 28.sp, modifier = Modifier.clickable { home() })
            Text(project, Modifier.weight(1f).padding(start = 12.dp), color = white, fontWeight = FontWeight.SemiBold)
            Text("1080p  ↑", color = purple, modifier = Modifier.clickable { export() })
        }
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 14.dp).background(Brush.linearGradient(listOf(Color(0xFF35277D), Color(0xFF8755A8), Color(0xFF1B6B87))), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
            Text("▶", color = white, fontSize = 52.sp)
        }
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("00:00", color = white); Text("⏮       ▶       ⏭", color = white, fontSize = 20.sp); Text("00:15", color = muted)
        }
        Column(Modifier.fillMaxWidth().background(Color(0xFF101421)).padding(12.dp)) {
            Text("TIMELINE     ＋ Add layer", color = muted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text("00:00             00:05             00:10", color = muted, fontSize = 10.sp, modifier = Modifier.padding(vertical = 8.dp))
            listOf("VIDEO", "AUDIO", "TEXT", "FX").forEachIndexed { i, label ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = muted, fontSize = 9.sp, modifier = Modifier.width(42.dp))
                    Box(Modifier.weight(1f).height(26.dp).background(listOf(Color(0xFF394D9C), Color(0xFF313D91), Color(0xFF376EAB), Color(0xFF6842A5))[i], RoundedCornerShape(5.dp)))
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            listOf("Split", "Speed", "Animation", "Effects", "Nodes", "Export").forEach { label ->
                Column(Modifier.width(64.dp).clickable { when (label) { "Nodes", "Animation" -> nodes(); "Effects" -> effects(); "Export" -> export() } }.padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(when (label) { "Split" -> "✂"; "Speed" -> "◷"; "Animation" -> "⌁"; "Effects" -> "✧"; "Nodes" -> "◇"; else -> "↑" }, color = purple, fontSize = 22.sp)
                    Text(label, color = muted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun SimplePage(title: String, subtitle: String, items: List<String>, back: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Header(title, "Done", back)
        Text(subtitle, color = muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp))
        if (title == "Node Editor") {
            Box(Modifier.fillMaxWidth().weight(1f).padding(14.dp).background(Color(0xFF101522), RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    items.forEachIndexed { i, item ->
                        Box(Modifier.width(210.dp).border(1.dp, purple, RoundedCornerShape(12.dp)).background(panel, RoundedCornerShape(12.dp)).padding(16.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(item, color = white, fontWeight = FontWeight.SemiBold)
                                Text(if (i == 0) "Media source" else "Effect node", color = muted, fontSize = 10.sp)
                            }
                        }
                        if (i != items.lastIndex) Text("↓", color = purple, fontSize = 20.sp)
                    }
                }
            }
        } else {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(14.dp)) {
                items.forEach { item ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).background(panel, RoundedCornerShape(12.dp)).clickable { }.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✧", color = purple, fontSize = 20.sp)
                        Text(item, color = white, modifier = Modifier.weight(1f).padding(start = 12.dp))
                        Text("+", color = muted)
                    }
                }
            }
        }
        Button(onClick = back, modifier = Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = purple)) { Text("Back to editor") }
    }
}

@Composable
private fun ExportPage(back: () -> Unit) {
    var resolution by remember { mutableStateOf("1080p") }
    var fps by remember { mutableStateOf("60 FPS") }
    Column(Modifier.fillMaxSize()) {
        Header("Export", "Close", back)
        Box(Modifier.fillMaxWidth().padding(18.dp).height(180.dp).background(Brush.linearGradient(listOf(Color(0xFF49328E), Color(0xFF1A6A85))), RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) { Text("VIDEO PREVIEW", color = white, fontWeight = FontWeight.Bold) }
        listOf("Resolution" to resolution, "Frame rate" to fps).forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = white, modifier = Modifier.weight(1f))
                Text(value + "  ▾", color = white, modifier = Modifier.clickable { if (label == "Resolution") resolution = if (resolution == "1080p") "720p" else "1080p" else fps = if (fps == "60 FPS") "30 FPS" else "60 FPS" }.background(panel, RoundedCornerShape(9.dp)).padding(12.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Export is a UI placeholder in this prototype.", color = muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Button(onClick = back, modifier = Modifier.fillMaxWidth().padding(16.dp), colors = ButtonDefaults.buttonColors(containerColor = purple)) { Text("Back to editor") }
    }
}
