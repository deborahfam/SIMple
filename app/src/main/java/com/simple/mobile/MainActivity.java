package com.simple.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.simple.mobile.data.SimRepository;
import java.util.List;

public final class MainActivity extends Activity {
    private static final int PHONE_PERMISSION = 100;
    private static final int INK = 0xFF162F2A;
    private static final int MUTED = 0xFF52665F;
    private LinearLayout content;
    private LinearLayout sims;
    private SimRepository repository;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new SimRepository(this);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(24), dp(24), dp(24));
        scroll.addView(content);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll);
        text(content, R.string.app_name, 30, true);
        text(content, R.string.tagline, 14, false);
        space(content, 32);
        text(content, R.string.overview, 26, true);
        text(content, R.string.intro, 16, false);
        space(content, 24);
        text(content, R.string.sims, 18, true);
        sims = card();
        content.addView(sims);
        addSummary(R.string.balance, R.string.balance_detail);
        addSummary(R.string.allowances, R.string.allowances_detail);
        space(content, 24);
        text(content, R.string.about_title, 18, true);
        text(content, R.string.about_detail, 14, false);
    }

    @Override protected void onResume() {
        super.onResume();
        renderSims(false);
    }

    private void renderSims(boolean denied) {
        sims.removeAllViews();
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            text(sims, denied ? R.string.permission_denied : R.string.permission_intro, 16, false);
            button(sims, R.string.connect, view -> requestPermissions(
                    new String[]{Manifest.permission.READ_PHONE_STATE}, PHONE_PERMISSION));
            if (denied || getPreferences(MODE_PRIVATE).getBoolean("permission_requested", false)) {
                button(sims, R.string.settings, view -> startActivity(new Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))));
            }
            return;
        }
        try {
            List<SubscriptionInfo> subscriptions = repository.activeSubscriptions();
            if (subscriptions.isEmpty()) text(sims, R.string.no_sims, 16, false);
            for (SubscriptionInfo subscription : subscriptions) {
                CharSequence carrier = subscription.getCarrierName();
                TextView label = text(sims, R.string.sims, 18, true);
                label.setText(getString(R.string.sim_slot, subscription.getSimSlotIndex() + 1,
                        carrier == null || carrier.length() == 0 ? getString(R.string.unknown_carrier) : carrier));
            }
        } catch (SecurityException | UnsupportedOperationException exception) {
            text(sims, R.string.unsupported, 16, false);
        }
        button(sims, R.string.refresh, view -> renderSims(false));
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == PHONE_PERMISSION) {
            getPreferences(MODE_PRIVATE).edit().putBoolean("permission_requested", true).apply();
            renderSims(results.length == 0 || results[0] != PackageManager.PERMISSION_GRANTED);
        }
    }

    private void addSummary(int title, int description) {
        LinearLayout panel = card();
        text(panel, title, 16, true);
        TextView value = text(panel, R.string.unavailable, 24, true);
        value.setTextColor(0xFF087F67);
        text(panel, description, 14, false);
        content.addView(panel);
    }

    private LinearLayout card() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(20));
        panel.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(14);
        panel.setLayoutParams(params);
        return panel;
    }

    private TextView text(LinearLayout parent, int resource, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(resource);
        view.setTextSize(size);
        view.setTextColor(bold ? INK : MUTED);
        view.setTypeface(Typeface.DEFAULT, bold ? Typeface.BOLD : Typeface.NORMAL);
        view.setPadding(0, dp(4), 0, dp(4));
        parent.addView(view);
        return view;
    }

    private void button(LinearLayout parent, int label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        parent.addView(button);
    }

    private void space(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
