package com.microllate.miuyellowpage

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
                findClass("miui.yellowpage.YellowPageUtils").hook {
                    injectMember {
                        method { name = "isYellowPageAvailable" }
                        afterHook {
                            result = true
                            loggerD(msg = "[miu-iYellowPage] isYellowPageAvailable() -> true")
                        }
                    }
                }
                loggerD(msg = "[miu-iYellowPage] minimal hook initialized")
            } catch (e: Throwable) {
                loggerD(msg = "[miu-iYellowPage] hook initialization failed: " + e.stackTraceToString())
            }
        }
    }
}
