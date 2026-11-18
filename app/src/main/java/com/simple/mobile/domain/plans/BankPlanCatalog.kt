package com.simple.mobile.domain.plans

import java.math.BigDecimal

enum class BankPlanCategory(val wireId: String, val title: String, val unit: String) {
    VOICE("3", "Voz", "min"), SMS("4", "SMS", "SMS")
}

data class BankPlanOffer(val option: Int, val quantity: Int, val cup: BigDecimal) {
    val priceLabel: String get() = cup.stripTrailingZeros().toPlainString().replace('.', ',') + " CUP"
}

/** Observed in Metropolitano's live category menus on 2026-11-18.
 * Display references only; the bank menu remains authoritative. No final purchase codes.
 */
object BankPlanCatalog {
    fun offers(category: BankPlanCategory): List<BankPlanOffer> = when (category) {
        BankPlanCategory.VOICE -> listOf(5 to "37.50", 10 to "72.50", 15 to "105", 25 to "162.50", 40 to "250")
        BankPlanCategory.SMS -> listOf(20 to "15", 50 to "30", 90 to "50", 120 to "60", 200 to "90")
    }.mapIndexed { index, (quantity, price) -> BankPlanOffer(index + 1, quantity, BigDecimal(price)) }
}
