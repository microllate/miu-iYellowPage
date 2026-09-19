package com.microllate.miuyellowpage;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Enumeration;
import android.content.ContentValues;

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


    private static void hookJobDispatcher(ClassLoader cl, Context context) {
        try {
            Class<?> dispatcher = null;

            // JADX reports this class as a0.C0166b, but some EEA builds can
            // expose the obfuscated package/class through a different dex
            // loading path. Try the exact name first, then locate the class
            // by the unique dispatcher method signatures.
            try {
                dispatcher = Class.forName("a0.C0166b", false, cl);
            } catch (Throwable ignored) {
                // Fall through to dex scan.
            }

            if (dispatcher == null) {
                java.util.ArrayList<String> paths = new java.util.ArrayList<>();
                if (context != null) {
                    paths.add(context.getApplicationInfo().sourceDir);
                    String[] splits = context.getApplicationInfo().splitSourceDirs;
                    if (splits != null) {
                        for (String split : splits) {
                            if (split != null && !paths.contains(split)) paths.add(split);
                        }
                    }
                }

                for (String apkPath : paths) {
                    DexFile dex = new DexFile(apkPath);
                    try {
                        Enumeration<String> entries = dex.entries();
                        while (entries.hasMoreElements() && dispatcher == null) {
                            String name = entries.nextElement();
                            if (name.indexOf('.') < 0) continue;
                            try {
                                Class<?> candidate = Class.forName(name, false, cl);
                                boolean hasA = false;
                                boolean hasE = false;
                                for (Method m : candidate.getDeclaredMethods()) {
                                    Class<?>[] p = m.getParameterTypes();
                                    if ("a".equals(m.getName())
                                            && Modifier.isStatic(m.getModifiers())
                                            && m.getReturnType() == Boolean.TYPE
                                            && p.length == 2
                                            && p[0] == Context.class
                                            && p[1] == Integer.TYPE) {
                                        hasA = true;
                                    }
                                    if ("e".equals(m.getName())
                                            && Modifier.isStatic(m.getModifiers())
                                            && m.getReturnType() == Void.TYPE
                                            && p.length == 3
                                            && p[0] == Context.class
                                            && p[1] == Integer.TYPE
                                            && p[2] == Boolean.TYPE) {
                                        hasE = true;
                                    }
                                }
                                if (hasA && hasE) {
                                    dispatcher = candidate;
                                    log("JobDispatcher class found by method shape: "
                                            + candidate.getName());
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    } finally {
                        dex.close();
                    }
                }
            }

            if (dispatcher == null) {
                log("JobDispatcher class not found");
                return;
            }

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
                final String methodName = method.getName();
                final Class<?> returnType = method.getReturnType();

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("PullTask o0.g ENTER: " + methodName
                                + " args=" + (param.args == null ? 0 : param.args.length));
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            Throwable t = param.getThrowable();
                            log("PullTask o0.g THROW: " + methodName
                                    + " " + t.getClass().getName() + ": " + t.getMessage());
                        } else {
                            Object result = param.getResult();
                            String text = String.valueOf(result);
                            if (text.length() > 300) text = text.substring(0, 300);
                            log("PullTask o0.g RESULT: " + methodName + "=" + text);

                            // o0.g.j(...) returns H. The actual network/data work
                            // appears to continue on that returned object, so hook
                            // its concrete methods when j() returns an object.
                            if ("j".equals(methodName) && result != null) {
                                hookReturnedPullObject(result);
                            }
                        }
                    }
                });

                log("hooked PullTask o0.g method: " + methodName
                        + "(" + method.getParameterTypes().length + " args) -> "
                        + returnType.getSimpleName());
            }
        } catch (Throwable e) {
            log("PullTask execution hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static String formatHookArgs(Object[] args) {
        if (args == null || args.length == 0) return "0";
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) out.append(" | ");
            Object value = args[i];
            if (value == null) {
                out.append("null");
            } else {
                String text = String.valueOf(value);
                if (text.length() > 300) text = text.substring(0, 300);
                out.append(value.getClass().getName()).append(":").append(text);
            }
        }
        return out.toString();
    }


    private static void hookConcreteHttpResponse(Object connection) {
        try {
            if (connection == null) return;
            Class<?> current = connection.getClass();
            int depth = 0;
            int found = 0;
            log("HTTP CONCRETE START: " + current.getName());
            while (current != null && current != Object.class && depth < 8) {
                for (Method method : current.getDeclaredMethods()) {
                    final String name = method.getName();
                    if (!("connect".equals(name)
                            || "getResponseCode".equals(name)
                            || "getResponseMessage".equals(name)
                            || "getInputStream".equals(name)
                            || "getErrorStream".equals(name)
                            || "getContent".equals(name)
                            || "disconnect".equals(name))) {
                        continue;
                    }
                    if (method.getParameterTypes().length != 0) continue;
                    try {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                log("HTTP CONCRETE ENTER: " + name
                                        + " class=" + param.thisObject.getClass().getName());
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.hasThrowable()) {
                                    Throwable t = param.getThrowable();
                                    log("HTTP CONCRETE THROW: " + name + " "
                                            + t.getClass().getName() + ": "
                                            + String.valueOf(t.getMessage()));
                                    return;
                                }
                                Object result = param.getResult();
                                String value = String.valueOf(result);
                                if (value.length() > 1200) value = value.substring(0, 1200);
                                log("HTTP CONCRETE RESULT: " + name + " -> " + value
                                        + " resultClass="
                                        + (result == null ? "null" : result.getClass().getName()));
                            }
                        });
                        found++;
                        log("HTTP CONCRETE HOOKED: " + current.getName() + "." + name);
                    } catch (Throwable e) {
                        log("HTTP CONCRETE HOOK FAILED: " + current.getName() + "."
                                + name + " " + e.getClass().getName() + ": "
                                + String.valueOf(e.getMessage()));
                    }
                }
                current = current.getSuperclass();
                depth++;
            }
            log("HTTP CONCRETE INSTALLED: " + found);
        } catch (Throwable e) {
            log("HTTP CONCRETE START FAILED: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }


    private static void hookYellowPageResponseBodyCapture(Object connection) {
        try {
            if (!(connection instanceof java.net.HttpURLConnection)) return;
            java.net.HttpURLConnection http = (java.net.HttpURLConnection) connection;
            final String url;
            try { url = String.valueOf(http.getURL()); } catch (Throwable e) { return; }
            if (!(url.contains("api.comm.miui.com/cspmisc/patch/info")
                    || url.contains("global.api.huangye.miui.com/spbook/atd/v2/cat_sync")
                    || url.contains("global.api.huangye.miui.com/spbook/yellowpage/provider/info"))) {
                return;
            }
            Class<?> cls = connection.getClass();
            for (Method m : cls.getMethods()) {
                if (!"getInputStream".equals(m.getName()) || m.getParameterTypes().length != 0) continue;
                try {
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable() || !(param.getResult() instanceof java.io.InputStream)) return;
                            final java.io.InputStream original = (java.io.InputStream) param.getResult();
                            if (original instanceof java.io.FilterInputStream) return;
                            java.io.FilterInputStream tee = new java.io.FilterInputStream(original) {
                                private final java.io.ByteArrayOutputStream capture = new java.io.ByteArrayOutputStream();
                                private int total;
                                private boolean dumped;

                                private void record(byte[] b, int off, int len) {
                                    if (len <= 0 || total >= 65536) return;
                                    int take = Math.min(len, 65536 - total);
                                    capture.write(b, off, take);
                                    total += take;
                                }

                                private void dumpIfNeeded() {
                                    if (dumped) return;
                                    dumped = true;
                                    byte[] data = capture.toByteArray();
                                    String body;
                                    try {
                                        body = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                                    } catch (Throwable e) {
                                        body = java.util.Arrays.toString(data);
                                    }
                                    if (body.length() > 12000) body = body.substring(0, 12000);
                                    log("HTTP BODY CAPTURE URL: " + url);
                                    log("HTTP BODY CAPTURE BYTES: " + data.length);
                                    log("HTTP BODY CAPTURE TEXT: " + body);
                                }

                                @Override public int read() throws java.io.IOException {
                                    int v = super.read();
                                    if (v >= 0) {
                                        byte[] one = {(byte) v};
                                        record(one, 0, 1);
                                    } else dumpIfNeeded();
                                    return v;
                                }

                                @Override public int read(byte[] b, int off, int len) throws java.io.IOException {
                                    int n = super.read(b, off, len);
                                    if (n > 0) record(b, off, n);
                                    else if (n < 0) dumpIfNeeded();
                                    return n;
                                }

                                @Override public void close() throws java.io.IOException {
                                    try { dumpIfNeeded(); } finally { super.close(); }
                                }
                            };
                            param.setResult(tee);
                            log("HTTP BODY CAPTURE WRAPPED: " + url);
                        }
                    });
                    log("HTTP BODY CAPTURE HOOKED: " + cls.getName() + ".getInputStream()");
                } catch (Throwable e) {
                    log("HTTP BODY CAPTURE HOOK FAILED: " + e.getClass().getName());
                }
            }
        } catch (Throwable e) {
            log("HTTP BODY CAPTURE INSTALL FAILED: " + e.getClass().getName() + ": " + e.getMessage());
        }
    }

    private static void hookHConnectionResponse(java.net.HttpURLConnection connection) {
        try {
            if (connection == null) {
                return;
            }

            final Class<?> connectionClass = connection.getClass();
            log("HTTP LIVE CLASS: " + connectionClass.getName());

            int hooked = 0;
            for (Method method : connectionClass.getMethods()) {
                String name = method.getName();
                Class<?>[] params = method.getParameterTypes();

                if ((("getResponseCode".equals(name) || "getInputStream".equals(name)
                        || "getErrorStream".equals(name))
                        && params.length == 0)) {

                    final String methodName = name;
                    try {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                log("HTTP LIVE " + methodName + " ENTER url=" + safeConnectionUrl(param.thisObject));
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.hasThrowable()) {
                                    Throwable t = param.getThrowable();
                                    log("HTTP LIVE " + methodName + " THROW "
                                            + t.getClass().getName() + ": "
                                            + String.valueOf(t.getMessage()));
                                } else {
                                    Object result = param.getResult();
                                    String resultText;
                                    if (result == null) {
                                        resultText = "null";
                                    } else {
                                        resultText = result.getClass().getName() + ":" + String.valueOf(result);
                                        if (resultText.length() > 500) {
                                            resultText = resultText.substring(0, 500);
                                        }
                                    }
                                    log("HTTP LIVE " + methodName + " RESULT " + resultText);
                                }
                            }
                        });
                        hooked++;
                        log("hooked HTTP LIVE method: " + connectionClass.getName()
                                + "." + methodName + "()");
                    } catch (Throwable e) {
                        log("HTTP LIVE hook failed " + methodName + ": "
                                + e.getClass().getName());
                    }
                }
            }

            log("HTTP LIVE hooks installed=" + hooked
                    + " class=" + connectionClass.getName());
        } catch (Throwable e) {
            log("HTTP LIVE hook install THROW: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static String safeConnectionUrl(Object object) {
        try {
            if (object instanceof java.net.HttpURLConnection) {
                return String.valueOf(((java.net.HttpURLConnection) object).getURL());
            }
        } catch (Throwable ignored) {
        }
        return "<unknown>";
    }


    private static void probeHNetworkCall(Object hObject) {
        try {
            if (hObject == null) {
                log("H NETWORK PROBE: hObject=null");
                return;
            }

            Method dMethod = null;
            Class<?> cls = hObject.getClass().getSuperclass();
            int depth = 0;
            while (cls != null && cls != Object.class && depth < 6) {
                for (Method method : cls.getDeclaredMethods()) {
                    if ("d".equals(method.getName())
                            && method.getParameterTypes().length == 0) {
                        dMethod = method;
                        break;
                    }
                }
                if (dMethod != null) break;
                cls = cls.getSuperclass();
                depth++;
            }

            if (dMethod == null) {
                log("H NETWORK PROBE: zero-arg d() not found");
                return;
            }

            dMethod.setAccessible(true);
            log("H NETWORK PROBE: invoking " + dMethod.getDeclaringClass().getName()
                    + ".d() return=" + dMethod.getReturnType().getName());

            Object result = dMethod.invoke(hObject);
            if (result == null) {
                log("H NETWORK PROBE RESULT: null");
                return;
            }

            log("H NETWORK PROBE RESULT: " + result.getClass().getName());

            if (result instanceof java.net.HttpURLConnection) {
                java.net.HttpURLConnection connection =
                        (java.net.HttpURLConnection) result;
                try {
                    log("H NETWORK PROBE URL: " + connection.getURL());
                } catch (Throwable ignored) {
                }
                hookHConnectionResponse(connection);
            }
        } catch (Throwable e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            log("H NETWORK PROBE THROW: " + cause.getClass().getName()
                    + ": " + String.valueOf(cause.getMessage()));
        }
    }


    private static void hookReturnedPullObject(Object target) {
        try {
            final Class<?> cls = target.getClass();
            log("PullTask returned object class: " + cls.getName());

            for (Method method : cls.getDeclaredMethods()) {
                final String methodName = method.getName();
                final Class<?> returnType = method.getReturnType();

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("PullTask returned ENTER: " + cls.getName() + "." + methodName
                                + " args=" + formatHookArgs(param.args));
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            Throwable t = param.getThrowable();
                            log("PullTask returned THROW: " + methodName
                                    + " " + t.getClass().getName() + ": " + t.getMessage());
                        } else {
                            String text = String.valueOf(param.getResult());
                            if (text.length() > 500) text = text.substring(0, 500);
                            log("PullTask returned RESULT: " + methodName + "=" + text);
                            if ("d".equals(methodName) && param.getResult() != null) {
                                hookReturnedPullObject(param.getResult());
                            }
                        }
                    }
                });

                log("hooked PullTask returned method: " + cls.getName() + "."
                        + methodName + "(" + method.getParameterTypes().length
                        + " args) -> " + returnType.getName());
            }

            Class<?> parent = cls.getSuperclass();
            if (parent != null && parent != Object.class) {
                log("H OBJECT SUPERCLASS: " + parent.getName());

                Method[] parentMethods = parent.getDeclaredMethods();
                log("H SUPER METHODS=" + parentMethods.length);

                // H.u() directly calls j0.d(). Do not filter by the reflected
                // return type here: some optimized/obfuscated builds can expose
                // a different reflection type even though the bytecode call is
                // the zero-argument d() method we need to observe.
                for (Method method : parentMethods) {
                    final String methodName = method.getName();
                    final Class<?> returnType = method.getReturnType();

                    if ("d".equals(methodName)
                            && method.getParameterTypes().length == 0) {
                        final Method dMethod = method;
                        final Class<?> declaring = parent;
                        XposedBridge.hookMethod(dMethod, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                log("J0.D LIVE ENTER: " + declaring.getName()
                                        + ".d() return=" + dMethod.getReturnType().getName());
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.hasThrowable()) {
                                    Throwable t = param.getThrowable();
                                    log("J0.D LIVE THROW: " + t.getClass().getName()
                                            + ": " + String.valueOf(t.getMessage()));
                                    return;
                                }

                                Object result = param.getResult();
                                log("J0.D LIVE RESULT: "
                                        + (result == null ? "null" : result.getClass().getName()));
                                if (result instanceof java.net.HttpURLConnection) {
                                    java.net.HttpURLConnection connection =
                                            (java.net.HttpURLConnection) result;
                                    try {
                                        log("J0.D LIVE URL: " + connection.getURL());
                                    } catch (Throwable ignored) {
                                    }
                                    // Install hooks on the exact concrete connection
                                    // object/class returned by the real j0.d() call.
                                    hookConcreteHttpResponse(connection);
                            hookYellowPageResponseBodyCapture(connection);
                                    hookHConnectionResponse(connection);
                                }
                            }
                        });
                        log("hooked LIVE J0.d(): " + parent.getName()
                                + ".d() -> " + returnType.getName());
                    }

                    // Keep the generic superclass hooks for additional network
                    // methods and diagnostics.
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("PullTask returned BASE ENTER: " + parent.getName() + "."
                                    + methodName);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) {
                                Throwable t = param.getThrowable();
                                log("PullTask returned BASE THROW: " + methodName + " "
                                        + t.getClass().getName() + ": " + t.getMessage());
                            } else {
                                log("PullTask returned BASE RESULT: " + methodName + "="
                                        + String.valueOf(param.getResult()));
                            }
                        }
                    });

                    log("hooked PullTask returned base method: " + parent.getName() + "."
                            + methodName + "(" + method.getParameterTypes().length
                            + " args) -> " + returnType.getName());
                }
            }
        } catch (Throwable e) {
            log("PullTask returned-object hook failed: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }


    private static void hookGlobalHttpsConnection(ClassLoader cl) {
        try {
            String[] names = new String[] {
                    "com.android.okhttp.internal.huc.HttpsURLConnectionImpl",
                    "com.android.okhttp.internal.huc.HttpURLConnectionImpl"
            };
            int total = 0;
            for (String className : names) {
                try {
                    Class<?> cls = Class.forName(className, false, cl);
                    log("GLOBAL HTTP CLASS FOUND: " + className);
                    Class<?> current = cls;
                    int depth = 0;
                    while (current != null && current != Object.class && depth < 8) {
                        for (Method method : current.getDeclaredMethods()) {
                            String name = method.getName();
                            if (!("connect".equals(name)
                                    || "getResponseCode".equals(name)
                                    || "getResponseMessage".equals(name)
                                    || "getInputStream".equals(name)
                                    || "getErrorStream".equals(name))) {
                                continue;
                            }
                            if (method.getParameterTypes().length != 0) continue;
                            final String methodName = name;
                            final Class<?> declaring = current;
                            try {
                                XposedBridge.hookMethod(method, new XC_MethodHook() {
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param) {
                                        log("GLOBAL HTTP ENTER: " + methodName
                                                + " class=" + param.thisObject.getClass().getName()
                                                + " url=" + safeConnectionUrl(param.thisObject));
                                    }

                                    @Override
                                    protected void afterHookedMethod(MethodHookParam param) {
                                        if (param.hasThrowable()) {
                                            Throwable t = param.getThrowable();
                                            log("GLOBAL HTTP THROW: " + methodName + " "
                                                    + t.getClass().getName() + ": "
                                                    + String.valueOf(t.getMessage()));
                                        } else {
                                            Object result = param.getResult();
                                            String value = String.valueOf(result);
                                            if (value.length() > 800) value = value.substring(0, 800);
                                            log("GLOBAL HTTP RESULT: " + methodName + " -> " + value
                                                    + " resultClass="
                                                    + (result == null ? "null" : result.getClass().getName()));
                                        }
                                    }
                                });
                                total++;
                                log("GLOBAL HTTP HOOKED: " + declaring.getName() + "." + methodName);
                            } catch (Throwable e) {
                                log("GLOBAL HTTP HOOK FAILED: " + declaring.getName() + "."
                                        + methodName + " " + e.getClass().getName() + ": "
                                        + String.valueOf(e.getMessage()));
                            }
                        }
                        current = current.getSuperclass();
                        depth++;
                    }
                } catch (Throwable e) {
                    log("GLOBAL HTTP CLASS FAILED: " + className + " "
                            + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
                }
            }
            log("GLOBAL HTTP HOOKS INSTALLED=" + total);
        } catch (Throwable e) {
            log("GLOBAL HTTP INSTALL FAILED: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }


    private static void hookYellowPageHttpDecision(ClassLoader cl) {
        // Trace the real j0.k setter. H.u() returns 6 immediately for k values other than 0/1.
        try {
            Class<?> j0 = Class.forName("com.miui.yellowpage.utils.j0", false, cl);
            Method setter = j0.getDeclaredMethod("j", Integer.TYPE);
            XposedBridge.hookMethod(setter, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    int value = param.args != null && param.args.length > 0 && param.args[0] instanceof Integer
                            ? (Integer) param.args[0] : Integer.MIN_VALUE;
                    log("J0.K SET: value=" + value + " object="
                            + (param.thisObject == null ? "null" : param.thisObject.getClass().getName()));
                    if (value == -1) {
                        try {
                            StackTraceElement[] trace = Thread.currentThread().getStackTrace();
                            log("J0.K SET -1 STACK BEGIN thread="
                                    + Thread.currentThread().getName()
                                    + " object="
                                    + (param.thisObject == null ? "null"
                                    : param.thisObject.getClass().getName()));
                            int count = 0;
                            for (StackTraceElement element : trace) {
                                String frame = String.valueOf(element);
                                if (frame.contains("HookEntry")) continue;
                                log("J0.K SET -1 STACK[" + count + "]: " + frame);
                                if (++count >= 30) break;
                            }
                            log("J0.K SET -1 STACK END");
                        } catch (Throwable ignored) {
                        }
                    }
                }
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) {
                        Throwable t = param.getThrowable();
                        log("J0.K SET THROW: " + t.getClass().getName() + ": " + String.valueOf(t.getMessage()));
                    }
                }
            });
            log("hooked J0.k setter: j0.j(int) -> field k");
        } catch (Throwable e) {
            log("J0.k setter hook failed: " + e.getClass().getSimpleName()
                    + ": " + String.valueOf(e.getMessage()));
        }

        // Trace Q.a(Context), the second gate used by H.u() when k == 0.
        try {
            Class<?> q = Class.forName("Q.a", false, cl);
            Method qMethod = q.getDeclaredMethod("a", Context.class);
            if (!Modifier.isStatic(qMethod.getModifiers()) || qMethod.getReturnType() != Boolean.TYPE) {
                log("NETWORK GATE Q.a signature mismatch");
            } else {
                XposedBridge.hookMethod(qMethod, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            Throwable t = param.getThrowable();
                            log("NETWORK GATE: Q.a(Context) THROW: " + t.getClass().getName()
                                    + ": " + String.valueOf(t.getMessage()));
                        } else {
                            log("NETWORK GATE: Q.a(Context) -> " + String.valueOf(param.getResult()));
                        }
                    }
                });
                log("hooked NETWORK GATE: Q.a(Context)");
            }
        } catch (Throwable e) {
            log("NETWORK GATE Q.a hook failed: " + e.getClass().getSimpleName()
                    + ": " + String.valueOf(e.getMessage()));
        }

        try {
            Class<?> http = Class.forName("com.miui.yellowpage.utils.H", false, cl);
            // H extends the actual j0 networking class. Hook the concrete
            // superclass resolved from H itself, rather than relying only on
            // Class.forName("com.miui.yellowpage.utils.j0", cl). This makes the
            // diagnostic hook follow the exact class used by H.u().
            Class<?> networkBase = http.getSuperclass();
            int networkDepth = 0;
            while (networkBase != null && networkBase != Object.class && networkDepth < 6) {
                for (Method method : networkBase.getDeclaredMethods()) {
                    if ("d".equals(method.getName())
                            && method.getParameterTypes().length == 0
                            && java.net.HttpURLConnection.class.isAssignableFrom(method.getReturnType())) {
                        final Method dMethod = method;
                        final Class<?> declaring = networkBase;
                        XposedBridge.hookMethod(dMethod, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                log("J0.D DIRECT ENTER: " + declaring.getName()
                                        + " return=" + dMethod.getReturnType().getName());
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.hasThrowable()) {
                                    Throwable t = param.getThrowable();
                                    log("J0.D DIRECT THROW: " + t.getClass().getName()
                                            + ": " + String.valueOf(t.getMessage()));
                                    return;
                                }
                                Object result = param.getResult();
                                log("J0.D DIRECT RESULT: "
                                        + (result == null ? "null" : result.getClass().getName()));
                                if (result instanceof java.net.HttpURLConnection) {
                                    try {
                                        log("J0.D DIRECT URL: "
                                                + String.valueOf(((java.net.HttpURLConnection) result).getURL()));
                                    } catch (Throwable ignored) {
                                    }
                                }
                            }
                        });
                        log("hooked DIRECT J0.d(): " + declaring.getName());
                    }
                }
                networkBase = networkBase.getSuperclass();
                networkDepth++;
            }

            final String[] targets = new String[] {
                    "https://api.comm.miui.com/cspmisc/patch/info",
                    "https://global.api.huangye.miui.com/spbook/yellowpage/provider/info"
            };

            for (Method method : http.getDeclaredMethods()) {
                Class<?>[] p = method.getParameterTypes();

                if ("z".equals(method.getName())
                        && method.getReturnType() == Boolean.TYPE
                        && p.length == 1
                        && p[0] == String.class) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            String url = (String) param.args[0];
                            Object original = param.getResult();
                            boolean target = false;
                            for (String item : targets) {
                                if (item.equals(url)) {
                                    target = true;
                                    break;
                                }
                            }
                            if (target) {
                                log("HTTP decision z: " + url + " original=" + original
                                        + " -> KEEP ORIGINAL");
                            } else {
                                log("HTTP decision z: " + url + " original=" + original);
                            }
                        }
                    });
                    log("hooked YellowPage HTTP decision: H.z(String)");
                }

                if ("B".equals(method.getName())
                        && p.length == 2
                        && p[0] == String.class
                        && p[1] == Boolean.TYPE) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("HTTP request B ENTER: url=" + String.valueOf(param.args[0])
                                    + " flag=" + String.valueOf(param.args[1]));
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) {
                                Throwable t = param.getThrowable();
                                log("HTTP request B THROW: " + t.getClass().getName()
                                        + ": " + String.valueOf(t.getMessage()));
                            } else {
                                Object result = param.getResult();
                                log("HTTP request B RESULT: " + String.valueOf(result)
                                        + " class=" + (result == null ? "null" : result.getClass().getName()));
                                if (result != null) {
                                    hookReturnedPullObject(result);
                                }
                            }
                        }
                    });
                    log("hooked YellowPage HTTP request: H.B(String,boolean)");
                }
            }
        } catch (Throwable e) {
            log("YellowPage HTTP decision hook failed: " + e.getClass().getSimpleName());
        }
    }


    private static void hookYellowPageHttpBase(ClassLoader cl) {
        try {
            Class<?> base = Class.forName("com.miui.yellowpage.utils.j0", false, cl);
            int found = 0;
            for (Method method : base.getDeclaredMethods()) {
                final String methodName = method.getName();
                final int argCount = method.getParameterTypes().length;

                if (!("d".equals(methodName) || "e".equals(methodName)
                        || "f".equals(methodName) || "g".equals(methodName)
                        || "h".equals(methodName) || "b".equals(methodName)
                        || "c".equals(methodName))) {
                    continue;
                }

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("HTTP BASE ENTER: j0." + methodName
                                + " args=" + formatHookArgs(param.args));
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            Throwable t = param.getThrowable();
                            log("HTTP BASE THROW: j0." + methodName
                                    + " " + t.getClass().getName()
                                    + ": " + String.valueOf(t.getMessage()));
                            return;
                        }

                        Object result = param.getResult();
                        String text = String.valueOf(result);
                        if (text.length() > 1000) {
                            text = text.substring(0, 1000);
                        }
                        log("HTTP BASE RESULT: j0." + methodName
                                + " -> " + text
                                + " class=" + (result == null
                                ? "null" : result.getClass().getName()));

                        if (result instanceof java.net.HttpURLConnection) {
                            hookLiveResponseCodeFromObject(result);
                            try {
                                java.net.HttpURLConnection conn =
                                        (java.net.HttpURLConnection) result;
                                log("HTTP BASE CONNECTION: url="
                                        + String.valueOf(conn.getURL()));
                                hookLiveHttpObject(conn);
                                hookLiveHttpAllMethods(conn);
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                });
                found++;
                log("hooked HTTP BASE: j0." + methodName
                        + "(" + argCount + " args)");
            }
            log("HTTP BASE hooks installed: " + found);
        } catch (Throwable e) {
            log("HTTP BASE hook failed: " + e.getClass().getSimpleName());
        }
    }


    private static void hookLiveResponseCodeFromObject(final Object connection) {
        try {
            if (connection == null) return;
            Class<?> current = connection.getClass();
            int depth = 0;
            int found = 0;
            while (current != null && current != Object.class && depth < 8) {
                for (Method method : current.getDeclaredMethods()) {
                    if (!"getResponseCode".equals(method.getName())
                            || method.getParameterTypes().length != 0
                            || method.getReturnType() != Integer.TYPE) {
                        continue;
                    }
                    try {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                log("LIVE RESPONSE ENTER: getResponseCode");
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.hasThrowable()) {
                                    Throwable t = param.getThrowable();
                                    log("LIVE RESPONSE THROW: getResponseCode "
                                            + t.getClass().getName() + ": "
                                            + String.valueOf(t.getMessage()));
                                    return;
                                }
                                log("LIVE RESPONSE CODE: " + String.valueOf(param.getResult()));
                            }
                        });
                        found++;
                        log("LIVE RESPONSE HOOKED: "
                                + current.getName() + ".getResponseCode()");
                    } catch (Throwable e) {
                        log("LIVE RESPONSE HOOK FAILED: "
                                + current.getName() + ".getResponseCode "
                                + e.getClass().getSimpleName());
                    }
                }
                current = current.getSuperclass();
                depth++;
            }
            log("LIVE RESPONSE HOOKS INSTALLED: " + found
                    + " class=" + connection.getClass().getName());
        } catch (Throwable e) {
            log("LIVE RESPONSE OBJECT HOOK FAILED: "
                    + e.getClass().getSimpleName());
        }
    }

    private static void hookYellowPageLiveHttp(ClassLoader cl) {
        try {
            Class<?> conn = Class.forName(
                    "com.android.okhttp.internal.huc.HttpsURLConnectionImpl",
                    false, cl);
            int found = 0;
            Class<?> current = conn;
            int depth = 0;
            while (current != null && current != Object.class && depth < 6) {
            for (Method method : current.getDeclaredMethods()) {
                final String name = method.getName();
                if (!"connect".equals(name)
                        && !"getResponseCode".equals(name)
                        && !"getResponseMessage".equals(name)
                        && !"getInputStream".equals(name)
                        && !"getErrorStream".equals(name)
                        && !"getContent".equals(name)) {
                    continue;
                }

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object target = param.thisObject;
                            java.net.HttpURLConnection c =
                                    target instanceof java.net.HttpURLConnection
                                            ? (java.net.HttpURLConnection) target : null;
                            log("LIVE HTTP ENTER: " + name
                                    + " url=" + (c == null ? "?" : String.valueOf(c.getURL())));
                        } catch (Throwable e) {
                            log("LIVE HTTP ENTER: " + name);
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            Throwable t = param.getThrowable();
                            log("LIVE HTTP THROW: " + name + " "
                                    + t.getClass().getName() + ": "
                                    + String.valueOf(t.getMessage()));
                            return;
                        }
                        Object result = param.getResult();
                        String text = String.valueOf(result);
                        if (text.length() > 1500) text = text.substring(0, 1500);
                        log("LIVE HTTP RESULT: " + name + " -> " + text
                                + " class=" + (result == null
                                ? "null" : result.getClass().getName()));

                        if ("getResponseCode".equals(name)) {
                            log("LIVE HTTP RESPONSE CODE: " + String.valueOf(result));
                        }
                    }
                });
                found++;
                log("hooked LIVE HTTP: " + current.getName() + "." + name
                        + "(" + method.getParameterTypes().length + " args)");
            }
            current = current.getSuperclass();
            depth++;
            }
            log("LIVE HTTP hooks installed: " + found);
        } catch (Throwable e) {
            log("LIVE HTTP hook failed: " + e.getClass().getSimpleName());
        }
    }


    private static void hookLiveHttpObject(Object connection) {
        try {
            if (connection == null) return;
            final Class<?> cls = connection.getClass();
            log("LIVE OBJECT CLASS: " + cls.getName());

            Class<?> current = cls;
            int depth = 0;
            while (current != null && current != Object.class && depth < 5) {
                for (Method method : current.getDeclaredMethods()) {
                    final String name = method.getName();
                    if (!"connect".equals(name)
                            && !"getResponseCode".equals(name)
                            && !"getResponseMessage".equals(name)
                            && !"getInputStream".equals(name)
                            && !"getErrorStream".equals(name)) {
                        continue;
                    }
                    if (method.getParameterTypes().length != 0) continue;

                    try {
                        XposedBridge.hookMethod(method, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    java.net.HttpURLConnection c =
                                            (java.net.HttpURLConnection) param.thisObject;
                                    log("LIVE OBJECT ENTER: " + name
                                            + " url=" + String.valueOf(c.getURL()));
                                } catch (Throwable e) {
                                    log("LIVE OBJECT ENTER: " + name);
                                }
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.hasThrowable()) {
                                    Throwable t = param.getThrowable();
                                    log("LIVE OBJECT THROW: " + name + " "
                                            + t.getClass().getName() + ": "
                                            + String.valueOf(t.getMessage()));
                                    return;
                                }

                                Object result = param.getResult();
                                String text = String.valueOf(result);
                                if (text.length() > 2000) text = text.substring(0, 2000);
                                log("LIVE OBJECT RESULT: " + name + " -> " + text
                                        + " class=" + (result == null
                                        ? "null" : result.getClass().getName()));

                                if ("getResponseCode".equals(name)) {
                                    log("LIVE RESPONSE CODE: " + String.valueOf(result));
                                }
                            }
                        });
                        log("hooked LIVE OBJECT: " + current.getName() + "." + name);
                    } catch (Throwable e) {
                        log("LIVE OBJECT hook failed: " + current.getName() + "." + name
                                + " " + e.getClass().getSimpleName());
                    }
                }
                current = current.getSuperclass();
                depth++;
            }
        } catch (Throwable e) {
            log("LIVE object hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static void hookLiveHttpAllMethods(Object connection) {
        try {
            if (connection == null) return;
            Class<?> current = connection.getClass();
            int depth = 0;
            log("LIVE ALL CLASS: " + current.getName());

            while (current != null && current != Object.class && depth < 6) {
                for (Method method : current.getDeclaredMethods()) {
                    final Method hookMethod = method;
                    final String name = hookMethod.getName();
                    if (hookMethod.isSynthetic() || hookMethod.isBridge()) continue;
                    try {
                        XposedBridge.hookMethod(hookMethod, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                log("LIVE ALL ENTER: " + name
                                        + " args=" + formatHookArgs(param.args));
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                if (param.hasThrowable()) {
                                    Throwable t = param.getThrowable();
                                    log("LIVE ALL THROW: " + name + " "
                                            + t.getClass().getName() + ": "
                                            + String.valueOf(t.getMessage()));
                                } else {
                                    Object result = param.getResult();
                                    String text = String.valueOf(result);
                                    if (text.length() > 1200) text = text.substring(0, 1200);
                                    log("LIVE ALL RESULT: " + name + " -> " + text);
                                }
                            }
                        });
                    } catch (Throwable ignored) {
                    }
                }
                current = current.getSuperclass();
                depth++;
            }
            log("LIVE ALL hooks installed");
        } catch (Throwable e) {
            log("LIVE ALL hook failed: " + e.getClass().getSimpleName());
        }
    }



    private static void hookYellowPageResponseSurface(ClassLoader cl) {
        try {
            Class<?> http = Class.forName("com.miui.yellowpage.utils.H", false, cl);
            int found = 0;
            for (Method method : http.getDeclaredMethods()) {
                final String name = method.getName();
                if (!"A".equals(name) || method.getParameterTypes().length != 0) {
                    continue;
                }
                if (method.getReturnType() != String.class
                        && method.getReturnType() != byte[].class) {
                    continue;
                }

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("HTTP RESPONSE SURFACE ENTER: H." + name + "()");
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            Throwable t = param.getThrowable();
                            log("HTTP RESPONSE SURFACE THROW: H." + name + " "
                                    + t.getClass().getName() + ": " + String.valueOf(t.getMessage()));
                            return;
                        }

                        Object result = param.getResult();
                        if (result instanceof byte[]) {
                            byte[] data = (byte[]) result;
                            int n = Math.min(data.length, 1200);
                            String text;
                            try {
                                text = new String(data, java.nio.charset.StandardCharsets.UTF_8);
                            } catch (Throwable ignored) {
                                text = "<binary>";
                            }
                            if (text.length() > 1200) text = text.substring(0, 1200);
                            log("HTTP RESPONSE SURFACE RESULT: H." + name
                                    + " bytes=" + data.length + " body=" + text);
                        } else {
                            String text = String.valueOf(result);
                            if (text.length() > 2000) text = text.substring(0, 2000);
                            log("HTTP RESPONSE SURFACE RESULT: H." + name
                                    + " -> " + text);
                        }
                    }
                });
                found++;
                log("hooked HTTP RESPONSE SURFACE: H." + name
                        + "() -> " + method.getReturnType().getName());
            }
            log("HTTP RESPONSE SURFACE hooks installed: " + found);
        } catch (Throwable e) {
            log("HTTP RESPONSE SURFACE hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static void hookYellowPageStreamUtility(ClassLoader cl) {
        try {
            Class<?> x = Class.forName("com.miui.yellowpage.utils.x", false, cl);
            int found = 0;
            for (Method method : x.getDeclaredMethods()) {
                final Method target = method;
                final String name = method.getName();
                if (!"i".equals(name) && !"j".equals(name)) continue;

                XposedBridge.hookMethod(target, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("STREAM UTIL ENTER: x." + name
                                + " args=" + formatHookArgs(param.args)
                                + " this=" + String.valueOf(param.thisObject));
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            Throwable t = param.getThrowable();
                            log("STREAM UTIL THROW: x." + name + " "
                                    + t.getClass().getName() + ": "
                                    + String.valueOf(t.getMessage()));
                            return;
                        }

                        Object result = param.getResult();
                        log("STREAM UTIL RESULT: x." + name
                                + " -> " + String.valueOf(result)
                                + " resultClass="
                                + (result == null ? "null" : result.getClass().getName()));

                        /*
                         * The last confirmed failure is x.j(...)->false.
                         * x.i() delegates to x.j(), and o0.d.p() aborts when the
                         * resulting boolean is false.  At this point the server has
                         * already returned valid patch metadata and the CDN URL is
                         * known, so reproduce the small file-copy operation here.
                         *
                         * x.j signature observed at runtime:
                         *   (Context, String url, String file, Map, int) -> boolean
                         *
                         * Only touch the YellowPage spam CDN path.  This keeps the
                         * hook from interfering with unrelated x.j() callers.
                         */
                        if ("j".equals(name)
                                && Boolean.FALSE.equals(result)
                                && param.args != null
                                && param.args.length == 5
                                && param.args[1] instanceof String
                                && param.args[2] instanceof String) {
                            String urlString = (String) param.args[1];
                            String filePath = (String) param.args[2];

                            if (urlString.contains("/yellowpage/yp-spam/")) {
                                java.io.File outFile = new java.io.File(filePath);
                                java.io.File parent = outFile.getParentFile();

                                log("STREAM UTIL DIRECT DOWNLOAD: url=" + urlString
                                        + " file=" + filePath);

                                java.net.HttpURLConnection http = null;
                                java.io.InputStream in = null;
                                java.io.FileOutputStream out = null;

                                try {
                                    if (parent != null && !parent.exists() && !parent.mkdirs()
                                            && !parent.exists()) {
                                        throw new java.io.IOException(
                                                "cannot create parent: " + parent);
                                    }

                                    java.net.URL url = new java.net.URL(urlString);
                                    java.net.URLConnection connection = url.openConnection();
                                    if (!(connection instanceof java.net.HttpURLConnection)) {
                                        throw new java.io.IOException(
                                                "not HttpURLConnection: "
                                                        + connection.getClass().getName());
                                    }

                                    http = (java.net.HttpURLConnection) connection;
                                    http.setConnectTimeout(15000);
                                    http.setReadTimeout(30000);
                                    http.setInstanceFollowRedirects(true);
                                    http.setRequestMethod("GET");
                                    http.setRequestProperty("Accept-Encoding", "identity");
                                    http.setRequestProperty("User-Agent",
                                            "MiuiYellowPage/1.0");

                                    int code = http.getResponseCode();
                                    long contentLength = http.getContentLengthLong();
                                    log("STREAM UTIL DIRECT HTTP: code=" + code
                                            + " length=" + contentLength
                                            + " contentType=" + http.getContentType());

                                    if (code < 200 || code >= 300) {
                                        throw new java.io.IOException(
                                                "HTTP " + code);
                                    }

                                    in = http.getInputStream();
                                    if (in == null) {
                                        throw new java.io.IOException("getInputStream()=null");
                                    }

                                    out = new java.io.FileOutputStream(outFile, false);
                                    java.security.MessageDigest md =
                                            java.security.MessageDigest.getInstance("MD5");

                                    byte[] buffer = new byte[32768];
                                    long total = 0;
                                    int n;
                                    while ((n = in.read(buffer)) != -1) {
                                        if (n == 0) continue;
                                        out.write(buffer, 0, n);
                                        md.update(buffer, 0, n);
                                        total += n;
                                    }
                                    out.flush();
                                    out.close();
                                    out = null;

                                    StringBuilder md5 = new StringBuilder(32);
                                    for (byte value : md.digest()) {
                                        md5.append(String.format(java.util.Locale.US,
                                                "%02x", value & 0xff));
                                    }

                                    log("STREAM UTIL DIRECT DOWNLOAD OK: bytes=" + total
                                            + " md5=" + md5
                                            + " fileLength=" + outFile.length());

                                    if (total <= 0 || !outFile.isFile() || outFile.length() != total) {
                                        throw new java.io.IOException(
                                                "download size invalid: " + total);
                                    }

                                    param.setResult(Boolean.TRUE);
                                    log("STREAM UTIL FORCE RESULT: x.j false -> true");
                                } catch (Throwable e) {
                                    try {
                                        if (out != null) out.close();
                                    } catch (Throwable ignored) {
                                    }
                                    try {
                                        if (outFile.exists()) outFile.delete();
                                    } catch (Throwable ignored) {
                                    }
                                    log("STREAM UTIL DIRECT DOWNLOAD FAILED: "
                                            + e.getClass().getName() + ": "
                                            + String.valueOf(e.getMessage()));
                                } finally {
                                    try {
                                        if (in != null) in.close();
                                    } catch (Throwable ignored) {
                                    }
                                    if (http != null) {
                                        http.disconnect();
                                    }
                                }
                            }
                        }

                        if ("i".equals(name) && param.getResult() == null) {
                            // x.i() is the final stream acquisition point used by
                            // o0.d.p -> s0.t. If the obfuscated helper returns null
                            // despite a live HTTP 200/content-length response, expose
                            // the exact j0.d()/getInputStream path without changing it.
                            try {
                                Object owner = param.thisObject;
                                if (owner != null) {
                                    Method d = owner.getClass().getMethod("d");
                                    Object conn = d.invoke(owner);
                                    if (conn instanceof java.net.HttpURLConnection) {
                                        java.net.HttpURLConnection http =
                                                (java.net.HttpURLConnection) conn;
                                        log("STREAM UTIL FALLBACK CONNECTION: "
                                                + http.getClass().getName()
                                                + " url=" + String.valueOf(http.getURL()));
                                        java.io.InputStream in = http.getInputStream();
                                        if (in != null) {
                                            param.setResult(in);
                                            log("STREAM UTIL FALLBACK: x.i null -> getInputStream()");
                                        }
                                    }
                                }
                            } catch (Throwable e) {
                                log("STREAM UTIL FALLBACK FAILED: "
                                        + e.getClass().getName() + ": "
                                        + String.valueOf(e.getMessage()));
                            }
                        }
                    }
                });
                found++;
                log("hooked STREAM UTIL: x." + method.toGenericString());
            }
            log("STREAM UTIL hooks installed=" + found);
        } catch (Throwable e) {
            log("STREAM UTIL hook failed: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static void hookYellowPageStreamRequest(ClassLoader cl) {
        try {
            Class<?> stream = Class.forName("com.miui.yellowpage.utils.s0", false, cl);
            int found = 0;

            for (Method method : stream.getDeclaredMethods()) {
                final String name = method.getName();
                Class<?>[] p = method.getParameterTypes();
                if (!(("s".equals(name) && p.length == 1)
                        || ("t".equals(name) && p.length == 2))) {
                    continue;
                }
                if (p[0] != java.io.OutputStream.class
                        || ("t".equals(name) && p[1] != java.util.Map.class)
                        || method.getReturnType() != Integer.TYPE) {
                    continue;
                }

                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("STREAM REQUEST ENTER: s0." + name
                                + " args=" + formatHookArgs(param.args));
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            Throwable t = param.getThrowable();
                            log("STREAM REQUEST THROW: s0." + name + " "
                                    + t.getClass().getName() + ": " + String.valueOf(t.getMessage()));
                            return;
                        }

                        Object out = param.args != null && param.args.length > 0
                                ? param.args[0] : null;
                        int code = param.getResult() instanceof Integer
                                ? (Integer) param.getResult() : -1;

                        if (out instanceof java.io.ByteArrayOutputStream) {
                            try {
                                byte[] data = ((java.io.ByteArrayOutputStream) out).toByteArray();
                                String body = new String(
                                        data, java.nio.charset.StandardCharsets.UTF_8);
                                if (body.length() > 2000) body = body.substring(0, 2000);
                                log("STREAM REQUEST RESULT: s0." + name
                                        + " code=" + code
                                        + " bytes=" + data.length
                                        + " body=" + body);
                            } catch (Throwable e) {
                                log("STREAM REQUEST RESULT: s0." + name
                                        + " code=" + code + " body-read-failed="
                                        + e.getClass().getSimpleName());
                            }
                        } else {
                            log("STREAM REQUEST RESULT: s0." + name
                                    + " code=" + code
                                    + " output=" + String.valueOf(out));
                        }
                    }
                });
                found++;
                log("hooked STREAM REQUEST: s0." + name
                        + "(" + p.length + " args)");
            }

            log("STREAM REQUEST hooks installed: " + found);
        } catch (Throwable e) {
            log("STREAM REQUEST hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static void hookCriticalYellowPageGates(ClassLoader cl) {
        log("CRITICAL GATES INSTALL START");
        try {
            Class<?> j0 = Class.forName("com.miui.yellowpage.utils.j0", false, cl);
            int found = 0;
            for (Method m : j0.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (!"j".equals(m.getName()) || p.length != 1 || p[0] != Integer.TYPE) continue;
                final Method target = m;
                XposedBridge.hookMethod(target, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Object v = param.args != null && param.args.length > 0 ? param.args[0] : null;
                        log("CRITICAL J0.j ENTER value=" + String.valueOf(v)
                                + " return=" + target.getReturnType().getName());
                        if (Integer.valueOf(-1).equals(v)) {
                            log("CRITICAL J0.j VALUE=-1");
                            StackTraceElement[] trace = Thread.currentThread().getStackTrace();
                            StringBuilder out = new StringBuilder("CRITICAL J0.j -1 STACK:");
                            int n = 0;
                            for (StackTraceElement e : trace) {
                                if (String.valueOf(e).contains("HookEntry")) continue;
                                out.append(" | ").append(String.valueOf(e));
                                if (++n >= 12) break;
                            }
                            log(out.toString());
                        }
                    }
                });
                found++;
                log("CRITICAL J0 SETTER HOOKED: " + m.toGenericString());
            }
            log("CRITICAL J0 SETTER COUNT=" + found);
        } catch (Throwable e) {
            log("CRITICAL J0 SETTER FAILED: " + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
        }

        try {
            Class<?> q = Class.forName("Q.a", false, cl);
            int found = 0;
            for (Method m : q.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (!"a".equals(m.getName()) || !Modifier.isStatic(m.getModifiers())
                        || m.getReturnType() != Boolean.TYPE
                        || p.length != 1 || p[0] != Context.class) continue;
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("CRITICAL Q.a ENTER");
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            log("CRITICAL Q.a THROW: " + param.getThrowable().getClass().getName()
                                    + ": " + String.valueOf(param.getThrowable().getMessage()));
                        } else {
                            Object result = param.getResult();
                            if (Boolean.FALSE.equals(result) && isYellowPageContext(param.args)) {
                                param.setResult(true);
                                log("NETWORK GATE BYPASS: Q.a false -> true");
                            } else {
                                log("CRITICAL Q.a RESULT=" + String.valueOf(result));
                            }
                        }
                    }
                });
                found++;
                log("CRITICAL Q.a HOOKED: " + m.toGenericString());
            }
            log("CRITICAL Q.a COUNT=" + found);
        } catch (Throwable e) {
            log("CRITICAL Q.a FAILED: " + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
        }
        log("CRITICAL GATES INSTALL END");
    }

    private static String safeCanonicalPath(java.io.File file) {
        try {
            return file.getCanonicalPath();
        } catch (Throwable e) {
            return "<canonical-error:" + e.getClass().getSimpleName() + ">";
        }
    }

    private static String shellQuote(String value) {
        if (value == null) return "''";
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String runRootCommand(String command) {
        Process process = null;
        java.io.InputStream in = null;
        java.io.InputStream err = null;
        java.io.ByteArrayOutputStream outBytes = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream errBytes = new java.io.ByteArrayOutputStream();
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            in = process.getInputStream();
            err = process.getErrorStream();

            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) != -1) {
                outBytes.write(buffer, 0, n);
            }
            while ((n = err.read(buffer)) != -1) {
                errBytes.write(buffer, 0, n);
            }

            int exit = process.waitFor();
            String stdout = outBytes.toString("UTF-8").trim();
            String stderr = errBytes.toString("UTF-8").trim();

            log("ROOT CMD exit=" + exit
                    + " stdout=" + (stdout.length() > 300 ? stdout.substring(0, 300) : stdout)
                    + " stderr=" + (stderr.length() > 300 ? stderr.substring(0, 300) : stderr));

            return exit == 0 ? stdout : null;
        } catch (Throwable e) {
            log("ROOT CMD FAILED: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
            return null;
        } finally {
            try {
                if (in != null) in.close();
            } catch (Throwable ignored) {
            }
            try {
                if (err != null) err.close();
            } catch (Throwable ignored) {
            }
            if (process != null) {
                try {
                    process.destroy();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean recoverYellowPageMove(Object[] args) {
        try {
            java.io.File source = null;
            java.io.File target = null;

            if (args != null) {
                for (Object arg : args) {
                    if (!(arg instanceof String)) continue;
                    String s = (String) arg;
                    if (s.contains(".yellow_pages.dat.tmp")) {
                        java.io.File f = new java.io.File(s);
                        if (f.isFile()) source = f;
                    } else if (s.endsWith("yellow_pages.dat")) {
                        target = new java.io.File(s);
                    }
                }
            }

            if (source == null) {
                source = new java.io.File(
                        "/data/user/0/com.miui.yellowpage/files/.yellow_pages.dat.tmp");
            }
            if (target == null) {
                target = new java.io.File(
                        "/data/user/0/com.miui.yellowpage/files/yellowpage/yellow_pages.dat");
            }

            if (!source.isFile()) {
                log("MOVE RECOVERY: source missing: " + source);
                return false;
            }

            java.io.File parent = target.getParentFile();
            log("MOVE RECOVERY TARGET: path=" + safeCanonicalPath(target)
                    + " exists=" + target.exists()
                    + " file=" + target.isFile()
                    + " dir=" + target.isDirectory()
                    + " canRead=" + target.canRead()
                    + " canWrite=" + target.canWrite()
                    + " length=" + target.length());
            log("MOVE RECOVERY PARENT: " + String.valueOf(parent)
                    + " exists=" + (parent != null && parent.exists())
                    + " dir=" + (parent != null && parent.isDirectory())
                    + " canWrite=" + (parent != null && parent.canWrite())
                    + " canExecute=" + (parent != null && parent.canExecute()));

            if (target.exists() && target.isDirectory()) {
                log("MOVE RECOVERY: target is directory");
                return false;
            }

            // First keep the normal in-process path for devices where the target
            // is writable. This avoids invoking su unnecessarily.
            if (!target.exists() && source.renameTo(target)) {
                log("MOVE RECOVERY OK: renameTo, bytes=" + target.length());
                return target.isFile() && target.length() > 0;
            }

            // The EEA build can leave an existing target that the YellowPage UID
            // can read but cannot replace. Probe the actual root channel and, when
            // available, perform the final replacement outside the app sandbox.
            String rootId = runRootCommand("id");
            if (rootId == null || !rootId.contains("uid=0")) {
                log("MOVE RECOVERY: root unavailable");
                return false;
            }

            log("MOVE RECOVERY: root available; replacing target");

            String sourcePath = source.getAbsolutePath();
            String targetPath = target.getAbsolutePath();

            String replaceCommand = "rm -f " + shellQuote(targetPath)
                    + " && mv -f " + shellQuote(sourcePath)
                    + " " + shellQuote(targetPath);

            String replaceResult = runRootCommand(replaceCommand);
            if (replaceResult == null && target.isFile() && target.length() > 0) {
                // Some su implementations produce no stdout on success; verify
                // the actual filesystem result rather than relying only on output.
                log("MOVE RECOVERY: root command returned failure");
                return false;
            }

            // Restore the device's expected SELinux label if restorecon is present.
            // Failure here is non-fatal; the actual replacement is already complete.
            runRootCommand("restorecon " + shellQuote(targetPath));

            boolean valid = target.isFile() && target.length() > 0 && !source.exists();
            log("MOVE RECOVERY ROOT RESULT: valid=" + valid
                    + " targetLength=" + target.length()
                    + " sourceExists=" + source.exists());

            if (valid) {
                return true;
            }

            log("MOVE RECOVERY: root replacement did not produce valid target");
            return false;
        } catch (Throwable e) {
            log("MOVE RECOVERY FAILED: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
            return false;
        }
    }

    private static void hookYellowPageDownload(ClassLoader cl) {
        try {
            Class<?> d = Class.forName("o0.d", false, cl);
            int found = 0;
            for (Method method : d.getDeclaredMethods()) {
                if (!"p".equals(method.getName())) continue;
                final Method target = method;
                XposedBridge.hookMethod(target, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("DOWNLOAD ENTER: o0.d.p args=" + formatHookArgs(param.args));
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            Throwable t = param.getThrowable();
                            log("DOWNLOAD THROW: o0.d.p " + t.getClass().getName()
                                    + ": " + String.valueOf(t.getMessage()));
                            Throwable c = t.getCause();
                            int depth = 0;
                            while (c != null && depth++ < 5) {
                                log("DOWNLOAD CAUSE[" + depth + "]: " + c.getClass().getName()
                                        + ": " + String.valueOf(c.getMessage()));
                                c = c.getCause();
                            }
                        } else {
                            log("DOWNLOAD RESULT: o0.d.p -> " + String.valueOf(param.getResult()));
                        }
                    }
                });
                found++;
            }
            log("DOWNLOAD o0.d.p hooks installed=" + found);

            // The CDN file is now downloaded successfully, but o0.d.q() fails
            // while moving .yellow_pages.dat.tmp to the final yellow_pages.dat.
            // Recover that exact filesystem transition and suppress only the
            // known "failed to move" exception.
            try {
                for (Method method : d.getDeclaredMethods()) {
                    if (!"q".equals(method.getName())) continue;
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("MOVE ENTER: o0.d.q args=" + formatHookArgs(param.args));
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!param.hasThrowable()) {
                                log("MOVE RESULT: o0.d.q -> " + String.valueOf(param.getResult()));
                                return;
                            }

                            Throwable t = param.getThrowable();
                            log("MOVE THROW: o0.d.q " + t.getClass().getName()
                                    + ": " + String.valueOf(t.getMessage()));

                            if (String.valueOf(t.getMessage()).contains("failed to move")
                                    && recoverYellowPageMove(param.args)) {
                                param.setThrowable(null);
                                log("MOVE RECOVERY: suppressed o0.d.q failure");
                            }
                        }
                    });
                    log("MOVE hook installed: " + method.toGenericString());
                }
            } catch (Throwable e) {
                log("MOVE hook install failed: " + e.getClass().getName()
                        + ": " + String.valueOf(e.getMessage()));
            }

            // o0.d.p wraps the underlying transport exception as a generic
            // "failed to download file". Hook URL.openConnection and the
            // connection surface to expose the real CDN failure.
            Class<?> url = Class.forName("java.net.URL", false, ClassLoader.getSystemClassLoader());
            for (Method method : url.getDeclaredMethods()) {
                if (!"openConnection".equals(method.getName())) continue;
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            Object u = param.thisObject;
                            String text = String.valueOf(u);
                            if (text.contains("yp_spam") || text.contains("yellowpage")) {
                                log("DOWNLOAD URL.OPEN: " + text);
                            }
                        } catch (Throwable ignored) {}
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            log("DOWNLOAD URL.OPEN THROW: "
                                    + param.getThrowable().getClass().getName() + ": "
                                    + String.valueOf(param.getThrowable().getMessage()));
                            return;
                        }
                        Object r = param.getResult();
                        if (r != null) {
                            String text = String.valueOf(r);
                            if (text.contains("yp_spam") || text.contains("yellowpage")) {
                                log("DOWNLOAD URL.OPEN RESULT: " + r.getClass().getName());
                            }
                        }
                    }
                });
            }
            log("DOWNLOAD java.net.URL.openConnection hooks installed");

            Class<?> uc = Class.forName("java.net.URLConnection", false,
                    ClassLoader.getSystemClassLoader());
            String[] names = new String[]{"connect", "getInputStream", "getResponseCode",
                    "getContentLength", "getContentLengthLong"};
            int connectionHooks = 0;
            Class<?> current = uc;
            while (current != null) {
                for (Method method : current.getDeclaredMethods()) {
                    boolean match = false;
                    for (String name : names) {
                        if (name.equals(method.getName())) {
                            match = true;
                            break;
                        }
                    }
                    if (!match) continue;
                    if (Modifier.isAbstract(method.getModifiers())) continue;
                    final Method target = method;
                    XposedBridge.hookMethod(target, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                String u = String.valueOf(param.thisObject);
                                if (u.contains("yp_spam") || u.contains("yellowpage")) {
                                    log("DOWNLOAD HTTP ENTER: " + target.getName()
                                            + " class=" + param.thisObject.getClass().getName()
                                            + " url=" + u);
                                }
                            } catch (Throwable ignored) {}
                        }
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                String u = String.valueOf(param.thisObject);
                                if (!(u.contains("yp_spam") || u.contains("yellowpage"))) return;
                                if (param.hasThrowable()) {
                                    Throwable t = param.getThrowable();
                                    log("DOWNLOAD HTTP THROW: " + target.getName() + " "
                                            + t.getClass().getName() + ": "
                                            + String.valueOf(t.getMessage()));
                                    Throwable cause = t.getCause();
                                    if (cause != null) {
                                        log("DOWNLOAD HTTP CAUSE: " + cause.getClass().getName()
                                                + ": " + String.valueOf(cause.getMessage()));
                                    }
                                } else {
                                    log("DOWNLOAD HTTP RESULT: " + target.getName()
                                            + " -> " + String.valueOf(param.getResult()));
                                }
                            } catch (Throwable ignored) {}
                        }
                    });
                    connectionHooks++;
                }
                current = current.getSuperclass();
            }
            log("DOWNLOAD URLConnection hooks installed=" + connectionHooks);
        } catch (Throwable e) {
            log("DOWNLOAD hook failed: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static void hookYellowPageDataDecode(ClassLoader cl) {
        try {
            // Trace every method in the post-response pipeline that receives the
            // server payload. We intentionally do not alter the returned data yet.
            String[] classes = new String[] {
                    "o0.g",
                    "o0.AbstractC0381d",
                    "o0.d",
                    "n0.d",
                    "com.miui.yellowpage.utils.H",
                    "com.miui.yellowpage.utils.j0"
            };
            int hooked = 0;
            for (String className : classes) {
                try {
                    Class<?> cls = Class.forName(className, false, cl);
                    for (Method method : cls.getDeclaredMethods()) {
                        Class<?>[] p = method.getParameterTypes();
                        boolean payloadArg = false;
                        for (Class<?> type : p) {
                            if (type == String.class || type == java.io.InputStream.class
                                    || type == org.json.JSONObject.class
                                    || type == byte[].class) {
                                payloadArg = true;
                                break;
                            }
                        }
                        if (!payloadArg) continue;

                        final Method target = method;
                        XposedBridge.hookMethod(target, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    StringBuilder sb = new StringBuilder();
                                    sb.append("DATA PIPE ENTER: ")
                                            .append(target.getDeclaringClass().getName())
                                            .append(".").append(target.getName())
                                            .append(" args=");
                                    if (param.args != null) {
                                        for (int i = 0; i < param.args.length; i++) {
                                            Object a = param.args[i];
                                            if (i > 0) sb.append(" | ");
                                            if (a == null) {
                                                sb.append("null");
                                            } else if (a instanceof String) {
                                                String s = (String) a;
                                                if (s.length() > 600) s = s.substring(0, 600);
                                                sb.append("String[").append(s).append("]");
                                            } else if (a instanceof byte[]) {
                                                sb.append("byte[").append(((byte[]) a).length).append("]");
                                            } else {
                                                sb.append(a.getClass().getName()).append(":")
                                                        .append(String.valueOf(a).substring(0,
                                                                Math.min(300, String.valueOf(a).length())));
                                            }
                                        }
                                    }
                                    log(sb.toString());
                                } catch (Throwable e) {
                                    log("DATA PIPE ENTER log failed: " + e.getClass().getSimpleName());
                                }
                            }

                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                try {
                                    if (param.hasThrowable()) {
                                        log("DATA PIPE THROW: " + target.getDeclaringClass().getName()
                                                + "." + target.getName() + " "
                                                + param.getThrowable().getClass().getName() + ": "
                                                + String.valueOf(param.getThrowable().getMessage()));
                                        return;
                                    }
                                    Object r = param.getResult();
                                    String s = String.valueOf(r);
                                    if (s.length() > 1000) s = s.substring(0, 1000);
                                    log("DATA PIPE RESULT: " + target.getDeclaringClass().getName()
                                            + "." + target.getName() + " -> " + s);
                                } catch (Throwable e) {
                                    log("DATA PIPE RESULT log failed: " + e.getClass().getSimpleName());
                                }
                            }
                        });
                        hooked++;
                        log("hooked DATA PIPE: " + target.getDeclaringClass().getName()
                                + "." + target.getName());
                    }
                } catch (Throwable e) {
                    log("DATA PIPE class scan failed " + className + ": "
                            + e.getClass().getSimpleName());
                }
            }
            log("DATA PIPE hooks installed=" + hooked);
        } catch (Throwable e) {
            log("DATA PIPE hook failed: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static void hookYellowPageRegionParam(ClassLoader cl) {
        try {
            Class<?> k0 = Class.forName("com.miui.yellowpage.utils.k0", false, cl);
            Method[] methods = k0.getDeclaredMethods();
            int eCount = 0;

            log("REGION PARAM k0 runtime class=" + k0.getName()
                    + " methods=" + methods.length);

            for (Method method : methods) {
                Class<?>[] p = method.getParameterTypes();
                StringBuilder shape = new StringBuilder();
                shape.append(method.getName()).append("(");
                for (int i = 0; i < p.length; i++) {
                    if (i > 0) shape.append(",");
                    shape.append(p[i].getName());
                }
                shape.append(")->").append(method.getReturnType().getName())
                        .append(" static=").append(Modifier.isStatic(method.getModifiers()));
                log("REGION PARAM METHOD: " + shape);

                if (!"e".equals(method.getName())
                        && !"b".equals(method.getName())
                        && !"d".equals(method.getName())) {
                    continue;
                }

                final Method target = method;
                XposedBridge.hookMethod(target, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            log("REGION PARAM ENTER: " + target.getName()
                                    + " args=" + formatHookArgs(param.args));

                            if (param.args == null) return;

                            for (int i = 0; i < param.args.length; i++) {
                                Object arg = param.args[i];
                                if (!(arg instanceof java.util.Map)) continue;

                                java.util.Map<?, ?> map = (java.util.Map<?, ?>) arg;
                                Object region = map.get("region");
                                Object locid = map.get("locid");
                                log("REGION PARAM MAP[" + i + "]: method=" + target.getName()
                                        + " keys=" + String.valueOf(map.keySet())
                                        + " region=" + String.valueOf(region)
                                        + " locid=" + String.valueOf(locid));

                                @SuppressWarnings("unchecked")
                                java.util.Map<Object, Object> mutable =
                                        (java.util.Map<Object, Object>) map;
                                if (map.containsKey("region")) {
                                    mutable.put("region", "CN");
                                    log("REGION PARAM FORCE: " + target.getName()
                                            + " region " + String.valueOf(region) + " -> CN");
                                } else if ("b".equals(target.getName())) {
                                    mutable.put("region", "CN");
                                    log("REGION PARAM FORCE: b(Map) added region=CN");
                                }
                            }
                        } catch (Throwable e) {
                            log("REGION PARAM FORCE FAILED: "
                                    + e.getClass().getName() + ": "
                                    + String.valueOf(e.getMessage()));
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            log("REGION PARAM THROW: " + target.getName() + " "
                                    + param.getThrowable().getClass().getName() + ": "
                                    + String.valueOf(param.getThrowable().getMessage()));
                            return;
                        }
                        Object result = param.getResult();
                        String text = String.valueOf(result);
                        if (text.length() > 1000) text = text.substring(0, 1000);
                        log("REGION PARAM RESULT: " + target.getName()
                                + " -> " + text);
                    }
                });

                eCount++;
                log("hooked YellowPage runtime k0." + target.getName()
                        + " overload: " + shape);
            }

            log("REGION PARAM e/b/d overloads hooked=" + eCount);
        } catch (Throwable e) {
            log("REGION PARAM runtime scan failed: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static void hookYellowPageActualRequestBuilder(ClassLoader cl) {
        try {
            Class<?> builder = Class.forName("o0.b", false, cl);
            int found = 0;
            for (Method method : builder.getDeclaredMethods()) {
                if (!"j".equals(method.getName())) {
                    continue;
                }
                final Method target = method;
                XposedBridge.hookMethod(target, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable() || param.getResult() == null) {
                            return;
                        }
                        Object result = param.getResult();
                        if (!"com.miui.yellowpage.utils.H".equals(result.getClass().getName())) {
                            return;
                        }
                        try {
                            Class<?> j0 = Class.forName("com.miui.yellowpage.utils.j0", false, cl);
                            java.lang.reflect.Field k = j0.getDeclaredField("k");
                            k.setAccessible(true);
                            Object old = k.get(result);
                            log("ACTUAL REQUEST BUILDER: o0.b.j -> H k=" + String.valueOf(old));
                            if (Integer.valueOf(-1).equals(old)) {
                                Method setter = j0.getDeclaredMethod("j", Integer.TYPE);
                                setter.setAccessible(true);
                                setter.invoke(result, 1);
                                log("ACTUAL REQUEST BUILDER: forced H.k -1 -> 1");
                            }
                        } catch (Throwable e) {
                            log("ACTUAL REQUEST BUILDER force failed: "
                                    + e.getClass().getName() + ": " + String.valueOf(e.getMessage()));
                        }
                    }
                });
                found++;
                log("hooked ACTUAL REQUEST BUILDER: o0.b." + target.getName()
                        + "(" + target.getParameterTypes().length + " args)");
            }
            if (found == 0) {
                log("ACTUAL REQUEST BUILDER: o0.b.j not found");
            }
        } catch (Throwable e) {
            log("ACTUAL REQUEST BUILDER hook failed: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static void hookYellowPageRequestMode(ClassLoader cl) {
        try {
            Class<?> taskBase = Class.forName("o0.a", false, cl);
            Method factory = taskBase.getDeclaredMethod("j", Context.class);
            XposedBridge.hookMethod(factory, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable() || param.getResult() == null) {
                        return;
                    }
                    Object result = param.getResult();
                    if (!"com.miui.yellowpage.utils.H".equals(result.getClass().getName())
                            && !isInstanceOf(result, "com.miui.yellowpage.utils.H", cl)) {
                        return;
                    }

                    try {
                        Class<?> j0 = Class.forName(
                                "com.miui.yellowpage.utils.j0", false, cl);
                        java.lang.reflect.Field k = j0.getDeclaredField("k");
                        k.setAccessible(true);
                        Object old = k.get(result);
                        log("REQUEST MODE: o0.a.j(Context) returned H k=" + String.valueOf(old));

                        if (Integer.valueOf(-1).equals(old)) {
                            Method setter = j0.getDeclaredMethod("j", Integer.TYPE);
                            setter.setAccessible(true);
                            setter.invoke(result, 1);
                            log("REQUEST MODE: forced H.k -1 -> 1");
                        }
                    } catch (Throwable e) {
                        log("REQUEST MODE: force failed: "
                                + e.getClass().getName() + ": "
                                + String.valueOf(e.getMessage()));
                    }
                }
            });
            log("hooked REQUEST MODE: o0.a.j(Context) -> H");
        } catch (Throwable e) {
            log("REQUEST MODE hook failed: " + e.getClass().getName()
                    + ": " + String.valueOf(e.getMessage()));
        }
    }

    private static boolean isYellowPageContext(Object[] args) {
        try {
            if (args == null) return false;
            for (Object arg : args) {
                if (arg instanceof Context) {
                    Context app = ((Context) arg).getApplicationContext();
                    if (app != null && YELLOWPAGE.equals(app.getPackageName())) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isInstanceOf(Object value, String className, ClassLoader cl) {
        try {
            Class<?> target = Class.forName(className, false, cl);
            return target.isInstance(value);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void hookYellowPageNetworkGates(ClassLoader cl) {
        // H.u() has a concrete gate sequence in the EEA APK:
        //   j0.k -> Permission.networkingAllowed(j0.i) -> X.k(j0.i) -> j0.d()
        // Hook the exact classes/methods and also dump the H/j0 state at H.u entry.
        try {
            Class<?> permission = Class.forName("miui.yellowpage.Permission", false, cl);
            Method method = permission.getDeclaredMethod(
                    "networkingAllowed", Context.class);
            if (!Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != Boolean.TYPE) {
                log("NETWORK GATE Permission.networkingAllowed signature mismatch");
            } else {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object result = param.getResult();
                        if (Boolean.FALSE.equals(result) && isYellowPageContext(param.args)) {
                            param.setResult(true);
                            log("NETWORK GATE BYPASS: Permission.networkingAllowed false -> true");
                        } else {
                            log("NETWORK GATE: Permission.networkingAllowed -> "
                                    + String.valueOf(result));
                        }
                    }
                });
                log("hooked NETWORK GATE: Permission.networkingAllowed(Context)");
            }
        } catch (Throwable e) {
            log("NETWORK GATE Permission hook failed: "
                    + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }

        try {
            Class<?> x = Class.forName("com.miui.yellowpage.utils.X", false, cl);
            Method method = x.getDeclaredMethod("k", Context.class);
            if (!Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != Boolean.TYPE) {
                log("NETWORK GATE X.k signature mismatch");
            } else {
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        log("NETWORK GATE: X.k(Context) -> "
                                + String.valueOf(param.getResult()));
                    }
                });
                log("hooked NETWORK GATE: X.k(Context)");
            }
        } catch (Throwable e) {
            log("NETWORK GATE X.k hook failed: "
                    + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }

        try {
            Class<?> http = Class.forName("com.miui.yellowpage.utils.H", false, cl);
            Method u = http.getDeclaredMethod("u");
            XposedBridge.hookMethod(u, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    // EEA request objects can enter H.u() with k=-1. That value
                    // makes H.u() return 6 before j0.d() is reached. Normalize
                    // only URL-bearing H objects owned by the Yellow Page process.
                    try {
                        Class<?> j0 = Class.forName("com.miui.yellowpage.utils.j0", false, cl);
                        java.lang.reflect.Field k = j0.getDeclaredField("k");
                        java.lang.reflect.Field cField = j0.getDeclaredField("c");
                        java.lang.reflect.Field iField = j0.getDeclaredField("i");
                        k.setAccessible(true);
                        cField.setAccessible(true);
                        iField.setAccessible(true);
                        Object value = k.get(param.thisObject);
                        Object url = cField.get(param.thisObject);
                        Object ctx = iField.get(param.thisObject);
                        boolean yellowContext = false;
                        if (ctx instanceof Context) {
                            Context app = ((Context) ctx).getApplicationContext();
                            yellowContext = app != null && YELLOWPAGE.equals(app.getPackageName());
                        }
                        if (Integer.valueOf(-1).equals(value)
                                && yellowContext
                                && url != null
                                && String.valueOf(url).startsWith("http")) {
                            k.set(param.thisObject, 1);
                            log("REQUEST MODE BYPASS: H.u k -1 -> 1 url=" + String.valueOf(url));
                        }
                    } catch (Throwable e) {
                        log("REQUEST MODE BYPASS failed: " + e.getClass().getSimpleName());
                    }

                    Object obj = param.thisObject;
                    StringBuilder state = new StringBuilder("H.u STATE:");
                    Class<?> current = obj == null ? null : obj.getClass();
                    try {
                        Class<?> j0 = Class.forName("com.miui.yellowpage.utils.j0", false, cl);
                        java.lang.reflect.Field k = j0.getDeclaredField("k");
                        k.setAccessible(true);
                        state.append(" k=").append(String.valueOf(k.get(obj)));
                    } catch (Throwable e) {
                        state.append(" k=?");
                    }
                    try {
                        Class<?> j0 = Class.forName("com.miui.yellowpage.utils.j0", false, cl);
                        java.lang.reflect.Field i = j0.getDeclaredField("i");
                        i.setAccessible(true);
                        Object ctx = i.get(obj);
                        state.append(" context=").append(
                                ctx == null ? "null" : ctx.getClass().getName());
                    } catch (Throwable e) {
                        state.append(" context=?");
                    }
                    try {
                        Class<?> j0 = Class.forName("com.miui.yellowpage.utils.j0", false, cl);
                        java.lang.reflect.Field j = j0.getDeclaredField("j");
                        j.setAccessible(true);
                        state.append(" method=").append(String.valueOf(j.get(obj)));
                    } catch (Throwable e) {
                        state.append(" method=?");
                    }
                    try {
                        Class<?> j0 = Class.forName("com.miui.yellowpage.utils.j0", false, cl);
                        java.lang.reflect.Field cField = j0.getDeclaredField("c");
                        cField.setAccessible(true);
                        state.append(" url=").append(String.valueOf(cField.get(obj)));
                    } catch (Throwable e) {
                        state.append(" url=?");
                    }
                    log(state.toString());
                }
            });
            log("hooked H.u state probe");
        } catch (Throwable e) {
            log("H.u state probe hook failed: "
                    + e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
        }
    }


    private static void hookYellowPageResponseParser(ClassLoader cl) {
        try {
            Class<?> http = Class.forName("com.miui.yellowpage.utils.H", false, cl);
            final String[] fields = new String[] {"f6461m", "f6462n", "f6463o", "f6464p"};
            for (Method method : http.getDeclaredMethods()) {
                final String name = method.getName();
                if (!"u".equals(name) && !"v".equals(name)
                        && !"w".equals(name) && !"x".equals(name)) {
                    continue;
                }
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("HTTP PARSER ENTER: H." + name
                                + " args=" + formatHookArgs(param.args));
                        if ("w".equals(name)) {
                            try {
                                StackTraceElement[] trace = Thread.currentThread().getStackTrace();
                                StringBuilder out = new StringBuilder("H.w CALLER STACK:");
                                int n = 0;
                                for (StackTraceElement e : trace) {
                                    if (String.valueOf(e).contains("HookEntry")) continue;
                                    out.append(" | ").append(String.valueOf(e));
                                    if (++n >= 18) break;
                                }
                                log(out.toString());
                            } catch (Throwable ignored) {
                            }
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (param.hasThrowable()) {
                            Throwable t = param.getThrowable();
                            log("HTTP PARSER THROW: H." + name + " "
                                    + t.getClass().getName() + ": "
                                    + String.valueOf(t.getMessage()));
                            return;
                        }
                        StringBuilder out = new StringBuilder();
                        out.append("HTTP PARSER RESULT: H.").append(name)
                                .append(" -> ").append(String.valueOf(param.getResult()));
                        for (String fieldName : fields) {
                            try {
                                java.lang.reflect.Field f =
                                        http.getDeclaredField(fieldName);
                                f.setAccessible(true);
                                Object value = f.get(param.thisObject);
                                String text = String.valueOf(value);
                                if (text.length() > 2000) text = text.substring(0, 2000);
                                out.append(" ").append(fieldName).append("=").append(text);
                            } catch (Throwable ignored) {
                            }
                        }
                        log(out.toString());
                    }
                });
                log("hooked HTTP PARSER: H." + name);
            }
        } catch (Throwable e) {
            log("HTTP parser hook failed: " + e.getClass().getSimpleName());
        }
    }

    private static void hookMeteredNetworkGuard(ClassLoader cl) {
        try {
            Class<?> cm = Class.forName("android.net.ConnectivityManager", false, cl);
            Method metered = cm.getDeclaredMethod("isActiveNetworkMetered");
            XposedBridge.hookMethod(metered, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    log("Metered guard: ConnectivityManager.isActiveNetworkMetered() -> false");
                    param.setResult(false);
                }
            });
            log("hooked ConnectivityManager.isActiveNetworkMetered()");
        } catch (Throwable e) {
            log("Metered ConnectivityManager hook failed: " + e.getClass().getSimpleName());
        }

        try {
            Class<?> nc = Class.forName("android.net.NetworkCapabilities", false, cl);
            Method hasCapability = nc.getDeclaredMethod("hasCapability", Integer.TYPE);
            XposedBridge.hookMethod(hasCapability, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length == 1
                            && param.args[0] instanceof Integer
                            && ((Integer) param.args[0]) == 11) {
                        log("Metered guard: NetworkCapabilities.NOT_METERED -> true");
                        param.setResult(true);
                    }
                }
            });
            log("hooked NetworkCapabilities.hasCapability(int)");
        } catch (Throwable e) {
            log("Metered NetworkCapabilities hook failed: " + e.getClass().getSimpleName());
        }

        try {
            Class<?> job = Class.forName("com.miui.yellowpage.job.a", false, cl);
            int found = 0;
            for (Method method : job.getDeclaredMethods()) {
                if (!"e".equals(method.getName())) {
                    continue;
                }
                final Class<?> returnType = method.getReturnType();
                XposedBridge.hookMethod(method, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Throwable t = param.getThrowable();
                        if (t == null || !(t instanceof java.io.IOException)) {
                            return;
                        }
                        String msg = t.getMessage();
                        if (msg == null || !msg.toLowerCase().contains("metered")) {
                            return;
                        }
                        log("Metered guard: suppressed job.a.e() exception: " + msg);
                        if (returnType == Void.TYPE) {
                            param.setResult(null);
                        } else if (!returnType.isPrimitive()) {
                            param.setResult(null);
                        } else if (returnType == Boolean.TYPE) {
                            param.setResult(false);
                        } else if (returnType == Long.TYPE) {
                            param.setResult(0L);
                        } else if (returnType == Integer.TYPE
                                || returnType == Short.TYPE
                                || returnType == Byte.TYPE
                                || returnType == Character.TYPE) {
                            param.setResult(0);
                        } else if (returnType == Float.TYPE) {
                            param.setResult(0f);
                        } else if (returnType == Double.TYPE) {
                            param.setResult(0d);
                        } else if (returnType == Boolean.TYPE) {
                            param.setResult(false);
                        }
                    }
                });
                found++;
                log("hooked YellowPage job.a.e() #" + found + " return=" + returnType.getName());
            }
            log("YellowPage metered exception fallback hooks installed: " + found);
        } catch (Throwable e) {
            log("YellowPage job.a.e() metered fallback failed: " + e.getClass().getSimpleName());
        }
    }


    private static void logDatabaseStats(Context context, ClassLoader cl, String stage) {
        try {
            Class<?> dbHelperClass = Class.forName(
                    "com.miui.yellowpage.providers.yellowpage.YellowPageDatabaseHelper",
                    false, cl);
            Object helper = XposedHelpers.callStaticMethod(dbHelperClass, "E", context);
            SQLiteDatabase db = (SQLiteDatabase) XposedHelpers.callMethod(
                    helper, "getReadableDatabase");
            Cursor c = null;
            try {
                c = db.rawQuery(
                        "SELECT (SELECT COUNT(*) FROM yellow_page),"
                                + " (SELECT COUNT(*) FROM phone_lookup),"
                                + " (SELECT COALESCE(MAX(update_time),0) FROM yellow_page),"
                                + " (SELECT COALESCE(MAX(last_use_time),0) FROM yellow_page)",
                        null);
                if (c.moveToFirst()) {
                    log("DB STATS " + stage
                            + ": yellow_page=" + c.getLong(0)
                            + ", phone_lookup=" + c.getLong(1)
                            + ", max_update_time=" + c.getLong(2)
                            + ", max_last_use_time=" + c.getLong(3));
                }
            } finally {
                if (c != null) c.close();
            }
        } catch (Throwable e) {
            log("DB STATS " + stage + " failed: " + e.getClass().getSimpleName());
        }
    }

    private static void hookYellowPageDatabaseWrites(ClassLoader cl) {
        try {
            Class<?> db = Class.forName("android.database.sqlite.SQLiteDatabase", false, cl);

            Method insert = db.getDeclaredMethod(
                    "insertWithOnConflict", String.class, String.class,
                    ContentValues.class, Integer.TYPE);
            XposedBridge.hookMethod(insert, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String table = String.valueOf(param.args[0]);
                    if ("yellow_page".equals(table) || "phone_lookup".equals(table)) {
                        ContentValues values = (ContentValues) param.args[2];
                        log("DB WRITE INSERT: table=" + table
                                + ", values=" + String.valueOf(values));
                    }
                }
            });

            Method update = db.getDeclaredMethod(
                    "updateWithOnConflict", String.class, ContentValues.class,
                    String.class, String[].class, Integer.TYPE);
            XposedBridge.hookMethod(update, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String table = String.valueOf(param.args[0]);
                    if ("yellow_page".equals(table) || "phone_lookup".equals(table)) {
                        ContentValues values = (ContentValues) param.args[1];
                        log("DB WRITE UPDATE: table=" + table
                                + ", where=" + String.valueOf(param.args[2])
                                + ", values=" + String.valueOf(values));
                    }
                }
            });

            Method delete = db.getDeclaredMethod(
                    "delete", String.class, String.class, String[].class);
            XposedBridge.hookMethod(delete, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String table = String.valueOf(param.args[0]);
                    if ("yellow_page".equals(table) || "phone_lookup".equals(table)) {
                        log("DB WRITE DELETE: table=" + table
                                + ", where=" + String.valueOf(param.args[1]));
                    }
                }
            });

            // Some Yellow Page DB code uses compiled statements instead of
            // SQLiteDatabase.insert/update. Trace those paths too.
            try {
                Class<?> stmt = Class.forName("android.database.sqlite.SQLiteStatement", false, cl);
                for (Method method : stmt.getDeclaredMethods()) {
                    if (!"executeInsert".equals(method.getName())
                            && !"executeUpdateDelete".equals(method.getName())) {
                        continue;
                    }
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("DB WRITE STATEMENT ENTER: " + method.getName()
                                    + " sql=" + String.valueOf(XposedHelpers.callMethod(
                                            param.thisObject, "toString")));
                        }
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) {
                                log("DB WRITE STATEMENT THROW: " + method.getName()
                                        + " " + param.getThrowable().getClass().getSimpleName());
                            } else {
                                log("DB WRITE STATEMENT RESULT: " + method.getName()
                                        + " -> " + String.valueOf(param.getResult()));
                            }
                        }
                    });
                }
                log("hooked YellowPage SQLiteStatement write methods");
            } catch (Throwable e) {
                log("SQLiteStatement hook failed: " + e.getClass().getSimpleName());
            }

            try {
                Method exec = db.getDeclaredMethod("execSQL", String.class);
                XposedBridge.hookMethod(exec, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        String sql = String.valueOf(param.args[0]);
                        String lower = sql.toLowerCase(java.util.Locale.ROOT);
                        if (lower.contains("yellow_page") || lower.contains("phone_lookup")) {
                            log("DB WRITE EXECSQL: " + sql);
                        }
                    }
                });
                log("hooked YellowPage SQLiteDatabase.execSQL");
            } catch (Throwable e) {
                log("SQLite execSQL hook failed: " + e.getClass().getSimpleName());
            }

            log("hooked YellowPage SQLite write methods");
        } catch (Throwable e) {
            log("YellowPage SQLite write hook failed: "
                    + e.getClass().getSimpleName());
        }
    }

    private static void hookYellowPagePostResponsePipeline(ClassLoader cl) {
        String[] classes = {"o0.d", "n0.d"};
        for (String name : classes) {
            try {
                Class<?> c = Class.forName(name, false, cl);
                int hooked = 0;
                for (Method m : c.getDeclaredMethods()) {
                    String mn = m.getName();
                    if (!(("o0.d".equals(name) && ("k".equals(mn) || "z".equals(mn)))
                            || ("n0.d".equals(name) && "a".equals(mn)))) {
                        continue;
                    }
                    final String methodName = name + "." + mn;
                    XposedBridge.hookMethod(m, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("POST PIPE ENTER: " + methodName
                                    + " args=" + formatHookArgs(param.args));
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (param.hasThrowable()) {
                                Throwable t = param.getThrowable();
                                log("POST PIPE THROW: " + methodName);
                                log("POST PIPE THROW CLASS: " + t.getClass().getName());
                                log("POST PIPE THROW MESSAGE: " + String.valueOf(t.getMessage()));
                                Throwable cause = t.getCause();
                                if (cause != null) {
                                    log("POST PIPE THROW CAUSE: " + cause.getClass().getName()
                                            + ": " + String.valueOf(cause.getMessage()));
                                }
                                StackTraceElement[] trace = t.getStackTrace();
                                int limit = Math.min(trace == null ? 0 : trace.length, 30);
                                for (int i = 0; i < limit; i++) {
                                    log("POST PIPE THROW STACK[" + i + "]: " + String.valueOf(trace[i]));
                                }
                            } else {
                                Object result = param.getResult();
                                log("POST PIPE RESULT: " + methodName
                                        + " -> " + String.valueOf(result)
                                        + " class=" + (result == null
                                        ? "null" : result.getClass().getName()));
                            }

                        }
                    });
                    hooked++;
                }
                log("hooked YellowPage post-response pipeline: " + name
                        + " methods=" + hooked);
            } catch (Throwable e) {
                log("post-response hook failed: " + name + " "
                        + e.getClass().getSimpleName());
            }
        }
    }

    private static void hookPullTaskPipeline(ClassLoader cl, Context context) {
        try {
            // Job 0 does not call PullTask.y() directly. The real chain is:
            // YellowPageJobService -> job.a.c(Context) -> n0.C0372d.a(...)
            // -> AbstractC0381d.z(...) -> concrete PullTask.y(Context).
            try {
                Class<?> jobManager = Class.forName("com.miui.yellowpage.job.a", false, cl);
                Method cMethod = jobManager.getDeclaredMethod("c", Context.class);
                XposedBridge.hookMethod(cMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("PullPipeline ENTER: job.a.c(Context)");
                        Context ctx = param.args[0] instanceof Context
                                ? (Context) param.args[0] : context;
                        logDatabaseStats(ctx, cl, "BEFORE_SYNC");
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        log("PullPipeline RESULT: job.a.c(Context)=" + String.valueOf(param.getResult()));
                        Context ctx = param.args[0] instanceof Context
                                ? (Context) param.args[0] : context;
                        logDatabaseStats(ctx, cl, "AFTER_SYNC");
                    }
                });
                log("hooked PullPipeline: com.miui.yellowpage.job.a.c(Context)");
            } catch (Throwable e) {
                log("PullPipeline job.a hook failed: " + e.getClass().getSimpleName());
            }

            try {
                Class<?> daemon = Class.forName("n0.C0372d", false, cl);
                for (Method method : daemon.getDeclaredMethods()) {
                    Class<?>[] p = method.getParameterTypes();
                    if (!"a".equals(method.getName())
                            || p.length != 2
                            || p[0] != Context.class) {
                        continue;
                    }
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            log("PullPipeline ENTER: n0.C0372d.a(Context,...)");
                        }
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            log("PullPipeline EXIT: n0.C0372d.a(Context,...)");
                        }
                    });
                    log("hooked PullPipeline: n0.C0372d.a");
                }
            } catch (Throwable e) {
                log("PullPipeline daemon hook failed: " + e.getClass().getSimpleName());
            }

            try {
                Class<?> base = Class.forName("o0.AbstractC0381d", false, cl);
                Method zMethod = base.getDeclaredMethod(
                        "z", Context.class, String.class, Long.TYPE, Boolean.TYPE);
                XposedBridge.hookMethod(zMethod, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        log("PullPipeline ENTER: AbstractC0381d.z(Context,String,long,boolean) "
                                + "class=" + param.thisObject.getClass().getName());
                    }
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        log("PullPipeline EXIT: AbstractC0381d.z class="
                                + param.thisObject.getClass().getName());
                    }
                });
                log("hooked PullPipeline: o0.AbstractC0381d.z");
            } catch (Throwable e) {
                log("PullPipeline AbstractC0381d.z hook failed: "
                        + e.getClass().getSimpleName());
            }
        } catch (Throwable e) {
            log("PullPipeline hook failed: " + e.getClass().getSimpleName());
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
                XposedBridge.hookMethod(method, new XC_MethodHook() {                    @Override
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
                            hookYellowPageJobServices(cl, context);
                            hookPullTaskExecution(cl);
                            hookYellowPageHttpDecision(cl);
                            hookYellowPageNetworkGates(cl);
                            hookYellowPageResponseParser(cl);
                            hookYellowPageResponseSurface(cl);
                            hookYellowPageStreamUtility(cl);
                            hookYellowPageStreamRequest(cl);
                            hookYellowPageHttpBase(cl);
                            hookYellowPageLiveHttp(cl);
                            hookYellowPageDatabaseWrites(cl);
        hookYellowPagePostResponsePipeline(cl);
                            hookPullTaskPipeline(cl, context);
                            hookMeteredNetworkGuard(cl);
                            hookJobDispatcher(cl, context);
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


    private static void hookYellowPageWStatus(ClassLoader cl) {
        try {
            Class<?> hClass = Class.forName("com.miui.yellowpage.utils.H", false, cl);
            Method w = hClass.getDeclaredMethod("w");
            if (w.getReturnType() != Integer.TYPE || w.getParameterTypes().length != 0) {
                log("H.w() shape unexpected");
                return;
            }
            XposedBridge.hookMethod(w, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (param.hasThrowable()) return;
                    Object result = param.getResult();
                    if (result instanceof Integer && ((Integer) result) == 3) {
                        log("H.w STATUS: 3 -> 0 (diagnostic bypass)");
                        param.setResult(0);
                    } else {
                        log("H.w STATUS: " + String.valueOf(result));
                    }
                }
            });
            log("hooked com.miui.yellowpage.utils.H.w()");
        } catch (Throwable e) {
            log("H.w hook failed: " + e.getClass().getSimpleName());
        }
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
            log("YELLOWPAGE LOAD ENTER classLoader=" + String.valueOf(cl));
            hookYellowPageRequestMode(cl);
            hookYellowPageWStatus(cl);
            hookYellowPageActualRequestBuilder(cl);
            hookYellowPageRegionParam(cl);
            hookYellowPageDataDecode(cl);
            hookYellowPageDownload(cl);
            hookGlobalHttpsConnection(cl);
            try {
                log("CRITICAL CALL BEFORE");
                hookCriticalYellowPageGates(cl);
                log("CRITICAL CALL AFTER");
            } catch (Throwable e) {
                log("CRITICAL CALL THROW: " + e.getClass().getName()
                        + ": " + String.valueOf(e.getMessage()));
                Throwable cause = e.getCause();
                if (cause != null) {
                    log("CRITICAL CALL CAUSE: " + cause.getClass().getName()
                            + ": " + String.valueOf(cause.getMessage()));
                }
            }

            hookBooleanContextMethod(
                    cl, "miui.yellowpage.YellowPageUtils",
                    "isYellowPageAvailable");
            hookBooleanContextMethod(
                    cl, "miui.yellowpage.YellowPageUtils",
                    "isYellowPageEnable");
            hookYellowPageSyncGate(cl);
            // Install the metered-network bypass immediately when Yellow Page loads,
            // before Provider/JobService can start the pull pipeline.
            hookMeteredNetworkGuard(cl);
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