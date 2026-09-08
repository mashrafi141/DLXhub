package com.example.storage

import android.content.Context
import android.util.Base64
import com.example.model.CookieProfile
import com.example.model.PlatformType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Local cookie vault.
 *
 * Cookies are normalized to Netscape format, encrypted at rest with an
 * AndroidKeyStore AES-GCM key, and materialized only to an app-private
 * temporary file when the Python engine needs a cookiefile path.
 */
class CookieManager(private val context: Context) {
    private val encryptedDir: File
        get() = File(context.filesDir, "secure_cookies").apply { if (!exists()) mkdirs() }

    private val runtimeDir: File
        get() = File(context.cacheDir, "python_cookies").apply { if (!exists()) mkdirs() }

    private val keyAlias = "DLXhub.CookieVault"

    private fun getEncryptedFile(platform: PlatformType): File? = when (platform) {
        PlatformType.FACEBOOK -> File(encryptedDir, "facebook.bin")
        PlatformType.TWITTER -> File(encryptedDir, "twitter.bin")
        PlatformType.YOUTUBE -> File(encryptedDir, "youtube.bin")
        else -> null
    }

    private fun isValidPlatformDomain(domain: String, platform: PlatformType): Boolean {
        val d = domain.lowercase().trim().trimStart('.')
        return when (platform) {
            PlatformType.FACEBOOK -> d == "facebook.com" || d.endsWith(".facebook.com") || d == "fb.com" || d.endsWith(".fb.com")
            PlatformType.TWITTER -> d == "twitter.com" || d.endsWith(".twitter.com") || d == "x.com" || d.endsWith(".x.com") || d.endsWith(".twimg.com")
            PlatformType.YOUTUBE -> d == "youtube.com" || d.endsWith(".youtube.com") || d == "google.com" || d.endsWith(".google.com") || d.endsWith(".googlevideo.com")
            else -> false
        }
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val existing = ks.getKey(keyAlias, null)
        if (existing is SecretKey) return existing
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(
            keyAlias,
            android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    private fun encrypt(plain: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(StandardCharsets.UTF_8))
        return cipher.iv + encrypted
    }

    private fun decrypt(blob: ByteArray): String {
        require(blob.size > 12) { "Invalid encrypted cookie data" }
        val iv = blob.copyOfRange(0, 12)
        val payload = blob.copyOfRange(12, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(payload), StandardCharsets.UTF_8)
    }

    private fun readPlain(platform: PlatformType): String? {
        val file = getEncryptedFile(platform) ?: return null
        if (!file.exists() || file.length() == 0L) return null
        return runCatching { decrypt(file.readBytes()) }.getOrNull()
    }

    private fun writePlain(platform: PlatformType, content: String) {
        val file = getEncryptedFile(platform) ?: throw IllegalArgumentException("Unsupported cookie platform")
        val encrypted = encrypt(content)
        val temp = File(file.parentFile, file.name + ".tmp")
        temp.writeBytes(encrypted)
        if (!temp.renameTo(file)) {
            file.writeBytes(encrypted)
            temp.delete()
        }
    }

    private data class Cookie(val domain: String, val path: String, val secure: Boolean, val name: String, val value: String)

    private fun parseNetscape(content: String, platform: PlatformType): List<Cookie> {
        val out = mutableListOf<Cookie>()
        content.lineSequence().forEach { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@forEach
            val parts = t.split('\t')
            if (parts.size >= 7) {
                val domain = parts[0].trim()
                val path = parts[2].trim().ifBlank { "/" }
                val secure = parts[3].trim().equals("TRUE", true)
                val name = parts[5].trim()
                val value = parts[6].trim()
                if (name.isNotBlank() && isValidPlatformDomain(domain, platform)) {
                    out += Cookie(domain, path, secure, name, value)
                }
            }
        }
        return out
    }

    private fun parseJson(content: String, platform: PlatformType): List<Cookie> {
        val out = mutableListOf<Cookie>()
        val root = runCatching { JSONArray(content) }.getOrNull() ?: runCatching { JSONObject(content).optJSONArray("cookies") }.getOrNull() ?: return out
        for (i in 0 until root.length()) {
            val obj = root.optJSONObject(i) ?: continue
            val domain = obj.optString("domain").trim()
            val name = obj.optString("name").trim()
            val value = obj.optString("value")
            val path = obj.optString("path").ifBlank { "/" }
            val secure = obj.optBoolean("secure", false)
            if (name.isNotBlank() && isValidPlatformDomain(domain, platform)) {
                out += Cookie(domain, path, secure, name, value)
            }
        }
        return out
    }

    private fun parseHeader(content: String, platform: PlatformType): List<Cookie> {
        val defaultDomain = when (platform) {
            PlatformType.FACEBOOK -> ".facebook.com"
            PlatformType.TWITTER -> ".x.com"
            PlatformType.YOUTUBE -> ".youtube.com"
            else -> return emptyList()
        }
        return content.split(';').mapNotNull { part ->
            val index = part.indexOf('=')
            if (index <= 0) return@mapNotNull null
            val name = part.substring(0, index).trim()
            val value = part.substring(index + 1).trim()
            if (name.isBlank()) null else Cookie(defaultDomain, "/", false, name, value)
        }
    }

    private fun normalizeToNetscape(cookies: List<Cookie>): String {
        return buildString {
            append("# Netscape HTTP Cookie File\n")
            cookies.forEach { c ->
                append(c.domain).append('\t')
                    .append("TRUE").append('\t')
                    .append(c.path).append('\t')
                    .append(if (c.secure) "TRUE" else "FALSE").append('\t')
                    .append("0").append('\t')
                    .append(c.name).append('\t')
                    .append(c.value).append('\n')
            }
        }
    }

    fun getProfiles(): List<CookieProfile> {
        return listOf(PlatformType.FACEBOOK, PlatformType.TWITTER, PlatformType.YOUTUBE).map { platform ->
            val content = readPlain(platform).orEmpty()
            val cookies = parseNetscape(content, platform)
            CookieProfile(
                platform = platform,
                fileName = when (platform) {
                    PlatformType.FACEBOOK -> "Fb_cookies.txt"
                    PlatformType.TWITTER -> "X_cookies.txt"
                    PlatformType.YOUTUBE -> "YT_cookies.txt"
                    else -> "cookies.txt"
                },
                isConfigured = cookies.isNotEmpty(),
                entryCount = cookies.size,
                lastModified = getEncryptedFile(platform)?.lastModified() ?: 0L
            )
        }
    }

    fun importCookieFile(platform: PlatformType, inputStream: InputStream): Boolean {
        val content = runCatching { inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() } }.getOrNull() ?: return false
        if (content.length > 5 * 1024 * 1024) return false
        val cookies = parseNetscape(content, platform).ifEmpty { parseJson(content, platform) }.ifEmpty { parseHeader(content, platform) }
        if (cookies.isEmpty()) return false
        writePlain(platform, normalizeToNetscape(cookies))
        runtimeDirFor(platform).delete()
        return true
    }

    fun clearCookies(platform: PlatformType): Boolean {
        runtimeDirFor(platform).delete()
        val file = getEncryptedFile(platform) ?: return false
        return !file.exists() || file.delete()
    }

    /**
     * Returns an app-private, decrypted Netscape cookie file for Python.
     * The returned file never lives in shared/external storage.
     */
    fun getCookieFilePath(platform: PlatformType): String? {
        val plain = readPlain(platform) ?: return null
        val cookies = parseNetscape(plain, platform)
        if (cookies.isEmpty()) return null
        val file = runtimeDirFor(platform)
        file.parentFile?.mkdirs()
        file.writeText(plain, StandardCharsets.UTF_8)
        return file.absolutePath
    }

    private fun runtimeDirFor(platform: PlatformType): File = File(
        runtimeDir,
        when (platform) {
            PlatformType.FACEBOOK -> "facebook.txt"
            PlatformType.TWITTER -> "twitter.txt"
            PlatformType.YOUTUBE -> "youtube.txt"
            else -> "unsupported.txt"
        }
    )
}
