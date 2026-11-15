package com.gathin.tvhome;

import android.app.Activity;
import android.app.ActivityManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.Bundle;
import android.os.Debug;
import android.os.Environment;
import android.os.StatFs;
import android.os.SystemClock;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TimeZone;

/** Refreshes system readings when opened or explicitly requested. */
public final class SystemInfoActivity extends Activity {
    private TextView readings;

    @Override protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(32 * getResources().getDisplayMetrics().density);
        rows.setPadding(padding, padding, padding, padding);
        scroll.addView(rows);
        TextView title = new TextView(this);
        title.setText(R.string.system_info_title);
        title.setTextSize(26);
        title.setTextColor(-1);
        rows.addView(title);
        readings = new TextView(this);
        readings.setTextSize(18);
        readings.setTextColor(-1);
        readings.setPadding(0, padding / 2, 0, padding / 2);
        TextView refresh = new TextView(this, null, 0, R.style.SettingsRow);
        refresh.setText(R.string.refresh_readings);
        refresh.setOnClickListener(view -> refresh());
        rows.addView(refresh, new LinearLayout.LayoutParams(-1, padding * 2));
        rows.addView(readings);
        setContentView(scroll);
        refresh.requestFocus();
    }

    @Override protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        getSystemService(ActivityManager.class).getMemoryInfo(memory);
        StatFs disk = new StatFs(Environment.getDataDirectory().getPath());
        String version;
        try { version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (android.content.pm.PackageManager.NameNotFoundException impossible) { version = "?"; }
        Set<String> transports = new LinkedHashSet<>();
        boolean vpn = false;
        ConnectivityManager manager = getSystemService(ConnectivityManager.class);
        for (Network network : manager.getAllNetworks()) {
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            if (caps == null) continue;
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) transports.add("Wi-Fi");
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) transports.add("Ethernet");
            vpn |= caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN);
        }
        readings.setText(getString(R.string.system_info_values, Build.MODEL, Build.VERSION.RELEASE,
                Build.DISPLAY, version, bytes(memory.availMem), bytes(memory.totalMem),
                bytes(Debug.getPss() * 1024L), bytes(disk.getAvailableBytes()), bytes(disk.getTotalBytes()),
                transports.isEmpty() ? getString(R.string.network_offline) : android.text.TextUtils.join(" / ", transports),
                getString(vpn ? R.string.state_on : R.string.state_off),
                getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels,
                TimeZone.getDefault().getID(), DateUtils.formatElapsedTime(SystemClock.elapsedRealtime() / 1000)));
        FontChoice.apply(getWindow().getDecorView(), FontChoice.face(this, FontChoice.selected(this)));
    }

    private String bytes(long value) { return Formatter.formatFileSize(this, value); }
}
