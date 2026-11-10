package com.panl.installer.ui.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.provider.OpenableColumns

/**
 * 系统分享（ACTION_SEND / ACTION_SEND_MULTIPLE）带来的文件地址。
 *
 * 文件管理器、MT 管理器、相册之类走的是 `EXTRA_STREAM`（单个）或
 * `EXTRA_STREAM` 的 ArrayList（多选），少数 App 只塞 `ClipData`，
 * 三种都认；多选时只取第一个（安装本来就只能一个一个来）。
 */
@Suppress("DEPRECATION")
fun Intent.sharedStream(): Uri? {
    (getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { return it }
    val list = getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
    (list?.firstOrNull() as? Uri)?.let { return it }
    clipData?.let { clip ->
        if (clip.itemCount > 0) clip.getItemAt(0)?.uri?.let { return it }
    }
    return null
}

/**
 * 这个 uri 对应的文件名（带扩展名）。分享过来的 content:// 的 path 段经常是
 * `msf:1000000123` 这种，光看 path 认不出扩展名，必须问 provider。
 */
fun Context.displayNameOf(uri: Uri?): String? {
    uri ?: return null
    if (uri.scheme == "file") return uri.lastPathSegment
    return runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    }.getOrNull()
}
