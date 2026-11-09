package com.simple.mobile.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

public record BalanceSnapshot(UUID snapshotId, UUID accountId, Instant capturedAt,
                              BigDecimal monetaryBalance, Currency currency,
                              Long remainingVoiceSeconds, Instant balanceExpiresAt,
                              List<DataPlanBundle> bundles, Source source,
                              String parserVersion) {
    public enum Source { USSD, USER_PROVIDED_SMS, CARRIER_API }
    public BalanceSnapshot {
        bundles = List.copyOf(bundles);
        if ((monetaryBalance == null) != (currency == null))
            throw new IllegalArgumentException("Money requires currency");
        if (remainingVoiceSeconds != null && remainingVoiceSeconds < 0)
            throw new IllegalArgumentException("Negative voice allowance");
    }
}
