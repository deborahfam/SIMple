package com.simple.mobile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.*;
import com.simple.mobile.data.SimRepository;
import com.simple.mobile.data.telephony.UssdDispatcher;
import com.simple.mobile.domain.carrier.CubacelQuery;
import java.math.BigDecimal;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public final class MainActivity extends Activity {
    private static final int PHONE_PERMISSION = 100;
    private static final int CALL_PERMISSION = 101;
    private static final int PURPLE = 0xFF6550C8, INK = 0xFF202039, MUTED = 0xFF71718A;
    private LinearLayout content;
    private ScrollView scroll;
    private SimRepository repository;
    private List<SubscriptionInfo> subscriptions = Collections.emptyList();
    private int selected = -1;
    private boolean checking;
    private boolean disposed;
    private final Map<Integer, String[]> readings = new HashMap<>();
    private final List<String> history = new ArrayList<>();
    private CompletableFuture<String> currentRequest;
    private AlertDialog responseDialog;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new SimRepository(this);
        if (state != null) {
            selected = state.getInt("selected", -1);
            Bundle values = state.getBundle("readings");
            if (values != null) for (String key : values.keySet())
                readings.put(Integer.parseInt(key), values.getStringArray(key));
            ArrayList<String> saved = state.getStringArrayList("history");
            if (saved != null) history.addAll(saved);
        }
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
    }

    @Override protected void onResume() { super.onResume(); reloadSims(); }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("selected", selected);
        Bundle values = new Bundle();
        for (var entry : readings.entrySet()) values.putStringArray(entry.getKey().toString(), entry.getValue());
        state.putBundle("readings", values);
        state.putStringArrayList("history", new ArrayList<>(history));
    }

    @Override protected void onDestroy() {
        disposed = true;
        if (currentRequest != null) { CompletableFuture<String> pending = currentRequest; currentRequest = null; pending.cancel(false); }
        if (responseDialog != null) responseDialog.dismiss();
        super.onDestroy();
    }

    private void reloadSims() {
        try {
            subscriptions = checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
                    ? repository.activeSubscriptions() : Collections.emptyList();
        } catch (SecurityException | UnsupportedOperationException ignored) { subscriptions = Collections.emptyList(); }
        readings.keySet().removeIf(id -> subscriptions.stream().noneMatch(s -> s.getSubscriptionId() == id));
        if (subscriptions.stream().noneMatch(s -> s.getSubscriptionId() == selected))
            selected = subscriptions.isEmpty() ? -1 : subscriptions.get(0).getSubscriptionId();
        render();
    }

    private void render() {
        int previousScroll = scroll.getScrollY();
        scroll.removeAllViews();
        content = column();
        content.setPadding(dp(20), dp(16), dp(20), dp(24));
        scroll.addView(content);
        LinearLayout header = row();
        text(header, R.string.app_name, 26, true);
        header.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        Button historyButton = button(header, R.string.history, v -> showHistory(), false);
        historyButton.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        historyButton.setContentDescription(getString(R.string.history));
        content.addView(header);
        text(content, R.string.tagline, 12, false);
        space(content, 18);
        text(content, R.string.overview, 28, true);
        text(content, R.string.intro, 13, false);
        space(content, 12);
        renderAccounts();

        LinearLayout hero = card(content);
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF7056DF, 0xFF3E3299});
        gradient.setCornerRadius(dp(24));
        hero.setBackground(gradient);
        TextView label = text(hero, R.string.balance, 14, true);
        label.setTextColor(0xFFE6E0FF);
        String[] values = readings.get(selected);
        LinearLayout amount = row();
        TextView balance = text(amount, values == null ? getString(R.string.empty_amount) : values[0], 40, true);
        balance.setTextColor(Color.WHITE);
        TextView currency = text(amount, R.string.currency_cup, 16, true);
        currency.setTextColor(0xFFE6E0FF);
        currency.setPadding(dp(12), 0, 0, 0);
        hero.addView(amount);
        TextView status = text(hero, checking ? getString(R.string.query_running)
                : values == null ? getString(R.string.balance_pending) : getString(R.string.manual_at, values[3]), 12, false);
        status.setTextColor(0xFFE6E0FF);
        Button refresh = button(hero, R.string.update_balance, v -> confirmQuery(CubacelQuery.BALANCE), false);
        refresh.setEnabled(!checking);

        LinearLayout actions = row();
        Button recharge = button(actions, R.string.recharge, v -> recharge(), true);
        recharge.setLayoutParams(weight());
        gap(actions);
        Button plans = button(actions, R.string.buy_plans, v -> openDialer("*133#"), false);
        plans.setLayoutParams(weight());
        content.addView(actions);
        space(content, 12);

        LinearLayout metrics = row();
        metric(metrics, R.string.data_title, R.string.data_unit, R.drawable.ic_data,
                values == null ? null : values[1], CubacelQuery.DATA, 0xFF5363CE);
        gap(metrics);
        metric(metrics, R.string.voice_title, R.string.voice_unit, R.drawable.ic_voice,
                values == null ? null : values[2], CubacelQuery.VOICE, 0xFFC2733D);
        content.addView(metrics);

        LinearLayout tools = card(content);
        text(tools, R.string.more_queries, 18, true);
        text(tools, R.string.more_queries_hint, 12, false);
        LinearLayout queries = row();
        Button bonus = button(queries, R.string.bonuses, v -> confirmQuery(CubacelQuery.BONUSES_AND_USD_PLANS), false);
        bonus.setLayoutParams(weight());
        gap(queries);
        Button sms = button(queries, R.string.sms, v -> confirmQuery(CubacelQuery.SMS), false);
        sms.setLayoutParams(weight());
        tools.addView(queries);
        button(tools, R.string.recharge_status, v -> confirmQuery(CubacelQuery.RECHARGE_STATUS), false);
        button(tools, R.string.register_reading, v -> editReading(), false);
        text(tools, R.string.manual_hint, 11, false);
        space(content, 18);
        text(content, R.string.about_title, 13, true);
        text(content, R.string.about_detail, 12, false);
        scroll.post(() -> scroll.scrollTo(0, previousScroll));
    }

    private void renderAccounts() {
        LinearLayout title = row();
        text(title, R.string.sims, 18, true);
        title.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        button(title, R.string.manage_sims, v -> openSettings(), false).setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        content.addView(title);
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            LinearLayout panel = card(content);
            text(panel, R.string.permission_intro, 13, false);
            button(panel, R.string.connect, v -> requestPermissions(new String[]{Manifest.permission.READ_PHONE_STATE}, PHONE_PERMISSION), true);
            button(panel, R.string.settings, v -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName()))), false);
            return;
        }
        if (subscriptions.isEmpty()) text(content, R.string.no_sims, 13, false);
        HorizontalScrollView horizontal = new HorizontalScrollView(this);
        horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout accounts = row();
        for (SubscriptionInfo sim : subscriptions) {
            LinearLayout panel = column();
            panel.setPadding(dp(16), dp(12), dp(16), dp(12));
            boolean active = selected == sim.getSubscriptionId();
            GradientDrawable bg = shape(active ? 0xFFEAE5FF : Color.WHITE, 18);
            bg.setStroke(dp(active ? 2 : 1), active ? PURPLE : 0xFFE5E5F0);
            panel.setBackground(bg);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(188), -2);
            params.setMarginEnd(dp(10));
            panel.setLayoutParams(params);
            LinearLayout line = row();
            icon(line, R.drawable.ic_sim, PURPLE);
            TextView slot = text(line, getString(R.string.sim_number, sim.getSimSlotIndex() + 1), 15, true);
            slot.setPadding(dp(8), 0, 0, 0);
            panel.addView(line);
            text(panel, sim.getCarrierName() == null ? getString(R.string.unknown_carrier) : sim.getCarrierName().toString(), 14, true);
            text(panel, active ? R.string.selected_sim : R.string.select_sim, 12, false);
            panel.setContentDescription(getString(R.string.sim_slot, sim.getSimSlotIndex() + 1, sim.getCarrierName()));
            panel.setSelected(active);
            panel.setFocusable(true);
            panel.setOnClickListener(v -> { selected = sim.getSubscriptionId(); render(); });
            accounts.addView(panel);
        }
        LinearLayout add = column();
        add.setPadding(dp(16), dp(12), dp(16), dp(12));
        add.setBackground(shape(0xFFEBEEFF, 18));
        button(add, R.string.add_sim, v -> openSettings(), false);
        text(add, R.string.add_sim_hint, 11, false);
        accounts.addView(add, new LinearLayout.LayoutParams(dp(180), -2));
        horizontal.addView(accounts);
        content.addView(horizontal);
    }

    private void metric(LinearLayout parent, int title, int unit, int drawable, String value,
                        CubacelQuery query, int tint) {
        LinearLayout panel = card(null);
        panel.setLayoutParams(weight());
        icon(panel, drawable, tint);
        text(panel, title, 14, true);
        text(panel, value == null ? getString(R.string.empty_amount) : value, 27, true);
        text(panel, unit, 12, false);
        Button check = button(panel, R.string.check, v -> confirmQuery(query), false);
        check.setEnabled(!checking);
        parent.addView(panel);
    }

    private String selectedLabel() {
        for (SubscriptionInfo sim : subscriptions) if (sim.getSubscriptionId() == selected)
            return getString(R.string.sim_slot, sim.getSimSlotIndex() + 1, sim.getCarrierName());
        return getString(R.string.no_selection);
    }

    private boolean requireSim() {
        if (selected < 0) { message(R.string.connect, R.string.no_selection); return false; }
        return true;
    }

    private void confirmQuery(CubacelQuery query) {
        if (!requireSim()) return;
        if (checking) { message(R.string.query_title, R.string.query_running); return; }
        if (checkSelfPermission(Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, CALL_PERMISSION);
            return;
        }
        final int subscription = selected;
        new AlertDialog.Builder(this).setTitle(queryLabel(query))
                .setMessage(getString(R.string.confirm_query, query.code(), selectedLabel()))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.check, (dialog, which) -> dispatch(subscription, query)).show();
    }

    private int queryLabel(CubacelQuery query) {
        return switch (query) {
            case BALANCE -> R.string.balance;
            case DATA -> R.string.data_title;
            case VOICE -> R.string.voice_title;
            case SMS -> R.string.sms;
            case BONUSES_AND_USD_PLANS -> R.string.bonuses;
            case RECHARGE_STATUS -> R.string.recharge_status;
        };
    }

    private void dispatch(int subscription, CubacelQuery query) {
        checking = true;
        render();
        currentRequest = ((SimpleApplication) getApplication()).dispatcher().queryText(subscription, query);
        currentRequest.whenComplete((response, error) -> runOnUiThread(() -> {
            if (disposed || isDestroyed() || isFinishing()) return;
            checking = false;
            currentRequest = null;
            addHistory(getString(error == null ? R.string.history_success : R.string.history_failure,
                    getString(queryLabel(query)), time()));
            render();
            if (error != null) {
                Throwable cause = error;
                while (cause.getCause() != null) cause = cause.getCause();
                int message = R.string.query_error;
                if (cause instanceof UssdDispatcher.QueryException failure) message = switch (failure.failure) {
                    case WRONG_CARRIER -> R.string.wrong_carrier;
                    case TIMEOUT, BUSY -> R.string.session_busy;
                    case PERMISSION -> R.string.permission_denied;
                    case INACTIVE_SIM -> R.string.no_selection;
                    default -> R.string.query_error;
                };
                message(R.string.query_title, message);
            } else {
                responseDialog = new AlertDialog.Builder(this).setTitle(queryLabel(query))
                        .setMessage(response).setPositiveButton(R.string.close, null)
                        .setNeutralButton(R.string.register_reading, (d, w) -> {
                            if (selected == subscription) editReading();
                        }).create();
                responseDialog.setOnDismissListener(d -> responseDialog = null);
                responseDialog.setOnShowListener(d -> responseDialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE));
                responseDialog.show();
            }
        }));
    }

    private void recharge() {
        new AlertDialog.Builder(this).setTitle(R.string.recharge)
                .setItems(R.array.recharge_options, (d, which) -> {
                    if (which == 0) openDialer("*666");
                    else if (which == 1) openExternal(new Intent(Intent.ACTION_VIEW,
                            Uri.parse("https://www.etecsa.cu/es/movil/necesitas-saldo")));
                    else confirmQuery(CubacelQuery.RECHARGE_STATUS);
                }).setNegativeButton(R.string.cancel, null).show();
    }

    private void openDialer(String code) {
        new AlertDialog.Builder(this).setTitle(R.string.open_dialer)
                .setMessage(getString(R.string.dialer_notice, code))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.continue_action, (d, w) -> openExternal(
                        new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", code, null)))).show();
    }

    private void editReading() {
        if (!requireSim()) return;
        int subscription = selected;
        LinearLayout form = column();
        form.setPadding(dp(24), dp(8), dp(24), 0);
        text(form, R.string.manual_hint, 12, false);
        EditText[] fields = new EditText[3];
        int[] labels = {R.string.balance_input, R.string.data_input, R.string.voice_input};
        String[] previous = readings.get(subscription);
        for (int i = 0; i < 3; i++) {
            text(form, labels[i], 13, true);
            fields[i] = new EditText(this);
            fields[i].setSingleLine(true);
            fields[i].setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            fields[i].setHint(R.string.optional_value);
            fields[i].setContentDescription(getString(labels[i]));
            if (previous != null && !previous[i].equals(getString(R.string.empty_amount))) fields[i].setText(previous[i]);
            form.addView(fields[i]);
        }
        ScrollView scrollForm = new ScrollView(this);
        scrollForm.addView(form);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(R.string.register_reading).setView(scrollForm)
                .setNegativeButton(R.string.cancel, null).setPositiveButton(R.string.save, null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String[] values = new String[4];
            boolean hasValue = false;
            for (int i = 0; i < 3; i++) {
                String input = fields[i].getText().toString().trim().replace(',', '.');
                if (input.isEmpty()) { values[i] = getString(R.string.empty_amount); continue; }
                if (!input.matches("[0-9]{1,9}(\\.[0-9]{1,3})?")) {
                    fields[i].setError(getString(R.string.invalid_value)); return;
                }
                values[i] = new BigDecimal(input).stripTrailingZeros().toPlainString();
                hasValue = true;
            }
            if (!hasValue) { fields[0].setError(getString(R.string.enter_value)); return; }
            values[3] = time();
            readings.put(subscription, values);
            addHistory(getString(R.string.manual_event, time()));
            render(); dialog.dismiss();
        }));
        dialog.show();
    }

    private void addHistory(String event) { history.add(0, event); if (history.size() > 20) history.remove(20); }
    private String time() { return DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date()); }
    private void showHistory() {
        new AlertDialog.Builder(this).setTitle(R.string.history)
                .setMessage(history.isEmpty() ? getString(R.string.empty_history) : String.join("\n\n", history))
                .setPositiveButton(R.string.close, null)
                .setNeutralButton(R.string.clear_history, (d, w) -> { history.clear(); readings.clear(); render(); }).show();
    }
    private void openSettings() { openExternal(new Intent(Settings.ACTION_WIRELESS_SETTINGS)); }
    private void openExternal(Intent intent) {
        try { startActivity(intent); } catch (ActivityNotFoundException ignored) { message(R.string.app_name, R.string.no_handler); }
    }
    private void message(int title, int message) { new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton(R.string.close, null).show(); }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        reloadSims();
        if (code == CALL_PERMISSION) {
            boolean allowed = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            message(R.string.query_title, allowed ? R.string.permission_ready : R.string.call_permission_denied);
        }
    }

    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); layout.setGravity(Gravity.CENTER_VERTICAL); return layout; }
    private LinearLayout.LayoutParams weight() { LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); params.topMargin = dp(12); return params; }
    private GradientDrawable shape(int color, int radius) { GradientDrawable bg = new GradientDrawable(); bg.setColor(color); bg.setCornerRadius(dp(radius)); return bg; }
    private LinearLayout card(LinearLayout parent) {
        LinearLayout panel = column(); panel.setPadding(dp(18), dp(18), dp(18), dp(18));
        GradientDrawable bg = shape(Color.WHITE, 22); bg.setStroke(dp(1), 0xFFE9E9F2); panel.setBackground(bg);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(14); panel.setLayoutParams(params);
        if (parent != null) parent.addView(panel); return panel;
    }
    private TextView text(LinearLayout parent, int res, int size, boolean bold) { return text(parent, getString(res), size, bold); }
    private TextView text(LinearLayout parent, String value, int size, boolean bold) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(bold ? INK : MUTED);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL); view.setPadding(0, dp(4), 0, dp(4));
        view.setLineSpacing(dp(2), 1); parent.addView(view); return view;
    }
    private Button button(LinearLayout parent, int label, View.OnClickListener listener, boolean primary) {
        Button button = new Button(this); button.setText(label); button.setAllCaps(false); button.setTextSize(13);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD); button.setTextColor(primary ? Color.WHITE : PURPLE);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x226550C8), shape(primary ? PURPLE : 0xFFF0ECFF, 13), null));
        button.setPadding(dp(12), dp(10), dp(12), dp(10)); button.setMinHeight(dp(48)); button.setMinimumWidth(0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2); params.topMargin = dp(10); button.setLayoutParams(params);
        button.setOnClickListener(listener); parent.addView(button); return button;
    }
    private void icon(LinearLayout parent, int resource, int tint) { ImageView icon = new ImageView(this); icon.setImageResource(resource); icon.setImageTintList(ColorStateList.valueOf(tint)); icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); parent.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24))); }
    private void gap(LinearLayout parent) { parent.addView(new View(this), new LinearLayout.LayoutParams(dp(12), 1)); }
    private void space(LinearLayout parent, int size) { parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(size))); }
    private int dp(int size) { return Math.round(size * getResources().getDisplayMetrics().density); }
}
