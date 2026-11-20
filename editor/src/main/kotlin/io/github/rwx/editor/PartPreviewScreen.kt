package io.github.rwx.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 部件预览（骨架）。
 *
 * 合流方案（方向已定，未实现）：
 *   预览器 = 读 ini 里 base/turret/arm/leg 的坐标 → 画出来 → 拖拽改坐标 → 写回 ini。
 *   编辑器这边先把「画布 + 网格 + 相机」占好位置，再把预览器的解析与写回搬过来；
 *   文本与画布共享同一份文档模型，拖拽就是一次文本区间替换（可撤销）。
 */
@Composable
fun PartPreviewScreen() {
    Column(Modifier.fillMaxSize().background(Color(0xFF14181E)).padding(12.dp)) {
        BasicText("部件预览（骨架）", style = TextStyle(color = Color(0xFFE6EAF0), fontSize = 15.sp))
        BasicText(
            "画布已就位；部件解析、拖拽、写回三件事还没接。",
            style = TextStyle(color = Color(0xFF9AA4B2), fontSize = 11.sp),
        )
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF0F1216))) {
            val step = 32f
            var x = 0f
            while (x <= size.width) {
                drawLine(Color(0xFF1E242C), Offset(x, 0f), Offset(x, size.height), 1f)
                x += step
            }
            var y = 0f
            while (y <= size.height) {
                drawLine(Color(0xFF1E242C), Offset(0f, y), Offset(size.width, y), 1f)
                y += step
            }
            drawLine(Color(0xFF2F80ED), Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), 1f)
            drawLine(Color(0xFF2F80ED), Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), 1f)
        }
    }
}
