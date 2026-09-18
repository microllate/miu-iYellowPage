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
                    injectMember {
                        method { name = "isYellowPageEnable" }
                        afterHook {
                            result = true
                            loggerD(msg = "[miu-iYellowPage] isYellowPageEnable() -> true")
                        }
                    }
                }

                findClass("c0.C0241b").hook {
                    injectMember {
                        method { name = "e" }
                        afterHook {
                            loggerD(msg = "[miu-iYellowPage] C0241b.e() called; forcing true")
                            result = true
                        }
                    }
                }

                findClass("com.miui.yellowpage.providers.yellowpage.YellowPageProvider").hook {
                    injectMember {
                        method { name = "k" }
                        beforeHook {
                            loggerD(msg = "[miu-iYellowPage] YellowPageProvider.k() invoked")
                        }
                    }
                }

                loggerD(msg = "[miu-iYellowPage] Yellow Page hooks initialized")
            } catch (e: Throwable) {
                loggerD(msg = "[miu-iYellowPage] Yellow Page hook initialization failed: " +
                    e.stackTraceToString())
            }
        }

        loadApp(name = "com.android.contacts") {
            try {
                findClass("com.android.contacts.util.YellowPageProxy").hook {
                    injectMember {
                        method { name = "j" }
                        afterHook {
                            result = true
                            loggerD(msg = "[miu-iYellowPage] Contacts YellowPageProxy.j() -> true")
                        }
                    }
                }
                loggerD(msg = "[miu-iYellowPage] Contacts YellowPageProxy hook initialized")
            } catch (e: Throwable) {
                loggerD(msg = "[miu-iYellowPage] Contacts YellowPageProxy hook failed: " +
                    e.stackTraceToString())
            }
        }
    }
}
