package com.panl.installer

import android.app.Application
import android.content.Context
import com.panl.installer.core.InstallerLog
import com.panl.installer.core.Notifications
import com.panl.installer.core.Settings
import com.panl.installer.i18n.AppLanguage
import com.panl.installer.corepatch.CorePatchPrefs
import com.panl.installer.i18n.I18n
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

class InstallerApp : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        // 早点连 LSPosed：连上才拿得到远程配置（核心破解的开关都存在那边）
        runCatching {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) = CorePatchPrefs.bind(service, this@InstallerApp)
                override fun onServiceDied(service: XposedService) = CorePatchPrefs.unbind()
            })
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 提前把设置读进内存，避免第一帧里做磁盘 IO。
        // 系统应用可能在 direct boot 阶段被拉起，此时 CE 存储不可用，包一层避免启动即崩。
        runCatching { Settings.get(this) }
        // 语言要在建通知渠道之前定下来，否则渠道名会停在中文
        runCatching { I18n.apply(AppLanguage.fromId(Settings.get(this).language)) }
        runCatching { Notifications.createChannel(this) }
        // 任何没被 catch 的异常都先落到「日志」页里，方便用户直接分享出来
        runCatching { InstallerLog.installCrashHandler(this) }
    }
}
