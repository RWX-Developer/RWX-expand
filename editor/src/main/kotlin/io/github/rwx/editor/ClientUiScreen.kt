package io.github.rwx.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * 客户端 UI 调整（骨架）。
 *
 * 独立程序不该依赖引擎类，所以这里直接读整合包里的 preferences.toml 文本；
 * 下一版做：开关/滑杆 → 即时预览（起游戏子进程）→ 写回（走文档模型，保留注释）。
 */
@Composable
fun ClientUiScreen(workspaceRoot: File) {
    val prefs = remember(workspaceRoot) {
        val packRoot = workspaceRoot.parentFile?.parentFile
        val f = File(packRoot ?: workspaceRoot, "preferences.toml")
        if (f.isFile) f else null
    }
    val lines = remember(prefs) {
        prefs?.let { runCatching { it.readLines(Charsets.UTF_8).take(200) }.getOrNull() } ?: emptyList()
    }
    val relevant = lines.filter { line ->
        val k = line.substringBefore("=").trim().lowercase()
        k.isNotEmpty() && (k.contains("ui") || k.contains("render") || k.contains("fullscreen") || k.contains("vsync") || k == "slick2dfullscreen")
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF14181E)).padding(12.dp)) {
        BasicText("客户端 UI（骨架）", style = TextStyle(color = Color(0xFFE6EAF0), fontSize = 15.sp))
        BasicText(
            "只读展示 preferences 里的 UI/渲染相关项；改值、预览、写回都还没做。",
            style = TextStyle(color = Color(0xFF9AA4B2), fontSize = 11.sp),
        )
        Spacer(Modifier.height(10.dp))
        BasicText(
            prefs?.absolutePath ?: "（找不到 preferences.toml，设 RWX_WORKSPACE 指向整合包）",
            style = TextStyle(color = Color(0xFF6B7480), fontSize = 10.sp),
        )
        Spacer(Modifier.height(8.dp))
        if (relevant.isEmpty()) {
            BasicText("没有匹配到 UI/渲染相关键", style = TextStyle(color = Color(0xFF9AA4B2), fontSize = 12.sp))
        } else {
            relevant.forEach { line ->
                BasicText(line.trim(), style = TextStyle(color = Color(0xFFC7CDD6), fontSize = 12.sp))
                Spacer(Modifier.height(3.dp))
            }
        }
    }
}
