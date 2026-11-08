package com.panl.installer.core

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.graphics.drawable.toBitmap
import java.io.File

/** 已安装应用的最小快照，用于版本对比与列表展示。 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val isSystem: Boolean,
    /** 系统应用是否被更新过（有可卸载的更新）。系统应用本体删不掉，只能卸更新。 */
    val hasUpdates: Boolean,
    val firstInstallTime: Long,
    val lastUpdateTime: Long,
    val apkPath: String?,
    val apkSize: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val signers: List<SignerInfo>,
    val icon: Bitmap?
)

/** 待安装 APK 的完整信息。 */
data class ApkInfo(
    val file: File,
    val packageName: String,
    val label: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val sizeBytes: Long,
    val icon: Bitmap?,
    val permissions: List<String>,
    val signers: List<SignerInfo>,
    val installed: InstalledApp?,
    val isDebuggable: Boolean,
    val isSystemApp: Boolean,
    val installLocation: Int,
    /** 欧加系（OPPO/一加/realme）OSDK 目标区间，来自 AndroidManifest 的 meta-data。 */
    val minOsdk: String? = null,
    val maxOsdk: String? = null,
    /** 这次安装是谁发起的（发起应用的名字），拿不到就是 null。 */
    val sourceLabel: String? = null,
    /** native 库是否全部按 16 KB 对齐；null 表示没有 native 库。 */
    val supports16K: Boolean? = null,
    /** APK 里带的 ABI（lib/<abi>/），空表示纯 Java。 */
    val abis: List<String> = emptyList(),
    /** AndroidManifest 里的 sharedUserId（拿不到就是 null）。 */
    val sharedUserId: String? = null,
    /** 拆分包（.apks/.xapk/.apkm）解出来的分片；空表示普通单 apk。 */
    val splits: List<File> = emptyList()
) {
    val relation: VersionRelation
        get() {
            val old = installed ?: return VersionRelation.NEW
            if (old.packageName != packageName) return VersionRelation.NEW
            return when {
                versionCode > old.versionCode -> VersionRelation.UPDATE
                versionCode < old.versionCode -> VersionRelation.DOWNGRADE
                else -> VersionRelation.REINSTALL
            }
        }

    /** 主签名者（多签名时取第一个）。 */
    val primarySigner: SignerInfo? get() = signers.firstOrNull()

    /** 是否与已安装版本签名一致 —— 不一致时覆盖安装必然失败。 */
    val signatureMatchesInstalled: Boolean?
        get() {
            val old = installed ?: return null
            if (old.signers.isEmpty() || signers.isEmpty()) return null
            return old.signers.map { it.sha256 }.toSet() == signers.map { it.sha256 }.toSet()
        }
}

enum class VersionRelation { NEW, UPDATE, DOWNGRADE, REINSTALL }

/** meta-data 里可能是 String 也可能是数字，统一转成字符串。 */
private fun android.os.Bundle?.metaString(key: String): String? =
    runCatching { this?.get(key)?.toString()?.trim()?.takeIf { it.isNotEmpty() } }.getOrNull()

object ApkInspector {

    /** 解析一个 APK 文件（必须是本进程可读的真实路径）。 */
    fun inspect(context: Context, file: File): ApkInfo? {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or
            PackageManager.GET_SIGNING_CERTIFICATES or
            PackageManager.GET_META_DATA

        val pi: PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(file.absolutePath, flags)
        } ?: return null

        val ai = pi.applicationInfo ?: return null
        // getApplicationIcon 要靠这两个字段去找 APK 里的资源
        ai.sourceDir = file.absolutePath
        ai.publicSourceDir = file.absolutePath

        val label = runCatching { pm.getApplicationLabel(ai).toString() }
            .getOrDefault(pi.packageName ?: file.name)

        val icon = runCatching { pm.getApplicationIcon(ai) }.getOrNull()?.toBitmapSafe()

        val signers = readSigners(pi)
        val installed = readInstalled(context, pi.packageName)

        return ApkInfo(
            file = file,
            packageName = pi.packageName ?: return null,
            label = label,
            versionName = pi.versionName ?: "—",
            versionCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong(),
            minSdk = if (Build.VERSION.SDK_INT >= 24) ai.minSdkVersion else 0,
            targetSdk = ai.targetSdkVersion,
            sizeBytes = file.length(),
            icon = icon,
            permissions = pi.requestedPermissions?.toList().orEmpty(),
            signers = signers,
            installed = installed,
            isDebuggable = (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0,
            isSystemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            installLocation = pi.installLocation,
            minOsdk = ai.metaData.metaString("minOsdkVersion"),
            maxOsdk = ai.metaData.metaString("maxOsdkVersion"),
            supports16K = PageSizeCheck.detect(file),
            abis = ApkAbi.detect(file),
            sharedUserId = runCatching { pi.sharedUserId }.getOrNull()
        )
    }

    /** 只读签名（签名检测页用，不解析图标等重资源）。 */
    fun inspectSignatures(context: Context, file: File): ApkInfo? = inspect(context, file)

    fun readInstalled(context: Context, packageName: String): InstalledApp? {
        val pm = context.packageManager
        val pi = runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                pm.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(
                        (PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.GET_META_DATA).toLong()
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
        }.getOrNull() ?: return null

        val ai = pi.applicationInfo ?: return null
        val label = runCatching { pm.getApplicationLabel(ai).toString() }.getOrDefault(packageName)
        val icon = runCatching { pm.getApplicationIcon(ai) }.getOrNull()?.toBitmapSafe()
        val apkPath = ai.sourceDir
        val size = apkPath?.let { runCatching { File(it).length() }.getOrDefault(0L) } ?: 0L

        val systemApp = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        // 更新过的系统应用：要么带 FLAG_UPDATED_SYSTEM_APP，要么 APK 已经落到 /data 里
        val updated = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 ||
            (systemApp && apkPath.startsWith("/data/"))

        return InstalledApp(
            packageName = packageName,
            label = label,
            versionName = pi.versionName ?: "—",
            versionCode = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong(),
            isSystem = systemApp,
            hasUpdates = updated,
            firstInstallTime = pi.firstInstallTime,
            lastUpdateTime = pi.lastUpdateTime,
            apkPath = apkPath,
            apkSize = size,
            targetSdk = ai.targetSdkVersion,
            minSdk = if (Build.VERSION.SDK_INT >= 24) ai.minSdkVersion else 0,
            signers = readSigners(pi),
            icon = icon
        )
    }

    private fun readSigners(pi: PackageInfo): List<SignerInfo> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            pi.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            pi.signatures
        } ?: return emptyList()
        return signatures.mapNotNull { ApkSignature.fromBytes(it.toByteArray()) }
    }
}

internal fun Drawable.toBitmapSafe(size: Int = 192): Bitmap? = runCatching {
    toBitmap(size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()

/** 把一个 APK 的 versionName / versionCode 拼成界面上的单行文本。 */
fun ApkInfo.versionText(): String = "$versionName ($versionCode)"

fun InstalledApp.versionText(): String = "$versionName ($versionCode)"
