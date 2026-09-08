package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.coordinator.DownloadCoordinator
import com.example.model.DownloadState
import com.example.model.DownloadType
import com.example.model.MediaMetadata
import com.example.model.QualityOption
import com.example.ui.components.FormatQualitySheet
import com.example.ui.screens.CookiesScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.QueueScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorderGlass
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceGlass
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class Screen(val title: String, val icon: ImageVector) {
    DASHBOARD("Hub", Icons.Default.Dashboard),
    QUEUE("Queue", Icons.Default.Download),
    HISTORY("History", Icons.Default.History),
    COOKIES("Cookies", Icons.Default.Cookie),
    SETTINGS("Settings", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var coordinator: DownloadCoordinator
    private var sharedUrlState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        coordinator = DownloadCoordinator(applicationContext)
        handleSharedIntent(intent)

        setContent {
            MyApplicationTheme {
                MainApp(
                    coordinator = coordinator,
                    initialSharedUrl = sharedUrlState.value,
                    onSharedUrlConsumed = { sharedUrlState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleSharedIntent(intent)
    }

    private fun handleSharedIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                sharedUrlState.value = sharedText.trim()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    coordinator: DownloadCoordinator,
    initialSharedUrl: String?,
    onSharedUrlConsumed: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }

    val queue by coordinator.queue.collectAsState()
    val activeCount = queue.count { it.state == DownloadState.DOWNLOADING || it.state == DownloadState.QUEUED }

    // Inspect Bottom Sheet
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var inspectingMetadata by remember { mutableStateOf<MediaMetadata?>(null) }

    // Permission request for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Auto-probe if opened via Share intent
    LaunchedEffect(initialSharedUrl) {
        if (!initialSharedUrl.isNullOrBlank()) {
            val result = coordinator.probeUrl(initialSharedUrl)
            onSharedUrlConsumed()
            if (result.isSuccess) {
                inspectingMetadata = result.getOrThrow()
            } else {
                Toast.makeText(context, "Could not resolve shared URL: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                tonalElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, DarkBorderGlass, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                    .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            ) {
                Screen.entries.forEach { screen ->
                    val isSelected = currentScreen == screen
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentScreen = screen },
                        modifier = Modifier.testTag("nav_${screen.name.lowercase()}"),
                        icon = {
                            if (screen == Screen.QUEUE && activeCount > 0) {
                                BadgedBox(
                                    badge = {
                                        Badge(
                                            containerColor = NeonCyan,
                                            contentColor = Color.Black
                                        ) {
                                            Text(activeCount.toString(), fontWeight = FontWeight.Bold)
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = screen.icon,
                                        contentDescription = screen.title
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = screen.icon,
                                    contentDescription = screen.title
                                )
                            }
                        },
                        label = {
                            Text(
                                text = screen.title,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = NeonCyan,
                            selectedTextColor = NeonCyan,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary,
                            indicatorColor = DarkSurfaceElevated
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBackground)
        ) {
            Crossfade(
                targetState = currentScreen,
                label = "screen_crossfade"
            ) { screen ->
                when (screen) {
                    Screen.DASHBOARD -> DashboardScreen(
                        coordinator = coordinator,
                        onInspectRequested = { metadata -> inspectingMetadata = metadata },
                        onNavigateToQueue = { currentScreen = Screen.QUEUE }
                    )
                    Screen.QUEUE -> QueueScreen(
                        coordinator = coordinator,
                        onNavigateToDashboard = { currentScreen = Screen.DASHBOARD }
                    )
                    Screen.HISTORY -> HistoryScreen(
                        coordinator = coordinator
                    )
                    Screen.COOKIES -> CookiesScreen(
                        cookieManager = coordinator.cookieManager
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        destinationManager = coordinator.destinationManager
                    )
                }
            }
        }
    }

    // Format & Quality Selection Modal Sheet
    if (inspectingMetadata != null) {
        val meta = inspectingMetadata!!
        FormatQualitySheet(
            sheetState = sheetState,
            metadata = meta,
            destinationPath = coordinator.destinationManager.getDisplayPath(),
            onDismiss = { inspectingMetadata = null },
            onConfirm = { type: DownloadType, quality: QualityOption ->
                if (meta.isPlaylist && meta.playlistEntries.isNotEmpty()) {
                    // Enqueue playlist
                    meta.playlistEntries.forEach { item ->
                        coordinator.enqueueDownload(
                            url = item.url,
                            platform = meta.platform,
                            type = type,
                            quality = quality,
                            title = item.title,
                            playlistName = meta.playlistTitle,
                            itemIndex = item.index,
                            totalItems = meta.playlistEntries.size
                        )
                    }
                    Toast.makeText(context, "Queued ${meta.playlistEntries.size} playlist items", Toast.LENGTH_SHORT).show()
                } else {
                    coordinator.enqueueDownload(
                        url = meta.url,
                        platform = meta.platform,
                        type = type,
                        quality = quality,
                        title = meta.title
                    )
                    Toast.makeText(context, "Download queued: ${meta.title}", Toast.LENGTH_SHORT).show()
                }

                coroutineScope.launch {
                    sheetState.hide()
                    inspectingMetadata = null
                    currentScreen = Screen.QUEUE
                }
            }
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier, color = TextPrimary)
}

