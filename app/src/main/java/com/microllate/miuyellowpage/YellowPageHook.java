package com.microllate.miuyellowpage;

import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class YellowPageHook implements IXposedHookLoadPackage {
    private static final String TAG = "[miu-iYellowPage]";
    private static final String CONTACTS = "com.android.contacts";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!CONTACTS.equals(lpparam.packageName)) return;

        XposedBridge.log(TAG + " CONTACTS loaded: " + lpparam.processName);
        hookContactsYellowPage(lpparam.classLoader);
    }

    private static void hookContactsYellowPage(final ClassLoader cl) {
        try {
            Class<?> proxy = XposedHelpers.findClass(
                    "com.android.contacts.util.YellowPageProxy", cl);

            hook(proxy, "isYellowPageInstalled");
            hook(proxy, "k", Context.class);
            hook(proxy, "o", Context.class, String.class);
            hook(proxy, "q", Context.class, String.class);
            hook(proxy, "r", Context.class, String.class, boolean.class);

            Class<?> customCategory = XposedHelpers.findClass(
                    "miui.yellowpage.AntispamCustomCategory", cl);
            hook(proxy, "p", Context.class, String.class, customCategory);

            XposedBridge.log(TAG + " YellowPageProxy hooks initialized");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " YellowPageProxy hook failed: " + e);
        }
    }

    private static void hook(final Class<?> clazz, final String method, Object... parameterTypes) {
        XposedHelpers.findAndHookMethod(clazz, method, parameterTypes, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                XposedBridge.log(TAG + " CALL " + clazz.getName() + "." + method
                        + formatArgs(param.args));
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                XposedBridge.log(TAG + " RET  " + clazz.getName() + "." + method
                        + " -> " + safeToString(param.getResult()));
            }
        });
    }

    private static String formatArgs(Object[] args) {
        if (args == null || args.length == 0) return "()";
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            Object arg = args[i];
            if (arg instanceof Context) {
                sb.append("Context[").append(((Context) arg).getPackageName()).append("]");
            } else {
                sb.append(safeToString(arg));
            }
        }
        return sb.append(")").toString();
    }

    private static String safeToString(Object value) {
        if (value == null) return "null";
        try {
            return String.valueOf(value);
        } catch (Throwable e) {
            return "<toString failed:" + e.getClass().getSimpleName() + ">";
        }
    }
}
