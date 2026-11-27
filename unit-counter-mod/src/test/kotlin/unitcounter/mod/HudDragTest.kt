package unitcounter.mod

import kotlin.test.Test
import kotlin.test.assertTrue

/** 验证 HUD 拖拽钳制：任意偏移下组件矩形都必须与窗口保留至少 KEEP 像素重叠。 */
class HudDragTest {
    private val keep = HUD_KEEP_VISIBLE_PX

    private fun overlap(pos: Int, comp: Int, win: Int): Int {
        val left = maxOf(0, pos)
        val right = minOf(win, pos + comp)
        return maxOf(0, right - left)
    }

    @Test
    fun `top-left anchor never leaves window`() {
        val comp = 120
        val win = 800
        for (raw in -5000..5000 step 7) {
            val off = clampAxis(raw, comp, win, baseAtEnd = false)
            val pos = 0 + off // TOP_LEFT 基准在 0
            assertTrue(overlap(pos, comp, win) >= minOf(keep, comp))
        }
    }

    @Test
    fun `bottom-left anchor never leaves window`() {
        val comp = 44
        val win = 600
        for (raw in -5000..5000 step 7) {
            val off = clampAxis(raw, comp, win, baseAtEnd = true)
            val pos = (win - comp) + off // BOTTOM_LEFT 基准在 win-comp
            assertTrue(overlap(pos, comp, win) >= minOf(keep, comp))
        }
    }

    @Test
    fun `extreme drag still clamps`() {
        val comp = 200
        val win = 1000
        assertTrue(clampAxis(Int.MIN_VALUE, comp, win, false) > Int.MIN_VALUE)
        assertTrue(clampAxis(Int.MAX_VALUE, comp, win, false) < Int.MAX_VALUE)
        // 组件比窗口还大时也不能整块消失
        val big = 1200
        for (raw in -3000..3000 step 13) {
            val off = clampAxis(raw, big, win, false)
            assertTrue(overlap(off, big, win) >= minOf(keep, big, win))
        }
    }
}
