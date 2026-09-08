package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Queue
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.coordinator.DownloadCoordinator
import com.example.model.DownloadItem
import com.example.model.DownloadState
import com.example.model.DownloadType
import com.example.model.QualityOption
import com.example.ui.components.DownloadProgressBar
import com.example.ui.components.GlassCard
import com.example.ui.components.GlassTextField
import com.example.ui.components.NeonButton
import com.example.ui.components.PlatformChip
import com.example.ui.theme.DarkBorderGlass
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceGlass
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VibrantRed

@Composable
fun QueueScreen(
    coordinator: DownloadCoordinator,
    onNavigateToDashboard: () -> Unit
) {
    val queue by coordinator.queue.collectAsState()
    var showBatchDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Download Queue",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "${queue.count { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED }} active · ${queue.size} total items",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurfaceElevated)
                    .border(1.dp, DarkBorderGlass, RoundedCornerShape(12.dp))
                    .clickable { showBatchDialog = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Batch URLs", color = NeonCyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (queue.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated)
                            .border(1.dp, DarkBorderGlass, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Queue,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Downloads in Queue",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Paste a link on the Hub to start downloading media at full speed.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    NeonButton(
                        text = "Go to Hub",
                        onClick = onNavigateToDashboard,
                        modifier = Modifier.width(180.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(queue, key = { it.id }) { item ->
                    QueueItemCard(
                        item = item,
                        onCancel = { coordinator.cancel(item.id) },
                        onRetry = { coordinator.retry(item.id) },
                        onRemove = { coordinator.remove(item.id) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    if (showBatchDialog) {
        BatchImportDialog(
            onDismiss = { showBatchDialog = false },
            onConfirm = { urls, type ->
                coordinator.enqueueBatch(
                    urls = urls,
                    type = type,
                    quality = QualityOption(
                        id = "best",
                        label = "Best Available",
                        resolutionOrBitrate = "Best",
                        format = if (type == DownloadType.AUDIO) "MP3" else "MP4",
                        isBest = true
                    )
                )
                showBatchDialog = false
            }
        )
    }
}

@Composable
private fun QueueItemCard(
    item: DownloadItem,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit
) {
    val borderColor = when (item.state) {
        DownloadState.DOWNLOADING -> item.platform.primaryColor.copy(alpha = 0.6f)
        DownloadState.COMPLETED -> StatusSuccess.copy(alpha = 0.5f)
        DownloadState.FAILED -> StatusError.copy(alpha = 0.5f)
        else -> DarkBorderGlass
    }

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        borderColor = borderColor
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                when (item.state) {
                    DownloadState.DOWNLOADING -> {
                        IconButton(onClick = onCancel, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel", tint = VibrantRed)
                        }
                    }
                    DownloadState.FAILED, DownloadState.CANCELLED -> {
                        Row {
                            IconButton(onClick = onRetry, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Refresh, contentDescription = "Retry", tint = NeonCyan)
                            }
                            IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextSecondary)
                            }
                        }
                    }
                    DownloadState.COMPLETED -> {
                        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextSecondary)
                        }
                    }
                    DownloadState.QUEUED -> {
                        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = TextSecondary)
                        }
                    }
                    else -> {}
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${item.type.name} · ${item.qualityLabel}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.weight(1f))
                StateBadge(state = item.state)
            }

            if (item.state == DownloadState.DOWNLOADING) {
                Spacer(modifier = Modifier.height(10.dp))
                val speedFormatted = DownloadCoordinator.formatBytes(item.progress.speedBytesPerSec) + "/s"
                val etaFormatted = if (item.progress.etaSeconds != null) DownloadCoordinator.formatTime(item.progress.etaSeconds) else ""
                DownloadProgressBar(
                    percentage = item.progress.percentage,
                    speedText = speedFormatted,
                    etaText = etaFormatted,
                    accentColor = item.platform.primaryColor
                )
            } else if (item.state == DownloadState.FAILED && !item.errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.errorMessage,
                    color = StatusError,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StateBadge(state: DownloadState) {
    val (label, color) = when (state) {
        DownloadState.QUEUED -> "QUEUED" to TextSecondary
        DownloadState.RESOLVING -> "RESOLVING" to StatusWarning
        DownloadState.READY -> "READY" to StatusInfoColor
        DownloadState.DOWNLOADING -> "DOWNLOADING" to NeonCyan
        DownloadState.PROCESSING -> "PROCESSING" to ElectricBlue
        DownloadState.COMPLETED -> "COMPLETED" to StatusSuccess
        DownloadState.FAILED -> "FAILED" to StatusError
        DownloadState.CANCELLED -> "CANCELLED" to TextSecondary
    }

    Text(
        text = label,
        color = color,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private val StatusInfoColor = Color(0xFF38BDF8)

@Composable
private fun BatchImportDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<String>, DownloadType) -> Unit
) {
    var rawText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(DownloadType.VIDEO) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceElevated,
        title = {
            Text(
                text = "Batch Download URLs",
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter one media URL per line. All supported platforms will be automatically detected and queued.",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                TabRow(
                    selectedTabIndex = if (selectedType == DownloadType.VIDEO) 0 else 1,
                    containerColor = DarkSurfaceGlass,
                    contentColor = NeonCyan,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = selectedType == DownloadType.VIDEO,
                        onClick = { selectedType = DownloadType.VIDEO },
                        text = { Text("Video (MP4)", fontSize = 12.sp) }
                    )
                    Tab(
                        selected = selectedType == DownloadType.AUDIO,
                        onClick = { selectedType = DownloadType.AUDIO },
                        text = { Text("Audio (MP3)", fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                GlassTextField(
                    value = rawText,
                    onValueChange = { rawText = it },
                    placeholder = "https://...\nhttps://...",
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lines = rawText.lines().map { it.trim() }.filter { it.isNotBlank() }
                    if (lines.isNotEmpty()) {
                        onConfirm(lines, selectedType)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
            ) {
                Text("Queue All", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        }
    )
}
