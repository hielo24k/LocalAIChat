package com.localai.chat.data.model

import java.io.File

/**
 * Represents a downloadable / locally stored model entry.
 */
data class ModelInfo(
    val id: String,
    val displayName: String,
    val description: String,
    val sizeBytes: Long,        // expected download size in bytes
    val downloadUrl: String,
    val fileName: String        // local file name inside filesDir/models/
) {
    val sizeMb: Double get() = sizeBytes / (1024.0 * 1024.0)
    val sizeGb: Double get() = sizeMb / 1024.0

    fun localFile(filesDir: File): File =
        File(File(filesDir, "models"), fileName)
}

enum class ModelTier { SMALL, MEDIUM, ADVANCED }
