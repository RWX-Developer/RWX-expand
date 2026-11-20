package io.github.rwx.editor

import androidx.compose.ui.awt.ComposePanel
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * RWX 编辑器外壳（骨架）。
 *
 * 独立窗口，不参与游戏主循环：编辑器自己的 Compose 面板 + 自己的状态，
 * 关掉窗口不影响游戏，游戏崩了也不影响编辑器。
 *
 * 打开方式：客户端加 -Drwx.editor=1，或以后从菜单里开。
 */
object EditorWindow {
    private var frame: JFrame? = null

    fun open() {
        SwingUtilities.invokeLater {
            val existing = frame
            if (existing != null) {
                existing.toFront()
                existing.requestFocus()
                return@invokeLater
            }
            val panel = ComposePanel()
            panel.setContent { EditorRoot() }
            val f = JFrame("RWX 编辑器（骨架）").apply {
                defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
                contentPane.add(panel)
                size = Dimension(1440, 900)
                setLocationRelativeTo(null)
                addWindowListener(object : WindowAdapter() {
                    override fun windowClosed(e: WindowEvent?) {
                        frame = null
                    }
                })
            }
            frame = f
            f.isVisible = true
        }
    }
}
