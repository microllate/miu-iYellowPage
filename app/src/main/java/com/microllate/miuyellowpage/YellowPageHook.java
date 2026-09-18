package com.microllate.miuyellowpage;

import android.app.Application;
import android.util.Log;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class YellowPageHook implements IXposedHookLoadPackage {
    private static final String TAG = "[miu-iYellowPage]";
    private static final String YELLOW_PAGE = "com.miui.yellowpage";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!YELLOW_PAGE.equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + " INJECTED package=" + lpparam.packageName
                + " process=" + lpparam.processName);
        Log.i(TAG, "INJECTED package=" + lpparam.packageName
                + " process=" + lpparam.processName);

        try {
            XposedHelpers.findAndHookMethod(
                Application.class,
                "attach",
                android.content.Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Log.i(TAG, "Application.attach reached");
                        XposedBridge.log(TAG + " Application.attach reached");
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                "com.miui.yellowpage.providers.yellowpage.YellowPageProvider",
                lpparam.classLoader,
                "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + " YellowPageProvider.onCreate");
                        Log.i(TAG, "YellowPageProvider.onCreate");
                    }
                }
            );

            XposedHelpers.findAndHookMethod(
                "com.miui.yellowpage.providers.yellowpage.YellowPageProvider",
                lpparam.classLoader,
                "k",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + " YellowPageProvider.k() invoked");
                        Log.i(TAG, "YellowPageProvider.k() invoked");
                    }
                }
            );

            XposedBridge.log(TAG + " ALL HOOKS INITIALIZED");
            Log.i(TAG, "ALL HOOKS INITIALIZED");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " HOOK ERROR: " + Log.getStackTraceString(e));
            Log.e(TAG, "HOOK ERROR", e);
        }
    }
}
