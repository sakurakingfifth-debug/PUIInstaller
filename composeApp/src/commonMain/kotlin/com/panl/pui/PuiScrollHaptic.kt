package com.panl.pui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll

/**
 * 滚到顶 / 到底时来一下轻震动。
 *
 * 做法是在**嵌套滚动**里看「还剩多少没被消费」：列表已经到边界、手指还在往下拉，
 * 这部分位移就没人消费，`available.y` 非零 —— 那就是「顶到了」的信号。
 *
 * 关键是**一次拖拽只震一下**：`available.y` 在边界上会连续很多帧都非零，
 * 不拦的话会变成持续震动。这里用 `hit` 标记，直到列表真的滚动起来（`consumed.y` 非零）才重新武装。
 */
@Composable
fun Modifier.puiEdgeHaptic(enabled: Boolean = true): Modifier {
    val tick = rememberTickHaptic()
    val connection = remember { EdgeHapticConnection() }
    connection.enabled = enabled
    connection.tick = tick
    return this.nestedScroll(connection)
}

private class EdgeHapticConnection : NestedScrollConnection {
    var enabled: Boolean = true
    var tick: () -> Unit = {}

    /** 本次拖拽是否已经震过；列表重新滚起来才复位。 */
    private var hit = false

    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource
    ): Offset {
        if (!enabled) return Offset.Zero
        if (available.y != 0f) {
            // 到边界了，还有位移没人要
            if (!hit) {
                hit = true
                tick()
            }
        } else if (consumed.y != 0f) {
            // 真的滚动了，下一次再顶到边界可以再震
            hit = false
        }
        return Offset.Zero
    }
}
