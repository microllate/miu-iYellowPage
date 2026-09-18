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
                findClass("com.miui.yellowpage.providers.yellowpage.YellowPageProvider").hook {
                    injectMember {
                        method { name = "k" }
                        beforeHook {
                            loggerD(msg = "[miu-iYellowPage] YellowPageProvider.k() invoked")
                        }
                    }
                }
                loggerD(msg = "[miu-iYellowPage] k() hook initialized")
            } catch (err: Throwable) {
                loggerD(msg = "[miu-iYellowPage] initialization failed: ${err.stackTraceToString()}")
            }
        }
    }
}
