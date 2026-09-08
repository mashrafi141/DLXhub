package com.example.storage

import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.example.model.DownloadType
import com.example.model.PlatformType
import java.io.File
import java.io.OutputStream

class StorageDestinationManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("dlxhub_storage_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SAF_URI = "key_saf_tree_uri"

        fun sanitizeFilename(raw: String): String {
            var name = raw.trim()
            name = name.replace(Regex("""[\\/:*?"<>|]+"""), "_")
            name = name.replace(Regex("""_+"""), "_")
            name = name.replace(Regex("""\s+"""), " ").trim('.', ' ', '_')
            if (name.isEmpty() || name in setOf(".", "..")) {
                name = "dlxhub_download"
            }
            return if (name.length > 180) name.substring(0, 180) else name
        }
    }

    var safTreeUri: Uri?
        get() {
            val uriStr = prefs.getString(KEY_SAF_URI, null) ?: return null
            return try { Uri.parse(uriStr) } catch (e: Exception) { null }
        }
        set(value) {
            prefs.edit().apply {
                if (value != null) putString(KEY_SAF_URI, value.toString())
                else remove(KEY_SAF_URI)
            }.apply()
        }

    fun getDisplayPath(): String {
        val uri = safTreeUri
        return if (uri != null) {
            "Custom SAF: ${uri.lastPathSegment ?: uri.path ?: "Selected Folder"}"
        } else {
            val defaultDir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
                "DLXhub"
            )
            defaultDir.absolutePath
        }
    }

    fun getTargetDirectory(
        platform: PlatformType,
        type: DownloadType,
        playlistName: String? = null
    ): File {
        val baseDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir,
            "DLXhub"
        )
        val platformDir = File(baseDir, platform.folderName)
        val targetDir = if (!playlistName.isNullOrBlank()) {
            val playlistSafe = sanitizeFilename(playlistName)
            File(File(platformDir, playlistSafe), if (type == DownloadType.AUDIO) "audio" else "video")
        } else {
            File(platformDir, if (type == DownloadType.AUDIO) "audio" else "video")
        }
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir
    }

    fun openOutputStream(
        platform: PlatformType,
        type: DownloadType,
        fileName: String,
        mimeType: String,
        playlistName: String? = null
    ): Pair<OutputStream, String> {
        val treeUri = safTreeUri
        if (treeUri != null) {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                ?: throw java.io.IOException("Selected destination folder is inaccessible (tree URI invalid)")
            if (!rootDoc.canWrite()) {
                throw java.io.IOException("Permission denied: cannot write to selected destination folder")
            }

            var currentDoc = rootDoc.findFile(platform.folderName)
                ?: rootDoc.createDirectory(platform.folderName)
                ?: throw java.io.IOException("Failed to create platform directory ${platform.folderName} in selected destination")

            if (!playlistName.isNullOrBlank()) {
                val playlistSafe = sanitizeFilename(playlistName)
                currentDoc = currentDoc.findFile(playlistSafe)
                    ?: currentDoc.createDirectory(playlistSafe)
                    ?: throw java.io.IOException("Failed to create playlist folder in selected destination")
            }

            val subfolderName = if (type == DownloadType.AUDIO) "audio" else "video"
            val leafDoc = currentDoc.findFile(subfolderName)
                ?: currentDoc.createDirectory(subfolderName)
                ?: throw java.io.IOException("Failed to create subfolder $subfolderName in selected destination")

            val uniqueName = uniqueDocumentName(leafDoc, fileName)
            val newFileDoc = leafDoc.createFile(mimeType, uniqueName)
                ?: throw java.io.IOException("Failed to create file $uniqueName in selected destination")

            val stream = context.contentResolver.openOutputStream(newFileDoc.uri)
                ?: throw java.io.IOException("Failed to open output stream for $fileName in selected destination")

            return Pair(stream, newFileDoc.uri.toString())
        }

        val targetDir = getTargetDirectory(platform, type, playlistName)
        if (!targetDir.exists() && !targetDir.mkdirs()) {
            throw java.io.IOException("Failed to create destination directory: ${targetDir.absolutePath}")
        }
        val localFile = uniqueLocalFile(targetDir, fileName)
        return Pair(localFile.outputStream(), localFile.absolutePath)
    }

    private fun uniqueDocumentName(parent: DocumentFile, original: String): String {
        if (parent.findFile(original) == null) return original
        val dot = original.lastIndexOf('.')
        val stem = if (dot > 0) original.substring(0, dot) else original
        val ext = if (dot > 0) original.substring(dot) else ""
        var index = 1
        while (parent.findFile("$stem ($index)$ext") != null) index++
        return "$stem ($index)$ext"
    }

    private fun uniqueLocalFile(parent: File, original: String): File {
        val first = File(parent, original)
        if (!first.exists()) return first
        val dot = original.lastIndexOf('.')
        val stem = if (dot > 0) original.substring(0, dot) else original
        val ext = if (dot > 0) original.substring(dot) else ""
        var index = 1
        while (File(parent, "$stem ($index)$ext").exists()) index++
        return File(parent, "$stem ($index)$ext")
    }
    /** App-private scratch directory used for transient media processing. */
    fun tempDirectory(): File = File(context.cacheDir, "dlxhub_media_tmp").apply {
        if (!exists() && !mkdirs()) {
            throw java.io.IOException("Unable to create DLXhub temporary media directory")
        }
    }

}
