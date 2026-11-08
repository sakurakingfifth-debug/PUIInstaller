package com.panl.installer.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import com.panl.installer.R
import com.panl.installer.i18n.tr
import com.panl.installer.ui.MainActivity

/**
 * 安装状态通知。
 *
 * 两条通道分工：
 *  - [CHANNEL_LIVE]：安装过程的**实时活动**（Android 16 Live Updates，ColorOS 叫流体云）。
 *    ProgressStyle + `requestPromotedOngoing`，系统会把它提升成状态栏胶囊；
 *    分段进度条按「准备 / 复制 / 安装」三段加权，胶囊里的短文本跟着阶段走。
 *    这套设计参照 InstallerX-Revived 的 ModernNotificationBuilder。
 *  - [CHANNEL_ID]：装完之后的结果通知（普通通知，autoCancel）。
 */
object Notifications {

    const val CHANNEL_ID = "install_status"

    /** 隐藏桌面图标后留一条静默提示，保证任何 ROM 上都有路可退。 */
    private const val CHANNEL_HIDDEN = "launcher_hidden"
    private const val HIDDEN_ID = 9200
    const val CHANNEL_LIVE = "install_live"

    /** 完成时「弹出来」用的实时通知渠道：带提示音，系统会把它展开一下。 */
    const val CHANNEL_DONE = "install_done"
    private const val DONE_ID = 9100

    const val LIVE_ID = 9000
    private var nextId = 1000

    /** 平台没有公开常量（compileSdk 36 里还没有），值取自 API 37 的 Notification。 */
    private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

    /** 实时通知模式：从通知点「安装」时带给 Activity 的标记。 */
    const val EXTRA_LIVE_AUTO_START = "com.panl.installer.extra.LIVE_AUTO_START"

    /** 实时通知模式：问「装不装」。 */
    const val ACTION_CANCEL = "com.panl.installer.action.CANCEL"

    /** 打开刚装好的应用（成功通知上的按钮）。 */
    const val ACTION_OPEN = "com.panl.installer.action.OPEN"
    const val EXTRA_OPEN_PACKAGE = "com.panl.installer.extra.OPEN_PACKAGE"

    /** 点通知弹窗时带的文案（应用名）。 */

    /**
     * 安装阶段。weight 决定这一段占整条进度条的多少，
     * 总和 10 → 准备 20%、复制 50%、安装 30%。
     */
    enum class Stage(val weight: Int) {
        PREPARING(2),
        COPYING(5),
        INSTALLING(3)
    }

    private val TOTAL_WEIGHT = Stage.entries.sumOf { it.weight }

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_HIDDEN,
                tr("桌面图标", "Launcher icon"),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = tr(
                    "桌面图标隐藏后留一条提示，随时可以恢复",
                    "Shown while the launcher icon is hidden so it can be restored anytime"
                )
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                tr("安装状态", "Install status"),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = tr("应用安装、更新、卸载的结果", "Results of installs, updates and uninstalls")
                setShowBadge(false)
            }
        )
        // 早期版本把这条渠道建成了 HIGH（会变成普通横幅），删掉重建一次
        runCatching { nm.deleteNotificationChannel("install_done") }
        runCatching { nm.deleteNotificationChannel("install_done_live") }
        // 完成时的「实时通知」：用 DEFAULT 才能被提升成胶囊（HIGH 会变成普通横幅），
        // 但保留提示音，系统会把它弹出来一下，而不是安静躺着
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DONE,
                tr("安装完成提醒", "Install finished"),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = tr(
                    "自动安装结束后弹出实时通知",
                    "Live notification shown when an automatic install finishes"
                )
                setShowBadge(false)
            }
        )
        // 实时活动要求渠道不能是 IMPORTANCE_MIN；这里用 DEFAULT，才能被系统提升
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LIVE,
                tr("安装进度", "Install progress"),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = tr(
                    "安装过程中的实时进度（Android 16 实时活动 / ColorOS 流体云）",
                    "Live progress while installing (Android 16 Live Updates / ColorOS Live Alerts)"
                )
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
        )
    }

    /** 普通结果通知。 */
    fun post(context: Context, title: String, text: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_install)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(nextId++, notification) }
    }

    /**
     * 实时通知模式：Activity 一进来就挂一条「正在读取安装包」。
     *
     * 大包复制要一两秒，这期间界面上什么都没有，用户会以为没点到而反复点击。
     */
    fun showReading(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val live = Settings.get(context).notificationsLive
        val builder = Notification.Builder(context, if (live) CHANNEL_LIVE else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_install)
            .setContentTitle(tr("正在读取安装包", "Reading the APK"))
            .setContentText(tr("马上就好", "Just a moment"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(context.getColor(R.color.pui_magenta))
        // 注意：这里**不**申请提升成胶囊。ColorOS 对同一个 id 短时间内重复提升
        // 会直接忽略，紧接着的「待安装」胶囊就浮不出来了。
        runCatching { nm.notify(LIVE_ID, builder.build()) }
    }

    /** 前台服务起手用的通知：不确定进度的「准备中」。 */
    fun progressNotification(
        context: Context,
        label: String,
        text: String,
        percent: Int,
        promoted: Boolean
    ): Notification {
        val builder = Notification.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_install)
            .setContentTitle(if (label.isBlank()) tr("正在准备安装", "Preparing") else tr("正在安装 $label", "Installing $label"))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(context.getColor(R.color.pui_magenta))
        if (Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText(tr("准备中", "Preparing"))
            if (promoted) {
                builder.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            }
        } else {
            builder.setProgress(100, percent, percent == 0)
        }
        return builder.build()
    }

    /**
     * 实时活动：按阶段加权算出总进度。
     *
     * @param fraction 当前阶段内的进度 0..1；null 表示这一段没法量化（写会话之后系统不回报）
     */
    fun showInstall(
        context: Context,
        label: String,
        stage: Stage,
        fraction: Float?,
        text: String,
        promoted: Boolean
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val before = Stage.entries.takeWhile { it != stage }.sumOf { it.weight }
        val base = before * 100 / TOTAL_WEIGHT
        val span = stage.weight * 100 / TOTAL_WEIGHT
        val percent = (base + ((fraction ?: 0f).coerceIn(0f, 1f) * span)).toInt().coerceIn(0, 100)

        val live = Settings.get(context).notificationsLive
        val builder = Notification.Builder(context, if (live) CHANNEL_LIVE else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_install)
            .setContentTitle(tr("正在安装 $label", "Installing $label"))
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(context.getColor(R.color.pui_magenta))
            .setContentIntent(contentIntent(context))

        if (live && Build.VERSION.SDK_INT >= 36) {
            // 三段分段条：走完一段自动点亮下一段，styledByProgress 让系统自己插值
            val style = Notification.ProgressStyle()
                .setProgressSegments(Stage.entries.map { Notification.ProgressStyle.Segment(it.weight) })
                .setStyledByProgress(true)
                .setProgress(percent)
                .setProgressIndeterminate(false)
            builder.setStyle(style)
            // 胶囊里那行短文本：复制阶段显示百分比，其余显示状态词
            builder.setShortCriticalText(
                when (stage) {
                    Stage.COPYING -> "$percent%"
                    Stage.PREPARING -> tr("准备中", "Preparing")
                    Stage.INSTALLING -> tr("安装中", "Installing")
                }
            )
            if (promoted) {
                builder.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            }
        } else {
            builder.setProgress(100, percent, false)
        }

        runCatching { nm.notify(LIVE_ID, builder.build()) }
    }

    /**
     * 装完了。
     *
     * @param popup true = 用高优先级渠道弹一条横幅（自动安装时用，用户不需要盯着通知栏）；
     *              false = 胶囊原地翻成「完成」，15 秒后自动消失。
     */
    fun showInstalled(context: Context, label: String, packageName: String, popup: Boolean = false) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val settings = Settings.get(context)
        val live = settings.notificationsLive
        // 0 = 从不自动清除；负数按 0 处理
        val clearMs = settings.autoClearSeconds.coerceAtLeast(0) * 1000L
        val open = PendingIntent.getBroadcast(
            context, 1003,
            Intent(context, InstallActionReceiver::class.java)
                .setAction(ACTION_OPEN)
                .putExtra(EXTRA_OPEN_PACKAGE, packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 点通知直接打开刚装的应用（原来的「点击通知时显示对话框」选项已经删掉）
        val tap = open
        val icon = Icon.createWithResource(context, R.drawable.ic_stat_install)

        if (popup) {
            // 仍然是「实时通知」（能提升成胶囊），只是换到会响的渠道，让它自己弹出来
            cancelProgress(context)
            val builder = Notification.Builder(context, CHANNEL_DONE)
                .setSmallIcon(R.drawable.ic_stat_install)
                .setContentTitle(tr("$label 已安装", "$label installed"))
                .setContentText(tr("点击打开", "Tap to open"))
                .setContentIntent(tap)
                .setColor(context.getColor(R.color.pui_magenta))
                .setCategory(Notification.CATEGORY_STATUS)
                .setOngoing(true)
                .setAutoCancel(true)
                .addAction(Notification.Action.Builder(icon, tr("打开", "Open"), open).build())
            if (clearMs > 0) builder.setTimeoutAfter(clearMs)
            if (Build.VERSION.SDK_INT >= 36) {
                builder.setShortCriticalText(tr("完成", "Done"))
                builder.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
            }
            runCatching { nm.notify(DONE_ID, builder.build()) }
            return
        }

        val builder = Notification.Builder(context, if (live) CHANNEL_LIVE else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_install)
            .setContentTitle(tr("$label 已安装", "$label installed"))
            .setContentText(tr("点击打开", "Tap to open"))
            .setContentIntent(tap)
            .setColor(context.getColor(R.color.pui_magenta))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .addAction(Notification.Action.Builder(icon, tr("打开", "Open"), open).build())

        // 实时活动必须 ongoing 才会被提升成胶囊；普通通知保持可滑动清除
        if (live) builder.setOngoing(true)
        if (clearMs > 0) builder.setTimeoutAfter(clearMs)

        if (live && Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText(tr("完成", "Done"))
            builder.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        }
        runCatching { nm.notify(LIVE_ID, builder.build()) }
    }

    /** 装失败：胶囊变成失败态，30 秒后自动收掉。 */
    fun showFailed(context: Context, label: String, message: String) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val settings = Settings.get(context)
        val live = settings.notificationsLive
        val clearMs = settings.autoClearSeconds.coerceAtLeast(0) * 1000L
        val builder = Notification.Builder(context, if (live) CHANNEL_LIVE else CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_install)
            .setContentTitle(tr("$label 安装失败", "$label failed to install"))
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(contentIntent(context))
            .setColor(context.getColor(R.color.pui_magenta))
            .setAutoCancel(true)
            // 失败态留一会儿：没设自动清除时按 30 秒兜底
            .setTimeoutAfter(if (clearMs > 0) clearMs else 30_000L)

        if (live) builder.setOngoing(true)
        if (live && Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText(tr("失败", "Failed"))
            builder.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        }
        runCatching { nm.notify(LIVE_ID, builder.build()) }
    }

    /** 安装结束（无论成败）都要撤掉进度胶囊。 */
    fun cancelProgress(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(LIVE_ID) }
    }

    /** 系统是否允许本应用发「被提升」的通知（用户可能在设置里关了）。 */
    fun canPromote(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 36) return false
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return runCatching { nm.canPostPromotedNotifications() }.getOrDefault(false)
    }

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /**
     * 桌面图标隐藏后的安全网。
     *
     * 拨号暗号在部分 ROM（含 ColorOS）上收不到 —— 那条广播带
     * `CONTROL_INCALL_EXPERIENCE` 签名权限。所以藏图标的同时留一条静默通知，
     * 点一下就能打开应用或直接恢复，避免「图标没了、暗号又不灵」直接失联。
     */
    fun showIconHidden(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val toggle = PendingIntent.getBroadcast(
            context, 1004,
            Intent(context, SecretCodeReceiver::class.java)
                .setAction(SecretCodeReceiver.ACTION_TOGGLE_ICON),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val open = PendingIntent.getActivity(
            context, 1005,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(context, CHANNEL_HIDDEN)
            .setSmallIcon(R.drawable.ic_stat_install)
            .setContentTitle(tr("桌面图标已隐藏", "Launcher icon hidden"))
            .setContentText(
                tr("点按打开应用，或点「恢复」放回桌面", "Tap to open the app, or tap Restore to put it back")
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_stat_import),
                    tr("恢复", "Restore"),
                    toggle
                ).build()
            )
        runCatching { nm.notify(HIDDEN_ID, builder.build()) }
    }

    /**
     * 兜底：暗号打不开界面时（没有 root、又被 BAL 拦住）发一条可点开的通知。
     */
    fun showOpenInstaller(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val open = PendingIntent.getActivity(
            context, 1006,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_install)
            .setContentTitle(tr("打开安装器", "Open the installer"))
            .setContentText(tr("点按打开安装器", "Tap to open the installer"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setShowWhen(false)
        runCatching { nm.notify(9300, builder.build()) }
    }

    fun cancelIconHidden(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(HIDDEN_ID) }
    }

    // ---------------- 实时通知模式：先问一句「装不装」 ----------------

    /**
     * 在实时通知里问用户装不装。
     *
     * 「安装」直接叫服务（APK 已经落到缓存，不需要 uri 权限）；
     * 「取消」也是叫服务，由它把通知收掉。
     */
    fun showInstallPrompt(
        context: Context,
        label: String,
        packageName: String,
        file: java.io.File,
        sourceUri: android.net.Uri?
    ) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val confirm = PendingIntent.getForegroundService(
            context, 1001,
            Intent(context, LiveInstallService::class.java)
                .setAction(LiveInstallService.ACTION_START)
                .putExtra(LiveInstallService.EXTRA_FILE, file.absolutePath)
                .putExtra(LiveInstallService.EXTRA_LABEL, label)
                .putExtra(LiveInstallService.EXTRA_PACKAGE, packageName)
                .putExtra(LiveInstallService.EXTRA_URI, sourceUri?.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getForegroundService(
            context, 1002,
            Intent(context, LiveInstallService::class.java)
                .setAction(LiveInstallService.ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 两个按钮各用各的图标：安装 = 收件箱箭头，取消 = 撤销
        val installIcon = Icon.createWithResource(context, R.drawable.ic_stat_import)
        val cancelIcon = Icon.createWithResource(context, R.drawable.ic_stat_undo)

        val builder = Notification.Builder(context, CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_install)
            .setContentTitle(tr("安装 $label", "Install $label"))
            .setContentText(tr("点「安装」继续，或取消", "Tap Install to continue, or cancel"))
            .setColor(context.getColor(R.color.pui_magenta))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setContentIntent(confirm)
            .addAction(Notification.Action.Builder(installIcon, tr("安装", "Install"), confirm).build())
            .addAction(Notification.Action.Builder(cancelIcon, tr("取消", "Cancel"), cancel).build())

        if (Build.VERSION.SDK_INT >= 36) {
            builder.setShortCriticalText(tr("待安装", "Ready"))
            builder.addExtras(Bundle().apply { putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true) })
        }
        runCatching { nm.notify(LIVE_ID, builder.build()) }
            .onFailure { android.util.Log.e("LiveNotify", "prompt notify failed", it) }
        android.util.Log.i("LiveNotify", "prompt posted: $label")
    }
}
