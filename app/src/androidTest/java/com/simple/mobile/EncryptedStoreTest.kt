package com.simple.mobile

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simple.mobile.data.local.EncryptedReadingStore
import com.simple.mobile.data.parser.CubacelParser
import com.simple.mobile.domain.carrier.CubacelQuery
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EncryptedStoreTest {
    @Test fun parsedReadingsSurviveReopeningAndRemainEncrypted() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val database = "test-readings.db"
        context.deleteDatabase(database)
        val parser = CubacelParser()
        val store = EncryptedReadingStore(context, database)
        val reading = parser.parse(CubacelQuery.BALANCE, "Saldo: 987654.32 CUP", 123L)
        store.save(77, reading)
        store.save(88, parser.parse(CubacelQuery.DATA, "Internet: 1.25 GB", 124L))
        val before = store.history()
        assertEquals(2, before.size)
        assertEquals(88, before.first().subscription)
        store.close()
        val reopened = EncryptedReadingStore(context, database)
        assertEquals("987654.32", reopened.history().last().reading.balanceCup())
        reopened.readableDatabase.rawQuery("SELECT payload FROM readings", null).use { cursor ->
            while (cursor.moveToNext()) {
                val payload = String(cursor.getBlob(0), Charsets.ISO_8859_1)
                assertFalse(payload.contains("987654.32"))
                assertFalse(payload.contains("balance"))
            }
        }
        reopened.clear()
        assertTrue(reopened.history().isEmpty())
        reopened.close()
        context.deleteDatabase(database)
    }
    @Test fun smsPurchaseRoundTripIsDeduplicatedAndNotRemainingQuota() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "test-sms-readings.db"
        context.deleteDatabase(name)
        val store = EncryptedReadingStore(context, name)
        val parsed = CubacelParser().parseSms("ETECSA Plan Nacional: Se ha comprado Plan 8GB+80MIN+90SMS valido por 35 dias, se activa con el primer uso. Se desconto 400.00 CUP. Saldo actual 2.50 CUP", 1800000000000L).first()
        assertTrue(store.save(77, parsed, "test-message"))
        assertFalse(store.save(77, parsed, "test-message"))
        assertEquals(1, store.history().size)
        store.close()
        val reopened = EncryptedReadingStore(context, name)
        val result = reopened.history().first().reading
        assertEquals("SMS", result.source())
        assertEquals("2.5", result.balanceCup())
        assertEquals(8000000000L, result.purchase().totalBytes())
        assertTrue(result.bundles().isEmpty())
        reopened.close()
        context.deleteDatabase(name)
    }

    @Test fun nicknamesStayEncryptedAndPerLineDeletionPreservesOtherLines() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "test-line-management.db"
        context.deleteDatabase(name)
        val store = EncryptedReadingStore(context, name)
        val reading = CubacelParser().parse(CubacelQuery.BALANCE, "Saldo: 25 CUP", 123L)
        store.save(77, reading)
        store.save(88, reading)
        store.rename(77, "Personal secreto")
        store.rename(88, "Trabajo")
        store.close()
        val reopened = EncryptedReadingStore(context, name)
        assertEquals("Personal secreto", reopened.nicknames()[77])
        reopened.readableDatabase.rawQuery("SELECT payload FROM line_profiles", null).use { cursor ->
            while (cursor.moveToNext()) assertFalse(String(cursor.getBlob(0), Charsets.UTF_8).contains("Personal secreto"))
        }
        reopened.clear(77)
        assertEquals(listOf(88), reopened.history().map { it.subscription })
        assertEquals(mapOf(88 to "Trabajo"), reopened.nicknames())
        reopened.rename(88, "")
        assertTrue(reopened.nicknames().isEmpty())
        reopened.close()
        context.deleteDatabase(name)
    }
    @Test fun versionOneUpgradePreservesReadings() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "test-upgrade.db"
        context.deleteDatabase(name)
        EncryptedReadingStore(context, name).use { store ->
            store.save(77, CubacelParser().parse(CubacelQuery.BALANCE, "Saldo: 25 CUP", 123L))
            store.writableDatabase.execSQL("DROP TABLE line_profiles")
            store.writableDatabase.execSQL("DROP TABLE recipients")
            store.writableDatabase.execSQL("DROP TABLE transfers")
            store.writableDatabase.execSQL("ALTER TABLE readings DROP COLUMN damaged")
            store.writableDatabase.version = 1
        }
        EncryptedReadingStore(context, name).use { upgraded ->
            assertEquals(1, upgraded.history().size)
            upgraded.rename(77, "Actualizada")
            assertEquals("Actualizada", upgraded.nicknames()[77])
        }
        context.deleteDatabase(name)
    }

    @Test fun corruptedReadingIsPreservedWhileHealthyReadingsLoad() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "test-damage.db"
        context.deleteDatabase(name)
        EncryptedReadingStore(context, name).use { store ->
            val r = CubacelParser().parse(CubacelQuery.BALANCE, "Saldo: 25 CUP", 123L)
            store.save(77, r)
            store.save(88, r)
            store.writableDatabase.execSQL("UPDATE readings SET payload=zeroblob(32) WHERE rowid=(SELECT MIN(rowid) FROM readings)")
            assertEquals(1, store.history().size)
            assertEquals(1, store.unreadableCount)
            repeat(305) { store.save(88, r) }
            store.readableDatabase.rawQuery("SELECT COUNT(*) FROM readings WHERE damaged=1", null).use { c ->
                c.moveToFirst(); assertEquals(1, c.getInt(0))
            }
            assertEquals(300, store.history().size)
        }
        context.deleteDatabase(name)
    }

}
