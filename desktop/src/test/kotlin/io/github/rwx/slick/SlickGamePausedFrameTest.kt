package io.github.rwx.slick

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 回归测试：战局内暂停（沙盒建造者的 Pause Game 动作、空格键把 gameSpeed 置 0）
 * 不能被当成「暂停背景」，否则渲染层会跳过整个游戏循环 —— HUD 不画（UI 消失），
 * 且 gameUI.updateInput() 不再执行，空格/建造者动作再也解不开暂停（游戏冻结）。
 */
class SlickGamePausedFrameTest {

    private fun pausedFrame(
        pausedBackground: Boolean,
        enginePaused: Boolean = false,
        hasLoadedLevel: Boolean = true,
        gameVisible: Boolean = true,
        runningMenuBackground: Boolean = false,
        networkMultiplayerActive: Boolean = false,
    ): Boolean = shouldRenderPausedSlickFrame(
        pausedBackground = pausedBackground,
        hasLoadedLevel = hasLoadedLevel,
        gameVisible = gameVisible,
        runningMenuBackground = runningMenuBackground,
        networkMultiplayerActive = networkMultiplayerActive,
        enginePaused = enginePaused,
    )

    @Test
    fun inGamePauseKeepsGameLoopRunning() {
        // 战局内暂停（gameSpeed=0）不是暂停背景：循环必须继续跑，
        // 引擎内部用 deltaSpeed *= gameSpeed 冻结模拟，HUD 和输入都还在。
        assertFalse(pausedFrame(pausedBackground = false))
    }

    @Test
    fun composePauseBackgroundStillFreezes() {
        // Compose 暂停菜单背景需要真的停循环并只画世界。
        assertTrue(pausedFrame(pausedBackground = true))
    }

    @Test
    fun enginePauseStillFreezes() {
        assertTrue(pausedFrame(pausedBackground = false, enginePaused = true))
    }

    @Test
    fun pausedBackgroundRequiresVisibleLoadedLevel() {
        assertFalse(pausedFrame(pausedBackground = true, hasLoadedLevel = false))
        assertFalse(pausedFrame(pausedBackground = true, gameVisible = false))
        assertFalse(pausedFrame(pausedBackground = true, runningMenuBackground = true))
        assertFalse(pausedFrame(pausedBackground = true, networkMultiplayerActive = true))
    }
}
