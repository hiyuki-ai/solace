package com.solace.app.data

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val bucketId: Long,
    val bucketName: String,
    val sizeBytes: Long,
    val dateAddedSec: Long,
)

data class VideoFolder(
    val bucketId: Long,
    val name: String,
    val videos: List<VideoItem>,
) {
    val coverUri: Uri? get() = videos.firstOrNull()?.uri
}
