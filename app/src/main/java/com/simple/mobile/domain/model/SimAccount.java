package com.simple.mobile.domain.model;

import java.util.UUID;

/** subscriptionId is a current routing handle, never a durable SIM identity. */
public record SimAccount(UUID accountId, int subscriptionId, int slotIndex,
                         String carrierName, String carrierProfileId,
                         State state, int dataNetworkType) {
    public enum State { UNKNOWN, ABSENT, LOCKED, READY, RESTRICTED }
    // accountId is generated locally. After SIM replacement/reinstall, ask the user to
    // re-associate history; never silently associate it by slot or subscription ID.
}
