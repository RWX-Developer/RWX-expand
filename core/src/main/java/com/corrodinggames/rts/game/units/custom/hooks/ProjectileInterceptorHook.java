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
 * ④锁定同一枚抛射物并每帧按它的 XY 方位持续跟踪瞄准（不看高度，高度只参与过滤）。
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

    /**
     * 拦截预热累加器（帧单位），按下标为炮塔 index。
     *
     * 为什么不直接用引擎的 `movementLevels[i].speed`：
     * 引擎 `updateAttackTargeting` 末尾有一句「没有攻击目标时把所有炮塔 speed 按
     * warmupCallDownRate 回落」。拦截炮塔往往没有地面攻击目标（很多还是 canShoot:false
     * 的纯拦截炮），这句回落会每帧把我们加进去的预热进度抹平——表现就是炮塔一直对着
     * 导弹转、预热永远到不了头、一发都不打。
     * 所以预热进度自己攒，只把结果镜像回 speed 供渲染/动画读取。
     */
    private static final java.util.Map<BaseUnit, float[]> RWX_WARMUP =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * 临时诊断：每个炮塔「锁着目标但没开火」的连续帧数。
     * 累计到 90 帧（约 1.5 秒）就写一行原因到 logs/rwx-intercept.log，然后清零，
     * 所以正常拦截时日志是空的，出问题时约每 1.5 秒每条炮塔一行。
     */
    private static final java.util.Map<BaseUnit, int[]> RWX_BLOCK_FRAMES =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());
    private static java.io.Writer rwxLogWriter;
    private static int rwxLogLines;
    private static final int RWX_LOG_MAX = 400;

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
        // attachedTo/slave 子炮的 linkedTurretIndex 指向父炮；父炮只跟随角度/状态，不额外开火。
        int parentIndex = turretConfig.linkedTurretIndex;
        if (parentIndex < 0 || parentIndex >= customUnit.movementLevels.length || parentIndex == turretIndex) {
            parentIndex = -1;
        }
        float f = turretConfig.interceptProjectilesAndTargetingGroundUnderDistance;
        float f2 = turretConfig.interceptProjectilesAndUnderDistance;
        float f3 = turretConfig.interceptProjectilesAndOverHeight;
        Projectile locked = getTargetLock(customUnit, turretIndex);
        // RWX：只拦炮塔正前方扇区。参考方向沿用引擎射界判定语义（单位朝向 + 本炮塔 idleDir），
        // 所以车体转向时「正面」跟着一起转。半角 >=180 或 <=0 视为不限方向，保持全向拦截。
        float rwxFrontHalfAngle = turretConfig.interceptProjectilesOnlyInFrontAngle;
        boolean rwxFrontOnly = rwxFrontHalfAngle > 0.0f && rwxFrontHalfAngle < 180.0f;
        float rwxFrontAngle = rwxFrontOnly ? rwxFrontFacingAngle(customUnit, turretConfig) : 0.0f;

        // 选目标照原版 updateAttackTargeting 的顺序：先校验手上已有的目标，它失效了才重新搜索。
        // 这样每帧省掉一次全抛射物遍历，也不会因为遍历顺序变化而频繁换目标、炮管来回摆。
        Projectile projectile = null;
        if (locked != null && isInterceptable(customUnit, turretConfig, locked,
                f, f2, f3, rwxFrontOnly, rwxFrontAngle, rwxFrontHalfAngle)) {
            projectile = locked;
        } else {
            Object[] objArrA = Projectile.a.a();
            int i = Projectile.a.size;
            for (int i2 = 0; i2 < i; i2++) {
                Projectile projectile2 = (Projectile) objArrA[i2];
                if (!isInterceptable(customUnit, turretConfig, projectile2,
                        f, f2, f3, rwxFrontOnly, rwxFrontAngle, rwxFrontHalfAngle)) {
                    continue;
                }
                // 与原版一致：同条件下取遍历到的最后一个（原版也是覆盖式赋值，不做距离排序）。
                projectile = projectile2;
            }
        }
        UnitMovementData movementData = customUnit.movementLevels[turretIndex];
        if (projectile == null) {
            clearTargetLock(customUnit, turretIndex);
            if (parentIndex != -1) {
                clearTargetLock(customUnit, parentIndex);
            }
            resetFireDelay(customUnit, turretIndex);
            return;
        }
        setTargetLock(customUnit, turretIndex, projectile);
        int[] blocked = blockedFrames(customUnit, turretIndex);
        // ---- 以下严格按原版 OrderableUnit.advanceMovementLevelTowardTarget 的顺序重排 ----
        // 原版一次开火的完整顺序：
        //   转向(先记 targetY) → 对齐置 isActive → rotation==0 && canShoot → 复核目标可打
        //   → 预热(speed 累加，起手播 warmupStartEffect) → 满预热 → rotation=b(i)+t(i)
        //   → 开火 → M(i) 复位后坐力 → isOddShot 翻转 → 非常驻预热清空 speed。

        // 1) 转向：每帧从锁定抛射物的当前 XY 重算方位角，持续跟踪它。
        //    只看 posX/posY，不看 posZ；高度只参与上面的「能不能拦」过滤，不参与瞄准。
        //    起点取炮塔实际位置 G(i) + 本级 shadowOffset，和引擎攻击敌人时一致。
        //    仍走引擎同一个 customUnit.a(frameDelta, angle, i)，转速/加减速与攻击敌人时完全一致。
        PointF turretPos = customUnit.G(turretIndex);
        PointF shadowOffset = customUnit.getShadowOffsetForLevel(turretIndex);
        float wantAngle = Utility.getAngleBetweenPoints(
                turretPos.x, turretPos.y,
                projectile.posX + shadowOffset.x, projectile.posY + shadowOffset.y);
        // 原版在转向之前会把「上一帧的目标角」存进 targetY；待机复位在 shouldResetTurret=false
        // 时正是拿 targetY 当基准，缺了这一步炮管归位会跳。
        movementData.targetY = movementData.targetX;
        float remaining = customUnit.a(frameDelta, wantAngle, turretIndex);

        // 对齐容差照原版 x(i)=canAttackMaxAngle。但拦截必须真的把炮管指过去，
        // 所以 canAttackMaxAngle=181 这种「免转向发射」的导弹配置不照抄，退回 2°。
        // 注意：这里只做局部判定，不写 movementData.isActive——那是引擎自己
        // u(i) 开火门控用的标记，我们的炮塔不走引擎开火循环，写它只会让同一门炮
        // 在瞄导弹的瞬间被引擎判成「可对地面目标开火」。
        float tolerance = resolveAimTolerance(customUnit.x(turretIndex));
        boolean aligned = Utility.abs(remaining) <= tolerance;

        // 子炮在拦截时，父炮同步进入瞄准状态并跟随子炮角度（父炮不额外发射）。
        if (parentIndex != -1) {
            setTargetLock(customUnit, parentIndex, projectile);
            UnitMovementData parentData = customUnit.movementLevels[parentIndex];
            parentData.targetY = parentData.targetX;
            parentData.targetX = movementData.targetX;
            parentData.velocityX = 0.0f;
            parentData.velocityY = 0.0f;
        }

        float warmup = Math.max(0.0f, customUnit.e(turretIndex));
        boolean warmupNoReset = customUnit.s(turretIndex);
        // rotation 由引擎每帧递减，归零才是冷却就绪（OrderableUnit:899 / :3296）。
        boolean cooldownReady = movementData.rotation == 0.0f;
        float[] warmupArr = warmupArray(customUnit, turretIndex);
        float charged = warmupArr[turretIndex];

        // 2) 没对准 / 冷却中：预热按原版 warmupCallDownRate 回落，不攒进度。
        //    这条对应原版 updateAttackTargeting 末尾那段 !z && speed!=0 的回落。
        //
        //    注意这里**不查** r(i)=canShoot。模组里的拦截炮塔普遍写着 canShoot:false——
        //    那个字段的语义是「不参与对单位/建筑的常规攻击」，不是「不能开火」。
        //    例如出发级战列舰的 [turret_L1]/[turret_L2]、维京驱逐舰、里海怪物、海军工厂，
        //    它们的开火许可来自 interceptProjectiles:true，而不是 canShoot。
        //    一旦照抄原版 r(i)，这些炮塔就会一直转、永远不开火。
        if (!aligned || !cooldownReady) {
            if (shouldLogBlock(customUnit, turretIndex, blocked)) {
                rwxLog("[BLOCK] " + unitLabel(customUnit) + " turret=" + turretIndex
                        + " aligned=" + aligned + " remaining=" + Utility.abs(remaining)
                        + " tol=" + tolerance + " rotation=" + movementData.rotation
                        + " speed=" + charged + "/" + warmup
                        + " frontOnly=" + rwxFrontOnly + " frontAngle=" + rwxFrontAngle
                        + " targetZ=" + projectile.posZ + " turretX=" + movementData.targetX);
            }
            warmupArr[turretIndex] = decayWarmup(customUnit, turretIndex, movementData,
                    charged, warmup, warmupNoReset, frameDelta);
            resetFireDelay(customUnit, turretIndex);
            return;
        }

        // 3) 开火前补一次资源门。原版 u(i) 在真正开火那一刻才查 a(turretConfig) 的消耗，
        //    目标是同一单位上多门拦截炮共享能量/弹药——前面的炮抽干了，这门就不该打。
        //    目标有效性不必重查：本帧开头已经验过，帧内没有能改变它的状态。
        if (!customUnit.a(turretConfig)) {
            if (shouldLogBlock(customUnit, turretIndex, blocked)) {
                rwxLog("[BLOCK] " + unitLabel(customUnit) + " turret=" + turretIndex
                        + " reason=resource energy=" + customUnit.currentEnergy
                        + " usage=" + (turretConfig.resourceUsage != null));
            }
            warmupArr[turretIndex] = decayWarmup(customUnit, turretIndex, movementData,
                    charged, warmup, warmupNoReset, frameDelta);
            resetFireDelay(customUnit, turretIndex);
            return;
        }

        // 4) 预热：进度存在钩子自己的累加器里，每帧镜像回 speed 给引擎渲染/动画用。
        //    起手那一帧播 warmupStartEffect；常驻预热(warmupNoReset)只是开火后不清零，
        //    所以第一发仍然有充能过程。
        if (charged <= 0.0f && warmup > 0.0f) {
            customUnit.b((BaseUnit) null, turretIndex);
        }
        charged = Math.min(warmup, charged + frameDelta);
        warmupArr[turretIndex] = charged;
        movementData.speed = charged;
        if (charged < warmup) {
            if (shouldLogBlock(customUnit, turretIndex, blocked)) {
                rwxLog("[BLOCK] " + unitLabel(customUnit) + " turret=" + turretIndex
                        + " reason=warmup charged=" + charged + "/" + warmup
                        + " aligned=" + aligned + " rotation=" + movementData.rotation);
            }
            resetFireDelay(customUnit, turretIndex);
            return;
        }

        // 5) 可选 fireDelay：这是"就绪之后额外等多久"，用独立累加器，绝不碰 rotation。
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

        // 6) 发射：照抄引擎 a(baseUnit,i) 的顺序 —— 先处理联炮冷却，再扣资源、生成弹丸、设 parent。
        //    这一步不能省：拦截钩子直接调 6 参数生成弹丸，绕过了 CustomUnit.a(baseUnit,i) 里的联炮联动。
        applyLinkedTurretCooldown(customUnit, turretConfig, turretIndex);
        customUnit.b(turretConfig);
        Projectile projectileA = customUnit.a((BaseUnit) null, projectile.posX, projectile.posY, turretConfig.turretIndex, (CustomProjectileTemplate) null, 0);
        if (projectileA == null) {
            return;
        }
        projectileA.fliesToPosition = true;
        projectileA.parentProjectile = projectile;
        if (blocked[turretIndex] != 0) {
            blocked[turretIndex] = 0;
        }
        if (rwxLogLines < RWX_LOG_MAX) {
            rwxLog("[FIRE] " + unitLabel(customUnit) + " turret=" + turretIndex
                    + " dist=" + Math.sqrt(Utility.distanceSq(customUnit.posX, customUnit.posY,
                    projectile.posX, projectile.posY)));
        }
        // 目标已经交给拦截弹处理，本炮塔（及跟随的父炮）下一帧重新锁定新的抛射物。
        clearTargetLock(customUnit, turretIndex);
        if (parentIndex != -1) {
            clearTargetLock(customUnit, parentIndex);
        }

        // 7) 发射后的收尾，顺序与原版一致。
        //    t(i) 读的是 speed/warmup 比值，必须在清 speed 之前算，否则这会白丢一段冷却缩减。
        movementData.rotation = customUnit.b(turretIndex) + customUnit.t(turretIndex);
        customUnit.M(turretIndex);
        movementData.isOddShot = !movementData.isOddShot;
        if (!warmupNoReset) {
            charged = 0.0f;
        }
        warmupArr[turretIndex] = charged;
        movementData.speed = charged;
    }

    /** 引擎待机复位用：该炮塔是否正在锁定瞄准抛射物。 */
    public static boolean isAiming(BaseUnit unit, int turretIndex) {
        return getTargetLock(unit, turretIndex) != null;
    }

    /**
     * RWX：方向是否落在「正面扇区」内（纯判定，便于回归测试）。
     *
     * @param refAngle  炮塔正面参考角（单位朝向 + idleDir）
     * @param bearing   目标相对炮塔的方位角
     * @param halfAngle 扇区半角；&lt;=0 或 &gt;=180 视为不限方向，永远返回 true
     */
    public static boolean isWithinFrontSector(float refAngle, float bearing, float halfAngle) {
        if (halfAngle <= 0.0f || halfAngle >= 180.0f) {
            return true;
        }
        return Utility.abs(Utility.rotateTowardsAngle(refAngle, bearing, 360.0f)) <= halfAngle;
    }

    /**
     * RWX：炮塔「正面」参考角。
     *
     * 与引擎自己的射界判定完全同款（CustomUnit.a(TurretConfig,int,BaseUnit,boolean,boolean)
     * 里的 limitingAngle 分支）：linkedTurretIndex 有效时取父炮当前角，否则取单位朝向，
     * 两者都再加本炮塔的 idleDir。所以车体一转，各炮塔的「正面」自动跟着转。
     */
    private static float rwxFrontFacingAngle(CustomUnit customUnit, TurretConfig turretConfig) {
        int linked = turretConfig.linkedTurretIndex;
        if (linked >= 0 && linked < customUnit.movementLevels.length) {
            return customUnit.movementLevels[linked].targetX + turretConfig.idleDir;
        }
        return customUnit.rotationSpeed + turretConfig.idleDir;
    }

    /**
     * 拦截开火的对齐容差（角度）。
     *
     * 原版 {@code OrderableUnit.advanceMovementLevelTowardTarget} 拿 {@code x(i)}
     * （即 {@code canAttackMaxAngle}）判断炮塔是否"已对准"。绝大多数炮塔这个值是 181，
     * 语义是"不用转，直接打"（导弹那种全向发射）。拦截炮必须真的把炮管指向来袭弹，
     * 所以：
     * <ul>
     *   <li>0 &lt; 角度 &lt; 90：尊重模组配置，直接用它；</li>
     *   <li>负数 / 0 / ≥90（含 181 这种免转向值）：退回 2°。</li>
     * </ul>
     */
    public static float resolveAimTolerance(float canAttackMaxAngle) {
        if (canAttackMaxAngle <= 0.0f || canAttackMaxAngle >= 90.0f) {
            return 2.0f;
        }
        return canAttackMaxAngle;
    }

    /**
     * 单枚抛射物能不能作为本炮塔的拦截目标。
     *
     * 对应原版 {@code OrderableUnit.a(int, BaseUnit, boolean, boolean)} 的角色：
     * 扫描选目标时用它过滤，开火前再用同一份判定复核一次——
     * 原版也是「rotation 归零那一帧重新 a(i, target, false, false)」，不是选完就不再验。
     */
    private static boolean isInterceptable(
            CustomUnit customUnit,
            TurretConfig turretConfig,
            Projectile projectile,
            float targetingGroundDistance,
            float underDistance,
            float overHeight,
            boolean frontOnly,
            float frontAngle,
            float frontHalfAngle) {
        if (projectile.isDestroyed || projectile.isInstantHit && !turretConfig.interceptProjectilesAllowInstantHit) {
            return false;
        }
        AnimationSet excludeTags = turretConfig.interceptProjectilesWithoutTags;
        if (excludeTags != null && projectile.animationSet != null && AnimationTag.a(projectile.animationSet, excludeTags)) {
            return false;
        }
        AnimationSet includeTags = turretConfig.interceptProjectilesWithTags;
        if (includeTags != null && (projectile.animationSet == null || !AnimationTag.a(projectile.animationSet, includeTags))) {
            return false;
        }
        if (projectile.posZ <= overHeight) {
            return false;
        }
        if (Utility.distanceSq(customUnit.posX, customUnit.posY, projectile.posX, projectile.posY) >= underDistance * underDistance) {
            return false;
        }
        if (targetingGroundDistance >= 0.0f
                && Utility.distanceSq(customUnit.posX, customUnit.posY, projectile.targetX, projectile.targetY) >= targetingGroundDistance * targetingGroundDistance) {
            return false;
        }
        // RWX：扇区过滤——只处理落在炮塔正前方半角内的抛射物，背后/侧后的不接。
        // 放在距离判定之后，常见情况（远处弹）能用更便宜的比较先排除。
        if (frontOnly) {
            float bearing = Utility.getAngleBetweenPoints(
                    customUnit.posX, customUnit.posY, projectile.posX, projectile.posY);
            if (!isWithinFrontSector(frontAngle, bearing, frontHalfAngle)) {
                return false;
            }
        }
        if (projectile.sourceUnit != null
                && (projectile.sourceUnit.team.d(customUnit.team) || projectile.sourceUnit.team == customUnit.team)) {
            return false;
        }
        if (projectile.lifeTimer <= 0.0f || a(projectile)) {
            return false;
        }
        return true;
    }

    private static int[] blockedFrames(CustomUnit unit, int turretIndex) {
        int[] arr = RWX_BLOCK_FRAMES.get(unit);
        if (arr == null || turretIndex >= arr.length) {
            arr = new int[Math.max(turretIndex + 1, unit.movementLevels.length)];
            RWX_BLOCK_FRAMES.put(unit, arr);
        }
        return arr;
    }

    /** 累计阻塞帧数；到 90 帧（约 1.5 秒）返回 true 表示该写一条诊断，并把计数清零。 */
    private static boolean shouldLogBlock(CustomUnit unit, int turretIndex, int[] blocked) {
        blocked[turretIndex]++;
        if (blocked[turretIndex] < 90) {
            return false;
        }
        blocked[turretIndex] = 0;
        return rwxLogLines < RWX_LOG_MAX;
    }

    private static String unitLabel(CustomUnit unit) {
        try {
            if (unit.r() != null) {
                return unit.r().getUnitTypeDescriptionShort() + "#" + unit.objectId;
            }
        } catch (Throwable ignored) {
        }
        return "unit#" + unit.objectId;
    }

    /** 临时诊断输出，写到 <game>/logs/rwx-intercept.log；只写前 400 行。 */
    private static void rwxLog(String line) {
        if (rwxLogLines >= RWX_LOG_MAX) {
            return;
        }
        try {
            if (rwxLogWriter == null) {
                java.io.File root = null;
                try {
                    root = io.github.rwx.LegacyAssetBridge.storageRootDir();
                } catch (Throwable ignored) {
                }
                java.io.File dir = new java.io.File(root != null ? root : new java.io.File("."), "logs");
                dir.mkdirs();
                rwxLogWriter = new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(new java.io.File(dir, "rwx-intercept.log")),
                        java.nio.charset.StandardCharsets.UTF_8);
            }
            rwxLogWriter.write(line);
            rwxLogWriter.write('\n');
            rwxLogWriter.flush();
            rwxLogLines++;
        } catch (Throwable ignored) {
        }
    }

    /** 取（必要时创建）该单位的预热累加器数组。 */
    private static float[] warmupArray(CustomUnit unit, int turretIndex) {        float[] arr = RWX_WARMUP.get(unit);
        if (arr == null || turretIndex >= arr.length) {
            arr = new float[Math.max(turretIndex + 1, unit.movementLevels.length)];
            RWX_WARMUP.put(unit, arr);
        }
        return arr;
    }

    /**
     * 预热进度回落，并把结果镜像回 {@code movementLevels[i].speed} 供引擎渲染/动画读取。
     * 原版语义：非 warmupNoReset 的炮塔在打不出去的时候按 warmupCallDownRate 掉进度。
     */
    private static float decayWarmup(CustomUnit unit, int turretIndex, UnitMovementData data,
                                     float charged, float warmup, boolean warmupNoReset, float frameDelta) {
        if (!warmupNoReset && charged != 0.0f) {
            charged = Utility.moveTowardsZero(charged, unit.f(turretIndex) * frameDelta);
        }
        if (charged < 0.0f) {
            charged = 0.0f;
        }
        data.speed = Math.min(warmup, charged);
        return charged;
    }

    private static Projectile getTargetLock(BaseUnit unit, int turretIndex) {        Projectile[] locked = RWX_TARGET_LOCK.get(unit);
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
