package io.github.rwx.slick

import java.awt.AWTEvent
import java.awt.Canvas
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.event.AWTEventListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities

object SlickCanvasHost {
    @Volatile
    private var uiFrameProvider: (() -> SlickUiFrame?)? = null

    private var uiInputDispatcher: ((InputEvent) -> Boolean)? = null

    /** 快照面板当前是否真的挂在窗口上；渲染线程据此决定要不要持续读回合成帧。 */
    @Volatile
    var snapshotPanelActive: Boolean = false

    private val screenshotHotkeyListener = AWTEventListener { event ->
        val keyEvent = event as? KeyEvent ?: return@AWTEventListener
        if (keyEvent.id != KeyEvent.KEY_PRESSED) return@AWTEventListener
        if (keyEvent.keyCode == KeyEvent.VK_F12) onScreenshotHotkey?.invoke()
    }

    internal var onScreenshotHotkey: (() -> Unit)? = null

    /** toolkit 级监听：F12 截图不依赖焦点落在哪一层，也不受引擎视野输入屏蔽影响。 */
    private var screenshotHotkeyInstalled = false

    internal fun installScreenshotHotkey() {
        if (screenshotHotkeyInstalled) return
        screenshotHotkeyInstalled = true
        runCatching {
            Toolkit.getDefaultToolkit().addAWTEventListener(
                screenshotHotkeyListener,
                AWTEvent.KEY_EVENT_MASK,
            )
        }.onFailure { error -> println("RWX screenshot hotkey unavailable: $error") }
    }


    internal fun installUiOverlay(frameProvider: () -> SlickUiFrame?, inputDispatcher: (InputEvent) -> Boolean) {
        uiFrameProvider = frameProvider
        uiInputDispatcher = inputDispatcher
    }

    internal fun currentUiFrame(): SlickUiFrame? = uiFrameProvider?.invoke()

    internal fun dispatchOverlayInput(event: InputEvent): Boolean = uiInputDispatcher?.invoke(event) == true

    @Volatile
    private var canvasProvider: (() -> Canvas?)? = null

    @Volatile
    private var visibilityController: ((Boolean, Boolean) -> Unit)? = null

    @Volatile
    private var resizeController: ((Int, Int) -> Unit)? = null

    @Volatile
    private var rendererShutdown: (() -> Unit)? = null

    fun install(
        canvasProvider: () -> Canvas?,
        visibilityController: (Boolean, Boolean) -> Unit,
    ) {
        this.canvasProvider = canvasProvider
        this.visibilityController = visibilityController
    }

    /** Detach only this host; a late close must not unregister a replacement window. */
    fun uninstall(canvas: Canvas) {
        check(SwingUtilities.isEventDispatchThread())
        if (canvasProvider?.invoke() !== canvas) return
        uiFrameProvider = null
        uiInputDispatcher = null
        canvasProvider = null
        visibilityController = null
        resizeController = null
        rendererShutdown = null
    }

    fun setRendererShutdown(shutdown: (() -> Unit)?) {
        rendererShutdown = shutdown
    }

    /**
     * 有的机器（实测 Intel Iris Xe，驱动 igxelpicd64.dll）在窗口首次呈现时会丢掉重绘，
     * 画面停在加载界面最后一帧；引擎逻辑其实已经到主菜单，只有最小化再还原才会重绘一次。
     * 这里用一次 1 像素的尺寸抖动逼 surface 重建，外加两次 repaint 顶替那下手动操作。
     */
    private val presentationNudgeEnabled: Boolean by lazy {
        System.getProperty("rwx.presentationNudge")?.toBoolean() ?: true
    }

    @Volatile
    private var presentationNudged = false

    /**
     * 菜单背景就绪只说明 GL 那层活了；切到主菜单还要再过两帧，那一次重绘同样会丢，
     * 结果就是「3D 背景在动、界面还停在加载页」。所以这里连续刷 12 次（约 1.8 秒），
     * 1 像素抖动只在第一次做，后面只做整窗重绘。
     */
    @Volatile
    private var uiRenderRequester: (() -> Unit)? = null

    internal fun installUiRenderRequester(requester: () -> Unit) {
        uiRenderRequester = requester
    }

    /** 平台无关入口：把当前画面整体重新呈现一次（切屏后连续调几帧）。 */
    fun requestPresentationRefresh() {
        uiRenderRequester?.invoke()
        val canvas = canvasProvider?.invoke() ?: return
        if (SwingUtilities.isEventDispatchThread()) repaintOnly(canvas)
        else SwingUtilities.invokeLater { repaintOnly(canvas) }
    }

    private fun repaintOnly(canvas: Canvas) {
        val window = SwingUtilities.getWindowAncestor(canvas)
        window?.invalidate()
        window?.validate()
        window?.repaint()
        canvas.invalidate()
        canvas.parent?.invalidate()
        canvas.parent?.validate()
        canvas.repaint()
    }

    fun nudgePresentation() {
        if (!presentationNudgeEnabled || presentationNudged) return
        presentationNudged = true
        val canvas = canvasProvider?.invoke() ?: return
        SwingUtilities.invokeLater { repaintCycle(canvas, 12, true) }
    }

    private fun repaintCycle(canvas: Canvas, remaining: Int, jiggle: Boolean) {
        if (remaining <= 0) return
        if (jiggle) {
            val width = canvas.width
            val height = canvas.height
            if (width > 2 && height > 2) {
                try {
                    canvas.size = Dimension(width, height - 1)
                    canvas.size = Dimension(width, height)
                } catch (error: Throwable) {
                    println("RWX presentation nudge failed: $error")
                }
            }
        }
        val window = SwingUtilities.getWindowAncestor(canvas)
        window?.invalidate()
        window?.validate()
        window?.repaint()
        canvas.invalidate()
        canvas.parent?.invalidate()
        canvas.parent?.validate()
        canvas.repaint()
        if (remaining > 1) {
            javax.swing.Timer(150) { repaintCycle(canvas, remaining - 1, false) }.apply {
                isRepeats = false
                start()
            }
        }
    }

    /**
     * Stops the embedded renderer and blocks until its GL thread has released the AWT drawing
     * surface. Call before disposing the game canvas' window to avoid a native JAWT crash.
     */
    fun shutdownRenderer() {
        rendererShutdown?.invoke()
    }

    fun gameCanvas(): Canvas? = canvasProvider?.invoke()

    fun gameCanvasSize(): Dimension? = canvasProvider?.invoke()?.size

    fun setResizeController(controller: ((Int, Int) -> Unit)?) {
        resizeController = controller
    }

    fun notifyGameCanvasResized(width: Int, height: Int) {
        resizeController?.invoke(width, height)
    }

    fun setGameVisible(visible: Boolean, overlay: Boolean = false) {
        visibilityController?.invoke(visible, overlay)
    }

    fun requestGameFocus() {
        val canvas = gameCanvas() ?: return
        val action = {
            if (canvas.isVisible && canvas.isShowing) {
                canvas.isFocusable = true
                if (!canvas.requestFocusInWindow()) {
                    canvas.requestFocus()
                }
            }
        }
        if (SwingUtilities.isEventDispatchThread()) {
            action()
        } else {
            SwingUtilities.invokeLater(action)
        }
    }
}
