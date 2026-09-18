package com.microllate.miuyellowpage;

import android.content.Context;
import android.database.Cursor;
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

    private static void dumpTableCounts(SQLiteDatabase db) {
        String[] tables = {"provider", "yellow_page", "phone_lookup", "t9_lookup"};
        for (String table : tables) {
            Cursor c = null;
            try {
                c = db.rawQuery("SELECT COUNT(*) FROM " + table, null);
                if (c.moveToFirst()) {
                    XposedBridge.log(TAG + table + " COUNT = " + c.getInt(0));
                }
            } catch (Throwable t) {
                XposedBridge.log(TAG + table + " COUNT FAILED: " + t);
            } finally {
                if (c != null) c.close();
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

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
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
                                    + param.args[0] + " selection=" + param.args[2]);
                        }
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            Object result = param.getResult();
                            if (param.hasThrowable()) {
                                XposedBridge.log(TAG + "query THREW: "
                                        + android.util.Log.getStackTraceString(param.getThrowable()));
                            }
                            if (result instanceof Cursor) {
                                Cursor cursor = (Cursor) result;
                                try {
                                    XposedBridge.log(TAG + "query cursor count=" + cursor.getCount()
                                            + " columns=" + java.util.Arrays.toString(cursor.getColumnNames()));
                                    if (cursor.moveToFirst()) {
                                        StringBuilder row = new StringBuilder();
                                        String[] cols = cursor.getColumnNames();
                                        for (int i = 0; i < cols.length; i++) {
                                            if (i > 0) row.append(" | ");
                                            row.append(cols[i]).append("=").append(cursor.getString(i));
                                        }
                                        XposedBridge.log(TAG + "query first row: " + row);
                                    }
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
