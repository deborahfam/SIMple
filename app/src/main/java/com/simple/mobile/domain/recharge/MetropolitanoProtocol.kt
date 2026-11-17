package com.simple.mobile.domain.recharge

import java.security.SecureRandom

/** Numeric USSD wire format observed in ETECSA's Transfermovil v.1.260416 APK.
 * Only Metropolitano authentication and associated CUP-account mobile recharge are exposed.
 * Not encryption for storage; never log either input or generated command.
 * Integer CUP only: decimal fields use a different legacy encoding, intentionally unsupported.
 */
object MetropolitanoProtocol {
    const val VERSION = "1260416"
    private val random = SecureRandom()
    private val substitution = intArrayOf(53,36,12,3,76,82,47,65,21,1,84,11,24,23,80,6,20,52,16,70,
        92,60,48,64,28,46,31,96,15,63,98,50,71,43,95,58,94,25,51,19,67,83,29,42,10,34,86,93,77,79,
        8,69,81,97,40,2,4,91,99,75,13,33,90,88,30,49,85,87,66,38,72,56,45,78,54,37,26,61,14,73,
        68,7,59,5,17,62,18,55,44,89,9,0,39,22,32,74,35,27,57,41)
    fun phone(value: String): String? = value.trim().takeIf { Regex("5[0-9]{7}").matches(it) }
    fun amount(value: String): String? = value.trim().takeIf { Regex("[0-9]{1,3}").matches(it) }
        ?.toIntOrNull()?.takeIf { it in 1..360 }?.toString()
    fun validPin(pin: CharArray) = pin.size == 4 && pin.all { it in '0'..'9' }
    @JvmStatic fun authenticate(pin: CharArray): String {
        require(validPin(pin)) { "PIN inválido" }
        return command("40", listOf("03", String(pin)), random.nextInt(100))
    }
    @JvmStatic fun recharge(phone: String, cup: String): String {
        require(phone(phone) == phone && amount(cup) == cup) { "Datos de recarga inválidos" }
        return command("54", listOf(phone, cup, "1"), random.nextInt(100))
    }
    internal fun command(operation: String, fields: List<String>, seed: Int): String {
        require(operation in setOf("40", "54"))
        return "*444*$operation*${encode(fields, seed)}*$VERSION#"
    }
    internal fun encode(fields: List<String>, seed: Int): String {
        require(seed in 0..99 && fields.isNotEmpty())
        val singleDigits = substitution.filter { it < 10 }
        return seed.toString().padStart(2, '0') + fields.joinToString("*") { field ->
            require(field.length in 1..8 && field.all { it in '0'..'9' })
            val swapped = buildString {
                for (offset in 0 until field.length - 1 step 2) {
                    append(substitution[field.substring(offset, offset + 2).toInt()].toString().padStart(2, '0'))
                }
                if (field.length % 2 == 1) append(singleDigits[field.last() - '0'])
            }
            var position = seed
            val mask = buildString {
                repeat(field.length) {
                    if (position == substitution.lastIndex) position = 0
                    append(substitution[position++])
                }
            }.take(field.length)
            field.length.toString().padStart(2, '0') + (swapped.toLong() xor mask.toLong()).toString()
        }
    }
}
