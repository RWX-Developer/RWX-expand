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
 * RWX 改动只在三点：①开关显式化（缺 key 时按旧规则自动开启）②新增排除标签/瞬间命中过滤 ③拦截时累加 rotation 充能。
 * 索敌与开火流程保持原版：遍历全部炮弹，命中的最后一个当作目标，不转向、不瞄准。
 */
public class ProjectileInterceptorHook extends CustomUnitRenderHook {
    public static final CustomUnitRenderHook a = new ProjectileInterceptorHook();
    static final PointF b = new PointF();

    /** 每台单位每个炮塔的拦截充能累计（毫秒），弱引用自动回收。 */
    private static final java.util.Map<BaseUnit, long[]> RWX_CHARGE =
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
        // 转向：借引擎自己的炮塔转向函数（a(float, float, int)），转速/加减速与普通索敌完全一致。
        int turretIndex = turretConfig.turretIndex;
        if (turretIndex < 0 || turretIndex >= customUnit.movementLevels.length) {
            turretIndex = 0;
        }
        UnitMovementData movementData = customUnit.movementLevels[turretIndex];
        float wantAngle = Utility.getAngleBetweenPoints(customUnit.posX, customUnit.posY, projectile.posX, projectile.posY);
        float remaining = customUnit.a(frameDelta, wantAngle, turretIndex);
        Float overrideDelay = turretConfig.interceptProjectilesFireDelay;
        float delay = overrideDelay != null ? overrideDelay.floatValue() : customUnit.b(turretIndex);
        float warmup = Math.max(0.0f, customUnit.e(turretIndex));
        long[] chargeArr = RWX_CHARGE.get(customUnit);
        if (chargeArr == null || turretIndex >= chargeArr.length) {
            chargeArr = new long[Math.max(turretIndex + 1, customUnit.movementLevels.length)];
            RWX_CHARGE.put(customUnit, chargeArr);
        }
        chargeArr[turretIndex] += (long) Math.max(0.0f, frameDelta * 1000.0f);
        float charged = chargeArr[turretIndex];
        movementData.rotation = Math.min(charged, delay);
        if (charged < delay + warmup) {
            return;
        }
        // 炮管没转到位不开火（引擎同款小角度判定）
        if (Utility.abs(remaining) > 2.0f) {
            return;
        }
        customUnit.b(turretConfig);
        Projectile projectileA = customUnit.a((BaseUnit) null, projectile.posX, projectile.posY, turretConfig.turretIndex, (CustomProjectileTemplate) null, 0);
        if (projectileA == null) {
            return;
        }
        projectileA.fliesToPosition = true;
        projectileA.parentProjectile = projectile;
        chargeArr[turretIndex] = 0L;
        movementData.rotation = 0.0f;
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
