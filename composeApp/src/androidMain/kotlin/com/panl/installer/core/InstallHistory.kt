package com.panl.installer.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 安装记录：装成功一次就存一条，包含**当时那个 APK** 的信息。
 *
 * 为什么要存快照而不是装完再去查已安装应用：应用可能被卸载 / 被覆盖成新版本，
 * 事后查到的就不是当时装的那个包了。图标也一起压成 PNG 存下来（96px，几 KB）。
 *
 * 存储：`files/install_history.json`，最多留 [MAX_ENTRIES] 条，新的在前。
 */
data class InstallRecord(
    /** 记录时间（毫秒），同时当 id 用。 */
    val time: Long,
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val sizeBytes: Long,
    /** NEW / UPDATE / DOWNGRADE / REINSTALL，见 [VersionRelation]。 */
    val relation: String,
    val signerSubject: String?,
    val signerSha256: String?,
    val isAosp: Boolean,
    val sourceLabel: String?,
    val minSdk: Int,
    val targetSdk: Int,
    val abis: List<String>,
    val iconBase64: String?
) {
    fun icon(): Bitmap? = iconBase64?.let {
        runCatching {
            val bytes = Base64.decode(it, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }.getOrNull()
    }

    val relationEnum: VersionRelation
        get() = runCatching { VersionRelation.valueOf(relation) }.getOrDefault(VersionRelation.NEW)
}

object InstallHistory {

    private const val TAG = "InstallHistory"
    private const val FILE_NAME = "install_history.json"
    private const val MAX_ENTRIES = 100
    private const val ICON_PX = 96

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    /** 新的在前。读失败（文件损坏 / 首次运行）就当空。 */
    fun read(context: Context): List<InstallRecord> = runCatching {
        val f = file(context)
        if (!f.exists()) return emptyList()
        val arr = JSONArray(f.readText())
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(o.toRecord())
            }
        }
    }.getOrElse {
        Log.w(TAG, "read failed", it)
        emptyList()
    }

    fun find(context: Context, time: Long): InstallRecord? = read(context).firstOrNull { it.time == time }

    /** 装成功后调用。同一包同一版本 10 秒内重复写只留一条。 */
    fun record(context: Context, info: ApkInfo) {
        runCatching {
            val now = System.currentTimeMillis()
            val existing = read(context)
            val head = existing.firstOrNull()
            if (head != null && head.packageName == info.packageName &&
                head.versionCode == info.versionCode && now - head.time < 10_000
            ) {
                return
            }
            val signer = info.primarySigner
            val record = InstallRecord(
                time = now,
                packageName = info.packageName,
                label = info.label,
                versionName = info.versionName,
                versionCode = info.versionCode,
                sizeBytes = info.sizeBytes,
                relation = info.relation.name,
                signerSubject = signer?.subject,
                signerSha256 = signer?.sha256?.chunked(2)?.joinToString(":")?.uppercase(),
                isAosp = signer?.isAosp == true,
                sourceLabel = info.sourceLabel,
                minSdk = info.minSdk,
                targetSdk = info.targetSdk,
                abis = info.abis,
                iconBase64 = encodeIcon(info.icon)
            )
            val list = (listOf(record) + existing).take(MAX_ENTRIES)
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            file(context).writeText(arr.toString())
        }.onFailure { Log.w(TAG, "record failed", it) }
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /** 按记录时间删掉若干条（只动历史，不影响已安装的应用）。 */
    fun delete(context: Context, times: Set<Long>) {
        if (times.isEmpty()) return
        runCatching {
            val kept = read(context).filterNot { it.time in times }
            val arr = JSONArray()
            kept.forEach { arr.put(it.toJson()) }
            file(context).writeText(arr.toString())
        }.onFailure { Log.w(TAG, "delete failed", it) }
    }

    private fun encodeIcon(icon: Bitmap?): String? {
        if (icon == null) return null
        return runCatching {
            val scaled = if (icon.width > ICON_PX || icon.height > ICON_PX) {
                val ratio = ICON_PX.toFloat() / maxOf(icon.width, icon.height)
                Bitmap.createScaledBitmap(
                    icon,
                    (icon.width * ratio).toInt().coerceAtLeast(1),
                    (icon.height * ratio).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                icon
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
        }.getOrNull()
    }

    private fun InstallRecord.toJson() = JSONObject().apply {
        put("time", time)
        put("packageName", packageName)
        put("label", label)
        put("versionName", versionName)
        put("versionCode", versionCode)
        put("sizeBytes", sizeBytes)
        put("relation", relation)
        put("signerSubject", signerSubject ?: "")
        put("signerSha256", signerSha256 ?: "")
        put("isAosp", isAosp)
        put("sourceLabel", sourceLabel ?: "")
        put("minSdk", minSdk)
        put("targetSdk", targetSdk)
        put("abis", JSONArray(abis))
        put("icon", iconBase64 ?: "")
    }

    private fun JSONObject.toRecord() = InstallRecord(
        time = optLong("time"),
        packageName = optString("packageName"),
        label = optString("label"),
        versionName = optString("versionName"),
        versionCode = optLong("versionCode"),
        sizeBytes = optLong("sizeBytes"),
        relation = optString("relation", VersionRelation.NEW.name),
        signerSubject = optString("signerSubject").ifBlank { null },
        signerSha256 = optString("signerSha256").ifBlank { null },
        isAosp = optBoolean("isAosp"),
        sourceLabel = optString("sourceLabel").ifBlank { null },
        minSdk = optInt("minSdk"),
        targetSdk = optInt("targetSdk"),
        abis = optJSONArray("abis")?.let { a -> (0 until a.length()).map { a.optString(it) } } ?: emptyList(),
        iconBase64 = optString("icon").ifBlank { null }
    )
}
