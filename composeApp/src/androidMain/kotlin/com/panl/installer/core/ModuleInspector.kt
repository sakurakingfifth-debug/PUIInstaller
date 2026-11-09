package com.panl.installer.core

import java.io.File
import java.util.zip.ZipFile

/**
 * 一个 Magisk / KernelSU / APatch 模块的基本信息（来自 zip 里的 `module.prop`）。
 */
data class ModuleInfo(
    val file: File,
    val id: String,
    val name: String,
    val version: String,
    val author: String,
    val description: String
)

/**
 * 判断一个 zip 是不是刷机模块，并把 `module.prop` 里的字段读出来。
 *
 * 三家（Magisk / KernelSU / APatch）的模块格式是一样的：zip 根目录放一个
 * `module.prop`，里面是 `key=value` 行。所以只认这一个文件就够了。
 */
object ModuleInspector {

    fun inspect(file: File): ModuleInfo? {
        if (!file.isFile || !file.name.endsWith(".zip", ignoreCase = true)) return null
        return runCatching {
            ZipFile(file).use { zip ->
                val entry = zip.getEntry("module.prop") ?: return@use null
                val props = zip.getInputStream(entry).bufferedReader().useLines { lines ->
                    lines.mapNotNull { line ->
                        val i = line.indexOf('=')
                        if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
                    }.toMap()
                }
                val id = props["id"]?.takeIf { it.isNotBlank() } ?: return@use null
                ModuleInfo(
                    file = file,
                    id = id,
                    name = props["name"]?.takeIf { it.isNotBlank() } ?: id,
                    version = props["version"].orEmpty(),
                    author = props["author"].orEmpty(),
                    description = props["description"].orEmpty()
                )
            }
        }.getOrNull()
    }
}
