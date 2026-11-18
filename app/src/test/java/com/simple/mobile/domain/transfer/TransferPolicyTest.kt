package com.simple.mobile.domain.transfer

import org.junit.Assert.*
import org.junit.Test

class TransferPolicyTest {
    @Test fun fixedFeeIsAddedToSenderWithoutReducingDestination() {
        assertEquals(TransferPolicy.Totals("100", "105"), TransferPolicy.totals("100"))
        assertEquals(TransferPolicy.Totals("360", "365"), TransferPolicy.totals("360"))
        assertEquals(TransferPolicy.Totals("0.01", "5.01"), TransferPolicy.totals("0,01"))
        assertNull(TransferPolicy.totals("0"))
        assertNull(TransferPolicy.totals("1.234"))
    }

    @Test fun acceptsSixPrefixWithCountryCode() {
        assertEquals("61234567", TransferPolicy.phone("+53 6123-4567"))
    }

    @Test fun normalizesLocalAndInternationalMobileNumbers() {
        listOf("51234567", "+53 5123-4567", "0053 (5123) 4567").forEach { assertEquals("51234567", TransferPolicy.phone(it)) }
    }
    @Test fun rejectsDialerCommandsForeignNumbersAndIncompleteRecipients() {
        listOf("*234#", "51234567#", "+1 51234567", "71234567", "5123456", "512345678", "٥١٢٣٤٥٦٧", "abc51234567").forEach { assertNull(TransferPolicy.phone(it)) }
    }
    @Test fun amountsMustBePositiveDecimalValuesWithoutCodeInjection() {
        assertEquals("10.5", TransferPolicy.amount("10,50"))
        assertEquals("100", TransferPolicy.amount("00100.00"))
        listOf("0", "-1", "1e3", "5*1234#", "NaN", "1.001", "1,000.00", "1000000000").forEach { assertNull(TransferPolicy.amount(it)) }
    }
    @Test fun validatesNamesWithoutInvisibleControls() {
        assertEquals("Mamá", TransferPolicy.name(" Mamá "))
        assertNull(TransferPolicy.name(" "))
        assertNull(TransferPolicy.name("A\nB"))
        assertNull(TransferPolicy.name("a".repeat(41)))
    }
}
