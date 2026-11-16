package com.simple.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simple.mobile.data.recharge.BankRechargeNoticeStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankRechargeNoticeStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val message = "Fallo la recarga del movil 5355500000 alcanzo el monto limite de recarga permitido en 30 dias (360 CUP), puede recargar posterior al dia 14-12-2026."
    private fun store(name: String) = BankRechargeNoticeStore(context, name).also { it.clear() }
    @Test fun encryptedRoundTripKeepsDestinationSeparateFromReceivingSim() {
        val store = store("bank-test-roundtrip")
        assertTrue(store.ingest("PAGOxMOVIL", message, 8, 350_000))
        val reopened = BankRechargeNoticeStore(context, "bank-test-roundtrip").history().single()
        assertEquals("55500000", reopened.destination)
        assertEquals(8, reopened.receivingSubscription)
        assertEquals("360", reopened.limitCup)
        assertEquals("2026-12-14", reopened.afterDate.toString())
        assertEquals(350_000L, reopened.receivedAt)
        val raw = context.getSharedPreferences("bank-test-roundtrip", Context.MODE_PRIVATE).all.toString()
        assertFalse(raw.contains("55500000")); assertFalse(raw.contains("Fallo la recarga"))
        store.clear()
    }
    @Test fun canonicalDuplicatesDoNotCreateRepeatedNotices() {
        val store = store("bank-test-duplicates")
        assertTrue(store.ingest("PAGOxMOVIL", message, 1, 350_000))
        assertFalse(store.ingest("pagoxmovil", message.replace("5355500000", "55500000").replace("360 CUP", "360.00 CUP"), 1, 350_000))
        assertTrue(store.ingest("PAGOxMOVIL", message, 2, 350_000))
        assertEquals(2, store.history().size)
        store.clear(1)
        assertEquals(2, store.history().single().receivingSubscription)
        store.clear()
    }
    @Test fun rejectsUnknownSendersFormatsAndSubscriptions() {
        val store = store("bank-test-invalid")
        assertFalse(store.ingest("someone", message, 1, 1))
        assertFalse(store.ingest("PAGOxMOVIL", "Recarga exitosa", 1, 1))
        assertFalse(store.ingest("PAGOxMOVIL", message, -1, 1))
        assertFalse(store.ingest("PAGOxMOVIL", message, 1, 0))
        assertTrue(store.history().isEmpty())
    }
    @Test fun retentionIsBoundedAndOldRescansDoNotCountAsNew() {
        val store = store("bank-test-retention")
        for (i in 1..31) assertTrue(store.ingest("PAGOxMOVIL", message, 1, i.toLong()))
        assertEquals(30, store.history().size)
        assertEquals(31L, store.history().first().receivedAt)
        assertFalse(store.ingest("PAGOxMOVIL", message, 1, 1))
        store.clear()
    }
    @Test fun corruptStorageDoesNotSilentlyReplaceHistory() {
        val store = store("bank-test-corrupt")
        store.ingest("PAGOxMOVIL", message, 1, 1)
        context.getSharedPreferences("bank-test-corrupt", Context.MODE_PRIVATE).edit().putString("data", "AAAA").commit()
        assertThrows(Exception::class.java) { store.history() }
        assertThrows(Exception::class.java) { store.ingest("PAGOxMOVIL", message, 1, 2) }
        store.clear()
        assertTrue(store.history().isEmpty())
    }
}
