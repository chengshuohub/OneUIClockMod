package com.shoren.oneui.clockmod.xposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "OneUIClockMod_Debug";
    private static final String SYSTEM_UI_PACKAGE = "com.android.systemui";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!SYSTEM_UI_PACKAGE.equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + ": === 命中 SystemUI 进程，开始注入 ===");
        
        XSharedPreferences prefs = new XSharedPreferences("com.shoren.oneui.clockmod", "com.shoren.oneui.clockmod_preferences");
        prefs.makeWorldReadable();

        ClockHook.init(lpparam, prefs);
        StatusBarLayoutHook.init(lpparam, prefs);
    }
}
