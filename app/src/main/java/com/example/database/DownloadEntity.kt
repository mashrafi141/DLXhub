package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.DownloadItem
import com.example.model.DownloadState
import com.example.model.DownloadType
import com.example.model.PlatformType

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey
    val id: String,
    val url: String,
    val title: String,
    val platformId: String,
    val type: String,
    val qualityLabel: String,
    val state: String,
    val filePath: String?,
    val fileSize: Long,
    val errorMessage: String?,
    val timestamp: Long
) {
    fun toDownloadItem(): DownloadItem = DownloadItem(
        id = id,
        url = url,
        title = title,
        platform = PlatformType.fromId(platformId),
        type = try { DownloadType.valueOf(type) } catch (e: Exception) { DownloadType.VIDEO },
        qualityLabel = qualityLabel,
        state = try { DownloadState.valueOf(state) } catch (e: Exception) { DownloadState.COMPLETED },
        errorMessage = errorMessage,
        filePath = filePath,
        fileSize = fileSize,
        timestamp = timestamp
    )

    companion object {
        fun fromDownloadItem(item: DownloadItem): DownloadEntity = DownloadEntity(
            id = item.id,
            url = item.url,
            title = item.title,
            platformId = item.platform.id,
            type = item.type.name,
            qualityLabel = item.qualityLabel,
            state = item.state.name,
            filePath = item.filePath,
            fileSize = item.fileSize,
            errorMessage = item.errorMessage,
            timestamp = item.timestamp
        )
    }
}
