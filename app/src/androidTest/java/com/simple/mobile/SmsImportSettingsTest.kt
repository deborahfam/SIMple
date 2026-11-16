package com.simple.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simple.mobile.data.telephony.SmsImportSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmsImportSettingsTest {
    private fun settings(): SmsImportSettings {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("test-sms-settings", Context.MODE_PRIVATE).edit().clear().commit()
        return SmsImportSettings(context, "test-sms-settings")
    }
    @Test fun cursorIsScopedPersistentAndCannotAdvanceAfterOptOut() {
        val settings = settings()
        settings.setEnabled(true)
        val token = settings.beginSync()!!
        settings.checkpoint(token, "scope-a", 120, true)
        assertEquals(120L, settings.inboxCursor("scope-a"))
        assertNull(settings.inboxCursor("scope-b"))
        assertTrue(settings.state.value.moreInbox)
        val reopened = SmsImportSettings(ApplicationProvider.getApplicationContext(), "test-sms-settings")
        assertEquals(120L, reopened.inboxCursor("scope-a"))
        settings.setEnabled(false)
        settings.checkpoint(token, "scope-a", 999, false)
        assertEquals(120L, settings.inboxCursor("scope-a"))
        settings.clearSummary()
        assertNull(settings.inboxCursor("scope-a"))
    }
    @Test fun optOutInvalidatesQueuedWorkEvenAfterReenabling() {
        val settings = settings()
        assertNull(settings.token())
        settings.setEnabled(true)
        val old = settings.beginSync()!!
        assertNull(settings.beginSync())
        settings.setEnabled(false)
        settings.setEnabled(true)
        var writes = 0
        assertFalse(settings.saveIfEnabled(old) { writes++; true })
        settings.finishSync(old, 10)
        assertEquals(0, writes)
        assertFalse(settings.state.value.syncing)
        assertNull(settings.state.value.lastAt)
        val fresh = settings.beginSync()!!
        assertTrue(settings.saveIfEnabled(fresh) { writes++; true })
        settings.finishSync(fresh, 1)
        assertEquals(1, settings.state.value.lastAdded)
    }
    @Test fun successfulSummarySurvivesRestartButFailedSyncDoesNotOverwriteIt() {
        val settings = settings()
        settings.setEnabled(true)
        settings.finishSync(settings.beginSync()!!, 3)
        val at = settings.state.value.lastAt
        settings.finishSync(settings.beginSync()!!, 0, "Permission denied")
        assertEquals(at, settings.state.value.lastAt)
        assertEquals(3, settings.state.value.lastAdded)
        assertEquals("Permission denied", settings.state.value.error)
        val reopened = SmsImportSettings(ApplicationProvider.getApplicationContext(), "test-sms-settings")
        assertTrue(reopened.state.value.enabled)
        assertEquals(at, reopened.state.value.lastAt)
        assertEquals(3, reopened.state.value.lastAdded)
        reopened.clearSummary()
        assertNull(reopened.state.value.lastAt)
    }
    @Test fun zeroNewReadingsIsACompletedSyncAndIgnoredBroadcastDoesNotReplaceIt() {
        val settings = settings()
        settings.setEnabled(true)
        val token = settings.beginSync()!!
        settings.finishSync(token, 0)
        assertNotNull(settings.state.value.lastAt)
        val at = settings.state.value.lastAt
        settings.received(token, 0)
        assertEquals(at, settings.state.value.lastAt)
        settings.setEnabled(false)
        settings.received(token, 5)
        assertEquals(0, settings.state.value.lastAdded)
    }
}
