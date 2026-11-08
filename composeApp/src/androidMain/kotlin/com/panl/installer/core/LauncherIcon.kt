package com.panl.installer.core

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * 桌面图标显隐。
 *
 * 用的是 `<activity-alias>`（[ALIAS]）：真正的 `MainActivity` 不动，只把桌面入口那个
 * 别名启用/禁用，所以隐藏之后：
 *  - 桌面 / 抽屉里看不到图标，也搜不到；
 *  - 应用本身还在，安装器身份、默认安装器、通知点击跳转全都正常；
 *  - 拨号盘输入 `*#*#078#*#*`（[SecretCodeReceiver]）随时能切回来。
 *
 * 用 `DONT_KILL_APP`：切图标不该把正在跑的安装流程干掉。
 */
object LauncherIcon {

    private const val TAG = "LauncherIcon"

    /** 与 AndroidManifest 里的 `<activity-alias android:name=".ui.LauncherAlias">` 对应。 */
    private const val ALIAS = "com.panl.installer.ui.LauncherAlias"

    private fun component(context: Context) = ComponentName(context.packageName, ALIAS)

    fun isHidden(context: Context): Boolean = runCatching {
        context.packageManager.getComponentEnabledSetting(component(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }.getOrDefault(false)

    /** @return 是否设置成功。 */
    fun setHidden(context: Context, hidden: Boolean): Boolean = runCatching {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (hidden) {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            },
            PackageManager.DONT_KILL_APP
        )
        Log.i(TAG, "launcher icon hidden=$hidden")
        // 藏起来就留一条静默通知当退路；恢复时收掉
        if (hidden) Notifications.showIconHidden(context) else Notifications.cancelIconHidden(context)
        true
    }.getOrElse {
        Log.w(TAG, "setComponentEnabledSetting failed", it)
        false
    }
}
