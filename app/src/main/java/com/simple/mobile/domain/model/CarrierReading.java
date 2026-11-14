package com.simple.mobile.domain.model;

import com.simple.mobile.domain.carrier.CubacelQuery;
import java.util.List;

/** Only typed carrier values cross into persistence. Unknown values remain null. */
public record CarrierReading(CubacelQuery query, long receivedAt, String balanceCup,
                             Long voiceSeconds, Long smsCount, List<Bundle> bundles,
                             Long expiresAt, String receivedRechargeCup, String availableRechargeCup,
                             String source, Purchase purchase, Long lineClosesAt, Long nextRechargeAt) {
    public CarrierReading(CubacelQuery query, long at, String balance, Long voice, Long sms,
                          List<Bundle> bundles, Long expiry, String received, String available) {
        this(query, at, balance, voice, sms, bundles, expiry, received, available, "USSD", null, null, null);
    }
    public CarrierReading asSms(Purchase purchase, Long closesAt) {
        return new CarrierReading(query, receivedAt, balanceCup, voiceSeconds, smsCount, bundles,
                expiresAt, receivedRechargeCup, availableRechargeCup, "SMS", purchase, closesAt, nextRechargeAt);
    }
    public record Purchase(Long totalBytes, Long lteBytes, Long voiceSeconds, Long smsCount, int validityDays,
                           boolean startsOnFirstUse) { }
    public CarrierReading { bundles = List.copyOf(bundles); }
    public record Bundle(String kind, Long bytes, boolean unlimited, Long expiresAt) { }
}
