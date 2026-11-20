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
import com.corrodinggames.rts.gameFramework.SettingsEngine

/**
 * 客户端 UI 调整（骨架）。
 *
 * 现在只读展示几个真实设置，证明能拿到客户端状态；
 * 下一版做：开关/滑杆 → 即时预览 → 写回配置。
 */
@Composable
fun ClientUiScreen() {
    val settings = remember { SettingsEngine.getInstance() }
    val rows = remember {
        listOf(
            "全屏 (slick2dFullScreen)" to settings.slick2dFullScreen.toString(),
            "垂直同步 (renderVsync)" to settings.renderVsync.toString(),
            "渲染后端 (-Drwx.slickRenderer)" to System.getProperty("rwx.slickRenderer", "vao"),
        )
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF14181E)).padding(12.dp)) {
        BasicText("客户端 UI（骨架）", style = TextStyle(color = Color(0xFFE6EAF0), fontSize = 15.sp))
        BasicText(
            "先做只读展示；下一版做：开关/滑杆 → 即时预览 → 写回配置。",
            style = TextStyle(color = Color(0xFF9AA4B2), fontSize = 11.sp),
        )
        Spacer(Modifier.height(10.dp))
        rows.forEach { pair ->
            BasicText(pair.first + " = " + pair.second, style = TextStyle(color = Color(0xFFC7CDD6), fontSize = 12.sp))
            Spacer(Modifier.height(4.dp))
        }
    }
}
