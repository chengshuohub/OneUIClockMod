package com.shoren.oneui.clockmod.xposed;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import com.shoren.oneui.clockmod.utils.PrefKeys;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class StatusBarLayoutHook {
    private static final String TAG = "OneUIClockMod_LayoutHook";
    private static String sClockPosition = "left";
    private static int sOffsetX = 0;
    private static int sOffsetY = 0;
    private static boolean sModuleEnabled = true;
    private static final List<ClockContainerHolder> sHolders = new ArrayList<>();
    private static BroadcastReceiver sDynamicReceiver;

    public static class ClockContainerHolder {
        public WeakReference<View> clockViewRef;
        public WeakReference<ViewGroup> originalParentRef;
        public int originalIndex = 0;
        public WeakReference<ViewGroup> centerContainerRef;
        public WeakReference<ViewGroup> rightContainerRef;
    }

    public static void init(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        if (prefs != null) {
            prefs.reload();
            sModuleEnabled = prefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, true);
            sClockPosition = prefs.getString("key_clock_position", "left");
            sOffsetX = prefs.getInt("key_horizontal_offset", 0);
            sOffsetY = prefs.getInt("key_vertical_offset", 0);
        }
        try {
            Class<?> phoneStatusBarViewClass = XposedHelpers.findClassIfExists("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader);
            if (phoneStatusBarViewClass != null) {
                XposedHelpers.findAndHookMethod(phoneStatusBarViewClass, "onFinishInflate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        processStatusBarView((ViewGroup) param.thisObject);
                    }
                });
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error hooking PhoneStatusBarView: " + t.getMessage());
        }
    }

    private static void processStatusBarView(ViewGroup statusBarView) {
        if (statusBarView == null) return;
        Context context = statusBarView.getContext();
        ensureBroadcastReceiver(context);

        int clockId = context.getResources().getIdentifier("clock", "id", "com.android.systemui");
        if (clockId == 0) clockId = context.getResources().getIdentifier("status_bar_clock", "id", "com.android.systemui");

        View clockView = clockId != 0 ? statusBarView.findViewById(clockId) : null;
        if (clockView == null) clockView = findClockViewRecursively(statusBarView);
        if (clockView == null) return;

        ViewGroup originalParent = (ViewGroup) clockView.getParent();
        if (originalParent == null) return;

        int originalIndex = originalParent.indexOfChild(clockView);
        ViewGroup centerContainer = findOrCreateCenterContainer(statusBarView);

        int systemIconsId = context.getResources().getIdentifier("system_icons", "id", "com.android.systemui");
        ViewGroup rightContainer = systemIconsId != 0 ? statusBarView.findViewById(systemIconsId) : null;

        ClockContainerHolder holder = new ClockContainerHolder();
        holder.clockViewRef = new WeakReference<>(clockView);
        holder.originalParentRef = new WeakReference<>(originalParent);
        holder.originalIndex = originalIndex;
        holder.centerContainerRef = new WeakReference<>(centerContainer);
        holder.rightContainerRef = new WeakReference<>(rightContainer);

        synchronized (sHolders) { sHolders.add(holder); }
        applyPosition(holder);
    }

    private static ViewGroup findOrCreateCenterContainer(ViewGroup statusBarView) {
        Context context = statusBarView.getContext();
        FrameLayout centerLayout = new FrameLayout(context);
        if (statusBarView instanceof RelativeLayout) {
            RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            rlp.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            centerLayout.setLayoutParams(rlp);
        } else {
            centerLayout.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        }
        try { statusBarView.addView(centerLayout); return centerLayout; } catch (Exception e) { return null; }
    }

    private static View findClockViewRecursively(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView && child.getClass().getName().toLowerCase().contains("clock")) return child;
            if (child instanceof ViewGroup) {
                View found = findClockViewRecursively((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static synchronized void applyPositionToAll() {
        for (ClockContainerHolder holder : sHolders) applyPosition(holder);
    }

    private static void applyPosition(ClockContainerHolder holder) {
        if (holder == null) return;
        View clock = holder.clockViewRef.get();
        ViewGroup originalParent = holder.originalParentRef.get();
        if (clock == null || originalParent == null) return;

        if (!sModuleEnabled) {
            moveToParent(clock, originalParent, holder.originalIndex);
            clock.setVisibility(View.VISIBLE);
            resetMargins(clock);
            return;
        }

        switch (sClockPosition) {
            case "hide": case "3": clock.setVisibility(View.GONE); break;
            case "center": case "1":
                clock.setVisibility(View.VISIBLE);
                if (holder.centerContainerRef.get() != null) moveToParent(clock, holder.centerContainerRef.get(), 0);
                applyMargins(clock, sOffsetX, sOffsetY);
                break;
            case "right": case "2":
                clock.setVisibility(View.VISIBLE);
                if (holder.rightContainerRef.get() != null) moveToParent(clock, holder.rightContainerRef.get(), 0);
                applyMargins(clock, sOffsetX, sOffsetY);
                break;
            default:
                clock.setVisibility(View.VISIBLE);
                moveToParent(clock, originalParent, holder.originalIndex);
                applyMargins(clock, sOffsetX, sOffsetY);
                break;
        }
    }

    private static void moveToParent(View view, ViewGroup targetParent, int index) {
        ViewGroup currentParent = (ViewGroup) view.getParent();
        if (currentParent == targetParent) return;
        if (currentParent != null) currentParent.removeView(view);
        if (index < 0 || index > targetParent.getChildCount()) targetParent.addView(view);
        else targetParent.addView(view, index);
    }

    private static void applyMargins(View view, int offsetX, int offsetY) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            mlp.leftMargin = Math.round(offsetX * view.getContext().getResources().getDisplayMetrics().density);
            mlp.topMargin = Math.round(offsetY * view.getContext().getResources().getDisplayMetrics().density);
            view.setLayoutParams(mlp);
        }
    }

    private static void resetMargins(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).leftMargin = 0;
            ((ViewGroup.MarginLayoutParams) lp).topMargin = 0;
            view.setLayoutParams(lp);
        }
    }

    private static void ensureBroadcastReceiver(Context context) {
        if (sDynamicReceiver != null || context == null) return;
        sDynamicReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                if (PrefKeys.ACTION_PREF_CHANGED.equals(intent.getAction())) {
                    if (intent.hasExtra(PrefKeys.KEY_MODULE_ENABLED)) sModuleEnabled = intent.getBooleanExtra(PrefKeys.KEY_MODULE_ENABLED, sModuleEnabled);
                    if (intent.hasExtra("key_clock_position")) sClockPosition = intent.getStringExtra("key_clock_position");
                    if (intent.hasExtra("key_horizontal_offset")) sOffsetX = intent.getIntExtra("key_horizontal_offset", sOffsetX);
                    if (intent.hasExtra("key_vertical_offset")) sOffsetY = intent.getIntExtra("key_vertical_offset", sOffsetY);
                    applyPositionToAll();
                }
            }
        };
        try { context.getApplicationContext().registerReceiver(sDynamicReceiver, new IntentFilter(PrefKeys.ACTION_PREF_CHANGED), Context.RECEIVER_EXPORTED); } 
        catch (Throwable t) { try { context.getApplicationContext().registerReceiver(sDynamicReceiver, new IntentFilter(PrefKeys.ACTION_PREF_CHANGED)); } catch (Throwable ignored) {} }
    }
}
