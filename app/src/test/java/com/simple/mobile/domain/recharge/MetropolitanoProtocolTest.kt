package com.simple.mobile.domain.recharge

import org.junit.Assert.*
import org.junit.Test

class MetropolitanoProtocolTest {
    /** 600 vectors computed by the unmodified numeric codec in ETECSA's 1.260416 APK,
     * loaded by app_process in a SIM-less API 36 emulator. Fictitious input only.
     * All 100 random seeds, leading-zero PINs, phone boundaries and integer amounts.
     * These verify wire encoding, NOT acceptance or payment completion by the bank.
     */
    @Test fun matchesOfficialBinaryForEverySeed() {
        val vectors = javaClass.classLoader!!.getResourceAsStream("metropolitano-numeric-vectors.csv")!!
            .bufferedReader().use { it.readLines() }
        assertEquals(600, vectors.size)
        vectors.forEach { line ->
            val (seed, fields, expected) = line.split(',')
            assertEquals("seed=$seed fields=$fields", expected, MetropolitanoProtocol.encode(fields.split('*'), seed.toInt()))
        }
    }
    @Test fun operationEnvelopeAndCupAccountAreFixed() {
        assertEquals("*444*40*000254*042795*1260416#", MetropolitanoProtocol.command("40", listOf("03", "4826"), 0))
        val command = MetropolitanoProtocol.recharge("55500000", "120")
        assertTrue(command.startsWith("*444*54*")); assertTrue(command.endsWith("*1260416#"))
        assertEquals(6, command.count { it == '*' })
    }
    @Test fun rejectsInjectionFractionsUnsupportedAmountsAndPins() {
        listOf("", "0", "361", "120.00", "1,5", "1*54", "-1", "999999").forEach { assertNull(MetropolitanoProtocol.amount(it)) }
        assertNull(MetropolitanoProtocol.amount("0120"))
    }
    @Test fun acceptsMobileNumbersStartingWithFiveOrSix() {
        listOf("55500000", "65500000").forEach { phone ->
            assertEquals(phone, MetropolitanoProtocol.phone(phone))
            assertTrue(MetropolitanoProtocol.recharge(phone, "360").startsWith("*444*54*"))
        }
        assertNull(MetropolitanoProtocol.phone("75500000"))
    }
    @Test fun inputValidationAndCanonicalAmount() {
        assertEquals("1", MetropolitanoProtocol.amount("001"))
        assertEquals("360", MetropolitanoProtocol.amount("360"))
        listOf("+5355500000", "55500000#", "45500000", "５5500000").forEach { assertNull(MetropolitanoProtocol.phone(it)) }
        listOf("123", "12345", "123#", "１２３４").forEach { assertFalse(MetropolitanoProtocol.validPin(it.toCharArray())) }
        assertTrue(MetropolitanoProtocol.validPin("0000".toCharArray()))
        assertThrows(IllegalArgumentException::class.java) { MetropolitanoProtocol.recharge("55500000", "001") }
    }
}
