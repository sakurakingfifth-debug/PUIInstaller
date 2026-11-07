package com.panl.installer.corepatch

import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.panl.installer.corepatch.Config
import io.github.libxposed.service.XposedService

/**
 * 设置页 ↔ 核心破解模块之间的桥。
 *
 * 开关存在 LSPosed 的远程 prefs（"conf"）里：应用进程写，system_server 里的
 * 模块用同一份读，所以改完不用重启，下一次安装就按新配置走。
 */
object CorePatchPrefs {

    /** 是否连上了 LSPosed 服务——连上说明模块已被 LSPosed 识别并启用。 */
    var connected by mutableStateOf(false)
        private set

    /** LSPosed 报告的生效范围，包含 system 才是真的在系统框架里跑。 */
    var scope: List<String> by mutableStateOf(emptyList())
        private set

    private var service: XposedService? = null
    private var appContext: android.content.Context? = null

    /**
     * 模块发布到 Settings.Global 的真实状态；应用侧读远程 prefs 是读不到别人写的值的。
     * 用 Compose 状态存，刷新后界面能跟着重组。
     */
    var state: Map<String, Boolean> by mutableStateOf(emptyMap())
        private set

    /** 本次会话里用户改过的值，优先于快照。 */
    private val overrides = mutableMapOf<String, Boolean>()

    /** 模块是否真正作用于系统框架。 */
    val activeInSystem: Boolean get() = connected && scope.contains("system")

    fun bind(xposedService: XposedService, context: android.content.Context) {
        runCatching {
            service = xposedService
            appContext = context.applicationContext
            scope = xposedService.scope.toList()
            connected = true
            refreshState()
        }
    }

    fun unbind() {
        service = null
        scope = emptyList()
        connected = false
    }

    /**
     * 每次都重新取一次代理。
     *
     * 之前把 `getRemotePreferences` 的结果缓存在字段里，读到的永远是绑定那一刻的快照
     * （基本全 false），界面上就会出现「模块里明明开着、这里全显示关」。
     */
    private fun prefs(): SharedPreferences? =
        runCatching { service?.getRemotePreferences("conf") }.getOrNull()

    /** 重新从模块发布的状态里取一次（页面打开时调）。 */
    fun refreshState() {
        val ctx = appContext ?: return
        state = runCatching {
            val raw = android.provider.Settings.Global.getString(
                ctx.contentResolver,
                Config.GLOBAL_STATE_KEY
            ) ?: return@runCatching emptyMap()
            raw.split(';').mapNotNull { seg ->
                val i = seg.indexOf('=')
                if (i <= 0) null else seg.substring(0, i) to (seg.substring(i + 1) == "1")
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    fun get(key: String): Boolean {
        overrides[key]?.let { return it }
        state[key]?.let { return it }
        return runCatching { prefs()?.getBoolean(key, false) ?: false }.getOrDefault(false)
    }

    fun set(key: String, value: Boolean) {
        overrides[key] = value
        prefs()?.edit()?.putBoolean(key, value)?.apply()
    }
}
