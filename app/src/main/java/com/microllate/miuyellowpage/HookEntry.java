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

            // EEA feature manager. JADX shows:
            // p007c0.b.e(Context, p007c0.a)
            // YELLOWPAGE_PROVIDER controls YellowPageDatabaseHelper.L()
            // and YELLOWPAGE_SYNC controls the cloud sync path.
            Class<?> featureEnum = Class.forName("p007c0.a", false, cl);
            XposedHelpers.findAndHookMethod(
                    "p007c0.b",
                    cl,
                    "e",
                    Context.class,
                    featureEnum,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Object feature = param.args[1];
                            if (feature == null) return;

                            String name = String.valueOf(feature);
                            if ("YELLOWPAGE_PROVIDER".equals(name)
                                    || "YELLOWPAGE_SYNC".equals(name)
                                    || "YELLOW_PAGE".equals(name)) {
                                boolean oldResult = Boolean.TRUE.equals(param.getResult());
                                param.setResult(true);
                                XposedBridge.log(TAG + "feature " + name
                                        + ": " + oldResult + " -> true");
                            }
                        }
                    }
            );

            XposedBridge.log(TAG + "feature gate hook installed");

            // Keep the public availability gate enabled as well.
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

            // Diagnostic Provider hooks.
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
                            XposedBridge.log(TAG + "YellowPageProvider.query() uri="
                                    + param.args[0] + " selection=" + param.args[2]);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "YellowPageProvider.query() returned="
                                    + (param.getResult() != null));
                        }
                    }
            );

            XposedBridge.log(TAG + "Provider class resolved: " + providerClass.getName());
            XposedBridge.log(TAG + "HookEntry initialized");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "HookEntry failed: " + t);
        }
    }
}
