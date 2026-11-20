package io.github.rwx.app

import io.github.rwx.geometry.Viewport
import io.github.rwx.session.GameSession
import io.github.rwx.ui.AppScreen

internal class FrameRenderController(
    private val gameSession: GameSession,
    private val screenPresenter: ScreenPresenter,
    private val startupPreparationController: StartupPreparationController,
    private val presenter: GameFramePresenter,
) {
    fun render(
        screen: AppScreen,
        isBattleRoomJoinPending: Boolean,
        canResumeForFrame: Boolean,
        canvasViewport: Viewport,
        deltaSeconds: Float,
    ) {
        screenPresenter.refresh(screen, canResumeForFrame)
        val source = FrameSource.from(
            presentation = screenPresenter.presentation,
            isJoinPending = isBattleRoomJoinPending,
            isStartupMenuBackgroundLoading = startupPreparationController.isStartupMenuBackgroundLoading(screen),
            isEngineLoading = startupPreparationController.isEngineLoading(screen),
        )
        when (source) {
            FrameSource.Game -> gameSession.updateFrame(canvasViewport, deltaSeconds)
            FrameSource.BackgroundBattle, FrameSource.ResumeBackground ->
                gameSession.updateFrame(canvasViewport, deltaSeconds, drainVisibleLayerBuffers = true)

            FrameSource.Loading -> when {
                startupPreparationController.isStartupMenuBackgroundLoading(screen) ->
                    startupPreparationController.renderStartupMenuBackgroundLoadingFrame(canvasViewport, deltaSeconds)
                startupPreparationController.isEngineLoading(screen) ->
                    startupPreparationController.renderEngineLoadingFrame(canvasViewport)
                else -> Unit
            }
            FrameSource.Empty -> Unit
        }
        if (screen != lastScreen) {
            lastScreen = screen
            presentationRefreshFrames = PRESENTATION_REFRESH_FRAMES
        }
        if (presentationRefreshFrames > 0) {
            presentationRefreshFrames--
            presenter.requestPresentationRefresh()
        }
        startupPreparationController.updateLoadingStatus(screen)
        presenter.present()
        if (probeDue()) {
            println(
                "RWX_PROBE screen=$screen source=$source" +
                    " startupMenuLoading=" + startupPreparationController.isStartupMenuBackgroundLoading(screen) +
                    " rwGameLoading=" + startupPreparationController.isRwGameLoading(screen) +
                    " preparingMap=" + gameSession.isPreparingMap() +
                    " menuBgActive=" + gameSession.isMenuBackgroundActive() +
                    " readyForDisplay=" + gameSession.isReadyForDisplay(),
            )
            System.out.flush()
        }
    }

    private var lastScreen: AppScreen? = null
    private var presentationRefreshFrames = 0
    private var lastProbeAt = 0L

    private fun probeDue(): Boolean {
        if (!PROBE_ENABLED) return false
        val now = System.nanoTime()
        if (now - lastProbeAt < 1_000_000_000L) return false
        lastProbeAt = now
        return true
    }

    private companion object {
        const val PRESENTATION_REFRESH_FRAMES = 12
        val PROBE_ENABLED: Boolean = System.getenv("RWX_PROBE_FRAME") != null
    }
}
