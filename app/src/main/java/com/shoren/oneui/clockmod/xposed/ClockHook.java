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

public class ClockHook {

    private static final String TAG = "OneUIClockMod_Debug";
    private static final Set<WeakReference<TextView>> sActiveClocks = new HashSet<>();
    private static ClockFormatter.FormatConfig sConfig = new ClockFormatter.FormatConfig();
    private static Handler sSecondsHandler;
    private static Runnable sSecondsTicker;
    private static boolean sIsTickerRunning = false;
    private static BroadcastReceiver sDynamicReceiver;

    public static void init(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        XposedBridge.log(TAG + ": === ClockHook 初始化 (Target: " + lpparam.packageName + ") ===");
        loadConfig(prefs);

        // 扩充三星 One UI 8 及各版本核心时钟类 Candidate 候选池
        String[] candidateClasses = new String[]{
            "com.android.systemui.statusbar.policy.Clock",
            "com.android.systemui.statusbar.views.DismissingStatusBarClockView",
            "com.android.systemui.statusbar.views.Clock",
            "com.android.systemui.statusbar.phone.StatusBarClockView",
            "com.samsung.systemui.statusbar.policy.Clock",
            "com.samsung.android.systemui.statusbar.policy.SamsungClock",
            "com.samsung.android.systemui.statusbar.views.SamsungClockView",
            "com.android.systemui.statusbar.policy.QSClock"
        };

        boolean hookedAny = false;
        for (String className : candidateClasses) {
            try {
                Class<?> clockClass = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (clockClass != null && TextView.class.isAssignableFrom(clockClass)) {
                    hookClockClass(clockClass);
                    hookedAny = true;
                    XposedBridge.log(TAG + ": >>> 成功挂钩时钟控件类: " + className);
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": 挂钩 " + className + " 失败: " + t.getMessage());
            }
        }

        if (!hookedAny) {
            XposedBridge.log(TAG + ": !!! 警告: 没有在 SystemUI 中识别到任何匹配的时钟类！");
        }
    }

    private static void hookClockClass(Class<?> clockClass) {
        XposedHelpers.findAndHookMethod(clockClass, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                TextView clockView = (TextView) param.thisObject;
                XposedBridge.log(TAG + ": View Attached: " + clockView.getClass().getName());
                registerClockView(clockView);
                ensureBroadcastReceiver(clockView.getContext());
                applyCustomFormatting(clockView);
                checkSecondsTicker();
            }
        });

        XposedHelpers.findAndHookMethod(clockClass, "onDetachedFromWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                TextView clockView = (TextView) param.thisObject;
                unregisterClockView(clockView);
                checkSecondsTicker();
            }
        });

        try {
            XposedHelpers.findAndHookMethod(clockClass, "updateClock", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (sConfig.moduleEnabled) {
                        applyCustomFormatting((TextView) param.thisObject);
                    }
                }
            });
        } catch (Throwable ignored) {}

        try {
            XposedHelpers.findAndHookMethod(clockClass, "getSmallTime", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    if (sConfig.moduleEnabled) {
                        TextView clockView = (TextView) param.thisObject;
                        Calendar cal = Calendar.getInstance();
                        CharSequence customText = ClockFormatter.format(clockView.getContext(), cal, sConfig);
                        param.setResult(customText);
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

        CharSequence formattedText = ClockFormatter.format(context, cal, sConfig);
        clockView.setText(formattedText);

        if (sConfig.multiLine) {
            clockView.setSingleLine(false);
            clockView.setMaxLines(2);
            clockView.setLineSpacing(0, 0.9f);
        } else {
            clockView.setSingleLine(true);
            clockView.setMaxLines(1);
        }

        if (sConfig.enableCustomColor && sConfig.customColorHex != null) {
            try {
                int color = Color.parseColor(sConfig.customColorHex);
                clockView.setTextColor(color);
            } catch (Exception ignored) {}
        }

        if (sConfig.fontScale != 100) {
            float baseSize = 14f;
            clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSize * (sConfig.fontScale / 100.0f));
        }
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

        IntentFilter filter = new IntentFilter(PrefKeys.ACTION_PREF_CHANGED);
        try {
            context.getApplicationContext().registerReceiver(sDynamicReceiver, filter, Context.RECEIVER_EXPORTED);
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
        prefs.reload();
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
    }

    private static void loadConfigFromIntent(Intent intent) {
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