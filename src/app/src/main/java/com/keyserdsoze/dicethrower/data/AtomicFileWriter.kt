package com.keyserdsoze.dicethrower.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Crash-safe replacement of small private files. The new content is written to a temporary
 * sibling and flushed to disk before it is renamed over the target, so a reader only ever sees a
 * complete previous or new version, never a truncated one. With a [backup] file, the replaced
 * version is kept there as the last known good copy.
 */
internal object AtomicFileWriter {
    fun writeText(target: File, text: String, backup: File? = null) {
        val directory = target.absoluteFile.parentFile
            ?: throw IOException("No directory for ${target.name}")
        if (!directory.isDirectory && !directory.mkdirs()) {
            throw IOException("Unable to create ${directory.name}")
        }
        val temporary = File(directory, "${target.name}.tmp")
        FileOutputStream(temporary).use { output ->
            output.write(text.toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
        }
        if (backup != null && target.isFile) {
            // A crash between both renames leaves no target: readers then fall back to the backup.
            backup.delete()
            if (!target.renameTo(backup)) target.copyTo(backup, overwrite = true)
        }
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Unable to replace ${target.name}")
        }
    }
}
