package io.github.rwx.ui

import com.corrodinggames.rts.game.units.custom.hooks.ProjectileInterceptorHook
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 回归测试：拦截炮塔「算不算已对准」的容差。
 *
 * 原版炮塔用 x(i) = canAttackMaxAngle 判断是否已对准。绝大多数炮塔这个值是 181，
 * 语义是「不用转，直接打」（导弹那种全向发射）。拦截炮如果照抄，炮管还指着别处
 * 就能开火，观感上就是「炮没转就冒弹」。
 *
 * 所以拦截走一层收窄：合理的角度值尊重模组配置，免转向值（≥90、≤0）退回 2°。
 */
class InterceptAimToleranceTest {

    @Test
    fun `missile style no-turn config falls back to two degrees`() {
        // canAttackMaxAngle=181 是「免转向发射」，拦截不能照抄
        assertEquals(2f, ProjectileInterceptorHook.resolveAimTolerance(181f))
    }

    @Test
    fun `reasonable mod angle is respected`() {
        // 模组显式配了 30°，说明它就想要 30° 的容差
        assertEquals(30f, ProjectileInterceptorHook.resolveAimTolerance(30f))
        assertEquals(5f, ProjectileInterceptorHook.resolveAimTolerance(5f))
    }

    @Test
    fun `zero and negative fall back to two degrees`() {
        assertEquals(2f, ProjectileInterceptorHook.resolveAimTolerance(0f))
        assertEquals(2f, ProjectileInterceptorHook.resolveAimTolerance(-10f))
    }

    @Test
    fun `exactly ninety falls back because it is a half plane`() {
        // 90° 已经不是「对准」而是「整半个平面」，对拦截没有意义
        assertEquals(2f, ProjectileInterceptorHook.resolveAimTolerance(90f))
    }
}
