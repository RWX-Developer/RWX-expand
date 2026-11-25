package com.corrodinggames.rts.game.units.custom;

import com.corrodinggames.rts.game.units.custom.hooks.ProjectileInterceptorHook;
import com.corrodinggames.rts.game.units.custom.logicBooleans.LogicBoolean;
import com.corrodinggames.rts.game.units.custom.price.UnitPrice;
import com.corrodinggames.rts.gameFramework.graphics.Texture;
import com.corrodinggames.rts.gameFramework.utility.IniFile;

/* JADX INFO: renamed from: com.corrodinggames.rts.game.units.custom.bn */
/* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/custom/bn.class */
public class TurretConfig {

    /* JADX INFO: renamed from: a */
    public String name;

    /* JADX INFO: renamed from: b */
    public String copyFrom;

    /* JADX INFO: renamed from: c */
    public boolean turretMultiTargeting;

    /* JADX INFO: renamed from: d */
    public boolean canRotate;

    /* JADX INFO: renamed from: e */
    public int turretIndex;

    /* JADX INFO: renamed from: f */
    public float offsetX;

    /* JADX INFO: renamed from: g */
    public float offsetY;

    /* JADX INFO: renamed from: h */
    public float offsetHeight;

    /* JADX INFO: renamed from: j */
    public float idleDir;

    /* JADX INFO: renamed from: k */
    public float idleDirReversing;

    /* JADX INFO: renamed from: l */
    public boolean hasIdleDirReversing;

    /* JADX INFO: renamed from: t */
    public float warmupShootDelayTransfer;

    /* JADX INFO: renamed from: v */
    public UnitPrice resourceUsage;

    /* JADX INFO: renamed from: y */
    public TurretConfig parentTurret;

    /* JADX INFO: renamed from: z */
    public TurretConfig childTurret;

    /* JADX INFO: renamed from: A */
    public boolean isSlave;

    /* JADX INFO: renamed from: C */
    public LogicBoolean invisibilityCondition;

    /* JADX INFO: renamed from: D */
    public SoundList shootSound;

    /* JADX INFO: renamed from: E */
    public CustomUnitSpawnList shootFlameEffect;

    /* JADX INFO: renamed from: F */
    public CustomUnitSpawnList warmupStartEffect;

    public float preFireDuration;
    public float postFireDuration;
    public String preFireRendererId;
    public String preFireRendererVariant;
    public String postFireRendererId;
    public String postFireRendererVariant;
    public String turretFireCycleObserverId;
    public String turretFireCycleObserverVariant;

    /* JADX INFO: renamed from: G */
    public Integer shootLightColor;

    /* JADX INFO: renamed from: H */
    public boolean hasAttackRestrictions;

    /* JADX INFO: renamed from: I */
    public boolean hasRangeRestrictions;

    /* JADX INFO: renamed from: J */
    public LogicBoolean canAttackFlyingUnits;

    /* JADX INFO: renamed from: K */
    public LogicBoolean canAttackLandUnits;

    /* JADX INFO: renamed from: L */
    public LogicBoolean canAttackUnderwaterUnits;

    /* JADX INFO: renamed from: M */
    public LogicBoolean canAttackNotTouchingWaterUnits;

    /* JADX INFO: renamed from: N */
    public LogicBoolean canAttackCondition;

    /* JADX INFO: renamed from: O */
    public AnimationSet canOnlyAttackUnitsWithTags;

    /* JADX INFO: renamed from: P */
    public AnimationSet canOnlyAttackUnitsWithoutTags;

    /* JADX INFO: renamed from: T */
    public LogicBoolean altProjectileCondition;

    /* JADX INFO: renamed from: U */
    public Float turnSpeed;

    /* JADX INFO: renamed from: X */
    public float barrelY;

    /* JADX INFO: renamed from: Y */
    public float barrelX;

    /* JADX INFO: renamed from: aa */
    public float barrelHeight;

    /* JADX INFO: renamed from: ac */
    public Boolean showRangeUIGuide;

    /* JADX INFO: renamed from: ad */
    public float effectiveRange;

    /* JADX INFO: renamed from: ae */
    public float effectiveRangeSquared;

    /* JADX INFO: renamed from: af */
    public float aimOffsetSpread;

    /* JADX INFO: renamed from: aj */
    public Float laserDefenceEnergyUse;

    /* JADX INFO: renamed from: ak */
    public AnimationSet interceptProjectilesWithTags;

    // RWX：拦截炮塔改成显式开关。以前「写了 withTags 才算拦截炮塔」，现在 withTags 只当过滤器用。
    public boolean interceptProjectiles = false;

    // RWX：排除规则。带这些标签的炮弹一律不拦（优先于 withTags 的包含规则）。
    public AnimationSet interceptProjectilesWithoutTags = null;

    // RWX：拦截发射间隔（毫秒）。-1 = 沿用该炮塔自己的 delay（再叠加 warmup 影响）。
    /**
     * RWX：拦截炮塔自己的开火间隔覆盖值。null = 没写这个键，用炮塔自己的 delay；
     * 写了才覆盖（含 0，表示不额外等待）。
     */
    public Float interceptProjectilesFireDelay = null;

    // RWX：是否允许拦截瞬间命中（Projectile.isInstantHit）的炮弹，默认不拦。
    public boolean interceptProjectilesAllowInstantHit = false;

    /* JADX INFO: renamed from: ao */
    public TurretConfig linkedTurret;

    /* JADX INFO: renamed from: au */
    public float idleSweepSpeed;

    /* JADX INFO: renamed from: av */
    public LogicBoolean idleSweepCondition;

    /* JADX INFO: renamed from: ax */
    public float idleSweepAddRandomAngle;

    /* JADX INFO: renamed from: ay */
    public boolean clearTurretTargetAfterFiring;

    public int az;

    /* JADX INFO: renamed from: aA */
    public CustomUnitAnimationReference onShootPlayAnimation;

    /* JADX INFO: renamed from: aB */
    public float onShootFreezeBodyMovementFor;

    /* JADX INFO: renamed from: aC */
    public CustomUnitActionHandler onShootTriggerActions;

    /* JADX INFO: renamed from: aD */
    public Texture image;

    /* JADX INFO: renamed from: aE */
    public Texture[] teamColoredImages;

    /* JADX INFO: renamed from: aF */
    public Texture chargeEffectImage;

    /* JADX INFO: renamed from: aG */
    public float imageDrawOffsetX;

    /* JADX INFO: renamed from: aH */
    public float imageDrawOffsetY;

    /* JADX INFO: renamed from: aI */
    public int unloadUnitsAndGiveAttackOrderCount;

    public AnimationSet aJ;
    public float i = 1.0f;
    public float m = -1.0f;
    /* JADX INFO: renamed from: n */
    public float warmup = 0.0f;
    /* JADX INFO: renamed from: o */
    public float warmupCallDownRate = 4.0f;
    public float p = 0.0f;
    public float q = 4.0f;
    public float r = 7.0f;
    /* JADX INFO: renamed from: s */
    public boolean warmupNoReset = false;
    public float u = 0.0f;
    /* JADX INFO: renamed from: w */
    public int linkedTurretIndex = -1;
    public int x = -1;
    /* JADX INFO: renamed from: B */
    public boolean canShoot = true;
    /* JADX INFO: renamed from: Q */
    public float canAttackMaxAngle = 5.0f;
    /* JADX INFO: renamed from: R */
    public int projectileId = 0;
    /* JADX INFO: renamed from: S */
    public int altProjectileId = -1;
    /* JADX INFO: renamed from: V */
    public float turnSpeedAcceleration = -1.0f;
    /* JADX INFO: renamed from: W */
    public float turnSpeedDeceleration = -1.0f;
    /* JADX INFO: renamed from: Z */
    public float barrelOffsetXOnOddShots = 0.0f;
    /* JADX INFO: renamed from: ab */
    public float limitingRange = 99999.0f;
    /* JADX INFO: renamed from: ag */
    public float limitingMinRange = -1.0f;
    /* JADX INFO: renamed from: ah */
    public float limitingMinRangeSquared = -1.0f;
    /* JADX INFO: renamed from: ai */
    public float limitingAngle = -1.0f;
    /* JADX INFO: renamed from: al */
    public float interceptProjectilesAndTargetingGroundUnderDistance = -1.0f;
    /* JADX INFO: renamed from: am */
    public float interceptProjectilesAndUnderDistance = 2000.0f;
    /* JADX INFO: renamed from: an */
    public float interceptProjectilesAndOverHeight = -999.0f;
    /**
     * RWX：拦截只在炮塔正前方扇区内进行（半角，度）。
     *
     * 参考方向沿用引擎自己的射界判定语义：单位朝向 + 本炮塔 idleDir
     * （见 CustomUnit.a(TurretConfig,int,BaseUnit,boolean,boolean) 里的 limitingAngle 分支），
     * 所以「正面」会跟着车体一起转。默认 90 = 正前方半圆；
     * 写 180 或 <=0 表示不做扇区限制（恢复全向拦截）。
     */
    public float interceptProjectilesOnlyInFrontAngle = 90.0f;
    /**
     * RWX：挂了父炮塔（`attachedTo`）的拦截炮塔是否允许**独立旋转**。
     *
     * 默认 false = 引擎原版行为：父炮塔转多少，子炮塔跟着转多少。
     * 引擎 `CustomUnit.a(int,float)` 会把父炮塔的角度增量原封不动加到所有
     * `linkedTurretIndex` 指向它的子炮塔上，所以父炮塔一对地瞄准，挂在它上面的
     * 拦截炮就被拖着走，永远指不到来袭弹。
     *
     * 置 true 后：本炮塔不再吃父炮的角度增量，待机复位也回到自己的角度，
     * 只由拦截钩子按来袭弹方位驱动。父炮塔自身行为不变。
     */
    public boolean interceptProjectilesIndependentRotation = false;

    /**
     * RWX：本炮塔的角度是否跟随父炮塔。
     *
     * 非 `attachedTo` 炮塔恒为 false；`attachedTo` 且写了
     * `interceptProjectiles_independentRotation: true` 的也为 false。
     */
    public boolean followsParentTurret() {
        return linkedTurretIndex != -1 && !interceptProjectilesIndependentRotation;
    }
    public int ap = -1;
    /* JADX INFO: renamed from: aq */
    public boolean shouldResetTurret = true;
    /* JADX INFO: renamed from: ar */
    public float idleSpin = 0.0f;
    /* JADX INFO: renamed from: as */
    public float idleSweepAngle = 0.0f;
    /* JADX INFO: renamed from: at */
    public float idleSweepDelay = 10.0f;
    /* JADX INFO: renamed from: aw */
    public float idleSweepAddRandomDelay = -1.0f;

    public int a(CustomUnit customUnit) {
        if (this.altProjectileId >= 0 && this.altProjectileCondition.read(customUnit)) {
            return this.altProjectileId;
        }
        return this.projectileId;
    }

    public void a(TurretConfig turretConfig) {
        this.offsetX = turretConfig.offsetX;
        this.offsetY = turretConfig.offsetY;
        this.offsetHeight = turretConfig.offsetHeight;
        this.idleDir = turretConfig.idleDir;
        this.idleDirReversing = turretConfig.idleDirReversing;
        this.hasIdleDirReversing = turretConfig.hasIdleDirReversing;
        this.m = turretConfig.m;
        this.warmup = turretConfig.warmup;
        this.warmupCallDownRate = turretConfig.warmupCallDownRate;
        this.u = turretConfig.u;
        this.resourceUsage = turretConfig.resourceUsage;
        this.unloadUnitsAndGiveAttackOrderCount = turretConfig.unloadUnitsAndGiveAttackOrderCount;
        this.aJ = turretConfig.aJ;
        this.warmupNoReset = turretConfig.warmupNoReset;
        this.warmupShootDelayTransfer = turretConfig.warmupShootDelayTransfer;
        this.p = turretConfig.p;
        this.q = turretConfig.q;
        this.r = turretConfig.r;
        this.parentTurret = turretConfig.parentTurret;
        this.isSlave = turretConfig.isSlave;
        this.canShoot = turretConfig.canShoot;
        this.invisibilityCondition = turretConfig.invisibilityCondition;
        this.shootSound = turretConfig.shootSound;
        this.shootFlameEffect = turretConfig.shootFlameEffect;
        this.shootLightColor = turretConfig.shootLightColor;
        this.warmupStartEffect = turretConfig.warmupStartEffect;
        this.preFireDuration = turretConfig.preFireDuration;
        this.postFireDuration = turretConfig.postFireDuration;
        this.preFireRendererId = turretConfig.preFireRendererId;
        this.preFireRendererVariant = turretConfig.preFireRendererVariant;
        this.postFireRendererId = turretConfig.postFireRendererId;
        this.postFireRendererVariant = turretConfig.postFireRendererVariant;
        this.turretFireCycleObserverId = turretConfig.turretFireCycleObserverId;
        this.turretFireCycleObserverVariant = turretConfig.turretFireCycleObserverVariant;
        this.canAttackFlyingUnits = turretConfig.canAttackFlyingUnits;
        this.canAttackLandUnits = turretConfig.canAttackLandUnits;
        this.canAttackUnderwaterUnits = turretConfig.canAttackUnderwaterUnits;
        this.canAttackNotTouchingWaterUnits = turretConfig.canAttackNotTouchingWaterUnits;
        this.canAttackCondition = turretConfig.canAttackCondition;
        this.canOnlyAttackUnitsWithTags = turretConfig.canOnlyAttackUnitsWithTags;
        this.canOnlyAttackUnitsWithoutTags = turretConfig.canOnlyAttackUnitsWithoutTags;
        this.canAttackMaxAngle = turretConfig.canAttackMaxAngle;
        this.image = turretConfig.image;
        this.teamColoredImages = turretConfig.teamColoredImages;
        this.chargeEffectImage = turretConfig.chargeEffectImage;
        this.projectileId = turretConfig.projectileId;
        this.altProjectileId = turretConfig.altProjectileId;
        this.altProjectileCondition = turretConfig.altProjectileCondition;
        this.turnSpeed = turretConfig.turnSpeed;
        this.turnSpeedAcceleration = turretConfig.turnSpeedAcceleration;
        this.turnSpeedDeceleration = turretConfig.turnSpeedDeceleration;
        this.barrelY = turretConfig.barrelY;
        this.barrelX = turretConfig.barrelX;
        this.barrelOffsetXOnOddShots = turretConfig.barrelOffsetXOnOddShots;
        this.barrelHeight = turretConfig.barrelHeight;
        this.linkedTurret = turretConfig.linkedTurret;
        this.shouldResetTurret = turretConfig.shouldResetTurret;
        this.idleSweepAngle = turretConfig.idleSweepAngle;
        this.idleSweepDelay = turretConfig.idleSweepDelay;
        this.idleSweepSpeed = turretConfig.idleSweepSpeed;
        this.idleSweepCondition = turretConfig.idleSweepCondition;
        this.idleSweepAddRandomDelay = turretConfig.idleSweepAddRandomDelay;
        this.idleSweepAddRandomAngle = turretConfig.idleSweepAddRandomAngle;
        this.limitingRange = turretConfig.limitingRange;
        this.limitingMinRange = turretConfig.limitingMinRange;
        this.limitingAngle = turretConfig.limitingAngle;
        this.aimOffsetSpread = turretConfig.aimOffsetSpread;
        this.clearTurretTargetAfterFiring = turretConfig.clearTurretTargetAfterFiring;
        this.az = turretConfig.az;
        this.interceptProjectiles = turretConfig.interceptProjectiles;
        this.interceptProjectilesWithTags = turretConfig.interceptProjectilesWithTags;
        this.interceptProjectilesWithoutTags = turretConfig.interceptProjectilesWithoutTags;
        this.interceptProjectilesFireDelay = turretConfig.interceptProjectilesFireDelay;
        this.interceptProjectilesAllowInstantHit = turretConfig.interceptProjectilesAllowInstantHit;
        this.interceptProjectilesAndTargetingGroundUnderDistance = turretConfig.interceptProjectilesAndTargetingGroundUnderDistance;
        this.interceptProjectilesAndUnderDistance = turretConfig.interceptProjectilesAndUnderDistance;
        this.interceptProjectilesAndOverHeight = turretConfig.interceptProjectilesAndOverHeight;
        this.interceptProjectilesOnlyInFrontAngle = turretConfig.interceptProjectilesOnlyInFrontAngle;
        this.interceptProjectilesIndependentRotation = turretConfig.interceptProjectilesIndependentRotation;
        this.onShootPlayAnimation = turretConfig.onShootPlayAnimation;
        this.imageDrawOffsetX = turretConfig.imageDrawOffsetX;
        this.imageDrawOffsetY = turretConfig.imageDrawOffsetY;
    }

    public static void a(TurretConfig turretConfig, CustomUnitConfig customUnitConfig, IniFile iniFile, String str) throws ConfigParseException {
        turretConfig.turretMultiTargeting = true;
        if (turretConfig.canRotate) {
            return;
        }
        Float fValueOf = Float.valueOf(iniFile.getFloatStrictRaw(str, "x"));
        Float fValueOf2 = Float.valueOf(iniFile.getFloatStrictRaw(str, "y"));
        String string = iniFile.getString(str, "copyFrom", (String) null);
        if (string != null) {
            TurretConfig turretConfigFindProjectileConfigByName = customUnitConfig.findProjectileConfigByName(string);
            if (turretConfigFindProjectileConfigByName == null) {
                throw new RuntimeException("[" + str + "] Could not find copy turret target with name:" + string);
            }
            if (turretConfigFindProjectileConfigByName.turretMultiTargeting && !turretConfigFindProjectileConfigByName.canRotate) {
                throw new RuntimeException("[" + str + "] Infinite loop detected with turret copies:" + string);
            }
            if (!turretConfigFindProjectileConfigByName.canRotate) {
                a(turretConfigFindProjectileConfigByName, customUnitConfig, iniFile, turretConfigFindProjectileConfigByName.copyFrom);
            }
            turretConfig.a(turretConfigFindProjectileConfigByName);
        } else {
            turretConfig.aimOffsetSpread = customUnitConfig.aimOffsetSpread;
            turretConfig.barrelY = customUnitConfig.turretSize;
        }
        turretConfig.offsetX = fValueOf.floatValue();
        turretConfig.offsetY = fValueOf2.floatValue();
        turretConfig.offsetHeight = iniFile.getFloat(str, "height", Float.valueOf(0.0f)).floatValue();
        turretConfig.i = iniFile.getFloat(str, "yAxisScaling", Float.valueOf(1.0f)).floatValue();
        String string2 = iniFile.getString(str, "linkDelayWithTurret", (String) null);
        if (string2 != null) {
            turretConfig.linkedTurret = customUnitConfig.findProjectileConfigByName(string2);
            if (turretConfig.linkedTurret == null) {
                throw new RuntimeException("[" + str + "] Could not find 'linkDelayWithTurret' turret target with name:" + string2);
            }
            turretConfig.m = 9000.0f;
        }
        Float time = iniFile.getTime(str, "delay", (Float) null);
        if (time != null) {
            turretConfig.m = time.floatValue();
        }
        if (turretConfig.m == -1.0f) {
            turretConfig.m = customUnitConfig.shootDelay;
        }
        Float time2 = iniFile.getTime(str, "warmup", (Float) null);
        if (time2 != null) {
            turretConfig.warmup = time2.floatValue();
        }
        Float f = iniFile.getFloat(str, "warmupCallDownRate", (Float) null);
        if (f != null) {
            turretConfig.warmupCallDownRate = f.floatValue();
        }
        Boolean bool = iniFile.getBoolean(str, "warmupNoReset", (Boolean) null);
        if (bool != null) {
            turretConfig.warmupNoReset = bool.booleanValue();
        }
        Float f2 = iniFile.getFloat(str, "warmupShootDelayTransfer", (Float) null);
        if (f2 != null) {
            turretConfig.warmupShootDelayTransfer = f2.floatValue();
        }
        turretConfig.p = iniFile.getFloat(str, "recoilOffset", Float.valueOf(turretConfig.p)).floatValue();
        turretConfig.q = iniFile.getFloat(str, "recoilOutTime", Float.valueOf(turretConfig.q)).floatValue();
        turretConfig.r = iniFile.getTime(str, "recoilReturnTime", Float.valueOf(turretConfig.r)).floatValue();
        Float f3 = iniFile.getFloat(str, "energyUsage", (Float) null);
        if (f3 != null) {
            turretConfig.u = f3.floatValue();
        }
        turretConfig.unloadUnitsAndGiveAttackOrderCount = iniFile.getInt(str, "unloadUpToXUnitsAndGiveAttackOrder", Integer.valueOf(turretConfig.unloadUnitsAndGiveAttackOrderCount)).intValue();
        UnitPrice unitPriceA = UnitPrice.a(customUnitConfig, iniFile, str, "resourceUsage", true);
        if (unitPriceA != null && unitPriceA.d()) {
            turretConfig.resourceUsage = unitPriceA;
            customUnitConfig.a(unitPriceA);
        }
        String string3 = iniFile.getString(str, "attachedTo", (String) null);
        if (string3 != null) {
            turretConfig.parentTurret = customUnitConfig.findProjectileConfigByName(string3);
            if (turretConfig.parentTurret == null) {
                throw new RuntimeException("[" + str + "] Could not find attachedTo turret target:" + string3);
            }
            if (turretConfig.parentTurret == turretConfig) {
                throw new RuntimeException("Turret cannot be attachedTo self");
            }
            customUnitConfig.hasAttachedTurrets = true;
        }
        Float f4 = iniFile.getFloat(str, "idleDir", (Float) null);
        if (f4 != null) {
            turretConfig.idleDir = f4.floatValue();
        }
        Float f5 = iniFile.getFloat(str, "idleDirReversing", (Float) null);
        if (f5 != null) {
            turretConfig.idleDirReversing = f5.floatValue();
            turretConfig.hasIdleDirReversing = true;
        } else if (!turretConfig.hasIdleDirReversing) {
            if (turretConfig.parentTurret != null) {
                turretConfig.idleDirReversing = 0.0f;
            } else {
                turretConfig.idleDirReversing = turretConfig.idleDir + 180.0f;
            }
        }
        Boolean bool2 = iniFile.getBoolean(str, "canShoot", (Boolean) null);
        Boolean bool3 = iniFile.getBoolean(str, "canAttack", (Boolean) null);
        if (bool2 != null && bool3 != null) {
            throw new RuntimeException("[" + str + "] Cannot use canShoot and canAttack at the same time, they have the same meaning");
        }
        if (bool2 != null) {
            turretConfig.canShoot = bool2.booleanValue();
        } else if (bool3 != null) {
            turretConfig.canShoot = bool3.booleanValue();
        }
        turretConfig.shootSound = SoundList.a(customUnitConfig, iniFile.getString(str, "shoot_sound", (String) null), turretConfig.shootSound);
        Float f6 = iniFile.getFloat(str, "shoot_sound_vol", (Float) null);
        if (f6 != null) {
            turretConfig.shootSound.a(f6.floatValue());
        }
        turretConfig.shootFlameEffect = customUnitConfig.addConfigExtension(iniFile.getString(str, "shoot_flame", (String) null), turretConfig.shootFlameEffect);
        turretConfig.shootLightColor = iniFile.getColorAsInt(str, "shoot_light", turretConfig.shootLightColor);
        turretConfig.warmupStartEffect = customUnitConfig.addConfigExtension(iniFile.getString(str, "warmupStartEffect", (String) null), turretConfig.warmupStartEffect);
        turretConfig.isSlave = iniFile.getBoolean(str, "slave", Boolean.valueOf(turretConfig.isSlave)).booleanValue();
        if (turretConfig.isSlave) {
            if (turretConfig.parentTurret == null) {
                throw new RuntimeException("Turret cannot be a slave without being 'attachedTo' to another turret");
            }
            turretConfig.childTurret = turretConfig.parentTurret;
        }
        turretConfig.invisibilityCondition = iniFile.getLogicBoolean(customUnitConfig, str, "invisible", turretConfig.invisibilityCondition);
        turretConfig.canAttackFlyingUnits = iniFile.getLogicBoolean(customUnitConfig, str, "canAttackFlyingUnits", turretConfig.canAttackFlyingUnits);
        turretConfig.canAttackLandUnits = iniFile.getLogicBoolean(customUnitConfig, str, "canAttackLandUnits", turretConfig.canAttackLandUnits);
        turretConfig.canAttackUnderwaterUnits = iniFile.getLogicBoolean(customUnitConfig, str, "canAttackUnderwaterUnits", turretConfig.canAttackUnderwaterUnits);
        turretConfig.canAttackNotTouchingWaterUnits = iniFile.getLogicBoolean(customUnitConfig, str, "canAttackNotTouchingWaterUnits", turretConfig.canAttackNotTouchingWaterUnits);
        turretConfig.canAttackCondition = iniFile.getLogicBoolean(customUnitConfig, str, "canAttackCondition", turretConfig.canAttackCondition);
        turretConfig.canOnlyAttackUnitsWithTags = iniFile.getAnimationSet(customUnitConfig, str, "canOnlyAttackUnitsWithTags", turretConfig.canOnlyAttackUnitsWithTags);
        turretConfig.canOnlyAttackUnitsWithoutTags = iniFile.getAnimationSet(customUnitConfig, str, "canOnlyAttackUnitsWithoutTags", turretConfig.canOnlyAttackUnitsWithoutTags);
        String string4 = iniFile.getString(str, "projectile", (String) null);
        if (string4 != null) {
            CustomProjectileTemplate customProjectileTemplateFindProjectileTemplateByName = customUnitConfig.findProjectileTemplateByName(string4);
            if (customProjectileTemplateFindProjectileTemplateByName == null) {
                if ("0".equals(string4) && customUnitConfig.projectileTemplates.size() == 1 && customUnitConfig.findProjectileTemplateByName("1") != null) {
                    turretConfig.projectileId = 0;
                } else {
                    throw new RuntimeException("[" + str + "] Could not find projectile with name:" + string4);
                }
            } else {
                turretConfig.projectileId = customProjectileTemplateFindProjectileTemplateByName.projectileId;
            }
        }
        String string5 = iniFile.getString(str, "altProjectile", (String) null);
        if (string5 != null) {
            CustomProjectileTemplate customProjectileTemplateFindProjectileTemplateByName2 = customUnitConfig.findProjectileTemplateByName(string5);
            if (customProjectileTemplateFindProjectileTemplateByName2 == null) {
                throw new RuntimeException("[" + str + "]altProjectile: Could not find projectile with name:" + string5);
            }
            turretConfig.altProjectileId = customProjectileTemplateFindProjectileTemplateByName2.projectileId;
        }
        turretConfig.altProjectileCondition = iniFile.getLogicBoolean(customUnitConfig, str, "altProjectileCondition", turretConfig.altProjectileCondition);
        if (turretConfig.altProjectileId >= 0 && turretConfig.altProjectileCondition == null) {
            throw new RuntimeException("[" + str + "]altProjectileCondition is required with altProjectile");
        }
        turretConfig.canAttackMaxAngle = iniFile.getFloat(str, "canAttackMaxAngle", Float.valueOf(turretConfig.canAttackMaxAngle)).floatValue();
        turretConfig.turnSpeed = iniFile.getFloat(str, "turnSpeed", turretConfig.turnSpeed);
        turretConfig.turnSpeedAcceleration = iniFile.getFloat(str, "turnSpeedAcceleration", Float.valueOf(turretConfig.turnSpeedAcceleration)).floatValue();
        turretConfig.turnSpeedDeceleration = iniFile.getFloat(str, "turnSpeedDeceleration", Float.valueOf(turretConfig.turnSpeedDeceleration)).floatValue();
        Float f7 = iniFile.getFloat(str, "barrelY", (Float) null);
        Float f8 = iniFile.getFloat(str, "size", (Float) null);
        if (f7 != null && f8 != null) {
            throw new RuntimeException("Turret [" + str + "]: barrelY and size can not both be used at the same time as they have the same meaning");
        }
        if (f7 != null) {
            turretConfig.barrelY = f7.floatValue();
        }
        if (f8 != null) {
            turretConfig.barrelY = f8.floatValue();
        }
        turretConfig.barrelX = iniFile.getFloat(str, "barrelX", Float.valueOf(turretConfig.barrelX)).floatValue();
        turretConfig.barrelOffsetXOnOddShots = iniFile.getFloat(str, "barrelOffsetX_onOddShots", Float.valueOf(turretConfig.barrelOffsetXOnOddShots)).floatValue();
        turretConfig.barrelHeight = iniFile.getFloat(str, "barrelHeight", Float.valueOf(turretConfig.barrelHeight)).floatValue();
        turretConfig.limitingRange = iniFile.getFloat(str, "limitingRange", Float.valueOf(turretConfig.limitingRange)).floatValue();
        turretConfig.limitingAngle = iniFile.getFloat(str, "limitingAngle", Float.valueOf(turretConfig.limitingAngle)).floatValue();
        turretConfig.limitingMinRange = iniFile.getFloat(str, "limitingMinRange", Float.valueOf(turretConfig.limitingMinRange)).floatValue();
        turretConfig.aimOffsetSpread = iniFile.getFloat(str, "aimOffsetSpread", Float.valueOf(turretConfig.aimOffsetSpread)).floatValue();
        if (turretConfig.limitingAngle >= 0.0f) {
            customUnitConfig.hasTurretWithLimitingAngle = true;
        }
        if (turretConfig.limitingRange < 99999.0f) {
            turretConfig.effectiveRange = turretConfig.limitingRange;
        } else {
            turretConfig.effectiveRange = customUnitConfig.unitStats.maxAttackRange;
        }
        turretConfig.effectiveRangeSquared = turretConfig.effectiveRange * turretConfig.effectiveRange;
        if (turretConfig.limitingMinRange > 0.0f) {
            turretConfig.limitingMinRangeSquared = turretConfig.limitingMinRange * turretConfig.limitingMinRange;
        } else {
            turretConfig.limitingMinRangeSquared = -1.0f;
        }
        turretConfig.showRangeUIGuide = iniFile.getBoolean(str, "showRangeUIGuide", (Boolean) null);
        turretConfig.laserDefenceEnergyUse = iniFile.getFloat(str, "laserDefenceEnergyUse", turretConfig.laserDefenceEnergyUse);
        if (turretConfig.laserDefenceEnergyUse != null) {
            customUnitConfig.hasLaserDefenceTurret = true;
            customUnitConfig.a(ProjectileInterceptorHook.a);
        }
        // RWX：拦截炮塔由 interceptProjectiles 显式开启；withTags 退化成「只拦这些标签」的过滤器。
        // 三态：没写这个键 = null，留给下面按旧规则推断（兼容老模组）
        Boolean explicitIntercept = iniFile.getBoolean(str, "interceptProjectiles", (Boolean) null);
        if (explicitIntercept != null) {
            turretConfig.interceptProjectiles = explicitIntercept.booleanValue();
        }
        turretConfig.interceptProjectilesWithTags = AnimationTag.a(iniFile.getString(str, "interceptProjectiles_withTags", (String) null), turretConfig.interceptProjectilesWithTags);
        turretConfig.interceptProjectilesWithoutTags = AnimationTag.a(iniFile.getString(str, "interceptProjectiles_withoutTags", (String) null), turretConfig.interceptProjectilesWithoutTags);
        turretConfig.interceptProjectilesAllowInstantHit = iniFile.getBoolean(str, "interceptProjectiles_allowInstantHit", Boolean.valueOf(turretConfig.interceptProjectilesAllowInstantHit)).booleanValue();
        turretConfig.interceptProjectilesFireDelay = iniFile.getTime(str, "interceptProjectiles_fireDelay", (Float) null);
        // 兼容：老模组只写 withTags/withoutTags、没写 interceptProjectiles 的，仍然算拦截炮塔。
        if (explicitIntercept == null && (turretConfig.interceptProjectilesWithTags != null || turretConfig.interceptProjectilesWithoutTags != null)) {
            turretConfig.interceptProjectiles = true;
        }
        if (turretConfig.interceptProjectiles || turretConfig.interceptProjectilesWithTags != null || turretConfig.interceptProjectilesWithoutTags != null) {
            customUnitConfig.hasProjectileInterceptorTurret = true;
            customUnitConfig.a(ProjectileInterceptorHook.a);
        }
        // 这三个键必须无条件解析：旧模组可能只写了它们，条件解析会让 luke 报「键未使用」直接加载失败。
        turretConfig.interceptProjectilesAndTargetingGroundUnderDistance = iniFile.getFloat(str, "interceptProjectiles_andTargetingGroundUnderDistance", Float.valueOf(turretConfig.interceptProjectilesAndTargetingGroundUnderDistance)).floatValue();
        turretConfig.interceptProjectilesAndUnderDistance = iniFile.getFloat(str, "interceptProjectiles_andUnderDistance", Float.valueOf(turretConfig.interceptProjectilesAndUnderDistance)).floatValue();
        turretConfig.interceptProjectilesAndOverHeight = iniFile.getFloat(str, "interceptProjectiles_andOverHeight", Float.valueOf(turretConfig.interceptProjectilesAndOverHeight)).floatValue();
        // RWX：拦截扇区半角。不写默认 90（正前方半圆）；写 180 或负数 = 不限方向，全向拦截。
        turretConfig.interceptProjectilesOnlyInFrontAngle = iniFile.getFloat(str, "interceptProjectiles_onlyInFrontAngle", Float.valueOf(turretConfig.interceptProjectilesOnlyInFrontAngle)).floatValue();
        // RWX：父炮塔挂在身上的拦截炮是否允许独立旋转（默认 false，跟随父炮）。
        turretConfig.interceptProjectilesIndependentRotation = iniFile.getBoolean(str, "interceptProjectiles_independentRotation", Boolean.valueOf(turretConfig.interceptProjectilesIndependentRotation)).booleanValue();
        // RWX：拦截炮塔必须保留转向结果，否则引擎每帧把炮管拉回 idleDir，
        // 拦截钩子的持续瞄准会被抵消，表现为「炮塔不转或只转一点」。
        // 没显式写 shouldResetTurret 的拦截炮默认 false；模组作者显式写的仍尊重。
        Boolean explicitShouldReset = iniFile.getBoolean(str, "shouldResetTurret", (Boolean) null);
        turretConfig.shouldResetTurret = explicitShouldReset != null
                ? explicitShouldReset.booleanValue()
                : !turretConfig.interceptProjectiles;
        turretConfig.idleSpin = iniFile.getFloat(str, "idleSpin", Float.valueOf(turretConfig.idleSpin)).floatValue();
        turretConfig.idleSweepAngle = iniFile.getFloat(str, "idleSweepAngle", Float.valueOf(turretConfig.idleSweepAngle)).floatValue();
        turretConfig.idleSweepDelay = iniFile.getFloat(str, "idleSweepDelay", Float.valueOf(turretConfig.idleSweepDelay)).floatValue();
        turretConfig.idleSweepSpeed = iniFile.getFloat(str, "idleSweepSpeed", Float.valueOf(turretConfig.idleSweepSpeed)).floatValue();
        turretConfig.idleSweepCondition = iniFile.getLogicBoolean(customUnitConfig, str, "idleSweepCondition", turretConfig.idleSweepCondition);
        turretConfig.idleSweepCondition = LogicBoolean.convertAlwaysTrueToNull(turretConfig.idleSweepCondition);
        turretConfig.idleSweepAddRandomDelay = iniFile.getFloat(str, "idleSweepAddRandomDelay", Float.valueOf(turretConfig.idleSweepAddRandomDelay)).floatValue();
        if (turretConfig.idleSweepAddRandomDelay < 0.0f) {
            float f9 = 1.0f;
            if (turretConfig.idleSweepDelay > 200.0f) {
                f9 = 20.0f;
            } else if (turretConfig.idleSweepDelay > 50.0f) {
                f9 = 5.0f;
            }
            turretConfig.idleSweepAddRandomDelay = f9;
        }
        turretConfig.idleSweepAddRandomAngle = iniFile.getFloat(str, "idleSweepAddRandomAngle", Float.valueOf(turretConfig.idleSweepAddRandomAngle)).floatValue();
        if (turretConfig.idleSweepAddRandomAngle < 0.0f) {
            throw new RuntimeException("Turret [" + str + "]: idleSweepAddRandomAngle must be >= 0");
        }
        if (turretConfig.idleSweepAngle < 0.0f) {
            throw new RuntimeException("Turret [" + str + "]: idleSweepAngle must be >= 0");
        }
        turretConfig.clearTurretTargetAfterFiring = iniFile.getBoolean(str, "clearTurretTargetAfterFiring", Boolean.valueOf(turretConfig.clearTurretTargetAfterFiring)).booleanValue();
        turretConfig.onShootPlayAnimation = customUnitConfig.loadCore(iniFile.getString(str, "onShoot_playAnimation", (String) null), turretConfig.onShootPlayAnimation);
        turretConfig.onShootFreezeBodyMovementFor = iniFile.getTime(str, "onShoot_freezeBodyMovementFor", Float.valueOf(turretConfig.onShootFreezeBodyMovementFor)).floatValue();
        turretConfig.onShootTriggerActions = iniFile.getCustomUnitAction(customUnitConfig, str, "onShoot_triggerActions", turretConfig.onShootTriggerActions);
        if (iniFile.getBoolean(str, "isMainNanoTurret", (Boolean) false).booleanValue()) {
            customUnitConfig.mainNanoTurret = turretConfig;
        }
        Texture textureA = customUnitConfig.a(iniFile, str, "image");
        if (textureA != null) {
            turretConfig.image = textureA;
            boolean zBooleanValue = customUnitConfig.teamColorsOnTurret;
            Boolean bool4 = iniFile.getBoolean(str, "image_applyTeamColors", (Boolean) null);
            if (bool4 != null) {
                zBooleanValue = bool4.booleanValue();
            }
            if (zBooleanValue) {
                turretConfig.teamColoredImages = customUnitConfig.a(turretConfig.image, customUnitConfig.teamColoringMode);
            } else {
                turretConfig.teamColoredImages = null;
            }
        }
        turretConfig.imageDrawOffsetX = iniFile.getFloat(str, "image_drawOffsetX", Float.valueOf(turretConfig.imageDrawOffsetX)).floatValue();
        turretConfig.imageDrawOffsetY = iniFile.getFloat(str, "image_drawOffsetY", Float.valueOf(turretConfig.imageDrawOffsetY)).floatValue();
        Texture textureA2 = customUnitConfig.a(iniFile, str, "chargeEffectImage");
        if (textureA2 != null) {
            turretConfig.chargeEffectImage = textureA2;
            customUnitConfig.hasTurretChargeEffectImage = true;
        }
        if (customUnitConfig.projectileTemplatesById[turretConfig.projectileId] == null) {
            throw new RuntimeException("Turret [" + str + "]: cannot find linked projectile:" + turretConfig.projectileId);
        }
        if (turretConfig.altProjectileId >= 0 && customUnitConfig.projectileTemplatesById[turretConfig.altProjectileId] == null) {
            throw new RuntimeException("Turret [" + str + "]altProjectile: cannot find linked projectile");
        }
        turretConfig.canAttackFlyingUnits = LogicBoolean.convertAlwaysTrueToNull(turretConfig.canAttackFlyingUnits);
        turretConfig.canAttackLandUnits = LogicBoolean.convertAlwaysTrueToNull(turretConfig.canAttackLandUnits);
        turretConfig.canAttackUnderwaterUnits = LogicBoolean.convertAlwaysTrueToNull(turretConfig.canAttackUnderwaterUnits);
        turretConfig.canAttackNotTouchingWaterUnits = LogicBoolean.convertAlwaysTrueToNull(turretConfig.canAttackNotTouchingWaterUnits);
        turretConfig.canAttackCondition = LogicBoolean.convertAlwaysTrueToNull(turretConfig.canAttackCondition);
        if (turretConfig.limitingAngle != -1.0f || turretConfig.canAttackFlyingUnits != null || turretConfig.canAttackLandUnits != null || turretConfig.canAttackUnderwaterUnits != null || turretConfig.canAttackNotTouchingWaterUnits != null || turretConfig.canAttackCondition != null) {
            turretConfig.hasAttackRestrictions = true;
        }
        if (turretConfig.limitingRange < 99999.0f || turretConfig.limitingMinRange > 0.0f) {
            turretConfig.hasAttackRestrictions = true;
            turretConfig.hasRangeRestrictions = true;
        }
        if (turretConfig.canOnlyAttackUnitsWithTags != null || turretConfig.canOnlyAttackUnitsWithoutTags != null) {
            turretConfig.hasAttackRestrictions = true;
        }
        turretConfig.canRotate = true;
    }
}
