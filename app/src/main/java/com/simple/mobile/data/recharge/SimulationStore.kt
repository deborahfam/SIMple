package com.simple.mobile.data.recharge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.simple.mobile.domain.recharge.RechargeSimulation as Demo
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate encrypted demo state. No PIN, carrier readings or bank messages are written here. */
class SimulationStore(context: Context, name: String = "recharge_simulation") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val aad = name.toByteArray()
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("simple.simulation.state.v1", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("simple.simulation.state.v1", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(a: Demo.Attempt) {
        val json = JSONObject().put("id", a.id).put("sub", a.subscription).put("line", a.line).put("bank", a.bank.name)
            .put("phone", a.phone).put("cup", a.cup).put("at", a.createdAt).put("phase", a.phase.name)
            .put("sends", a.simulatedSends).put("reference", a.resultReference)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(aad) }
        val plain = json.toString().toByteArray()
        val data = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(data, Base64.NO_WRAP)).commit())
    }
    @Synchronized fun load(): Demo.Attempt? {
        val data = prefs.getString("data", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP))); updateAAD(aad)
        }
        val plain = cipher.doFinal(Base64.decode(data, Base64.NO_WRAP))
        val j = try { JSONObject(String(plain)) } finally { plain.fill(0) }
        return Demo.Attempt(j.getString("id"), j.getInt("sub"), j.getString("line"), Demo.Bank.valueOf(j.getString("bank")),
            j.getString("phone"), j.getString("cup"), j.getLong("at"), Demo.Phase.valueOf(j.getString("phase")), j.getInt("sends"),
            j.optString("reference").takeIf { it.isNotBlank() })
    }
    @Synchronized fun clear() { check(prefs.edit().clear().commit()) }
}
