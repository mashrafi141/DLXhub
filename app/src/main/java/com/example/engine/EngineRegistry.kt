package com.example.engine

import android.content.Context
import com.example.model.PlatformType
import com.example.storage.CookieManager
import com.example.storage.StorageDestinationManager

class EngineRegistry(
    context: Context,
    destinationManager: StorageDestinationManager,
    cookieManager: CookieManager
) {
    val engines: List<DownloaderEngine> = PlatformType.values().map {
        PythonEngine(context, destinationManager, cookieManager, it)
    }

    fun resolveEngine(url: String): DownloaderEngine? = engines.firstOrNull { it.supports(url) }

    fun detectPlatform(url: String): PlatformType? {
        val host = runCatching { java.net.URI(url.trim().let { if (it.startsWith("http", true)) it else "https://$it" }).host?.lowercase().orEmpty() }.getOrDefault("")
        return when {
            host == "redgifs.com" || host.endsWith(".redgifs.com") -> PlatformType.REDGIFS
            host == "xxxfollow.com" || host.endsWith(".xxxfollow.com") -> PlatformType.XXXFOLLOW
            host == "facebook.com" || host.endsWith(".facebook.com") || host == "fb.watch" -> PlatformType.FACEBOOK
            host == "twitter.com" || host.endsWith(".twitter.com") || host == "x.com" || host.endsWith(".x.com") -> PlatformType.TWITTER
            host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be" || host.endsWith(".youtu.be") -> PlatformType.YOUTUBE
            else -> null
        }
    }
}
