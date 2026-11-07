package com.panl.installer.corepatch

import android.os.Build
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import com.panl.installer.corepatch.Config.printAllConfig
import com.panl.installer.corepatch.hook.ApkSignatureVerifierHook
import com.panl.installer.corepatch.hook.ApkSigningBlockUtilsHook
import com.panl.installer.corepatch.hook.ApplicationInfoHook
import com.panl.installer.corepatch.hook.AssetManagerHook
import com.panl.installer.corepatch.hook.InstallPackageHelperHook
import com.panl.installer.corepatch.hook.KeySetManagerServiceHook
import com.panl.installer.corepatch.hook.MessageDigestHook
import com.panl.installer.corepatch.hook.NtConfigListServiceImplHook
import com.panl.installer.corepatch.hook.PackageManagerServiceHook
import com.panl.installer.corepatch.hook.PackageManagerServiceUtilsHook
import com.panl.installer.corepatch.hook.ReconcilePackageUtilsHook
import com.panl.installer.corepatch.hook.ScanPackageUtilsHook
import com.panl.installer.corepatch.hook.SharedUserSettingHook
import com.panl.installer.corepatch.hook.SigningDetailsHook
import com.panl.installer.corepatch.hook.StrictJarVerifierHook
import com.panl.installer.corepatch.hook.VerificationParamsHook
import com.panl.installer.corepatch.hook.VerifyingSessionHook

class CorePatchModule : XposedModule() {

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        super.onModuleLoaded(param)
        XposedHelper.setXposedModule(this)
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        super.onSystemServerStarting(param)
        XposedHelper.log("onSystemServerStarting: Current sdk version is ${Build.VERSION.SDK_INT}")

        XposedHelper.setHostClassLoader(param.classLoader)

        printAllConfig()
        publishStateToGlobal()

        val hooks = listOf(
            ApkSignatureVerifierHook,
            ApkSigningBlockUtilsHook,
            ApplicationInfoHook,
            AssetManagerHook,
            InstallPackageHelperHook,
            KeySetManagerServiceHook,
            MessageDigestHook,
            NtConfigListServiceImplHook,
            PackageManagerServiceHook,
            PackageManagerServiceUtilsHook,
            ReconcilePackageUtilsHook,
            ScanPackageUtilsHook,
            SharedUserSettingHook,
            SigningDetailsHook,
            StrictJarVerifierHook,
            VerificationParamsHook,
            VerifyingSessionHook,
        )
        hooks.forEach { it.init() }

        // 远程 prefs 在应用侧只能读到自己写过的值（读回来全是默认 false），
        // 所以由模块把真实状态发布到 Settings.Global，应用读那里来显示开关。
        runCatching {
            XposedHelper.prefs.registerOnSharedPreferenceChangeListener { _, _ -> publishStateToGlobal() }
        }
    }

    /**
     * 把开关状态写进 Settings.Global 给应用侧读。
     *
     * onSystemServerStarting 阶段 ActivityManager 还没起来，直接拿 system context 会 NPE，
     * 所以开个线程每秒重试，直到系统服务可用（最多 60 次，开机只跑这一轮）。
     */
    private fun publishStateToGlobal() {
        Thread({
            repeat(60) {
                val ok = runCatching {
                    val at = Class.forName("android.app.ActivityThread")
                    val current = at.getMethod("currentActivityThread").invoke(null)
                    val ctx = at.getMethod("getSystemContext").invoke(current) as android.content.Context
                    android.provider.Settings.Global.putString(
                        ctx.contentResolver,
                        com.panl.installer.corepatch.Config.GLOBAL_STATE_KEY,
                        com.panl.installer.corepatch.Config.snapshot()
                    )
                    XposedHelper.log("published state: ${com.panl.installer.corepatch.Config.snapshot()}")
                }.isSuccess
                if (ok) return@Thread
                runCatching { Thread.sleep(1000) }
            }
            XposedHelper.log("publishStateToGlobal gave up")
        }, "CorePatchPublish").start()
    }
}
