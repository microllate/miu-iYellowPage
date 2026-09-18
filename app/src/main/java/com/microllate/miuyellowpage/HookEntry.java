package com.microllate.miuyellowpage;

import android.content.Context;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class HookEntry implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final IXposedHookLoadPackage.LoadPackageParam lpparam) {
        if (!"com.miui.yellowpage".equals(lpparam.packageName)) return;

        try {
            XposedHelpers.findAndHookMethod(
                    "miui.yellowpage.YellowPageUtils",
                    lpparam.classLoader,
                    "isYellowPageAvailable",
                    Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            param.setResult(true);
                            XposedBridge.log("[miu-iYellowPage] isYellowPageAvailable() -> true");
                        }
                    }
            );
            XposedBridge.log("[miu-iYellowPage] HookEntry initialized");
        } catch (Throwable t) {
            XposedBridge.log("[miu-iYellowPage] HookEntry failed: " + t);
        }
    }
}
