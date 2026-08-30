package com.shoren.oneui.clockmod.xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.widget.TextView;
import com.shoren.oneui.clockmod.utils.ClockFormatter;
import com.shoren.oneui.clockmod.utils.PrefKeys;
import java.lang.ref.WeakReference;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ClockHook {
    private static final String TAG = "OneUIClockMod_Debug";
    private static final Set<WeakReference<TextView>> sActiveClocks = new HashSet<>();
    private static ClockFormatter.FormatConfig sConfig = new ClockFormatter.FormatConfig();
    private static Handler sSecondsHandler;
    private static Runnable sSecondsTicker;
    private static boolean sIsTickerRunning = false;
    private static BroadcastReceiver sDynamicReceiver;

    public static void init(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        loadConfig(prefs);
        String[] candidateClasses = {
            "com.android.systemui.statusbar.policy.Clock",
            "com.android.systemui.statusbar.views.DismissingStatusBarClockView",
            "com.samsung.systemui.statusbar.policy.Clock",
            "com.samsung.android.systemui.statusbar.policy.SamsungClock",
            "com.samsung.android.systemui.statusbar.views.SamsungClockView"
        };
        for (String className : candidateClasses) {
            try {
                Class<?> clockClass = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (clockClass != null && TextView.class.isAssignableFrom(clockClass)) {
                    hookClockClass(clockClass);
                    XposedBridge.log(TAG + ": >>> 成功挂钩时钟类: " + className);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void hookClockClass(Class<?> clockClass) {
        XposedHelpers.findAndHookMethod(clockClass, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                TextView clockView = (TextView) param.thisObject;
                sActiveClocks.add(new WeakReference<>(clockView));
                ensureBroadcastReceiver(clockView.getContext());
                applyCustomFormatting(clockView);
                checkSecondsTicker();
            }
        });
        XposedHelpers.findAndHookMethod(clockClass, "onDetachedFromWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                TextView clockView = (TextView) param.thisObject;
                sActiveClocks.removeIf(ref -> ref.get() == null || ref.get() == clockView);
                checkSecondsTicker();
            }
        });
        try {
            XposedHelpers.findAndHookMethod(clockClass, "updateClock", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (sConfig.moduleEnabled) applyCustomFormatting((TextView) param.thisObject);
                }
            });
        } catch (Throwable ignored) {}
    }

    public static synchronized void updateAllClocks() {
        for (WeakReference<TextView> ref : sActiveClocks) {
            TextView tv = ref.get();
            if (tv != null && tv.isAttachedToWindow()) applyCustomFormatting(tv);
        }
    }

    private static void applyCustomFormatting(TextView clockView) {
        if (clockView == null || !sConfig.moduleEnabled) return;
        clockView.setText(ClockFormatter.format(clockView.getContext(), Calendar.getInstance(), sConfig));
        if (sConfig.multiLine) {
            clockView.setSingleLine(false);
            clockView.setMaxLines(2);
            clockView.setLineSpacing(0, 0.9f);
        } else {
            clockView.setSingleLine(true);
            clockView.setMaxLines(1);
        }
        if (sConfig.enableCustomColor && sConfig.customColorHex != null) {
            try { clockView.setTextColor(Color.parseColor(sConfig.customColorHex)); } catch (Exception ignored) {}
        }
        if (sConfig.fontScale != 100) clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f * (sConfig.fontScale / 100.0f));
    }

    private static void ensureBroadcastReceiver(Context context) {
        if (sDynamicReceiver != null || context == null) return;
        sDynamicReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (PrefKeys.ACTION_PREF_CHANGED.equals(intent.getAction())) {
                    loadConfigFromIntent(intent);
                    updateAllClocks();
                    checkSecondsTicker();
                }
            }
        };
        try { context.getApplicationContext().registerReceiver(sDynamicReceiver, new IntentFilter(PrefKeys.ACTION_PREF_CHANGED), Context.RECEIVER_EXPORTED); } 
        catch (Throwable t) { try { context.getApplicationContext().registerReceiver(sDynamicReceiver, new IntentFilter(PrefKeys.ACTION_PREF_CHANGED)); } catch (Throwable ignored) {} }
    }

    private static synchronized void checkSecondsTicker() {
        boolean needTicker = sConfig.moduleEnabled && sConfig.showSeconds && !sActiveClocks.isEmpty();
        if (needTicker && !sIsTickerRunning) {
            if (sSecondsHandler == null) sSecondsHandler = new Handler(Looper.getMainLooper());
            sSecondsTicker = new Runnable() {
                @Override
                public void run() {
                    if (sIsTickerRunning) {
                        updateAllClocks();
                        sSecondsHandler.postDelayed(this, 1000 - (System.currentTimeMillis() % 1000));
                    }
                }
            };
            sIsTickerRunning = true;
            sSecondsHandler.post(sSecondsTicker);
        } else if (!needTicker && sIsTickerRunning) {
            sIsTickerRunning = false;
            if (sSecondsHandler != null && sSecondsTicker != null) sSecondsHandler.removeCallbacks(sSecondsTicker);
        }
    }

    private static void loadConfig(XSharedPreferences prefs) {
        if (prefs == null) return;
        prefs.reload();
        sConfig.moduleEnabled = prefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, true);
        sConfig.formatMode = prefs.getString(PrefKeys.KEY_FORMAT_MODE, "preset");
        sConfig.customPattern = prefs.getString(PrefKeys.KEY_CUSTOM_PATTERN, "{TIME} {WEEK} {LUNAR}");
        sConfig.showSeconds = prefs.getBoolean(PrefKeys.KEY_SHOW_SECONDS, false);
        sConfig.datePreset = prefs.getString(PrefKeys.KEY_DATE_PRESET, "M月d日");
        sConfig.weekPreset = prefs.getString(PrefKeys.KEY_WEEK_PRESET, "zh_short");
        sConfig.enableLunar = prefs.getBoolean(PrefKeys.KEY_ENABLE_LUNAR, true);
        sConfig.lunarShowYear = prefs.getBoolean(PrefKeys.KEY_LUNAR_SHOW_YEAR, false);
        sConfig.lunarShowSolarTerm = prefs.getBoolean(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM, true);
        sConfig.enableWeather = prefs.getBoolean(PrefKeys.KEY_ENABLE_WEATHER, false);
        sConfig.weatherFormat = prefs.getString(PrefKeys.KEY_WEATHER_FORMAT, "text_temp");
        sConfig.fontScale = prefs.getInt(PrefKeys.KEY_FONT_SCALE, 100);
        sConfig.enableCustomColor = prefs.getBoolean(PrefKeys.KEY_ENABLE_CUSTOM_COLOR, false);
        sConfig.customColorHex = prefs.getString(PrefKeys.KEY_CUSTOM_COLOR_HEX, "#FFFFFF");
        sConfig.multiLine = prefs.getBoolean(PrefKeys.KEY_MULTI_LINE, false);
    }

    private static void loadConfigFromIntent(Intent intent) {
        if (intent == null) return;
        if (intent.hasExtra(PrefKeys.KEY_MODULE_ENABLED)) sConfig.moduleEnabled = intent.getBooleanExtra(PrefKeys.KEY_MODULE_ENABLED, sConfig.moduleEnabled);
        if (intent.hasExtra(PrefKeys.KEY_FORMAT_MODE)) sConfig.formatMode = intent.getStringExtra(PrefKeys.KEY_FORMAT_MODE);
        if (intent.hasExtra(PrefKeys.KEY_CUSTOM_PATTERN)) sConfig.customPattern = intent.getStringExtra(PrefKeys.KEY_CUSTOM_PATTERN);
        if (intent.hasExtra(PrefKeys.KEY_SHOW_SECONDS)) sConfig.showSeconds = intent.getBooleanExtra(PrefKeys.KEY_SHOW_SECONDS, sConfig.showSeconds);
        if (intent.hasExtra(PrefKeys.KEY_DATE_PRESET)) sConfig.datePreset = intent.getStringExtra(PrefKeys.KEY_DATE_PRESET);
        if (intent.hasExtra(PrefKeys.KEY_WEEK_PRESET)) sConfig.weekPreset = intent.getStringExtra(PrefKeys.KEY_WEEK_PRESET);
        if (intent.hasExtra(PrefKeys.KEY_ENABLE_LUNAR)) sConfig.enableLunar = intent.getBooleanExtra(PrefKeys.KEY_ENABLE_LUNAR, sConfig.enableLunar);
        if (intent.hasExtra(PrefKeys.KEY_LUNAR_SHOW_YEAR)) sConfig.lunarShowYear = intent.getBooleanExtra(PrefKeys.KEY_LUNAR_SHOW_YEAR, sConfig.lunarShowYear);
        if (intent.hasExtra(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM)) sConfig.lunarShowSolarTerm = intent.getBooleanExtra(PrefKeys.KEY_LUNAR_SHOW_SOLAR_TERM, sConfig.lunarShowSolarTerm);
        if (intent.hasExtra(PrefKeys.KEY_ENABLE_WEATHER)) sConfig.enableWeather = intent.getBooleanExtra(PrefKeys.KEY_ENABLE_WEATHER, sConfig.enableWeather);
        if (intent.hasExtra(PrefKeys.KEY_WEATHER_FORMAT)) sConfig.weatherFormat = intent.getStringExtra(PrefKeys.KEY_WEATHER_FORMAT);
        if (intent.hasExtra(PrefKeys.KEY_FONT_SCALE)) sConfig.fontScale = intent.getIntExtra(PrefKeys.KEY_FONT_SCALE, sConfig.fontScale);
        if (intent.hasExtra(PrefKeys.KEY_ENABLE_CUSTOM_COLOR)) sConfig.enableCustomColor = intent.getBooleanExtra(PrefKeys.KEY_ENABLE_CUSTOM_COLOR, sConfig.enableCustomColor);
        if (intent.hasExtra(PrefKeys.KEY_CUSTOM_COLOR_HEX)) sConfig.customColorHex = intent.getStringExtra(PrefKeys.KEY_CUSTOM_COLOR_HEX);
        if (intent.hasExtra(PrefKeys.KEY_MULTI_LINE)) sConfig.multiLine = intent.getBooleanExtra(PrefKeys.KEY_MULTI_LINE, sConfig.multiLine);
    }
}
