package com.shoren.oneui.clockmod.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import com.shoren.oneui.clockmod.R;
import com.shoren.oneui.clockmod.utils.PrefKeys;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        Preference logPref = findPreference("key_open_log_viewer");
        if (logPref != null) logPref.setOnPreferenceClickListener(p -> {
            startActivity(new Intent(requireContext(), LogViewerActivity.class));
            return true;
        });

        Preference applyPref = findPreference("key_apply_changes");
        if (applyPref != null) applyPref.setOnPreferenceClickListener(p -> {
            sendBroadcastToSystemUI();
            Toast.makeText(requireContext(), "已强制广播最新配置到系统", Toast.LENGTH_SHORT).show();
            return true;
        });

        Preference restartSysUiPref = findPreference("key_restart_sysui");
        if (restartSysUiPref != null) restartSysUiPref.setOnPreferenceClickListener(p -> {
            try {
                Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill -f com.android.systemui"});
                Toast.makeText(requireContext(), "正在重启 SystemUI...", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(requireContext(), "重启失败，请检查 Root 权限", Toast.LENGTH_LONG).show();
            }
            return true;
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getPreferenceManager().getSharedPreferences() != null) getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getPreferenceManager().getSharedPreferences() != null) getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        sendBroadcastToSystemUI();
    }

    private void sendBroadcastToSystemUI() {
        if (getContext() == null || getPreferenceManager().getSharedPreferences() == null) return;
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        Intent intent = new Intent(PrefKeys.ACTION_PREF_CHANGED);

        intent.putExtra(PrefKeys.KEY_MODULE_ENABLED, prefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, true));
        intent.putExtra(PrefKeys.KEY_FORMAT_MODE, prefs.getString(PrefKeys.KEY_FORMAT_MODE, "preset"));
        intent.putExtra(PrefKeys.KEY_CUSTOM_PATTERN, prefs.getString(PrefKeys.KEY_CUSTOM_PATTERN, "{TIME} {WEEK} {LUNAR}"));
        intent.putExtra(PrefKeys.KEY_SHOW_SECONDS, prefs.getBoolean(PrefKeys.KEY_SHOW_SECONDS, false));
        intent.putExtra(PrefKeys.KEY_DATE_PRESET, prefs.getString(PrefKeys.KEY_DATE_PRESET, "M月d日"));
        intent.putExtra(PrefKeys.KEY_WEEK_PRESET, prefs.getString(PrefKeys.KEY_WEEK_PRESET, "zh_short"));
        intent.putExtra(PrefKeys.KEY_ENABLE_LUNAR, prefs.getBoolean(PrefKeys.KEY_ENABLE_LUNAR, true));
        intent.putExtra(PrefKeys.KEY_LUNAR_SHOW_YEAR, prefs.getBoolean(PrefKeys.KEY_LUNAR_SHOW_YEAR, false));
        intent.putExtra(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM, prefs.getBoolean(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM, true));
        intent.putExtra(PrefKeys.KEY_ENABLE_WEATHER, prefs.getBoolean(PrefKeys.KEY_ENABLE_WEATHER, false));
        intent.putExtra(PrefKeys.KEY_WEATHER_FORMAT, prefs.getString(PrefKeys.KEY_WEATHER_FORMAT, "text_temp"));
        intent.putExtra(PrefKeys.KEY_FONT_SCALE, prefs.getInt(PrefKeys.KEY_FONT_SCALE, 100));
        intent.putExtra(PrefKeys.KEY_ENABLE_CUSTOM_COLOR, prefs.getBoolean(PrefKeys.KEY_ENABLE_CUSTOM_COLOR, false));
        intent.putExtra(PrefKeys.KEY_CUSTOM_COLOR_HEX, prefs.getString(PrefKeys.KEY_CUSTOM_COLOR_HEX, "#FFFFFF"));
        intent.putExtra(PrefKeys.KEY_MULTI_LINE, prefs.getBoolean(PrefKeys.KEY_MULTI_LINE, false));
        
        intent.putExtra("key_clock_position", prefs.getString("key_clock_position", "left"));
        intent.putExtra("key_horizontal_offset", prefs.getInt("key_horizontal_offset", 0));
        intent.putExtra("key_vertical_offset", prefs.getInt("key_vertical_offset", 0));

        requireContext().sendBroadcast(intent);
    }
}
