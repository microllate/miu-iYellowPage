package com.microllate.miuyellowpage;

import java.lang.reflect.Constructor;
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
        hookFairnpCroms(lp.classLoader);
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
                        XposedBridge.log(TAG + " YP CALL " + m.toGenericString() + args(p.args));
                        if (m.getName().equals("j")) {
                            stack("YP j");
                            p.setResult(true);
                            XposedBridge.log(TAG + " FORCE j -> true");
                            return;
                        }
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

    private static void hookFairnpCroms(final ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass("android.provider.FairnpCroms", cl);
            int count = 0;
            for (final Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("j") || m.isSynthetic()) continue;
                hookOnce(m, new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) {
                        XposedBridge.log(TAG + " FAIRNP CALL " + m.toGenericString() + args(p.args));
                        stack("FAIRNP j");
                    }
                    protected void afterHookedMethod(MethodHookParam p) {
                        XposedBridge.log(TAG + " FAIRNP RET " + m.getName() + " -> " + safe(p.getResult()));
                    }
                });
                count++;
            }
            XposedBridge.log(TAG + " FAIRNP FOUND methods=" + count);
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals("j")) {
                    XposedBridge.log(TAG + " FAIRNP METHOD " + m.toGenericString());
                }
            }
        } catch (Throwable e) {
            XposedBridge.log(TAG + " FairnpCroms hook failed: " + e);
        }
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
        for (Method x : c.getDeclaredMethods()) {
            try { XposedBridge.log(TAG + " LOADER METHOD " + x.toGenericString()); } catch (Throwable ignored) {}
        }
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
        String key = m.toGenericString();
        if (!HOOKED.add(key)) return;
        XposedBridge.hookMethod(m, h);
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
            for (int i = 2; i < Math.min(s.length, 14); i++) {
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
