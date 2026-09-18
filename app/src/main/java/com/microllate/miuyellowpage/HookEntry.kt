package com.microllate.miuyellowpage

import android.net.Uri
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.findClass
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.log.loggerD
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    override fun onInit() = configs { isDebug = true }
    override fun onHook() = encase {
        loadApp(name = "com.miui.yellowpage") {
            try {
                findClass("com.miui.yellowpage.providers.yellowpage.YellowPageProvider").hook {
                    injectMember {
                        method { name = "k" }
                        beforeHook { loggerD(msg = "[miu-iYellowPage] YellowPageProvider.k() invoked") }
                    }
                    injectMember {
                        method {
                            name = "query"
                            param("android.net.Uri", "[Ljava.lang.String;", "java.lang.String", "[Ljava.lang.String;", "java.lang.String")
                        }
                        afterHook {
                            val uri = args(0).cast<Uri?>()
                            val path = uri?.path ?: return@afterHook
                            if (!path.startsWith("/phone_lookup")) return@afterHook
                            loggerD(msg = "[miu-iYellowPage] phone_lookup observed: $uri")
                            try {
                                instance.callMethod { name = "k" }
                                loggerD(msg = "[miu-iYellowPage] k() invoked from phone_lookup")
                            } catch (err: Throwable) {
                                loggerD(msg = "[miu-iYellowPage] invoke k() failed: ${err.stackTraceToString()}")
                            }
                        }
                    }
                }
                loggerD(msg = "[miu-iYellowPage] hooks initialized")
            } catch (err: Throwable) {
                loggerD(msg = "[miu-iYellowPage] initialization failed: \${err.stackTraceToString()}")
            }
        }
    }
}
