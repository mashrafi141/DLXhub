package com.example

import com.example.model.PlatformType
import com.example.storage.StorageDestinationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DLXhubUnitTest {

    @Test
    fun testFilenameSanitization() {
        val raw = "Test/Video:Name*With?Invalid\"Chars|And<Spaces>   "
        val safe = StorageDestinationManager.sanitizeFilename(raw)
        assertEquals("Test_Video_Name_With_Invalid_Chars_And_Spaces", safe)
    }

    @Test
    fun testPlatformDetection() {
        val rgUrl = "https://www.redgifs.com/watch/clevercutepanda"
        val xfUrl = "https://xxxfollow.com/video/12345"
        val fbUrl = "https://www.facebook.com/watch/?v=987654321"
        val xUrl = "https://x.com/username/status/1234567890"
        val ytUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

        assertEquals(PlatformType.REDGIFS, detectPlatform(rgUrl))
        assertEquals(PlatformType.XXXFOLLOW, detectPlatform(xfUrl))
        assertEquals(PlatformType.FACEBOOK, detectPlatform(fbUrl))
        assertEquals(PlatformType.TWITTER, detectPlatform(xUrl))
        assertEquals(PlatformType.YOUTUBE, detectPlatform(ytUrl))
    }

    private fun detectPlatform(url: String): PlatformType? {
        val lower = url.lowercase().trim()
        return when {
            lower.contains("redgifs.com") || lower.contains("redgifs") -> PlatformType.REDGIFS
            lower.contains("xxxfollow.com") || lower.contains("xxxfollow") -> PlatformType.XXXFOLLOW
            lower.contains("facebook.com") || lower.contains("fb.watch") -> PlatformType.FACEBOOK
            lower.contains("twitter.com") || lower.contains("x.com") -> PlatformType.TWITTER
            lower.contains("youtube.com") || lower.contains("youtu.be") -> PlatformType.YOUTUBE
            else -> null
        }
    }
}
