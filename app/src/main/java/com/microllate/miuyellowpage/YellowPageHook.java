package com.microllate.miuyellowpage;

import android.content.Context;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

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
            final Class<?> proxy = XposedHelpers.findClass(
                    "com.android.contacts.util.YellowPageProxy", cl);

            // Hook by method name only. This avoids guessing parameter ClassLoader/types.
            final Set<String> targets = new HashSet<>();
            targets.add("isYellowPageInstalled");
            targets.add("k");
            targets.add("o");
            targets.add("q");
            targets.add("r");
            targets.add("p");

            int count = 0;
            for (Method method : proxy.getDeclaredMethods()) {
                if (!targets.contains(method.getName())) continue;

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + " CALL " + method.toGenericString()
                                + formatArgs(param.args));
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + " RET  " + method.toGenericString()
                                + " -> " + safeToString(param.getResult()));
                    }
                });
                count++;
                XposedBridge.log(TAG + " HOOKED " + method.toGenericString());
            }

            XposedBridge.log(TAG + " YellowPageProxy hooks initialized, count=" + count);
        } catch (Throwable e) {
            XposedBridge.log(TAG + " YellowPageProxy hook failed: " + e);
        }
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
