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
    // 保存当前活跃的时钟视图弱引用，方便统一刷新
    private static final Set<WeakReference<TextView>> sActiveClocks = new HashSet<>();
    private static ClockFormatter.FormatConfig sConfig = new ClockFormatter.FormatConfig();
    private static Handler sSecondsHandler;
    private static Runnable sSecondsTicker;
    private static boolean sIsTickerRunning = false;
    private static BroadcastReceiver sDynamicReceiver;

    /**
     * Xposed 模块入口初始化方法
     */
    public static void init(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        XposedBridge.log(TAG + ": === ClockHook 模块开始初始化，目标包名: " + lpparam.packageName + " ===");
        loadConfig(prefs);
        XposedBridge.log(TAG + ": 配置加载成功. 模块总开关状态 (moduleEnabled) = " + sConfig.moduleEnabled);

        // 三星 One UI 及各代系统可能用到的时钟类路径候选池
        String[] candidateClasses = new String[]{
            "com.android.systemui.statusbar.policy.Clock",
            "com.android.systemui.statusbar.views.DismissingStatusBarClockView",
            "com.android.systemui.statusbar.views.Clock",
            "com.android.systemui.statusbar.phone.StatusBarClockView",
            "com.samsung.systemui.statusbar.policy.Clock",
            "com.android.systemui.statusbar.policy.QSClock"
        };

        boolean hookedAny = false;
        // 依次遍历候选类名并尝试 Hook
        for (String className : candidateClasses) {
            try {
                Class<?> clockClass = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (clockClass != null) {
                    XposedBridge.log(TAG + ": 发现目标类 -> " + className + ", 是否继承自 TextView: " + TextView.class.isAssignableFrom(clockClass));
                    if (TextView.class.isAssignableFrom(clockClass)) {
                        hookClockClass(clockClass);
                        hookedAny = true;
                        XposedBridge.log(TAG + ": >>> 成功成功 Hook 时钟类: " + className);
                    }
                } else {
                    XposedBridge.log(TAG + ": 未找到类: " + className);
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": Hook 类 " + className + " 时发生异常 -> " + t.getMessage());
            }
        }

        if (!hookedAny) {
            XposedBridge.log(TAG + ": !!! 警告: 在当前的 SystemUI 中没有成功匹配并 Hook 任何时钟类！");
        }
    }

    /**
     * 对找到的时钟类进行具体的方法拦截 (Hook)
     */
    private static void hookClockClass(Class<?> clockClass) {
        // 1. 监听时钟控件被加载到窗口时 (onAttachedToWindow)
        XposedHelpers.findAndHookMethod(clockClass, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                TextView clockView = (TextView) param.thisObject;
                XposedBridge.log(TAG + ": 时钟控件触发 onAttachedToWindow: " + clockView);
                registerClockView(clockView);
                ensureBroadcastReceiver(clockView.getContext());
                applyCustomFormatting(clockView);
                checkSecondsTicker();
            }
        });

        // 2. 监听时钟控件从窗口移除时 (onDetachedFromWindow)
        XposedHelpers.findAndHookMethod(clockClass, "onDetachedFromWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                TextView clockView = (TextView) param.thisObject;
                XposedBridge.log(TAG + ": 时钟控件触发 onDetachedFromWindow");
                unregisterClockView(clockView);
                checkSecondsTicker();
            }
        });

        // 3. 尝试 Hook 系统自带的 updateClock 更新方法
        try {
            XposedHelpers.findAndHookMethod(clockClass, "updateClock", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log(TAG + ": 触发 updateClock 方法");
                    if (sConfig.moduleEnabled) {
                        TextView clockView = (TextView) param.thisObject;
                        applyCustomFormatting(clockView);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": updateClock 方法不存在或 Hook 失败: " + t.getMessage());
        }

        // 4. 尝试 Hook 获取短时间文本的方法 (getSmallTime)
        try {
            XposedHelpers.findAndHookMethod(clockClass, "getSmallTime", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                    XposedBridge.log(TAG + ": 触发 getSmallTime 方法, 原返回结果: " + param.getResult());
                    if (sConfig.moduleEnabled) {
                        TextView clockView = (TextView) param.thisObject;
                        Calendar calendar = (Calendar) XposedHelpers.getObjectField(clockView, "mCalendar");
                        if (calendar == null) {
                            calendar = Calendar.getInstance();
                        }
                        CharSequence customText = ClockFormatter.format(clockView.getContext(), calendar, sConfig);
                        XposedBridge.log(TAG + ": 正在将时钟文本强行覆写为: " + customText);
                        param.setResult(customText);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": getSmallTime 方法不存在: " + t.getMessage());
        }
    }

    /**
     * 注册当前活跃的时钟实例
     */
    private static synchronized void registerClockView(TextView clockView) {
        sActiveClocks.add(new WeakReference<>(clockView));
    }

    /**
     * 移除失效的时钟实例
     */
    private static synchronized void unregisterClockView(TextView clockView) {
        sActiveClocks.removeIf(ref -> ref.get() == null || ref.get() == clockView);
    }

    /**
     * 刷新所有活跃的时钟视图
     */
    public static synchronized void updateAllClocks() {
        XposedBridge.log(TAG + ": 触发 updateAllClocks，当前活跃时钟数: " + sActiveClocks.size());
        for (WeakReference<TextView> ref : sActiveClocks) {
            TextView tv = ref.get();
            if (tv != null && tv.isAttachedToWindow()) {
                applyCustomFormatting(tv);
            }
        }
    }

    /**
     * 将自定义的时间格式、字体、颜色应用到时钟 TextView 上
     */
    private static void applyCustomFormatting(TextView clockView) {
        if (clockView == null) return;
        XposedBridge.log(TAG + ": 执行 applyCustomFormatting. moduleEnabled = " + sConfig.moduleEnabled);
        if (!sConfig.moduleEnabled) return;

        Context context = clockView.getContext();
        Calendar cal = Calendar.getInstance();

        // 1. 格式化并设置自定义文本
        CharSequence formattedText = ClockFormatter.format(context, cal, sConfig);
        XposedBridge.log(TAG + ": 正在向状态栏写入格式化文本: " + formattedText);
        clockView.setText(formattedText);

        // 2. 处理多行紧凑布局
        if (sConfig.multiLine) {
            clockView.setSingleLine(false);
            clockView.setMaxLines(2);
            clockView.setLineSpacing(0, 0.9f);
        } else {
            clockView.setSingleLine(true);
            clockView.setMaxLines(1);
        }

        // 3. 自定义文字颜色
        if (sConfig.enableCustomColor && sConfig.customColorHex != null) {
            try {
                int color = Color.parseColor(sConfig.customColorHex);
                clockView.setTextColor(color);
            } catch (Exception ignored) {}
        }

        // 4. 字体缩放大小
        if (sConfig.fontScale != 100) {
            float baseSize = 14f; // 状态栏默认 sp 大小
            clockView.setTextSize(TypedValue.COMPLEX_UNIT_SP, baseSize * (sConfig.fontScale / 100.0f));
        }
    }

    /**
     * 确保动态广播接收器已注册，用于接收设置界面的实时配置更改
     */
    private static void ensureBroadcastReceiver(Context context) {
        if (sDynamicReceiver != null || context == null) return;

        sDynamicReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                String action = intent.getAction();
                XposedBridge.log(TAG + ": 收到动态广播 action: " + action);
                if (PrefKeys.ACTION_PREF_CHANGED.equals(action)) {
                    loadConfigFromIntent(intent);
                    updateAllClocks();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction(PrefKeys.ACTION_PREF_CHANGED);
        try {
            context.getApplicationContext().registerReceiver(sDynamicReceiver, filter, Context.RECEIVER_EXPORTED);
            XposedBridge.log(TAG + ": 动态广播接收器注册成功。");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 动态广播接收器注册失败: " + t.getMessage());
        }
    }

    /**
     * 如果开启了秒数显示，启动定时器每秒刷新
     */
    private static void checkSecondsTicker() {
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
                        long delay = 1000 - (now % 1000); // 毫秒级对齐，保证秒跳动精准
                        sSecondsHandler.postDelayed(this, delay);
                    }
                }
            };
            sIsTickerRunning = true;
            sSecondsHandler.post(sSecondsTicker);
        }
    }

    /**
     * 从配置文件加载初始配置
     */
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
        sConfig.fontScale = prefs.getInt(PrefKeys.KEY_FONT_SCALE, PrefKeys.DEFAULT_FONT_SCALE);
        sConfig.multiLine = prefs.getBoolean(PrefKeys.KEY_MULTI_LINE, PrefKeys.DEFAULT_MULTI_LINE);
    }

    /**
     * 从 Intent 广播中实时更新配置项
     */
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
        if (intent.hasExtra(PrefKeys.KEY_ENABLE_LUNAR)) {
            sConfig.enableLunar = intent.getBooleanExtra(PrefKeys.KEY_ENABLE_LUNAR, sConfig.enableLunar);
        }
        if (intent.hasExtra(PrefKeys.KEY_FONT_SCALE)) {
            sConfig.fontScale = intent.getIntExtra(PrefKeys.KEY_FONT_SCALE, sConfig.fontScale);
        }
        if (intent.hasExtra(PrefKeys.KEY_MULTI_LINE)) {
            sConfig.multiLine = intent.getBooleanExtra(PrefKeys.KEY_MULTI_LINE, sConfig.multiLine);
        }
    }
}