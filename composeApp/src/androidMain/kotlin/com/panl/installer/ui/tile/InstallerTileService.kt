package com.panl.installer.ui.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.panl.installer.R
import com.panl.installer.ui.MainActivity

/**
 * 控制中心磁贴：一键打开安装器主页面。
 *
 * 磁贴本身是「入口」，但状态跟着安装器走：它在前台时点亮，不在前台时是普通状态 ——
 * 这样既能一眼看出安装器是否在运行，又不会让人以为它是个开关。
 *
 * API 34 起 `startActivityAndCollapse(Intent)` 已废弃，必须用 PendingIntent 版本，
 * 而且 PendingIntent 得带 `FLAG_IMMUTABLE`，否则系统直接拒。
 */
/**
 * 安装器是否正在前台。
 *
 * 磁贴服务和应用跑在同一个进程里，直接共享这个标记就够了（进程被杀 → 重新初始化为 false，
 * 正好等于「没在跑」）。用 onStart/onStop 而不是 onResume/onPause：下拉控制中心只会触发
 * onPause，用后者会让磁贴在你正看着它的时候反而变灰。
 */
object TileState {
    @Volatile
    var appVisible: Boolean = false
}

class InstallerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            // 安装器在前台时点亮（ACTIVE 会被系统染成强调色），退到后台就恢复灰
            state = if (TileState.appVisible) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.tile_label)
            runCatching {
                icon = Icon.createWithResource(this@InstallerTileService, R.drawable.ic_tile_installer)
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        runCatching { startActivityAndCollapse(pending) }
    }

    /** 磁贴被拖进控制中心后，系统会调这个同步一次状态。 */
    override fun onTileAdded() {
        super.onTileAdded()
        onStartListening()
    }
}
