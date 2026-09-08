package com.example.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cookie
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CookieProfile
import com.example.model.PlatformType
import com.example.storage.CookieManager
import com.example.ui.components.GlassCard
import com.example.ui.theme.DarkBorderGlass
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceGlass
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CookiesScreen(
    cookieManager: CookieManager
) {
    val context = LocalContext.current
    var profiles by remember { mutableStateOf(cookieManager.getProfiles()) }
    var selectedPlatformForImport by remember { mutableStateOf<PlatformType?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val platform = selectedPlatformForImport
        if (uri != null && platform != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val ok = cookieManager.importCookieFile(platform, stream)
                    if (ok) {
                        profiles = cookieManager.getProfiles()
                        Toast.makeText(context, "${platform.displayName} cookies imported successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to import cookie file", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cookie Profiles",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = "Netscape cookie files enable access to private/restricted content.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        }

        // Privacy Guarantee Card
        item {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = ElectricBlue.copy(alpha = 0.08f),
                borderColor = ElectricBlue.copy(alpha = 0.3f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "100% Offline & Private",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Your cookies stay encrypted within this application's private storage and are only transmitted directly to the selected media platform to authorize stream requests.",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Platform cookie profiles
        items(profiles.size) { index ->
            val profile = profiles[index]
            CookieProfileCard(
                profile = profile,
                onImportClick = {
                    selectedPlatformForImport = profile.platform
                    filePicker.launch(arrayOf("text/plain", "*/*"))
                },
                onClearClick = {
                    cookieManager.clearCookies(profile.platform)
                    profiles = cookieManager.getProfiles()
                    Toast.makeText(context, "${profile.platform.displayName} cookies cleared", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Instructions Card
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "How to Export Netscape Cookies",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "1. In Chrome / Firefox, install a cookie exporter extension (such as 'Get cookies.txt LOCALLY').\n" +
                               "2. Navigate to the website (e.g. Facebook or X) while logged in.\n" +
                               "3. Export cookies in standard Netscape format (.txt).\n" +
                               "4. Transfer or select the file here via the Import button.",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
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
private fun CookieProfileCard(
    profile: CookieProfile,
    onImportClick: () -> Unit,
    onClearClick: () -> Unit
) {
    val dateStr = if (profile.lastModified > 0L) {
        SimpleDateFormat("MMM d, yyyy · HH:mm", Locale.getDefault()).format(Date(profile.lastModified))
    } else null

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = DarkSurfaceGlass,
        borderColor = if (profile.isConfigured) profile.platform.primaryColor.copy(alpha = 0.4f) else DarkBorderGlass
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(profile.platform.primaryColor.copy(alpha = 0.16f))
                        .border(1.dp, profile.platform.primaryColor.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Cookie,
                        contentDescription = null,
                        tint = profile.platform.primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.platform.displayName,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = profile.fileName,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                if (profile.isConfigured) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(StatusSuccess.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Connected",
                            color = StatusSuccess,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(TextSecondary.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "Not Configured",
                            color = TextSecondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (profile.isConfigured) {
                Text(
                    text = "${profile.entryCount} cookies loaded${if (dateStr != null) " · Updated $dateStr" else ""}",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            } else {
                Text(
                    text = "Import ${profile.fileName} to download age-restricted or private posts.",
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row {
                Button(
                    onClick = onImportClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = profile.platform.primaryColor.copy(alpha = 0.2f),
                        contentColor = profile.platform.primaryColor
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import .txt", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (profile.isConfigured) {
                    Spacer(modifier = Modifier.width(10.dp))
                    OutlinedButton(
                        onClick = onClearClick,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = StatusError, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear", color = StatusError, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
