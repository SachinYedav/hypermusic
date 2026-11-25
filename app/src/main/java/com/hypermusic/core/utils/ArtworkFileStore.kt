package com.hypermusic.core.utils

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

internal object ArtworkFileStore {
    /** Temp and target must share a filesystem; a failed replacement leaves the old cover intact. */
    fun install(temp: File, target: File): String {
        try {
            if (!temp.isFile || temp.length() == 0L) throw IOException("Empty artwork")
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return target.absolutePath
        } finally {
            temp.delete()
        }
    }
}
