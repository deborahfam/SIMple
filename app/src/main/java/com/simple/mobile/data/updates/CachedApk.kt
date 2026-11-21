package com.simple.mobile.data.updates

import java.io.File

internal object CachedApk {
    /** Metadata is not proof of trust: revalidate the file before restoring installation state. */
    fun recover(file: File, readCode: (File) -> Int?, validate: (Int, File) -> Unit): Int? {
        if (!file.isFile) return null
        return try {
            val code = readCode(file) ?: error("APK inválido")
            validate(code, file)
            code
        } catch (_: Exception) {
            file.delete()
            null
        }
    }
}
