package com.microllate.miuyellowpage;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "[miu-iYellowPage] ";

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

            Class<?> dbHelperClass = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageDatabaseHelper", false, cl);

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

            Class<?> providerClass = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageProvider", false, cl);

            XposedHelpers.findAndHookMethod(
                    providerClass, "onCreate", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            XposedBridge.log(TAG + "YellowPageProvider.onCreate()");

                            try {
                                Context context = (Context) XposedHelpers.callMethod(
                                        param.thisObject, "getContext");
                                Object helper = XposedHelpers.callStaticMethod(
                                        dbHelperClass, "E", context);
                                SQLiteDatabase db = (SQLiteDatabase) XposedHelpers.callMethod(
                                        helper, "getWritableDatabase");

                                XposedBridge.log(TAG + "forcing Provider data import via L()");
                                XposedHelpers.callMethod(helper, "L", db);
                                XposedBridge.log(TAG + "forced Provider data import finished");
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
                            if (result instanceof Cursor) {
                                Cursor cursor = (Cursor) result;
                                try {
                                    XposedBridge.log(TAG + "query cursor count=" + cursor.getCount()
                                            + " columns=" + java.util.Arrays.toString(cursor.getColumnNames()));
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
