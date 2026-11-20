package io.github.rwx

import androidx.compose.ui.awt.ComposePanel
import io.github.rwx.slick.SlickUiFrame
import java.awt.AlphaComposite
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Graphics
import java.awt.KeyboardFocusManager
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import javax.swing.JPanel
import javax.swing.SwingUtilities

/** Keeps Compose's Swing input/IME host, publishing only UI repaints for Slick to composite. */
internal class ComposeTexturePanel(private val panel: ComposePanel) : JPanel(BorderLayout()) {
    private var pointerOwner: Component? = null
    private var gameCursor: Cursor? = null
    @Volatile var uiFrame: SlickUiFrame? = null
        private set
    var nativePresentation = false
        set(value) {
            if (field == value) return
            field = value
            if (!value) {
                uiFrame = null
                pointerOwner?.cursor = gameCursor
                pointerOwner = null
                gameCursor = null
            } else SwingUtilities.invokeLater { if (nativePresentation) renderUi() }
            repaint()
        }

    init {
        isOpaque = false
        add(panel, BorderLayout.CENTER)
    }

    // Descendant invalidations reach us even though a heavyweight canvas covers this panel.
    override fun isPaintingOrigin() = true

    override fun paintImmediately(x: Int, y: Int, w: Int, h: Int) {
        if (nativePresentation) renderUi() else super.paintImmediately(x, y, w, h)
    }

    override fun paint(graphics: Graphics) {
        if (nativePresentation) renderUi() else super.paint(graphics)
    }

    // RWX：JFR 里 DataBufferInt 占 19% 的分配——每张 UI 快照都是一块新的 1920x1080 int 位图（约 8.3 MB）。
    // 改成三缓冲轮换：EDT 永远重画「GL 最久以前读过的那块」，
    // GL 线程发布后至少隔两轮才会再碰到同一块，读到的始终是完整画面。
    private var backImages: Array<BufferedImage?> = arrayOfNulls(3)
    private var backIndex = 0

    // RWX：崩溃转储里的两条栈都指向同一件事——panel.paint() 渲染快照的过程中，
    // Compose 又同步绕回来调 renderNow()。第二次进入会把它的协程调度器踩坏
    // （CompletedContinuation 强转失败 / ConcurrentModificationException），
    // 随后的致命异常处理各带一次 System.gc()，看起来就像「战斗中突然 full GC」。
    // 快照渲染中直接忽略重入请求：本轮 paint 出来的画面本来就是最新状态。
    private var renderingUi = false

    private fun renderUi() {
        if (renderingUi) return
        if (!isShowing || !panel.isVisible || width <= 0 || height <= 0) return
        renderingUi = true
        try {
            renderUiInner()
        } finally {
            renderingUi = false
        }
    }

    private fun renderUiInner() {
        val scale = graphicsConfiguration.defaultTransform
        val imageWidth = (width * scale.scaleX).toInt().coerceAtLeast(1)
        val imageHeight = (height * scale.scaleY).toInt().coerceAtLeast(1)
        backIndex = (backIndex + 1) % 3
        var image = backImages[backIndex]
        if (image == null || image.width != imageWidth || image.height != imageHeight) {
            image = BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB_PRE)
            backImages[backIndex] = image
        }
        val graphics = image.createGraphics()
        graphics.composite = AlphaComposite.Clear
        graphics.fillRect(0, 0, imageWidth, imageHeight)
        graphics.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER)
        try {
            graphics.scale(scale.scaleX, scale.scaleY)
            panel.paint(graphics)
        } finally { graphics.dispose() }
        uiFrame = SlickUiFrame(image.width, image.height, (image.raster.dataBuffer as DataBufferInt).data)
        if (PROBE_ENABLED) {
            val now = System.nanoTime()
            if (now - lastUiProbeAt > 1_000_000_000L) {
                lastUiProbeAt = now
                println("RWX_UI renderUi ${image.width}x${image.height} native=$nativePresentation showing=$isShowing")
                System.out.flush()
            }
        }
    }

    private var lastUiProbeAt = 0L

    /**
     * 按当前的 UI 状态重画一份快照。这个面板被重量级 GL canvas 盖住，
     * Swing 的 paint 事件经常不会送过来，所以状态变了要主动调这里，
     * 否则 Slick 会一直合成那张停在加载页的旧图。
     */
    fun renderNow() {
        if (!nativePresentation) return
        if (SwingUtilities.isEventDispatchThread()) renderUi()
        else SwingUtilities.invokeLater { renderUi() }
    }

    private companion object {
        val PROBE_ENABLED: Boolean = System.getenv("RWX_PROBE_FRAME") != null
    }

    private var dispatchingInput = false

    fun dispatchInput(event: InputEvent): Boolean {
        if (!nativePresentation || !panel.isShowing) return false
        // RWX：同一手势在 Compose 内部被重新派发时（见 renderUi 的重入注释），
        // 消费掉外层这一次，不让两个派发栈同时改同一个手势队列。
        if (dispatchingInput) {
            event.consume()
            return true
        }
        dispatchingInput = true
        try {
            return dispatchInputInner(event)
        } finally {
            dispatchingInput = false
        }
    }

    private fun dispatchInputInner(event: InputEvent): Boolean {
        when (event) {
            is MouseEvent -> {
                val point = SwingUtilities.convertPoint(event.component, event.point, panel)
                val pointerTarget = SwingUtilities.getDeepestComponentAt(panel, point.x, point.y) ?: panel
                val target = if (event is MouseWheelEvent) panel else pointerTarget
                target.dispatchEvent(SwingUtilities.convertMouseEvent(event.component, event, target))
                if (pointerOwner == null) {
                    pointerOwner = event.component
                    gameCursor = event.component.cursor
                }
                event.component.cursor = pointerTarget.cursor
            }
            is KeyEvent -> {
                val focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner
                val target = focus?.takeIf { SwingUtilities.isDescendingFrom(it, panel) }
                    ?: SwingUtilities.getDeepestComponentAt(panel, panel.width / 2, panel.height / 2) ?: panel
                KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(target,
                    KeyEvent(target, event.id, event.`when`, event.modifiersEx, event.keyCode, event.keyChar, event.keyLocation))
            }
        }
        event.consume()
        return true
    }
}
