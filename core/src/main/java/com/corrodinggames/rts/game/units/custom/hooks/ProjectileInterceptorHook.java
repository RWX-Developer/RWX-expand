package com.corrodinggames.rts.game.units.custom.hooks;

import com.corrodinggames.rts.game.Projectile;
import com.corrodinggames.rts.game.units.BaseUnit;
import com.corrodinggames.rts.game.units.UnitMovementData;
import com.corrodinggames.rts.game.units.buildings.LaserDefense;
import com.corrodinggames.rts.game.units.custom.*;
import com.corrodinggames.rts.gameFramework.Utility;
import io.github.rwx.geometry.PointF;

/**
 * 拦截炮塔钩子。
 * RWX 改动只在三点：①开关显式化（缺 key 时按旧规则自动开启）②新增排除标签/瞬间命中过滤
 * ③拦截时复用引擎自己的炮塔状态机开火（冷却 / 预热 / 转向全部沿用引擎语义，不自造充能）。
 *
 * 索敌保持原版：遍历全部炮弹，命中的最后一个当作目标。开火走引擎同款
 * 「rotation 归零就绪 → 扣费 → 发射 → rotation 重置为 b(i)+t(i)」，
 * rotation 依旧由引擎每帧递减，这里绝不再写正数把它"充回去"。
 */
public class ProjectileInterceptorHook extends CustomUnitRenderHook {
    public static final CustomUnitRenderHook a = new ProjectileInterceptorHook();
    static final PointF b = new PointF();

    /**
     * fireDelay 额外等待累加器（frameDelta 帧单位）。
     * 仅当 interceptProjectiles_fireDelay > 0 时启用；默认走引擎冷却、不碰这个 map。
     * 弱引用自动回收，按下标为炮塔 index。
     */
    private static final java.util.Map<BaseUnit, float[]> RWX_FIREDELAY_WAIT =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    @Override
    public void b(CustomUnit customUnit, float f) {
        CustomUnitConfig customUnitConfig = customUnit.unitConfig;
        int techLevel = customUnit.getTechLevel();
        for (int i = 0; i < techLevel; i++) {
            TurretConfig turretConfig = customUnitConfig.turrets[i];
            if (turretConfig.laserDefenceEnergyUse != null && customUnit.currentEnergy > 0.0f && !customUnit.v) {
                float fFloatValue = turretConfig.laserDefenceEnergyUse.floatValue();
                b.a(customUnit.E(i));
                float fM = customUnit.m();
                if (turretConfig.limitingRange < 99999.0f) {
                    fM = turretConfig.limitingRange;
                }
                if (LaserDefense.attackTarget(customUnit, b.x, b.y, customUnit.posZ, fM, fFloatValue)) {
                }
                if (customUnit.currentEnergy < 0.0f) {
                    customUnit.currentEnergy = 0.0f;
                    customUnit.v = true;
                }
            }
            if (turretConfig.interceptProjectiles) {
                a(customUnit, turretConfig, f);
            }
        }
    }

    public static void a(CustomUnit customUnit, TurretConfig turretConfig) {
        a(customUnit, turretConfig, 0.0f);
    }

    public static void a(CustomUnit customUnit, TurretConfig turretConfig, float frameDelta) {
        if (turretConfig == null || !turretConfig.interceptProjectiles) {
            return;
        }
        if (!customUnit.a(turretConfig)) {
            return;
        }
        float f = turretConfig.interceptProjectilesAndTargetingGroundUnderDistance;
        float f2 = turretConfig.interceptProjectilesAndUnderDistance;
        float f3 = turretConfig.interceptProjectilesAndOverHeight;
        AnimationSet includeTags = turretConfig.interceptProjectilesWithTags;
        AnimationSet excludeTags = turretConfig.interceptProjectilesWithoutTags;
        Projectile projectile = null;
        Object[] objArrA = Projectile.a.a();
        int i = Projectile.a.size;
        for (int i2 = 0; i2 < i; i2++) {
            Projectile projectile2 = (Projectile) objArrA[i2];
            if (projectile2.isInstantHit && !turretConfig.interceptProjectilesAllowInstantHit) {
                continue;
            }
            if (excludeTags != null && projectile2.animationSet != null && AnimationTag.a(projectile2.animationSet, excludeTags)) {
                continue;
            }
            if (includeTags != null && (projectile2.animationSet == null || !AnimationTag.a(projectile2.animationSet, includeTags))) {
                continue;
            }
            if (projectile2.posZ <= f3) {
                continue;
            }
            if (Utility.distanceSq(customUnit.posX, customUnit.posY, projectile2.posX, projectile2.posY) >= f2 * f2) {
                continue;
            }
            if (f >= 0.0f && Utility.distanceSq(customUnit.posX, customUnit.posY, projectile2.targetX, projectile2.targetY) >= f * f) {
                continue;
            }
            if (projectile2.sourceUnit != null && (projectile2.sourceUnit.team.d(customUnit.team) || projectile2.sourceUnit.team == customUnit.team)) {
                continue;
            }
            if (projectile2.lifeTimer <= 0.0f || a(projectile2)) {
                continue;
            }
            projectile = projectile2;
        }
        if (projectile == null) {
            return;
        }
        // ---- 以下全部沿用引擎炮塔状态机语义，不再自造 rotation 充能 ----
        int turretIndex = turretConfig.turretIndex;
        if (turretIndex < 0 || turretIndex >= customUnit.movementLevels.length) {
            turretIndex = 0;
        }
        UnitMovementData movementData = customUnit.movementLevels[turretIndex];

        // 1) 转向：直接调引擎的转向函数（它改 movementData.targetX，返回剩余角度差）
        float wantAngle = Utility.getAngleBetweenPoints(customUnit.posX, customUnit.posY, projectile.posX, projectile.posY);
        float remaining = customUnit.a(frameDelta, wantAngle, turretIndex);

        // 2) 预热：和引擎 advanceMovementLevelTowardTarget 同款，把 speed 累到 e(i)=warmup。
        //    s(i)=warmupNoReset 为真时视为常驻就绪（引擎同款）。
        float warmup = Math.max(0.0f, customUnit.e(turretIndex));
        boolean warmupNoReset = customUnit.s(turretIndex);
        if (warmupNoReset) {
            movementData.speed = warmup;
        } else if (movementData.speed < warmup) {
            movementData.speed = Math.min(warmup, movementData.speed + frameDelta);
        }
        boolean warmupReady = movementData.speed >= warmup;

        // 3) 冷却门槛：引擎每帧已把 rotation 递减，归零才就绪（OrderableUnit:3296）。
        //    绝不能写正数把它"充回去"——那是旧代码的 bug。
        if (movementData.rotation != 0.0f) {
            return;
        }
        // 炮管没转到位不开火（引擎同款小角度判定）
        if (Utility.abs(remaining) > 2.0f) {
            return;
        }
        if (!warmupReady) {
            return;
        }

        // 4) 可选 fireDelay：这是"就绪之后额外等多久"，用独立累加器，绝不碰 rotation。
        //    默认 -1/null 时完全跳过，行为与引擎普通开火一致。
        Float overrideDelay = turretConfig.interceptProjectilesFireDelay;
        if (overrideDelay != null && overrideDelay.floatValue() > 0.0f) {
            float[] waitArr = RWX_FIREDELAY_WAIT.get(customUnit);
            if (waitArr == null || turretIndex >= waitArr.length) {
                waitArr = new float[Math.max(turretIndex + 1, customUnit.movementLevels.length)];
                RWX_FIREDELAY_WAIT.put(customUnit, waitArr);
            }
            waitArr[turretIndex] += frameDelta;
            if (waitArr[turretIndex] < overrideDelay.floatValue()) {
                return;
            }
            waitArr[turretIndex] = 0.0f;
        }

        // 5) 发射：照抄引擎 a(baseUnit,i) 的顺序 —— 扣资源、生成弹丸、设 parent。
        customUnit.b(turretConfig);
        Projectile projectileA = customUnit.a((BaseUnit) null, projectile.posX, projectile.posY, turretConfig.turretIndex, (CustomProjectileTemplate) null, 0);
        if (projectileA == null) {
            return;
        }
        projectileA.fliesToPosition = true;
        projectileA.parentProjectile = projectile;

        // 6) 重置冷却：引擎同款 rotation = b(i) + t(i)，之后由引擎每帧递减到 0 再次就绪。
        //    非 warmupNoReset 炮塔发射后清空预热（引擎同款 speed=0）。
        movementData.rotation = customUnit.b(turretIndex) + customUnit.t(turretIndex);
        if (!warmupNoReset) {
            movementData.speed = 0.0f;
        }
    }

    public static boolean a(Projectile projectile) {
        Object[] objArrA = Projectile.a.a();
        int i = Projectile.a.size;
        for (int i2 = 0; i2 < i; i2++) {
            Projectile projectile2 = (Projectile) objArrA[i2];
            if (projectile2 != projectile && projectile2.parentProjectile == projectile) {
                return true;
            }
        }
        return false;
    }
}
