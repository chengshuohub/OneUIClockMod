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
 * 核心 Xposed Hook 类：负责拦截三星 One UI 状态栏时钟视图，
 * 注入自定义时间格式、农历、天气以及秒数实时跳动逻辑，并自带详细调试日志。
 */
public class ClockHook {

    private static final String TAG = "OneUIClockMod_Debug";
    private static final Set<WeakReference<TextView>> sActiveClocks = new HashSet<>();
    private static ClockFormatter.FormatConfig sConfig = new ClockFormatter.FormatConfig();
    private static Handler sSecondsHandler;
    private static Runnable sSecondsTicker;
    private static boolean sIsTickerRunning = false;
    private static BroadcastReceiver sDynamicReceiver;

    public static void init(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        XposedBridge.log(TAG + ": === ClockHook 模块开始初始化，目标包名: " + lpparam.packageName + " ===");
        loadConfig(prefs);

        String[] candidateClasses = new String[]{
            "com.android.systemui.statusbar.policy.Clock",
            "com.android.systemui.statusbar.views.DismissingStatusBarClockView",
            "com.android.systemui.statusbar.views.Clock",
            "com.android.systemui.statusbar.phone.StatusBarClockView",
            "com.samsung.systemui.statusbar.policy.Clock",
            "com.android.systemui.statusbar.policy.QSClock"
        };

        boolean hookedAny = false;
        for (String className : candidateClasses) {
            try {
                Class<?> clockClass = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (clockClass != null && TextView.class.isAssignableFrom(clockClass)) {
                    hookClockClass(clockClass);
                    hookedAny = true;
                    XposedBridge.log(TAG + ": >>> 成功 Hook 时钟类: " + className);
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Hook 类 " + className + " 异常 -> " + t.getMessage());
            }
        }

        if (!hookedAny) {
            XposedBridge.log(TAG + ": !!! 警告: 未能匹配任何时钟类！");
        }
    }

    private static void hookClockClass(Class<?> clockClass) {
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
                        TextView clockView = (TextView) param.thisObject;
                        applyCustomFormatting(clockView);
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
                String action = intent.getAction();
                if (PrefKeys.ACTION_PREF_CHANGED.equals(action)) {
                    loadConfigFromIntent(intent);
                    updateAllClocks();
                    checkSecondsTicker();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(PrefKeys.ACTION_PREF_CHANGED);
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