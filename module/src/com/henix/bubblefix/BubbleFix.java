package com.henix.bubblefix;

import android.content.res.Resources;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Fixes SystemUI chat-bubble avatar scaling on HyperOS (peridot, Android 17).
 * Root cause: BaseIconFactory wraps non-adaptive drawables (photos) in a white
 * AdaptiveIconDrawable background scaled down (wrapNonAdaptiveIcon defaults true),
 * so avatars render as a small picture inside a white circle.
 * Fix: force IconOptions.wrapNonAdaptiveIcon = false for createBadgedIconBitmap
 * calls inside com.android.systemui -> photos draw full-bleed in the bubble.
 */
public class BubbleFix implements IXposedHookLoadPackage {
    private static final String PKG = "com.android.systemui";
    private static int badgeId = -1;

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) return;

        // 1) Main fix: stop wrapping non-adaptive icons in the white background.
        try {
            Class<?> bif = XposedHelpers.findClass(
                    "com.android.launcher3.icons.BaseIconFactory", lpparam.classLoader);
            XposedBridge.hookAllMethods(bif, "createBadgedIconBitmap", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    for (Object arg : param.args) {
                        if (arg != null && arg.getClass().getName().equals(
                                "com.android.launcher3.icons.BaseIconFactory$IconOptions")) {
                            XposedHelpers.setBooleanField(arg, "wrapNonAdaptiveIcon", false);
                        }
                    }
                }
            });
            XposedBridge.log("BubbleFix: createBadgedIconBitmap hook installed");
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        // 2) Keep badge dimen at stock (24dp). No dimen overrides needed anymore.
    }
}
