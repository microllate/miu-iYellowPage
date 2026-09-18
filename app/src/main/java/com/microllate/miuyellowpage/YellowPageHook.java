package com.microllate.miuyellowpage;

import android.app.Application;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class YellowPageHook implements IXposedHookLoadPackage {
    private static final String TAG = "[miu-iYellowPage]";
    private static final String YELLOW_PAGE = "com.miui.yellowpage";
    private static final int CN_PRESET_RESOURCE_ID = 0x7f0f000a;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!YELLOW_PAGE.equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + " loaded: " + lpparam.processName);

        XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                hookYellowPage(lpparam.classLoader);
            }
        });
    }

    private static void hookYellowPage(final ClassLoader cl) {
        hookPresetResource(cl);
        hookYellowPageProvider(cl);
    }

    private static void hookPresetResource(final ClassLoader cl) {
        try {
            Class<?> presetProvider = XposedHelpers.findClass("r0.c", cl);

            XposedHelpers.findAndHookMethod(presetProvider, "h", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    Object result = param.getResult();
                    if (result instanceof Integer && ((Integer) result) == -1) {
                        param.setResult(CN_PRESET_RESOURCE_ID);
                        XposedBridge.log(TAG + " r0.c.h(): international preset disabled -> CN resource 0x7f0f000a");
                    }
                }
            });

            XposedBridge.log(TAG + " r0.c.h() hook initialized");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " r0.c.h() hook failed: " + e);
        }
    }

    private static void hookYellowPageProvider(final ClassLoader cl) {
        try {
            Class<?> provider = XposedHelpers.findClass(
                "com.miui.yellowpage.providers.yellowpage.YellowPageProvider", cl);

            XposedHelpers.findAndHookMethod(provider, "onCreate", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + " YellowPageProvider.onCreate");
                }
            });

            XposedHelpers.findAndHookMethod(provider, "k", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + " YellowPageProvider.k() invoked");
                }
            });

            XposedBridge.log(TAG + " YellowPageProvider hooks initialized");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " provider hook failed: " + e);
        }
    }
}
