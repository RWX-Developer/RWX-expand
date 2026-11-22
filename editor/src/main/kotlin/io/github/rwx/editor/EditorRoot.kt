package io.github.rwx.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File

/**
 * 编辑器的工作面。
 *
 * 部件预览不再单列：它已经并进「模组编辑」的画布页，
 * 和文本共用同一份 ini 文档模型。
 */
enum class EditorMode(val title: String, val hint: String) {
    MOD_EDITOR("模组编辑", "ini / 代码 / 模块 / 画布 / 打包"),
    CLIENT_UI("客户端 UI", "界面调整类小功能"),
}

private val PanelBg = Color(0xFF1B1F26)
private val SideBg = Color(0xFF20242B)
private val Accent = Color(0xFF2F80ED)
private val DimText = Color(0xFF9AA4B2)
private val MainText = Color(0xFFE6EAF0)

@Composable
fun EditorRoot(workspaceRoot: File, initialEntry: String? = null) {
    var mode by remember { mutableStateOf(EditorMode.MOD_EDITOR) }
    Row(Modifier.fillMaxSize().background(PanelBg)) {
        Column(Modifier.width(220.dp).fillMaxHeight().background(SideBg).padding(10.dp)) {
            BasicText("RWX 编辑器", style = TextStyle(color = MainText, fontSize = 16.sp))
            BasicText("独立程序 · v1", style = TextStyle(color = DimText, fontSize = 11.sp))
            Spacer(Modifier.height(14.dp))
            EditorMode.entries.forEach { m ->
                val selected = m == mode
                Column(
                    Modifier.fillMaxWidth()
                        .clickable { mode = m }
                        .background(if (selected) Accent else Color.Transparent)
                        .padding(8.dp),
                ) {
                    BasicText(m.title, style = TextStyle(color = MainText, fontSize = 14.sp))
                    BasicText(m.hint, style = TextStyle(color = DimText, fontSize = 11.sp))
                }
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(10.dp))
            BasicText(
                "工作区",
                style = TextStyle(color = DimText, fontSize = 10.sp),
            )
            BasicText(
                workspaceRoot.absolutePath,
                style = TextStyle(color = Color(0xFF6B7480), fontSize = 9.sp),
            )
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when (mode) {
                EditorMode.MOD_EDITOR -> ModEditorScreen(workspaceRoot, initialEntry)
                EditorMode.CLIENT_UI -> ClientUiScreen(workspaceRoot)
            }
        }
    }
}
