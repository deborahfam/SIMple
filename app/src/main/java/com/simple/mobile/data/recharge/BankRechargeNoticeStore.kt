package com.simple.mobile.data.recharge

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.simple.mobile.domain.recharge.MetropolitanoResponseParser
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.MessageDigest
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class BankRechargeNotice(val id: String, val receivingSubscription: Int, val destination: String,
    val limitCup: String, val afterDate: LocalDate, val receivedAt: Long)

/** Bounded, encrypted observations. Never a payment ledger or an automatic retry queue. */
class BankRechargeNoticeStore(context: Context, name: String = "bank_recharge_notices") {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val aad = name.toByteArray()
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun history(): List<BankRechargeNotice> {
        val data = prefs.getString("data", null) ?: return emptyList()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)))
            updateAAD(aad)
        }
        val plain = cipher.doFinal(Base64.decode(data, Base64.NO_WRAP))
        val json = try { JSONArray(String(plain)) } finally { plain.fill(0) }
        return (0 until json.length()).map { i -> json.getJSONObject(i).let {
            BankRechargeNotice(it.getString("id"), it.getInt("sub"), it.getString("phone"), it.getString("limit"),
                LocalDate.parse(it.getString("after")), it.getLong("at"))
        } }.sortedByDescending { it.receivedAt }
    }
    private fun write(entries: List<BankRechargeNotice>) {
        val array = JSONArray()
        entries.forEach { a -> array.put(JSONObject().put("id", a.id).put("sub", a.receivingSubscription)
            .put("phone", a.destination).put("limit", a.limitCup).put("after", a.afterDate.toString()).put("at", a.receivedAt)) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()); updateAAD(aad) }
        val plain = array.toString().toByteArray()
        val encrypted = try { cipher.doFinal(plain) } finally { plain.fill(0) }
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("data", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
    }
    @Synchronized fun ingest(sender: String?, body: String, receivingSubscription: Int, at: Long): Boolean {
        if (receivingSubscription < 0 || at <= 0) return false
        val rejection = MetropolitanoResponseParser.quotaRejection(sender, body) ?: return false
        val limit = rejection.limitCup.stripTrailingZeros().toPlainString()
        val id = MessageDigest.getInstance("SHA-256").digest(
            "$receivingSubscription|$at|${rejection.destination}|$limit|${rejection.afterDate}".toByteArray()
        ).joinToString("") { "%02x".format(it) }
        val entries = history()
        if (entries.any { it.id == id }) return false
        val retained = (entries + BankRechargeNotice(id, receivingSubscription, rejection.destination, limit, rejection.afterDate, at))
            .sortedByDescending { it.receivedAt }.take(30)
        if (retained.none { it.id == id }) return false
        write(retained)
        return true
    }
    @Synchronized fun clear(subscription: Int? = null) {
        if (subscription == null) check(prefs.edit().clear().commit())
        else write(history().filterNot { it.receivingSubscription == subscription })
    }
    companion object { private const val ALIAS = "simple.bank.recharge.notices.v1" }
}
