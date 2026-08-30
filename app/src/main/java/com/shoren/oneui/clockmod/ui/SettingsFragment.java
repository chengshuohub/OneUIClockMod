package com.shoren.oneui.clockmod.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.shoren.oneui.clockmod.R;
import com.shoren.oneui.clockmod.ui.LogViewerActivity;
import com.shoren.oneui.clockmod.utils.PrefKeys;

/**
 * 设置界面：已全面汉化，提供一站式状态栏时钟与农历、天气、布局配置，并集成日志查看入口。
 */
public class SettingsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey);

        // 绑定日志查看界面跳转
        Preference logPref = findPreference("key_open_log_viewer");
        if (logPref != null) {
            logPref.setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(requireContext(), LogViewerActivity.class);
                startActivity(intent);
                return true;
            });
        }

        // 绑定手动触发广播
        Preference applyPref = findPreference("key_apply_changes");
        if (applyPref != null) {
            applyPref.setOnPreferenceClickListener(preference -> {
                sendBroadcastToSystemUI();
                Toast.makeText(requireContext(), "已成功发送广播，刷新系统时钟！", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        // 绑定重启 SystemUI
        Preference restartSysUiPref = findPreference("key_restart_sysui");
        if (restartSysUiPref != null) {
            restartSysUiPref.setOnPreferenceClickListener(preference -> {
                boolean success = restartSystemUI();
                if (success) {
                    Toast.makeText(requireContext(), "正在重启 SystemUI...", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "重启失败，请检查是否已授权 Root 权限", Toast.LENGTH_LONG).show();
                }
                return true;
            });
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getPreferenceManager().getSharedPreferences() != null) {
            getPreferenceManager().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getPreferenceManager().getSharedPreferences() != null) {
            getPreferenceManager().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // 任何配置改变时，发送全局广播同步给 SystemUI
        sendBroadcastToSystemUI();
    }

    private void sendBroadcastToSystemUI() {
        if (getContext() == null) return;
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs == null) return;

        Intent intent = new Intent(PrefKeys.ACTION_PREF_CHANGED);

        // 1. 基础开关与样式
        intent.putExtra(PrefKeys.KEY_MODULE_ENABLED, prefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEFAULT_MODULE_ENABLED));
        intent.putExtra(PrefKeys.KEY_FORMAT_MODE, prefs.getString(PrefKeys.KEY_FORMAT_MODE, PrefKeys.DEFAULT_FORMAT_MODE));
        intent.putExtra(PrefKeys.KEY_CUSTOM_PATTERN, prefs.getString(PrefKeys.KEY_CUSTOM_PATTERN, PrefKeys.DEFAULT_CUSTOM_PATTERN));
        intent.putExtra(PrefKeys.KEY_SHOW_SECONDS, prefs.getBoolean(PrefKeys.KEY_SHOW_SECONDS, PrefKeys.DEFAULT_SHOW_SECONDS));
        intent.putExtra(PrefKeys.KEY_DATE_PRESET, prefs.getString(PrefKeys.KEY_DATE_PRESET, PrefKeys.DEFAULT_DATE_PRESET));
        intent.putExtra(PrefKeys.KEY_WEEK_PRESET, prefs.getString(PrefKeys.KEY_WEEK_PRESET, PrefKeys.DEFAULT_WEEK_PRESET));
        
        // 2. 农历与天气
        intent.putExtra(PrefKeys.KEY_ENABLE_LUNAR, prefs.getBoolean(PrefKeys.KEY_ENABLE_LUNAR, PrefKeys.DEFAULT_ENABLE_LUNAR));
        intent.putExtra(PrefKeys.KEY_LUNAR_SHOW_YEAR, prefs.getBoolean(PrefKeys.KEY_LUNAR_SHOW_YEAR, PrefKeys.DEFAULT_LUNAR_SHOW_YEAR));
        intent.putExtra(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM, prefs.getBoolean(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM, PrefKeys.DEFAULT_LUNAR_SHOW_SOLAR_TERM));
        intent.putExtra(PrefKeys.KEY_ENABLE_WEATHER, prefs.getBoolean(PrefKeys.KEY_ENABLE_WEATHER, PrefKeys.DEFAULT_ENABLE_WEATHER));
        intent.putExtra(PrefKeys.KEY_WEATHER_FORMAT, prefs.getString(PrefKeys.KEY_WEATHER_FORMAT, PrefKeys.DEFAULT_WEATHER_FORMAT));
        
        // 3. 外观与字体
        intent.putExtra(PrefKeys.KEY_FONT_SCALE, prefs.getInt(PrefKeys.KEY_FONT_SCALE, PrefKeys.DEFAULT_FONT_SCALE));
        intent.putExtra(PrefKeys.KEY_ENABLE_CUSTOM_COLOR, prefs.getBoolean(PrefKeys.KEY_ENABLE_CUSTOM_COLOR, PrefKeys.DEFAULT_ENABLE_CUSTOM_COLOR));
        intent.putExtra(PrefKeys.KEY_CUSTOM_COLOR_HEX, prefs.getString(PrefKeys.KEY_CUSTOM_COLOR_HEX, PrefKeys.DEFAULT_CUSTOM_COLOR_HEX));
        intent.putExtra(PrefKeys.KEY_MULTI_LINE, prefs.getBoolean(PrefKeys.KEY_MULTI_LINE, PrefKeys.DEFAULT_MULTI_LINE));

        // 4. 位置对齐与边距偏移 (补齐此部分，配合 StatusBarLayoutHook 生效)
        intent.putExtra("key_clock_position", prefs.getString("key_clock_position", "left"));
        intent.putExtra("key_horizontal_offset", prefs.getInt("key_horizontal_offset", 0));
        intent.putExtra("key_vertical_offset", prefs.getInt("key_vertical_offset", 0));

        requireContext().sendBroadcast(intent);
    }

    private boolean restartSystemUI() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "pkill -f com.android.systemui"});
            process.waitFor();
            return process.exitValue() == 0;
        } catch (Throwable t) {
            return false;
        }
    }
}