package io.github.rwx.editor

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import java.io.File

/**
 * RWX 编辑器：独立程序入口。
 *
 * 不启动游戏、不依赖引擎：只读工作区文件 + 自己的 Compose UI。
 * 需要用到游戏内能力时（预览、校验），以子进程或本地服务的方式接，而不是把编辑器塞进游戏进程。
 */
fun main(args: Array<String>) {
    val workspace = args.firstOrNull { !it.startsWith("-") }
        ?: System.getenv("RWX_WORKSPACE")
        ?: detectDefaultWorkspace()
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "RWX 编辑器（骨架）— " + workspace,
            state = WindowState(size = DpSize(1440.dp, 900.dp)),
        ) {
            EditorRoot(workspaceRoot = File(workspace))
        }
    }
}

private fun detectDefaultWorkspace(): String {
    val candidates = listOf(
        "D:/Game/RWX-整合包/mods/units",
        "D:/Game/铁锈战争/Rusted Warfare1.15/mods/units",
    )
    return candidates.firstOrNull { File(it).isDirectory } ?: "."
}
