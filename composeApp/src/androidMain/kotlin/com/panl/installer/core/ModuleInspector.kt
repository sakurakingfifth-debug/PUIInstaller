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
    val needsKeyInput: Boolean = false,
    /**
     * module.prop 读不出来（条目存在，但用了 Java 解不了的压缩方式）。
     *
     * 新版 LSPosed / Vector 的模块包用 **zstd**（zip 压缩方法 93/95）压 module.prop、脚本和 daemon，
     * `java.util.zip` 只认 STORED/DEFLATE，读就会抛异常。但 KernelSU 的 ksud（Rust 的 zip 库）
     * 完全解得开，所以这种包**能刷**，只是我们拿不到名字/版本这些信息 —— 退化成用文件名当标题，
     * 刷入后再从 /data/adb/modules 里补。
     */
    val metaUnreadable: Boolean = false
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
        // 不能用 ZipFile：zstd 压缩的条目会让它连档案都打不开（见 ZipLite 注释）
        val entries = ZipLite.entries(file) ?: return null
        val propEntry = entries.firstOrNull { it.name == "module.prop" } ?: return null
        val text = ZipLite.read(file, propEntry)
        if (text == null) {
            // module.prop 在，但读不出内容（zstd）：包本身合法，照样当模块，信息退化
            return ModuleInfo(
                file = file,
                id = "",
                name = file.name.removeSuffix(".zip").removeSuffix(".ZIP"),
                version = "",
                author = "",
                description = "",
                metaUnreadable = true
            )
        }
        val props = text.lineSequence().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) null else line.substring(0, i).trim() to line.substring(i + 1).trim()
        }.toMap()
        val id = props["id"]?.takeIf { it.isNotBlank() } ?: return null
        return ModuleInfo(
            file = file,
            id = id,
            name = props["name"]?.takeIf { it.isNotBlank() } ?: id,
            version = props["version"].orEmpty(),
            author = props["author"].orEmpty(),
            description = props["description"].orEmpty(),
            needsKeyInput = scriptsNeedKeyInput(file, entries)
        )
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
    private fun scriptsNeedKeyInput(file: File, entries: List<ZipLite.Entry>): Boolean {
        val scripts = entries
            .filter { it.name.endsWith(".sh", ignoreCase = true) && it.name.count { c -> c == '/' } <= 1 }
            .take(8)
        for (script in scripts) {
            val text = ZipLite.read(file, script)?.lowercase() ?: continue
            if (KEY_INPUT_HINTS.any { text.contains(it) }) return true
        }
        return false
    }
}
