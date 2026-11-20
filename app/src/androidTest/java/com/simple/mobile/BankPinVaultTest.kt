package com.simple.mobile

import android.app.KeyguardManager
import android.content.Context
import android.security.keystore.KeyInfo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.simple.mobile.data.recharge.BankPinVault
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankPinVaultTest {
    @Test fun savedPinKeyRequiresDeviceAuthenticationWithShortLifetime() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val vault = BankPinVault(context, 900003)
        vault.forget()
        try {
            vault.prepare()
            val key = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
                .getKey("simple.metro.pin.v3.900003", null) as SecretKey
            val info = SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            assertTrue(info.isUserAuthenticationRequired)
            assertEquals(15, info.userAuthenticationValidityDurationSeconds)
            if (android.os.Build.VERSION.SDK_INT >= 30)
                assertEquals(android.security.keystore.KeyProperties.AUTH_DEVICE_CREDENTIAL, info.userAuthenticationType)
        } finally { vault.forget() }
    }

    @Test fun savingAfterAuthenticationExpiresFailsAndWipesInput() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assumeTrue(context.getSystemService(KeyguardManager::class.java).isDeviceSecure)
        val vault = BankPinVault(context, 900005)
        vault.forget()
        try {
            vault.prepare()
            Thread.sleep(16_000) // Exceed the Keystore authorization window, including device setup.
            val pin = "4826".toCharArray()
            assertThrows(android.security.keystore.UserNotAuthenticatedException::class.java) { vault.save(pin) }
            assertFalse(vault.saved)
            assertTrue(pin.all { it == '\u0000' })
        } finally { vault.forget() }
    }

    @Test fun legacyAutomaticPinIsRemovedWithoutDecrypting() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences("bank_pins", Context.MODE_PRIVATE)
        prefs.edit().putString("900004.data", "old-ciphertext").putString("900004.iv", "old-iv")
            .putBoolean("900004.automatic", true).commit()
        val vault = BankPinVault(context, 900004)
        try {
            assertFalse(vault.saved)
            assertTrue(vault.needsReplacement)
            assertFalse(prefs.contains("900004.data"))
            assertFalse(prefs.contains("900004.iv"))
            assertThrows(IllegalStateException::class.java) { vault.unlock() }
        } finally { vault.forget() }
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
            prefs.edit().putString("900001.data", "opaque-one").putInt("900001.version", 3)
                .putString("900002.data", "opaque-two").putInt("900002.version", 3).commit()
            BankPinVault.clear(context, 900001)
            assertFalse(first.saved)
            assertTrue(second.saved)
        } finally { first.forget(); second.forget() }
    }
}
