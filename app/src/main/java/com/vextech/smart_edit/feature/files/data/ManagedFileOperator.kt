package com.vextech.smart_edit.feature.files.data

import java.io.File
import java.io.IOException

object ManagedFileOperator {
    fun relocate(source: File, target: File): File {
        require(source.exists()) { "Source file does not exist." }
        require(!target.exists()) { "A file or folder named ${target.name} already exists." }
        val parent = target.parentFile ?: throw IOException("Destination has no parent directory.")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Unable to create destination directory.")
        if (!source.renameTo(target)) throw IOException("Unable to move ${source.name}.")
        return target
    }

    fun rollback(current: File, original: File) {
        if (!current.exists() || original.exists()) return
        val parent = original.parentFile ?: throw IOException("Original location has no parent directory.")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Unable to recreate the original directory.")
        if (!current.renameTo(original)) throw IOException("Unable to roll back ${current.name}.")
    }
}
