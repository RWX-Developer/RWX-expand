package unitcounter.mod

import kotlin.test.Test
import kotlin.test.assertTrue

/** 验证 HUD 越界贴回：任意实际位置钳制后，组件矩形都必须与窗口保留至少 min(KEEP, 组件尺寸) 重叠。 */
class HudDragTest {
    private val keep = HUD_KEEP_VISIBLE_PX

    private fun overlap(pos: Int, comp: Int, win: Int): Int {
        val left = maxOf(0, pos)
        val right = minOf(win, pos + comp)
        return maxOf(0, right - left)
    }

    @Test
    fun `clamp keeps overlap for any position`() {
        for (comp in listOf(44, 120, 460)) {
            val win = 800
            for (raw in -5000..5000 step 7) {
                val pos = clampPosition(raw, comp, win)
                assertTrue(overlap(pos, comp, win) >= minOf(keep, comp, win))
            }
        }
    }

    @Test
    fun `extreme drag still clamps`() {
        val comp = 200
        val win = 1000
        assertTrue(clampPosition(Int.MIN_VALUE, comp, win) > Int.MIN_VALUE)
        assertTrue(clampPosition(Int.MAX_VALUE, comp, win) < Int.MAX_VALUE)
        // 组件比窗口还大时也不能整块消失
        val big = 1200
        for (raw in -3000..3000 step 13) {
            val pos = clampPosition(raw, big, win)
            assertTrue(overlap(pos, big, win) >= minOf(keep, big, win))
        }
    }

    @Test
    fun `in-range position is untouched`() {
        val pos = clampPosition(300, 120, 800)
        assertTrue(pos == 300)
    }
}
