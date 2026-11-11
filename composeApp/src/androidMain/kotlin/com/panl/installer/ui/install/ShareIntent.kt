package com.panl.installer.ui.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import android.provider.OpenableColumns

/**
 * 系统分享（ACTION_SEND / ACTION_SEND_MULTIPLE）带来的所有文件地址。
 *
 * 多选分享用的是 `EXTRA_STREAM` 的 ArrayList（ACTION_SEND_MULTIPLE），
 * 少数 App 只塞 `ClipData`，两种都认；单个分享也走这里（返回 1 个）。
 */
@Suppress("DEPRECATION")
fun Intent.sharedStreams(): List<Uri> {
    val out = ArrayList<Uri>(2)
    (getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { out += it }
    getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.forEach { item ->
        (item as? Uri)?.let { out += it }
    }
    clipData?.let { clip ->
        for (i in 0 until clip.itemCount) clip.getItemAt(i)?.uri?.let { out += it }
    }
    // 同一个文件可能在 EXTRA_STREAM 和 ClipData 里各出现一次
    return out.distinct()
}

/** 取第一个分享文件（单文件安装的快捷方式）。 */
fun Intent.sharedStream(): Uri? = sharedStreams().firstOrNull()

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
