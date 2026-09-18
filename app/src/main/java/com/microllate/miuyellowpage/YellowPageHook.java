package com.microllate.miuyellowpage;

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
    private static final Set<String> HOOKED = new HashSet<>();

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!CONTACTS.equals(lp.packageName)) return;
        XposedBridge.log(TAG + " CONTACTS loaded: " + lp.processName);
        hookProxy(lp.classLoader);
        scanAndHookLoader(lp.classLoader);
    }

    private static void hookProxy(final ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass("com.android.contacts.util.YellowPageProxy", cl);
            for (final Method m : c.getDeclaredMethods()) {
                String n = m.getName();
                if (!n.equals("o") && !n.equals("q") && !n.equals("r") && !n.equals("p")
                        && !n.equals("isYellowPageInstalled")) continue;
                hookOnce(m, new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) {
                        XposedBridge.log(TAG + " YP CALL " + m.toGenericString() + args(p.args));
                        stack("YP " + m.getName());
                    }
                    protected void afterHookedMethod(MethodHookParam p) {
                        XposedBridge.log(TAG + " YP RET " + m.getName() + " -> " + safe(p.getResult()));
                    }
                });
            }
        } catch (Throwable e) {
            XposedBridge.log(TAG + " proxy scan failed: " + e);
        }
    }

    private static void scanAndHookLoader(final ClassLoader cl) {
        // First try the known class names.
        String[] known = {
                "com.android.contacts.util.YellowPagePhoneLoader",
                "com.android.contacts.yellowpage.YellowPagePhoneLoader"
        };
        for (String name : known) {
            try {
                hookLoader(XposedHelpers.findClass(name, cl), name);
            } catch (Throwable ignored) {}
        }

        // Then enumerate already-loaded classes. This catches obfuscated/nested package names.
        try {
            Class<?>[] loaded = XposedBridge.getAllLoadedClasses();
            for (Class<?> c : loaded) {
                String n = c.getName().toLowerCase();
                if (n.contains("yellowpagephoneloader") || n.endsWith(".yellowpagephoneloader")) {
                    hookLoader(c, c.getName());
                }
            }
        } catch (Throwable e) {
            XposedBridge.log(TAG + " class scan failed: " + e);
        }
    }

    private static void hookLoader(Class<?> c, String name) {
        int count = 0;
        for (final Method m : c.getDeclaredMethods()) {
            if (m.isSynthetic()) continue;
            hookOnce(m, new XC_MethodHook() {
                protected void beforeHookedMethod(MethodHookParam p) {
                    XposedBridge.log(TAG + " LOADER CALL " + m.toGenericString() + args(p.args));
                    stack("LOADER " + m.getName());
                }
                protected void afterHookedMethod(MethodHookParam p) {
                    XposedBridge.log(TAG + " LOADER RET " + m.getName() + " -> " + safe(p.getResult()));
                }
            });
            count++;
        }
        XposedBridge.log(TAG + " LOADER FOUND " + name + " methods=" + count);
    }

    private static void hookOnce(final Method m, final XC_MethodHook h) {
        String key = m.toGenericString();
        if (!HOOKED.add(key)) return;
        XposedBridge.hookMethod(m, h);
    }

    private static void stack(String label) {
        try {
            StackTraceElement[] s = new Throwable().getStackTrace();
            StringBuilder b = new StringBuilder(TAG + " STACK " + label);
            for (int i = 2; i < Math.min(s.length, 12); i++) {
                b.append("\n  at ").append(s[i]);
            }
            XposedBridge.log(b.toString());
        } catch (Throwable ignored) {}
    }

    private static String args(Object[] a) {
        if (a == null || a.length == 0) return "()";
        StringBuilder b = new StringBuilder("(");
        for (int i = 0; i < a.length; i++) {
            if (i > 0) b.append(", ");
            try { b.append(String.valueOf(a[i])); } catch (Throwable e) { b.append("<err>"); }
        }
        return b.append(")").toString();
    }

    private static String safe(Object o) {
        try { return String.valueOf(o); } catch (Throwable e) { return "<err>"; }
    }
}
