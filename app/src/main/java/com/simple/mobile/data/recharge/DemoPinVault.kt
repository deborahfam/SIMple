package com.simple.mobile.data.recharge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.simple.mobile.domain.recharge.RechargeSimulation
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores ONLY the fixed fictitious PIN. Android must authorize each UI operation. */
class DemoPinVault(context: Context) {
    private val prefs = context.getSharedPreferences("demo_pin", Context.MODE_PRIVATE)
    val saved: Boolean get() = prefs.contains("data")
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            @Suppress("DEPRECATION")
            val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true).setUserAuthenticationValidityDurationSeconds(30).build()
            init(spec)
        }.generateKey()
    }
    fun prepareKey() { key() }
    fun saveDemo() {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(ALIAS.toByteArray()) }
        val plain = RechargeSimulation.DEMO_PIN.toByteArray()
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        check(prefs.edit().putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit())
    }
    fun unlockDemo(): Boolean {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)))
            updateAAD(ALIAS.toByteArray())
        }
        val plain = cipher.doFinal(Base64.decode(prefs.getString("data", ""), Base64.NO_WRAP))
        return try { plain.contentEquals(RechargeSimulation.DEMO_PIN.toByteArray()) } finally { plain.fill(0) }
    }
    fun forget() {
        check(prefs.edit().clear().commit())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(ALIAS) }
    }
    companion object { private const val ALIAS = "simple.demo.pin.v1" }
}
