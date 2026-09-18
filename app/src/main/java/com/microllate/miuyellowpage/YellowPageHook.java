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

            final Set<String> targets = new HashSet<>();
            targets.add("isYellowPageInstalled");
            targets.add("k");
            targets.add("o");
            targets.add("q");
            targets.add("r");
            targets.add("p");

            int count = 0;
            for (final Method method : proxy.getDeclaredMethods()) {
                if (!targets.contains(method.getName())) continue;

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        XposedBridge.log(TAG + " CALL " + method.toGenericString()
                                + formatArgs(param.args));
                        if (!"k".equals(method.getName())) {
                            logStack("CALL " + method.getName());
                        }
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

            // Also hook YellowPagePhoneLoader if present, without assuming its package/class loader.
            hookLoaderClasses(cl);

            XposedBridge.log(TAG + " YellowPageProxy hooks initialized, count=" + count);
        } catch (Throwable e) {
            XposedBridge.log(TAG + " YellowPageProxy hook failed: " + e);
        }
    }

    private static void hookLoaderClasses(final ClassLoader cl) {
        final String[] names = {
                "com.android.contacts.util.YellowPagePhoneLoader",
                "com.android.contacts.yellowpage.YellowPagePhoneLoader"
        };

        for (String name : names) {
            try {
                Class<?> c = XposedHelpers.findClass(name, cl);
                int count = 0;
                for (final Method m : c.getDeclaredMethods()) {
                    String n = m.getName().toLowerCase();
                    if (n.contains("load") || n.contains("phone") || n.contains("yellow")) {
                        XposedBridge.hookMethod(m, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam p) {
                                XposedBridge.log(TAG + " LOADER CALL " + m.toGenericString()
                                        + formatArgs(p.args));
                                logStack("LOADER " + m.getName());
                            }
                            @Override
                            protected void afterHookedMethod(MethodHookParam p) {
                                XposedBridge.log(TAG + " LOADER RET  " + m.toGenericString()
                                        + " -> " + safeToString(p.getResult()));
                            }
                        });
                        count++;
                    }
                }
                XposedBridge.log(TAG + " LOADER HOOKED " + name + " count=" + count);
            } catch (Throwable ignored) {
                // Class may not exist in this Contacts build.
            }
        }
    }

    private static void logStack(String label) {
        try {
            StackTraceElement[] stack = new Throwable().getStackTrace();
            StringBuilder sb = new StringBuilder(TAG + " STACK " + label + ":");
            int limit = Math.min(stack.length, 14);
            for (int i = 2; i < limit; i++) {
                sb.append("\n  at ").append(stack[i].toString());
            }
            XposedBridge.log(sb.toString());
        } catch (Throwable ignored) {
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
