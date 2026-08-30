package com.shoren.oneui.clockmod.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.shoren.oneui.clockmod.R;
import com.shoren.oneui.clockmod.utils.PrefKeys;
import com.shoren.oneui.clockmod.utils.WeatherHelper;

import java.io.File;

public class SettingsFragment extends PreferenceFragmentCompat implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        // Click listeners for actions
        Preference applyPref = findPreference("pref_apply_broadcast");
        if (applyPref != null) {
            applyPref.setOnPreferenceClickListener(pref -> {
                broadcastSettings(requireContext());
                Toast.makeText(requireContext(), R.string.toast_broadcast_sent, Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        Preference restartPref = findPreference("pref_restart_systemui");
        if (restartPref != null) {
            restartPref.setOnPreferenceClickListener(pref -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).showRestartDialog();
                }
                return true;
            });
        }

        Preference weatherTestPref = findPreference("pref_test_weather");
        if (weatherTestPref != null) {
            weatherTestPref.setOnPreferenceClickListener(pref -> {
                sendTestWeatherBroadcast(requireContext());
                Toast.makeText(requireContext(), "Test weather broadcasted (Sunny 28°C)", Toast.LENGTH_SHORT).show();
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        makePrefsWorldReadable(requireContext());
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        broadcastSettings(requireContext());
        makePrefsWorldReadable(requireContext());
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateLivePreview();
        }
    }

    private void broadcastSettings(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Intent intent = new Intent(PrefKeys.ACTION_PREF_CHANGED);
        intent.setPackage("com.android.systemui");

        intent.putExtra(PrefKeys.KEY_MODULE_ENABLED, prefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEFAULT_MODULE_ENABLED));
        intent.putExtra(PrefKeys.KEY_FORMAT_MODE, prefs.getString(PrefKeys.KEY_FORMAT_MODE, PrefKeys.DEFAULT_FORMAT_MODE));
        intent.putExtra(PrefKeys.KEY_CUSTOM_PATTERN, prefs.getString(PrefKeys.KEY_CUSTOM_PATTERN, PrefKeys.DEFAULT_CUSTOM_PATTERN));
        intent.putExtra(PrefKeys.KEY_SHOW_SECONDS, prefs.getBoolean(PrefKeys.KEY_SHOW_SECONDS, PrefKeys.DEFAULT_SHOW_SECONDS));
        intent.putExtra(PrefKeys.KEY_DATE_PRESET, prefs.getString(PrefKeys.KEY_DATE_PRESET, PrefKeys.DEFAULT_DATE_PRESET));
        intent.putExtra(PrefKeys.KEY_WEEK_PRESET, prefs.getString(PrefKeys.KEY_WEEK_PRESET, PrefKeys.DEFAULT_WEEK_PRESET));
        intent.putExtra(PrefKeys.KEY_ENABLE_LUNAR, prefs.getBoolean(PrefKeys.KEY_ENABLE_LUNAR, PrefKeys.DEFAULT_ENABLE_LUNAR));
        intent.putExtra(PrefKeys.KEY_LUNAR_SHOW_YEAR, prefs.getBoolean(PrefKeys.KEY_LUNAR_SHOW_YEAR, PrefKeys.DEFAULT_LUNAR_SHOW_YEAR));
        intent.putExtra(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM, prefs.getBoolean(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM, PrefKeys.DEFAULT_LUNAR_SHOW_SOLAR_TERM));
        intent.putExtra(PrefKeys.KEY_ENABLE_WEATHER, prefs.getBoolean(PrefKeys.KEY_ENABLE_WEATHER, PrefKeys.DEFAULT_ENABLE_WEATHER));
        intent.putExtra(PrefKeys.KEY_WEATHER_FORMAT, prefs.getString(PrefKeys.KEY_WEATHER_FORMAT, PrefKeys.DEFAULT_WEATHER_FORMAT));
        intent.putExtra(PrefKeys.KEY_FONT_SCALE, prefs.getInt(PrefKeys.KEY_FONT_SCALE, PrefKeys.DEFAULT_FONT_SCALE));
        intent.putExtra(PrefKeys.KEY_ENABLE_CUSTOM_COLOR, prefs.getBoolean(PrefKeys.KEY_ENABLE_CUSTOM_COLOR, PrefKeys.DEFAULT_ENABLE_CUSTOM_COLOR));
        intent.putExtra(PrefKeys.KEY_CUSTOM_COLOR_HEX, prefs.getString(PrefKeys.KEY_CUSTOM_COLOR_HEX, PrefKeys.DEFAULT_CUSTOM_COLOR_HEX));
        intent.putExtra(PrefKeys.KEY_MULTI_LINE, prefs.getBoolean(PrefKeys.KEY_MULTI_LINE, PrefKeys.DEFAULT_MULTI_LINE));

        int pos = PrefKeys.DEFAULT_CLOCK_POSITION;
        try {
            pos = Integer.parseInt(prefs.getString(PrefKeys.KEY_CLOCK_POSITION, String.valueOf(PrefKeys.DEFAULT_CLOCK_POSITION)));
        } catch (Exception e) {
            pos = prefs.getInt(PrefKeys.KEY_CLOCK_POSITION, PrefKeys.DEFAULT_CLOCK_POSITION);
        }
        intent.putExtra(PrefKeys.KEY_CLOCK_POSITION, pos);
        intent.putExtra(PrefKeys.KEY_CLOCK_OFFSET_X, prefs.getInt(PrefKeys.KEY_CLOCK_OFFSET_X, PrefKeys.DEFAULT_CLOCK_OFFSET_X));
        intent.putExtra(PrefKeys.KEY_CLOCK_OFFSET_Y, prefs.getInt(PrefKeys.KEY_CLOCK_OFFSET_Y, PrefKeys.DEFAULT_CLOCK_OFFSET_Y));

        context.sendBroadcast(intent);
    }

    private void sendTestWeatherBroadcast(Context context) {
        WeatherHelper.WeatherInfo info = new WeatherHelper.WeatherInfo("晴", "28°C", "☀️", System.currentTimeMillis());
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        WeatherHelper.saveToPreferences(prefs, info);

        Intent intent = new Intent(PrefKeys.ACTION_UPDATE_WEATHER);
        intent.setPackage("com.android.systemui");
        intent.putExtra("condition", "晴");
        intent.putExtra("temp", "28°C");
        intent.putExtra("icon", "☀️");
        context.sendBroadcast(intent);

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).updateLivePreview();
        }
    }

    @SuppressWarnings("deprecation")
    private void makePrefsWorldReadable(Context context) {
        try {
            File prefsDir = new File(context.getApplicationInfo().dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, PrefKeys.PREFS_NAME + ".xml");
            if (prefsFile.exists()) {
                prefsFile.setReadable(true, false);
                prefsFile.setExecutable(true, false);
            }
            if (prefsDir.exists()) {
                prefsDir.setReadable(true, false);
                prefsDir.setExecutable(true, false);
            }
        } catch (Exception ignored) {}
    }
}
