package com.panl.installer.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.panl.installer.R
import com.panl.installer.core.ModuleInfo
import com.panl.installer.core.ModuleInstaller
import com.panl.installer.i18n.tr
import com.panl.installer.ui.theme.InstallerTheme
import com.panl.pui.PuiIcon
import com.panl.pui.PuiIconView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 模块刷写页 —— 跟 root 管理器的刷写界面一个路子：
 * 全屏黑底、等宽字体、**实时**滚动打印安装脚本的输出，下面一行状态在「刷写中 / 刷写完成 / 失败」之间切换。
 *
 * 为什么单独做一个 Activity：sheet 太矮，日志三两行就到底了，看不到完整过程；
 * 而且刷写过程中用户大概率就想盯着输出看，不该被弹窗挡住。
 */
class ModuleFlashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_PATH).orEmpty()
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val modeName = intent.getStringExtra(EXTRA_MODE).orEmpty()
        val name = intent.getStringExtra(EXTRA_NAME).orEmpty()
        val mode = runCatching {
            com.panl.installer.core.ModuleInstaller.RootMode.valueOf(modeName)
        }.getOrDefault(com.panl.installer.core.ModuleInstaller.RootMode.None)

        setContent {
            InstallerTheme {
                FlashScreen(
                    moduleName = name,
                    file = File(path),
                    moduleId = id,
                    mode = mode,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_ID = "id"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_NAME = "name"

        fun start(context: Context, module: ModuleInfo, mode: ModuleInstaller.RootMode) {
            context.startActivity(
                Intent(context, ModuleFlashActivity::class.java).apply {
                    putExtra(EXTRA_PATH, module.file.absolutePath)
                    putExtra(EXTRA_ID, module.id)
                    putExtra(EXTRA_MODE, mode.name)
                    putExtra(EXTRA_NAME, module.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}

@Composable
private fun FlashScreen(
    moduleName: String,
    file: File,
    moduleId: String,
    mode: ModuleInstaller.RootMode,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }
    var ok by remember { mutableStateOf<Boolean?>(null) }
    val listState = rememberLazyListState()

    suspend fun runFlash() {
        if (running) return
        running = true
        ok = null
        lines.clear()
        lines += "- 开始刷写：${moduleName.ifBlank { moduleId }}"
        val result = withContext(Dispatchers.IO) {
            ModuleInstaller.install(file, mode, moduleId, onLine = { line ->
                // 回调在工作线程，这里直接往 snapshot list 里塞（Compose 状态是线程安全的）
                lines += line
            })
        }
        if (result.ok) {
            lines += "- Done"
            lines += "- Module installed successfully!"
        } else {
            lines += "- 失败：${result.output.take(400)}"
        }
        ok = result.ok
        running = false
    }

    LaunchedEffect(Unit) { runFlash() }

    // 有新行就贴底
    LaunchedEffect(lines.size, running) {
        if (lines.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(lines.lastIndex) }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E0E11))
            .statusBarsPadding()
    ) {
        // 顶栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClose),
                contentAlignment = Alignment.Center
            ) {
                PuiIconView(
                    PuiIcon.ChevronRight,
                    Modifier
                        .size(20.dp)
                        .rotate(180f),
                    tint = Color.White
                )
            }
            Spacer(Modifier.width(2.dp))
            Text(
                when {
                    running -> tr("刷写中…", "Flashing…")
                    ok == true -> tr("刷写完成", "Done")
                    ok == false -> tr("刷写失败", "Failed")
                    else -> tr("刷写", "Flash")
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        // 控制台
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                itemsIndexed(lines) { _, line ->
                    Text(
                        line.ifEmpty { " " },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp
                        ),
                        color = when {
                            line.startsWith("- Done") || line.contains("successfully") -> Color(0xFF7BD88F)
                            line.contains("失败") || line.contains("Failed") || line.contains("error", true) -> Color(0xFFFF7A7A)
                            else -> Color(0xFFD8D8DE)
                        }
                    )
                }
            }
        }

        // 底部：状态 + 重新刷写
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when {
                    running -> tr("正在执行安装脚本…", "Running installer…")
                    ok == true -> tr("刷写成功，重启后生效", "Flashed — takes effect after reboot")
                    ok == false -> tr("刷写失败，请看上面的输出", "Failed — see the log above")
                    else -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    running -> Color(0xFFD8D8DE)
                    ok == true -> Color(0xFF7BD88F)
                    else -> Color(0xFFFF7A7A)
                }
            )
            Spacer(Modifier.weight(1f))
            if (!running) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2C6BED))
                        .clickable { scope.launch { runFlash() } },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_document_download),
                        contentDescription = tr("重新刷写", "Flash again"),
                        modifier = Modifier.size(22.dp),
                        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White)
                    )
                }
            }
        }
    }
}
