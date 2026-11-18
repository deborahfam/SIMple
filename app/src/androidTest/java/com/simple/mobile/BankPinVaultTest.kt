package com.simple.mobile

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simple.mobile.data.recharge.BankPinVault
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankPinVaultTest {
    @Test fun optedInPinLoadsAfterReopenWithoutDevicePromptAndDetectsTampering() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = BankPinVault(context, 900003)
        vault.forget()
        try {
            val pin = "4826".toCharArray()
            vault.save(pin)
            assertTrue(pin.all { it == '\u0000' })
            val reopened = BankPinVault(context, 900003)
            assertTrue(reopened.automatic)
            val recovered = reopened.unlock()
            try { assertArrayEquals("4826".toCharArray(), recovered) } finally { recovered.fill('\u0000') }
            val prefs = context.getSharedPreferences("bank_pins", Context.MODE_PRIVATE)
            assertFalse(prefs.all.toString().contains("4826"))
            prefs.edit().putString("900003.data", "AAAA").commit()
            assertThrows(Exception::class.java) { reopened.unlock() }
        } finally { vault.forget() }
        assertFalse(vault.saved)
    }

    @Test fun invalidPinIsWipedAndNeverPersisted() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val vault = BankPinVault(context, 900001)
        vault.forget()
        val pin = "12#4".toCharArray()
        assertThrows(IllegalArgumentException::class.java) { vault.save(pin) }
        assertTrue(pin.all { it == '\u0000' })
        assertFalse(vault.saved)
        assertThrows(IllegalStateException::class.java) { vault.unlock() }
    }
    @Test fun forgettingOneSimDoesNotForgetAnother() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("bank_pins", Context.MODE_PRIVATE)
        val first = BankPinVault(context, 900001)
        val second = BankPinVault(context, 900002)
        try {
            // Opaque fixtures only; never a plaintext bank PIN.
            prefs.edit().putString("900001.data", "opaque-one").putString("900002.data", "opaque-two").commit()
            BankPinVault.clear(context, 900001)
            assertFalse(first.saved)
            assertTrue(second.saved)
        } finally { first.forget(); second.forget() }
    }
}
