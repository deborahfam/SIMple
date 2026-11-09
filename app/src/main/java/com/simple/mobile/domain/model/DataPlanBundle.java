package com.simple.mobile.domain.model;

import java.time.Instant;

/** Null means unknown; unlimited is distinct from a zero or unknown quota. */
public record DataPlanBundle(String bundleKey, String displayName, Long remainingBytes,
                             Long totalBytes, boolean unlimited, Instant expiresAt) {
    public DataPlanBundle {
        if (remainingBytes != null && remainingBytes < 0 || totalBytes != null && totalBytes < 0)
            throw new IllegalArgumentException("Negative quota");
        if (remainingBytes != null && totalBytes != null && remainingBytes > totalBytes)
            throw new IllegalArgumentException("Remaining exceeds total");
        if (unlimited && (remainingBytes != null || totalBytes != null))
            throw new IllegalArgumentException("Unlimited quota must not have byte limits");
    }
}
