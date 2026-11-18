package com.simple.mobile.domain.plans

import com.simple.mobile.domain.recharge.MetropolitanoProtocol
import org.junit.Assert.*
import org.junit.Test

class BankPlanCatalogTest {
    @Test fun preservesLiveMenuOrderAndFractionalPrices() {
        val voice = BankPlanCatalog.offers(BankPlanCategory.VOICE)
        assertEquals(listOf(5, 10, 15, 25, 40), voice.map { it.quantity })
        assertEquals(listOf("37,5 CUP", "72,5 CUP", "105 CUP", "162,5 CUP", "250 CUP"), voice.map { it.priceLabel })
        assertEquals(listOf(1, 2, 3, 4, 5), voice.map { it.option })
        assertEquals(listOf(20, 50, 90, 120, 200), BankPlanCatalog.offers(BankPlanCategory.SMS).map { it.quantity })
    }

    @Test fun opensOnlyTheCategoryWithAssociatedCupAccount() {
        BankPlanCategory.entries.forEach { category ->
            val command = MetropolitanoProtocol.planMenu("65500000", category)
            val chunks = command.split('*')
            // Five fields: beneficiary, category, catalog version, CUP, associated account.
            // No selected offer, amount, or purchase confirmation is sent.
            assertEquals(9, chunks.size)
            assertEquals("444", chunks[1])
            assertEquals("6", chunks[2])
            val seed = chunks[3].take(2).toInt()
            assertEquals("*444*6*${MetropolitanoProtocol.encode(listOf("65500000", category.wireId, "2", "1", "0000"), seed)}*1260416#", command)
        }
        assertThrows(IllegalArgumentException::class.java) { MetropolitanoProtocol.planMenu("55500000*1#", BankPlanCategory.VOICE) }
    }
}
