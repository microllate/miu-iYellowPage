package com.microllate.miuyellowpage;

import android.content.Context;
import android.net.Uri;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "[miu-iYellowPage] ";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.yellowpage".equals(lpparam.packageName)) return;

        try {
            XposedHelpers.findAndHookMethod(
                    "miui.yellowpage.providers.yellowpage.YellowPageProvider",
                    lpparam.classLoader,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "YellowPageProvider.onCreate()");
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "miui.yellowpage.providers.yellowpage.YellowPageProvider",
                    lpparam.classLoader,
                    "query",
                    Uri.class,
                    String[].class,
                    String.class,
                    String[].class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "YellowPageProvider.query() uri=" + param.args[0]
                                    + " selection=" + param.args[2]);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "YellowPageProvider.query() returned="
                                    + (param.getResult() != null));
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    "miui.yellowpage.YellowPageUtils",
                    lpparam.classLoader,
                    "isYellowPageAvailable",
                    Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(true);
                            XposedBridge.log(TAG + "isYellowPageAvailable() -> true");
                        }
                    }
            );

            XposedBridge.log(TAG + "HookEntry initialized; Provider hooks installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "HookEntry failed: " + t);
        }
    }
}
