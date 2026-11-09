package com.simple.mobile.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import java.util.Collections;
import java.util.List;

/** Device subscription access. Carrier balances belong in a separate carrier integration. */
public final class SimRepository {
    private final Context context;

    public SimRepository(Context context) {
        this.context = context.getApplicationContext();
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
