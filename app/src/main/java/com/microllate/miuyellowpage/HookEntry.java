package com.microllate.miuyellowpage;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.app.Application;
import dalvik.system.DexFile;

import java.lang.reflect.Method;
import java.util.Enumeration;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "[miu-iYellowPage] ";

    private static void diagnoseYellowPageJoin(SQLiteDatabase db, String number) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT yid, content FROM yellow_page WHERE yid=307", null);
            XposedBridge.log(TAG + "diagnose: yellow_page yid=307 count=" + c.getCount());
            while (c.moveToNext()) {
                XposedBridge.log(TAG + "diagnose yellow_page: yid=" + c.getString(0)
                        + " contentLength=" + (c.isNull(1) ? "null" : c.getString(1).length()));
            }
            c.close(); c = null;

            c = db.rawQuery("SELECT pl.number, pl.normalized_number, pl.yid, yp.yid, yp.content "
                    + "FROM phone_lookup pl LEFT JOIN yellow_page yp ON pl.yid=yp.yid "
                    + "WHERE pl.number=? OR pl.normalized_number=?", new String[]{number, number});
            XposedBridge.log(TAG + "diagnose: phone_lookup LEFT JOIN yellow_page for "
                    + number + " count=" + c.getCount());
            while (c.moveToNext()) {
                XposedBridge.log(TAG + "diagnose JOIN row: number=" + c.getString(0)
                        + " normalized=" + c.getString(1)
                        + " pl.yid=" + c.getString(2)
                        + " yp.yid=" + c.getString(3)
                        + " yp.contentLength=" + (c.isNull(4) ? "null" : c.getString(4).length()));
            }
            c.close(); c = null;

            c = db.rawQuery("SELECT COUNT(*) FROM phone_lookup pl INNER JOIN yellow_page yp ON pl.yid=yp.yid "
                    + "WHERE pl.normalized_number=?", new String[]{number});
            if (c.moveToFirst()) {
                XposedBridge.log(TAG + "diagnose: INNER JOIN normalized_number=" + number
                        + " count=" + c.getInt(0));
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "diagnose JOIN failed: " + t);
        } finally {
            if (c != null) try { c.close(); } catch (Throwable ignored) {}
        }
    }

    private static void dumpTableCounts(SQLiteDatabase db) {
        String[] tables = {"provider", "yellow_page", "phone_lookup", "t9_lookup"};
        for (String table : tables) {
            Cursor c = null;
            try {
                c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
                if (c.moveToFirst()) {
                    XposedBridge.log(TAG + table + " COUNT=" + c.getInt(0));
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + table + " COUNT FAILED: " + t);
            } finally {
                if (c != null) try { c.close(); } catch (Throwable ignored) {}
            }
        }
    }

    private static boolean hasNoArgMethodReturning(Class<?> cls, String name, Class<?> returnType) {
        try {
            Method m = cls.getDeclaredMethod(name);
            return m.getReturnType() == returnType;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /*
     * JADX's p052r0.c is R8-obfuscated at runtime. Find the actual class by
     * its distinctive method shape instead of relying on the JADX-generated name:
     *   n() -> singleton of same class
     *   h() -> int resource id
     *   d()/f()/i() -> String
     */
    private static void findAndHookPresetProvider(final ClassLoader cl, final Context context) {
        try {
            String apkPath = context.getApplicationInfo().sourceDir;
            DexFile dex = new DexFile(apkPath);
            Enumeration<String> entries = dex.entries();
            int scanned = 0;
            int candidates = 0;

            while (entries.hasMoreElements()) {
                String name = entries.nextElement();
                scanned++;

                // Only inspect classes with the distinctive no-arg method shape.
                if (name.indexOf('.') < 0) continue;

                try {
                    Class<?> cls = Class.forName(name, false, cl);

                    if (!hasNoArgMethodReturning(cls, "h", Integer.TYPE)
                            || !hasNoArgMethodReturning(cls, "d", String.class)
                            || !hasNoArgMethodReturning(cls, "f", String.class)
                            || !hasNoArgMethodReturning(cls, "i", String.class)) {
                        continue;
                    }

                    Method n;
                    try {
                        n = cls.getDeclaredMethod("n");
                    } catch (Throwable ignored) {
                        continue;
                    }

                    if (n.getReturnType() != cls
                            || !java.lang.reflect.Modifier.isStatic(n.getModifiers())) {
                        continue;
                    }

                    candidates++;
                    XposedBridge.log(TAG + "preset provider candidate: " + cls.getName());

                    final Class<?> target = cls;
                    XposedHelpers.findAndHookMethod(
                            target, "h", new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) {
                                    try {
                                        XposedBridge.log(TAG + "preset h() invoked on " + target.getName());
                                        int resId = context.getResources().getIdentifier(
                                                "yellow_pages_cn",
                                                "raw",
                                                "com.miui.yellowpage");
                                        if (resId != 0) {
                                            int old = (Integer) param.getResult();
                                            param.setResult(resId);
                                            XposedBridge.log(TAG + target.getName()
                                                    + ".h() " + old + " -> " + resId
                                                    + " (force yellow_pages_cn)");
                                        } else {
                                            XposedBridge.log(TAG
                                                    + "yellow_pages_cn resource NOT FOUND");
                                        }
                                    } catch (Throwable t) {
                                        XposedBridge.log(TAG + "preset h() failed: " + t);
                                    }
                                }
                            });
                    try {
                        XposedHelpers.findAndHookMethod(
                                target.getSuperclass(), "l", Context.class, new XC_MethodHook() {
                                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                                        XposedBridge.log(TAG + target.getName() + ".l() ENTER");
                                    }
                                    @Override protected void afterHookedMethod(MethodHookParam param) {
                                        Object old = param.getResult();
                                        param.setResult(true);
                                        XposedBridge.log(TAG + target.getName() + ".l() EXIT result="
                                                + old + " -> FORCED true");
                                    }
                                });
                        XposedBridge.log(TAG + "preset superclass l(Context) hook installed: " + target.getSuperclass().getName());
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "preset superclass l(Context) hook failed: " + t);
                    }

                    try {
                        XposedHelpers.findAndHookMethod(
                                target.getSuperclass(), "c", Context.class, new XC_MethodHook() {
                                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                                        XposedBridge.log(TAG + target.getName() + ".c(Context) ENTER");
                                    }
                                    @Override protected void afterHookedMethod(MethodHookParam param) {
                                        XposedBridge.log(TAG + target.getName() + ".c(Context) EXIT result="
                                                + param.getResult());
                                    }
                                });
                        XposedBridge.log(TAG + "preset superclass c(Context) hook installed: " + target.getSuperclass().getName());
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "preset superclass c(Context) hook failed: " + t);
                    }

                    XposedBridge.log(TAG + "preset h() hook installed: " + target.getName());

                    // One matching class is expected; stop after the first exact match.
                    break;
                } catch (Throwable ignored) {
                }
            }

            dex.close();

            XposedBridge.log(TAG + "preset provider scan finished: scanned="
                    + scanned + " candidates=" + candidates);

            if (candidates == 0) {
                XposedBridge.log(TAG + "preset provider runtime class NOT FOUND");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "preset provider scan failed: " + t);
        }
    }


    /*
     * Contacts-side tracing. The Yellow Page provider is already known to have
     * usable data, so this side only answers one question:
     * does com.android.contacts actually create the loader / call the proxy?
     */
    private static void installContactsDiagnostics(final XC_LoadPackage.LoadPackageParam lpparam) {
        final ClassLoader cl = lpparam.classLoader;
        try {
            hookContactsClass(cl, "com.android.contacts.activities.UnknownContactActivity");
            hookContactsClass(cl, "com.android.contacts.fragment.UnknownContactAtyFragment");
            hookContactsClass(cl, "com.android.contacts.detail.yellowpage.YellowPagePhoneLoader");
            XposedBridge.log(TAG + "CONTACTS targeted diagnostics installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "CONTACTS targeted diagnostics failed: " + t);
        }
    }

    private static void hookContactsClass(final ClassLoader cl, final String className) {
        try {
            Class<?> cls = Class.forName(className, false, cl);
            XposedBridge.log(TAG + "CONTACTS target=" + className);

            for (Method m : cls.getDeclaredMethods()) {
                String mn = m.getName();

                if (("Y0".equals(mn) || "Y2".equals(mn)) && m.getParameterTypes().length == 1
                        && m.getParameterTypes()[0] == String.class) {
                    final String methodName = mn;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "CONTACTS " + className + "." + methodName
                                    + " number=" + String.valueOf(param.args[0]));
                        }
                    });
                }

                if ("loadInBackground".equals(mn)) {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "CONTACTS YellowPagePhoneLoader.loadInBackground ENTER");
                        }
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) {
                                XposedBridge.log(TAG + "CONTACTS YellowPagePhoneLoader.loadInBackground THREW="
                                        + param.getThrowable());
                            } else {
                                XposedBridge.log(TAG + "CONTACTS YellowPagePhoneLoader.loadInBackground EXIT result="
                                        + (param.getResult() == null ? "null" : param.getResult().getClass().getName()));
                            }
                        }
                    });
                }
            }

            if (className.endsWith("YellowPagePhoneLoader")) {
                for (java.lang.reflect.Constructor<?> ctor : cls.getDeclaredConstructors()) {
                    XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "CONTACTS YellowPagePhoneLoader NEW args="
                                    + java.util.Arrays.toString(param.args));
                        }
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) {
                                XposedBridge.log(TAG + "CONTACTS YellowPagePhoneLoader NEW THREW="
                                        + param.getThrowable());
                            } else {
                                XposedBridge.log(TAG + "CONTACTS YellowPagePhoneLoader NEW OK");
                            }
                        }
                    });
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "CONTACTS target hook failed " + className + ": " + t);
        }
    }

    @Override protected void beforeHookedMethod(MethodHookParam param) {
                                            XposedBridge.log(TAG + "CONTACTS Proxy.g(version gate) ENTER args="
                                                    + java.util.Arrays.toString(param.args));
                                        }
                                        @Override protected void afterHookedMethod(MethodHookParam param) {
                                            Object old = param.getResult();
                                            param.setResult(true);
                                            XposedBridge.log(TAG + "CONTACTS Proxy.g(version gate) "
                                                    + old + " -> FORCED true");
                                        }
                                    });
                                    XposedBridge.log(TAG + "CONTACTS Proxy.g(version gate) hook installed: " + m);
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "CONTACTS Proxy.g version gate hook failed: " + t);
                                }
                            }

                            if ("j".equals(mn) || "r".equals(mn) || "q".equals(mn)
                                    || "o".equals(mn) || "p".equals(mn)) {
                                try {
                                    final String methodName = mn;
                                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                                            XposedBridge.log(TAG + "CONTACTS Proxy." + methodName
                                                    + " ENTER args=" + java.util.Arrays.toString(param.args)
                                                    + "\n" + android.util.Log.getStackTraceString(
                                                    new Throwable("CALL STACK")));
                                        }

                                        @Override protected void afterHookedMethod(MethodHookParam param) {
                                            if (param.hasThrowable()) {
                                                XposedBridge.log(TAG + "CONTACTS Proxy." + methodName
                                                        + " THREW " + android.util.Log.getStackTraceString(
                                                        param.getThrowable()));
                                            } else {
                                                Object result = param.getResult();
                                                XposedBridge.log(TAG + "CONTACTS Proxy." + methodName
                                                        + " EXIT result="
                                                        + (result == null ? "null" : result.getClass().getName())
                                                        + " value=" + String.valueOf(result));
                                            }
                                        }
                                    });
                                    XposedBridge.log(TAG + "CONTACTS Proxy." + methodName
                                            + " hook installed: " + m);
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "CONTACTS Proxy." + mn
                                            + " hook failed: " + t);
                                }
                            }
                        }
                    }

                    if (name.contains("YellowPagePhoneLoader")) {
                        loaderCandidates++;
                        XposedBridge.log(TAG + "CONTACTS loader candidate: " + name);
                        for (java.lang.reflect.Constructor<?> ctor : cls.getDeclaredConstructors()) {
                            try {
                                XposedBridge.hookMethod(ctor, new XC_MethodHook() {
                                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                                        XposedBridge.log(TAG + "CONTACTS YellowPagePhoneLoader NEW args="
                                                + java.util.Arrays.toString(param.args)
                                                + "\n" + android.util.Log.getStackTraceString(
                                                new Throwable("LOADER NEW STACK")));
                                    }

                                    @Override protected void afterHookedMethod(MethodHookParam param) {
                                        if (param.hasThrowable()) {
                                            XposedBridge.log(TAG + "CONTACTS YellowPagePhoneLoader NEW THREW "
                                                    + android.util.Log.getStackTraceString(param.getThrowable()));
                                        } else {
                                            XposedBridge.log(TAG + "CONTACTS YellowPagePhoneLoader NEW OK");
                                        }
                                    }
                                });
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "CONTACTS loader constructor hook failed: " + t);
                            }
                        }
                    }

                    if (name.contains("TwelveKeyDialerFragment")) {
                        dialerCandidates++;
                        XposedBridge.log(TAG + "CONTACTS dialer candidate: " + name);
                        for (Method m : cls.getDeclaredMethods()) {
                            if ("F4".equals(m.getName())) {
                                try {
                                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                                            XposedBridge.log(TAG + "CONTACTS TwelveKeyDialerFragment.F4 ENTER"
                                                    + " args=" + java.util.Arrays.toString(param.args));
                                        }
                                        @Override protected void afterHookedMethod(MethodHookParam param) {
                                            if (param.hasThrowable()) {
                                                XposedBridge.log(TAG + "CONTACTS TwelveKeyDialerFragment.F4 THREW "
                                                        + android.util.Log.getStackTraceString(param.getThrowable()));
                                            } else {
                                                XposedBridge.log(TAG + "CONTACTS TwelveKeyDialerFragment.F4 EXIT result="
                                                        + String.valueOf(param.getResult()));
                                            }
                                        }
                                    });
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "CONTACTS F4 hook failed: " + t);
                                }
                            }
                        }
                    }

                    if (name.equals("androidx.loader.app.LoaderManager")
                            || name.endsWith(".LoaderManager")) {
                        loaderManagerCandidates++;
                        XposedBridge.log(TAG + "CONTACTS LoaderManager candidate: " + name);
                        for (Method m : cls.getDeclaredMethods()) {
                            String mn = m.getName();
                            if ("initLoader".equals(mn) || "restartLoader".equals(mn)
                                    || "destroyLoader".equals(mn)) {
                                try {
                                    final String methodName = mn;
                                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                                            XposedBridge.log(TAG + "CONTACTS LoaderManager."
                                                    + methodName + " ENTER args="
                                                    + java.util.Arrays.toString(param.args)
                                                    + "\n" + android.util.Log.getStackTraceString(
                                                    new Throwable("LOADER MANAGER STACK")));
                                        }
                                        @Override protected void afterHookedMethod(MethodHookParam param) {
                                            XposedBridge.log(TAG + "CONTACTS LoaderManager."
                                                    + methodName + " EXIT result="
                                                    + String.valueOf(param.getResult()));
                                        }
                                    });
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "CONTACTS LoaderManager." + mn
                                            + " hook failed: " + t);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            dex.close();

            XposedBridge.log(TAG + "CONTACTS scan finished scanned=" + scanned
                    + " proxyCandidates=" + proxyCandidates
                    + " loaderCandidates=" + loaderCandidates
                    + " dialerCandidates=" + dialerCandidates
                    + " loaderManagerCandidates=" + loaderManagerCandidates);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "CONTACTS scan failed: " + t);
        }
    }

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        if ("com.android.contacts".equals(lpparam.packageName)) {
            installContactsDiagnostics(lpparam);
            return;
        }
        if (!"com.miui.yellowpage".equals(lpparam.packageName)) return;

        try {
            final ClassLoader cl = lpparam.classLoader;

            XposedHelpers.findAndHookMethod(
                    "miui.yellowpage.YellowPageUtils", cl, "isYellowPageAvailable",
                    Context.class, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(true);
                            XposedBridge.log(TAG + "isYellowPageAvailable() -> true");
                        }
                    });

            try {
                XposedHelpers.findAndHookMethod(
                        "miui.yellowpage.YellowPageUtils", cl, "isYellowPageEnable",
                        Context.class, new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam param) {
                                boolean old = Boolean.TRUE.equals(param.getResult());
                                param.setResult(true);
                                XposedBridge.log(TAG + "isYellowPageEnable() " + old + " -> true");
                            }
                        });
                XposedBridge.log(TAG + "enable hook installed");
            } catch (Throwable t) {
                XposedBridge.log(TAG + "enable hook failed: " + t);
            }

            final Class<?> dbHelperClass = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageDatabaseHelper",
                    false, cl);

            XposedHelpers.findAndHookMethod(
                    dbHelperClass, "L", SQLiteDatabase.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "YellowPageDatabaseHelper.L() ENTER");
                        }
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "YellowPageDatabaseHelper.L() EXIT");
                        }
                    });
            XposedBridge.log(TAG + "DatabaseHelper.L hook installed");

            XposedHelpers.findAndHookMethod(
                    dbHelperClass, "N", Context.class, SQLiteDatabase.class,
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "YellowPageDatabaseHelper.N() ENTER");

                            Context context = (Context) param.args[0];
                            findAndHookPresetProvider(cl, context);
                        }

                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) {
                                XposedBridge.log(TAG + "YellowPageDatabaseHelper.N() THREW: "
                                        + param.getThrowable());
                                XposedBridge.log(TAG + "N() throwable stack: "
                                        + android.util.Log.getStackTraceString(param.getThrowable()));
                            } else {
                                XposedBridge.log(TAG + "YellowPageDatabaseHelper.N() EXIT normally");
                            }
                            SQLiteDatabase db = (SQLiteDatabase) param.args[1];
                            dumpTableCounts(db);
                        }
                    });
            XposedBridge.log(TAG + "DatabaseHelper.N hook installed");

            Class<?> providerClass = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageProvider", false, cl);

            XposedHelpers.findAndHookMethod(
                    providerClass, "onCreate", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "YellowPageProvider.onCreate()");

                            try {
                                Context context = (Context) XposedHelpers.callMethod(
                                        param.thisObject, "getContext");
                                XposedBridge.log(TAG + "scanning preset provider before database open");
                                findAndHookPresetProvider(cl, context);
                                Object helper = XposedHelpers.callStaticMethod(
                                        dbHelperClass, "E", context);
                                SQLiteDatabase db = (SQLiteDatabase) XposedHelpers.callMethod(
                                        helper, "getWritableDatabase");

                                XposedBridge.log(TAG + "forcing Provider data import via L()");
                                XposedHelpers.callMethod(helper, "L", db);
                                XposedBridge.log(TAG + "forced Provider data import finished");

                                XposedBridge.log(TAG + "forcing preset Yellow Page import via N()");
                                try {
                                    Object preset = XposedHelpers.callStaticMethod(
                                            Class.forName("r0.c", false, cl), "n");
                                    Object presetPath = XposedHelpers.callMethod(preset, "c", context);
                                    java.io.File pf = new java.io.File(String.valueOf(presetPath));
                                    XposedBridge.log(TAG + "preset file path=" + pf.getAbsolutePath()
                                            + " exists=" + pf.exists()
                                            + " length=" + (pf.exists() ? pf.length() : -1)
                                            + " parentExists=" + (pf.getParentFile() != null && pf.getParentFile().exists()));
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "preset file precheck failed: " + t);
                                }
                                XposedHelpers.callMethod(helper, "N", context, db);
                                XposedBridge.log(TAG + "forced preset Yellow Page import finished");

                                dumpTableCounts(db);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "forced Provider import failed: " + t);
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(
                    providerClass, "query",
                    android.net.Uri.class, String[].class, String.class,
                    String[].class, String.class, new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "YellowPageProvider.query() uri="
                                    + param.args[0] + " projection=" + java.util.Arrays.toString((String[]) param.args[1])
                                    + " selection=" + param.args[2]
                                    + " args=" + java.util.Arrays.toString((String[]) param.args[3])
                                    + " sort=" + param.args[4]);
                            try {
                                Object calling = XposedHelpers.callMethod(param.thisObject, "getCallingPackage");
                                XposedBridge.log(TAG + "Provider.query callingPackage=" + calling
                                        + " uid=" + android.os.Binder.getCallingUid());
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "Provider.query caller diagnostic failed: " + t);
                            }
                        }
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            Object result = param.getResult();

                            // EEA provider successfully executes the JOIN query but its
                            // post-processing can discard the Cursor and return null.
                            // Recover the already-proven local result for phone lookup items.
                            if (result == null) {
                                try {
                                    android.net.Uri uri = (android.net.Uri) param.args[0];
                                    if (uri != null && uri.toString().startsWith(
                                            "content://miui.yellowpage/phone_lookup/")) {
                                        Context context = (Context) XposedHelpers.callMethod(
                                                param.thisObject, "getContext");
                                        Object helper = XposedHelpers.callStaticMethod(
                                                dbHelperClass, "E", context);
                                        SQLiteDatabase db = (SQLiteDatabase) XposedHelpers.callMethod(
                                                helper, "getReadableDatabase");
                                        String number = uri.getLastPathSegment();
                                        String normalized = number;
                                        try {
                                            Class<?> norm = Class.forName("p022h0.e", false, cl);
                                            normalized = (String) XposedHelpers.callStaticMethod(
                                                    norm, "a", context, number);
                                        } catch (Throwable ignored) {
                                        }
                                        String table = "((SELECT yid AS yellowpage_id, photo_url,thumbnail_url,tag,"
                                                + "yellow_page_name,yellow_page_name_pinyin,tag_pinyin,number,"
                                                + "normalized_number,min_match,hide,suspect,call_menu,t9_rank,"
                                                + "atd_category_id,atd_count,atd_provider,flag,slogan,credit_img,"
                                                + "number_type,provider_id FROM phone_lookup WHERE normalized_number = ?)"
                                                + " INNER JOIN yellow_page ON yellowpage_id = yid)";
                                        Cursor recovery = db.query(table, null, null,
                                                new String[]{normalized}, null, null,
                                                "update_time desc");
                                        if (recovery != null && recovery.moveToFirst()) {
                                            XposedBridge.log(TAG + "Provider recovery: replacing null with "
                                                    + "direct JOIN Cursor for number=" + number
                                                    + " normalized=" + normalized
                                                    + " count=" + recovery.getCount());
                                            // Return a standalone MatrixCursor instead of the raw SQLiteCursor.
                                            // This avoids the EEA provider's URL/post-processing path while
                                            // preserving the exact columns and values from the proven JOIN row.
                                            String[] recoveryColumns = recovery.getColumnNames();
                                            MatrixCursor matrix = new MatrixCursor(recoveryColumns, recovery.getCount());
                                            recovery.moveToPosition(-1);
                                            while (recovery.moveToNext()) {
                                                Object[] row = new Object[recoveryColumns.length];
                                                for (int i = 0; i < recoveryColumns.length; i++) {
                                                    row[i] = recovery.getString(i);
                                                }
                                                matrix.addRow(row);
                                            }
                                            recovery.close();
                                            param.setResult(matrix);
                                            result = matrix;
                                        } else if (recovery != null) {
                                            recovery.close();
                                        }
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "Provider recovery failed: " + t);
                                }
                            }

                            if (param.hasThrowable()) {
                                XposedBridge.log(TAG + "query THREW: "
                                        + android.util.Log.getStackTraceString(param.getThrowable()));
                            }
                            if (result instanceof Cursor) {
                                Cursor cursor = (Cursor) result;
                                try {
                                    XposedBridge.log(TAG + "Provider.query result count=" + cursor.getCount());
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + "cursor inspect failed: " + t);
                                }
                            } else {
                                XposedBridge.log(TAG + "query returned non-Cursor="
                                        + (result == null ? "null" : result.getClass().getName()));
                            }
                        }
                    });

            XposedBridge.log(TAG + "Provider hooks installed");
            XposedBridge.log(TAG + "HookEntry initialized");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "HookEntry failed: " + t);
        }
    }
}
