package com.simple.mobile.data.recharge

import android.content.Context
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
    private val alias = "simple.metro.pin.v3.$subscription"
    private val legacyAliases = listOf("simple.metro.pin.v1.$subscription", "simple.metro.pin.v2.$subscription")
    init {
        require(subscription >= 0)
        if (prefs.contains("$subscription.data") && prefs.getInt("$subscription.version", 0) != 3) {
            // Never decrypt or retain a legacy key that permits unauthenticated access.
            forget()
            check(prefs.edit().putBoolean("$subscription.replace", true).commit())
        }
    }
    val saved get() = prefs.contains("$subscription.data") && prefs.getInt("$subscription.version", 0) == 3
    val needsReplacement get() = prefs.getBoolean("$subscription.replace", false)

    @Suppress("DEPRECATION")
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val builder = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setUserAuthenticationRequired(true)
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            builder.setUserAuthenticationParameters(15, KeyProperties.AUTH_DEVICE_CREDENTIAL)
        } else {
            builder.setUserAuthenticationValidityDurationSeconds(15)
        }

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
                .putString("$subscription.iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
                .remove("$subscription.replace").putInt("$subscription.version", 3).remove("$subscription.automatic").commit())
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); legacyAliases.forEach { deleteEntry(it) } }
        } finally { pin.fill('\u0000') }
    }
    fun unlock(): CharArray {
        check(saved)
        val readAlias = alias
        val readKey = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.getKey(readAlias, null) as? SecretKey
            ?: error("Missing PIN key")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, readKey, GCMParameterSpec(128, Base64.decode(prefs.getString("$subscription.iv", ""), Base64.NO_WRAP)))
            updateAAD(readAlias.toByteArray())
        }
        val plain = cipher.doFinal(Base64.decode(prefs.getString("$subscription.data", ""), Base64.NO_WRAP))
        return try {
            check(plain.size == 4 && plain.all { it.toInt() in 48..57 })
            CharArray(plain.size) { plain[it].toInt().toChar() }
        } finally { plain.fill(0) }
    }
    fun forget() {
        check(prefs.edit().remove("$subscription.data").remove("$subscription.iv").remove("$subscription.automatic").remove("$subscription.version").remove("$subscription.replace").commit())
        KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias); legacyAliases.forEach { deleteEntry(it) } }
    }
    companion object {
        fun removeLegacy(context: Context) {
            context.getSharedPreferences("bank_pins", Context.MODE_PRIVATE).all.keys
                .mapNotNull { it.substringBefore('.').toIntOrNull() }.distinct().forEach { BankPinVault(context, it) }
        }
        fun clear(context: Context, subscription: Int?) {
            if (subscription != null) BankPinVault(context, subscription).forget()
            else context.getSharedPreferences("bank_pins", Context.MODE_PRIVATE).all.keys
                .mapNotNull { it.substringBefore('.').toIntOrNull() }.distinct().forEach { BankPinVault(context, it).forget() }
        }
    }
}
