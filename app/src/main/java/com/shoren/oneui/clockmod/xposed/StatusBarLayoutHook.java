package com.shoren.oneui.clockmod.xposed;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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

/**
 * Handles dynamic status bar layout manipulation and clock repositioning
 * (Left, Center, Right, Hidden) specifically tuned for Samsung One UI & Galaxy S23 Ultra cutout geometry.
 */
public class StatusBarLayoutHook {

    private static final String TAG = "OneUIClockMod_LayoutHook";

    private static int sClockPosition = PrefKeys.DEFAULT_CLOCK_POSITION;
    private static int sOffsetX = PrefKeys.DEFAULT_CLOCK_OFFSET_X;
    private static int sOffsetY = PrefKeys.DEFAULT_CLOCK_OFFSET_Y;
    private static boolean sModuleEnabled = PrefKeys.DEFAULT_MODULE_ENABLED;

    private static final List<ClockContainerHolder> sHolders = new ArrayList<>();

    public static class ClockContainerHolder {
        public WeakReference<View> clockViewRef;
        public WeakReference<ViewGroup> originalParentRef;
        public int originalIndex = 0;
        public WeakReference<ViewGroup> statusBarRootRef;
        public WeakReference<ViewGroup> centerContainerRef;
        public WeakReference<ViewGroup> rightContainerRef;
    }

    public static void init(XC_LoadPackage.LoadPackageParam lpparam, XSharedPreferences prefs) {
        loadPrefs(prefs);

        // Hook PhoneStatusBarView
        try {
            Class<?> phoneStatusBarViewClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader
            );
            if (phoneStatusBarViewClass != null) {
                XposedHelpers.findAndHookMethod(phoneStatusBarViewClass, "onFinishInflate", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        ViewGroup statusBarView = (ViewGroup) param.thisObject;
                        processStatusBarView(statusBarView);
                    }
                });
                XposedBridge.log(TAG + ": Hooked PhoneStatusBarView.onFinishInflate");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": Error hooking PhoneStatusBarView: " + t.getMessage());
        }

        // Hook CollapsedStatusBarFragment if available
        try {
            Class<?> fragmentClass = XposedHelpers.findClassIfExists(
                "com.android.systemui.statusbar.phone.fragment.CollapsedStatusBarFragment", lpparam.classLoader
            );
            if (fragmentClass != null) {
                XposedHelpers.findAndHookMethod(fragmentClass, "initStatusBarModel", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        applyPositionToAll();
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static void processStatusBarView(ViewGroup statusBarView) {
        if (statusBarView == null) return;

        Context context = statusBarView.getContext();
        int clockId = context.getResources().getIdentifier("clock", "id", "com.android.systemui");
        if (clockId == 0) {
            clockId = context.getResources().getIdentifier("status_bar_clock", "id", "com.android.systemui");
        }

        View clockView = clockId != 0 ? statusBarView.findViewById(clockId) : null;
        if (clockView == null) {
            // Find by type
            clockView = findClockViewRecursively(statusBarView);
        }

        if (clockView == null) {
            XposedBridge.log(TAG + ": Clock view not found in PhoneStatusBarView hierarchy.");
            return;
        }

        ViewGroup originalParent = (ViewGroup) clockView.getParent();
        if (originalParent == null) return;

        int originalIndex = originalParent.indexOfChild(clockView);

        // Find or create Center Container
        ViewGroup centerContainer = findOrCreateCenterContainer(statusBarView);

        // Find Right Container (system icon area / battery area)
        int systemIconsId = context.getResources().getIdentifier("system_icons", "id", "com.android.systemui");
        if (systemIconsId == 0) {
            systemIconsId = context.getResources().getIdentifier("system_icon_area", "id", "com.android.systemui");
        }
        ViewGroup rightContainer = systemIconsId != 0 ? (ViewGroup) statusBarView.findViewById(systemIconsId) : null;

        ClockContainerHolder holder = new ClockContainerHolder();
        holder.clockViewRef = new WeakReference<>(clockView);
        holder.originalParentRef = new WeakReference<>(originalParent);
        holder.originalIndex = originalIndex;
        holder.statusBarRootRef = new WeakReference<>(statusBarView);
        holder.centerContainerRef = new WeakReference<>(centerContainer);
        holder.rightContainerRef = new WeakReference<>(rightContainer);

        synchronized (sHolders) {
            sHolders.add(holder);
        }

        applyPosition(holder);
    }

    private static ViewGroup findOrCreateCenterContainer(ViewGroup statusBarView) {
        Context context = statusBarView.getContext();
        int centerId = context.getResources().getIdentifier("center_clock_layout", "id", "com.android.systemui");
        if (centerId != 0) {
            ViewGroup existing = statusBarView.findViewById(centerId);
            if (existing != null) return existing;
        }

        // Create an overlay FrameLayout centered in statusBarView
        FrameLayout centerLayout = new FrameLayout(context);
        centerLayout.setId(View.generateViewId());
        FrameLayout.LayoutParams lp;

        if (statusBarView instanceof RelativeLayout) {
            RelativeLayout.LayoutParams rlp = new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
            );
            rlp.addRule(RelativeLayout.CENTER_IN_PARENT, RelativeLayout.TRUE);
            centerLayout.setLayoutParams(rlp);
        } else {
            lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER
            );
            centerLayout.setLayoutParams(lp);
        }

        try {
            statusBarView.addView(centerLayout);
            return centerLayout;
        } catch (Exception e) {
            XposedBridge.log(TAG + ": Error adding centerLayout: " + e.getMessage());
            return null;
        }
    }

    private static View findClockViewRecursively(ViewGroup root) {
        for (int i = 0; i < root.getChildCount(); i++) {
            View child = root.getChildAt(i);
            if (child instanceof TextView) {
                String className = child.getClass().getName();
                if (className.contains("Clock") || className.contains("clock")) {
                    return child;
                }
            } else if (child instanceof ViewGroup) {
                View found = findClockViewRecursively((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    public static synchronized void applyPositionToAll() {
        for (ClockContainerHolder holder : sHolders) {
            applyPosition(holder);
        }
    }

    private static void applyPosition(ClockContainerHolder holder) {
        if (holder == null) return;
        View clock = holder.clockViewRef.get();
        ViewGroup originalParent = holder.originalParentRef.get();
        ViewGroup centerContainer = holder.centerContainerRef.get();
        ViewGroup rightContainer = holder.rightContainerRef.get();

        if (clock == null || originalParent == null) return;

        if (!sModuleEnabled) {
            // Restore default
            restoreToOriginal(clock, originalParent, holder.originalIndex);
            clock.setVisibility(View.VISIBLE);
            resetMargins(clock);
            return;
        }

        switch (sClockPosition) {
            case PrefKeys.POSITION_HIDE:
                clock.setVisibility(View.GONE);
                break;

            case PrefKeys.POSITION_CENTER:
                clock.setVisibility(View.VISIBLE);
                if (centerContainer != null) {
                    moveToParent(clock, centerContainer, 0);
                }
                applyMargins(clock, sOffsetX, sOffsetY);
                break;

            case PrefKeys.POSITION_RIGHT:
                clock.setVisibility(View.VISIBLE);
                if (rightContainer != null) {
                    moveToParent(clock, rightContainer, 0);
                }
                applyMargins(clock, sOffsetX, sOffsetY);
                break;

            case PrefKeys.POSITION_LEFT:
            default:
                clock.setVisibility(View.VISIBLE);
                restoreToOriginal(clock, originalParent, holder.originalIndex);
                applyMargins(clock, sOffsetX, sOffsetY);
                break;
        }
    }

    private static void moveToParent(View view, ViewGroup targetParent, int index) {
        ViewGroup currentParent = (ViewGroup) view.getParent();
        if (currentParent == targetParent) return;

        if (currentParent != null) {
            currentParent.removeView(view);
        }
        if (index < 0 || index > targetParent.getChildCount()) {
            targetParent.addView(view);
        } else {
            targetParent.addView(view, index);
        }
    }

    private static void restoreToOriginal(View view, ViewGroup originalParent, int originalIndex) {
        ViewGroup currentParent = (ViewGroup) view.getParent();
        if (currentParent == originalParent) return;

        if (currentParent != null) {
            currentParent.removeView(view);
        }
        if (originalIndex >= 0 && originalIndex <= originalParent.getChildCount()) {
            originalParent.addView(view, originalIndex);
        } else {
            originalParent.addView(view);
        }
    }

    private static void applyMargins(View view, int offsetX, int offsetY) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            mlp.leftMargin = dpToPx(view.getContext(), offsetX);
            mlp.topMargin = dpToPx(view.getContext(), offsetY);
            view.setLayoutParams(mlp);
        }
    }

    private static void resetMargins(View view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
            mlp.leftMargin = 0;
            mlp.topMargin = 0;
            view.setLayoutParams(mlp);
        }
    }

    private static int dpToPx(Context context, int dp) {
        if (context == null) return dp;
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    public static void loadPrefs(XSharedPreferences prefs) {
        if (prefs == null) return;
        sModuleEnabled = prefs.getBoolean(PrefKeys.KEY_MODULE_ENABLED, PrefKeys.DEFAULT_MODULE_ENABLED);
        try {
            sClockPosition = Integer.parseInt(prefs.getString(PrefKeys.KEY_CLOCK_POSITION, String.valueOf(PrefKeys.DEFAULT_CLOCK_POSITION)));
        } catch (Exception e) {
            sClockPosition = prefs.getInt(PrefKeys.KEY_CLOCK_POSITION, PrefKeys.DEFAULT_CLOCK_POSITION);
        }
        sOffsetX = prefs.getInt(PrefKeys.KEY_CLOCK_OFFSET_X, PrefKeys.DEFAULT_CLOCK_OFFSET_X);
        sOffsetY = prefs.getInt(PrefKeys.KEY_CLOCK_OFFSET_Y, PrefKeys.DEFAULT_CLOCK_OFFSET_Y);
    }
}
