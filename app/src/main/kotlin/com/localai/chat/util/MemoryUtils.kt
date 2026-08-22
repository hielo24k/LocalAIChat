package com.localai.chat.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.File

object MemoryUtils {

    /**
     * Returns the total available RAM on the device in bytes.
     */
    fun totalRamBytes(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem
    }

    /**
     * Returns the currently available (free) RAM in bytes.
     */
    fun availableRamBytes(context: Context): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.availMem
    }

    /**
     * Returns true if [requiredBytes] of RAM are likely available.
     * Uses a conservative threshold: available must be >= required.
     */
    fun hasEnoughRam(context: Context, requiredBytes: Long): Boolean {
        return availableRamBytes(context) >= requiredBytes
    }

    /**
     * Returns free bytes on the partition containing [directory].
     */
    fun freeDiskBytes(directory: File): Long {
        return try {
            val stat = StatFs(directory.absolutePath)
            stat.availableBlocksLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * Returns true if there is at least [requiredBytes] of free disk space
     * in the directory containing [directory].
     */
    fun hasEnoughDisk(directory: File, requiredBytes: Long): Boolean {
        return freeDiskBytes(directory) >= requiredBytes
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.1f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L     -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1_024L         -> "%.1f KB".format(bytes / 1_024.0)
        else                    -> "$bytes B"
    }
}
