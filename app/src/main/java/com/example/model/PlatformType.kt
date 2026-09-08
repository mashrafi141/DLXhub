package com.example.model

import androidx.compose.ui.graphics.Color

enum class PlatformType(
    val id: String,
    val displayName: String,
    val shortCode: String,
    val primaryColor: Color,
    val folderName: String
) {
    REDGIFS(
        id = "rg",
        displayName = "RedGIFs",
        shortCode = "RG",
        primaryColor = Color(0xFFFF8A00),
        folderName = ".RG_downloads"
    ),
    XXXFOLLOW(
        id = "xf",
        displayName = "XXXFollow",
        shortCode = "XF",
        primaryColor = Color(0xFFD946EF),
        folderName = ".XF_downloads"
    ),
    FACEBOOK(
        id = "fb",
        displayName = "Facebook",
        shortCode = "FB",
        primaryColor = Color(0xFF2563EB),
        folderName = "FB_downloads"
    ),
    TWITTER(
        id = "x",
        displayName = "Twitter / X",
        shortCode = "X",
        primaryColor = Color(0xFF06B6D4),
        folderName = "X_downloads"
    ),
    YOUTUBE(
        id = "yt",
        displayName = "YouTube",
        shortCode = "YT",
        primaryColor = Color(0xFFEF4444),
        folderName = "YT_downloads"
    );

    companion object {
        fun fromId(id: String): PlatformType =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: YOUTUBE
    }
}
