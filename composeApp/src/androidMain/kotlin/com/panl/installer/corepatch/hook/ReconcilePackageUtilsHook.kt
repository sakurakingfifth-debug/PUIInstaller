package com.panl.installer.corepatch.hook

import android.annotation.SuppressLint
import android.os.Build
import com.panl.installer.corepatch.Config
import com.panl.installer.corepatch.XposedHelper
import com.panl.installer.corepatch.XposedHelper.hostClassLoader
import com.panl.installer.corepatch.XposedHelper.log
import com.panl.installer.corepatch.XposedHelper.setStaticBoolean

object ReconcilePackageUtilsHook : BaseHook() {
    override val name = "ReconcilePackageUtilsHook"

    @SuppressLint("PrivateApi")
    override fun hook() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        // https://cs.android.com/android/platform/superproject/+/android-14.0.0_r75:frameworks/base/services/core/java/com/android/server/pm/ReconcilePackageUtils.java
        val reconcilePackageUtilsClazz =
            hostClassLoader.loadClass("com.android.server.pm.ReconcilePackageUtils")
        val reconcilePackagesMethod =
            reconcilePackageUtilsClazz.declaredMethods.first { m -> m.name == "reconcilePackages" }
        if (!XposedHelper.deoptimize(reconcilePackagesMethod)) log("failed to deoptimize reconcilePackages")

        if (Config.isBypassDigestEnabled() && Config.isBypassSharedUserEnabled()) {
            reconcilePackageUtilsClazz.declaredFields.firstOrNull { field -> field.name == "ALLOW_NON_PRELOADS_SYSTEM_SHAREDUIDS" }
                ?.let { field ->
                    setStaticBoolean(field, true)
                }
        }
    }
}
