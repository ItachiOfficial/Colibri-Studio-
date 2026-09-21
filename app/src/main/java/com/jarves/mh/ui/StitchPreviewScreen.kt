package com.jarves.mh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jarves.mh.model.StitchDesignScreen
import com.jarves.mh.model.StitchPreviewState
import com.jarves.mh.model.StitchPreviewStatus

/**
 * Full-screen design-preview flow: prompt → generated screens (multi-select, in
 * pick order) → optional fullscreen swipe review → hand off to the agent. See
 * /areas/mobile-harness-fork.md for why this exists (no live app webview like AI
 * Studio) and the reliability caveats around Stitch itself — every state here has to
 * degrade to a usable "skip and code directly" path, not just the happy one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StitchPreviewScreen(
    preview: StitchPreviewState,
    onClose: () -> Unit,
    onGenerate: (String) -> Unit,
    onToggleSelect: (String) -> Unit,
    onOpenFullscreen: (String?) -> Unit,
    onLoadHtml: (String) -> Unit,
    onBuild: () -> Unit,
    onSkip: () -> Unit,
) {
    preview.fullscreenScreenId?.let { screenId ->
        val screen = preview.screens.firstOrNull { it.id == screenId }
        if (screen != null) {
            StitchFullscreenReview(
                screens = preview.screens,
                current = screen,
                selectedIds = preview.selectedScreenIds,
                onNavigate = { onOpenFullscreen(it) },
                onClose = { onOpenFullscreen(null) },
                onToggleSelect = onToggleSelect,
                onLoadHtml = onLoadHtml,
            )
            return
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Design preview", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text("Powered by Stitch · before we write code", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            var draft by remember { mutableStateOf(preview.prompt) }

            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Describe the screens you want") },
                    placeholder = { Text("A plant care app with a watering log, dark theme, green accents") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                )
                Button(
                    onClick = { onGenerate(draft) },
                    enabled = draft.isNotBlank() && preview.status != StitchPreviewStatus.GENERATING,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (preview.status == StitchPreviewStatus.GENERATING) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                        Spacer(Modifier.width(8.dp))
                        Text("Generating…")
                    } else {
                        Icon(Icons.Default.Route, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (preview.screens.isEmpty()) "Generate screens" else "Regenerate")
                    }
                }
            }

            when (preview.status) {
                StitchPreviewStatus.FAILED -> StitchErrorState(preview.errorMessage, onSkip)
                StitchPreviewStatus.READY -> StitchScreenGallery(
                    preview = preview,
                    onToggleSelect = onToggleSelect,
                    onOpenFullscreen = { onOpenFullscreen(it) },
                    onBuild = onBuild,
                    onSkip = onSkip,
                )
                StitchPreviewStatus.GENERATING, StitchPreviewStatus.IDLE -> {
                    if (preview.status == StitchPreviewStatus.IDLE) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Describe your app above and generate a few screens to review before the agent starts coding.",
                                fontSize = 12.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                            OutlinedButton(onClick = onSkip) {
                                Icon(Icons.Default.Message, null, Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Skip preview, just start coding")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StitchErrorState(message: String?, onSkip: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            message ?: "Stitch couldn't complete that generation.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        OutlinedButton(onClick = onSkip) {
            Icon(Icons.Default.Message, null, Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("Skip preview, just start coding")
        }
    }
}

@Composable
private fun StitchScreenGallery(
    preview: StitchPreviewState,
    onToggleSelect: (String) -> Unit,
    onOpenFullscreen: (String) -> Unit,
    onBuild: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("${preview.screens.size}-screen flow generated", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Text(
            "Tap to open full size · check the boxes to include in the build",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(preview.screens, key = StitchDesignScreen::id) { screen ->
                val order = preview.selectedScreenIds.indexOf(screen.id)
                StitchScreenThumbnail(
                    screen = screen,
                    selectedOrder = order.takeIf { it >= 0 }?.plus(1),
                    onTap = { onOpenFullscreen(screen.id) },
                    onToggle = { onToggleSelect(screen.id) },
                )
            }
        }

        if (preview.selectedScreenIds.size >= 2) {
            val orderedTitles = preview.selectedScreenIds
                .mapNotNull { id -> preview.screens.firstOrNull { it.id == id }?.title }
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Route, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Flow: ${orderedTitles.joinToString(" → ")}", fontSize = 11.5.sp)
                }
            }
        }

        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Button(
                onClick = onBuild,
                enabled = preview.selectedScreenIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Code, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Build this flow (${preview.selectedScreenIds.size} screen${if (preview.selectedScreenIds.size == 1) "" else "s"} selected)")
            }
            Text(
                "Image + HTML/CSS for each screen sent to Claude Code as reference",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Message, null, Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("Skip preview, just start coding")
            }
        }
    }
}

@Composable
private fun StitchScreenThumbnail(
    screen: StitchDesignScreen,
    selectedOrder: Int?,
    onTap: () -> Unit,
    onToggle: () -> Unit,
) {
    val selected = selectedOrder != null
    Column(Modifier.width(150.dp)) {
        Box(
            Modifier
                .size(150.dp, 200.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(
                    if (selected) 1.5.dp else 0.5.dp,
                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(12.dp),
                ),
        ) {
            if (screen.imageUrl != null) {
                AsyncImage(
                    model = screen.imageUrl,
                    contentDescription = screen.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)).clickable { onTap() },
                )
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
            Box(
                Modifier
                    .padding(7.dp)
                    .align(Alignment.TopEnd)
                    .size(21.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .border(1.5.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                    .clickable { onToggle() },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Default.Check, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            selectedOrder?.let { order ->
                Surface(
                    Modifier.padding(7.dp).align(Alignment.BottomStart),
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                ) {
                    Text(order.toString(), Modifier.padding(horizontal = 7.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        Text(screen.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun StitchFullscreenReview(
    screens: List<StitchDesignScreen>,
    current: StitchDesignScreen,
    selectedIds: List<String>,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
    onToggleSelect: (String) -> Unit,
    onLoadHtml: (String) -> Unit,
) {
    val index = screens.indexOf(current)
    val isSelected = current.id in selectedIds
    var showHtml by remember(current.id) { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (current.imageUrl != null) {
            AsyncImage(
                model = current.imageUrl,
                contentDescription = current.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(current.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Color.White)
                Text("Screen ${index + 1} of ${screens.size}", fontSize = 10.5.sp, color = Color.White.copy(alpha = 0.75f))
            }
            Spacer(Modifier.size(48.dp))
        }

        if (index > 0) {
            IconButton(
                onClick = { onNavigate(screens[index - 1].id) },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 6.dp),
            ) { Icon(Icons.Default.ArrowBackIosNew, "Previous", tint = Color.White) }
        }
        if (index < screens.lastIndex) {
            IconButton(
                onClick = { onNavigate(screens[index + 1].id) },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
            ) { Icon(Icons.Default.ArrowForwardIos, "Next", tint = Color.White) }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
                .padding(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                screens.forEach { s ->
                    Box(
                        Modifier
                            .size(46.dp, 62.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.5.dp, if (s.id == current.id) MaterialTheme.colorScheme.primary else Color(0xFF3A4149), RoundedCornerShape(8.dp))
                            .clickable { onNavigate(s.id) },
                    ) {
                        if (s.imageUrl != null) {
                            AsyncImage(
                                model = s.imageUrl,
                                contentDescription = s.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                alpha = if (s.id == current.id) 1f else 0.6f,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { showHtml = true; onLoadHtml(current.id) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                ) {
                    Icon(Icons.Default.Code, null, Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("View HTML")
                }
                Button(
                    onClick = { onToggleSelect(current.id) },
                    modifier = Modifier.weight(1f),
                    colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White,
                    ),
                ) {
                    Icon(Icons.Default.Check, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isSelected) "Included in flow" else "Include in flow")
                }
            }
        }

        if (showHtml) {
            Surface(
                Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
            ) {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                        Text("HTML/CSS · ${current.title}", Modifier.weight(1f), fontWeight = FontWeight.Medium, fontSize = 14.sp)
                        IconButton(onClick = { showHtml = false }) { Icon(Icons.Default.Close, "Close") }
                    }
                    if (current.htmlCode == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.height(10.dp))
                                Text("Loading…", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        Surface(
                            Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF0B0F14),
                        ) {
                            Text(
                                current.htmlCode,
                                Modifier.fillMaxSize().padding(12.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.5.sp,
                                color = Color(0xFFE2E6EB),
                            )
                        }
                    }
                }
            }
        }
    }
}
