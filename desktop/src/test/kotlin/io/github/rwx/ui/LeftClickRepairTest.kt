package io.github.rwx.ui

import com.corrodinggames.rts.gameFramework.ui.GameUI
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 回归测试：左键「点到即修复」的判定边界。
 *
 * 背景：原版桌面语义是「左键只选中、右键才下令」，沙盒建造者/工程单位用它点友方受损目标
 * 只会选中、不会修，表现为「点击修复失效」。修复后左键在命中「友方 + 已受损 + 选中单位
 * 都能修」时会直接下修复令；其余情况必须原样回落到选中，不能抢走右键的攻击/移动语义。
 *
 * 参数顺序：friendly, damaged, hasOrderable, allCanRepair（Java 静态方法不支持具名参数）。
 */
class LeftClickRepairTest {

    @Test
    fun `friendly damaged repairable triggers repair`() {
        assertTrue(GameUI.shouldRepairOnLeftClick(true, true, true, true))
    }

    @Test
    fun `full health friendly unit only selects`() {
        assertFalse(
            GameUI.shouldRepairOnLeftClick(true, false, true, true),
            "满血目标不应该触发修复，否则左键就没法正常选中自己的单位",
        )
    }

    @Test
    fun `enemy target never repairs on left click`() {
        assertFalse(
            GameUI.shouldRepairOnLeftClick(false, true, true, true),
            "敌方目标必须留给右键攻击",
        )
    }

    @Test
    fun `no selected orderable unit cannot repair`() {
        assertFalse(GameUI.shouldRepairOnLeftClick(true, true, false, true))
    }

    @Test
    fun `selection containing a non repairer falls back to selection`() {
        assertFalse(
            GameUI.shouldRepairOnLeftClick(true, true, true, false),
            "只要有一个选中单位修不了，就不应该抢左键（避免误下令）",
        )
    }
}
