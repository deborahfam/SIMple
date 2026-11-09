package com.simple.mobile.domain.query;

import com.simple.mobile.domain.model.BalanceSnapshot;
import java.time.Instant;
import java.util.UUID;

/** Implementations must not retain, log or return raw carrier text. */
public interface CarrierResponseParser {
    BalanceSnapshot parse(UUID accountId, Instant capturedAt, CharSequence response)
            throws UnrecognizedResponse;

    final class UnrecognizedResponse extends Exception {
        public UnrecognizedResponse() { super("Carrier response did not match the selected profile"); }
    }
}
