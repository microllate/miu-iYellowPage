package com.microllate.miuyellowpage;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

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
        hookYellowPageUtils(lp.classLoader);
        hookRuntimeBridge(lp.classLoader);
        scanAndHookLoader(lp.classLoader);
        hookLoaderCallers(lp.classLoader);
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
                        XposedBridge.log(TAG + " YP " + m.getName() + " CALL" + args(p.args));
                        if (m.getName().equals("j")) {
                            traceJCaller();
                        }
                    }

                    protected void afterHookedMethod(MethodHookParam p) {
                        Object result = p.getResult();
                        if ((m.getName().equals("i") || m.getName().equals("d"))
                                && result instanceof Boolean && !((Boolean) result)) {
                            XposedBridge.log(TAG + " YP " + m.getName()
                                    + " RET false -> FORCE true (EEA compatibility gate)");
                            p.setResult(true);
                            result = true;
                        }
                        // Keep j return visible, but do not spam every successful call.
                        if (m.getName().equals("j")) {
                            XposedBridge.log(TAG + " YP j RET " + safe(result));
                        } else if (m.getName().equals("i") || m.getName().equals("d")) {
                            XposedBridge.log(TAG + " YP " + m.getName() + " RET " + safe(result));
                        }
                    }
                });
            }
            XposedBridge.log(TAG + " proxy hooks installed");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " proxy scan failed: " + e);
        }
    }

    private static final AtomicInteger jTraceCount = new AtomicInteger();

    private static void traceJCaller() {
        int count = jTraceCount.incrementAndGet();
        if (count > 3) return;
        try {
            StackTraceElement[] s = new Throwable().getStackTrace();
            XposedBridge.log(TAG + " J_CALLER #" + count);
            for (int i = 2; i < Math.min(s.length, 12); i++) {
                String n = s[i].getClassName();
                if (!n.contains("com.microllate.miuyellowpage")
                        && !n.contains("de.robv.android.xposed")) {
                    XposedBridge.log(TAG + "   at " + s[i]);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static void hookYellowPageUtils(final ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass("miui.yellowpage.YellowPageUtils", cl);
            int count = 0;

            for (final Method m : c.getDeclaredMethods()) {
                String n = m.getName();
                if (!n.equals("isYellowPageAvailable")
                        && !n.equals("isYellowPageEnable")
                        && !n.equals("isCloudAntispamEnable")
                        && !n.equals("getLocalYellowPagePhones")
                        && !n.equals("getPhoneInfo")
                        && !n.equals("getAntispamNumberCategory")
                        && !n.equals("queryPhoneInfo")
                        && !n.equals("isContentProviderInstalled")
                        && !n.equals("getNormalizedNumber")) {
                    continue;
                }

                hookOnce(m, new XC_MethodHook() {
                    protected void beforeHookedMethod(MethodHookParam p) {
                        String n = m.getName();
                        if (n.equals("getLocalYellowPagePhones")
                                || n.equals("getPhoneInfo")
                                || n.equals("getAntispamNumberCategory")
                                || n.equals("queryPhoneInfo")) {
                            XposedBridge.log(TAG + " UTILS " + n + " CALL" + args(p.args));
                        } else {
                            XposedBridge.log(TAG + " UTILS " + n + " CALL" + args(p.args));
                        }
                    }

                    protected void afterHookedMethod(MethodHookParam p) {
                        String n = m.getName();
                        XposedBridge.log(TAG + " UTILS " + n + " RET " + safe(p.getResult()));
                        if (n.equals("isYellowPageAvailable")
                                || n.equals("isCloudAntispamEnable")
                                || n.equals("isYellowPageEnable")) {
                            stack("UTILS " + n);
                        }
                    }
                });
                count++;
            }

            XposedBridge.log(TAG + " YellowPageUtils hooks installed methods=" + count);
        } catch (Throwable e) {
            XposedBridge.log(TAG + " YellowPageUtils hook failed: " + e);
        }
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

    private static void hookLoaderCallers(final ClassLoader cl) {
        String[][] targets = {
                {"com.android.contacts.list.TwelveKeyDialerFragment", "F4"},
                {"com.android.contacts.dialer.serviceimpl.ContactsServiceImpl", "m"},
                {"com.android.contacts.dialer.utils.ContactServiceUtil", "y"},
                {"com.android.contacts.dialer.list.DialerItemVM", "L"}
        };

        for (String[] target : targets) {
            try {
                Class<?> c = XposedHelpers.findClass(target[0], cl);
                int count = 0;
                for (final Method m : c.getDeclaredMethods()) {
                    if (!m.getName().equals(target[1])) continue;
                    hookOnce(m, new XC_MethodHook() {
                        protected void beforeHookedMethod(MethodHookParam p) {
                            XposedBridge.log(TAG + " CALLER CALL " + m.toGenericString()
                                    + args(p.args));
                            stack("CALLER " + m.getName());
                        }

                        protected void afterHookedMethod(MethodHookParam p) {
                            XposedBridge.log(TAG + " CALLER RET " + m.toGenericString()
                                    + " -> " + safe(p.getResult()));
                        }
                    });
                    count++;
                }
                XposedBridge.log(TAG + " CALLER HOOK " + target[0]
                        + "." + target[1] + " methods=" + count);
            } catch (Throwable e) {
                XposedBridge.log(TAG + " CALLER FAIL " + target[0]
                        + "." + target[1] + ": " + e);
            }
        }
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

    private static void stack(String label) {
        try {
            XposedBridge.log(TAG + " STACK " + label);
            StackTraceElement[] s = new Throwable().getStackTrace();
            for (int i = 2; i < Math.min(s.length, 14); i++) {
                XposedBridge.log(TAG + "   at " + s[i]);
            }
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
