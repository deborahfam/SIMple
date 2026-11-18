package com.simple.mobile.domain.transfer

import java.math.BigDecimal

data class Recipient(val id: String, val name: String, val phone: String, val favorite: Boolean)
data class TransferRecord(val id: String, val subscription: Int, val line: String, val name: String,
    val phone: String, val amount: String, val at: Long, val status: String)
data class TransferDraft(val subscription: Int, val line: String, val name: String, val phone: String, val amount: String)

object TransferPolicy {
    const val MENU = "*234#"
    const val DEFAULT_PIN = "1234"
    // ETECSA: https://www.etecsa.cu/es/movil/necesitas-saldo (checked 2026-11-17).
    // Fixed fee charged to the sender in addition to the amount received.
    data class Totals(val received: String, val debited: String)
    fun totals(input: String): Totals? = amount(input)?.let {
        Totals(it, it.toBigDecimal().add(publishedFee).stripTrailingZeros().toPlainString())
    }
    val publishedFee = BigDecimal("5")
    fun phone(input: String): String? {
        val clean = input.replace(Regex("[\\s()\\-]"), "")
        val national = when {
            clean.startsWith("+53") -> clean.drop(3)
            clean.startsWith("0053") -> clean.drop(4)
            else -> clean
        }
        return national.takeIf { it.matches(Regex("[56][0-9]{7}")) }
    }
    fun amount(input: String): String? {
        val text = input.trim().replace(',', '.')
        if (!text.matches(Regex("[0-9]{1,9}(\\.[0-9]{1,2})?"))) return null
        return text.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }?.stripTrailingZeros()?.toPlainString()
    }
    fun name(input: String): String? = input.trim().takeIf { it.isNotEmpty() && it.length <= 40 && it.none(Char::isISOControl) }
    val statuses = setOf("PREPARED", "OPENED", "USER_CONFIRMED", "NOT_COMPLETED", "OPEN_FAILED")
}
