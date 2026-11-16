package com.simple.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simple.mobile.data.recharge.SimulationStore
import com.simple.mobile.domain.recharge.RechargeSimulation as D
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SimulationStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    @Test fun encryptedRoundTripAndRestartDoNotReplaySend() {
        val store = SimulationStore(context, "test-demo-state")
        store.clear()
        val draft = D.prepare(8, "Secret demo line", D.Bank.METRO, "55500000", "123.45", 1_000)
        val auth = D.authenticate(draft, D.DEMO_PIN)
        val sent = D.receive(auth, D.fixture(auth, D.Kind.AUTH_OK, 2_000))
        store.save(sent)
        val reopened = SimulationStore(context, "test-demo-state").load()!!
        assertEquals(sent, reopened)
        assertEquals(D.Phase.UNCERTAIN, D.restore(reopened).phase)
        assertEquals(1, D.restore(reopened).simulatedSends)
        val raw = context.getSharedPreferences("test-demo-state", Context.MODE_PRIVATE).all.toString()
        assertFalse(raw.contains("Secret demo line"))
        assertFalse(raw.contains("55500000"))
        assertFalse(raw.contains("123.45"))
        assertNull(reopened.resultReference)
        store.clear()
    }
    @Test fun clearOnlyRemovesChosenDemoStore() {
        val one = SimulationStore(context, "test-demo-one")
        val two = SimulationStore(context, "test-demo-two")
        val draft = D.prepare(1, "Demo", D.Bank.BPA, "55500000", "5", 0)
        one.save(draft); two.save(draft)
        one.clear()
        assertNull(one.load())
        assertEquals(draft, two.load())
        two.clear()
    }
    @Test fun tamperedStateFailsClosed() {
        val store = SimulationStore(context, "test-demo-tamper")
        store.save(D.prepare(1, "Demo", D.Bank.BANDEC, "55500000", "1", 0))
        context.getSharedPreferences("test-demo-tamper", Context.MODE_PRIVATE).edit().putString("data", "AAAA").commit()
        assertThrows(Exception::class.java) { store.load() }
        store.clear()
    }
}
