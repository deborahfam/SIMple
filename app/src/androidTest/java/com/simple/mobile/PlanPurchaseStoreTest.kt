package com.simple.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simple.mobile.data.plans.PlanPurchaseStore
import com.simple.mobile.domain.plans.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlanPurchaseStoreTest {
    @Test fun pendingAttemptSurvivesReopenWithoutPlaintextAndCanBeCleared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test_plan_attempt"
        val store = PlanPurchaseStore(context, name)
        store.clear()
        try {
            val attempt = PlanAttempt("private-attempt-id", "combo_6gb", 12345, 1730000000000, PurchasePhase.SENDING)
            store.save(attempt)
            assertEquals(attempt, PlanPurchaseStore(context, name).load())
            val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toString()
            assertFalse(values.contains("private-attempt-id"))
            assertFalse(values.contains("combo_6gb"))
            assertFalse(values.contains("SENDING"))
            store.clear()
            assertNull(store.load())
        } finally { store.clear() }
    }
    @Test fun corruptAttemptIsRejectedInsteadOfForgotten() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test_plan_attempt_corrupt"
        val store = PlanPurchaseStore(context, name)
        store.clear()
        try {
            store.save(PlanAttempt("id", "combo_2gb", 7, 10, PurchasePhase.SENDING))
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("data", "AAAA").commit()
            assertThrows(Exception::class.java) { store.load() }
        } finally { store.clear() }
    }
    @Test fun carrierReplySurvivesEncryptedReopenAndIsRemovedWithAttempt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test_plan_reply"
        val store = PlanPurchaseStore(context, name)
        store.clear()
        try {
            val attempt = PlanAttempt("reply-id", "combo_2gb", 7, 10, PurchasePhase.RESPONSE,
                "Respuesta privada de Cubacel: saldo 20 CUP")
            store.save(attempt)
            assertEquals(attempt, PlanPurchaseStore(context, name).load())
            val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toString()
            assertFalse(values.contains("Respuesta privada"))
            assertFalse(values.contains("saldo 20 CUP"))
            assertEquals(setOf("iv", "data"), context.getSharedPreferences(name, Context.MODE_PRIVATE).all.keys)
            store.clear()
            assertNull(PlanPurchaseStore(context, name).load())
        } finally { store.clear() }
    }

}
