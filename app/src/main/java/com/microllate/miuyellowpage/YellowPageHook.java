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

    @Override public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!YELLOW_PAGE.equals(lpparam.packageName)) return;
        XposedBridge.log(TAG + " loaded: " + lpparam.processName);

        XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                hookYellowPageProvider(lpparam.classLoader);
            }
        });
    }

    private static void hookYellowPageProvider(final ClassLoader cl) {
        try {
            Class<?> provider = XposedHelpers.findClass(
                "com.miui.yellowpage.providers.yellowpage.YellowPageProvider", cl);

            XposedHelpers.findAndHookMethod(provider, "onCreate", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + " YellowPageProvider.onCreate");
                }
            });

            XposedHelpers.findAndHookMethod(provider, "k", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    XposedBridge.log(TAG + " YellowPageProvider.k() invoked");
                }
            });

            XposedBridge.log(TAG + " hook initialized");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " hook failed: " + e);
        }
    }
}
