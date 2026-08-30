package com.shoren.oneui.clockmod.ui;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import com.shoren.oneui.clockmod.R;
import com.shoren.oneui.clockmod.utils.ClockFormatter;
import com.shoren.oneui.clockmod.utils.PrefKeys;
import com.shoren.oneui.clockmod.utils.WeatherHelper;

import java.io.DataOutputStream;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private TextView mTvPreviewLeft;
    private TextView mTvPreviewCenter;
    private TextView mTvPreviewRight;
    private View mViewCameraCutout;

    private Handler mHandler;
    private Runnable mTicker;
    private SharedPreferences mPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTvPreviewLeft = findViewById(R.id.tv_preview_clock_left);
        mTvPreviewCenter = findViewById(R.id.tv_preview_clock_center);
        mTvPreviewRight = findViewById(R.id.tv_preview_clock_right);
        mViewCameraCutout = findViewById(R.id.view_camera_cutout);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings_container, new SettingsFragment())
                .commit();
        }

        mHandler = new Handler(Looper.getMainLooper());
        mTicker = new Runnable() {
            @Override
            public void run() {
                updateLivePreview();
                long now = System.currentTimeMillis();
                long delay = 1000 - (now % 1000);
                mHandler.postDelayed(this, delay);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        mHandler.post(mTicker);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mTicker);
    }

    public void updateLivePreview() {
        if (mPrefs == null || mTvPreviewLeft == null) return;

        ClockFormatter.FormatConfig config = new ClockFormatter.FormatConfig();
        config.moduleEnabled = mPrefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEFAULT_MODULE_ENABLED);
        config.formatMode = mPrefs.getString(PrefKeys.KEY_FORMAT_MODE, PrefKeys.DEFAULT_FORMAT_MODE);
        config.customPattern = mPrefs.getString(PrefKeys.KEY_CUSTOM_PATTERN, PrefKeys.DEFAULT_CUSTOM_PATTERN);
        config.showSeconds = mPrefs.getBoolean(PrefKeys.KEY_SHOW_SECONDS, PrefKeys.DEFAULT_SHOW_SECONDS);
        config.datePreset = mPrefs.getString(PrefKeys.KEY_DATE_PRESET, PrefKeys.DEFAULT_DATE_PRESET);
        config.weekPreset = mPrefs.getString(PrefKeys.KEY_WEEK_PRESET, PrefKeys.DEFAULT_WEEK_PRESET);
        config.enableLunar = mPrefs.getBoolean(PrefKeys.KEY_ENABLE_LUNAR, PrefKeys.DEFAULT_ENABLE_LUNAR);
        config.lunarShowYear = mPrefs.getBoolean(PrefKeys.KEY_LUNAR_SHOW_YEAR, PrefKeys.DEFAULT_LUNAR_SHOW_YEAR);
        config.lunarShowSolarTerm = mPrefs.getBoolean(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM, PrefKeys.DEFAULT_LUNAR_SHOW_SOLAR_TERM);
        config.enableWeather = mPrefs.getBoolean(PrefKeys.KEY_ENABLE_WEATHER, PrefKeys.DEFAULT_ENABLE_WEATHER);
        config.weatherFormat = mPrefs.getString(PrefKeys.KEY_WEATHER_FORMAT, PrefKeys.DEFAULT_WEATHER_FORMAT);
        config.fontScale = mPrefs.getInt(PrefKeys.KEY_FONT_SCALE, PrefKeys.DEFAULT_FONT_SCALE);
        config.enableCustomColor = mPrefs.getBoolean(PrefKeys.KEY_ENABLE_CUSTOM_COLOR, PrefKeys.DEFAULT_ENABLE_CUSTOM_COLOR);
        config.customColorHex = mPrefs.getString(PrefKeys.KEY_CUSTOM_COLOR_HEX, PrefKeys.DEFAULT_CUSTOM_COLOR_HEX);
        config.multiLine = mPrefs.getBoolean(PrefKeys.KEY_MULTI_LINE, PrefKeys.DEFAULT_MULTI_LINE);
        config.weatherInfo = WeatherHelper.loadFromPreferences(mPrefs);

        CharSequence previewText = ClockFormatter.format(this, Calendar.getInstance(), config);

        int pos = PrefKeys.DEFAULT_CLOCK_POSITION;
        try {
            pos = Integer.parseInt(mPrefs.getString(PrefKeys.KEY_CLOCK_POSITION, String.valueOf(PrefKeys.DEFAULT_CLOCK_POSITION)));
        } catch (Exception e) {
            pos = mPrefs.getInt(PrefKeys.KEY_CLOCK_POSITION, PrefKeys.DEFAULT_CLOCK_POSITION);
        }

        mTvPreviewLeft.setVisibility(View.GONE);
        mTvPreviewCenter.setVisibility(View.GONE);
        mTvPreviewRight.setVisibility(View.GONE);

        if (!config.moduleEnabled) {
            mTvPreviewLeft.setVisibility(View.VISIBLE);
            mTvPreviewLeft.setText(ClockFormatter.getDefaultTime(this, Calendar.getInstance(), false));
            resetTextViewAppearance(mTvPreviewLeft);
            return;
        }

        TextView activeTv = null;
        switch (pos) {
            case PrefKeys.POSITION_CENTER:
                mTvPreviewCenter.setVisibility(View.VISIBLE);
                activeTv = mTvPreviewCenter;
                break;
            case PrefKeys.POSITION_RIGHT:
                mTvPreviewRight.setVisibility(View.VISIBLE);
                activeTv = mTvPreviewRight;
                break;
            case PrefKeys.POSITION_HIDE:
                // Hide all
                break;
            case PrefKeys.POSITION_LEFT:
            default:
                mTvPreviewLeft.setVisibility(View.VISIBLE);
                activeTv = mTvPreviewLeft;
                break;
        }

        if (activeTv != null) {
            activeTv.setText(previewText);
            applyAppearanceToPreview(activeTv, config);
        }
    }

    private void applyAppearanceToPreview(TextView tv, ClockFormatter.FormatConfig config) {
        if (config.enableCustomColor && config.customColorHex != null) {
            try {
                tv.setTextColor(Color.parseColor(config.customColorHex));
            } catch (Exception e) {
                tv.setTextColor(Color.WHITE);
            }
        } else {
            tv.setTextColor(Color.WHITE);
        }

        float baseSize = 14f;
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSize * (config.fontScale / 100.0f));

        if (config.multiLine) {
            tv.setSingleLine(false);
            tv.setMaxLines(2);
            tv.setLineSpacing(0, 0.9f);
        } else {
            tv.setSingleLine(true);
            tv.setMaxLines(1);
        }
    }

    private void resetTextViewAppearance(TextView tv) {
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        tv.setSingleLine(true);
        tv.setMaxLines(1);
    }

    public void showRestartDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_restart_title)
            .setMessage(R.string.dialog_restart_message)
            .setPositiveButton(R.string.dialog_positive, (dialog, which) -> restartSystemUIWithRoot())
            .setNegativeButton(R.string.dialog_negative, null)
            .show();
    }

    public void restartSystemUIWithRoot() {
        Toast.makeText(this, R.string.toast_restart_sent, Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                Process su = Runtime.getRuntime().exec("su");
                DataOutputStream os = new DataOutputStream(su.getOutputStream());
                os.writeBytes("pkill -f com.android.systemui\n");
                os.writeBytes("exit\n");
                os.flush();
                su.waitFor();
            } catch (Exception e) {
                // Ignore or fallback
            }
        }).start();
    }
}
