package io.github.rwx.ui

import com.corrodinggames.rts.game.units.custom.hooks.ProjectileInterceptorHook
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * 回归测试：拦截炮塔的「只拦正前方扇区」判定。
 *
 * 背景：拦截炮塔原本全向拦截，炮管会为背后的抛射物转半圈，看起来乱转。
 * 改成只接炮塔正面半角内的目标；参考方向 = 单位朝向 + 炮塔 idleDir，
 * 所以车体转向时「正面」跟着转。
 *
 * 参数顺序：refAngle, bearing, halfAngle（Java 静态方法不支持具名参数）。
 * 角度单位是度，顺时针为正；0 = 正右方，90 = 正下方（RW 的屏幕坐标系）。
 */
class InterceptFrontSectorTest {

    @Test
    fun `target straight ahead is within front sector`() {
        // 参考 0°，目标也在 0°，正前方
        assertTrue(ProjectileInterceptorHook.isWithinFrontSector(0f, 0f, 90f))
    }

    @Test
    fun `target at sector edge is still accepted`() {
        // 半角 90°，目标正好在 90° 边界上，应当算命中
        assertTrue(ProjectileInterceptorHook.isWithinFrontSector(0f, 90f, 90f))
    }

    @Test
    fun `target slightly beyond the edge is rejected`() {
        assertFalse(ProjectileInterceptorHook.isWithinFrontSector(0f, 91f, 90f))
    }

    @Test
    fun `target directly behind is rejected`() {
        // 背后 180°，绝不该被拦
        assertFalse(ProjectileInterceptorHook.isWithinFrontSector(0f, 180f, 90f))
    }

    @Test
    fun `sector wraps across the zero degree boundary`() {
        // 参考 350°，目标 10°：实际只差 20°，应当命中（不能因为跨 0° 就漏掉）
        assertTrue(ProjectileInterceptorHook.isWithinFrontSector(350f, 10f, 90f))
        // 反过来也一样
        assertTrue(ProjectileInterceptorHook.isWithinFrontSector(10f, 350f, 90f))
    }

    @Test
    fun `halfangle 180 or below zero disables the restriction`() {
        // 180 = 不限方向，背后也拦（兼容想要全向拦截的模组）
        assertTrue(ProjectileInterceptorHook.isWithinFrontSector(0f, 180f, 180f))
        // 0 / 负数同样视为开关关闭，而不是「什么都拦不到」
        assertTrue(ProjectileInterceptorHook.isWithinFrontSector(0f, 180f, 0f))
        assertTrue(ProjectileInterceptorHook.isWithinFrontSector(0f, 180f, -1f))
    }

    @Test
    fun `narrow sector only accepts near the facing direction`() {
        // 半角 30°：45° 的侧向目标不该被接
        assertTrue(ProjectileInterceptorHook.isWithinFrontSector(0f, 20f, 30f))
        assertFalse(ProjectileInterceptorHook.isWithinFrontSector(0f, 45f, 30f))
    }
}
