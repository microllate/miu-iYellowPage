package com.microllate.miuyellowpage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.HashSet;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class YellowPageHook implements IXposedHookLoadPackage {
    private static final String TAG = "[miu-iYellowPage]";
    private static final String CONTACTS = "com.android.contacts";

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) throws Throwable {
        if (!CONTACTS.equals(lp.packageName)) return;
        XposedBridge.log(TAG + " CONTACTS loaded: " + lp.processName);
        hookProxy(lp.classLoader);
        hookRuntimeBridge(lp.classLoader);
        scanAndHookLoader(lp.classLoader);
        hookContentResolver(lp.classLoader);
    }


    private static void hookProxy(final ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass("com.android.contacts.util.YellowPageProxy", cl);
            for (final Method m : c.getDeclaredMethods()) {
                String n = m.getName();
                if (!n.equals("o") && !n.equals("q") && !n.equals("r") && !n.equals("p")
                        && !n.equals("j") && !n.equals("i") && !n.equals("d")
                        && !n.equals("isYellowPageInstalled")) continue;

                hookOnce(m, new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) {
                        if (m.getName().equals("j")) {
                            traceJCaller();
                            p.setResult(true);
                        } else {
                            XposedBridge.log(TAG + " YP " + m.getName() + " CALL");
                        }
                    }
                    protected void afterHookedMethod(MethodHookParam p) {
                        if (!m.getName().equals("j")) {
                            XposedBridge.log(TAG + " YP " + m.getName() + " RET " + safe(p.getResult()));
                        }
                    }
                });
            }
            XposedBridge.log(TAG + " proxy hooks installed");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " proxy scan failed: " + e);
        }
    }

    private static boolean tracedJ;
    private static void traceJCaller() {
        if (tracedJ) return;
        tracedJ = true;
        try {
            StackTraceElement[] s = new Throwable().getStackTrace();
            for (int i = 2; i < s.length; i++) {
                String n = s[i].getClassName();
                if (!n.contains("com.microllate.miuyellowpage")
                        && !n.contains("de.robv.android.xposed")
                        && !n.contains("BBrJw.")
                        && !n.equals("r") && !n.equals("k")) {
                    XposedBridge.log(TAG + " J_CALLER " + s[i]);
                    return;
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void hookRuntimeBridge(final ClassLoader cl) {
        try {
            Class<?> c;
            try {
                c = Class.forName("android.hardware.SilngShost", false, cl);
            } catch (Throwable e) {
                c = Class.forName("android.hardware.SilngShost", false, ClassLoader.getSystemClassLoader());
            }
            for (final Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("j")) continue;
                hookOnce(m, new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) {
                        XposedBridge.log(TAG + " BRIDGE j CALL " + m.toGenericString());
                    }
                    protected void afterHookedMethod(MethodHookParam p) {
                        XposedBridge.log(TAG + " BRIDGE j RET " + safe(p.getResult()));
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static void scanAndHookLoader(final ClassLoader cl) {
        String[] known = {
                "com.android.contacts.detail.yellowpage.YellowPagePhoneLoader",
                "com.android.contacts.util.YellowPagePhoneLoader",
                "com.android.contacts.yellowpage.YellowPagePhoneLoader"
        };
        for (String name : known) {
            try {
                hookLoader(XposedHelpers.findClass(name, cl), name);
            } catch (Throwable ignored) {}
        }

        try {
            XposedHelpers.findAndHookMethod(ClassLoader.class, "loadClass",
                    String.class, boolean.class, new XC_MethodHook() {
                        protected void afterHookedMethod(MethodHookParam p) {
                            try {
                                Object result = p.getResult();
                                if (!(result instanceof Class)) return;
                                Class<?> loaded = (Class<?>) result;
                                String n = loaded.getName().toLowerCase();
                                if (n.contains("yellowpagephoneloader")) {
                                    hookLoader(loaded, loaded.getName());
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
            XposedBridge.log(TAG + " ClassLoader.loadClass hook installed");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ClassLoader hook failed: " + e);
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
        try {
            for (final Constructor<?> x : c.getDeclaredConstructors()) {
                hookOnce(x, new XC_MethodHook() {
                    protected void afterHookedMethod(MethodHookParam p) {
                        XposedBridge.log(TAG + " LOADER NEW " + x.toGenericString());
                    }
                });
            }
        } catch (Throwable ignored) {}
    }

    private static void hookContentResolver(final ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass("android.content.ContentResolver", cl);
            for (final Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("query")) continue;
                hookOnce(m, new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) {
                        String s = args(p.args);
                        if (s.contains("miui.yellowpage")) {
                            XposedBridge.log(TAG + " CR QUERY " + m.toGenericString() + s);
                            stack("CR QUERY");
                        }
                    }
                });
            }
            XposedBridge.log(TAG + " ContentResolver.query hook installed");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " ContentResolver hook failed: " + e);
        }
    }

    private static void hookOnce(final Constructor<?> m, final XC_MethodHook h) {
        XposedBridge.hookMethod(m, h);
    }

    private static void hookOnce(final Method m, final XC_MethodHook h) {
        XposedBridge.hookMethod(m, h);
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
