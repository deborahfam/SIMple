package com.simple.mobile.data.plans

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.simple.mobile.domain.plans.*
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypted last purchase intent/outcome; no raw USSD reply, PIN or subscriber identity. */
class PlanPurchaseStore(context: Context, name: String = "plan_purchase") : PlanAttemptStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val aad = name.toByteArray()
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("simple.plan.purchase.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("simple.plan.purchase.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized override fun save(attempt: PlanAttempt) {
        val json = JSONObject().put("id", attempt.id).put("sub", attempt.subscription).put("plan", attempt.planId)
            .put("at", attempt.at).put("phase", attempt.phase.name)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(aad) }
        val plain = json.toString().toByteArray()
        val data = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(data, Base64.NO_WRAP)).commit())
    }
    @Synchronized override fun load(): PlanAttempt? {
        val data = prefs.getString("data", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP))); updateAAD(aad)
        }
        val plain = cipher.doFinal(Base64.decode(data, Base64.NO_WRAP))
        val j = try { JSONObject(String(plain)) } finally { plain.fill(0) }
        return PlanAttempt(j.getString("id"), j.getString("plan"), j.getInt("sub"),
            j.getLong("at"), PurchasePhase.valueOf(j.getString("phase")))
    }
    @Synchronized override fun clear() { check(prefs.edit().clear().commit()) }
}
