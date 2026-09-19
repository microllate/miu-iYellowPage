package com.microllate.miuyellowpage;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;

import dalvik.system.DexFile;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage {
    private static final String YELLOWPAGE = "com.miui.yellowpage";

    private static void hookBooleanContextMethod(
            ClassLoader cl, String className, String methodName) {
        try {
            Class<?> cls = Class.forName(className, false, cl);
            for (Method method : cls.getDeclaredMethods()) {
                if (!methodName.equals(method.getName())
                        || method.getReturnType() != Boolean.TYPE
                        || method.getParameterTypes().length != 1
                        || method.getParameterTypes()[0] != Context.class) {
                    continue;
                }
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(true);
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static void hookContactsGate(
            ClassLoader cl, String methodName) {
        try {
            Class<?> proxy = Class.forName(
                    "com.android.contacts.util.YellowPageProxy", false, cl);
            for (Method method : proxy.getDeclaredMethods()) {
                if (!methodName.equals(method.getName())
                        || method.getReturnType() != Boolean.TYPE
                        || method.getParameterTypes().length != 1
                        || method.getParameterTypes()[0] != Context.class) {
                    continue;
                }
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        param.setResult(true);
                    }
                });
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean hasNoArgMethodReturning(
            Class<?> cls, String name, Class<?> returnType) {
        try {
            Method method = cls.getDeclaredMethod(name);
            return method.getReturnType() == returnType;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isPresetProviderClass(Class<?> cls) {
        try {
            if (!hasNoArgMethodReturning(cls, "h", Integer.TYPE)
                    || !hasNoArgMethodReturning(cls, "d", String.class)
                    || !hasNoArgMethodReturning(cls, "f", String.class)
                    || !hasNoArgMethodReturning(cls, "i", String.class)) {
                return false;
            }

            Method n = cls.getDeclaredMethod("n");
            return n.getReturnType() == cls
                    && Modifier.isStatic(n.getModifiers());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void hookPresetProviderClass(
            Class<?> preset, Context context) {
        XposedHelpers.findAndHookMethod(
                preset, "h",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int resId = context.getResources().getIdentifier(
                                "yellow_pages_cn", "raw", YELLOWPAGE);
                        if (resId != 0) {
                            param.setResult(resId);
                        }
                    }
                });

        Class<?> base = preset.getSuperclass();
        if (base != null) {
            XposedHelpers.findAndHookMethod(
                    base, "l", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(true);
                        }
                    });
        }
    }

    private static void installPresetHooks(
            ClassLoader cl, Context context) {
        try {
            try {
                Class<?> preset = Class.forName("r0.c", false, cl);
                hookPresetProviderClass(preset, context);
                return;
            } catch (Throwable ignored) {
                // Current build uses r0.c, but R8 can rename this class.
                // Fall through to the lazy shape-based scan.
            }

            String apkPath = context.getApplicationInfo().sourceDir;
            DexFile dex = new DexFile(apkPath);
            try {
                Enumeration<String> entries = dex.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement();
                    if (name.indexOf('.') < 0) {
                        continue;
                    }

                    try {
                        Class<?> candidate = Class.forName(name, false, cl);
                        if (isPresetProviderClass(candidate)) {
                            hookPresetProviderClass(candidate, context);
                            return;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } finally {
                dex.close();
            }
        } catch (Throwable ignored) {
        }
    }

    private static void importYellowPageData(
            ClassLoader cl, Context context, Class<?> dbHelperClass) {
        try {
            Object helper = XposedHelpers.callStaticMethod(
                    dbHelperClass, "E", context);
            SQLiteDatabase db = (SQLiteDatabase) XposedHelpers.callMethod(
                    helper, "getWritableDatabase");

            Cursor c = null;
            try {
                c = db.rawQuery(
                        "SELECT (SELECT COUNT(*) FROM yellow_page),"
                                + " (SELECT COUNT(*) FROM phone_lookup)", null);
                if (c.moveToFirst() && c.getInt(0) > 0 && c.getInt(1) > 0) {
                    return;
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }

            installPresetHooks(cl, context);
            XposedHelpers.callMethod(helper, "L", db);
            XposedHelpers.callMethod(helper, "N", context, db);
        } catch (Throwable ignored) {
        }
    }

    private static void copyCursorValue(
            Cursor source, int column, Object[] row) {
        switch (source.getType(column)) {
            case Cursor.FIELD_TYPE_NULL:
                row[column] = null;
                break;
            case Cursor.FIELD_TYPE_INTEGER:
                row[column] = source.getLong(column);
                break;
            case Cursor.FIELD_TYPE_FLOAT:
                row[column] = source.getDouble(column);
                break;
            case Cursor.FIELD_TYPE_BLOB:
                row[column] = source.getBlob(column);
                break;
            case Cursor.FIELD_TYPE_STRING:
            default:
                row[column] = source.getString(column);
                break;
        }
    }

    private static void installProviderHooks(
            ClassLoader cl, Class<?> dbHelperClass) throws Throwable {
        Class<?> providerClass = Class.forName(
                "com.miui.yellowpage.providers.yellowpage.YellowPageProvider",
                false, cl);

        XposedHelpers.findAndHookMethod(
                providerClass, "onCreate",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        try {
                            Context context = (Context) XposedHelpers.callMethod(
                                    param.thisObject, "getContext");
                            importYellowPageData(cl, context, dbHelperClass);
                        } catch (Throwable ignored) {
                        }
                    }
                });

        XposedHelpers.findAndHookMethod(
                providerClass, "query",
                android.net.Uri.class,
                String[].class,
                String.class,
                String[].class,
                String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            return;
                        }

                        Cursor original = param.getResult() instanceof Cursor
                                ? (Cursor) param.getResult() : null;
                        if (original != null && original.getCount() > 0) {
                            return;
                        }

                        try {
                            if (original != null) {
                                original.close();
                            }
                            param.setResult(null);

                            android.net.Uri uri = (android.net.Uri) param.args[0];
                            if (uri == null
                                    || !"miui.yellowpage".equals(uri.getAuthority())
                                    || uri.getPathSegments().size() != 2
                                    || !"phone_lookup".equals(
                                            uri.getPathSegments().get(0))) {
                                return;
                            }

                            Context context = (Context) XposedHelpers.callMethod(
                                    param.thisObject, "getContext");
                            Object helper = XposedHelpers.callStaticMethod(
                                    dbHelperClass, "E", context);
                            SQLiteDatabase db = (SQLiteDatabase) XposedHelpers.callMethod(
                                    helper, "getReadableDatabase");

                            String number = uri.getLastPathSegment();
                            String normalized = number;

                            try {
                                Class<?> normalizer = Class.forName(
                                        "p022h0.e", false, cl);
                                normalized = (String) XposedHelpers.callStaticMethod(
                                        normalizer, "a", context, number);
                            } catch (Throwable ignored) {
                            }

                            String table =
                                    "((SELECT yid AS yellowpage_id, photo_url,thumbnail_url,tag,"
                                    + "yellow_page_name,yellow_page_name_pinyin,tag_pinyin,number,"
                                    + "normalized_number,min_match,hide,suspect,call_menu,t9_rank,"
                                    + "atd_category_id,atd_count,atd_provider,flag,slogan,credit_img,"
                                    + "number_type,provider_id FROM phone_lookup WHERE normalized_number = ?)"
                                    + " INNER JOIN yellow_page ON yellowpage_id = yid)";

                            Cursor recovery = db.query(
                                    table, null, null, new String[]{normalized},
                                    null, null, "update_time desc");

                            if (recovery == null || !recovery.moveToFirst()) {
                                if (recovery != null) {
                                    recovery.close();
                                }
                                return;
                            }

                            String[] columns = recovery.getColumnNames();
                            MatrixCursor matrix =
                                    new MatrixCursor(columns, recovery.getCount());
                            recovery.moveToPosition(-1);

                            while (recovery.moveToNext()) {
                                Object[] row = new Object[columns.length];
                                for (int i = 0; i < columns.length; i++) {
                                    copyCursorValue(recovery, i, row);
                                }
                                matrix.addRow(row);
                            }

                            recovery.close();
                            param.setResult(matrix);
                        } catch (Throwable ignored) {
                        }
                    }
                });
    }

    @Override
    public void handleLoadPackage(
            final XC_LoadPackage.LoadPackageParam lpparam) {
        if ("com.android.contacts".equals(lpparam.packageName)) {
            hookContactsGate(lpparam.classLoader, "i");
            hookContactsGate(lpparam.classLoader, "j");
            return;
        }

        if (!YELLOWPAGE.equals(lpparam.packageName)) {
            return;
        }

        try {
            ClassLoader cl = lpparam.classLoader;

            hookBooleanContextMethod(
                    cl, "miui.yellowpage.YellowPageUtils",
                    "isYellowPageAvailable");
            hookBooleanContextMethod(
                    cl, "miui.yellowpage.YellowPageUtils",
                    "isYellowPageEnable");

            Class<?> dbHelperClass = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageDatabaseHelper",
                    false, cl);

            installProviderHooks(cl, dbHelperClass);
        } catch (Throwable ignored) {
        }
    }
}
