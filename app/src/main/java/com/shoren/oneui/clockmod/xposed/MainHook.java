package com.shoren.oneui.clockmod.xposed;

import android.os.Build;

import com.shoren.oneui.clockmod.utils.PrefKeys;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Xposed Entry Point for Samsung One UI Clock Customizer.
 * Scoped to com.android.systemui.
 */
public class MainHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    public static final String TAG = "OneUIClockMod";
    public static XSharedPreferences sPrefs;

    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        sPrefs = new XSharedPreferences(PrefKeys.PACKAGE_NAME, PrefKeys.PREFS_NAME);
        sPrefs.makeWorldReadable();
        XposedBridge.log(TAG + ": Initialized Zygote, prefs loaded.");
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.android.systemui".equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + ": Hooking Samsung SystemUI on " + Build.MODEL + " (Android " + Build.VERSION.RELEASE + ")");

        // Reload prefs if needed
        if (sPrefs != null) {
            sPrefs.reload();
        } else {
            sPrefs = new XSharedPreferences(PrefKeys.PACKAGE_NAME, PrefKeys.PREFS_NAME);
            sPrefs.makeWorldReadable();
        }

        // Initialize Core Hooks
        try {
            ClockHook.init(lpparam, sPrefs);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " Error in ClockHook.init: " + t.getMessage());
        }

        try {
            StatusBarLayoutHook.init(lpparam, sPrefs);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " Error in StatusBarLayoutHook.init: " + t.getMessage());
        }
    }
}
