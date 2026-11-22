package io.github.rwx.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 部件画布：已并入模组编辑器（不再是独立工作面）。
 *
 * 读 ini 里的 turret_* 节的 x/y 画出来，拖拽直接改回文档模型 —— 拖一下就改 ini 的值，
 * 保存时随文本一起写回。缩放是固定倍数，不做相机。
 */
@Composable
fun PartCanvas(
    doc: IniDocument?,
    revision: Int,
    onChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (doc == null) {
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            BasicText("（没有打开的 ini 文档，画布为空）", style = TextStyle(color = Color(0xFF9AA4B2), fontSize = 12.sp))
        }
        return
    }
    val turrets = doc.turretSections()
    var selected by remember(doc) { mutableStateOf(turrets.firstOrNull()?.name) }
    var zoom by remember { mutableStateOf(2f) }

    Row(modifier) {
        Column(Modifier.width(200.dp).padding(8.dp)) {
            BasicText("部件（" + turrets.size + "）", style = TextStyle(color = Color(0xFFE6EAF0), fontSize = 13.sp))
            BasicText("点选后拖拽改 x/y", style = TextStyle(color = Color(0xFF6B7480), fontSize = 10.sp))
            Spacer(Modifier.height(6.dp))
            if (turrets.isEmpty()) {
                BasicText("这个 ini 没有 turret_* 节", style = TextStyle(color = Color(0xFF9AA4B2), fontSize = 11.sp))
            }
            LazyColumn {
                items(turrets) { t ->
                    val on = t.name == selected
                    BasicText(
                        t.name + "  (" + (t.valueOf("x") ?: "0") + "," + (t.valueOf("y") ?: "0") + ")",
                        modifier = Modifier.fillMaxWidth().clickable { selected = t.name }.padding(4.dp),
                        style = TextStyle(
                            color = if (on) Color(0xFF2F80ED) else Color(0xFFC7CDD6),
                            fontSize = 11.sp,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            BasicText(
                "缩放 " + zoom.toInt() + "x   [ +/- ]",
                modifier = Modifier.clickable { zoom = if (zoom >= 6f) 1f else zoom + 1f }.padding(4.dp),
                style = TextStyle(color = Color(0xFF2F80ED), fontSize = 11.sp),
            )
        }
        Canvas(
            Modifier.weight(1f).fillMaxSize().background(Color(0xFF0F1216))
                .pointerInput(doc, selected, zoom) {
                    detectDragGestures { change, drag ->
                        change.consume()
                        val s = doc.sections.firstOrNull { it.name == selected } ?: return@detectDragGestures
                        val nx = ((s.numeric("x") ?: 0f) + drag.x / zoom)
                        val ny = ((s.numeric("y") ?: 0f) + drag.y / zoom)
                        s.setValue("x", nx.toInt().toString())
                        s.setValue("y", ny.toInt().toString())
                        onChanged()
                    }
                },
        ) {
            val step = 32f
            var gx = 0f
            while (gx <= size.width) {
                drawLine(Color(0xFF1E242C), Offset(gx, 0f), Offset(gx, size.height), 1f)
                gx += step
            }
            var gy = 0f
            while (gy <= size.height) {
                drawLine(Color(0xFF1E242C), Offset(0f, gy), Offset(size.width, gy), 1f)
                gy += step
            }
            val cx = size.width / 2f
            val cy = size.height / 2f
            drawLine(Color(0xFF2F80ED), Offset(cx, 0f), Offset(cx, size.height), 1f)
            drawLine(Color(0xFF2F80ED), Offset(0f, cy), Offset(size.width, cy), 1f)
            for (t in turrets) {
                val px = cx + (t.numeric("x") ?: 0f) * zoom
                val py = cy + (t.numeric("y") ?: 0f) * zoom
                val on = t.name == selected
                drawCircle(
                    color = if (on) Color(0xFFEB5757) else Color(0xFF27AE60),
                    radius = if (on) 8f else 5f,
                    center = Offset(px, py),
                )
            }
        }
    }
}
