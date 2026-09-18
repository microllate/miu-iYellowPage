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
        // 1. EEA Yellow Page itself blocks Yellow Page availability because
        //    the EEA region is not present in localization.json.
        loadApp(name = "com.miui.yellowpage") {
            try {
                findClass("miui.yellowpage.YellowPageUtils").hook {
                    injectMember {
                        method {
                            name = "isYellowPageAvailable"
                            paramCount = 1
                            returnType = Boolean::class.javaPrimitiveType
                        }
                        afterHook {
                            if (result<Boolean>() != true) {
                                loggerD(msg = "[miu-iYellowPage] force isYellowPageAvailable() = true")
                                result = true
                            }
                        }
                    }

                    injectMember {
                        method {
                            name = "isYellowPageEnable"
                            paramCount = 1
                            returnType = Boolean::class.javaPrimitiveType
                        }
                        afterHook {
                            if (result<Boolean>() != true) {
                                loggerD(msg = "[miu-iYellowPage] force isYellowPageEnable() = true")
                                result = true
                            }
                        }
                    }
                }

                // 2. EEA is absent from localization.json, so C0241b.e()
                //    normally returns false for every feature. Only enable
                //    the three Yellow Page related features needed here.
                findClass("c0.C0241b").hook {
                    injectMembers {
                        method {
                            name = "e"
                            paramCount = 2
                            returnType = Boolean::class.javaPrimitiveType
                        }.all()
                        afterHook {
                            when (args(index = 1).any()?.toString()) {
                                "YELLOW_PAGE",
                                "YELLOWPAGE_PROVIDER",
                                "YELLOWPAGE_SYNC" -> {
                                    if (result<Boolean>() != true) {
                                        loggerD(
                                            msg = "[miu-iYellowPage] force feature " +
                                                args(index = 1).any()?.toString() + " = true"
                                        )
                                        result = true
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Keep the existing provider diagnostic hook.
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

        // 4. CN Contacts was ported to EEA. Its YellowPageProxy.j() gate
        //    can reject the EEA Yellow Page. Force only Boolean j() methods
        //    to true and log when the original value was false.
        loadApp(name = "com.android.contacts") {
            try {
                findClass("com.android.contacts.util.YellowPageProxy").hook {
                    injectMembers {
                        method {
                            name = "j"
                            returnType = Boolean::class.javaPrimitiveType
                        }.all()
                        afterHook {
                            if (result<Boolean>() != true) {
                                loggerD(msg = "[miu-iYellowPage] force Contacts YellowPageProxy.j() = true")
                                result = true
                            }
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
