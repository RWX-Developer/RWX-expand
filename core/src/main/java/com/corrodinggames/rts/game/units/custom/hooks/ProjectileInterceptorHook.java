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
 * RWX 改动：①开关显式化（缺 key 时按旧规则自动开启）②排除标签/瞬间命中过滤
 * ③拦截时复用引擎自己的炮塔状态机开火（冷却 / 预热 / 转向全部沿用引擎语义，不自造充能）
 * ④锁定同一枚抛射物持续瞄准，瞄它的飞行反向角（入射角），该角度稳定、不随距离抖动。
 *
 * 开火走引擎同款「rotation 归零就绪 → 扣费 → 发射 → rotation 重置为 b(i)+t(i)」，
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

    /**
     * 持续锁定标记：每个单位/炮塔记住当前跟踪的抛射物。
     * 只要它还在全局抛射物表里且通过过滤，就一直瞄它，避免每帧换目标导致炮管来回摆、反应变慢。
     */
    private static final java.util.Map<BaseUnit, Projectile[]> RWX_TARGET_LOCK =
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
        int turretIndex = turretConfig.turretIndex;
        if (turretIndex < 0 || turretIndex >= customUnit.movementLevels.length) {
            turretIndex = 0;
        }
        float f = turretConfig.interceptProjectilesAndTargetingGroundUnderDistance;
        float f2 = turretConfig.interceptProjectilesAndUnderDistance;
        float f3 = turretConfig.interceptProjectilesAndOverHeight;
        AnimationSet includeTags = turretConfig.interceptProjectilesWithTags;
        AnimationSet excludeTags = turretConfig.interceptProjectilesWithoutTags;
        Projectile locked = getTargetLock(customUnit, turretIndex);
        Projectile projectile = null;
        Object[] objArrA = Projectile.a.a();
        int i = Projectile.a.size;
        for (int i2 = 0; i2 < i; i2++) {
            Projectile projectile2 = (Projectile) objArrA[i2];
            if (projectile2.isDestroyed || projectile2.isInstantHit && !turretConfig.interceptProjectilesAllowInstantHit) {
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
            // 已锁定的目标优先；只有它失效时才退回原版的「最后一个通过过滤的」。
            if (projectile2 == locked) {
                projectile = projectile2;
                break;
            }
            projectile = projectile2;
        }
        if (projectile == null) {
            clearTargetLock(customUnit, turretIndex);
            resetFireDelay(customUnit, turretIndex);
            return;
        }
        setTargetLock(customUnit, turretIndex, projectile);
        // ---- 以下全部沿用引擎炮塔状态机语义，不再自造 rotation 充能 ----
        UnitMovementData movementData = customUnit.movementLevels[turretIndex];

        // 1) 转向：目标角取抛射物飞行方向的反向角（入射角）。这个角在弹道直线飞行时是稳定值，
        //    不会像「炮塔→抛射物当前位置」那样随距离快速变化，炮塔能更快完成对准。
        //    仍走引擎同一个 customUnit.a(frameDelta, angle, i)，转速/加减速与攻击敌人时完全一致。
        float incomingAngle = Utility.normalizeAngle(projectile.angle + 180.0f, false);
        float angleDelta = Utility.rotateTowardsAngle(movementData.targetX, incomingAngle, 360.0f);
        float remaining = customUnit.a(frameDelta, incomingAngle, turretIndex);

        float warmup = Math.max(0.0f, customUnit.e(turretIndex));
        boolean warmupNoReset = customUnit.s(turretIndex);
        // rotation 由引擎每帧递减，归零才是冷却就绪（OrderableUnit:899 / :3296）。
        boolean cooldownReady = movementData.rotation == 0.0f;
        // 拦截炮必须真的把炮管转到目标方向。这里刻意用固定小角度，
        // 不能被 canAttackMaxAngle=181 那种「免转向发射」配置绕过（那是导弹语义）。
        boolean turretAligned = Utility.abs(angleDelta) <= 2.0f || Utility.abs(remaining) <= 2.0f;

        // 2) 先转向、再预热：转向中或冷却中不允许攒预热，已攒的按引擎 warmupCallDownRate 回落。
        //    这样炮管到位后还要走完 warmup 才会开火，不会出现「弹比炮管快」。
        if (!turretAligned || !cooldownReady) {
            if (!warmupNoReset && movementData.speed != 0.0f) {
                movementData.speed = Utility.moveTowardsZero(
                        movementData.speed, customUnit.f(turretIndex) * frameDelta);
            }
            resetFireDelay(customUnit, turretIndex);
            return;
        }

        // 3) 预热：和引擎 advanceMovementLevelTowardTarget 同款，把 speed 累到 e(i)=warmup。
        //    s(i)=warmupNoReset 为真时视为常驻就绪（引擎同款）。
        if (warmupNoReset) {
            movementData.speed = warmup;
        } else if (movementData.speed < warmup) {
            movementData.speed = Math.min(warmup, movementData.speed + frameDelta);
        }
        if (movementData.speed < warmup) {
            resetFireDelay(customUnit, turretIndex);
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

        // 5) 发射：照抄引擎 a(baseUnit,i) 的顺序 —— 先处理联炮冷却，再扣资源、生成弹丸、设 parent。
        //    这一步不能省：拦截钩子直接调 6 参数生成弹丸，绕过了 CustomUnit.a(baseUnit,i) 里的联炮联动。
        applyLinkedTurretCooldown(customUnit, turretConfig, turretIndex);
        customUnit.b(turretConfig);
        Projectile projectileA = customUnit.a((BaseUnit) null, projectile.posX, projectile.posY, turretConfig.turretIndex, (CustomProjectileTemplate) null, 0);
        if (projectileA == null) {
            return;
        }
        projectileA.fliesToPosition = true;
        projectileA.parentProjectile = projectile;
        // 目标已经交给拦截弹处理，本炮塔下一帧重新锁定新的抛射物。
        clearTargetLock(customUnit, turretIndex);

        // 6) 重置冷却：引擎同款 rotation = b(i) + t(i)，之后由引擎每帧递减到 0 再次就绪。
        //    非 warmupNoReset 炮塔发射后清空预热（引擎同款 speed=0）。
        movementData.rotation = customUnit.b(turretIndex) + customUnit.t(turretIndex);
        if (!warmupNoReset) {
            movementData.speed = 0.0f;
        }
    }

    private static Projectile getTargetLock(BaseUnit unit, int turretIndex) {
        Projectile[] locked = RWX_TARGET_LOCK.get(unit);
        return locked != null && turretIndex < locked.length ? locked[turretIndex] : null;
    }

    private static void setTargetLock(CustomUnit unit, int turretIndex, Projectile projectile) {
        Projectile[] locked = RWX_TARGET_LOCK.get(unit);
        if (locked == null || turretIndex >= locked.length) {
            locked = new Projectile[Math.max(turretIndex + 1, unit.movementLevels.length)];
            RWX_TARGET_LOCK.put(unit, locked);
        }
        locked[turretIndex] = projectile;
    }

    private static void clearTargetLock(BaseUnit unit, int turretIndex) {
        Projectile[] locked = RWX_TARGET_LOCK.get(unit);
        if (locked != null && turretIndex < locked.length) {
            locked[turretIndex] = null;
        }
    }

    /**
     * 复刻 CustomUnit.a(BaseUnit, int) 里的联炮冷却联动：
     * - linkDelayWithTurret：本炮开火时把引用它的那门炮冷却清零；
     * - attachedTo（linkedTurretIndex）：子炮开火时，若父炮不能射击但有 recoilOffset，则给父炮上冷却。
     * 拦截钩子直接走 6 参数生成弹丸，原本绕过了这段，附属炮塔会与主炮塔失步。
     */
    private static void applyLinkedTurretCooldown(CustomUnit customUnit, TurretConfig turretConfig, int turretIndex) {
        TurretConfig[] turrets = customUnit.unitConfig.turrets;
        for (int i = 0; i < turrets.length; i++) {
            TurretConfig other = turrets[i];
            if (other != null && i != turretIndex && other.linkedTurret != null
                    && other.linkedTurret == turretConfig
                    && customUnit.movementLevels[i].rotation < 9000.0f - ((customUnit.b(turretIndex) * 0.5f) - other.warmup)) {
                customUnit.movementLevels[i].rotation = 0.0f;
            }
        }
        if (turretConfig.linkedTurretIndex != -1) {
            int parentIndex = turretConfig.linkedTurretIndex;
            if (parentIndex < turrets.length) {
                TurretConfig parent = turrets[parentIndex];
                if (!parent.canShoot && parent.p != 0.0f) {
                    customUnit.movementLevels[parentIndex].rotation = customUnit.b(parentIndex) + customUnit.t(parentIndex);
                }
            }
        }
    }

    /** 炮管未到位、冷却中或无目标时，清掉 fireDelay 的累计，避免跨转向攒出「瞬间开火」。 */
    private static void resetFireDelay(BaseUnit customUnit, int turretIndex) {
        float[] waitArr = RWX_FIREDELAY_WAIT.get(customUnit);
        if (waitArr != null && turretIndex < waitArr.length) {
            waitArr[turretIndex] = 0.0f;
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
