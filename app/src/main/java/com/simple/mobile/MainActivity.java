package com.simple.mobile;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.widget.ImageView;
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
    private static final int INK = 0xFF202039;
    private static final int MUTED = 0xFF71718A;
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
        LinearLayout header = row();
        LinearLayout brand = new LinearLayout(this);
        brand.setOrientation(LinearLayout.VERTICAL);
        text(brand, R.string.app_name, 28, true);
        text(brand, R.string.tagline, 12, false);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = text(header, R.string.carrier_badge, 12, true);
        badge.setTextColor(0xFF6550C8);
        badge.setPadding(dp(12), dp(8), dp(12), dp(8));
        badge.setBackground(shape(0xFFEAE5FF, 16));
        content.addView(header);
        space(content, 28);
        text(content, R.string.overview, 30, true);
        text(content, R.string.intro, 14, false);
        space(content, 18);

        LinearLayout hero = card();
        GradientDrawable gradient = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF6850D7, 0xFF40349C});
        gradient.setCornerRadius(dp(26));
        hero.setBackground(gradient);
        LinearLayout heroTop = row();
        TextView title = text(heroTop, R.string.balance, 15, true);
        title.setTextColor(0xFFE6E0FF);
        heroTop.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        icon(heroTop, R.drawable.ic_signal, 0xFFE6E0FF, 28);
        hero.addView(heroTop);
        space(hero, 16);
        LinearLayout amount = row();
        TextView value = text(amount, R.string.empty_amount, 48, true);
        value.setTextColor(Color.WHITE);
        TextView unit = text(amount, R.string.currency_cup, 16, true);
        unit.setTextColor(0xFFE6E0FF);
        unit.setPadding(dp(12), dp(16), 0, 0);
        hero.addView(amount);
        space(hero, 14);
        View divider = new View(this);
        divider.setBackgroundColor(0x33FFFFFF);
        hero.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        space(hero, 10);
        TextView status = text(hero, R.string.balance_pending, 13, false);
        status.setTextColor(0xFFE6E0FF);
        content.addView(hero);
        space(content, 14);

        LinearLayout metrics = row();
        addMetric(metrics, R.string.data_title, R.string.data_unit, R.drawable.ic_data, 0xFF5363CE, 0xFFEBEEFF);
        spaceGap(metrics);
        addMetric(metrics, R.string.voice_title, R.string.voice_unit, R.drawable.ic_voice, 0xFFC2733D, 0xFFFFEFE2);
        content.addView(metrics);
        space(content, 26);
        text(content, R.string.sims, 20, true);
        text(content, R.string.sim_subtitle, 13, false);
        sims = card();
        content.addView(sims);
        space(content, 20);
        LinearLayout privacy = row();
        privacy.setGravity(Gravity.CENTER_VERTICAL);
        icon(privacy, R.drawable.ic_shield, 0xFF6550C8, 22);
        LinearLayout privacyText = new LinearLayout(this);
        privacyText.setOrientation(LinearLayout.VERTICAL);
        privacyText.setPadding(dp(12), 0, 0, 0);
        text(privacyText, R.string.about_title, 13, true);
        text(privacyText, R.string.about_detail, 12, false);
        privacy.addView(privacyText, new LinearLayout.LayoutParams(0, -2, 1));
        content.addView(privacy);
        space(content, 12);
    }

    @Override protected void onResume() {
        super.onResume();
        renderSims(false);
    }

    private void renderSims(boolean denied) {
        sims.removeAllViews();
        LinearLayout simHeader = row();
        simHeader.setGravity(Gravity.CENTER_VERTICAL);
        icon(simHeader, R.drawable.ic_sim, 0xFF6550C8, 32);
        TextView simTitle = text(simHeader, R.string.sim_card_title, 16, true);
        simTitle.setPadding(dp(12), 0, 0, 0);
        sims.addView(simHeader);
        space(sims, 10);
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            text(sims, denied ? R.string.permission_denied : R.string.permission_intro, 14, false);
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
            if (subscriptions.isEmpty()) text(sims, R.string.no_sims, 14, false);
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

    private void addMetric(LinearLayout parent, int title, int unit, int drawable, int tint, int fill) {
        LinearLayout panel = card();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        panel.setLayoutParams(params);
        LinearLayout iconRow = row();
        icon(iconRow, drawable, tint, 24);
        panel.addView(iconRow);
        space(panel, 10);
        text(panel, title, 14, true);
        text(panel, R.string.empty_amount, 30, true);
        TextView caption = text(panel, unit, 12, false);
        caption.setTextColor(tint);
        caption.setBackground(shape(fill, 8));
        caption.setPadding(dp(8), dp(4), dp(8), dp(4));
        space(panel, 6);
        text(panel, R.string.awaiting_query, 11, false);
        parent.addView(panel);
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private void spaceGap(LinearLayout parent) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(dp(14), 1));
    }

    private GradientDrawable shape(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }

    private void icon(LinearLayout parent, int resource, int tint, int size) {
        ImageView icon = new ImageView(this);
        icon.setImageResource(resource);
        icon.setImageTintList(ColorStateList.valueOf(tint));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(icon, new LinearLayout.LayoutParams(dp(size), dp(size)));
    }

    private LinearLayout card() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(20));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(22));
        background.setStroke(dp(1), 0xFFE9E9F2);
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
        view.setLineSpacing(dp(2), 1);
        parent.addView(view);
        return view;
    }

    private void button(LinearLayout parent, int label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        button.setTextColor(Color.WHITE);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF),
                shape(0xFF6550C8, 14), null));
        button.setPadding(dp(16), dp(12), dp(16), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = dp(16);
        button.setLayoutParams(params);
        button.setMinHeight(dp(50));
        parent.addView(button);
    }

    private void space(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
