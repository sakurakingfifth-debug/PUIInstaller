package com.panl.installer.core

import java.io.File
import java.util.zip.ZipFile

/**
 * 读取 APK 里的 ABI（`lib/<abi>/` 目录）。
 *
 * 只看目录名，不解压：返回 `arm64-v8a` / `armeabi-v7a` 这类原始名字，
 * 空列表表示没有原生库（纯 Java/Kotlin）。
 */
object ApkAbi {

    fun detect(apk: File): List<String> = runCatching {
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .mapNotNull { entry ->
                    val name = entry.name
                    if (!name.startsWith("lib/")) return@mapNotNull null
                    val rest = name.removePrefix("lib/")
                    val abi = rest.substringBefore('/')
                    if (abi.isBlank() || abi == rest) null else abi
                }
                .distinct()
                .sorted()
                .toList()
        }
    }.getOrDefault(emptyList())

    /** 64 位 ABI：arm64-v8a / x86_64 / mips64。 */
    fun is64(abi: String): Boolean =
        abi == "arm64-v8a" || abi == "x86_64" || abi == "mips64"

    /** 把 ABI 列表压成一句人话。 */
    fun describe(abis: List<String>): String {
        if (abis.isEmpty()) return ""
        val has64 = abis.any { is64(it) }
        val has32 = abis.any { !is64(it) }
        return when {
            has64 && has32 -> "32/64 位"
            has64 -> "64 位"
            else -> "32 位"
        }
    }
}
