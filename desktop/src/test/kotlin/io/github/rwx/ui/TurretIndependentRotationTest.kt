package io.github.rwx.ui

import com.corrodinggames.rts.game.units.custom.TurretConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 回归测试：挂了父炮塔的拦截炮能否独立旋转（`interceptProjectiles_independentRotation`）。
 *
 * 引擎 `CustomUnit.a(int,float)` 会把父炮塔的角度增量原封不动加到所有
 * `linkedTurretIndex` 指向它的子炮塔上。父炮塔一对地瞄准，挂在它上面的拦截炮就被拖着走，
 * 永远指不到来袭弹——表现就是「炮塔跟着主炮转，但不朝导弹开火」。
 *
 * 开关默认 false（保持原版联动），显式写 true 才让这门炮自己转自己的。
 */
class TurretIndependentRotationTest {

    @Test
    fun `attached turret follows parent by default`() {
        val turret = TurretConfig()
        turret.linkedTurretIndex = 1

        assertFalse(turret.interceptProjectilesIndependentRotation, "默认必须是 false，不能悄悄改掉原版联动")
        assertTrue(turret.followsParentTurret())
    }

    @Test
    fun `standalone turret has no parent to follow`() {
        val turret = TurretConfig()
        assertEquals(-1, turret.linkedTurretIndex)
        assertFalse(turret.followsParentTurret())

        // 没挂父炮时这个开关没有意义，也不该把它变成「跟随」
        turret.interceptProjectilesIndependentRotation = true
        assertFalse(turret.followsParentTurret())
    }

    @Test
    fun `independent rotation detaches from parent angle`() {
        val turret = TurretConfig()
        turret.linkedTurretIndex = 2
        turret.interceptProjectilesIndependentRotation = true

        assertFalse(turret.followsParentTurret(), "开了独立旋转就不该再吃父炮的角度增量")
        // 独立旋转只解耦角度，父炮下标仍要保留（挂点位置、绘制层级都靠它）
        assertEquals(2, turret.linkedTurretIndex)
    }

    @Test
    fun `copy keeps the flag`() {
        val source = TurretConfig()
        source.interceptProjectilesIndependentRotation = true

        val copy = TurretConfig()
        copy.a(source)

        assertTrue(copy.interceptProjectilesIndependentRotation, "逐级拷贝（继承/升级）时开关不能丢")
    }
}
