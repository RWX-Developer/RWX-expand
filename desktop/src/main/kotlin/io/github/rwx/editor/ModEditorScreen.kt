package io.github.rwx.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
 * 模组编辑（骨架）：左边列模组与文件，中间看文本，右边留给校验/查键。
 *
 * 已实现：扫描工作区、读文件、截断显示。
 * 未实现（标出来，不假装）：无损编辑、保存、lint 调用（MCP）、与预览器联动。
 */
@Composable
fun ModEditorScreen() {
    val root = remember { File("D:/Game/RWX-整合包/mods/units") }
    val mods = remember(root) {
        root.listFiles()?.filter { it.isDirectory || it.name.endsWith(".rwmod") }?.sortedBy { it.name } ?: emptyList()
    }
    var selectedMod by remember { mutableStateOf<File?>(mods.firstOrNull()) }
    val files = remember(selectedMod) {
        selectedMod?.listFiles()?.sortedBy { it.name } ?: emptyList()
    }
    var selectedFile by remember(files) {
        mutableStateOf<File?>(files.firstOrNull { it.name.endsWith(".ini") || it.name.endsWith(".txt") })
    }
    val text = remember(selectedFile) { readTextCapped(selectedFile) }
    val modCount = mods.size

    Row(Modifier.fillMaxSize().background(Color(0xFF14181E))) {
        LazyColumn(Modifier.width(240.dp).fillMaxHeight().padding(8.dp)) {
            item {
                BasicText("模组（" + modCount + "）", style = TextStyle(color = Color(0xFFE6EAF0), fontSize = 13.sp))
                BasicText(root.absolutePath, style = TextStyle(color = Color(0xFF6B7480), fontSize = 10.sp))
                Spacer(Modifier.height(8.dp))
            }
            items(mods) { m ->
                BasicText(
                    m.name,
                    modifier = Modifier.fillMaxWidth().clickable { selectedMod = m }.padding(4.dp),
                    style = TextStyle(
                        color = if (m == selectedMod) Color(0xFF2F80ED) else Color(0xFFC7CDD6),
                        fontSize = 12.sp,
                    ),
                )
            }
        }
        LazyColumn(Modifier.width(220.dp).fillMaxHeight().padding(8.dp)) {
            item {
                BasicText("文件", style = TextStyle(color = Color(0xFFE6EAF0), fontSize = 13.sp))
                Spacer(Modifier.height(6.dp))
            }
            items(files) { f ->
                BasicText(
                    f.name,
                    modifier = Modifier.fillMaxWidth().clickable { selectedFile = f }.padding(4.dp),
                    style = TextStyle(
                        color = if (f == selectedFile) Color(0xFF2F80ED) else Color(0xFFC7CDD6),
                        fontSize = 11.sp,
                    ),
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(8.dp)) {
            BasicText(
                selectedFile?.absolutePath ?: "未选文件",
                style = TextStyle(color = Color(0xFF9AA4B2), fontSize = 11.sp),
            )
            Spacer(Modifier.height(6.dp))
            Column(
                Modifier.weight(1f).fillMaxWidth().background(Color(0xFF0F1216)).padding(8.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                BasicText(text, style = TextStyle(color = Color(0xFFD7DCE3), fontSize = 11.sp))
            }
        }
        Column(Modifier.width(220.dp).fillMaxHeight().padding(8.dp)) {
            BasicText("校验 / 查键", style = TextStyle(color = Color(0xFFE6EAF0), fontSize = 13.sp))
            Spacer(Modifier.height(6.dp))
            BasicText(
                "接入 rw-modding-mcp 后，这里显示：\n" +
                    "· lint 的问题（文件/行/说明）\n" +
                    "· 当前键的规范拼写与例子\n" +
                    "· autoTrigger 的动作链\n\n" +
                    "（骨架阶段未接入，不假装有）",
                style = TextStyle(color = Color(0xFF9AA4B2), fontSize = 11.sp),
            )
        }
    }
}

/** 读文本，最多 4000 行，避免大文件把 UI 卡死。 */
private fun readTextCapped(file: File?): String {
    if (file == null || !file.isFile) return "（未选择文件）"
    return runCatching {
        val lines = file.readLines(Charsets.UTF_8)
        val head = lines.take(4000).joinToString("\n")
        if (lines.size > 4000) head + "\n\n…（只显示前 4000 行，共 " + lines.size + " 行）" else head
    }.getOrElse { "读取失败: " + it.message }
}
