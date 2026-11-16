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
 * 磁贴只是「入口」，不是开关 —— 所以固定 `STATE_INACTIVE`（点一下会收起面板并跳转），
 * 不要做成 ACTIVE/INACTIVE 切换，否则用户会以为它代表某个开关状态。
 *
 * API 34 起 `startActivityAndCollapse(Intent)` 已废弃，必须用 PendingIntent 版本，
 * 而且 PendingIntent 得带 `FLAG_IMMUTABLE`，否则系统直接拒。
 */
class InstallerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
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
