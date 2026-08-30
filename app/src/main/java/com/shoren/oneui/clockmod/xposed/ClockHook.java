package com.shoren.oneui.clockmod.xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import com.shoren.oneui.clockmod.utils.ClockFormatter;
import com.shoren.oneui.clockmod.utils.PrefKeys;
import com.shoren.oneui.clockmod.utils.WeatherHelper;

import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Hooks Samsung One UI Status Bar Clock views to intercept time updates,
 * apply custom date formats, lunar calendar calculations, and live weather.
 */
public class ClockHook {

    private static final String TAG = "OneUIClockMod_ClockHook";
    private static final Set<WeakReference<TextView>> sActiveClocks = new HashSet<>();
    private static ClockFormatter.FormatConfig sConfig = new ClockFormatter.FormatConfig();
    private static Handler sSecondsHandler;
    private static Runnable sSecondsTicker;
    private static boolean sIsTickerRunning = false;
    private static BroadcastReceiver sDynamicReceiver;

    public static void init(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        loadConfig(prefs);

        // Potential clock class names in Samsung One UI SystemUI
        String[] candidateClasses = new String[]{
            "com.android.systemui.statusbar.policy.Clock",
            "com.android.systemui.statusbar.views.DismissingStatusBarClockView",
            "com.android.systemui.statusbar.views.Clock"
        };

        boolean hookedAny = false;
        for (String className : candidateClasses) {
            try {
                Class<?> clockClass = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (clockClass != null && TextView.class.isAssignableFrom(clockClass)) {
                    hookClockClass(clockClass);
                    hookedAny = true;
                    XposedBridge.log(TAG + ": Successfully hooked clock class: " + className);
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Failed hooking " + className + ": " + t.getMessage());
            }
        }

        if (!hookedAny) {
            XposedBridge.log(TAG + ": Warning - no specific clock subclass found, attempting generic Clock lookup.");
        }
    }

    private static void hookClockClass(Class<?> clockClass) {
        // Hook onAttachedToWindow to track view & register broadcast receiver
        XposedHelpers.findAndHookMethod(clockClass, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                TextView clockView = (TextView) param.thisObject;
                registerClockView(clockView);
                ensureBroadcastReceiver(clockView.getContext());
                applyCustomFormatting(clockView);
                checkSecondsTicker();
            }
        });

        // Hook onDetachedFromWindow
        XposedHelpers.findAndHookMethod(clockClass, "onDetachedFromWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                TextView clockView = (TextView) param.thisObject;
                unregisterClockView(clockView);
                checkSecondsTicker();
            }
        });

        // Hook updateClock
        try {
            XposedHelpers.findAndHookMethod(clockClass, "updateClock", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (sConfig.moduleEnabled) {
                        TextView clockView = (TextView) param.thisObject;
                        applyCustomFormatting(clockView);
                    }
                }
            });
        } catch (Throwable ignored) {
            // Some Samsung builds may use getSmallTime instead
        }

        // Hook getSmallTime if present
        try {
            XposedHelpers.findAndHookMethod(clockClass, "getSmallTime", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (sConfig.moduleEnabled) {
                        TextView clockView = (TextView) param.thisObject;
                        Calendar calendar = (Calendar) XposedHelpers.getObjectField(clockView, "mCalendar");
                        if (calendar == null) {
                            calendar = Calendar.getInstance();
                        }
                        CharSequence customText = ClockFormatter.format(clockView.getContext(), calendar, sConfig);
                        param.setResult(customText);
                    }
                }
            });
        } catch (Throwable ignored) {}

        // Hook onSetText / setText if needed
        try {
            XposedHelpers.findAndHookMethod(clockClass, "setText", CharSequence.class, TextView.BufferType.class, boolean.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (sConfig.moduleEnabled) {
                        TextView clockView = (TextView) param.thisObject;
                        Calendar calendar = (Calendar) XposedHelpers.getObjectField(clockView, "mCalendar");
                        if (calendar == null) {
                            calendar = Calendar.getInstance();
                        }
                        CharSequence customText = ClockFormatter.format(clockView.getContext(), calendar, sConfig);
                        param.args[0] = customText;
                    }
                }
            });
        } catch (Throwable ignored) {}
    }

    private static synchronized void registerClockView(TextView clockView) {
        sActiveClocks.add(new WeakReference<>(clockView));
    }

    private static synchronized void unregisterClockView(TextView clockView) {
        sActiveClocks.removeIf(ref -> ref.get() == null || ref.get() == clockView);
    }

    public static synchronized void updateAllClocks() {
        for (WeakReference<TextView> ref : sActiveClocks) {
            TextView tv = ref.get();
            if (tv != null && tv.isAttachedToWindow()) {
                applyCustomFormatting(tv);
            }
        }
    }

    private static void applyCustomFormatting(TextView clockView) {
        if (clockView == null || !sConfig.moduleEnabled) return;

        Context context = clockView.getContext();
        Calendar cal = Calendar.getInstance();

        // 1. Text Format
        CharSequence formattedText = ClockFormatter.format(context, cal, sConfig);
        clockView.setText(formattedText);

        // 2. Multi-line adjustment
        if (sConfig.multiLine) {
            clockView.setSingleLine(false);
            clockView.setMaxLines(2);
            clockView.setLineSpacing(0, 0.9f);
        } else {
            clockView.setSingleLine(true);
            clockView.setMaxLines(1);
        }

        // 3. Custom font color
        if (sConfig.enableCustomColor && sConfig.customColorHex != null) {
            try {
                int color = Color.parseColor(sConfig.customColorHex);
                clockView.setTextColor(color);
            } catch (Exception ignored) {}
        }

        // 4. Font scale
        if (sConfig.fontScale != 100) {
            float baseSize = 14f; // Default status bar clock sp
            clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSize * (sConfig.fontScale / 100.0f));
        }
    }

    private static void ensureBroadcastReceiver(Context context) {
        if (sDynamicReceiver != null || context == null) return;

        sDynamicReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                if (PrefKeys.ACTION_PREF_CHANGED.equals(action)) {
                    updateConfigFromIntent(intent);
                    updateAllClocks();
                    checkSecondsTicker();
                    StatusBarLayoutHook.applyPositionToAll();
                } else if (PrefKeys.ACTION_UPDATE_WEATHER.equals(action)) {
                    WeatherHelper.WeatherInfo info = WeatherHelper.parseFromIntent(intent);
                    if (info != null) {
                        sConfig.weatherInfo = info;
                        updateAllClocks();
                    }
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(PrefKeys.ACTION_PREF_CHANGED);
        filter.addAction(PrefKeys.ACTION_UPDATE_WEATHER);
        filter.addAction("com.sec.android.daemonapp.weather.UPDATE_WEATHER");

        try {
            context.getApplicationContext().registerReceiver(sDynamicReceiver, filter, Context.RECEIVER_EXPORTED);
            XposedBridge.log(TAG + ": Dynamic BroadcastReceiver registered in SystemUI");
        } catch (Throwable t) {
            try {
                context.getApplicationContext().registerReceiver(sDynamicReceiver, filter);
            } catch (Throwable ignored) {}
        }
    }

    private static synchronized void checkSecondsTicker() {
        boolean needTicker = sConfig.moduleEnabled && sConfig.showSeconds && !sActiveClocks.isEmpty();

        if (needTicker && !sIsTickerRunning) {
            if (sSecondsHandler == null) {
                sSecondsHandler = new Handler(Looper.getMainLooper());
            }
            sSecondsTicker = new Runnable() {
                @Override
                public void run() {
                    if (sIsTickerRunning) {
                        updateAllClocks();
                        long now = System.currentTimeMillis();
                        long delay = 1000 - (now % 1000);
                        sSecondsHandler.postDelayed(this, delay);
                    }
                }
            };
            sIsTickerRunning = true;
            sSecondsHandler.post(sSecondsTicker);
        } else if (!needTicker && sIsTickerRunning) {
            sIsTickerRunning = false;
            if (sSecondsHandler != null && sSecondsTicker != null) {
                sSecondsHandler.removeCallbacks(sSecondsTicker);
            }
        }
    }

    private static void loadConfig(XSharedPreferences prefs) {
        if (prefs == null) return;
        sConfig.moduleEnabled = prefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEFAULT_MODULE_ENABLED);
        sConfig.formatMode = prefs.getString(PrefKeys.KEY_FORMAT_MODE, PrefKeys.DEFAULT_FORMAT_MODE);
        sConfig.customPattern = prefs.getString(PrefKeys.KEY_CUSTOM_PATTERN, PrefKeys.DEFAULT_CUSTOM_PATTERN);
        sConfig.showSeconds = prefs.getBoolean(PrefKeys.KEY_SHOW_SECONDS, PrefKeys.DEFAULT_SHOW_SECONDS);
        sConfig.datePreset = prefs.getString(PrefKeys.KEY_DATE_PRESET, PrefKeys.DEFAULT_DATE_PRESET);
        sConfig.weekPreset = prefs.getString(PrefKeys.KEY_WEEK_PRESET, PrefKeys.DEFAULT_WEEK_PRESET);
        sConfig.enableLunar = prefs.getBoolean(PrefKeys.KEY_ENABLE_LUNAR, PrefKeys.DEFAULT_ENABLE_LUNAR);
        sConfig.lunarShowYear = prefs.getBoolean(PrefKeys.KEY_LUNAR_SHOW_YEAR, PrefKeys.DEFAULT_LUNAR_SHOW_YEAR);
        sConfig.lunarShowSolarTerm = prefs.getBoolean(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM, PrefKeys.DEFAULT_LUNAR_SHOW_SOLAR_TERM);
        sConfig.enableWeather = prefs.getBoolean(PrefKeys.KEY_ENABLE_WEATHER, PrefKeys.DEFAULT_ENABLE_WEATHER);
        sConfig.weatherFormat = prefs.getString(PrefKeys.KEY_WEATHER_FORMAT, PrefKeys.DEFAULT_WEATHER_FORMAT);
        sConfig.fontScale = prefs.getInt(PrefKeys.KEY_FONT_SCALE, PrefKeys.DEFAULT_FONT_SCALE);
        sConfig.enableCustomColor = prefs.getBoolean(PrefKeys.KEY_ENABLE_CUSTOM_COLOR, PrefKeys.DEFAULT_ENABLE_CUSTOM_COLOR);
        sConfig.customColorHex = prefs.getString(PrefKeys.KEY_CUSTOM_COLOR_HEX, PrefKeys.DEFAULT_CUSTOM_COLOR_HEX);
        sConfig.multiLine = prefs.getBoolean(PrefKeys.KEY_MULTI_LINE, PrefKeys.DEFAULT_MULTI_LINE);

        sConfig.weatherInfo = WeatherHelper.loadFromPreferences(prefs);
    }

    private static void updateConfigFromIntent(Intent intent) {
        if (intent == null) return;
        if (intent.hasExtra(PrefKeys.KEY_MODULE_ENABLED)) {
            sConfig.moduleEnabled = intent.getBooleanExtra(PrefKeys.KEY_MODULE_ENABLED, sConfig.moduleEnabled);
        }
        if (intent.hasExtra(PrefKeys.KEY_FORMAT_MODE)) {
            sConfig.formatMode = intent.getStringExtra(PrefKeys.KEY_FORMAT_MODE);
        }
        if (intent.hasExtra(PrefKeys.KEY_CUSTOM_PATTERN)) {
            sConfig.customPattern = intent.getStringExtra(PrefKeys.KEY_CUSTOM_PATTERN);
        }
        if (intent.hasExtra(PrefKeys.KEY_SHOW_SECONDS)) {
            sConfig.showSeconds = intent.getBooleanExtra(PrefKeys.KEY_SHOW_SECONDS, sConfig.showSeconds);
        }
        if (intent.hasExtra(PrefKeys.KEY_DATE_PRESET)) {
            sConfig.datePreset = intent.getStringExtra(PrefKeys.KEY_DATE_PRESET);
        }
        if (intent.hasExtra(PrefKeys.KEY_WEEK_PRESET)) {
            sConfig.weekPreset = intent.getStringExtra(PrefKeys.KEY_WEEK_PRESET);
        }
        if (intent.hasExtra(PrefKeys.KEY_ENABLE_LUNAR)) {
            sConfig.enableLunar = intent.getBooleanExtra(PrefKeys.KEY_ENABLE_LUNAR, sConfig.enableLunar);
        }
        if (intent.hasExtra(PrefKeys.KEY_LUNAR_SHOW_YEAR)) {
            sConfig.lunarShowYear = intent.getBooleanExtra(PrefKeys.KEY_LUNAR_SHOW_YEAR, sConfig.lunarShowYear);
        }
        if (intent.hasExtra(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM)) {
            sConfig.lunarShowSolarTerm = intent.getBooleanExtra(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM, sConfig.lunarShowSolarTerm);
        }
        if (intent.hasExtra(PrefKeys.KEY_ENABLE_WEATHER)) {
            sConfig.enableWeather = intent.getBooleanExtra(PrefKeys.KEY_ENABLE_WEATHER, sConfig.enableWeather);
        }
        if (intent.hasExtra(PrefKeys.KEY_WEATHER_FORMAT)) {
            sConfig.weatherFormat = intent.getStringExtra(PrefKeys.KEY_WEATHER_FORMAT);
        }
        if (intent.hasExtra(PrefKeys.KEY_FONT_SCALE)) {
            sConfig.fontScale = intent.getIntExtra(PrefKeys.KEY_FONT_SCALE, sConfig.fontScale);
        }
        if (intent.hasExtra(PrefKeys.KEY_ENABLE_CUSTOM_COLOR)) {
            sConfig.enableCustomColor = intent.getBooleanExtra(PrefKeys.KEY_ENABLE_CUSTOM_COLOR, sConfig.enableCustomColor);
        }
        if (intent.hasExtra(PrefKeys.KEY_CUSTOM_COLOR_HEX)) {
            sConfig.customColorHex = intent.getStringExtra(PrefKeys.KEY_CUSTOM_COLOR_HEX);
        }
        if (intent.hasExtra(PrefKeys.KEY_MULTI_LINE)) {
            sConfig.multiLine = intent.getBooleanExtra(PrefKeys.KEY_MULTI_LINE, sConfig.multiLine);
        }
    }
}
