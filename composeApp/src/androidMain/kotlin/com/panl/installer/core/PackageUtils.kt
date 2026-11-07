package com.panl.installer.core

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.LruCache
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.panl.installer.i18n.tr

object PackageUtils {

    /** 是否具备系统安装权限（决定能否静默安装 / 卸载）。 */
    fun hasInstallPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.INSTALL_PACKAGES) ==
            PackageManager.PERMISSION_GRANTED

    fun hasDeletePermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.DELETE_PACKAGES) ==
            PackageManager.PERMISSION_GRANTED

    /** 是否被系统选为默认安装器（解析 .apk 的 VIEW intent 落到自己身上）。 */
    fun isDefaultInstaller(context: Context): Boolean {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.parse("file:///sdcard/probe.apk"),
                "application/vnd.android.package-archive"
            )
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        val resolved = runCatching {
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
        return resolved?.activityInfo?.packageName == context.packageName
    }

    /** 已安装应用列表。默认只列用户应用，系统应用按需展开。 */
    suspend fun loadInstalled(context: Context, includeSystem: Boolean): List<InstalledApp> =
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = runCatching { pm.getInstalledPackages(0) }.getOrDefault(emptyList())
            val collator = Collator.getInstance(Locale.CHINA)

            packages.asSequence()
                .filter { includeSystem || ((it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM) == 0 }
                .filter { it.packageName != "android" }
                .mapNotNull { pi ->
                    val ai = pi.applicationInfo ?: return@mapNotNull null
                    val apkPath = ai.sourceDir
                    InstalledApp(
                        packageName = pi.packageName,
                        label = runCatching { pm.getApplicationLabel(ai).toString() }
                            .getOrDefault(pi.packageName),
                        versionName = pi.versionName ?: "—",
                        versionCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong(),
                        isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        hasUpdates = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||
                            ((ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                                apkPath?.startsWith("/data/") == true),
                        firstInstallTime = pi.firstInstallTime,
                        lastUpdateTime = pi.lastUpdateTime,
                        apkPath = apkPath,
                        apkSize = apkPath?.let { runCatching { java.io.File(it).length() }.getOrDefault(0L) } ?: 0L,
                        targetSdk = ai.targetSdkVersion,
                        minSdk = if (Build.VERSION.SDK_INT >= 24) ai.minSdkVersion else 0,
                        signers = emptyList(),
                        icon = null
                    )
                }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
                .toList()
        }
}

/** 应用图标缓存：列表滚动时不重复解码。 */
object IconLoader {
    private val cache = LruCache<String, Bitmap>(256)

    fun icon(context: Context, packageName: String): Bitmap? {
        cache.get(packageName)?.let { return it }
        val bitmap = runCatching {
            context.packageManager.getApplicationIcon(packageName).toBitmapSafe(144)
        }.getOrNull()
        if (bitmap != null) cache.put(packageName, bitmap)
        return bitmap
    }

    fun put(packageName: String, bitmap: Bitmap?) {
        if (bitmap != null) cache.put(packageName, bitmap)
    }
}

object Formatters {

    fun size(bytes: Long): String = when {
        bytes <= 0 -> "—"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
        bytes < 1024L * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024f / 1024f)
        else -> String.format(Locale.US, "%.2f GB", bytes / 1024f / 1024f / 1024f)
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)

    fun date(millis: Long): String =
        if (millis <= 0) "—" else dateFormat.format(Date(millis))

    fun sdkLevel(level: Int): String {
        val name = when (level) {
            37 -> "Android 17"
            36 -> "Android 16"
            35 -> "Android 15"
            34 -> "Android 14"
            33 -> "Android 13"
            32 -> "Android 12L"
            31 -> "Android 12"
            30 -> "Android 11"
            29 -> "Android 10"
            28 -> "Android 9"
            27 -> "Android 8.1"
            26 -> "Android 8.0"
            25 -> "Android 7.1"
            24 -> "Android 7.0"
            in 1..23 -> "Android ${level}"
            else -> return "—"
        }
        return "$name（API $level）"
    }

    fun installLocation(value: Int): String = when (value) {
        0 -> tr("自动", "Auto")
        1 -> tr("仅内部存储", "Internal only")
        2 -> tr("优先外部存储", "Prefer external")
        else -> tr("自动", "Auto")
    }
}
