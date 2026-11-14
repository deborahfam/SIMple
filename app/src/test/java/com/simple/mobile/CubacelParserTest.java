package com.simple.mobile;

import com.simple.mobile.data.parser.CubacelParser;
import com.simple.mobile.domain.carrier.CubacelQuery;
import org.junit.Test;
import static org.junit.Assert.*;

/** Synthetic redacted format fixtures: not a claim of live carrier coverage. */
public class CubacelParserTest {
    private final CubacelParser parser = new CubacelParser();
    @Test public void balanceWithCommaAndExpiry() throws Exception {
        var r = parser.parse(CubacelQuery.BALANCE, "Su saldo es: 123,45 CUP. Vence el 30/12/2026.", 1);
        assertEquals("123.45", r.balanceCup()); assertNotNull(r.expiresAt());
    }
    @Test public void keepsRestrictedBucketsSeparate() throws Exception {
        var r = parser.parse(CubacelQuery.DATA, "Internet: 1.25 GB; Nacional: 300 MB; LTE: 512 MB", 1);
        assertEquals(3, r.bundles().size());
        assertEquals(Long.valueOf(1_250_000_000L), r.bundles().get(0).bytes());
        assertEquals("national", r.bundles().get(1).kind());
    }
    @Test public void trailingRestrictionsAreNotGeneralData() throws Exception {
        var r = parser.parse(CubacelQuery.DATA, "Dispone de 500 MB para navegacion nacional", 1);
        assertEquals("national", r.bundles().get(0).kind());
    }
    @Test public void voiceAndSmsAndZero() throws Exception {
        var r = parser.parse(CubacelQuery.VOICE, "Dispone de 12 min 30 seg; 25 SMS", 1);
        assertEquals(Long.valueOf(750), r.voiceSeconds()); assertEquals(Long.valueOf(25), r.smsCount());
        assertEquals("0", parser.parse(CubacelQuery.BALANCE, "Saldo: 0 CUP", 1).balanceCup());
    }
    @Test public void rechargeIsNotBalance() throws Exception {
        var r = parser.parse(CubacelQuery.RECHARGE_STATUS, "Recibido: 100 CUP; Disponible para recargar: 260 CUP", 1);
        assertNull(r.balanceCup()); assertEquals("260", r.availableRechargeCup());
    }
    @Test public void unlimitedIsNotZero() throws Exception {
        var r = parser.parse(CubacelQuery.BONUSES_AND_USD_PLANS, "WhatsApp ilimitado. Vence: 20/12/2026", 1);
        assertTrue(r.bundles().get(0).unlimited()); assertNull(r.bundles().get(0).bytes());
    }
    @Test public void refusesAmbiguityAndErrors() throws Exception {
        reject(CubacelQuery.BALANCE, "Saldo insuficiente. Recargue 100 CUP");
        reject(CubacelQuery.BALANCE, "Saldo: 1,234.50 CUP");
        reject(CubacelQuery.BALANCE, "Saldo: 1 CUP. Vence: 31/02/2026");
        reject(CubacelQuery.BALANCE, "Saldo: 1 CUC");
        reject(CubacelQuery.DATA, "1 GB y 2 GB");
        reject(CubacelQuery.DATA, "Internet: 1 GB; Internet: 2 GB");
        reject(CubacelQuery.DATA, "Su numero es 5351234567");
        reject(CubacelQuery.VOICE, "Usted dispone de 01:99:30 MIN NAC");
        reject(CubacelQuery.BALANCE, "x".repeat(4097));
        reject(CubacelQuery.DATA, "ETECSA Promo Internacional: Internet: 25GB + Datos Ilimitados. Si recibes 2 recargas ganas MIN y SMS.");
    }
    @Test public void observedPlanReceiptSavesActualBalanceNotDebitOrQuota() throws Exception {
        var r = parser.parseSms("ETECSA Plan Nacional: Se ha comprado Plan 8GB+80MIN+90SMS valido por 35 dias, se activa con el primer uso. Se desconto 400.00 CUP. Saldo actual 2.50 CUP", 1).get(0);
        assertEquals("2.5", r.balanceCup());
        assertEquals("SMS", r.source());
        assertEquals(Long.valueOf(8_000_000_000L), r.purchase().totalBytes());
        assertTrue(r.bundles().isEmpty());
        assertTrue(r.purchase().startsOnFirstUse());
        assertNull(r.expiresAt());
    }
    @Test public void observedTransferNoticeUsesBalanceNotTransferAmount() throws Exception {
        var r = parser.parseSms("Usted ha transferido 100 CUP al numero [REDACTED]. Saldo principal 50.25 CUP, linea activa hasta 2027-09-08, vence 2028-03-06", 1).get(0);
        assertEquals("50.25", r.balanceCup());
        assertNotEquals(r.expiresAt(), r.lineClosesAt());
    }
    @Test public void actualPromotionAndCreditAdvertisementAreRejected() throws Exception {
        for (String body : new String[]{"ETECSA Promo Internacional: Del 1 al 31 de octubre Datos ilimitados las 24 horas", "Usted puede seguir comunicado. Solicite el servicio Adelanta Saldo, marque *234#."}) {
            try { parser.parseSms(body, 1); fail("Advertisement must not become a reading"); }
            catch (CubacelParser.ParseException expected) { }
        }
    }
    @Test public void olderPurchaseFormatsKeepBoughtQuotasSeparate() throws Exception {
        String[] bodies = {
            "ETECSA Plan Nacional: Ha recibido Plan 8GB valido por 35 dias, se activa con el primer uso.",
            "Ha comprado un plan de 8GB +80MIN +90SMS valido por 35 dias, se activara con el primer uso.",
            "Ha comprado 8GB +2GB LTE +80MIN +90SMS valido por 35 dias, se activara con el primer uso. Se desconto 400 de su cuenta principal. Saldo actual 5.25",
            "Ha comprado un plan de 80 min valido por 35 dias, se activara con el primer uso. Se desconto 100 de su cuenta principal. Saldo actual 5.25"};
        for (String body : bodies) {
            var r = parser.parseSms(body, 1800000000000L).get(0);
            assertNotNull(r.purchase()); assertTrue(r.bundles().isEmpty());
            assertEquals(35, r.purchase().validityDays());
        }
        var lte = parser.parseSms(bodies[2], 1800000000000L).get(0);
        assertEquals(Long.valueOf(2_000_000_000L), lte.purchase().lteBytes());
        assertEquals("5.25", lte.balanceCup());
    }
    @Test public void rechargeReceiptIsNotCurrentBalance() throws Exception {
        var r = parser.parseSms("Su cuenta ha sido recargada con 50 CUP. Transaccion [REDACTED]. Para consultar su saldo llame al *222#", 1).get(0);
        assertNull(r.balanceCup()); assertEquals("50", r.receivedRechargeCup());
    }
    @Test public void actualUssdSummaryClockAndShortDates() throws Exception {
        var r = parser.parse(CubacelQuery.BALANCE, "Saldo: 2.50 CUP. Datos: 512.00 MB. Voz: 01:02:30. SMS: 0070. Linea activa hasta 10-11-26 vence 10-05-27.", 1);
        assertEquals("2.5", r.balanceCup());
        assertEquals(Long.valueOf(3750), r.voiceSeconds());
        assertEquals(Long.valueOf(70), r.smsCount());
        assertEquals(Long.valueOf(512000000), r.bundles().get(0).bytes());
        assertNotEquals(r.expiresAt(), r.lineClosesAt());
    }
    @Test public void actualUssdVoiceClockIsNotLastSegmentMinutes() throws Exception {
        var r = parser.parse(CubacelQuery.VOICE, "Usted dispone de 01:02:30 MIN NAC validos por 30 dias", 1);
        assertEquals(Long.valueOf(3750), r.voiceSeconds());
    }
    @Test public void actualNationalBonusDateAndBucket() throws Exception {
        var r = parser.parse(CubacelQuery.BONUSES_AND_USD_PLANS, "Datos.cu 300 MB vence 10-12-26.", 1);
        assertEquals("national", r.bundles().get(0).kind());
        assertNotNull(r.bundles().get(0).expiresAt());
    }
    @Test public void actualRechargeCapIsZeroAndDateIsAfterNamedDay() throws Exception {
        var r = parser.parse(CubacelQuery.RECHARGE_STATUS, "Ud ha alcanzado el monto de recarga permitido de 360 CUP. Puede recargar posterior al dia 10-11-26", 1);
        assertEquals("0", r.availableRechargeCup());
        assertNull(r.receivedRechargeCup());
        var date = java.time.Instant.ofEpochMilli(r.nextRechargeAt()).atZone(java.time.ZoneId.of("America/Havana")).toLocalDate();
        assertEquals(java.time.LocalDate.of(2026, 11, 11), date);
        assertNull(r.expiresAt());
    }
    @Test public void partialRechargeCapacityAndInclusiveOpeningDate() throws Exception {
        var r = parser.parse(CubacelQuery.RECHARGE_STATUS, "Ud ha recargado 100 CUP. Puede recargar hasta 260 CUP. Puede recargar a partir del dia 10-11-26", 1);
        assertEquals("100", r.receivedRechargeCup()); assertEquals("260", r.availableRechargeCup());
        assertEquals(java.time.LocalDate.of(2026, 11, 10), java.time.Instant.ofEpochMilli(r.nextRechargeAt()).atZone(java.time.ZoneId.of("America/Havana")).toLocalDate());
    }
    private void reject(CubacelQuery kind, String text) throws Exception {
        try { parser.parse(kind, text, 1); fail("Expected rejection"); }
        catch (CubacelParser.ParseException expected) { }
    }
}
