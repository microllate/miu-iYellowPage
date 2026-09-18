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
            ClassLoader cl = lpparam.classLoader;

            // Resolve the Provider class through the app ClassLoader first.
            // This avoids the previous XposedHelpers string lookup failure.
            Class<?> providerClass = Class.forName(
                    "miui.yellowpage.providers.yellowpage.YellowPageProvider",
                    false,
                    cl
            );

            XposedHelpers.findAndHookMethod(
                    providerClass,
                    "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "YellowPageProvider.onCreate()");
                        }
                    }
            );

            XposedHelpers.findAndHookMethod(
                    providerClass,
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

            XposedBridge.log(TAG + "Provider class resolved: " + providerClass.getName());

            XposedHelpers.findAndHookMethod(
                    "miui.yellowpage.YellowPageUtils",
                    cl,
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
