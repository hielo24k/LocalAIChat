package com.localai.chat.util

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object FileUtils {

    /**
     * Ensures a directory exists, creating it if necessary.
     * Returns true if the directory is ready to use.
     */
    fun ensureDir(dir: File): Boolean {
        return dir.exists() || dir.mkdirs()
    }

    /**
     * Deletes [file] if it exists. Returns true on success or if file didn't exist.
     */
    fun deleteIfExists(file: File): Boolean {
        return if (file.exists()) file.delete() else true
    }

    /**
     * Returns a human-readable file size string.
     */
    fun readableSize(file: File): String {
        return if (file.exists()) MemoryUtils.formatBytes(file.length()) else "unknown"
    }

    /**
     * Computes SHA-256 hex of [file]. Throws IOException if file can't be read.
     */
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Returns true if [file] appears to be a valid non-empty file.
     * Does not validate the internal format (that's the engine's responsibility).
     */
    fun isValidFile(file: File): Boolean {
        return file.exists() && file.isFile && file.length() > 0
    }
}
