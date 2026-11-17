package com.simple.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simple.mobile.data.recharge.BankRechargeStore
import com.simple.mobile.domain.recharge.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankRechargeStoreTest {
    @Test fun pendingAttemptSurvivesReopenWithoutPlaintextAndCanBeCleared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test_bank_attempt"
        val store = BankRechargeStore(context, name)
        store.clear()
        try {
            val attempt = BankAttempt("private-attempt-id", 12345, "Private SIM", "55500000", "120", 1730000000000, BankPhase.SENDING)
            store.save(attempt)
            assertEquals(attempt, BankRechargeStore(context, name).load())
            val values = context.getSharedPreferences(name, Context.MODE_PRIVATE).all.toString()
            assertFalse(values.contains("private-attempt-id"))
            assertFalse(values.contains("55500000"))
            assertFalse(values.contains("SENDING"))
            store.clear()
            assertNull(store.load())
        } finally { store.clear() }
    }
    @Test fun corruptAttemptIsRejectedInsteadOfForgotten() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "test_bank_attempt_corrupt"
        val store = BankRechargeStore(context, name)
        store.clear()
        try {
            store.save(BankAttempt("id", 7, "SIM", "55500000", "120", 10, BankPhase.SENDING))
            context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().putString("data", "AAAA").commit()
            assertThrows(Exception::class.java) { store.load() }
        } finally { store.clear() }
    }
}
