package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
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
import com.example.coordinator.DownloadCoordinator
import com.example.model.DownloadItem
import com.example.model.DownloadState
import com.example.model.MediaMetadata
import com.example.model.PlatformType
import com.example.ui.components.DownloadProgressBar
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.NeonButton
import com.example.ui.components.PlatformChip
import com.example.ui.theme.DarkBorderGlass
import com.example.ui.theme.DarkBorderSubtle
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceGlass
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonViolet
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VibrantRed

@Composable
fun DashboardScreen(
    coordinator: DownloadCoordinator,
    onInspectRequested: (MediaMetadata) -> Unit,
    onNavigateToQueue: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var inputUrl by remember { mutableStateOf("") }
    var isProbing by remember { mutableStateOf(false) }
    var probeError by remember { mutableStateOf<String?>(null) }
    var playlistMode by remember { mutableStateOf(false) }

    val activeItem by coordinator.activeDownload.collectAsState()
    val queue by coordinator.queue.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            // Hero Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DLXhub",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = NeonCyan,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Universal Media Hub · Ultra High Speed",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, DarkBorderGlass, RoundedCornerShape(12.dp))
                        .clickable { onNavigateToQueue() }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Queue",
                            tint = NeonCyan,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Queue: ${queue.size}",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Active Download Card (if active)
        if (activeItem != null) {
            item {
                ActiveDownloadCard(
                    item = activeItem!!,
                    onCancel = { coordinator.cancel(activeItem!!.id) }
                )
            }
        }

        // Input Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (playlistMode) "Paste Playlist Link" else "Paste Media Link",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        val detected = coordinator.engineRegistry.detectPlatform(inputUrl)
                        if (detected != null) {
                            PlatformChip(platform = detected)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    GlassTextField(
                        value = inputUrl,
                        onValueChange = {
                            inputUrl = it
                            probeError = null
                        },
                        placeholder = if (playlistMode) "https://www.youtube.com/playlist?list=..." else "https://www.youtube.com/watch?v=...",
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Row {
                                if (inputUrl.isNotBlank()) {
                                    IconButton(onClick = { inputUrl = ""; probeError = null }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                                    }
                                }
                                IconButton(onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                    val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                    if (!clip.isNullOrBlank()) {
                                        inputUrl = clip.trim()
                                        probeError = null
                                    }
                                }) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = NeonCyan)
                                }
                            }
                        }
                    )

                    if (probeError != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = probeError!!,
                            color = StatusError,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    NeonButton(
                        text = if (isProbing) {
                            if (playlistMode) "RESOLVING PLAYLIST..." else "RESOLVING MEDIA..."
                        } else {
                            if (playlistMode) "INSPECT PLAYLIST" else "INSPECT & DOWNLOAD"
                        },
                        enabled = inputUrl.isNotBlank() && !isProbing,
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = {
                            if (isProbing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = NeonCyan
                                )
                            } else {
                                Icon(
                                    if (playlistMode) Icons.Default.PlaylistPlay else Icons.Default.Download,
                                    contentDescription = null,
                                    tint = TextPrimary
                                )
                            }
                        },
                        onClick = {
                            isProbing = true
                            probeError = null
                            coroutineScope.launch {
                                val result = if (playlistMode) {
                                    coordinator.probePlaylistUrl(inputUrl.trim())
                                } else {
                                    coordinator.probeUrl(inputUrl.trim())
                                }
                                isProbing = false
                                if (result.isSuccess) {
                                    onInspectRequested(result.getOrThrow())
                                } else {
                                    probeError = result.exceptionOrNull()?.message ?:
                                        if (playlistMode) "Failed to resolve playlist" else "Failed to resolve media stream"
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    NeonButton(
                        text = if (playlistMode) "BACK TO MEDIA DOWNLOADER" else "PLAYLIST DOWNLOADER",
                        enabled = !isProbing,
                        onClick = {
                            playlistMode = !playlistMode
                            probeError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        accentColor = if (playlistMode) NeonViolet else NeonCyan,
                        leadingIcon = {
                            Icon(
                                if (playlistMode) Icons.Default.Download else Icons.Default.PlaylistPlay,
                                contentDescription = null,
                                tint = TextPrimary
                            )
                        }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActiveDownloadCard(
    item: DownloadItem,
    onCancel: () -> Unit
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = NeonCyan.copy(alpha = 0.6f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlatformChip(platform = item.platform)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.title,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = VibrantRed)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val speedFormatted = DownloadCoordinator.formatBytes(item.progress.speedBytesPerSec) + "/s"
            val etaFormatted = if (item.progress.etaSeconds != null) DownloadCoordinator.formatTime(item.progress.etaSeconds) else ""

            DownloadProgressBar(
                percentage = item.progress.percentage,
                speedText = speedFormatted,
                etaText = etaFormatted,
                accentColor = item.platform.primaryColor
            )
        }
    }
}
