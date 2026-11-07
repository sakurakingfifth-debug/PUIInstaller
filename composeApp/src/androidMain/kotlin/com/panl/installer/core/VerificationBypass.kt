package com.panl.installer.core

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.util.Log

/**
 * 安装校验绕过（移植自 vvb2060/PackageInstaller 的 BinderWrapper）。
 *
 * 两个隐藏接口，都在 IPackageInstaller 上：
 *  - `disableVerificationForUid(uid)`：Android 14 起，关掉本 UID 的安装校验（Play 保护 / 校验代理）
 *  - `addDeveloperVerificationExperiment(pkg, 3, [1])`：Android 16 起，跳过「开发者验证」实验
 *
 * 拿不到 IPackageInstaller（隐藏 API 被拦、权限不足）时静默失败，安装照常走。
 */
object VerificationBypass {

    private const val TAG = "VerificationBypass"

    /** 关闭当前 UID 的安装校验。返回是否成功。 */
    fun disableForUid(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return false
        return runCatching {
            val proxy = installerProxy(context) ?: return false
            val m = proxy.javaClass.getMethod(
                "disableVerificationForUid",
                Int::class.javaPrimitiveType
            )
            m.invoke(proxy, Process.myUid())
            true
        }.onFailure { Log.w(TAG, "disableVerificationForUid failed", it) }
            .getOrDefault(false)
    }

    /** 跳过开发者验证（Android 16+）。返回是否成功。 */
    fun addDeveloperExperiment(context: Context, packageName: String): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        return runCatching {
            val proxy = installerProxy(context) ?: return false
            val m = proxy.javaClass.getMethod(
                "addDeveloperVerificationExperiment",
                String::class.java,
                Int::class.javaPrimitiveType,
                IntArray::class.java
            )
            m.invoke(proxy, packageName, 3, intArrayOf(1))
            true
        }.onFailure { Log.w(TAG, "addDeveloperVerificationExperiment failed", it) }
            .getOrDefault(false)
    }

    /** 反射拿到 PackageInstaller 内部的 IPackageInstaller 代理。 */
    private fun installerProxy(context: Context): Any? {
        val installer = context.packageManager.packageInstaller
        val field = PackageInstaller::class.java.getDeclaredField("mInstaller")
        field.isAccessible = true
        return field.get(installer)
    }

    /** 会话参数里塞隐藏安装标志位（INSTALL_ALLOW_TEST 等）。 */
    fun addInstallFlag(params: PackageInstaller.SessionParams, flag: Int): Boolean {
        return runCatching {
            val field = params.javaClass.getDeclaredField("installFlags")
            field.isAccessible = true
            val current = field.getInt(params)
            field.setInt(params, current or flag)
            true
        }.getOrDefault(false)
    }

    /** 部分 ROM 把安装器身份也当成校验条件，这里统一给个「看起来可信」的包名。 */
    fun forgedInstaller(context: Context): String {
        val pm = context.packageManager
        val play = runCatching {
            pm.getPackageInfo("com.android.vending", PackageManager.MATCH_SYSTEM_ONLY)
        }.isSuccess
        return if (play) "com.android.vending" else "com.android.shell"
    }
}
