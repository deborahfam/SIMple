package com.simple.mobile

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simple.mobile.data.local.EncryptedReadingStore
import com.simple.mobile.domain.transfer.TransferDraft
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransferStoreTest {
    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()
    @Test fun encryptedRecipientsSurviveReopenAndCanBeEditedAndDeleted() {
        val name = "test-transfer-recipients.db"
        context.deleteDatabase(name)
        EncryptedReadingStore(context, name).use { store ->
            store.saveRecipient(null, "Test Recipient", "+53 51234567", false)
            store.saveRecipient(null, "Favorite", "52345678", true)
            assertEquals("Favorite", store.recipients().first().name)
            store.readableDatabase.rawQuery("SELECT payload FROM recipients", null).use { c ->
                while (c.moveToNext()) {
                    val raw = String(c.getBlob(0), Charsets.ISO_8859_1)
                    assertFalse(raw.contains("Test Recipient")); assertFalse(raw.contains("51234567"))
                }
            }
        }
        EncryptedReadingStore(context, name).use { store ->
            val saved = store.recipients().last()
            assertEquals("51234567", saved.phone)
            store.saveRecipient(saved.id, "Edited", saved.phone, true)
            assertEquals("Edited", store.recipients().first().name)
            try { store.saveRecipient(null, "Duplicate", saved.phone, false); fail("Duplicate accepted") } catch (_: IllegalArgumentException) { }
            store.deleteRecipient(saved.id)
            assertEquals(1, store.recipients().size)
        }
        context.deleteDatabase(name)
    }
    @Test fun transferStatusNeverAutomaticallyConfirmsAndDeletionIsScoped() {
        val name = "test-transfer-events.db"
        context.deleteDatabase(name)
        EncryptedReadingStore(context, name).use { store ->
            store.saveRecipient(null, "Recipient", "51234567", true)
            val one = store.prepareTransfer(TransferDraft(77, "Personal", "Recipient", "51234567", "10.50"))
            store.prepareTransfer(TransferDraft(88, "Work", "Recipient", "51234567", "20"))
            assertEquals("PREPARED", store.transfers().first { it.id == one }.status)
            try { store.transferStatus(one, "USER_CONFIRMED"); fail("Unopened event confirmed") } catch (_: IllegalArgumentException) { }
            store.transferStatus(one, "OPENED")
            assertEquals("OPENED", store.transfers().first { it.id == one }.status)
            store.transferStatus(one, "USER_CONFIRMED")
            store.transferStatus(one, "NOT_COMPLETED")
            assertEquals("NOT_COMPLETED", store.transfers().first { it.id == one }.status)
            store.readableDatabase.rawQuery("SELECT payload FROM transfers", null).use { c ->
                while (c.moveToNext()) assertFalse(String(c.getBlob(0), Charsets.ISO_8859_1).contains("51234567"))
            }
            store.clear(77)
            assertEquals(listOf(88), store.transfers().map { it.subscription })
            assertEquals(1, store.recipients().size)
            store.clear()
            assertTrue(store.transfers().isEmpty()); assertTrue(store.recipients().isEmpty())
        }
        context.deleteDatabase(name)
    }
    @Test fun versionThreeUpgradePreservesProfilesAndAddsTransferStorage() {
        val name = "test-transfer-upgrade.db"
        context.deleteDatabase(name)
        EncryptedReadingStore(context, name).use { store ->
            store.rename(77, "Preserved")
            store.writableDatabase.execSQL("DROP TABLE recipients")
            store.writableDatabase.execSQL("DROP TABLE transfers")
            store.writableDatabase.version = 3
        }
        EncryptedReadingStore(context, name).use { store ->
            assertEquals("Preserved", store.nicknames()[77])
            store.saveRecipient(null, "Recipient", "51234567", false)
            assertEquals(1, store.recipients().size)
            assertTrue(store.transfers().isEmpty())
        }
        context.deleteDatabase(name)
    }
}
