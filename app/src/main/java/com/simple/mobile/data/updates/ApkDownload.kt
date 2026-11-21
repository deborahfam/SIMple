package com.simple.mobile.data.updates

import java.io.InputStream
import java.io.OutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

internal class DownloadCancelled : IOException()
internal class DownloadControl {
    private val cancelled = AtomicBoolean(false)
    fun cancel() { cancelled.set(true) }
    fun check() { if (cancelled.get()) throw DownloadCancelled() }
}

internal object ApkDownload {
    const val MAX_BYTES = 150_000_000L
    fun copy(input: InputStream, output: OutputStream, expected: Long, control: DownloadControl,
        progress: (Long, Long?) -> Unit) {
        check(expected <= MAX_BYTES) { "El APK supera el tamaño permitido." }
        val size = expected.takeIf { it > 0 }
        val buffer = ByteArray(32_768)
        var total = 0L
        while (true) {
            control.check()
            val n = input.read(buffer)
            control.check()
            if (n < 0) break
            total += n
            check(total <= MAX_BYTES) { "El APK supera el tamaño permitido." }
            output.write(buffer, 0, n)
            progress(total, size)
        }
        check(total > 0 && (size == null || total == size)) { "La descarga quedó incompleta. Inténtalo de nuevo." }
    }
}
