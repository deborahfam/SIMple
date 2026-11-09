package com.simple.mobile.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.simple.mobile.domain.model.SimAccount;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.IntFunction;
import java.util.Collections;
import java.util.List;

/** Device subscription access. Carrier balances belong in a separate carrier integration. */
public final class SimRepository {
    private final Context context;

    public SimRepository(Context context) {
        this.context = context.getApplicationContext();
    }

    /** Resolve local account IDs via explicit user association, never by persistent hardware IDs.
     * Only active subscriptions are returned; a locked or absent SIM may not be enumerable.
     */
    public List<SimAccount> accounts(IntFunction<UUID> accountForSubscription) {
        List<SimAccount> accounts = new ArrayList<>();
        TelephonyManager base = context.getSystemService(TelephonyManager.class);
        for (SubscriptionInfo info : activeSubscriptions()) {
            int network = TelephonyManager.NETWORK_TYPE_UNKNOWN;
            int state = TelephonyManager.SIM_STATE_UNKNOWN;
            if (base != null) {
                try {
                    state = base.getSimState(info.getSimSlotIndex());
                    network = base.createForSubscriptionId(info.getSubscriptionId()).getDataNetworkType();
                } catch (SecurityException | UnsupportedOperationException ignored) {
                    // A revoked permission or unavailable radio leaves metadata unknown.
                }
            }
            SimAccount.State mapped = switch (state) {
                case TelephonyManager.SIM_STATE_READY -> SimAccount.State.READY;
                case TelephonyManager.SIM_STATE_ABSENT -> SimAccount.State.ABSENT;
                case TelephonyManager.SIM_STATE_PIN_REQUIRED, TelephonyManager.SIM_STATE_PUK_REQUIRED,
                     TelephonyManager.SIM_STATE_NETWORK_LOCKED -> SimAccount.State.LOCKED;
                case TelephonyManager.SIM_STATE_CARD_RESTRICTED -> SimAccount.State.RESTRICTED;
                default -> SimAccount.State.UNKNOWN;
            };
            accounts.add(new SimAccount(accountForSubscription.apply(info.getSubscriptionId()),
                    info.getSubscriptionId(), info.getSimSlotIndex(),
                    String.valueOf(info.getCarrierName()), null, mapped, network));
        }
        return Collections.unmodifiableList(accounts);
    }

    public List<SubscriptionInfo> activeSubscriptions() {
        if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE)
                != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("Phone permission required");
        }
        SubscriptionManager manager = context.getSystemService(SubscriptionManager.class);
        if (manager == null) return Collections.emptyList();
        List<SubscriptionInfo> subscriptions = manager.getActiveSubscriptionInfoList();
        return subscriptions == null ? Collections.emptyList() : subscriptions;
    }
}
