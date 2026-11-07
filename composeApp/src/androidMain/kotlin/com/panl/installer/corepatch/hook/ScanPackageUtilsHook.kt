package com.panl.installer.corepatch.hook

import android.annotation.SuppressLint
import android.os.Build
import com.panl.installer.corepatch.Config
import com.panl.installer.corepatch.XposedHelper.hookBefore
import com.panl.installer.corepatch.XposedHelper.hostClassLoader

object ScanPackageUtilsHook : BaseHook() {
    override val name = "ScanPackageUtilsHook"

    @SuppressLint("PrivateApi")
    override fun hook() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val scanPackageUtilsClazz =
            hostClassLoader.loadClass("com.android.server.pm.ScanPackageUtils")
        val assertMinSignatureSchemeIsValidMethod =
            scanPackageUtilsClazz.declaredMethods.first { m -> m.name == "assertMinSignatureSchemeIsValid" }
        hookBefore(assertMinSignatureSchemeIsValidMethod) { callback ->
            if (Config.isBypassVerificationEnabled()) {
                callback.returnAndSkip(null)
            }
        }
    }
}
