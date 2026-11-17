package com.simple.mobile.data.recharge

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.simple.mobile.domain.recharge.MetropolitanoProtocol
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Local, opt-in Metropolitano PIN, scoped to the paying SIM. Never backed up. */
class BankPinVault(context: Context, private val subscription: Int) {
    private val prefs = context.getSharedPreferences("bank_pins", Context.MODE_PRIVATE)
    private val alias = "simple.metro.pin.v1.$subscription"
    init { require(subscription >= 0) }
    val saved get() = prefs.contains("$subscription.data")
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
        if (Build.VERSION.SDK_INT >= 30) builder.setUserAuthenticationParameters(30, KeyProperties.AUTH_DEVICE_CREDENTIAL)
        else { @Suppress("DEPRECATION") builder.setUserAuthenticationValidityDurationSeconds(30) }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply { init(builder.build()) }.generateKey()
    }
    fun prepare() { key() }
    fun save(pin: CharArray) {
        try {
            require(MetropolitanoProtocol.validPin(pin))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(alias.toByteArray()) }
            val plain = ByteArray(pin.size) { pin[it].code.toByte() }
            val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
            check(prefs.edit().putString("$subscription.data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("$subscription.iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).commit())
        } finally { pin.fill('\u0000') }
    }
    fun unlock(): CharArray {
        check(saved)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("$subscription.iv", ""), Base64.NO_WRAP)))
            updateAAD(alias.toByteArray())
        }
        val plain = cipher.doFinal(Base64.decode(prefs.getString("$subscription.data", ""), Base64.NO_WRAP))
        return try {
            check(plain.size == 4 && plain.all { it.toInt() in 48..57 })
            CharArray(plain.size) { plain[it].toInt().toChar() }
        } finally { plain.fill(0) }
    }
    fun forget() {
        check(prefs.edit().remove("$subscription.data").remove("$subscription.iv").commit())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
    }
    companion object {
        fun clear(context: Context, subscription: Int?) {
            if (subscription != null) BankPinVault(context, subscription).forget()
            else context.getSharedPreferences("bank_pins", Context.MODE_PRIVATE).all.keys
                .mapNotNull { it.substringBefore('.').toIntOrNull() }.distinct().forEach { BankPinVault(context, it).forget() }
        }
    }
}
