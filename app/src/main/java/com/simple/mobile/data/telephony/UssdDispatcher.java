package com.simple.mobile.data.telephony;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import com.simple.mobile.domain.model.BalanceSnapshot;
import com.simple.mobile.domain.carrier.CubacelQuery;
import com.simple.mobile.domain.query.CarrierResponseParser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** One application-scoped instance serializes USSD across BOTH SIMs.
 * Caller selects a verified Cubacel query after explicit user confirmation.
 */
public final class UssdDispatcher {
    public enum Failure { PERMISSION, INACTIVE_SIM, UNSUPPORTED, BUSY, TIMEOUT, NETWORK, PARSE }
    public static final class QueryException extends Exception {
        public final Failure failure;
        QueryException(Failure failure) { super(failure.name()); this.failure = failure; }
    }
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Executor parserExecutor;
    private boolean busy;

    public UssdDispatcher(Context context, Executor parserExecutor) {
        this.context = context.getApplicationContext();
        this.parserExecutor = parserExecutor;
    }

    public CompletableFuture<BalanceSnapshot> query(UUID accountId, int subscriptionId,
            CubacelQuery query, CarrierResponseParser parser) {
        CompletableFuture<BalanceSnapshot> result = new CompletableFuture<>();
        if (query == null || parser == null || accountId == null) {
            result.completeExceptionally(new IllegalArgumentException("Query, parser and account required"));
            return result;
        }
        handler.post(() -> dispatch(accountId, subscriptionId, query.code(), parser, result));
        return result;
    }

    private void dispatch(UUID accountId, int subscriptionId, String code,
            CarrierResponseParser parser, CompletableFuture<BalanceSnapshot> result) {
        if (result.isDone()) return;
        if (busy) { fail(result, Failure.BUSY); return; }
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            fail(result, Failure.PERMISSION); return;
        }
        TelephonyManager base = context.getSystemService(TelephonyManager.class);
        SubscriptionManager subscriptions = context.getSystemService(SubscriptionManager.class);
        if (base == null || subscriptions == null) { fail(result, Failure.UNSUPPORTED); return; }
        try {
            List<SubscriptionInfo> active = subscriptions.getActiveSubscriptionInfoList();
            if (active == null || active.stream().noneMatch(s -> s.getSubscriptionId() == subscriptionId)) {
                fail(result, Failure.INACTIVE_SIM); return;
            }
            busy = true;
            Runnable timeout = () -> fail(result, Failure.TIMEOUT);
            handler.postDelayed(timeout, 30_000);
            // Timeout/cancellation cannot cancel the modem session. Keep busy until callback;
            // never retry automatically, switch SIMs, or claim cancellation stopped a request.
            base.createForSubscriptionId(subscriptionId).sendUssdRequest(code,
                    new TelephonyManager.UssdResponseCallback() {
                        @Override public void onReceiveUssdResponse(TelephonyManager manager,
                                String request, CharSequence response) {
                            busy = false;
                            handler.removeCallbacks(timeout);
                            if (result.isDone()) return;
                            Instant receivedAt = Instant.now();
                            try {
                                parserExecutor.execute(() -> {
                                    if (result.isDone()) return;
                                    try { result.complete(parser.parse(accountId, receivedAt, response)); }
                                    catch (Exception ignored) { fail(result, Failure.PARSE); }
                                });
                            } catch (RuntimeException ignored) { fail(result, Failure.PARSE); }
                        }
                        @Override public void onReceiveUssdResponseFailed(TelephonyManager manager,
                                String request, int failureCode) {
                            busy = false;
                            handler.removeCallbacks(timeout);
                            fail(result, failureCode == TelephonyManager.USSD_ERROR_SERVICE_UNAVAIL
                                    ? Failure.UNSUPPORTED : Failure.NETWORK);
                        }
                    }, handler);
        } catch (SecurityException ignored) {
            busy = false;
            fail(result, Failure.PERMISSION);
        } catch (UnsupportedOperationException ignored) {
            busy = false;
            fail(result, Failure.UNSUPPORTED);
        } catch (RuntimeException ignored) {
            busy = false;
            fail(result, Failure.NETWORK);
        }
    }

    private static void fail(CompletableFuture<?> result, Failure failure) {
        result.completeExceptionally(new QueryException(failure));
    }
}
