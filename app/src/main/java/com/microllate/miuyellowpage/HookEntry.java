package com.microllate.miuyellowpage;

import android.net.Uri;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "[miu-iYellowPage]";
    private static final String TARGET_PACKAGE = "com.miui.yellowpage";
    private static final String PROVIDER_CLASS =
            "com.miui.yellowpage.providers.yellowpage.YellowPageProvider";

    private static final AtomicBoolean TRIGGERED = new AtomicBoolean(false);

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + " loaded: " + lpparam.packageName);

        final Class<?> providerClass;
        try {
            providerClass = XposedHelpers.findClass(PROVIDER_CLASS, lpparam.classLoader);
        } catch (Throwable t) {
            XposedBridge.log(TAG + " provider class not found: " + t);
            return;
        }

        hookAntispamScheduler(providerClass);
        hookPhoneLookupQuery(providerClass);
    }

    private static void hookAntispamScheduler(final Class<?> providerClass) {
        try {
            XposedHelpers.findAndHookMethod(
                    providerClass,
                    "k",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + " YellowPageProvider.k() invoked");
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + " YellowPageProvider.k() returned");
                        }
                    });
            XposedBridge.log(TAG + " hooked k()");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook k() failed: " + t);
        }
    }

    private static void hookPhoneLookupQuery(final Class<?> providerClass) {
        try {
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
                        protected void afterHookedMethod(MethodHookParam param) {
                            Uri uri = (Uri) param.args[0];
                            if (uri == null) {
                                return;
                            }

                            final String path = uri.getPath();
                            if (path == null || !path.startsWith("/phone_lookup")) {
                                return;
                            }

                            if (!TRIGGERED.compareAndSet(false, true)) {
                                return;
                            }

                            XposedBridge.log(TAG +
                                    " phone_lookup observed: " + uri +
                                    "; invoking YellowPageProvider.k() once");

                            try {
                                XposedHelpers.callMethod(param.thisObject, "k");
                                XposedBridge.log(TAG + " k() invoked from phone_lookup");
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + " invoke k() failed: " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + " hooked query()");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " hook query() failed: " + t);
        }
    }
}
