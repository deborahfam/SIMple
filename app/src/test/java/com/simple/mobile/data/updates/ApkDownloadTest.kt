package com.simple.mobile.data.updates

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ApkDownloadTest {
    @Test fun knownSizeReportsProgressAndCopiesExactBytes() {
        val bytes = ByteArray(100_000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()
        ApkDownload.copy(ByteArrayInputStream(bytes), output, bytes.size.toLong(), DownloadControl()) { count, size ->
            assertEquals(bytes.size.toLong(), size)
            progress += count
        }
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(bytes.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> b > a })
    }
    @Test fun unknownSizeDoesNotInventPercentage() {
        val output = ByteArrayOutputStream()
        ApkDownload.copy(ByteArrayInputStream(byteArrayOf(1, 2, 3)), output, -1, DownloadControl()) { _, size -> assertNull(size) }
        assertEquals(3, output.size())
    }
    @Test fun cancellationStopsBeforeWritingAnotherChunk() {
        val control = DownloadControl()
        val output = ByteArrayOutputStream()
        assertThrows(DownloadCancelled::class.java) {
            ApkDownload.copy(ByteArrayInputStream(ByteArray(100_000)), output, 100_000, control) { _, _ -> control.cancel() }
        }
        assertEquals(32_768, output.size())
    }
    @Test fun cancellationDuringReadDoesNotWriteThatChunk() {
        val control = DownloadControl()
        val input = object : ByteArrayInputStream(ByteArray(100)) {
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                control.cancel()
                return super.read(b, off, len)
            }
        }
        val output = ByteArrayOutputStream()
        assertThrows(DownloadCancelled::class.java) { ApkDownload.copy(input, output, 100, control) { _, _ -> } }
        assertEquals(0, output.size())
    }
    @Test fun truncatedEmptyAndOversizedDownloadsAreRejected() {
        for ((bytes, size) in listOf(byteArrayOf(1) to 2L, byteArrayOf() to -1L, byteArrayOf(1) to (ApkDownload.MAX_BYTES + 1))) {
            assertThrows(IllegalStateException::class.java) {
                ApkDownload.copy(ByteArrayInputStream(bytes), ByteArrayOutputStream(), size, DownloadControl()) { _, _ -> }
            }
        }
    }
}
