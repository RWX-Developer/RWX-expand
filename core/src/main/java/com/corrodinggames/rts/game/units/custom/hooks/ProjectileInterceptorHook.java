package com.corrodinggames.rts.game.units.custom.hooks;

import com.corrodinggames.rts.game.Projectile;
import com.corrodinggames.rts.game.units.BaseUnit;
import com.corrodinggames.rts.game.units.UnitMovementData;
import com.corrodinggames.rts.game.units.buildings.LaserDefense;
import com.corrodinggames.rts.game.units.custom.*;
import com.corrodinggames.rts.gameFramework.Utility;
import io.github.rwx.geometry.PointF;

/* JADX INFO: renamed from: com.corrodinggames.rts.game.units.custom.b.k */
/* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/b/k.class */
public class ProjectileInterceptorHook extends CustomUnitRenderHook {
    public static final CustomUnitRenderHook a = new ProjectileInterceptorHook();
    static final PointF b = new PointF();

    @Override // com.corrodinggames.rts.game.units.custom.hooks.CustomUnitRenderHook
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
            // RWX：开关改成 interceptProjectiles；withTags 只当过滤器，不再负责开启。
            if (turretConfig.interceptProjectiles) {
                a(customUnit, turretConfig);
            }
        }
    }

    /** RWX：炮管每 tick 最多转多少度。转到阈值内才开火。 */
    private static final float TURN_PER_TICK = 12.0f;

    /** RWX：开火允许的角度误差（度）。 */
    private static final float AIM_TOLERANCE = 6.0f;

    public static void a(CustomUnit customUnit, TurretConfig turretConfig) {
        if (turretConfig == null || !turretConfig.interceptProjectiles) {
            return;
        }
        if (!customUnit.a(turretConfig)) {
            return;
        }
        int turretIndex = turretConfig.turretIndex;
        int techLevel = customUnit.getTechLevel();
        if (turretIndex < 0 || turretIndex >= techLevel || turretIndex >= customUnit.movementLevels.length) {
            return;
        }
        UnitMovementData movementData = customUnit.movementLevels[turretIndex];
        // 充能：默认沿用该炮塔自己的 delay，写了 interceptProjectiles_fireDelay 就用新键。
        float delay = turretConfig.interceptProjectilesFireDelay > 0.0f
                ? turretConfig.interceptProjectilesFireDelay
                : customUnit.b(turretIndex);
        if (movementData.rotation < delay) {
            return;
        }
        // 预热（warmup）也要到位，否则等于绕过预热直接开火。
        float warmup = customUnit.e(turretIndex);
        if (warmup > 0.0f && movementData.speed < warmup) {
            return;
        }
        float f = turretConfig.interceptProjectilesAndTargetingGroundUnderDistance;
        float f2 = turretConfig.interceptProjectilesAndUnderDistance;
        float f3 = turretConfig.interceptProjectilesAndOverHeight;
        AnimationSet includeTags = turretConfig.interceptProjectilesWithTags;
        AnimationSet excludeTags = turretConfig.interceptProjectilesWithoutTags;
        Projectile projectile = null;
        float bestDistanceSq = Float.MAX_VALUE;
        Object[] objArrA = Projectile.a.a();
        int i = Projectile.a.size;
        for (int i2 = 0; i2 < i; i2++) {
            Projectile projectile2 = (Projectile) objArrA[i2];
            // 瞬间命中的炮弹默认不拦（可以在 INI 里打开）
            if (projectile2.isInstantHit && !turretConfig.interceptProjectilesAllowInstantHit) {
                continue;
            }
            // 排除标签优先
            if (excludeTags != null && projectile2.animationSet != null && AnimationTag.a(projectile2.animationSet, excludeTags)) {
                continue;
            }
            // 写了 withTags 才做包含过滤；没写就一视同仁
            if (includeTags != null && (projectile2.animationSet == null || !AnimationTag.a(projectile2.animationSet, includeTags))) {
                continue;
            }
            if (projectile2.posZ <= f3) {
                continue;
            }
            float distanceSq = Utility.distanceSq(customUnit.posX, customUnit.posY, projectile2.posX, projectile2.posY);
            if (distanceSq >= f2 * f2 || distanceSq >= bestDistanceSq) {
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
            bestDistanceSq = distanceSq;
        }
        if (projectile == null) {
            return;
        }
        // 转向瞄准：炮管角度逐步转向目标，转到位才发射。
        float wantAngle = Utility.getAngleBetweenPoints(customUnit.posX, customUnit.posY, projectile.posX, projectile.posY);
        float diff = Utility.rotateTowardsAngle(movementData.targetX, wantAngle, 360.0f);
        if (Utility.abs(diff) > AIM_TOLERANCE) {
            movementData.targetX = Utility.rotateTowardsAngle(movementData.targetX + (diff > 0.0f ? TURN_PER_TICK : -TURN_PER_TICK), 0.0f, 360.0f);
            return;
        }
        movementData.targetX = wantAngle;
        customUnit.b(turretConfig);
        Projectile projectileA = customUnit.a((BaseUnit) null, projectile.posX, projectile.posY, turretIndex, (CustomProjectileTemplate) null, 0);
        projectileA.fliesToPosition = true;
        projectileA.parentProjectile = projectile;
        // 开火后重置充能
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
