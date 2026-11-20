package io.github.rwx

import com.corrodinggames.rts.gameFramework.GameEngine
import com.corrodinggames.rts.gameFramework.InputController
import com.corrodinggames.rts.gameFramework.SettingsEngine
import io.github.rwx.app.AppOptions
import io.github.rwx.app.GameFramePresenter
import io.github.rwx.app.installApp
import io.github.rwx.di.coreModule
import io.github.rwx.di.desktopModule
import io.github.rwx.i18n.LocaleSettings
import io.github.rwx.headless.HeadlessDesktopRunner
import io.github.rwx.render.canvas.GameFontMetrics
import io.github.rwx.settings.GameSettingsRepository
import io.github.rwx.skia.SkiaGameSession
import io.github.rwx.skia.SkiaGameView
import io.github.rwx.slick.SlickFramePresenter
import io.github.rwx.slick.SlickGameSession
import io.github.rwx.steam.SteamBridge
import io.github.rwx.ui.AppUiState
import io.github.rwx.ui.ColorSchemeRegistry
import io.github.rwx.ui.host.LoadingSceneHost
import io.github.rwx.ui.model.SettingsModel
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.GlobalContext
import org.koin.dsl.module
import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout
import javax.swing.SwingUtilities

object DesktopMain : KoinComponent {
    /**
     * RWX：在触碰任何 AWT 窗口前把进程声明为 Per-Monitor v2 DPI 感知。
     * 不这样做时 Windows 会把整个窗口按缩放比（如 125%）位图拉伸，游戏以
     * 逻辑分辨率（1536x864）渲染后再放大 —— 画面整体发糊、边缘出现拉伸伪影。
     */
    private fun makeProcessDpiAware() {
        if (!System.getProperty("os.name", "").contains("Windows", ignoreCase = true)) return
        if (System.getProperty("rwx.dpiAware", "true") == "false") return
        val linker = Linker.nativeLinker()
        // 1) Win10 1703+：DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2 = (HANDLE)-4
        try {
            Arena.ofConfined().use { arena ->
                val sym = SymbolLookup.libraryLookup("user32", arena).find("SetProcessDpiAwarenessContext")
                if (sym.isPresent) {
                    val ok = linker.downcallHandle(
                        sym.get(),
                        FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.ADDRESS),
                    ).invokeExact(MemorySegment.ofAddress(-4L))
                    if (ok == true) return
                }
            }
        } catch (_: Throwable) {
        }
        // 2) Win8.1+：PROCESS_PER_MONITOR_DPI_AWARE = 2
        try {
            Arena.ofConfined().use { arena ->
                val sym = SymbolLookup.libraryLookup("shcore", arena).find("SetProcessDpiAwareness")
                if (sym.isPresent) {
                    linker.downcallHandle(
                        sym.get(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
                    ).invokeExact(2)
                    return
                }
            }
        } catch (_: Throwable) {
        }
        // 3) 兜底：系统级感知（Win7+）
        try {
            Arena.ofConfined().use { arena ->
                val sym = SymbolLookup.libraryLookup("user32", arena).find("SetProcessDPIAware")
                if (sym.isPresent) {
                    linker.downcallHandle(sym.get(), FunctionDescriptor.of(ValueLayout.JAVA_INT)).invokeExact()
                }
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * RWX：Windows 显示缩放不是整数倍（125%、150%）时，强制 Java 按物理像素工作。
     * 默认行为下 Swing/Compose 会把窗口尺寸换算成逻辑像素（1920x1080 @125% -> 1536x864），
     * 游戏世界按逻辑分辨率画好后再由 GPU 非整数倍拉伸到物理视口 —— 像素画被抹开，整体发糊。
     * 显式给了 -Dsun.java2d.uiScale 或者设了 rwx.keepSystemScale=true 时完全不干预。
     */
    private fun preferNativePixelScale(): String? {
        if (!System.getProperty("os.name", "").contains("Windows", ignoreCase = true)) return null
        if (System.getProperty("sun.java2d.uiScale") != null) return null
        if (System.getProperty("rwx.keepSystemScale", "false") == "true") return null
        val dpi = try {
            Arena.ofConfined().use { arena ->
                val user32 = SymbolLookup.libraryLookup("user32", arena)
                val sym = user32.find("GetDpiForSystem")
                if (sym.isPresent) {
                    Linker.nativeLinker().downcallHandle(
                        sym.get(),
                        FunctionDescriptor.of(ValueLayout.JAVA_INT),
                    ).invokeExact() as Int
                } else {
                    0
                }
            }
        } catch (_: Throwable) {
            0
        }
        if (dpi <= 0) {
            // 兜底：老系统没有 GetDpiForSystem 时，读 Java 自己的屏幕变换比例
            val javaScale = try {
                java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
            } catch (_: Throwable) {
                1.0
            }
            if (javaScale % 1.0 == 0.0) return null
            System.setProperty("sun.java2d.uiScale", "1")
            return "Non-integer display scaling detected (${javaScale}x), forcing uiScale=1 to render at native pixel resolution"
        }
        val scale = dpi / 96.0
        if (scale % 1.0 == 0.0) return null
        System.setProperty("sun.java2d.uiScale", "1")
        return "Non-integer display scaling detected (${dpi}dpi = ${scale * 100}%), forcing uiScale=1 to render at native pixel resolution"
    }

    /**
     * RWX：Slick 的 SGL 被各绘制类在 static 初始化时捕获（`static SGL GL = Renderer.get()`），
     * 所以渲染器必须在任何 slick 类被加载之前定下来，否则改不动。
     * 用 VAOGLRenderer 时四边形会攒进顶点数组批量提交，而不是一个四边形一次 glBegin/glEnd。
     * RWX：默认已切 vao（批量提交）；有兼容问题可用 -Drwx.slickRenderer=immediate 退回。
     */
    private fun installSlickRenderer() {
        val id = System.getProperty("rwx.slickRenderer", "vao").trim().lowercase()
        if (id != "vao" && id != "vertexarray") return
        org.newdawn.slick.opengl.renderer.Renderer.setRenderer(
            org.newdawn.slick.opengl.renderer.Renderer.VERTEX_ARRAY_RENDERER
        )
    }

    @JvmStatic
    fun main(args: Array<String>) {
        makeProcessDpiAware()
        installSlickRenderer()
        configureDesktopLogging()
        val pixelScaleNotice = preferNativePixelScale()
        val options = AppOptions.parseArgs(args, isDesktop = true)
        if (options.headless) {
            val exitCode = try {
                HeadlessDesktopRunner.run(options)
                0
            } catch (error: Throwable) {
                error.printStackTrace()
                1
            }
            kotlin.system.exitProcess(exitCode)
        }
        configureLwjglMemoryStack()
        System.setProperty("org.lwjgl.opengl.contextAPI", "native")
        GameEngine.isMenuBackgroundDisabled = true
        GameEngine.isNonAndroidVersion = true
        GameEngine.isDesktopInitialized = true
        GameEngine.isJavaDesktopVersion = true
        GameEngine.isPCOrIOSVersion = true
        InputController.b = DesktopInputHandler()
        ensureDesktopOpenAlMusicFactory()
        GlobalContext.startKoin { modules(coreModule, desktopModule, module { single { options } }) }
        pixelScaleNotice?.let { logger.info { it } }
        val storedBackendId =
            get<PreferenceStorage>().preference(PREFERENCE_NAME).getString("desktopRenderBackend", "slick")
        val renderBackend = DesktopRenderBackend.selectedId(options.backendId, storedBackendId)
        (get<CrashReporter>() as? FileCrashReporter)?.installAsDefaultUncaughtExceptionHandler()
        GameFontMetrics.install(DesktopFontMetrics(get<PlatformStorage>()))
        SettingsEngine.getInstance().save()
        val settings = SettingsModel().also {
            get<GameSettingsRepository>().loadInto(it)
            get<GameSettingsRepository>().saveFrom(it)
        }
        LocaleSettings.initialize()
        if (options.colorSchemeId != ColorSchemeRegistry.defaultSchemeId) {
            settings.selectedColorSchemeId.value = options.colorSchemeId
        }
        val bridge = get<PlatformBridge>()
        val steamBridge = SteamBridge()
        if (steamBridge.init(options.noSteam)) {
            GameEngine.isSteamModeEnabled = true
            logger.info { "Steam initialized; playtime and overlay enabled" }
        } else {
            logger.info { "Steam unavailable; running without Steam features" }
        }
        Runtime.getRuntime().addShutdownHook(Thread { steamBridge.shutdown() })
        SwingUtilities.invokeLater {
            val host = when (renderBackend) {
                DesktopRenderBackend.Skia -> SwingAppHost.create(
                    fullscreen = SettingsEngine.getInstance().slick2dFullScreen,
                    shutdownRenderer = { get<SkiaGameSession>().close() },
                    skiaGameContent = { inputEnabled ->
                        SkiaGameView(session = get<SkiaGameSession>(), inputEnabled = inputEnabled)
                    },
                )

                DesktopRenderBackend.Slick -> SwingAppHost.create(
                    fullscreen = SettingsEngine.getInstance().slick2dFullScreen
                )
                DesktopRenderBackend.Headless -> error("Headless mode does not create a desktop host")
            }
            bridge.filePickerHost = host
            host.closeCompletion.whenComplete { _, error ->
                if (error != null) {
                    logger.error(error) { "Desktop shutdown failed; native frame was not disposed" }
                } else {
                    bridge.filePickerHost = null
                    GlobalContext.stopKoin()
                    kotlin.system.exitProcess(0)
                }
            }
            host.installComposeStartupUi(AppUiState(
                composeEnabled = true,
                colorSchemeId = settings.selectedColorSchemeId.value,
                overlayOpacity = settings.overlayOpacity.value,
                loading = LoadingSceneHost(settings).snapshot(),
            ))
            if (System.getProperty("rwx.editor") == "1") {
                io.github.rwx.editor.EditorWindow.open()
            }
            try {
                val presenter: GameFramePresenter = when (renderBackend) {
                    DesktopRenderBackend.Skia -> GameFramePresenter { }
                    DesktopRenderBackend.Slick -> SlickFramePresenter(
                        presentSnapshot = { host.presentSnapshot(get<SlickGameSession>().currentSnapshot()) },
                        refreshPresentation = io.github.rwx.slick.SlickCanvasHost::requestPresentationRefresh,
                    )
                    DesktopRenderBackend.Headless -> error("Headless mode does not create a frame presenter")
                }
                val session = installApp(
                    viewportProvider = { host.viewport },
                    scheduler = SwingFrameScheduler(framePump = steamBridge::pump),
                    presenter = presenter,
                    options = options,
                    onQuit = host::requestClose,
                )
                host.installComposeUi(session)
            } catch (error: Throwable) {
                bridge.filePickerHost = null
                host.dispose()
                throw error
            }
        }
    }
}
