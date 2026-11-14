package com.panl.installer.core

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process

/**
 * 跳到 LSPosed 管理器里「这个模块」的详情 / 作用域页。
 *
 * 作用域页没有导出的 Activity，只能走管理器自己认的深链：
 * `MainActivity#handleIntent` 里判断 `data.scheme == "module"`，然后
 * host 当作模块包名、port 当作用户 id，导航到该模块的详情页。
 * （`modules` / `logs` / `repo` / `settings` 那四个 data 是它桌面快捷方式用的，这里用不上。）
 *
 * 管理器没装、或者 LSPosed 服务没起来时返回 false，由调用方给个提示。
 */
object Lsposed {

    const val MANAGER = "org.lsposed.manager"
    private const val MAIN = "org.lsposed.manager.ui.activity.MainActivity"

    /** 管理器装没装。 */
    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(MANAGER, 0)
        true
    }.getOrDefault(false)

    /** 打开 [packageName] 这个模块的作用域页；跳不过去返回 false。 */
    fun openModuleScope(context: Context, packageName: String): Boolean {
        if (!isInstalled(context)) return false
        val userId = runCatching { Process.myUid() / 100000 }.getOrDefault(0)
        val direct = Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(MANAGER, MAIN)
            data = Uri.parse("module://$packageName:$userId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (runCatching { context.startActivity(direct); true }.getOrDefault(false)) return true
        // 深链没走通（管理器换过组件名之类）就退回打开管理器首页
        val launch = context.packageManager.getLaunchIntentForPackage(MANAGER) ?: return false
        return runCatching {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
    }
}
