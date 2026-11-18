package com.simple.mobile.domain.recharge

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class MetropolitanoResponseParserTest {
    // Observed PAGOxMOVIL format; the destination has been replaced with fictitious digits.
    private val rejection = "Fallo la recarga del movil 5355500000 alcanzo el monto limite de recarga permitido en 30 dias (360 CUP), puede recargar posterior al dia 14-12-2026."
    @Test fun acceptsSixPrefixInBankNotice() {
        assertEquals("65500000", MetropolitanoResponseParser.quotaRejection("PAGOxMOVIL", rejection.replace("5355500000", "+5365500000"))?.destination)
    }
    @Test fun extractsQuotaAndAfterDateWithoutInventingPaymentAmount() {
        val result = MetropolitanoResponseParser.quotaRejection("PAGOxMOVIL", rejection)!!
        assertEquals("55500000", result.destination)
        assertEquals(BigDecimal("360"), result.limitCup)
        assertEquals(LocalDate.of(2026, 12, 14), result.afterDate)
    }
    @Test fun acceptsNationalAndInternationalDestinationWithoutTruncating() {
        for (number in listOf("55500000", "5355500000", "+5355500000")) {
            assertEquals("55500000", MetropolitanoResponseParser.quotaRejection("pagoxmovil", rejection.replace("5355500000", number))!!.destination)
        }
        assertNull(MetropolitanoResponseParser.quotaRejection("PAGOxMOVIL", rejection.replace("5355500000", "53555000001")))
    }
    @Test fun handlesAccentsCaseAndMultipartWhitespace() {
        val message = rejection.uppercase().replace("FALLO", "FALLÓ").replace("MOVIL", "MÓVIL").replace(" LIMITE ", " LÍMITE ").replace(" DIAS ", " DÍAS ").replace("30 DÍAS", "30\nDÍAS")
        assertNotNull(MetropolitanoResponseParser.quotaRejection("PAGOxMOVIL", message))
    }
    @Test fun rejectsOtherSendersAndDifferentMeanings() {
        assertNull(MetropolitanoResponseParser.quotaRejection("ETEC SA", rejection))
        assertNull(MetropolitanoResponseParser.quotaRejection(null, rejection))
        assertNull(MetropolitanoResponseParser.quotaRejection("PAGOxMOVIL", rejection.replace("Fallo", "No fallo")))
        assertNull(MetropolitanoResponseParser.quotaRejection("PAGOxMOVIL", rejection.replace("30 dias", "1 dia")))
        assertNull(MetropolitanoResponseParser.quotaRejection("PAGOxMOVIL", rejection + " Otra operacion completada."))
    }
    @Test fun rejectsInvalidDatesAndAmounts() {
        assertNull(MetropolitanoResponseParser.quotaRejection("PAGOxMOVIL", rejection.replace("14-12-2026", "31-02-2026")))
        assertNull(MetropolitanoResponseParser.quotaRejection("PAGOxMOVIL", rejection.replace("360 CUP", "0 CUP")))
        assertNull(MetropolitanoResponseParser.quotaRejection("PAGOxMOVIL", rejection.replace("360 CUP", "360.001 CUP")))
    }
    @Test fun processingIsNotARechargeOutcome() {
        val acknowledgement = "Su solicitud esta siendo procesada, espere un sms con los datos solicitados y detalles de la transaccion realizada."
        assertTrue(MetropolitanoResponseParser.isProcessingAcknowledgement(acknowledgement))
        assertNull(MetropolitanoResponseParser.quotaRejection("PAGOxMOVIL", acknowledgement))
        assertFalse(MetropolitanoResponseParser.isProcessingAcknowledgement(rejection))
        assertFalse(MetropolitanoResponseParser.isProcessingAcknowledgement("Su solicitud no esta siendo procesada"))
    }
}
