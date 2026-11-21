package com.simple.mobile.data.updates

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CachedApkTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun recoveryRevalidatesTheActualFileEveryTime() {
        val file = folder.newFile("update.apk").apply { writeText("fixture") }
        var validations = 0
        repeat(2) {
            assertEquals(23, CachedApk.recover(file, { 23 }) { code, actual ->
                assertEquals(23, code)
                assertEquals(file, actual)
                assertEquals("fixture", actual.readText())
                validations++
            })
        }
        assertEquals(2, validations)
        assertTrue(file.exists())
    }
    @Test fun rejectedSignatureOrVersionRemovesCacheAndNeverRestoresReadyState() {
        val file = folder.newFile("update.apk")
        assertNull(CachedApk.recover(file, { 23 }) { _, _ -> error("Rejected") })
        assertFalse(file.exists())
    }
    @Test fun unparseableAndMissingFilesCannotBeRecovered() {
        val file = folder.newFile("update.apk")
        assertNull(CachedApk.recover(file, { null }) { _, _ -> fail("Must not validate missing metadata") })
        assertFalse(file.exists())
        assertNull(CachedApk.recover(file, { fail("Must not read missing file"); null }) { _, _ -> fail() })
    }
}
