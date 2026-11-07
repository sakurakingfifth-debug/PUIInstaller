package com.panl.installer.corepatch.hook

import com.panl.installer.corepatch.Config
import com.panl.installer.corepatch.XposedHelper.hookBefore
import com.panl.installer.corepatch.XposedHelper.hostClassLoader

object NtConfigListServiceImplHook : BaseHook() {
    override val name = "NtConfigListServiceImplHook"

    override fun hook() {
        // TODO: Check is Nothing Phone
        val ntConfigListServiceImplClazz = try {
            hostClassLoader.loadClass("com.nothing.server.ex.NtConfigListServiceImpl")
        } catch (e: ClassNotFoundException) {
            return
        }
        val isInstallingAppForbiddenMethod =
            ntConfigListServiceImplClazz.declaredMethods.first { m -> m.name == "isInstallingAppForbidden" }
        hookBefore(isInstallingAppForbiddenMethod) { callback ->
            if (Config.isBypassBlockEnabled()) callback.returnAndSkip(false)
        }
        val isStartingAppForbiddenMethod =
            ntConfigListServiceImplClazz.declaredMethods.first { m -> m.name == "isStartingAppForbidden" }
        hookBefore(isStartingAppForbiddenMethod) { callback ->
            if (Config.isBypassBlockEnabled()) callback.returnAndSkip(false)
        }
    }
}
