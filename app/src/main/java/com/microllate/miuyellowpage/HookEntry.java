package com.microllate.miuyellowpage;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

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
    private static final String TAG = "miu-iYellowPage";

    private static void log(String message) {
        Log.i(TAG, message);
        try {
            XposedBridge.log(TAG + ": " + message);
        } catch (Throwable ignored) {
        }
    }

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
                log("hooked " + className + "." + methodName + "(Context)");
            }
        } catch (Throwable e) {
            log("hook failed " + className + "." + methodName + ": "
                    + e.getClass().getSimpleName());
        }
    }

        private static void hookYellowPageSyncGate(ClassLoader cl) {
        try {
            Class<?> feature = Class.forName("c0.b", false, cl);
            Class<?> enumClass = Class.forName("c0.a", false, cl);
            Object sync = Enum.valueOf((Class<Enum>) enumClass.asSubclass(Enum.class), "YELLOWPAGE_SYNC");
            for (Method method : feature.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();
                if (!Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != Boolean.TYPE
                        || p.length != 2
                        || p[0] != Context.class
                        || p[1] != enumClass) {
                    continue;
                }
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.args[1] == sync) {
                            log("YELLOWPAGE_SYNC: original=" + param.getResult() + " -> true");
                            param.setResult(true);
                        }
                    }
                });
                log("hooked YELLOWPAGE_SYNC feature gate");
                return;
            }
            log("YELLOWPAGE_SYNC feature gate method not found");
        } catch (Throwable e) {
            log("YELLOWPAGE_SYNC hook failed: " + e.getClass().getSimpleName());
        }
    }

private static void hookYellowPagePullTask(ClassLoader cl, Context context) {
        try {
            if (context == null) {
                log("PullTask scan skipped: context=null");
                return;
            }

            int found = 0;
            java.util.ArrayList<String> paths = new java.util.ArrayList<>();
            paths.add(context.getApplicationInfo().sourceDir);
            String[] splits = context.getApplicationInfo().splitSourceDirs;
            if (splits != null) {
                for (String split : splits) {
                    if (split != null && !paths.contains(split)) {
                        paths.add(split);
                    }
                }
            }

            for (String apkPath : paths) {
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
                            for (Method method : candidate.getDeclaredMethods()) {
                                Class<?>[] p = method.getParameterTypes();
                                if (!"y".equals(method.getName())
                                        || method.getReturnType() != Boolean.TYPE
                                        || p.length != 1
                                        || p[0] != Context.class) {
                                    continue;
                                }

                                final String className = candidate.getName();
                                XposedBridge.hookMethod(method, new XC_MethodHook() {
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param) {
                                        log("PullTask candidate ENTER: "
                                                + className + ".y(Context)");
                                    }

                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) {
                                        log("PullTask candidate RESULT: "
                                                + className + ".y(Context)=" + param.getResult());
                                    }
                                });
                                found++;
                                log("hooked PullTask candidate: "
                                        + className + ".y(Context)");
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                } finally {
                    dex.close();
                }
            }

            if (found == 0) {
                log("PullTask candidate y(Context):boolean not found");
            } else {
                log("PullTask candidate hooks installed: " + found);
            }
        } catch (Throwable e) {
            log("PullTask scan failed: " + e.getClass().getSimpleName());
        }
    }


    private static void hookJobDispatcher(ClassLoader cl) {
        try {
            Class<?> dispatcher = Class.forName("a0.C0166b", false, cl);

            // EEA disables pull_task_job through i.f(Context). Restore only
            // the Yellow Page pull job gate; leave the other jobs untouched.
            for (Method method : dispatcher.getDeclaredMethods()) {
                if (!"a".equals(method.getName())
                        || !Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != Boolean.TYPE) {
                    continue;
                }

                Class<?>[] p = method.getParameterTypes();
                if (p.length != 2 || p[0] != Context.class || p[1] != Integer.TYPE) {
                    continue;
                }

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        int jobId = (Integer) param.args[1];
                        if (jobId == 0) {
                            log("JobDispatcher.canScheduleJob: pull_task_job -> true");
                            param.setResult(true);
                        }
                    }
                });
                log("hooked JobDispatcher.canScheduleJob(Context,int)");
            }

            // Log the actual scheduling call so we can verify that JobScheduler
            // receives pull_task_job after the gate is restored.
            for (Method method : dispatcher.getDeclaredMethods()) {
                if (!"e".equals(method.getName())
                        || !Modifier.isStatic(method.getModifiers())
                        || method.getReturnType() != Void.TYPE) {
                    continue;
                }

                Class<?>[] p = method.getParameterTypes();
                if (p.length != 3
                        || p[0] != Context.class
                        || p[1] != Integer.TYPE
                        || p[2] != Boolean.TYPE) {
                    continue;
                }

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        int jobId = (Integer) param.args[1];
                        if (jobId == 0) {
                            log("JobDispatcher.scheduleJob ENTER: pull_task_job");
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        int jobId = (Integer) param.args[1];
                        if (jobId == 0) {
                            log("JobDispatcher.scheduleJob EXIT: pull_task_job");
                        }
                    }
                });
                log("hooked JobDispatcher.scheduleJob(Context,int,boolean)");
            }
        } catch (Throwable e) {
            log("JobDispatcher hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static void hookYellowPageJobServices(ClassLoader cl, Context context) {
        try {
            int found = 0;
            java.util.ArrayList<String> paths = new java.util.ArrayList<>();
            paths.add(context.getApplicationInfo().sourceDir);
            String[] splits = context.getApplicationInfo().splitSourceDirs;
            if (splits != null) {
                for (String split : splits) {
                    if (split != null && !paths.contains(split)) paths.add(split);
                }
            }

            for (String apkPath : paths) {
                DexFile dex = new DexFile(apkPath);
                try {
                    Enumeration<String> entries = dex.entries();
                    while (entries.hasMoreElements()) {
                        String name = entries.nextElement();
                        if (name.indexOf('.') < 0 || !name.toLowerCase().contains("jobservice")) continue;
                        try {
                            Class<?> cls = Class.forName(name, false, cl);
                            for (Method method : cls.getDeclaredMethods()) {
                                String mn = method.getName();
                                if (!"onStartJob".equals(mn) && !"onStopJob".equals(mn)) continue;
                                XposedBridge.hookMethod(method, new XC_MethodHook() {
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param) {
                                        log("JobService ENTER: " + cls.getName() + "." + method.getName());
                                    }
                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) {
                                        log("JobService RESULT: " + cls.getName() + "." + method.getName()
                                                + "=" + param.getResult());
                                    }
                                });
                                found++;
                                log("hooked JobService: " + cls.getName() + "." + mn);
                            }
                        } catch (Throwable ignored) {
                        }
                    }
                } finally {
                    dex.close();
                }
            }
            log("JobService hooks installed: " + found);
        } catch (Throwable e) {
            log("JobService scan failed: " + e.getClass().getSimpleName());
        }
    }

    private static void hookPullTaskExecution(ClassLoader cl) {
        try {
            Class<?> cls = Class.forName("o0.g", false, cl);
            log("PullTask class found: " + cls.getName());
            for (Method method : cls.getDeclaredMethods()) {
                String mn = method.getName();
                Class<?>[] p = method.getParameterTypes();
                log("PullTask method: " + mn + "(" + p.length + " args) -> "
                        + method.getReturnType().getSimpleName());

                if (!"run".equals(mn) && !"execute".equals(mn) && !"pull".equals(mn)
                        && !"y".equals(mn)) continue;

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("PullTask EXEC ENTER: o0.g." + method.getName());
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        log("PullTask EXEC RESULT: o0.g." + method.getName()
                                + "=" + String.valueOf(param.getResult()));
                    }
                });
                log("hooked PullTask execution method: o0.g." + mn);
            }
        } catch (Throwable e) {
            log("PullTask execution hook failed: " + e.getClass().getSimpleName());
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
                log("hooked Contacts YellowPageProxy." + methodName + "(Context)");
            }
        } catch (Throwable e) {
            log("Contacts hook failed " + methodName + ": "
                    + e.getClass().getSimpleName());
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
        log("preset hooks installed: " + preset.getName());
    }

    private static void installPresetHooks(
            ClassLoader cl, Context context) {
        try {
            try {
                Class<?> preset = Class.forName("r0.c", false, cl);
                hookPresetProviderClass(preset, context);
                return;
            } catch (Throwable ignored) {
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
            log("preset provider class not found");
        } catch (Throwable e) {
            log("preset hook install failed: " + e.getClass().getSimpleName());
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
                    log("database ready; yellow_page=" + c.getInt(0)
                            + ", phone_lookup=" + c.getInt(1));
                    return;
                }
            } finally {
                if (c != null) {
                    c.close();
                }
            }

            log("database incomplete; importing preset data");
            installPresetHooks(cl, context);
            XposedHelpers.callMethod(helper, "L", db);
            XposedHelpers.callMethod(helper, "N", context, db);
            log("preset import requested");
        } catch (Throwable e) {
            log("preset import failed: " + e.getClass().getSimpleName());
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
                            log("YellowPageProvider.onCreate");
                            hookYellowPagePullTask(cl, context);
                            hookJobDispatcher(cl);
                            hookYellowPageJobServices(cl, context);
                            hookPullTaskExecution(cl);
                            importYellowPageData(cl, context, dbHelperClass);
                        } catch (Throwable e) {
                            log("provider onCreate hook failed: "
                                    + e.getClass().getSimpleName());
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

                            int count = matrix.getCount();
                            recovery.close();
                            param.setResult(matrix);
                            log("fallback lookup: " + number + " -> " + count + " row(s)");
                        } catch (Throwable e) {
                            log("fallback query failed: "
                                    + e.getClass().getSimpleName());
                        }
                    }
                });

        log("YellowPageProvider hooks installed");
    }

    @Override
    public void handleLoadPackage(
            final XC_LoadPackage.LoadPackageParam lpparam) {
        if ("com.android.contacts".equals(lpparam.packageName)) {
            log("loaded in Contacts");
            hookContactsGate(lpparam.classLoader, "i");
            hookContactsGate(lpparam.classLoader, "j");
            return;
        }

        if (!YELLOWPAGE.equals(lpparam.packageName)) {
            return;
        }

        log("loaded in YellowPage");

        try {
            ClassLoader cl = lpparam.classLoader;

            hookBooleanContextMethod(
                    cl, "miui.yellowpage.YellowPageUtils",
                    "isYellowPageAvailable");
            hookBooleanContextMethod(
                    cl, "miui.yellowpage.YellowPageUtils",
                    "isYellowPageEnable");
            hookYellowPageSyncGate(cl);
            // JobDispatcher is installed after a real application/provider context exists.
            // The provider hook below also ensures the EEA pull-task gate is restored.
            // Application context can be null this early in Zygote package loading.
            // The provider hook below scans after a real YellowPage Context exists.
            log("PullTask scan deferred until YellowPageProvider.onCreate");

            Class<?> dbHelperClass = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageDatabaseHelper",
                    false, cl);

            installProviderHooks(cl, dbHelperClass);
        } catch (Throwable e) {
            log("YellowPage initialization failed: "
                    + e.getClass().getSimpleName());
        }
    }
}
