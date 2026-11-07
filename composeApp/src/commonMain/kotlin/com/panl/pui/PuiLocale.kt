package com.panl.pui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * PUI 组件里的默认文案。
 *
 * 设计系统本身不绑定语言，宿主在启动 / 切换语言时把这几条灌进来；
 * 字段是 Compose 状态，所以改完用到它们的组件会自动重组。
 */
object PuiLocale {
    var confirm: String by mutableStateOf("确定")
    var cancel: String by mutableStateOf("取消")
    var search: String by mutableStateOf("搜索")
    var searchApps: String by mutableStateOf("搜索应用")
}
