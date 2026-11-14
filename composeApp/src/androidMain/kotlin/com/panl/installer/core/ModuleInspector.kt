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
    val description: String,
    /**
     * 刷入时要按键（音量上/下、电源）才会有反应。
     *
     * 这类模块的 customize.sh 会调 `chooseport` / 直接读 `getevent`，
     * 而我们是 `su -c "ksud module install"` 跑命令，没有 TTY 也没有按键来源，
     * 脚本就卡在那里等输入 —— 必须提前告诉用户，并给命令加超时。
     */
    val needsKeyInput: Boolean = false
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
                val needsKey = scriptsNeedKeyInput(zip)
                ModuleInfo(
                    file = file,
                    id = id,
                    name = props["name"]?.takeIf { it.isNotBlank() } ?: id,
                    version = props["version"].orEmpty(),
                    author = props["author"].orEmpty(),
                    description = props["description"].orEmpty(),
                    needsKeyInput = needsKey
                )
            }
        }.getOrNull()
    }

    /** 出现这些字样就说明脚本在等按键（Magisk 的 chooseport 走 getevent，KSU 的菜单同理）。 */
    private val KEY_INPUT_HINTS = listOf(
        "chooseport", "getevent", "key_volumeup", "key_volumedown",
        "volumeup", "volumedown", "volume_key", "key_power", "keycheck"
    )

    /**
     * 扫 zip 根目录和一级目录下的 .sh（customize.sh / install.sh / uninstall.sh 等），
     * 找有没有等按键的调用。只看前 200KB，避免超大脚本拖慢解析。
     */
    private fun scriptsNeedKeyInput(zip: java.util.zip.ZipFile): Boolean {
        val entries = java.util.Collections.list(zip.entries())
            .filter { !it.isDirectory && it.name.endsWith(".sh", ignoreCase = true) }
            .filter { it.name.count { c -> c == '/' } <= 1 }
            .take(8)
        for (entry in entries) {
            val text = runCatching {
                zip.getInputStream(entry).bufferedReader().use { reader ->
                    val sb = StringBuilder()
                    val buffer = CharArray(4096)
                    while (sb.length < 200_000) {
                        val n = reader.read(buffer)
                        if (n <= 0) break
                        sb.append(buffer, 0, n)
                    }
                    sb.toString().lowercase()
                }
            }.getOrNull() ?: continue
            if (KEY_INPUT_HINTS.any { text.contains(it) }) return true
        }
        return false
    }
}
