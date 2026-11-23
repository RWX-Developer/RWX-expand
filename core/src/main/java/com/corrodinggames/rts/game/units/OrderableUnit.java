package com.corrodinggames.rts.game.units;

import com.corrodinggames.rts.game.PlayerTeam;
import com.corrodinggames.rts.game.ScorchMark;
import com.corrodinggames.rts.game.ai.AIUnitGroupBase;
import com.corrodinggames.rts.game.ai.BaseZone;
import com.corrodinggames.rts.game.map.MapTile;
import com.corrodinggames.rts.game.map.TileMap;
import com.corrodinggames.rts.game.units.actions.AbstractUnitAction;
import com.corrodinggames.rts.game.units.actions.FilteredUnitAction;
import com.corrodinggames.rts.game.units.buildings.BaseBuilding;
import com.corrodinggames.rts.game.units.buildings.RepairBay;
import com.corrodinggames.rts.game.units.custom.AnimationSet;
import com.corrodinggames.rts.game.units.custom.CustomUnitConfigParser;
import com.corrodinggames.rts.game.units.custom.PlacementRules;
import com.corrodinggames.rts.game.units.custom.UnitEventType;
import com.corrodinggames.rts.game.units.custom.hooks.AttachmentSlotDefinition;
import com.corrodinggames.rts.game.units.custom.price.UnitPrice;
import com.corrodinggames.rts.game.units.g.AirUnitEffectManager;
import com.corrodinggames.rts.game.units.spatial.UnitSpatialCallback;
import com.corrodinggames.rts.gameFramework.*;
import com.corrodinggames.rts.gameFramework.audio.SoundEngine;
import com.corrodinggames.rts.gameFramework.effects.*;
import com.corrodinggames.rts.gameFramework.graphics.GamePaint;
import com.corrodinggames.rts.gameFramework.graphics.GraphicsEngine;
import com.corrodinggames.rts.gameFramework.graphics.Texture;
import com.corrodinggames.rts.gameFramework.network.GameInputStream;
import com.corrodinggames.rts.gameFramework.network.GameOutputStream;
import com.corrodinggames.rts.gameFramework.path.PathEngine;
import com.corrodinggames.rts.gameFramework.pathfinding.Path;
import com.corrodinggames.rts.gameFramework.pathfinding.PathPoint;
import com.corrodinggames.rts.gameFramework.pathfinding.PathPositionProvider;
import com.corrodinggames.rts.gameFramework.utility.*;
import io.github.rwx.geometry.Point;
import io.github.rwx.geometry.PointF;
import io.github.rwx.geometry.Rect;
import io.github.rwx.geometry.RectF;
import io.github.rwx.render.canvas.*;
import io.github.rwx.render.canvas.CanvasBlendMode;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

/* JADX INFO: renamed from: com.corrodinggames.rts.game.units.y */
/* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/units/y.class */
public abstract class OrderableUnit extends UnitBase {
    // RWX：越界寻路目标取证（只打前 20 条）
    private static int RWX_TARGET_CLAMP_N = 0;
    private static int RWX_EDGE_PATH_N = 0;
    // RWX：边界航点直接丢弃（不去走过去）时的计数与目标记忆，防止同一条目标反复丢弃。
    private int rwxBorderDropCount = 0;
    private float rwxBorderDropLastX = -1.0f;
    private float rwxBorderDropLastY = -1.0f;
    // RWX：路径全量跟踪（-Drwx.pathTrace=1），每条路径打一行航点清单。
    static final boolean RWX_PATH_TRACE = "1".equals(System.getProperty("rwx.pathTrace", "0").trim());
    private static int RWX_PATH_TRACE_N = 0;

    /* JADX INFO: renamed from: M */
    protected Texture baseTexture;

    /* JADX INFO: renamed from: N */
    protected Texture shadowTexture;

    /* JADX INFO: renamed from: a */
    private int blockedRecoveryMisses;

    /* JADX INFO: renamed from: b */
    private float blockedRecoveryTime;

    /* JADX INFO: renamed from: c */
    private float moveThrottle;

    /* JADX INFO: renamed from: d */
    private float transportRecoveryTime;

    /* JADX INFO: renamed from: e */
    private float transportRecoveryDelay;

    /* JADX INFO: renamed from: f */
    private int waypointCount;

    /* JADX INFO: renamed from: g */
    private UnitCommand[] waypoints;

    /* JADX INFO: renamed from: P */
    public AttackMode attackMode;

    /* JADX INFO: renamed from: W */
    public float pathRetryTimer;

    /* JADX INFO: renamed from: R */
    public BaseUnit attackTarget;

    /* JADX INFO: renamed from: S */
    public float turretRotation;

    /* JADX INFO: renamed from: T */
    public float targetTurretRotation;

    /* JADX INFO: renamed from: U */
    public float turretTurnSpeed;
    /* JADX INFO: renamed from: Q */
    int lastAttackTick;

    /* JADX INFO: renamed from: i */
    private int lastReclaimSearchTick;

    /* JADX INFO: renamed from: V */
    public float wayPointTimer;
    /* JADX INFO: renamed from: h */
    private boolean isActing;

    /* JADX INFO: renamed from: X */
    public float previousPathRetryTimer;

    /* JADX INFO: renamed from: Y */
    public float waypointDwellTimer;

    /* JADX INFO: renamed from: j */
    private boolean canAttack;

    /* JADX INFO: renamed from: k */
    private boolean isPathingActive;

    /* JADX INFO: renamed from: l */
    private float pathTargetX;

    /* JADX INFO: renamed from: m */
    private float pathTargetY;

    /* JADX INFO: renamed from: n */
    private int pathTargetRadius;

    /* JADX INFO: renamed from: o */
    private float lastPathTargetX;

    /* JADX INFO: renamed from: p */
    private float lastPathTargetY;

    /* JADX INFO: renamed from: q */
    private byte pathRetryCount;

    /* JADX INFO: renamed from: r */
    private int repathDistanceThreshold;
    /* JADX INFO: renamed from: s */
    private float repathCooldown;

    /* JADX INFO: renamed from: t */
    private boolean isLowPriority;

    /* JADX INFO: renamed from: Z */
    public BaseUnit lastDamagedBy;

    /* JADX INFO: renamed from: aa */
    public int lastCollisionTick;
    /* JADX INFO: renamed from: ab */
    public float pendingCredits;

    /* JADX INFO: renamed from: ac */
    public int waypointSyncGroupId;

    /* JADX INFO: renamed from: ad */
    public OrderableUnit transportedBy;
    /* JADX INFO: renamed from: ae */
    public boolean isTransportAttached;
    /* JADX INFO: renamed from: af */
    public boolean shouldMaintainFormation;

    /* JADX INFO: renamed from: ag */
    public int transportedUnitCount;
    /* JADX INFO: renamed from: ah */
    public short formationSlotIndex;

    /* JADX INFO: renamed from: ai */
    public float transportAttackAssistTimer;
    /* JADX INFO: renamed from: aj */
    public boolean inFormation;

    /* JADX INFO: renamed from: ak */
    public float transportOffsetX;

    /* JADX INFO: renamed from: al */
    public float transportOffsetY;
    /* JADX INFO: renamed from: am */
    public float formationSlotAngle;

    /* JADX INFO: renamed from: an */
    public int lastTransportPathUpdateTick;
    /* JADX INFO: renamed from: ao */
    public float formationSlotDistanceSq;
    /* JADX INFO: renamed from: ap */
    public boolean isFormationMember;
    /* JADX INFO: renamed from: aq */
    public float navigationAngle;
    /* JADX INFO: renamed from: ar */
    public boolean isSecondaryRecharging;
    /* JADX INFO: renamed from: as */
    public boolean isTargetSearchPending;
    /* JADX INFO: renamed from: au */
    public PathPositionProvider pathPositionProvider;

    /* JADX INFO: renamed from: av */
    protected PositionData[] pathPositions;

    /* JADX INFO: renamed from: aw */
    protected int activePathCount;

    /* JADX INFO: renamed from: u */
    public boolean isPathIncomplete;

    // RWX：RWX_PATH_STATS=1 时统计「确认不可达后取消路径点」次数，默认零开销
    private static final boolean RW_PATH_STATS = System.getenv("RWX_PATH_STATS") != null;
    private static int rwUnreachableCancels = 0;
    // RWX 路径点停滞自愈：有移动意图却长时间没挪窝（无路可走 / 只拿到部分路径 / 被单位堵住），
    // 就作废这条路径点以及后面指向同一片区域的移动行动，让单位与 AI 重新决策。
    // 阈值可用环境变量 RWX_STUCK_LIMIT 覆盖，默认 600（与 repathCooldown 700 同量级）。
    private static final float RWX_STUCK_LIMIT = rwxReadStuckLimit();
    public float rwxStuckTimer = 0.0f;
    private float rwxStuckX;
    private float rwxStuckY;
    private boolean rwxStuckSampled = false;

    private static float rwxReadStuckLimit() {
        try {
            String s = System.getenv("RWX_STUCK_LIMIT");
            if (s != null) {
                return Float.parseFloat(s.trim());
            }
        } catch (Throwable th) {
        }
        return 600.0f;
    }
    /* JADX INFO: renamed from: v */
    private int totalPathPositions;
    /* JADX INFO: renamed from: w */
    private int longRangePathing;
    /* JADX INFO: renamed from: ax */
    public boolean needsRecalculation;

    /* JADX INFO: renamed from: ay */
    public boolean movementActiveThisFrame;

    /* JADX INFO: renamed from: az */
    public float bodyMovementFreezeTimer;

    /* JADX INFO: renamed from: aA */
    public float legacyMovementTimer;
    public AIUnitGroupBase aB;
    public BaseZone aC;
    public boolean aD;

    /* JADX INFO: renamed from: aF */
    public static final GamePaint selectedTeamPaint;
    public static final PointF aG;

    /* JADX INFO: renamed from: x */
    private GamePaint cachedBasePaint;

    /* JADX INFO: renamed from: y */
    private int cachedBasePaintColor;

    /* JADX INFO: renamed from: z */
    private GamePaint cachedSelectedPaint;

    /* JADX INFO: renamed from: A */
    private int cachedSelectedPaintColor;

    /* JADX INFO: renamed from: B */
    private static final Paint paralyzedOverlayPaint;
    private static int C;

    /* JADX INFO: renamed from: D */
    private static final GamePaint baseTeamTint;

    /* JADX INFO: renamed from: E */
    private static final GamePaint selectedTeamTint;
    public static UnitSpatialCallback aH;

    /* JADX INFO: renamed from: aI */
    public byte nearbyCollisionSize;

    /* JADX INFO: renamed from: aJ */
    public BaseUnit[] nearbyUnits;

    /* JADX INFO: renamed from: aK */
    public float[] nearbyUnitDistances;

    /* JADX INFO: renamed from: aL */
    public int nextCollisionQueryTick;
    public static final UnitList aM;
    public boolean aN;
    public boolean aO;
    static final UnitStateTracker aP;
    public static SingleTargetPassiveCallback aQ;
    public static SingleTargetPassiveCallback aR;
    public static MultiTurretPassiveTargetCallback aS;
    public static MultiTurretPassiveTargetCallback aT;
    Path aU;
    static FastArrayList aV;
    public static final PositionData aW;

    /* JADX INFO: renamed from: aX */
    protected static BlendColorFilter overlayFilterLightGreen;

    /* JADX INFO: renamed from: aY */
    protected static BlendColorFilter overlayFilterGreen;

    /* JADX INFO: renamed from: aZ */
    protected static BlendColorFilter overlayFilterRed;

    /* JADX INFO: renamed from: ba */
    protected static BlendColorFilter overlayFilterBlue;

    /* JADX INFO: renamed from: bb */
    protected static Paint overlayPaint1;

    /* JADX INFO: renamed from: bc */
    protected static Paint overlayPaint2;

    /* JADX INFO: renamed from: bd */
    protected static Paint overlayPaint3;

    /* JADX INFO: renamed from: be */
    static final PointF tempPointF1;
    protected static final Vector3D bf;

    /* JADX INFO: renamed from: bg */
    protected static final PointF tempPointF2;

    /* JADX INFO: renamed from: bh */
    protected static final PointF tempPointF3;
    protected static final Vector3D bi;

    /* JADX INFO: renamed from: bj */
    protected static final PointF tempPointF4;

    /* JADX INFO: renamed from: bk */
    static final Point tempPoint1;

    /* JADX INFO: renamed from: bl */
    static final Point tempPoint2;
    static final PointF bm;
    static final TriggerDebugAction bn;
    public static final NearestUnitFinder bo;

    /* JADX INFO: renamed from: bp */
    public FastArrayList activeStatusEffects;
    static FastArrayList bq;
    public static boolean L = false;
    // RWX：自测开关与计数
    private static final boolean RWX_SELFTEST = "1".equals(System.getProperty("rwx.selftest", "0"));
    private static int RWX_AUDIT_N = 0;
    public static final UnitCommand[] O = new UnitCommand[0];
    public static final PositionData[] at = new PositionData[0];

    /* JADX INFO: renamed from: aE */
    public static final GamePaint defaultTeamPaint = new GamePaint();

    public abstract Texture d();

    public abstract Texture k();

    public abstract Texture d(int i);

    public abstract boolean I();

    public abstract float m();

    public abstract float b(int i);

    /* JADX INFO: renamed from: A */
    public abstract float getMaxTurnSpeed();

    public abstract float c(int i);

    /* JADX INFO: renamed from: z */
    public abstract float getMoveSpeed();

    public abstract void a(BaseUnit baseUnit, int i);

    static {
        defaultTeamPaint.a(128, 255, 255, 255);
        defaultTeamPaint.o();
        selectedTeamPaint = new GamePaint();
        selectedTeamPaint.a(defaultTeamPaint);
        selectedTeamPaint.a(true);
        selectedTeamPaint.d(true);
        selectedTeamPaint.b(true);
        selectedTeamPaint.o();
        aG = new PointF();
        paralyzedOverlayPaint = new Paint();
        baseTeamTint = a(false);
        selectedTeamTint = a(true);
        aH = new UnitSpatialCallback() { // from class: com.corrodinggames.rts.game.units.y.1
            @Override // com.corrodinggames.rts.game.units.spatial.UnitSpatialCallback
            public void callback(OrderableUnit orderableUnit, float f, BaseUnit baseUnit) {
                orderableUnit.resolveSoftCollisionWithUnit(baseUnit, f, true);
            }
        };
        aM = new UnitList(64);
        aP = new UnitStateTracker();
        aQ = new SingleTargetPassiveCallback(true);
        aR = new SingleTargetPassiveCallback(false);
        aS = new MultiTurretPassiveTargetCallback(true);
        aT = new MultiTurretPassiveTargetCallback(false);
        aV = new FastArrayList();
        aW = new PositionData();
        overlayFilterLightGreen = new BlendColorFilter(ArgbColor.a(200, 255, 200), CanvasBlendMode.Multiply);
        overlayFilterGreen = new BlendColorFilter(ArgbColor.a(70, 255, 70), CanvasBlendMode.Multiply);
        overlayFilterRed = new BlendColorFilter(ArgbColor.a(255, 40, 40), CanvasBlendMode.Multiply);
        overlayFilterBlue = new BlendColorFilter(ArgbColor.a(120, 120, 255), CanvasBlendMode.Multiply);
        overlayPaint1 = GameViewUtils.b();
        overlayPaint2 = GameViewUtils.b();
        overlayPaint3 = GameViewUtils.b();
        tempPointF1 = new PointF();
        bf = new Vector3D();
        tempPointF2 = new PointF();
        tempPointF3 = new PointF();
        bi = new Vector3D();
        tempPointF4 = new PointF();
        tempPoint1 = new Point();
        tempPoint2 = new Point();
        bm = new PointF();
        bn = new TriggerDebugAction();
        bo = new NearestUnitFinder();
        bq = new FastArrayList();
    }

    /* JADX INFO: renamed from: b */
    public void setBodyMovementFreezeTimer(float f) {
        if (this.bodyMovementFreezeTimer < f) {
            this.bodyMovementFreezeTimer = f;
        }
    }

    /* JADX INFO: renamed from: R */
    public Paint getSelectionPaint() {
        if (isRenderAntiAliasEnabled()) {
            return selectedTeamPaint;
        }
        return defaultTeamPaint;
    }

    /* JADX INFO: renamed from: a */
    public static void copyWaypoints(OrderableUnit orderableUnit, OrderableUnit orderableUnit2) {
        try {
            GameOutputStream gameOutputStream = new GameOutputStream();
            int i = orderableUnit.waypointCount;
            for (int i2 = 0; i2 < i; i2++) {
                orderableUnit.waypoints[i2].serialize(gameOutputStream);
            }
            GameInputStream gameInputStream = new GameInputStream(gameOutputStream.toByteArray());
            orderableUnit2.waypointCount = i;
            for (int i3 = 0; i3 < i; i3++) {
                int length = i3;
                orderableUnit2.ensureWaypointCapacityForIndex(length);
                if (length >= orderableUnit2.waypoints.length) {
                    GameEngine.logColored("Too many waypoints:" + i3);
                    length = orderableUnit2.waypoints.length - 1;
                }
                if (orderableUnit2.waypoints[length] == null) {
                    orderableUnit2.waypoints[length] = new UnitCommand();
                }
                orderableUnit2.waypoints[length].deserialize(gameInputStream);
                orderableUnit2.waypoints[length].resolveTargetUnitFromId();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public OrderableUnit(boolean z) {
        super(z);
        this.waypointCount = 0;
        this.waypoints = O;
        this.attackMode = AttackMode.onlyInRange;
        this.lastAttackTick = -9999;
        this.lastReclaimSearchTick = -9999;
        this.pathTargetX = 3.0f;
        this.pathTargetY = 3.0f;
        this.inFormation = false;
        this.transportOffsetX = 0.0f;
        this.transportOffsetY = 0.0f;
        this.formationSlotAngle = 0.0f;
        this.lastTransportPathUpdateTick = 0;
        this.formationSlotDistanceSq = 0.0f;
        this.navigationAngle = -999.0f;
        this.isSecondaryRecharging = false;
        this.isTargetSearchPending = false;
        this.pathPositions = at;
        this.activePathCount = 0;
        this.totalPathPositions = 0;
        this.needsRecalculation = true;
        this.cachedBasePaint = null;
        this.cachedSelectedPaint = null;
        this.nearbyCollisionSize = (byte) 0;
        this.nextCollisionQueryTick = -9999;
        this.aU = null;
    }

    /* JADX INFO: renamed from: g */
    public static void updateAllUnitCollisions(float f) {
        byte b;
        float f2;
        GameEngine gameEngine = GameEngine.getInstance();
        gameEngine.performanceProfiler.a(ProfilerSection.update_do_all_collisions);
        int i = gameEngine.gameTimeMillis;
        boolean perfCollisions = gameEngine.settingsEngine.perfCollisions;
        UnitList unitList = aM;
        BaseUnit[] baseUnitArrA = BaseUnit.bE.a();
        int size = BaseUnit.bE.size();
        for (int i2 = 0; i2 < size; i2++) {
            baseUnitArrA[i2].bR();
        }
        for (int i3 = 0; i3 < size; i3++) {
            if (baseUnitArrA[i3] instanceof OrderableUnit) {
                OrderableUnit orderableUnit = (OrderableUnit) baseUnitArrA[i3];
                if ((orderableUnit.movementActiveThisFrame || orderableUnit.collisionActive) && orderableUnit.I() && orderableUnit.nextCollisionQueryTick <= i) {
                    orderableUnit.collisionActive = false;
                    orderableUnit.movementActiveThisFrame = true;
                    if (orderableUnit.isMoving) {
                        f2 = orderableUnit.radius + 7.0f;
                        if (orderableUnit.nearbyCollisionSize > 9) {
                            orderableUnit.nextCollisionQueryTick = i + 200 + (i3 % 50);
                        } else {
                            orderableUnit.nextCollisionQueryTick = i + 50 + (i3 % 50);
                        }
                    } else {
                        f2 = orderableUnit.radius + 5.0f;
                        orderableUnit.nextCollisionQueryTick = i + 250 + (i3 % 50);
                    }
                    orderableUnit.nearbyCollisionSize = (byte) 0;
                    unitList.clear();
                    if (perfCollisions) {
                        gameEngine.unitSpatialIndex.b(orderableUnit.posX, orderableUnit.posY, f2, 16.0f, unitList);
                    } else {
                        gameEngine.unitSpatialIndex.b(orderableUnit.posX, orderableUnit.posY, f2, unitList);
                    }
                    BaseUnit[] baseUnitArrA2 = unitList.a();
                    int i4 = unitList.b;
                    for (int i5 = 0; i5 < i4; i5++) {
                        orderableUnit.resolveSoftCollisionWithUnit(baseUnitArrA2[i5], f, true);
                    }
                    if (orderableUnit.nearbyCollisionSize > 9 && orderableUnit.timeAliveStamp > i - 400) {
                        orderableUnit.nextCollisionQueryTick = gameEngine.gameTimeMillis + (perfCollisions ? 40 + (i3 % 20) : 5 + (i3 % 5));
                        orderableUnit.collisionActive = true;
                    }
                }
            }
        }
        gameEngine.performanceProfiler.b(ProfilerSection.update_do_all_collisions);
        gameEngine.performanceProfiler.a(ProfilerSection.update_do_all_collisions2);
        // RWX 性能模式：推挤每两帧做一次，步长加倍补回两帧总位移；关闭时逐帧原样。
        boolean pushThisFrame = !perfCollisions || (gameEngine.currentTick & 1) == 0;
        float pushScale = perfCollisions ? 2.0f : 1.0f;
        for (int i6 = 0; i6 < size && pushThisFrame; i6++) {
            if (baseUnitArrA[i6] instanceof OrderableUnit) {
                OrderableUnit orderableUnit2 = (OrderableUnit) baseUnitArrA[i6];
                if (orderableUnit2.movementActiveThisFrame && (b = orderableUnit2.nearbyCollisionSize) > 0 && orderableUnit2.I()) {
                    if (!orderableUnit2.collisionActive) {
                        orderableUnit2.collisionActive = true;
                    }
                    for (int i7 = 0; i7 < b; i7++) {
                        orderableUnit2.resolveSoftCollisionWithUnit(orderableUnit2.nearbyUnits[i7], f * pushScale, false);
                    }
                }
            }
        }
        gameEngine.performanceProfiler.b(ProfilerSection.update_do_all_collisions2);
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    /* JADX INFO: renamed from: b */
    public void setUnitTeam(PlayerTeam playerTeam) {
        super.setUnitTeam(playerTeam);
        S();
    }

    public void S() {
        this.baseTexture = d();
        this.shadowTexture = k();
    }

    public float h(int i) {
        return 0.0f;
    }

    public float i(int i) {
        return 0.0f;
    }

    public Texture T() {
        return null;
    }

    public Paint a(int i, ColorFilter colorFilter, boolean z) {
        Paint paint;
        int i2;
        if (i == -1 && colorFilter == null) {
            if (z) {
                return selectedTeamTint;
            }
            return baseTeamTint;
        }
        if (this.isUnitParalyzed) {
            if (colorFilter == null) {
                paint = paralyzedOverlayPaint;
                i2 = C;
                C = i;
            } else {
                paint = paralyzedOverlayPaint;
                i2 = -1;
                if (colorFilter == overlayFilterRed) {
                    paint = overlayPaint2;
                }
                if (colorFilter == overlayFilterGreen) {
                    paint = overlayPaint1;
                }
                if (colorFilter == overlayFilterBlue) {
                    paint = overlayPaint3;
                }
            }
        } else if (z) {
            if (this.cachedSelectedPaint == null) {
                this.cachedSelectedPaint = a(true);
            }
            paint = this.cachedSelectedPaint;
            i2 = this.cachedSelectedPaintColor;
            this.cachedSelectedPaintColor = i;
        } else {
            if (this.cachedBasePaint == null) {
                this.cachedBasePaint = a(false);
            }
            paint = this.cachedBasePaint;
            i2 = this.cachedBasePaintColor;
            this.cachedBasePaintColor = i;
        }
        if (i2 != i) {
            paint.b(i);
        }
        if (paint.h() != colorFilter) {
            paint.a(colorFilter);
        }
        return paint;
    }

    public static GamePaint a(boolean z) {
        GamePaint gamePaint = new GamePaint();
        if (z) {
            gamePaint.a(true);
            gamePaint.d(true);
            gamePaint.b(true);
        } else {
            gamePaint.a(false);
            gamePaint.d(false);
            gamePaint.b(false);
        }
        return gamePaint;
    }

    @Override
    // com.corrodinggames.rts.game.units.BaseUnit, com.corrodinggames.rts.gameFramework.PositionedObject, com.corrodinggames.rts.gameFramework.GameObject, com.corrodinggames.rts.gameFramework.Serializable
    public void a(GameOutputStream gameOutputStream) throws IOException {
        gameOutputStream.writeFloat(this.blockedRecoveryTime);
        gameOutputStream.writeFloat(this.moveThrottle);
        gameOutputStream.writeFloat(this.movementLevels[0].rotation);
        gameOutputStream.writeInt(this.waypointCount);
        int i = this.waypointCount;
        gameOutputStream.writeInt(i);
        for (int i2 = 0; i2 < i; i2++) {
            this.waypoints[i2].serialize(gameOutputStream);
        }
        gameOutputStream.writeEnumOrdinal(this.attackMode);
        BaseUnit baseUnit = this.attackTarget;
        if (baseUnit != null && baseUnit.isDead) {
            baseUnit = null;
        }
        gameOutputStream.writeUnitIdOrNullBaseUnit(baseUnit);
        gameOutputStream.writeFloat(this.turretRotation);
        gameOutputStream.writeFloat(this.turretTurnSpeed);
        gameOutputStream.writeFloat(this.wayPointTimer);
        gameOutputStream.writeDebugMessage("pathing_active:");
        gameOutputStream.writeBoolean(this.isPathingActive);
        gameOutputStream.writeFloat(this.pathTargetX);
        gameOutputStream.writeFloat(this.pathTargetY);
        gameOutputStream.writeFloat(this.repathCooldown);
        gameOutputStream.writeOrderableUnit(this.transportedBy);
        gameOutputStream.writeBoolean(this.isTransportAttached);
        gameOutputStream.writeBoolean(this.shouldMaintainFormation);
        gameOutputStream.writeBoolean(this.inFormation);
        gameOutputStream.writeFloat(this.transportOffsetX);
        gameOutputStream.writeFloat(this.transportOffsetY);
        gameOutputStream.writeFloat(this.formationSlotAngle);
        gameOutputStream.writeInt(this.lastTransportPathUpdateTick);
        gameOutputStream.writeInt(this.waypointSyncGroupId);
        gameOutputStream.writeDebugMessage("activePathCount:");
        gameOutputStream.writeInt(this.activePathCount);
        for (int i3 = 0; i3 < this.activePathCount; i3++) {
            this.pathPositions[i3].a(gameOutputStream);
        }
        gameOutputStream.writeInt(this.activePathCount);
        gameOutputStream.writeInt(this.totalPathPositions);
        if (gameOutputStream.isDebugStream()) {
        }
        gameOutputStream.writeByte(12);
        gameOutputStream.writeFloat(this.lastPathTargetX);
        gameOutputStream.writeFloat(this.lastPathTargetY);
        gameOutputStream.writeFloat(this.transportRecoveryTime);
        gameOutputStream.writeFloat(this.transportRecoveryDelay);
        gameOutputStream.writeBoolean(this.isPathIncomplete);
        gameOutputStream.writeFloat(this.transportAttackAssistTimer);
        gameOutputStream.writeInt(this.pathTargetRadius);
        gameOutputStream.writeFloat(this.pathRetryTimer);
        gameOutputStream.writeFloat(this.navigationAngle);
        gameOutputStream.writeBoolean(this.isSecondaryRecharging);
        gameOutputStream.writeBoolean(this.isTargetSearchPending);
        gameOutputStream.writeShort(this.formationSlotIndex);
        gameOutputStream.writeFloat(this.pendingCredits);
        gameOutputStream.writeInt(this.longRangePathing);
        gameOutputStream.writeFloat(this.previousPathRetryTimer);
        gameOutputStream.writeFloat(this.bodyMovementFreezeTimer);
        gameOutputStream.writeFloat(this.legacyMovementTimer);
        AirUnitEffectManager.a(this, gameOutputStream);
        super.a(gameOutputStream);
    }

    public final void j(int i) {
        int techLevel = getTechLevel();
        for (int i2 = 0; i2 < techLevel; i2++) {
            this.movementLevels[i2].a(i);
        }
    }

    @Override
    // com.corrodinggames.rts.game.units.BaseUnit, com.corrodinggames.rts.gameFramework.PositionedObject, com.corrodinggames.rts.gameFramework.GameObject
    public void a(GameInputStream gameInputStream) throws IOException {
        this.blockedRecoveryTime = gameInputStream.readFloat();
        this.moveThrottle = gameInputStream.readFloat();
        this.movementLevels[0].rotation = gameInputStream.readFloat();
        this.waypointCount = gameInputStream.collectionSize(gameInputStream.readInt());
        if (this.waypointCount > 0) {
            ensureWaypointCapacityForIndex(Utility.min(this.waypointCount - 1, 29));
        }
        int i = 30;
        if (gameInputStream.getProtocolVersion() >= 42) {
            i = gameInputStream.collectionSize(gameInputStream.readInt());
        }
        for (int i2 = 0; i2 < i; i2++) {
            int length = i2;
            ensureWaypointCapacityForIndex(length);
            if (length >= this.waypoints.length) {
                GameEngine.logColored("Too many waypoints:" + i2);
                length = this.waypoints.length - 1;
            }
            if (this.waypoints[length] == null) {
                this.waypoints[length] = new UnitCommand();
            }
            this.waypoints[length].deserialize(gameInputStream);
        }
        this.attackMode = (AttackMode) gameInputStream.readEnumOrdinalOrNull(AttackMode.class);
        if (this.attackMode == AttackMode.outOfRange) {
            if (!I()) {
                this.attackMode = AttackMode.onlyInRange;
            }
            if (gameInputStream.getProtocolVersion() < 74) {
                this.attackMode = AttackMode.onlyInRange;
            }
        }
        long attackTargetUnitId = gameInputStream.readUnitId();
        this.turretRotation = gameInputStream.readFloat();
        this.turretTurnSpeed = gameInputStream.readFloat();
        this.wayPointTimer = gameInputStream.readFloat();
        this.isPathingActive = gameInputStream.readBoolean();
        this.pathTargetX = gameInputStream.readFloat();
        this.pathTargetY = gameInputStream.readFloat();
        this.repathCooldown = gameInputStream.readFloat();
        setTransportParent(gameInputStream.readOrderableUnit());
        this.isTransportAttached = gameInputStream.readBoolean();
        this.shouldMaintainFormation = gameInputStream.readBoolean();
        this.inFormation = gameInputStream.readBoolean();
        this.transportOffsetX = gameInputStream.readFloat();
        this.transportOffsetY = gameInputStream.readFloat();
        this.formationSlotAngle = gameInputStream.readFloat();
        this.lastTransportPathUpdateTick = gameInputStream.readInt();
        if (gameInputStream.getProtocolVersion() >= 18) {
            this.waypointSyncGroupId = gameInputStream.readInt();
        }
        if (gameInputStream.getProtocolVersion() >= 21) {
            int i3 = gameInputStream.collectionSize(gameInputStream.readInt());
            for (int i4 = 0; i4 < i3; i4++) {
                ensurePathPositionCapacityForIndex(i4);
                if (this.pathPositions[i4] == null) {
                    this.pathPositions[i4] = new PositionData();
                }
                this.pathPositions[i4].a(gameInputStream);
            }
        } else {
            for (int i5 = 0; i5 < 60; i5++) {
                ensurePathPositionCapacityForIndex(i5);
                if (this.pathPositions[i5] == null) {
                    this.pathPositions[i5] = new PositionData();
                }
                this.pathPositions[i5].a(gameInputStream);
            }
        }
        this.activePathCount = gameInputStream.readInt();
        this.totalPathPositions = gameInputStream.readInt();
        byte b = gameInputStream.readByte();
        if (b >= 1) {
            this.lastPathTargetX = gameInputStream.readFloat();
            this.lastPathTargetY = gameInputStream.readFloat();
        }
        if (b >= 2) {
            this.transportRecoveryTime = gameInputStream.readFloat();
            this.transportRecoveryDelay = gameInputStream.readFloat();
        }
        if (b >= 3) {
            this.isPathIncomplete = gameInputStream.readBoolean();
        }
        if (b >= 4) {
            this.transportAttackAssistTimer = gameInputStream.readFloat();
            this.pathTargetRadius = gameInputStream.readInt();
        }
        if (b >= 5) {
            this.pathRetryTimer = gameInputStream.readFloat();
        }
        if (b >= 6) {
            this.navigationAngle = gameInputStream.readFloat();
            this.isSecondaryRecharging = gameInputStream.readBoolean();
            this.isTargetSearchPending = gameInputStream.readBoolean();
        }
        if (b >= 7) {
            this.formationSlotIndex = gameInputStream.readShortValue();
        }
        if (b >= 8) {
            this.pendingCredits = gameInputStream.readFloat();
        }
        if (b >= 9) {
            this.longRangePathing = gameInputStream.readInt();
        }
        if (b >= 10) {
            this.previousPathRetryTimer = gameInputStream.readFloat();
        }
        if (b >= 11) {
            this.bodyMovementFreezeTimer = gameInputStream.readFloat();
            this.legacyMovementTimer = gameInputStream.readFloat();
        }
        if (b >= 12) {
            AirUnitEffectManager.a(this, gameInputStream);
        }
        super.a(gameInputStream);
        if (!this.isDead) {
            this.attackTarget = (BaseUnit) gameInputStream.resolveObjectId(attackTargetUnitId, BaseUnit.class, false);
            for (int i6 = 0; i6 < this.waypointCount; i6++) {
                if (this.waypoints[i6] == null) {
                    GameEngine.log("readIn: convertUnitIds is null: " + i6 + " waypointsCount:" + this.waypointCount);
                } else {
                    this.waypoints[i6].resolveTargetUnitFromId(gameInputStream);
                }
            }
        }
        S();
        if (this.isDead) {
            this.ew = true;
        }
    }

    public void a(String str) {
        String unitTypeDescriptionShort;
        if (r() != null) {
            unitTypeDescriptionShort = r().getUnitTypeDescriptionShort();
        } else {
            unitTypeDescriptionShort = "<NO UNIT TYPE>";
        }
        GameEngine.log("(Unit log:" + unitTypeDescriptionShort + " id:" + this.objectId + "): " + str);
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit, com.corrodinggames.rts.gameFramework.GameObject
    /* JADX INFO: renamed from: a */
    public void update(float f) {
        if (this.pendingPortalTransfer != null) return;
        float fD;
        float fFastCos;
        float fFastSin;
        super.update(f);
        if (this.movementActiveThisFrame) {
            this.movementActiveThisFrame = false;
        }
        if (this.spawnExitLockTimer != 0.0f) {
            this.spawnExitLockTimer = Utility.moveTowardsZero(this.spawnExitLockTimer, f);
        }
        if (!this.isDead && isAlive()) {
            GameEngine gameEngine = GameEngine.getInstance();
            if (this.bodyMovementFreezeTimer > 0.0f) {
                this.bodyMovementFreezeTimer = Utility.moveTowardsZero(this.bodyMovementFreezeTimer, f);
            }
            if (this.legacyMovementTimer > 0.0f) {
                this.legacyMovementTimer = Utility.moveTowardsZero(this.legacyMovementTimer, f);
            }
            if (this.activeStatusEffects != null) {
                AirUnitEffectManager.a(this, f);
            }
            float fFastCos2 = this.posX;
            float moveYAxisScaling = this.posY;
            int techLevel = getTechLevel();
            for (int i = 0; i < techLevel; i++) {
                UnitMovementData unitMovementData = this.movementLevels[i];
                if (unitMovementData.velocityY == 0.0f) {
                    float fC = C(i);
                    if (b(i, f) && unitMovementData.targetX != fC) {
                        if (Utility.abs(Utility.rotateTowardsAngle(unitMovementData.targetX, fC, 360.0f)) < 0.5f) {
                            unitMovementData.velocityY = 20.0f;
                            unitMovementData.velocityX = 0.0f;
                        } else {
                            a(f, fC, i);
                        }
                    }
                } else {
                    unitMovementData.velocityY = Utility.moveTowardsZero(unitMovementData.velocityY, f);
                }
            }
            if (!bk()) {
                updateMovementLogic(f);
            }
            for (int i2 = 0; i2 < techLevel; i2++) {
                UnitMovementData unitMovementData2 = this.movementLevels[i2];
                if (unitMovementData2.rotation != 0.0f) {
                    unitMovementData2.rotation = Utility.moveTowardsZero(unitMovementData2.rotation, f);
                }
            }
            boolean zIsSlidingMovement = isSlidingMovement();
            boolean z = (this.velocityX == 0.0f && this.velocityY == 0.0f) ? false : true;
            if ((this.rotation != 0.0f || z) && I()) {
                float f2 = this.rotationSpeed;
                float moveSpeed = getMoveSpeed();
                if (isIgnoreMoveOrders()) {
                    f2 = this.targetRotation;
                }
                if (!zIsSlidingMovement) {
                    float f3 = moveSpeed * this.rotation * f;
                    fFastCos2 += Utility.fastCos(f2) * f3;
                    moveYAxisScaling += Utility.fastSin(f2) * f3 * getMoveYAxisScaling();
                    if (z) {
                        fFastCos2 += this.velocityX * f;
                        moveYAxisScaling += this.velocityY * f * getMoveYAxisScaling();
                        if (Utility.distanceSq(0.0f, 0.0f, this.velocityX, this.velocityY) > moveSpeed * moveSpeed) {
                            this.velocityX = (float) (((double) this.velocityX) - ((((double) this.velocityX) * 0.05d) * ((double) f)));
                            this.velocityY = (float) (((double) this.velocityY) - ((((double) this.velocityY) * 0.05d) * ((double) f)));
                        }
                        this.velocityX = Utility.distanceSq(this.velocityX, 0.0f, 0.5f * moveSpeed * f);
                        this.velocityY = Utility.distanceSq(this.velocityY, 0.0f, 0.5f * moveSpeed * f);
                    }
                } else {
                    if (this.rotation != 0.0f) {
                        fD = getMoveAccelerationSpeed() * 1.41f;
                        fFastCos = Utility.fastCos(f2) * moveSpeed * this.rotation;
                        fFastSin = Utility.fastSin(f2) * moveSpeed * this.rotation;
                    } else {
                        fD = D() * 1.41f;
                        fFastCos = 0.0f;
                        fFastSin = 0.0f;
                    }
                    float fDistanceSq = Utility.distanceSq(this.velocityX, this.velocityY, fFastCos, fFastSin);
                    if (fDistanceSq > moveSpeed * moveSpeed) {
                        this.velocityX = (float) (((double) this.velocityX) - ((((double) this.velocityX) * 0.05d) * ((double) f)));
                        this.velocityY = (float) (((double) this.velocityY) - ((((double) this.velocityY) * 0.05d) * ((double) f)));
                    }
                    float f4 = fD * f;
                    if (fDistanceSq < f4 * f4) {
                        this.velocityX = fFastCos;
                        this.velocityY = fFastSin;
                    } else {
                        float angleBetweenPoints = Utility.getAngleBetweenPoints(this.velocityX, this.velocityY, fFastCos, fFastSin);
                        this.velocityX += Utility.fastCos(angleBetweenPoints) * f4;
                        this.velocityY += Utility.fastSin(angleBetweenPoints) * f4;
                    }
                    fFastCos2 += this.velocityX * f;
                    moveYAxisScaling += this.velocityY * f * getMoveYAxisScaling();
                }
                this.movementActiveThisFrame = true;
            }
            if (this.worldX != 0.0f || this.worldY != 0.0f) {
                this.worldX = Utility.clampTo255(this.worldX, -9.0f, 9.0f);
                this.worldY = Utility.clampTo255(this.worldY, -9.0f, 9.0f);
                fFastCos2 += this.worldX;
                moveYAxisScaling += this.worldY;
                this.worldY = 0.0f;
                this.worldX = 0.0f;
                this.movementActiveThisFrame = true;
            }
            if (this.movementActiveThisFrame && I() && this.parentEntity == null) {
                applyPositionChange(f, gameEngine, fFastCos2, moveYAxisScaling);
            }
            if (this.needsRecalculation) {
                this.needsRecalculation = false;
                c(false);
                this.movementActiveThisFrame = true;
            }
        }
    }

    /* JADX INFO: renamed from: a */
    private void applyPositionChange(float f, GameEngine gameEngine, float f2, float f3) {
        TileMap tileMap = gameEngine.tileMap;
        float f4 = tileMap.tileScaleX;
        float f5 = tileMap.tileScaleY;
        float f6 = this.posX * f4;
        float f7 = this.posY * f5;
        float f8 = f2 * f4;
        float f9 = f3 * f5;
        PointF pointFA = null;
        boolean z = false;
        int iMax = Utility.max(f6);
        int iMax2 = Utility.max(f7);
        int iMax3 = Utility.max(f8);
        int iMax4 = Utility.max(f9);
        if ((iMax != iMax3 || iMax2 != iMax4) && this.spawnExitLockTimer == 0.0f && gameEngine.pathfindingEngine.isTileBlockedForMovement(getMovementType(), iMax3, iMax4)) {
            if (iMax != iMax3 && iMax2 != iMax4) {
                boolean zA = gameEngine.pathfindingEngine.isTileBlockedForMovement(getMovementType(), iMax, iMax4);
                boolean zA2 = gameEngine.pathfindingEngine.isTileBlockedForMovement(getMovementType(), iMax3, iMax2);
                if (zA && zA2) {
                    z = true;
                    aG.a(f6, f7);
                    pointFA = aG;
                }
                if (pointFA == null && zA) {
                    pointFA = PathfindingUtils.a(getMovementType(), f6, f7, f8, f9, iMax, iMax4, false);
                }
                if (pointFA == null && zA2) {
                    pointFA = PathfindingUtils.a(getMovementType(), f6, f7, f8, f9, iMax3, iMax2, false);
                }
            }
            if (pointFA == null) {
                pointFA = PathfindingUtils.a(getMovementType(), f6, f7, f8, f9, iMax3, iMax4, false);
            }
            if (pointFA == null) {
                z = true;
                aG.a(f6, f7);
                pointFA = aG;
            }
        }
        boolean z2 = false;
        if (pointFA != null) {
            boolean z3 = false;
            if (gameEngine.pathfindingEngine.isTileBlockedForMovement(getMovementType(), iMax, iMax2) && !gameEngine.pathfindingEngine.b(getMovementType(), iMax3, iMax4)) {
                z3 = true;
            }
            if (!z3) {
                f2 = pointFA.x * tileMap.tileWorldSizeX;
                f3 = pointFA.y * tileMap.tileWorldSizeY;
                z2 = true;
            } else {
                z = false;
            }
        }
        if (z2) {
            this.blockedRecoveryTime += f;
            this.blockedRecoveryMisses = 0;
        } else if (this.blockedRecoveryTime != 0.0f && f > 0.0f) {
            this.blockedRecoveryMisses++;
            if (this.blockedRecoveryMisses >= 3) {
                this.blockedRecoveryTime = 0.0f;
            }
        }
        if (!z) {
            int iMax5 = Utility.max(f2 * f4);
            int iMax6 = Utility.max(f3 * f5);
            this.posX = f2;
            this.posY = f3;
            if (iMax != iMax5 || iMax2 != iMax6) {
                c(true);
            }
        }
    }

    public void b(float f, float f2) {
        TileMap tileMap = GameEngine.getInstance().tileMap;
        float f3 = tileMap.tileScaleX;
        float f4 = tileMap.tileScaleY;
        int iMax = Utility.max(this.posX * f3);
        int iMax2 = Utility.max(this.posY * f4);
        int iMax3 = Utility.max(f * f3);
        int iMax4 = Utility.max(f2 * f4);
        this.posX = f;
        this.posY = f2;
        if (iMax != iMax3 || iMax2 != iMax4) {
            c(true);
        }
    }

    public void U() {
        String unitTypeDescriptionShort;
        if (r() != null) {
            unitTypeDescriptionShort = r().getUnitTypeDescriptionShort();
        } else {
            unitTypeDescriptionShort = "<NO UNIT TYPE>";
        }
        GameEngine.log("---- Debug for:" + unitTypeDescriptionShort + " id:" + this.objectId + "---");
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX INFO: renamed from: a */
    public final void resolveSoftCollisionWithUnit(BaseUnit baseUnit, float f, boolean z) {
        if (isTransferUnavailable() || baseUnit.isTransferUnavailable()) return;
        int i;
        UnitCommand currentWaypoint;
        UnitCommand currentWaypoint2;
        if (baseUnit == this || (i = super.collisionGroup) == -1 || i != baseUnit.collisionGroup || this.targetUnit == baseUnit || baseUnit.targetUnit == this) {
            return;
        }
        float f2 = this.posX + this.worldX;
        float f3 = this.posY + this.worldY;
        float f4 = baseUnit.posX + baseUnit.worldX;
        float f5 = baseUnit.posY + baseUnit.worldY;
        float fDistanceSq = Utility.distanceSq(f2, f3, f4, f5);
        float f6 = this.radius + baseUnit.radius;
        if (z) {
            float f7 = fDistanceSq;
            if (fDistanceSq < f6 * f6) {
                f7 = 0.0f;
            }
            if (baseUnit instanceof OrderableUnit) {
                OrderableUnit orderableUnit = (OrderableUnit) baseUnit;
                byte b = orderableUnit.nearbyCollisionSize;
                for (int i2 = 0; i2 < b; i2++) {
                    if (orderableUnit.nearbyUnits[i2] == this) {
                        return;
                    }
                }
            }
            if (this.nearbyUnits == null) {
                this.nearbyUnits = new BaseUnit[10];
                this.nearbyUnitDistances = new float[10];
            }
            BaseUnit[] baseUnitArr = this.nearbyUnits;
            float[] fArr = this.nearbyUnitDistances;
            int i3 = -1;
            int i4 = 0;
            while (true) {
                if (i4 >= this.nearbyCollisionSize) {
                    break;
                }
                if (f7 >= fArr[i4]) {
                    i4++;
                } else {
                    i3 = i4;
                    break;
                }
            }
            if (i3 == -1) {
                if (this.nearbyCollisionSize < baseUnitArr.length) {
                    i3 = this.nearbyCollisionSize;
                } else {
                    return;
                }
            }
            if (this.nearbyCollisionSize < baseUnitArr.length) {
                this.nearbyCollisionSize = (byte) (this.nearbyCollisionSize + 1);
            }
            for (int i5 = this.nearbyCollisionSize - 1; i5 > i3; i5--) {
                baseUnitArr[i5] = baseUnitArr[i5 - 1];
            }
            baseUnitArr[i3] = baseUnit;
            fArr[i3] = f7;
            return;
        }
        if (fDistanceSq < f6 * f6 && !baseUnit.a(this, f) && !a(baseUnit, f)) {
            float angleBetweenPoints = Utility.getAngleBetweenPoints(f2, f3, f4, f5);
            float fSqrt = (f6 - ((float) Math.sqrt(fDistanceSq))) + 0.001f;
            if (fSqrt <= 0.0f) {
                return;
            }
            int iS = getSoftCollisionDivisor(baseUnit);
            int softCollisionDivisor = baseUnit.getSoftCollisionDivisor(this);
            int i6 = iS > softCollisionDivisor ? iS : softCollisionDivisor;
            if (i6 != 0) {
                float f8 = (fSqrt / i6) * f;
                if (f8 > fSqrt) {
                    f8 = fSqrt;
                }
                fSqrt = f8;
            }
            float f9 = fSqrt * 0.95f;
            if (f9 > 1.0f) {
                f9 *= 0.7f;
            }
            if (f9 > 3.0f) {
                f9 = 3.0f + ((f9 - 3.0f) * 0.7f);
            }
            if (f9 > 6.0f) {
                f9 = 6.0f + ((f9 - 6.0f) * 0.7f);
            }
            if (f9 > 10.0f) {
                f9 = 10.0f + ((f9 - 10.0f) * 0.7f);
            }
            float f10 = 0.0f;
            float fBN = getPushMass();
            float pushMass = baseUnit.getPushMass();
            OrderableUnit orderableUnit2 = null;
            if (baseUnit instanceof OrderableUnit) {
                orderableUnit2 = (OrderableUnit) baseUnit;
            }
            if (this.team == baseUnit.team) {
                boolean z2 = false;
                float f11 = 1.7f;
                if (orderableUnit2 != null) {
                    OrderableUnit orderableUnit3 = orderableUnit2;
                    if (this.pathRetryTimer > 200.0f || orderableUnit3.pathRetryTimer > 200.0f) {
                        f11 = 5.0f;
                    }
                    if (this.transportedBy == orderableUnit3) {
                        pushMass *= f11;
                        z2 = true;
                    }
                    if (orderableUnit3.transportedBy == this) {
                        fBN *= f11;
                        z2 = true;
                    }
                    if (!z2) {
                        if (this.isTransportAttached && orderableUnit3.transportedBy != null) {
                            fBN *= f11;
                        } else if (orderableUnit3.isTransportAttached && this.transportedBy != null) {
                            pushMass *= f11;
                        } else if (this.moveThrottle == 0.0f && orderableUnit3.moveThrottle != 0.0f) {
                            fBN *= f11;
                        } else if (orderableUnit3.moveThrottle == 0.0f && this.moveThrottle != 0.0f) {
                            pushMass *= f11;
                        }
                    }
                }
            }
            if (baseUnit instanceof MovableUnit) {
                f10 = fBN / (fBN + pushMass);
            }
            float f12 = 1.0f - f10;
            float fFastCos = Utility.fastCos(angleBetweenPoints);
            float fFastSin = Utility.fastSin(angleBetweenPoints);
            if (baseUnit instanceof MovableUnit) {
                float f13 = f9 * f10;
                baseUnit.worldX += fFastCos * f13;
                baseUnit.worldY += fFastSin * f13;
            }
            float f14 = f9 * f12;
            this.worldX -= fFastCos * f14;
            this.worldY -= fFastSin * f14;
            int i7 = GameEngine.getInstance().currentTick;
            this.lastDamagedBy = baseUnit;
            this.lastCollisionTick = i7;
            if (orderableUnit2 != null) {
                OrderableUnit orderableUnit4 = orderableUnit2;
                orderableUnit4.lastDamagedBy = this;
                orderableUnit4.lastCollisionTick = i7;
                if (this.waypointSyncGroupId != 0 && this.waypointSyncGroupId == orderableUnit4.waypointSyncGroupId) {
                    if (getCurrentWaypoint() == null && (currentWaypoint2 = orderableUnit4.getCurrentWaypoint()) != null && (currentWaypoint2.commandType == UnitCommandType.move || currentWaypoint2.commandType == UnitCommandType.attackMove)) {
                        orderableUnit4.advanceWaypoint();
                    }
                    if (orderableUnit4.getCurrentWaypoint() == null && (currentWaypoint = getCurrentWaypoint()) != null) {
                        if (currentWaypoint.commandType == UnitCommandType.move || currentWaypoint.commandType == UnitCommandType.attackMove) {
                            advanceWaypoint();
                        }
                    }
                }
            }
        }
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    /* JADX INFO: renamed from: V */
    public int getUpgradeLevel() {
        return 1;
    }

    public void a(int i) {
    }

    /* JADX INFO: Access modifiers changed from: protected */
    public void W() {
        GameEngine gameEngine = GameEngine.getInstance();
        if (this.team == gameEngine.playerTeam) {
            gameEngine.gameUI.warLogDisplay.logUnitUpgraded(this);
        }
    }

    /* JADX INFO: renamed from: b */
    public float rotateTowardWorldPoint(float f, float f2, float f3) {
        if (E()) {
            if (bI()) {
                return 0.0f;
            }
            return faceTowardPosition(f, Utility.getAngleBetweenPoints(this.posX, this.posY, f2, f3));
        }
        if (getTechLevel() < 1) {
            return 0.0f;
        }
        int defaultTurretIndex = getDefaultTurretIndex();
        if (defaultTurretIndex == -1) {
            defaultTurretIndex = 0;
        }
        PointF pointFG = G(defaultTurretIndex);
        float angleBetweenPoints = Utility.getAngleBetweenPoints(pointFG.x, pointFG.y, f2, f3);
        this.movementLevels[defaultTurretIndex].a(70);
        return a(f, angleBetweenPoints, defaultTurretIndex);
    }

    /* JADX INFO: renamed from: c */
    public float faceTowardPosition(float f, float f2) {
        boolean z = false;
        boolean z2 = false;
        if (this.isRotating && bb()) {
            z = true;
            z2 = true;
        }
        return rotateToward(f, f2, z, z2);
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    public void h(float f) {
        float fEndsWith = Utility.rotateTowardsAngle(this.rotationSpeed, f, 360.0f);
        if (Utility.abs(fEndsWith) > 0.01d) {
            addRotation(fEndsWith);
        }
    }

    /* JADX INFO: renamed from: a */
    public float rotateToward(float f, float f2, boolean z, boolean z2) {
        this.targetRotation = f2;
        if (Utility.abs(this.rotationSpeed - f2) < 0.01f) {
            if (z && this.isRotating) {
                j(25);
                this.isRotating = false;
                return 0.0f;
            }
            return 0.0f;
        }
        float fEndsWith = Utility.rotateTowardsAngle(this.rotationSpeed, f2, 360.0f);
        if (z) {
            if (z2 && Utility.abs(fEndsWith) > 100.0f) {
                fEndsWith = Utility.rotateTowardsAngle(this.rotationSpeed, f2 + 180.0f, 360.0f);
                if (!this.isRotating) {
                    j(25);
                    this.isRotating = true;
                }
            } else if (this.isRotating) {
                j(25);
                this.isRotating = false;
            }
        }
        if (Utility.abs(fEndsWith) < 0.01f) {
            return 0.0f;
        }
        if (this.bodyMovementFreezeTimer <= 0.0f) {
            float fB = B();
            if (fB <= 0.0f) {
                float maxTurnSpeed = (fEndsWith > 0.0f ? 1.0f : -1.0f) * getMaxTurnSpeed() * f;
                if (Utility.abs(maxTurnSpeed) > Utility.abs(fEndsWith)) {
                    maxTurnSpeed = fEndsWith;
                }
                addRotation(maxTurnSpeed);
            } else {
                float f3 = fEndsWith > 0.0f ? 1.0f : -1.0f;
                if (Utility.abs(fEndsWith) < Utility.abs(this.direction) / fB) {
                    this.direction = Utility.distanceSq(this.direction, f3 * fB, fB * f);
                } else {
                    this.direction = Utility.distanceSq(this.direction, f3 * getMaxTurnSpeed(), fB * f);
                }
                float f4 = this.direction * f;
                if (Utility.abs(f4) > Utility.abs(fEndsWith)) {
                    this.direction = 0.0f;
                    f4 = fEndsWith;
                }
                addRotation(f4);
            }
        }
        return fEndsWith;
    }

    /* JADX INFO: renamed from: i */
    public void addRotation(float f) {
        this.rotationSpeed += f;
        if (this.rotationSpeed > 180.0f) {
            this.rotationSpeed -= 360.0f;
        }
        if (this.rotationSpeed < -180.0f) {
            this.rotationSpeed += 360.0f;
        }
        if (bm()) {
            int techLevel = getTechLevel();
            for (int i = 0; i < techLevel; i++) {
                UnitMovementData unitMovementData = this.movementLevels[i];
                unitMovementData.targetX += f;
                if (unitMovementData.targetX > 180.0f) {
                    unitMovementData.targetX -= 360.0f;
                }
                if (unitMovementData.targetX < -180.0f) {
                    unitMovementData.targetX += 360.0f;
                }
            }
        }
    }

    public void j(float f) {
        int techLevel = getTechLevel();
        for (int i = 0; i < techLevel; i++) {
            this.movementLevels[i].targetX = f + B(i);
        }
    }

    public void a(int i, float f) {
        this.movementLevels[i].targetX += f;
    }

    public float a(float f, float f2, int i) {
        float f3;
        UnitMovementData unitMovementData = this.movementLevels[i];
        float fEndsWith = Utility.rotateTowardsAngle(unitMovementData.targetX, f2, 360.0f);
        if (fEndsWith == 0.0f) {
            return fEndsWith;
        }
        float fW = w(i);
        if (fW <= 0.0f) {
            float fEndsWith2 = Utility.rotateTowardsAngle(unitMovementData.targetX, f2, c(i) * f);
            a(i, fEndsWith2);
            f3 = fEndsWith - fEndsWith2;
        } else {
            float fY = y(i);
            float f4 = fEndsWith > 0.0f ? 1.0f : -1.0f;
            float fAbs = Utility.abs(unitMovementData.velocityX) / fY;
            boolean z = ((fEndsWith > 0.0f ? 1 : (fEndsWith == 0.0f ? 0 : -1)) > 0) == ((unitMovementData.velocityX > 0.0f ? 1 : (unitMovementData.velocityX == 0.0f ? 0 : -1)) > 0);
            if (Utility.abs(fEndsWith) < fAbs && z) {
                unitMovementData.velocityX = Utility.distanceSq(unitMovementData.velocityX, f4 * fY, fY * f);
            } else {
                unitMovementData.velocityX = Utility.distanceSq(unitMovementData.velocityX, f4 * c(i), fW * f);
            }
            float f5 = unitMovementData.velocityX * f;
            if (Utility.abs(f5) > Utility.abs(fEndsWith)) {
                unitMovementData.velocityX = 0.0f;
                f5 = fEndsWith;
            }
            a(i, f5);
            f3 = fEndsWith - f5;
        }
        return f3;
    }

    /* JADX INFO: renamed from: X */
    public BaseUnit getCurrentRepairOrReclaimTarget() {
        UnitCommand currentWaypoint;
        if (this.isActing && (currentWaypoint = getCurrentWaypoint()) != null) {
            if ((currentWaypoint.commandType == UnitCommandType.repair || currentWaypoint.commandType == UnitCommandType.reclaim) && currentWaypoint.targetUnit != null && !currentWaypoint.targetUnit.isDead) {
                return currentWaypoint.targetUnit;
            }
            return null;
        }
        return null;
    }

    /* JADX INFO: renamed from: Y */
    public boolean isCurrentCommandReclaim() {
        UnitCommand currentWaypoint = getCurrentWaypoint();
        if (currentWaypoint != null && currentWaypoint.commandType == UnitCommandType.reclaim) {
            return true;
        }
        return false;
    }

    /* JADX INFO: renamed from: a */
    private void updateTriggerActionCommand(float f, UnitCommand unitCommand, UnitStateTracker unitStateTracker) {
        if (unitCommand.actionId == null) {
            advanceWaypoint();
            unitCommand = null;
        }
        if (unitCommand != null) {
            if (unitCommand.commandType == UnitCommandType.triggerActionWhenInRange) {
            }
            if (1 != 0) {
                AbstractUnitAction abstractUnitActionA = validateActionId(unitCommand.actionId);
                U();
                if (abstractUnitActionA == null) {
                    a("Failed to find action:" + unitCommand.actionId.getId());
                } else {
                    a(abstractUnitActionA, false, new PointF(unitCommand.targetX, unitCommand.targetY), unitCommand.targetUnit);
                }
                advanceWaypoint();
            }
        }
    }

    /* JADX INFO: renamed from: b */
    private void updateSetPassiveTargetCommand(float f, UnitCommand unitCommand, UnitStateTracker unitStateTracker) {
        BaseUnit targetUnit = unitCommand.getTargetUnit();
        if (targetUnit != null) {
            this.attackTarget = targetUnit;
            if (this.targetTurretRotation > 5.0f) {
                this.targetTurretRotation = 5.0f;
            }
        }
        advanceWaypoint();
    }

    /* JADX INFO: renamed from: c */
    private void updateGuardOrFollowCommand(float f, UnitCommand unitCommand, UnitStateTracker unitStateTracker) {
        float f2;
        BaseUnit currentRepairOrReclaimTarget;
        UnitCommand unitCommandQueueNextWaypoint;
        UnitCommand unitCommandQueueNextWaypoint2;
        float targetX = unitCommand.getTargetX();
        float targetY = unitCommand.getTargetY();
        float fDistanceSq = Utility.distanceSq(this.posX, this.posY, targetX, targetY);
        boolean z = unitCommand.commandType == UnitCommandType.guard || unitCommand.commandType == UnitCommandType.follow;
        boolean z2 = unitCommand.commandType == UnitCommandType.guard;
        BaseUnit baseUnit = unitCommand.targetUnit;
        if (z) {
            if (baseUnit == null || baseUnit.isDead) {
                advanceWaypoint();
                unitCommand = null;
            }
            if (unitCommand != null && baseUnit != null && !baseUnit.isVisibleToEnemies() && this.team.c(baseUnit.team)) {
                advanceWaypoint();
                unitCommand = null;
            }
        }
        if (unitCommand != null) {
            boolean z3 = false;
            float f3 = this.radius;
            if (z) {
                f3 += baseUnit.radius;
            }
            if (unitCommand.commandType == UnitCommandType.follow) {
                if (this.isMoving) {
                    f2 = f3 + 30.0f;
                } else {
                    f2 = f3 + 50.0f;
                }
            } else if (this.isMoving) {
                f2 = f3 + 80.0f;
            } else {
                f2 = f3 + 100.0f;
            }
            if (fDistanceSq > f2 * f2) {
                this.isPathingActive = true;
                this.pathTargetX = targetX;
                this.pathTargetY = targetY;
                this.pathTargetRadius = 2;
                if (this.repathCooldown > 90.0f) {
                    this.repathCooldown = 90.0f;
                }
                this.repathDistanceThreshold = 18;
                if (this.transportedBy != null && !this.transportedBy.isAlive()) {
                    unitStateTracker.isReset = false;
                }
            } else {
                this.longRangePathing = 0;
            }
            unitStateTracker.isReset = false;
            if (0 == 0 && this.attackTarget != null && !this.attackTarget.isDead) {
                boolean z4 = false;
                if (b(this.attackTarget, false)) {
                    z4 = true;
                }
                if (z4) {
                    float fDistanceSq2 = Utility.distanceSq(this.posX, this.posY, this.attackTarget.posX, this.attackTarget.posY);
                    float engagementRange = getEngagementRange(this.attackTarget);
                    boolean z5 = false;
                    boolean z6 = false;
                    if (fDistanceSq2 < engagementRange * engagementRange) {
                        z6 = true;
                    }
                    if (z6 && !hasActiveMovementTarget()) {
                        z6 = false;
                    }
                    if (fDistanceSq < 22500.0f) {
                        this.longRangePathing = 0;
                    }
                    if (!z6 && (this.longRangePathing == 1 || fDistanceSq > 122500.0f)) {
                        z5 = true;
                        this.longRangePathing = 1;
                    }
                    if (fDistanceSq > 302500.0f || (this.longRangePathing == 1 && fDistanceSq > 202500.0f)) {
                        z5 = true;
                        this.longRangePathing = 1;
                    }
                    if (!z5) {
                        z3 = true;
                        this.longRangePathing = 0;
                        if (z6) {
                            this.isPathingActive = false;
                        } else {
                            if (this.repathCooldown > 90.0f) {
                                this.repathCooldown = 90.0f;
                            }
                            this.isPathingActive = true;
                            this.pathTargetX = this.attackTarget.posX;
                            this.pathTargetY = this.attackTarget.posY;
                            this.pathTargetRadius = 0;
                            this.canAttack = true;
                        }
                    }
                }
            }
            if (z2 && !z3) {
                BaseUnit baseUnitQ = baseUnit.q(2.0f);
                if (baseUnitQ != null && !b(baseUnitQ, true)) {
                    baseUnitQ = null;
                }
                if (baseUnitQ == null && this.longRangePathing != 1) {
                    baseUnitQ = q(2.0f);
                    if (baseUnitQ != null && !b(baseUnitQ, true)) {
                        baseUnitQ = null;
                    }
                }
                if (baseUnitQ != null) {
                    z3 = true;
                    if (this.repathCooldown > 90.0f) {
                        this.repathCooldown = 90.0f;
                    }
                    this.isPathingActive = true;
                    this.pathTargetX = baseUnitQ.posX;
                    this.pathTargetY = baseUnitQ.posY;
                    this.pathTargetRadius = 0;
                    this.canAttack = true;
                }
            }
            if (z2 && !z3 && canRepairTarget(baseUnit) && ((baseUnit.currentHealth < baseUnit.maxHealth || baseUnit.buildProgress < 1.0f) && canRepairTarget(baseUnit) && (unitCommandQueueNextWaypoint2 = queueNextWaypoint()) != null)) {
                unitCommandQueueNextWaypoint2.setRepairCommand(baseUnit);
                unitCommandQueueNextWaypoint2.isRepeating = true;
                z3 = true;
                if (this.repathCooldown > 20.0f) {
                    this.repathCooldown = 20.0f;
                }
            }
            if (z2 && !z3 && canMove() && (baseUnit instanceof OrderableUnit) && (currentRepairOrReclaimTarget = ((OrderableUnit) baseUnit).getCurrentRepairOrReclaimTarget()) != null && canRepairTarget(currentRepairOrReclaimTarget) && (unitCommandQueueNextWaypoint = queueNextWaypoint()) != null) {
                unitCommandQueueNextWaypoint.setRepairCommand(currentRepairOrReclaimTarget);
                unitCommandQueueNextWaypoint.isRepeating = true;
                if (this.repathCooldown > 20.0f) {
                    this.repathCooldown = 20.0f;
                }
            }
        }
    }

    /* JADX INFO: renamed from: d */
    private void updateTouchTargetCommand(float f, UnitCommand unitCommand, UnitStateTracker unitStateTracker) {
        float targetX = unitCommand.getTargetX();
        float targetY = unitCommand.getTargetY();
        float fDistanceSq = Utility.distanceSq(this.posX, this.posY, targetX, targetY);
        if (unitCommand.targetUnit == null || unitCommand.targetUnit.isDead) {
            advanceWaypoint();
            unitCommand = null;
        }
        if (unitCommand != null) {
            boolean z = false;
            if (unitCommand.targetUnit.bI()) {
                if (fDistanceSq < 961.0f) {
                    this.waypointDwellTimer += f;
                }
                if (this.waypointDwellTimer > 240.0f) {
                    z = true;
                }
                float f2 = 21.0f;
                if (unitCommand.targetUnit.cc().a()) {
                    f2 = 11.0f;
                }
                if (this.blockedRecoveryTime > 0.0f) {
                    f2 = unitCommand.targetUnit.radius + this.radius + 31.0f;
                }
                if (fDistanceSq < f2 * f2) {
                    z = true;
                }
            } else {
                float f3 = unitCommand.targetUnit.radius + this.radius + 5.0f;
                if (fDistanceSq < f3 * f3) {
                    z = true;
                }
            }
            if (!z) {
                this.isPathingActive = true;
                this.pathTargetX = targetX;
                this.pathTargetY = targetY;
                this.pathTargetRadius = 0;
                if (unitCommand.targetUnit.bI()) {
                    Rect rectCc = unitCommand.targetUnit.cc();
                    this.pathTargetRadius = Utility.min(rectCc.c() / 2, rectCc.b() / 2) + 1;
                }
                if (this.repathCooldown > 90.0f) {
                    this.repathCooldown = 90.0f;
                }
                this.repathDistanceThreshold = 18;
                if (fDistanceSq < 48400.0f) {
                    unitStateTracker.isReset = false;
                    if (this.repathCooldown > 0.0f && getCurrentPathPosition() == null) {
                        this.canAttack = true;
                    }
                }
                if (this.transportedBy != null && !this.transportedBy.isAlive()) {
                    unitStateTracker.isReset = false;
                }
            }
            if (z) {
                a(UnitEventType.touchTargetSuccess, unitCommand.targetUnit);
                advanceWaypoint();
            }
        }
    }

    /* JADX INFO: renamed from: e */
    private void updateLoadUpCommand(float f, UnitCommand unitCommand, UnitStateTracker unitStateTracker) {
        float targetX = unitCommand.getTargetX();
        float targetY = unitCommand.getTargetY();
        float fDistanceSq = Utility.distanceSq(this.posX, this.posY, targetX, targetY);
        if (unitCommand.targetUnit == null || unitCommand.targetUnit.isDead || !unitCommand.targetUnit.isAlive()) {
            advanceWaypoint();
            unitCommand = null;
        }
        if (unitCommand != null && !d(unitCommand.targetUnit, false)) {
            advanceWaypoint();
        }
        if (unitCommand != null) {
            this.targetUnit = unitCommand.targetUnit;
            float transportInteractionRange = getTransportInteractionRange();
            if (fDistanceSq > transportInteractionRange * transportInteractionRange) {
                this.isPathingActive = true;
                this.pathTargetX = targetX;
                this.pathTargetY = targetY;
                if (this.repathCooldown > 90.0f) {
                    this.repathCooldown = 90.0f;
                }
                this.repathDistanceThreshold = 18;
                if (fDistanceSq < 72900.0f) {
                    unitStateTracker.isReset = false;
                    if (this.repathCooldown > 0.0f && this.aU == null) {
                        this.canAttack = true;
                    }
                }
                if (this.transportedBy != null && !this.transportedBy.isAlive()) {
                    unitStateTracker.isReset = false;
                    return;
                }
                return;
            }
            e(unitCommand.targetUnit, false);
            advanceWaypoint();
        }
    }

    /* JADX INFO: renamed from: a */
    private void updateMoveAttackMovePatrolCommand(float f, UnitCommand unitCommand, UnitStateTracker unitStateTracker, boolean z) {
        UnitCommand unitCommandA;
        BaseUnit baseUnitQ;
        float targetX = unitCommand.getTargetX();
        float targetY = unitCommand.getTargetY();
        float fDistanceSq = Utility.distanceSq(this.posX, this.posY, targetX, targetY);
        GameEngine gameEngine = GameEngine.getInstance();
        if (!canExecuteMovementCommands()) {
            boolean z2 = false;
            AttachmentSlotDefinition attachmentSlotDefinitionDn = dn();
            if (attachmentSlotDefinitionDn != null && attachmentSlotDefinitionDn.H) {
                bx();
                z2 = true;
            }
            if (!z2) {
                advanceWaypointWithTransportedUnits();
                unitCommand = null;
            }
        }
        float f2 = 7.0f;
        if (fDistanceSq < 1681.0f) {
            this.waypointDwellTimer += f;
        }
        if (this.waypointDwellTimer > 240.0f) {
            f2 = 16.0f;
        }
        if (this.waypointDwellTimer > 340.0f) {
            f2 = 36.0f;
        }
        if (unitCommand != null && unitCommand.commandType == UnitCommandType.patrol) {
            if (getWaypointCount() != 1) {
                f2 = 20.0f;
                float f3 = 30.0f;
                if (!z || this.lastCollisionTick == gameEngine.currentTick || this.lastCollisionTick == gameEngine.currentTick - 1) {
                    f3 = 70.0f;
                }
                if (fDistanceSq < f3 * f3) {
                    appendWaypointCopy(unitCommand);
                    advanceWaypointWithTransportedUnits();
                    unitCommand = null;
                }
            } else {
                f2 = 30.0f;
                if (!z || this.lastCollisionTick == gameEngine.currentTick || this.lastCollisionTick == gameEngine.currentTick - 1) {
                    f2 = 80.0f;
                }
            }
        }
        if (unitCommand != null) {
            if (fDistanceSq < f2 * f2) {
                if (unitCommand.commandType == UnitCommandType.patrol) {
                    if (getWaypointCount() == 1) {
                    }
                } else if (unitCommand.commandType == UnitCommandType.attackMove) {
                    boolean z3 = false;
                    if (this.attackTarget != null && !this.attackTarget.isDead && a(this.attackTarget, false)) {
                        z3 = true;
                    }
                    if (!z3) {
                        advanceWaypointWithTransportedUnits();
                        unitCommand = null;
                    }
                } else {
                    advanceWaypointWithTransportedUnits();
                    unitCommand = null;
                }
            } else {
                this.isPathingActive = true;
                this.pathTargetX = targetX;
                this.pathTargetY = targetY;
                this.pathTargetRadius = 0;
                if (unitCommand.commandType == UnitCommandType.patrol) {
                    this.isLowPriority = true;
                    clearTransportState();
                }
                // RWX：有路却走不动（被单位堵住、被地形夹住、目标落在不可达的孤点）同样算停滞。
                if (this.rwxStuckSampled) {
                    float sdx = this.posX - this.rwxStuckX;
                    float sdy = this.posY - this.rwxStuckY;
                    if (sdx * sdx + sdy * sdy > 1024.0f) {
                        this.rwxStuckTimer = 0.0f;
                        this.rwxStuckX = this.posX;
                        this.rwxStuckY = this.posY;
                    } else {
                        this.rwxStuckTimer += f;
                        if (this.rwxStuckTimer > RWX_STUCK_LIMIT) {
                            rwxAbandonStuckWaypoint();
                            return;
                        }
                    }
                } else {
                    this.rwxStuckSampled = true;
                    this.rwxStuckX = this.posX;
                    this.rwxStuckY = this.posY;
                }
            }
        }
        if (unitCommand != null) {
            if (unitCommand.commandType == UnitCommandType.attackMove || unitCommand.commandType == UnitCommandType.patrol) {
                if (this.attackTarget != null && !this.attackTarget.isDead && a(this.attackTarget, false)) {
                    updateAttackTargetState(f, this.attackTarget, unitStateTracker, true);
                }
                if (this.transportedBy != null && this.transportedBy.attackTarget != null) {
                    unitStateTracker.isReset = false;
                }
            }
            if (unitCommand.commandType == UnitCommandType.patrol) {
                if (this.attackTarget == null && (baseUnitQ = q(3.0f)) != null && b(baseUnitQ, true)) {
                    if (this.repathCooldown > 90.0f) {
                        this.repathCooldown = 90.0f;
                    }
                    this.isPathingActive = true;
                    this.pathTargetX = baseUnitQ.posX;
                    this.pathTargetY = baseUnitQ.posY;
                    this.pathTargetRadius = 0;
                    this.canAttack = true;
                }
                if (canMove() && gameEngine.currentTick % 10 == this.objectId % 10 && (unitCommandA = RepairBay.a(this, f, 150.0f, true)) != null) {
                    unitCommandA.isRepeating = false;
                    unitCommandA.attackMoveRange = 200.0f;
                    this.isPathingActive = false;
                    clearPathData();
                }
            }
        }
    }

    /* JADX INFO: renamed from: f */
    private void updateBuildCommand(float f, UnitCommand unitCommand, UnitStateTracker unitStateTracker) {
        boolean z;
        float targetX = unitCommand.getTargetX();
        float targetY = unitCommand.getTargetY();
        float fDistanceSq = Utility.distanceSq(this.posX, this.posY, targetX, targetY);
        GameEngine gameEngine = GameEngine.getInstance();
        UnitType unitType = unitCommand.buildUnitType;
        if (unitType == null) {
            a("activeBuildingType==null, removing waypoint");
            advanceWaypoint();
            unitCommand = null;
        }
        if (unitCommand != null) {
            float f2 = f(unitType);
            boolean z2 = false;
            if (f2 <= 30.0f) {
            }
            if (f2 <= 25.0f && this.posZ > 4.0f) {
                z2 = true;
            }
            if (this.transportedBy != null) {
                UnitCommand currentWaypoint = this.transportedBy.getCurrentWaypoint();
                if (currentWaypoint == null || currentWaypoint.commandType != UnitCommandType.build) {
                    unitStateTracker.isReset = false;
                }
                if (currentWaypoint != null && !unitCommand.isSameCommand(currentWaypoint)) {
                    unitStateTracker.isReset = false;
                }
            }
            boolean z3 = !GameViewUtils.a(this.lastAttackTick, 200);
            if (f2 > 800000.0f) {
                z = true;
            } else {
                z = fDistanceSq <= f2 * f2;
            }
            if (!z || z2) {
                if (!canExecuteMovementCommands()) {
                    advanceWaypoint();
                    return;
                }
                this.isPathingActive = true;
                this.pathTargetX = targetX;
                this.pathTargetY = targetY;
                if (f2 > 58.0f) {
                    this.pathTargetRadius = (int) ((f2 - 41.0f) / (gameEngine.tileMap.tileWorldSizeX * 1.414f));
                }
                if (this.repathCooldown > 90.0f) {
                    this.repathCooldown = 90.0f;
                }
                if (this.pathRetryCount > 3) {
                    advanceWaypoint();
                    return;
                }
                return;
            }
            if (!z3) {
                if (!requiresFacingForActions() || Utility.abs(rotateTowardWorldPoint(f, targetX, targetY)) <= 30.0f) {
                    TriggerDebugAction triggerDebugActionA = a(unitCommand, unitCommand.buildUnitType, unitCommand.buildQueueSize, unitCommand.targetX, unitCommand.targetY);
                    BaseUnit baseUnit = null;
                    if (triggerDebugActionA.sourceUnit != null) {
                        baseUnit = triggerDebugActionA.sourceUnit;
                    } else if (triggerDebugActionA.targetUnit != null) {
                        baseUnit = triggerDebugActionA.targetUnit;
                    }
                    if (baseUnit != null) {
                        triggerDebugActionA.action.onTargetSelected(this, baseUnit);
                        if (!canRepairTarget(baseUnit)) {
                            advanceWaypoint();
                        } else if (getDistanceToTarget(baseUnit) > 10000.0f) {
                            baseUnit.r(1.0f);
                            advanceWaypointWithTransportedUnits();
                        } else {
                            unitCommand.resetCommand();
                            unitCommand.commandType = UnitCommandType.repair;
                            unitCommand.targetUnit = baseUnit;
                            clearPathData();
                        }
                        this.lastAttackTick = -9999;
                        return;
                    }
                    if (unitCommand.buildUnitType == null) {
                        GameEngine.log("active.build==null");
                    }
                    if (!triggerDebugActionA.isActive) {
                        advanceWaypoint();
                    }
                }
            }
        }
    }

    /* JADX INFO: renamed from: a */
    private void updateAttackTargetState(float f, BaseUnit baseUnit, UnitStateTracker unitStateTracker, boolean z) {
        UnitBehaviorType unitBehaviorTypeBe = be();
        float f2 = baseUnit.posX;
        float f3 = baseUnit.posY;
        float fDistanceSq = Utility.distanceSq(this.posX, this.posY, f2, f3);
        if (this.transportedBy != null) {
            if (fDistanceSq < 490000.0f) {
                if (fDistanceSq < 48400.0f) {
                    unitStateTracker.isReset = false;
                }
                float fDistanceSq2 = Utility.distanceSq(this.transportedBy.posX, this.transportedBy.posY, f2, f3);
                if (fDistanceSq2 < 48400.0f) {
                    unitStateTracker.isReset = false;
                }
                if (fDistanceSq2 < 270400.0f && useVelocityExtendedRange()) {
                    unitStateTracker.isReset = false;
                }
            }
            if (this.transportedBy.attackTarget == baseUnit) {
                unitStateTracker.isReset = false;
            }
            if (unitStateTracker.isReset) {
                this.transportAttackAssistTimer = 0.0f;
            } else {
                this.transportAttackAssistTimer += f;
            }
        } else {
            this.transportAttackAssistTimer = 500.0f;
        }
        float engagementRange = getEngagementRange(baseUnit);
        boolean z2 = true;
        if (fDistanceSq < engagementRange * engagementRange) {
            if (this.attackTarget != baseUnit) {
                if (PathfindingUtils.a(this, baseUnit)) {
                    this.attackTarget = baseUnit;
                    this.turretRotation = 10.0f;
                    M(-1);
                }
            } else {
                this.turretRotation = 10.0f;
            }
            float f4 = engagementRange;
            if (!E()) {
                f4 -= 1.0f;
                if (useVelocityExtendedRange()) {
                    f4 -= 2.0f;
                }
                if (e(0) > 5.0f) {
                    f4 -= 3.0f;
                }
            }
            if (fDistanceSq < f4 * f4 && be() != UnitBehaviorType.bomber) {
                if (baseUnit == null) {
                    z2 = false;
                } else if (canEngageTargetNow(baseUnit)) {
                    z2 = false;
                    if (z) {
                        this.isPathingActive = false;
                    }
                } else if (!canEngageTargetWithMovement(baseUnit)) {
                    z2 = false;
                }
            }
        }
        if (z2) {
            this.isPathingActive = true;
            this.pathTargetX = f2;
            this.pathTargetY = f3;
            this.pathTargetRadius = 0;
            if (unitBehaviorTypeBe == UnitBehaviorType.bomber) {
                navigateToPosition(fDistanceSq, f2, f3);
            }
            this.pathTargetRadius = getPathingTargetRadiusTiles(baseUnit);
            if (this.repathCooldown > 90.0f) {
                this.repathCooldown = 90.0f;
            }
            if (fDistanceSq < 810000.0f) {
                if (isAirborne() || useVelocityExtendedRange()) {
                    this.canAttack = true;
                }
                if (!unitStateTracker.isReset && this.transportAttackAssistTimer < 120.0f) {
                    this.repathCooldown = 0.1f;
                    this.canAttack = true;
                }
            }
        }
    }

    /* JADX INFO: renamed from: g */
    private void updateAttackCommand(float f, UnitCommand unitCommand, UnitStateTracker unitStateTracker) {
        GameEngine gameEngine = GameEngine.getInstance();
        if (be() == UnitBehaviorType.bomber) {
            if (unitCommand != null && ((unitCommand.targetUnit == null || unitCommand.targetUnit.isDead || unitCommand.targetUnit.team == this.team) && !this.isTargetSearchPending)) {
                if (this.attackTarget != null && this.attackTarget.isDead) {
                    this.attackTarget = null;
                }
                queryNearbyCollisionUnits(gameEngine, f, getTargetSearchRange(true) + 200.0f);
                if (this.attackTarget != null) {
                    unitCommand.targetUnit = this.attackTarget;
                    clearTransportState();
                    clearPathData();
                } else {
                    this.isTargetSearchPending = true;
                    this.isSecondaryRecharging = true;
                }
            }
            if (unitCommand != null && ((unitCommand.targetUnit == null || unitCommand.targetUnit.isDead || unitCommand.targetUnit.team == this.team) && (unitCommand.targetUnit == null || !this.isSecondaryRecharging))) {
                advanceWaypoint();
                unitCommand = null;
            }
        } else if (unitCommand.targetUnit == null || unitCommand.targetUnit.isDead || unitCommand.targetUnit.team == this.team) {
            boolean z = true;
            if (getWaypointCount() > 1) {
                z = false;
            }
            unitCommand.targetUnit = null;
            if (z) {
                if (this.attackTarget != null && this.attackTarget.isDead) {
                    this.attackTarget = null;
                }
                queryNearbyCollisionUnits(gameEngine, f, getTargetSearchRange(true));
                if (this.attackTarget != null) {
                    unitCommand.targetUnit = this.attackTarget;
                    clearTransportState();
                    clearPathData();
                }
            }
            if (unitCommand.targetUnit == null) {
                advanceWaypoint();
                unitCommand = null;
            }
        }
        if (unitCommand != null && unitCommand.targetUnit != null && !unitCommand.targetUnit.isDead && !unitCommand.targetUnit.isVisibleToEnemies() && this.team.c(unitCommand.targetUnit.team) && !PathfindingUtils.b(this, unitCommand.targetUnit)) {
            advanceWaypoint();
            return;
        }
        if (unitCommand != null && !canExecuteMovementCommands() && !canAttack()) {
            advanceWaypoint();
            unitCommand = null;
        }
        if (unitCommand != null) {
            updateAttackTargetState(f, unitCommand.targetUnit, unitStateTracker, false);
        }
    }

    /* JADX INFO: renamed from: h */
    private void updateLoadIntoCommand(float f, UnitCommand unitCommand, UnitStateTracker unitStateTracker) {
        float targetX = unitCommand.getTargetX();
        float targetY = unitCommand.getTargetY();
        float fDistanceSq = Utility.distanceSq(this.posX, this.posY, targetX, targetY);
        if (unitCommand.targetUnit == null || unitCommand.targetUnit.isDead) {
            advanceWaypoint();
            unitCommand = null;
        }
        if (unitCommand != null && !unitCommand.targetUnit.d(this, false)) {
            advanceWaypoint();
        }
        if (unitCommand != null) {
            BaseUnit baseUnit = unitCommand.targetUnit;
            this.targetUnit = baseUnit;
            boolean z = false;
            if (baseUnit.bI()) {
                float transportInteractionRange = baseUnit.getTransportInteractionRange() + 10.0f;
                if (fDistanceSq < transportInteractionRange * transportInteractionRange) {
                    this.waypointDwellTimer += f;
                }
                if (this.waypointDwellTimer > 240.0f) {
                    z = true;
                }
                float f2 = 21.0f;
                if (baseUnit.cc().a()) {
                    f2 = 11.0f;
                }
                if (this.blockedRecoveryTime > 0.0f) {
                    f2 = baseUnit.radius + 31.0f;
                }
                if (fDistanceSq < f2 * f2) {
                    z = true;
                }
            } else {
                float transportInteractionRange = baseUnit.getTransportInteractionRange();
                if (fDistanceSq < transportInteractionRange * transportInteractionRange) {
                    z = true;
                }
            }
            if (!z) {
                this.isPathingActive = true;
                this.pathTargetX = targetX;
                this.pathTargetY = targetY;
                if (this.repathCooldown > 90.0f) {
                    this.repathCooldown = 90.0f;
                }
                this.repathDistanceThreshold = 18;
                if (fDistanceSq < 48400.0f) {
                    unitStateTracker.isReset = false;
                    if (this.repathCooldown > 0.0f && this.aU == null) {
                        this.canAttack = true;
                    }
                }
                if (this.transportedBy != null && !this.transportedBy.isAlive()) {
                    unitStateTracker.isReset = false;
                }
            }
            if (z) {
                unitCommand.targetUnit.e(this, false);
                advanceWaypoint();
            }
        }
    }

    /* JADX INFO: renamed from: a_ */
    public float getRepairProgressRate(BaseUnit baseUnit) {
        float fD = baseUnit.r().D();
        if (baseUnit.getUpgradeLevel() == 2) {
            fD *= 0.5f;
        }
        if (baseUnit.getUpgradeLevel() == 3) {
            fD *= 0.25f;
        }
        return fD * getDistanceToTarget(baseUnit);
    }

    /* JADX INFO: renamed from: f */
    public float getReclaimRate(BaseUnit baseUnit) {
        return 0.001f * 5.1f;
    }

    /* JADX INFO: renamed from: g */
    public UnitPrice getRepairOrReclaimPrice(BaseUnit baseUnit) {
        if (baseUnit.additionalCost != null) {
            return baseUnit.additionalCost;
        }
        return baseUnit.r().B();
    }

    /* JADX INFO: renamed from: i */
    private void updateRepairOrReclaimCommand(float f, UnitCommand unitCommand, UnitStateTracker unitStateTracker) {
        int iU;
        boolean zX;
        GameEngine gameEngine = GameEngine.getInstance();
        boolean z = false;
        boolean z2 = false;
        if (unitCommand == null) {
            return;
        }
        float targetX = unitCommand.getTargetX();
        float targetY = unitCommand.getTargetY();
        float fDistanceSq = Utility.distanceSq(this.posX, this.posY, targetX, targetY);
        if (unitCommand != null && unitCommand.commandType == UnitCommandType.reclaim && unitCommand.targetUnit != null && unitCommand.targetUnit.getResourceRate() > 0.0f) {
            z2 = true;
        }
        if (unitCommand != null && (unitCommand.targetUnit == null || unitCommand.targetUnit.isDead || unitCommand.targetUnit.transportContainer != null)) {
            if (z2) {
                z = true;
            } else {
                advanceWaypointWithTransportedUnits();
                unitCommand = null;
            }
        }
        if (unitCommand != null && !z && z2 && unitCommand.targetUnit != null) {
            boolean z3 = true;
            if (this.lastReclaimSearchTick < gameEngine.gameTimeMillis - 100) {
                z3 = false;
            }
            if (!g(unitCommand.targetUnit, z3)) {
                z = true;
            }
            if (!z) {
                this.lastReclaimSearchTick = gameEngine.gameTimeMillis;
            }
        }
        if (unitCommand != null && z) {
            AnimationSet unitTypeId = null;
            if (unitCommand.targetUnit != null) {
                unitTypeId = unitCommand.targetUnit.getSimilarResourcesTag();
            }
            BaseUnit baseUnitA = a(this, unitCommand.targetUnit.posX, unitCommand.targetUnit.posY, getReclaimSearchRange(), unitTypeId);
            if (baseUnitA != null) {
                unitCommand.targetUnit = baseUnitA;
                targetX = unitCommand.getTargetX();
                targetY = unitCommand.getTargetY();
                fDistanceSq = Utility.distanceSq(this.posX, this.posY, targetX, targetY);
                clearTransportState();
            } else {
                advanceWaypointWithTransportedUnits();
                unitCommand = null;
            }
        }
        if (unitCommand != null) {
            if (unitCommand.commandType == UnitCommandType.repair) {
                if (!canRepairTarget(unitCommand.targetUnit)) {
                    advanceWaypoint();
                    unitCommand = null;
                }
            } else if (!z2 && !canReclaimTarget(unitCommand.targetUnit)) {
                advanceWaypoint();
                unitCommand = null;
            }
        }
        if (unitCommand != null && unitCommand.commandType == UnitCommandType.repair && unitCommand.targetUnit != null && unitCommand.targetUnit.currentHealth >= unitCommand.targetUnit.maxHealth && unitCommand.targetUnit.buildProgress >= 1.0f) {
            advanceWaypointWithTransportedUnits();
            unitCommand = null;
        }
        if (unitCommand != null && unitCommand.targetUnit == this) {
            advanceWaypoint();
            unitCommand = null;
        }
        if (unitCommand != null && unitCommand != null && unitCommand.targetUnit != null && unitCommand.targetUnit.getResourceRate() != 0.0f) {
            boolean z4 = false;
            if (unitCommand.commandType == UnitCommandType.repair) {
                z4 = true;
            }
            if (z4) {
                advanceWaypoint();
                unitCommand = null;
            }
        }
        if (unitCommand != null && unitCommand.commandType == UnitCommandType.reclaim && unitCommand.targetUnit.team != this.team && unitCommand.targetUnit.getResourceRate() == 0.0f) {
            boolean z5 = true;
            if (gameEngine.isSinglePlayerGame() && this.team.d(unitCommand.targetUnit.team)) {
                z5 = false;
            }
            if (z5) {
                advanceWaypoint();
                unitCommand = null;
            }
        }
        if (unitCommand != null) {
            if (unitCommand.commandType == UnitCommandType.reclaim) {
                iU = v(unitCommand.targetUnit);
                zX = w(unitCommand.targetUnit);
            } else {
                iU = getRepairRange(unitCommand.targetUnit);
                zX = x(unitCommand.targetUnit);
            }
            if (this.transportedBy != null) {
                int i = iU + 80;
                if (Utility.distanceSq(this.transportedBy.posX, this.transportedBy.posY, targetX, targetY) < i * i) {
                    unitStateTracker.isReset = false;
                }
                UnitCommand currentWaypoint = this.transportedBy.getCurrentWaypoint();
                if (currentWaypoint == null) {
                    unitStateTracker.isReset = false;
                }
                if (currentWaypoint != null && !unitCommand.isSameCommand(currentWaypoint)) {
                    unitStateTracker.isReset = false;
                }
            }
            float f2 = iU;
            if (this.isActing) {
                f2 += 5.0f;
            }
            if (iU <= 30) {
            }
            if (fDistanceSq > f2 * f2) {
                if (!canExecuteMovementCommands() || unitCommand.attackMoveRange == 0.0f) {
                    advanceWaypoint();
                    return;
                }
                boolean z6 = false;
                if (unitCommand.attackMoveRange >= 0.0f) {
                    if (unitCommand.attackMoveRange < Utility.fastSquareRootInt((int) fDistanceSq) - f2) {
                        z6 = true;
                    }
                }
                if (z6) {
                    advanceWaypoint();
                    return;
                }
                this.isPathingActive = true;
                this.pathTargetX = targetX;
                this.pathTargetY = targetY;
                if (iU > 58) {
                    this.pathTargetRadius = (int) ((iU - 41.0f) / (gameEngine.tileMap.tileWorldSizeX * 1.414f));
                } else {
                    this.pathTargetRadius = 0;
                }
                if (iU < 30 || zX) {
                    if (fDistanceSq < 841.0f) {
                        this.canAttack = true;
                    }
                    float f3 = iU + 14;
                    if (fDistanceSq < f3 * f3 && this.repathCooldown > 0.0f && this.aU == null) {
                        this.canAttack = true;
                    }
                }
                this.repathDistanceThreshold = this.pathTargetRadius;
                if (this.repathCooldown > 90.0f) {
                    this.repathCooldown = 90.0f;
                    return;
                }
                return;
            }
            int defaultTurretIndex = getDefaultTurretIndex();
            if (defaultTurretIndex == -1) {
                defaultTurretIndex = 0;
            }
            float fRotateTowardWorldPoint = 0.0f;
            if (requiresFacingForActions()) {
                fRotateTowardWorldPoint = rotateTowardWorldPoint(f, targetX, targetY);
            }
            boolean z7 = false;
            if (Utility.abs(fRotateTowardWorldPoint) < 30.0f || !requiresFacingForActions()) {
                this.isActing = true;
                unitStateTracker.stateFlag1 = true;
                UnitMovementData unitMovementData = this.movementLevels[defaultTurretIndex];
                if (unitMovementData.speed < e(defaultTurretIndex)) {
                    unitMovementData.speed += f;
                } else {
                    unitMovementData.speed = e(defaultTurretIndex);
                    z7 = true;
                }
            }
            if (z7) {
                BaseUnit baseUnit = unitCommand.targetUnit;
                if (unitCommand.commandType != UnitCommandType.reclaim) {
                    if (baseUnit.buildProgress < 1.0f) {
                        updateUnitMovement();
                        float repairProgressRate = getRepairProgressRate(baseUnit);
                        float f4 = repairProgressRate * f;
                        boolean z8 = false;
                        boolean z9 = false;
                        UnitPrice repairOrReclaimPrice = getRepairOrReclaimPrice(baseUnit);
                        if (repairOrReclaimPrice != null) {
                            if (baseUnit.buildProgress + f4 > 1.0f) {
                                f4 = 1.0f - baseUnit.buildProgress;
                                z8 = true;
                            }
                            double d = (baseUnit.buildProgress + f4) - baseUnit.paidBuildProgress;
                            double d2 = 0.0d;
                            if (z8) {
                                d2 = 1.0f - baseUnit.paidBuildProgress;
                            } else if (d >= 0.0010000000474974513d) {
                                d2 = ((double) ((int) (d / 0.0010000000474974513d))) * 0.0010000000474974513d;
                            }
                            boolean z10 = false;
                            if (d2 > 0.0d && this.team.resourceShortageTracker.a(repairOrReclaimPrice)) {
                                z10 = true;
                            }
                            if (!z10 && (d2 <= 0.0d || repairOrReclaimPrice.c(this, d2))) {
                                baseUnit.paidBuildProgress = (float) (((double) baseUnit.paidBuildProgress) + d2);
                            } else {
                                if (!z10) {
                                    this.team.resourceShortageTracker.a(repairOrReclaimPrice, this, d2);
                                }
                                f4 = 0.0f;
                                z8 = false;
                                z9 = true;
                            }
                        }
                        if (!z9) {
                            a(baseUnit, f, defaultTurretIndex);
                            float f5 = baseUnit.buildProgress + f4;
                            if (z8) {
                                f5 = 1.0f;
                            }
                            baseUnit.r(f5);
                            if (f5 >= 1.0f && repairProgressRate < 0.3d && baseUnit.team == gameEngine.playerTeam) {
                                gameEngine.gameUI.warLogDisplay.logUnitCreated(baseUnit);
                            }
                            this.aO = false;
                            return;
                        }
                        this.aO = true;
                        return;
                    }
                    a(baseUnit, f, defaultTurretIndex);
                    baseUnit.currentHealth += c(baseUnit) * f;
                    if (baseUnit.currentHealth > baseUnit.maxHealth) {
                        baseUnit.currentHealth = baseUnit.maxHealth;
                        advanceWaypoint();
                    }
                    this.aO = false;
                    return;
                }
                b(baseUnit, f, defaultTurretIndex);
                this.aO = false;
                updateUnitMovement();
                boolean z11 = false;
                boolean zY = y(baseUnit);
                float fZ = calculateUnitSpeed(baseUnit);
                boolean z12 = unitCommand.targetUnit.getResourceRate() > 0.0f;
                UnitPrice repairOrReclaimPrice2 = getRepairOrReclaimPrice(baseUnit);
                if (z12 || repairOrReclaimPrice2 != null) {
                }
                boolean z13 = false;
                if (!z12 && this.wayPointTimer < 100.0f && !z12) {
                    if (baseUnit.buildProgress < 0.5d) {
                        if (repairOrReclaimPrice2 == null) {
                            z13 = true;
                        }
                    } else if (baseUnit.currentHealth / baseUnit.maxHealth < 0.5d) {
                        z13 = true;
                    }
                }
                if (!z13) {
                    if (baseUnit.buildProgress < 1.0f) {
                        float reclaimRate = getReclaimRate(baseUnit) * f;
                        if (reclaimRate >= baseUnit.buildProgress) {
                            reclaimRate = baseUnit.buildProgress;
                            baseUnit.buildProgress = 0.0f;
                        } else {
                            baseUnit.buildProgress -= reclaimRate;
                        }
                        baseUnit.paidBuildProgress = baseUnit.buildProgress;
                        if (repairOrReclaimPrice2 != null) {
                            repairOrReclaimPrice2.a((BaseUnit) this, reclaimRate, true);
                        }
                        if (baseUnit.buildProgress <= 0.0f) {
                            z11 = true;
                        }
                    } else {
                        float f6 = fZ * f;
                        if (f6 >= baseUnit.currentHealth) {
                            f6 = baseUnit.currentHealth;
                            baseUnit.currentHealth = -1.0f;
                        } else {
                            baseUnit.currentHealth -= f6;
                        }
                        baseUnit.damageEffectDurationTimer = 1000.0f;
                        if (zY) {
                            float f7 = f6 / baseUnit.maxHealth;
                            UnitPrice unitDescription = baseUnit.getBuildPrice();
                            UnitPrice unitDisplayName = baseUnit.getReclaimPrice();
                            if (unitDisplayName != null) {
                                unitDescription = unitDisplayName;
                            }
                            if (z12 || repairOrReclaimPrice2 != null) {
                            }
                            if (unitDescription.a() > 0) {
                                this.pendingCredits += f7 * unitDescription.a();
                                if (this.pendingCredits > 1.0f) {
                                    this.team.credits += (double) ((int) this.pendingCredits);
                                    this.pendingCredits -= (int) this.pendingCredits;
                                }
                                unitDescription.a((BaseUnit) this, f7, false);
                            } else {
                                unitDescription.a((BaseUnit) this, f7, true);
                            }
                        }
                        if (baseUnit.currentHealth <= 0.0f) {
                            z11 = true;
                        }
                    }
                }
                if (z11 && !baseUnit.isDead) {
                    if (!zY) {
                        UnitPrice unitDisplayName2 = baseUnit.getReclaimPrice();
                        if (unitDisplayName2 != null) {
                            GameEngine.log("refund: " + unitDisplayName2.a(false, true, 10, true));
                            unitDisplayName2.a((BaseUnit) this, 1.0d, true);
                        } else {
                            UnitPrice unitDescription2 = baseUnit.getBuildPrice();
                            if (baseUnit.price != null) {
                                unitDescription2 = baseUnit.price;
                                GameEngine.log("refund==null overridePriceBuildCost: " + unitDescription2.a(false, true, 10, true));
                            }
                            unitDescription2.a((BaseUnit) this, 0.800000011920929d, true);
                            if (baseUnit.buildProgress >= 1.0f && repairOrReclaimPrice2 != null) {
                                repairOrReclaimPrice2.a((BaseUnit) this, 0.800000011920929d, true);
                            }
                        }
                    }
                    baseUnit.isDead = true;
                    baseUnit.unitCreationTime = gameEngine.gameTimeMillis;
                    baseUnit.removeFromGame();
                    if ((baseUnit instanceof OrderableUnit) && baseUnit.bI()) {
                        gameEngine.pathfindingEngine.a((OrderableUnit) baseUnit);
                    }
                }
            }
        }
    }

    /* JADX INFO: renamed from: k */
    public void updateMovementLogic(float f) {
        GameEngine gameEngine = GameEngine.getInstance();
        if (this.targetUnit != null) {
            this.targetUnit = null;
        }
        if (this.attackTargetUnit != null) {
            this.bS = Utility.moveTowardsZero(this.bS, f);
            this.targetUnit = this.attackTargetUnit;
            if (this.bS == 0.0f) {
                this.attackTargetUnit = null;
            }
        }
        if (this.repathCooldown != 0.0f) {
            this.repathCooldown = Utility.moveTowardsZero(this.repathCooldown, f);
        }
        if (this.rotation != 0.0f) {
            this.moveThrottle = Utility.moveTowardsZero(this.moveThrottle, f);
        }
        UnitCommand currentWaypoint = getCurrentWaypoint();
        // Retain the order, but do not interact with its target while that group is pending.
        if (currentWaypoint != null && currentWaypoint.targetUnit != null && currentWaypoint.targetUnit.isTransferUnavailable()) {
            currentWaypoint = null;
        }
        this.canAttack = false;
        boolean z = this.isPathingActive;
        this.isPathingActive = false;
        this.isLowPriority = false;
        this.repathDistanceThreshold = 150;
        if (currentWaypoint != null && currentWaypoint.maxWayPointSurvivingTime > 0.0f && currentWaypoint.maxWayPointSurvivingTime < this.wayPointTimer) {
            advanceWaypointWithTransportedUnits();
            currentWaypoint = null;
        }
        UnitStateTracker unitStateTracker = aP;
        unitStateTracker.a();
        if (currentWaypoint != null) {
            this.wayPointTimer += f;
            UnitCommandType unitCommandType = currentWaypoint.commandType;
            if (unitCommandType == UnitCommandType.move || unitCommandType == UnitCommandType.attackMove || unitCommandType == UnitCommandType.patrol) {
                updateMoveAttackMovePatrolCommand(f, currentWaypoint, unitStateTracker, z);
            } else if (unitCommandType == UnitCommandType.attack) {
                updateAttackCommand(f, currentWaypoint, unitStateTracker);
            } else if (unitCommandType == UnitCommandType.build) {
                updateBuildCommand(f, currentWaypoint, unitStateTracker);
            } else if (unitCommandType == UnitCommandType.repair || unitCommandType == UnitCommandType.reclaim) {
                updateRepairOrReclaimCommand(f, currentWaypoint, unitStateTracker);
            } else if (unitCommandType == UnitCommandType.loadInto) {
                updateLoadIntoCommand(f, currentWaypoint, unitStateTracker);
            } else if (unitCommandType == UnitCommandType.loadUp) {
                updateLoadUpCommand(f, currentWaypoint, unitStateTracker);
            } else if (unitCommandType == UnitCommandType.touchTarget) {
                updateTouchTargetCommand(f, currentWaypoint, unitStateTracker);
            } else if (unitCommandType == UnitCommandType.guard || unitCommandType == UnitCommandType.guardAt || unitCommandType == UnitCommandType.follow) {
                updateGuardOrFollowCommand(f, currentWaypoint, unitStateTracker);
            } else if (unitCommandType == UnitCommandType.triggerAction || unitCommandType == UnitCommandType.triggerActionWhenInRange) {
                updateTriggerActionCommand(f, currentWaypoint, unitStateTracker);
            } else if (unitCommandType == UnitCommandType.setPassiveTarget) {
                updateSetPassiveTargetCommand(f, currentWaypoint, unitStateTracker);
            }
            if (currentWaypoint != getCurrentWaypoint()) {
                currentWaypoint = null;
            }
        }
        this.isActing = unitStateTracker.stateFlag1;
        if (currentWaypoint != null && currentWaypoint.isRepeating && this.waypointCount > 1) {
            boolean z2 = true;
            UnitCommand waypointAt = getWaypointAt(1);
            if (waypointAt != null && (waypointAt.commandType == UnitCommandType.guard || waypointAt.commandType == UnitCommandType.patrol)) {
                z2 = false;
            }
            if (z2) {
                advanceWaypoint();
                currentWaypoint = null;
            }
        }
        if (currentWaypoint == null) {
            this.isPathingActive = false;
        }
        if (this.isPathingActive) {
            AttachmentSlotDefinition attachmentSlotDefinitionDn = dn();
            if (attachmentSlotDefinitionDn != null && attachmentSlotDefinitionDn.H) {
                bx();
            }
        } else if (this.pathRetryCount != 0) {
            this.pathRetryCount = (byte) 0;
        }
        updateAttackTargeting(gameEngine, f);
        updateMovementStateFromCommand(gameEngine, f, currentWaypoint, unitStateTracker);
    }

    /* JADX INFO: renamed from: a */
    private void navigateToPosition(float f, float f2, float f3) {
        if (this.navigationAngle < -900.0f) {
            this.navigationAngle = Utility.getAngleBetweenPoints(this.posX, this.posY, f2, f3);
        }
        if (f < 10000.0f && isSecondaryBarRecharging()) {
            this.isSecondaryRecharging = true;
        }
        if (this.isSecondaryRecharging) {
            if (this.currentEnergy < ((double) bd()) * 0.6d || (f < 40000.0f && this.currentEnergy < bd())) {
                this.pathTargetX += Utility.fastCos(this.navigationAngle + 180.0f) * 600.0f;
                this.pathTargetY += Utility.fastSin(this.navigationAngle + 180.0f) * 600.0f;
            } else {
                this.isSecondaryRecharging = false;
                this.navigationAngle = -999.0f;
                clearPathData();
            }
        }
    }

    /* JADX INFO: renamed from: a */
    private void updateTransportMovementState(float f, PositionData positionData, UnitStateTracker unitStateTracker, UnitCommand unitCommand) {
        PositionData pathPositionAt;
        PositionData pathPositionAt2;
        float f2;
        float f3;
        float f4;
        GameEngine gameEngine = GameEngine.getInstance();
        OrderableUnit orderableUnit = this.transportedBy;
        float f5 = orderableUnit.posX + this.transportOffsetX;
        float f6 = orderableUnit.posY + this.transportOffsetY;
        int i = gameEngine.gameTimeMillis - orderableUnit.lastTransportPathUpdateTick;
        float fDistanceSq = Utility.distanceSq(this.posX, this.posY, f5, f6);
        if (i > 300 || this.blockedRecoveryTime > 1.0f) {
            this.transportRecoveryTime += f;
        }
        boolean z = false;
        if (this.transportRecoveryTime > 300.0f) {
            z = true;
        }
        if (i > 300 && fDistanceSq > 250000.0f) {
            z = true;
        }
        if (this.blockedRecoveryTime > 1.0f) {
            if (this.moveThrottle != 0.0f) {
                z = true;
            }
            if (this.transportRecoveryTime > 10.0f) {
                z = true;
            }
        }
        if (z) {
            this.moveThrottle = 90.0f;
        }
        if (this.moveThrottle == 0.0f) {
            clearPathData();
            unitStateTracker.stateValue1 = f5;
            unitStateTracker.stateValue2 = f6;
            PositionData pathPositionAt3 = null;
            if (i < 3000 && 0 == 0 && orderableUnit.totalPathPositions > 2 && orderableUnit.totalPathPositions - orderableUnit.activePathCount <= 2) {
                pathPositionAt3 = orderableUnit.getPathPositionAt(2);
            }
            if (i < 1500 && pathPositionAt3 == null && orderableUnit.totalPathPositions > 0 && orderableUnit.activePathCount + 0 >= orderableUnit.totalPathPositions) {
                PositionData pathPositionAt4 = orderableUnit.getPathPositionAt(0);
                pathPositionAt3 = aW;
                float angleBetweenPoints = Utility.getAngleBetweenPoints(orderableUnit.posX, orderableUnit.posY, pathPositionAt4.posX, pathPositionAt4.posY);
                float f7 = 80.0f;
                if (i > 300) {
                    f7 = 80.0f - ((i - 300) * 0.06666667f);
                }
                pathPositionAt3.posX = orderableUnit.posX + (Utility.fastCos(angleBetweenPoints) * f7);
                pathPositionAt3.posY = orderableUnit.posY + (Utility.fastSin(angleBetweenPoints) * f7);
            }
            if (pathPositionAt3 != null) {
                unitStateTracker.stateFlag3 = true;
                unitStateTracker.stateValue1 = pathPositionAt3.posX + this.transportOffsetX;
                unitStateTracker.stateValue2 = pathPositionAt3.posY + this.transportOffsetY;
            } else if (orderableUnit.totalPathPositions >= 2 && orderableUnit.activePathCount >= 1) {
                if (orderableUnit.activePathCount >= 2) {
                    pathPositionAt = orderableUnit.getPathPositionAt(0);
                    pathPositionAt2 = orderableUnit.getPathPositionAt(1);
                } else {
                    pathPositionAt = orderableUnit.getPathPositionAt(0);
                    pathPositionAt2 = orderableUnit.getPathPositionAt(0);
                }
                if (pathPositionAt != null && pathPositionAt2 != null) {
                    float fDistanceInt = 1.0f - ((Utility.distanceInt(orderableUnit.posX, orderableUnit.posY, pathPositionAt.posX, pathPositionAt.posY) - 15.0f) * 0.05f);
                    if (fDistanceInt > 2.0f) {
                        fDistanceInt = 2.0f;
                    }
                    if (fDistanceInt < 0.0f) {
                        fDistanceInt = 0.0f;
                    }
                    if (fDistanceInt > 1.0f) {
                        if (orderableUnit.activePathCount >= 3) {
                            PositionData pathPositionAt5 = orderableUnit.getPathPositionAt(2);
                            float f8 = pathPositionAt2.posX - pathPositionAt.posX;
                            float f9 = pathPositionAt2.posY - pathPositionAt.posY;
                            f2 = f8 + ((pathPositionAt5.posX - pathPositionAt2.posX) * (fDistanceInt - 1.0f));
                            f3 = f9 + ((pathPositionAt5.posY - pathPositionAt2.posY) * (fDistanceInt - 1.0f));
                        } else {
                            f2 = pathPositionAt2.posX - pathPositionAt.posX;
                            f3 = pathPositionAt2.posY - pathPositionAt.posY;
                        }
                    } else {
                        f2 = (pathPositionAt2.posX - pathPositionAt.posX) * fDistanceInt;
                        f3 = (pathPositionAt2.posY - pathPositionAt.posY) * fDistanceInt;
                    }
                    float f10 = pathPositionAt.posX + this.transportOffsetX + f2;
                    float f11 = pathPositionAt.posY + this.transportOffsetY + f3;
                    unitStateTracker.stateValue1 = f10;
                    unitStateTracker.stateValue2 = f11;
                }
            }
            float f12 = 45.0f;
            if (this.blockedRecoveryTime <= 1.0f) {
                f12 = 60.0f;
            } else if (i < 500 && this.blockedRecoveryTime <= 1.0f) {
                f12 = 110.0f;
            }
            if (fDistanceSq < f12 * f12) {
                this.transportRecoveryTime = 0.0f;
            }
            boolean z2 = false;
            UnitCommand currentWaypoint = orderableUnit.getCurrentWaypoint();
            if (currentWaypoint == null || unitCommand != null) {
            }
            if (currentWaypoint == null || 0 != 0) {
                this.transportRecoveryDelay += f;
                boolean z3 = false;
                if (unitCommand != null && (unitCommand.commandType == UnitCommandType.move || unitCommand.commandType == UnitCommandType.attackMove || unitCommand.commandType == UnitCommandType.patrol)) {
                    z3 = true;
                }
                if (z3 && this.transportRecoveryDelay > 600.0f) {
                    f4 = 260.0f;
                } else if (z3 && this.transportRecoveryDelay > 360.0f) {
                    f4 = 140.0f;
                } else if (z3 && this.transportRecoveryDelay > 180.0f) {
                    f4 = 70.0f;
                } else if (z3 && this.transportRecoveryDelay > 120.0f) {
                    f4 = 50.0f;
                } else {
                    f4 = 16.0f;
                }
                if (fDistanceSq < f4 * f4) {
                    z2 = true;
                }
                if (0 != 0) {
                    z2 = true;
                }
            }
            if (z2) {
                boolean z4 = false;
                if (currentWaypoint == null) {
                    z4 = true;
                }
                if (0 != 0) {
                    z4 = true;
                }
                if (z4 && Utility.abs(faceTowardPosition(f, this.formationSlotAngle)) < 3.0f && unitCommand != null) {
                    if (unitCommand.commandType == UnitCommandType.move || unitCommand.commandType == UnitCommandType.attackMove) {
                        advanceWaypoint();
                        if (orderableUnit != null) {
                            boolean z5 = false;
                            UnitCommand currentWaypoint2 = getCurrentWaypoint();
                            UnitCommand currentWaypoint3 = orderableUnit.getCurrentWaypoint();
                            if (currentWaypoint2 != null && currentWaypoint3 != null && currentWaypoint2.isSameCommand(currentWaypoint3)) {
                                z5 = true;
                            }
                            if (!z5) {
                                setTransportParent((OrderableUnit) null);
                                return;
                            }
                            return;
                        }
                        return;
                    }
                    return;
                }
                return;
            }
            if (0 == 0) {
                unitStateTracker.stateFlag2 = true;
                return;
            }
            return;
        }
        PositionData pathPositionAt6 = null;
        if (0 == 0 && orderableUnit.totalPathPositions > 2 && 8 < orderableUnit.activePathCount) {
            pathPositionAt6 = orderableUnit.getPathPositionAt(8);
        }
        if (pathPositionAt6 == null) {
            pathPositionAt6 = aW;
            pathPositionAt6.posX = orderableUnit.posX;
            pathPositionAt6.posY = orderableUnit.posY;
        }
        float fDistanceSq2 = Utility.distanceSq(this.posX, this.posY, pathPositionAt6.posX, pathPositionAt6.posY);
        float f13 = this.radius + orderableUnit.radius + 15.0f;
        float f14 = this.radius + orderableUnit.radius + 100.0f;
        if (fDistanceSq2 < f13 * f13) {
            this.transportRecoveryTime = 0.0f;
            this.moveThrottle = 0.0f;
        } else if (fDistanceSq2 < f14 * f14) {
        }
        if (this.aU == null && positionData != null && ((Utility.abs(this.lastPathTargetX - pathPositionAt6.posX) > 300.0f || Utility.abs(this.lastPathTargetY - pathPositionAt6.posY) > 300.0f) && this.repathCooldown > 30.0f)) {
            this.repathCooldown = 30.0f;
        }
        if (this.repathCooldown == 0.0f && this.aU == null) {
            this.repathCooldown = 700.0f;
            requestPathToTarget(pathPositionAt6.posX, pathPositionAt6.posY, 0, false, false);
        }
        if (positionData != null) {
            unitStateTracker.stateValue1 = positionData.posX;
            unitStateTracker.stateValue2 = positionData.posY;
            if (0 == 0) {
                unitStateTracker.stateFlag2 = true;
            }
        }
    }

    /* JADX INFO: renamed from: a */
    private void updateMovementStateFromCommand(GameEngine gameEngine, float f, UnitCommand unitCommand, UnitStateTracker unitStateTracker) {
        UnitCommand currentWaypoint;
        boolean zI = I();
        if (this.aU != null) {
            applyPendingPathResult(gameEngine);
        }
        if (this.transportedBy != null && (this.transportedBy.isDead || !this.transportedBy.isAlive())) {
            setTransportParent((OrderableUnit) null);
        }
        if (this.isPathingActive) {
            PositionData currentPathPosition = getCurrentPathPosition();
            UnitCommand currentWaypoint2 = getCurrentWaypoint();
            if (currentWaypoint2 == null) {
                unitStateTracker.isReset = false;
            }
            if (L) {
                unitStateTracker.isReset = false;
            }
            if (this.isTransportAttached && this.transportedUnitCount > 0 && aG()) {
                this.lastTransportPathUpdateTick = gameEngine.gameTimeMillis;
            }
            if (currentWaypoint2 != null && this.transportedBy != null && unitStateTracker.isReset && (currentWaypoint = this.transportedBy.getCurrentWaypoint()) != null && !currentWaypoint.isSameCommand(currentWaypoint2)) {
                unitStateTracker.isReset = false;
            }
            if (this.transportedBy != null && unitStateTracker.isReset) {
                updateTransportMovementState(f, currentPathPosition, unitStateTracker, unitCommand);
            } else if (this.spawnExitLockTimer != 0.0f) {
                unitStateTracker.stateValue1 = this.pathTargetX;
                unitStateTracker.stateValue2 = this.pathTargetY;
                unitStateTracker.stateFlag2 = true;
            } else {
                boolean z = false;
                if (this.aU == null) {
                    if (currentPathPosition == null) {
                        if (this.isPathIncomplete && this.repathCooldown < 450.0f && this.aU == null) {
                            z = true;
                        }
                        if (this.repathCooldown == 0.0f) {
                            z = true;
                        }
                        // RWX：完全无路可走时也计入停滞，超阈值直接作废路径点，避免每 500 冷却无限重试。
                        this.rwxStuckTimer += f;
                        if (this.rwxStuckTimer > RWX_STUCK_LIMIT) {
                            rwxAbandonStuckWaypoint();
                            return;
                        }
                    }
                    if (this.repathCooldown == 0.0f && (isAirborne() || useVelocityExtendedRange())) {
                        float fM = m() - 1.0f;
                        if (Utility.abs(this.lastPathTargetX - this.pathTargetX) > fM || Utility.abs(this.lastPathTargetY - this.pathTargetY) > fM) {
                            z = true;
                        }
                    }
                    if (unitCommand != null && this.repathCooldown == 0.0f && ((unitCommand.commandType == UnitCommandType.loadInto || unitCommand.commandType == UnitCommandType.loadUp) && (Utility.abs(this.lastPathTargetX - this.pathTargetX) > 12.0f || Utility.abs(this.lastPathTargetY - this.pathTargetY) > 12.0f))) {
                        z = true;
                    }
                    if (unitCommand != null) {
                        float f2 = this.repathDistanceThreshold;
                        if (Utility.abs(this.lastPathTargetX - this.pathTargetX) > f2 || Utility.abs(this.lastPathTargetY - this.pathTargetY) > f2) {
                            if (this.repathCooldown > 30.0f) {
                                this.repathCooldown = 30.0f;
                            }
                            if (this.repathCooldown == 0.0f) {
                                z = true;
                            }
                        }
                    }
                }
                if (z) {
                    this.repathCooldown = 500.0f;
                    requestPathToTarget(this.pathTargetX, this.pathTargetY, this.pathTargetRadius, this.isTransportAttached && this.formationSlotIndex > 1, this.isLowPriority);
                }
                if (currentPathPosition != null && this.pathPositionProvider == null && this.activePathCount >= 2 && getMoveSpeed() > 5.0f) {
                    PositionData positionData = this.pathPositions[1];
                    float fDistanceSq = Utility.distanceSq(this.posX, this.posY, currentPathPosition.posX, currentPathPosition.posY);
                    float fDistanceSq2 = Utility.distanceSq(this.posX, this.posY, positionData.posX, positionData.posY);
                    if (fDistanceSq < 36.0f || fDistanceSq2 < 361.0f) {
                        advancePathPosition();
                        currentPathPosition = getCurrentPathPosition();
                    }
                }
                if (currentPathPosition != null) {
                    unitStateTracker.stateValue1 = currentPathPosition.posX;
                    unitStateTracker.stateValue2 = currentPathPosition.posY;
                    unitStateTracker.stateFlag2 = true;
                } else if (this.canAttack) {
                    unitStateTracker.stateValue1 = this.pathTargetX;
                    unitStateTracker.stateValue2 = this.pathTargetY;
                    unitStateTracker.stateFlag2 = true;
                }
            }
        }
        applyMovementToward(f, unitStateTracker, unitCommand, zI);
    }

    /* JADX INFO: renamed from: a */
    private void applyMovementToward(float f, UnitStateTracker unitStateTracker, UnitCommand unitCommand, boolean z) {
        float fBc = 0.0f;
        GameEngine gameEngine = GameEngine.getInstance();
        if (this.isPathingActive && unitStateTracker.stateFlag2 && z) {
            float f2 = unitStateTracker.stateValue1;
            float f3 = unitStateTracker.stateValue2;
            float moveSpeed = getMoveSpeed();
            float fDistanceSq = Utility.distanceSq(this.posX, this.posY, f2, f3);
            float angleBetweenPoints = Utility.getAngleBetweenPoints(this.posX, this.posY, f2, ((f3 - this.posY) * getMoveYAxisScalingInverse()) + this.posY);
            boolean z2 = false;
            float fBc2 = bc();
            if (fBc2 > 0.95f) {
                z2 = true;
            } else if (fBc2 > 0.87d) {
                if (this.formationSlotIndex <= 1 && this.activePathCount > 0 && this.activePathCount <= 9 && this.isTransportAttached && fDistanceSq < 250000.0f) {
                    z2 = true;
                }
            } else if (fBc2 > 0.7d) {
                if (this.formationSlotIndex <= 1 && this.activePathCount > 0 && this.activePathCount <= 4 && this.isTransportAttached && fDistanceSq < 40000.0f) {
                    z2 = true;
                }
            } else if (fBc2 > 0.4d && this.formationSlotIndex <= 1 && this.activePathCount > 0 && this.activePathCount <= 2 && this.isTransportAttached && fDistanceSq < 10000.0f) {
                z2 = true;
            }
            float fRotateToward = 179.0f;
            if (this.attackTarget != null && E() && isIgnoreMoveOrders() && !useVelocityExtendedRange()) {
                this.targetRotation = angleBetweenPoints;
            } else if (this.bodyMovementFreezeTimer <= 0.0f) {
                fRotateToward = rotateToward(f, angleBetweenPoints, true, z2);
            }
            float f4 = 20.0f;
            if (fDistanceSq > 361.0f) {
                f4 = 46.0f;
            }
            if (fDistanceSq > 3600.0f) {
                f4 = 89.0f;
            }
            float maxTurnSpeed = getMaxTurnSpeed();
            if (maxTurnSpeed < 1.4d) {
                if (fDistanceSq > 6400.0f) {
                    f4 *= 0.5f;
                } else {
                    f4 = 17.0f;
                }
            }
            if (moveSpeed > 5.0f && this.rotation < 0.01d && this.rotation > -0.01d) {
                f4 = 1.0f;
            }
            if (maxTurnSpeed < 1.1d) {
                f4 *= 0.7f;
            }
            if (this.rotation > 0.4d && fDistanceSq > 16900.0f) {
                f4 = 180.0f;
            }
            if (aY() && this.totalPathPositions == this.activePathCount) {
                f4 = 1.0f;
            }
            if (isIgnoreMoveOrders()) {
                f4 = 181.0f;
            }
            boolean z3 = this.activePathCount == 1;
            if ((!z3 || fDistanceSq >= 4.0f * 4.0f) && Utility.abs(fRotateToward) <= f4) {
                fBc = 1.0f;
                if (unitStateTracker.stateFlag3) {
                    if (fDistanceSq < 2500.0f) {
                        fBc = 1.0f - 0.15f;
                    }
                    if (fDistanceSq < 900.0f) {
                        fBc -= 0.15f;
                    }
                    if (fDistanceSq < 225.0f) {
                        fBc -= 0.3f;
                    }
                } else if (this.transportedBy != null) {
                    if (fDistanceSq > 400.0f) {
                        fBc = 1.0f + 0.2f;
                    }
                    if (fDistanceSq < 49.0f) {
                        fBc -= 0.15f;
                    }
                    if (fDistanceSq < 9.0f) {
                        fBc -= 0.15f;
                    }
                }
                if (fDistanceSq < 9.0f) {
                    fBc = 0.0f;
                }
            }
            if (z3 && fBc != 0.0f) {
                if (fDistanceSq < 324.0f && D() < 0.13f && getMoveSpeed() > 1.0f) {
                    fBc = 0.5f * fBc;
                }
                if (fDistanceSq < 169.0f && D() < 0.15f && getMoveSpeed() > 0.9f) {
                    fBc = 0.5f * fBc;
                }
                if (moveSpeed > 5.0f) {
                    if (fDistanceSq < 324.0f && fBc > 0.5f) {
                        fBc = 0.5f;
                    }
                    if (fDistanceSq < 81.0f && fBc > 0.25f) {
                        fBc = 0.25f;
                    }
                }
            }
            boolean z4 = false;
            if (!z3 && fDistanceSq < 256.0f) {
                z4 = true;
            }
            if (z3 && fDistanceSq < 4.0f * 4.0f) {
                z4 = true;
            }
            if ((this.lastCollisionTick == gameEngine.currentTick || this.lastCollisionTick == gameEngine.currentTick - 1) && this.lastDamagedBy != null && this.lastDamagedBy.isWithinRange(f2, f3, 2.0f)) {
                z4 = true;
            }
            if (fBc > 0.0f) {
                this.pathRetryTimer += f;
                if (this.pathRetryTimer > 200.0f && fDistanceSq < 3600.0f && this.activePathCount >= 2) {
                    float f5 = this.pathRetryTimer;
                    advancePathPosition();
                    this.pathRetryTimer = f5;
                }
                if (this.pathRetryTimer > 600.0f && this.activePathCount >= 2 && this.pathPositionProvider == null) {
                    clearPathData();
                }
                if (this.pathRetryTimer > 80.0f && this.blockedRecoveryTime > 30.0f) {
                    clearPathData();
                }
                if (this.pathRetryTimer > 40.0f && this.activePathCount >= 2 && this.pathPositionProvider == null) {
                    PositionData positionData = this.pathPositions[1];
                    if (Utility.distanceSq(this.posX, this.posY, positionData.posX, positionData.posY) < fDistanceSq) {
                        float f6 = this.pathRetryTimer;
                        advancePathPosition();
                        this.pathRetryTimer = f6;
                    }
                }
            }
            if (z4) {
                advancePathPosition();
                if (z3) {
                    this.transportRecoveryTime = 0.0f;
                    this.moveThrottle = 0.0f;
                    if (!this.isPathIncomplete && this.transportedBy == null && unitCommand != null && unitCommand.commandType == UnitCommandType.move) {
                        advanceWaypointWithTransportedUnits();
                    }
                }
            }
        }
        if (this.isRotating && !isIgnoreMoveOrders()) {
            fBc = (-fBc) * bc();
        }
        if (this.bodyMovementFreezeTimer > 0.0f) {
            fBc = 0.0f;
        }
        if (isSlidingMovement()) {
            this.rotation = fBc;
        } else {
            if (this.rotation < fBc) {
                this.rotation = Utility.distanceSq(this.rotation, fBc, getMoveAccelerationSpeed() * f);
            }
            if (this.rotation > fBc) {
                this.rotation = Utility.distanceSq(this.rotation, fBc, D() * f);
            }
        }
        this.isMoving = unitStateTracker.stateFlag2 && z;
    }

    @Deprecated
    /* JADX INFO: renamed from: Z */
    public boolean hasAttackTarget() {
        return this.attackTarget != null;
    }

    /* JADX INFO: renamed from: aa */
    public boolean hasActiveMovementTarget() {
        if (this.attackTarget != null && !this.attackTarget.isDead) {
            int techLevel = getTechLevel();
            for (int i = 0; i < techLevel; i++) {
                if (this.movementLevels[i].targetUnit != null && r(i)) {
                    return true;
                }
            }
            return false;
        }
        return false;
    }

    /* JADX INFO: renamed from: ab */
    public BaseUnit getCommandOrAttackTarget() {
        if (this.attackTarget != null && !this.attackTarget.isDead) {
            return this.attackTarget;
        }
        UnitCommand currentWaypoint = getCurrentWaypoint();
        if (currentWaypoint != null && currentWaypoint.targetUnit != null && !currentWaypoint.targetUnit.isDead) {
            return currentWaypoint.targetUnit;
        }
        return null;
    }

    /* JADX INFO: renamed from: a */
    private void queryNearbyCollisionUnits(GameEngine gameEngine, float f, float f2) {
        aQ.a(f2);
        gameEngine.unitSpatialIndex.a(this.posX, this.posY, f2, this, f, aQ);
        if (aQ.callbackCount != 0) {
            if (this.attackTarget == null || !isWithinEngagementRange(this.attackTarget)) {
                aR.a(f2);
                gameEngine.unitSpatialIndex.a(this.posX, this.posY, f2, this, f, aR);
            }
        }
    }

    /* JADX INFO: renamed from: ac */
    public boolean supportsIndependentTurretTargets() {
        return getTechLevel() > 1;
    }

    /* JADX INFO: renamed from: a */
    private void syncAttackTargetToMovementLevels(GameEngine gameEngine, float f) {
        int techLevel = getTechLevel();
        if (!supportsIndependentTurretTargets()) {
            for (int i = 0; i < techLevel; i++) {
                this.movementLevels[i].targetUnit = this.attackTarget;
            }
            return;
        }
        boolean z = false;
        for (int i2 = 0; i2 < techLevel; i2++) {
            UnitMovementData unitMovementData = this.movementLevels[i2];
            if (getLinkedTurretIndex(i2) == -1) {
                if (a(i2, this.attackTarget, false, false)) {
                    unitMovementData.targetUnit = this.attackTarget;
                } else {
                    z = true;
                    if (unitMovementData.targetUnit == this.attackTarget) {
                        unitMovementData.targetUnit = null;
                    }
                }
            }
        }
        if (z) {
            float targetSearchRange = getTargetSearchRange(false);
            aT.prepareForUnit(this);
            gameEngine.unitSpatialIndex.a(this.posX, this.posY, targetSearchRange, this, f, aT);
        }
        for (int i3 = 0; i3 < techLevel; i3++) {
            int linkedTurretIndex = getLinkedTurretIndex(i3);
            if (linkedTurretIndex != -1) {
                this.movementLevels[i3].targetUnit = this.movementLevels[linkedTurretIndex].targetUnit;
            }
        }
    }

    /* JADX INFO: renamed from: ad */
    public boolean canAutoAcquireTargets() {
        if (!canAttack()) {
            return false;
        }
        AttachmentSlotDefinition attachmentSlotDefinitionDn = dn();
        if (attachmentSlotDefinitionDn != null && !attachmentSlotDefinitionDn.M) {
            return false;
        }
        return true;
    }

    /* JADX INFO: renamed from: b */
    private void updateAttackTargeting(GameEngine gameEngine, float f) {
        int techLevel = getTechLevel();
        boolean z = false;
        if (canAutoAcquireTargets()) {
            boolean z2 = false;
            boolean z3 = false;
            if (this.attackTarget != null) {
                AttachmentSlotDefinition attachmentSlotDefinitionDn = dn();
                if (attachmentSlotDefinitionDn != null && this.parentEntity != null && attachmentSlotDefinitionDn.L && this.parentEntity.attackTarget == this.attackTarget) {
                    z2 = true;
                }
                if (!a(this.attackTarget, false) && !z2 && 1 != 0) {
                    this.attackTarget = null;
                }
            }
            if (this.attackTarget != null && !z2) {
                z3 = !isWithinEngagementRange(this.attackTarget);
            }
            this.turretRotation = Utility.moveTowardsZero(this.turretRotation, f);
            this.targetTurretRotation = Utility.moveTowardsZero(this.targetTurretRotation, f);
            if ((this.attackTarget == null || z3) && this.turretRotation == 0.0f && bf()) {
                this.turretRotation = 20.0f + (this.posX % 5.0f) + (this.posY % 5.0f);
                queryNearbyCollisionUnits(gameEngine, f, getTargetSearchRange(false));
                if (this.attackTarget != null) {
                    this.targetTurretRotation = 0.0f;
                }
            }
            if (this.attackTarget != null && this.targetTurretRotation == 0.0f) {
                this.targetTurretRotation = 20.0f + (this.posX % 5.0f) + (this.posY % 5.0f);
                syncAttackTargetToMovementLevels(gameEngine, f);
            }
            for (int i = 0; i < techLevel; i++) {
                this.movementLevels[i].isActive = false;
            }
            if (this.attackTarget != null) {
                float fDistanceSq = Utility.distanceSq(this.posX, this.posY, this.attackTarget.posX, this.attackTarget.posY);
                float engagementRange = getEngagementRange(this.attackTarget);
                if (fDistanceSq < engagementRange * engagementRange || z2) {
                    int defaultTurretIndex = getDefaultTurretIndex();
                    for (int i2 = 0; i2 < techLevel; i2++) {
                        UnitMovementData unitMovementData = this.movementLevels[i2];
                        BaseUnit baseUnit = unitMovementData.targetUnit;
                        if (baseUnit != null) {
                            boolean z4 = baseUnit == this.attackTarget;
                            if (!z4 && !b(baseUnit, true)) {
                                unitMovementData.targetUnit = null;
                            } else if (!a(i2, baseUnit, false, !z4)) {
                                unitMovementData.targetUnit = null;
                            } else {
                                PointF pointFG = G(i2);
                                PointF shadowTexture = getShadowOffsetForLevel(i2);
                                shadowTexture.x += baseUnit.posX;
                                shadowTexture.y += baseUnit.posY;
                                float angleBetweenPoints = Utility.getAngleBetweenPoints(pointFG.x, pointFG.y, shadowTexture.x, shadowTexture.y);
                                if (getLinkedTurretIndex(i2) == -1 && i2 != defaultTurretIndex) {
                                    if (!E()) {
                                        unitMovementData.a(70);
                                        unitMovementData.targetY = unitMovementData.targetX;
                                        float fA = 179.0f;
                                        if (!unitMovementData.b()) {
                                            fA = a(f, angleBetweenPoints, i2);
                                        }
                                        if (Utility.abs(fA) < x(i2)) {
                                            unitMovementData.isActive = true;
                                        }
                                    } else {
                                        boolean z5 = false;
                                        UnitCommand currentWaypoint = getCurrentWaypoint();
                                        if (currentWaypoint != null && (currentWaypoint.commandType == UnitCommandType.build || currentWaypoint.commandType == UnitCommandType.repair || currentWaypoint.commandType == UnitCommandType.reclaim)) {
                                            z5 = true;
                                        }
                                        if (!z5 && (!this.isPathingActive || isIgnoreMoveOrders())) {
                                            float fFaceTowardPosition = faceTowardPosition(f, angleBetweenPoints);
                                            unitMovementData.targetY = unitMovementData.targetX;
                                            if (Utility.abs(fFaceTowardPosition) < x(i2)) {
                                                unitMovementData.isActive = true;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    for (int i3 = 0; i3 < techLevel; i3++) {
                        UnitMovementData unitMovementData2 = this.movementLevels[i3];
                        BaseUnit baseUnit2 = unitMovementData2.targetUnit;
                        if (baseUnit2 != null) {
                            if (u(i3) && unitMovementData2.rotation == 0.0f) {
                                z = true;
                            }
                            if (u(i3)) {
                                advanceMovementLevelTowardTarget(f, baseUnit2, i3);
                            }
                        }
                    }
                } else if (!this.isPathingActive && isChaseAttackMode()) {
                    this.canAttack = true;
                    this.isPathingActive = true;
                    this.pathTargetX = this.attackTarget.posX;
                    this.pathTargetY = this.attackTarget.posY;
                    this.pathTargetRadius = 0;
                }
            }
        }
        if (this.aN && getCurrentRepairOrReclaimTarget() != null) {
            z = true;
        }
        for (int i4 = 0; i4 < techLevel; i4++) {
            UnitMovementData unitMovementData3 = this.movementLevels[i4];
            if (!z && unitMovementData3.speed != 0.0f) {
                unitMovementData3.speed = Utility.moveTowardsZero(unitMovementData3.speed, f(i4) * f);
            }
        }
    }

    public void b(BaseUnit baseUnit, int i) {
    }

    /* JADX INFO: renamed from: a */
    public boolean advanceMovementLevelTowardTarget(float f, BaseUnit baseUnit, int i) {
        if (isTransferUnavailable() || baseUnit.isTransferUnavailable()) return false;
        UnitMovementData unitMovementData = this.movementLevels[i];
        int linkedTurretIndex = getLinkedTurretIndex(i);
        if (linkedTurretIndex != -1) {
            unitMovementData.targetX = this.movementLevels[linkedTurretIndex].targetX;
        }
        boolean zS = s(i);
        boolean z = false;
        if (zS) {
            if (unitMovementData.speed < e(i)) {
                if (unitMovementData.speed == 0.0f) {
                    b(baseUnit, i);
                }
                unitMovementData.speed += f;
            } else {
                unitMovementData.speed = e(i);
            }
            z = true;
        }
        if (unitMovementData.rotation == 0.0f && r(i)) {
            if (!a(i, baseUnit, false, false)) {
                unitMovementData.rotation = -10.0f;
                return false;
            }
            if (!zS) {
                if (unitMovementData.speed < e(i)) {
                    if (unitMovementData.speed == 0.0f) {
                        b(baseUnit, i);
                    }
                    unitMovementData.speed += f;
                } else {
                    z = true;
                }
            }
            if (z) {
                unitMovementData.rotation = b(i) + t(i);
                if (!zS) {
                    unitMovementData.speed = 0.0f;
                }
                a(baseUnit, i);
                M(i);
                unitMovementData.isOddShot = !unitMovementData.isOddShot;
                return true;
            }
            return false;
        }
        return false;
    }

    /* JADX INFO: renamed from: h */
    public boolean isWithinEngagementRange(BaseUnit baseUnit) {
        float fDistanceSq = Utility.distanceSq(this.posX, this.posY, baseUnit.posX, baseUnit.posY);
        float engagementRange = getEngagementRange(baseUnit);
        if (fDistanceSq < engagementRange * engagementRange) {
            return true;
        }
        return false;
    }

    /* JADX INFO: renamed from: ae */
    public boolean canAttackSubmergedUnits() {
        return false;
    }

    /* JADX INFO: renamed from: af */
    public boolean canAttackFlyingUnits() {
        return true;
    }

    /* JADX INFO: renamed from: ag */
    public boolean canAttackSurfaceUnits() {
        return true;
    }

    public boolean ah() {
        return true;
    }

    /* JADX INFO: renamed from: i */
    public boolean canEngageTargetNow(BaseUnit baseUnit) {
        if (isTransferUnavailable() || baseUnit.isTransferUnavailable()) return false;
        int linkedTurretIndex;
        int techLevel = getTechLevel();
        for (int i = 0; i < techLevel; i++) {
            if (r(i) && a(i, baseUnit, false, false) && ((linkedTurretIndex = getLinkedTurretIndex(i)) == -1 || a(linkedTurretIndex, baseUnit, false, false))) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: renamed from: j */
    public boolean canEngageTargetWithMovement(BaseUnit baseUnit) {
        if (isTransferUnavailable() || baseUnit.isTransferUnavailable()) return false;
        int linkedTurretIndex;
        int techLevel = getTechLevel();
        for (int i = 0; i < techLevel; i++) {
            if (r(i) && a(i, baseUnit, true, false) && ((linkedTurretIndex = getLinkedTurretIndex(i)) == -1 || a(linkedTurretIndex, baseUnit, true, false))) {
                return true;
            }
        }
        return false;
    }

    public boolean a(int i, BaseUnit baseUnit, boolean z, boolean z2) {
        if (isTransferUnavailable() || baseUnit.isTransferUnavailable()) return false;
        if (!z && z2 && !isWithinEngagementRange(baseUnit)) {
            return false;
        }
        return true;
    }

    /* JADX INFO: renamed from: k */
    public boolean canAttackUnitType(BaseUnit baseUnit) {
        if (baseUnit.i()) {
            return canAttackFlyingUnits();
        }
        if (baseUnit.Q()) {
            return canAttackSubmergedUnits();
        }
        if (!ah() && !baseUnit.isTouchingWater()) {
            return false;
        }
        return canAttackSurfaceUnits();
    }

    /* JADX INFO: renamed from: a */
    public boolean canRepairTarget(BaseUnit baseUnit) {
        return false;
    }

    /* JADX INFO: renamed from: l */
    public boolean canReclaimTarget(BaseUnit baseUnit) {
        if (baseUnit.getResourceRate() != 0.0f && h(baseUnit, true)) {
            return true;
        }
        return canRepairTarget(baseUnit);
    }

    /* JADX INFO: renamed from: a */
    public AbstractUnitAction findActionForUnitType(UnitType unitType, boolean z) {
        return findActionForUnitTypeWithQueueSize(unitType, -1, z);
    }

    /* JADX INFO: renamed from: ai */
    public boolean hasHighPriorityAction() {
        Iterator it = getAvailableActions().iterator();
        while (it.hasNext()) {
            if (((AbstractUnitAction) it.next()).isHighPriority()) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: renamed from: a */
    public AbstractUnitAction findActionForUnitTypeWithQueueSize(UnitType unitType, int i, boolean z) {
        UnitType actionType;
        ArrayList<AbstractUnitAction> arrayListN = getAvailableActions();
        AbstractUnitAction abstractUnitAction = null;
        if (arrayListN.size() > 0) {
            for (AbstractUnitAction abstractUnitAction2 : arrayListN) {
                UnitType attachedUnitType = abstractUnitAction2.getAttachedUnitType();
                if (z && (actionType = abstractUnitAction2.getAiConsiderSameAsBuildingUnitType()) != null) {
                    attachedUnitType = actionType;
                }
                if (attachedUnitType == unitType && (i == -1 || i == abstractUnitAction2.getQueueSize())) {
                    abstractUnitAction = abstractUnitAction2;
                    if (abstractUnitAction2.b(this) && abstractUnitAction2.canAfford((BaseUnit) this, false)) {
                        return abstractUnitAction2;
                    }
                }
            }
        }
        return abstractUnitAction;
    }

    /* JADX INFO: renamed from: b */
    public boolean canUseActionForUnitType(UnitType unitType, boolean z) {
        AbstractUnitAction abstractUnitActionFindActionForUnitType = findActionForUnitType(unitType, z);
        if (abstractUnitActionFindActionForUnitType == null || abstractUnitActionFindActionForUnitType.isNotAvailable(this) || !abstractUnitActionFindActionForUnitType.b(this)) {
            return false;
        }
        return true;
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    /* JADX INFO: renamed from: aj */
    public boolean canUnitAttack() {
        return r().m();
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    /* JADX INFO: renamed from: ak */
    public boolean canMove() {
        return r().l();
    }

    public void m(BaseUnit baseUnit) {
    }

    /* JADX INFO: renamed from: al */
    public boolean isTurretUnit() {
        return false;
    }

    public final boolean a(BaseUnit baseUnit, boolean z) {
        if (isTransferUnavailable() || baseUnit.isTransferUnavailable()) return false;
        if (this.team == baseUnit.team || baseUnit.isDead || !this.team.c(baseUnit.team) || this.attackMode == AttackMode.holdFire || this.attackMode == AttackMode.returnFire || baseUnit.transportContainer != null || !canAttackUnitType(baseUnit) || !baseUnit.d((BaseUnit) this)) {
            return false;
        }
        if (!z) {
            float fDistanceSq = Utility.distanceSq(this.posX, this.posY, baseUnit.posX, baseUnit.posY);
            float targetSearchRange = getTargetSearchRange(false);
            if (fDistanceSq < targetSearchRange * targetSearchRange) {
                return true;
            }
            return false;
        }
        return true;
    }

    public final boolean b(BaseUnit baseUnit, boolean z) {
        if (baseUnit.isNotPassivelyTargetedByOtherUnits()) {
            return false;
        }
        return a(baseUnit, z);
    }

    /* JADX INFO: renamed from: am */
    public float getPassiveTargetSearchRangeBonus() {
        return 0.0f;
    }

    /* JADX INFO: renamed from: an */
    public boolean isChaseAttackMode() {
        return this.attackMode == AttackMode.outOfRange || this.attackMode == AttackMode.guardArea || this.attackMode == AttackMode.aggressive;
    }

    /* JADX INFO: renamed from: b */
    public float getTargetSearchRange(boolean z) {
        float passiveTargetSearchRangeBonus;
        float fM = m();
        UnitCommand currentWaypoint = getCurrentWaypoint();
        if (currentWaypoint != null && (currentWaypoint.commandType == UnitCommandType.attackMove || currentWaypoint.commandType == UnitCommandType.patrol || currentWaypoint.commandType == UnitCommandType.guard)) {
            if (currentWaypoint.commandType == UnitCommandType.patrol) {
                fM += 110.0f;
            } else if (currentWaypoint.commandType == UnitCommandType.guard) {
                fM += 90.0f;
            } else {
                fM += 20.0f;
            }
            if (fM < 190.0f) {
                fM = 190.0f;
            }
        }
        if (this.attackMode == AttackMode.outOfRange) {
            passiveTargetSearchRangeBonus = fM + 250.0f;
        } else if (this.attackMode == AttackMode.guardArea) {
            passiveTargetSearchRangeBonus = fM + 150.0f;
        } else if (this.attackMode == AttackMode.aggressive) {
            passiveTargetSearchRangeBonus = fM + 180.0f;
        } else {
            passiveTargetSearchRangeBonus = fM + getPassiveTargetSearchRangeBonus();
            if (z) {
                passiveTargetSearchRangeBonus += 110.0f;
            }
        }
        return passiveTargetSearchRangeBonus;
    }

    /* JADX INFO: renamed from: ao */
    public UnitCommand queueNextWaypoint() {
        ensureWaypointCapacityForIndex(29);
        if (this.waypointCount > 0) {
            onCurrentWaypointRemoved(this.waypoints[0]);
        }
        UnitCommand unitCommand = this.waypoints[29];
        for (int i = 29; i >= 1; i--) {
            this.waypoints[i] = this.waypoints[i - 1];
        }
        this.waypoints[0] = unitCommand;
        if (this.waypointCount < 29) {
            this.waypointCount++;
        }
        if (this.waypoints[0] == null) {
            this.waypoints[0] = new UnitCommand();
        }
        UnitCommand unitCommand2 = this.waypoints[0];
        unitCommand2.resetCommand();
        this.wayPointTimer = 0.0f;
        this.waypointDwellTimer = 0.0f;
        this.pathRetryTimer = 0.0f;
        onCurrentWaypointChanged(unitCommand2);
        clearPathData();
        return unitCommand2;
    }

    public void a(UnitCommand unitCommand) {
    }

    /* JADX INFO: renamed from: b */
    public final void onCurrentWaypointRemoved(UnitCommand unitCommand) {
        this.isActing = false;
    }

    /* JADX INFO: renamed from: c */
    public void onCurrentWaypointChanged(UnitCommand unitCommand) {
        updateUnitMovement();
        this.lastReclaimSearchTick = -9999;
        if (this.attackTarget != null && this.attackTarget.isNotPassivelyTargetedByOtherUnits()) {
            this.attackTarget = null;
        }
    }

    /* JADX INFO: renamed from: ap */
    public UnitCommand appendWaypoint() {
        ensureWaypointCapacityForIndex(this.waypointCount);
        if (this.waypoints[this.waypointCount] == null) {
            this.waypoints[this.waypointCount] = new UnitCommand();
        }
        UnitCommand unitCommand = this.waypoints[this.waypointCount];
        unitCommand.resetCommand();
        if (this.waypointCount < 29) {
            this.waypointCount++;
        }
        if (this.waypointCount > 0) {
            onCurrentWaypointChanged(this.waypoints[0]);
        }
        return unitCommand;
    }

    /* JADX INFO: renamed from: d */
    public UnitCommand appendMoveWaypoint(float f, float f2) {
        UnitCommand unitCommandAppendWaypoint = appendWaypoint();
        unitCommandAppendWaypoint.setMoveTarget(f, f2);
        return unitCommandAppendWaypoint;
    }

    /* JADX INFO: renamed from: n */
    public UnitCommand appendAttackWaypoint(BaseUnit baseUnit) {
        UnitCommand unitCommandAppendWaypoint = appendWaypoint();
        unitCommandAppendWaypoint.setAttackTarget(baseUnit);
        return unitCommandAppendWaypoint;
    }

    /* JADX INFO: renamed from: e */
    public UnitCommand appendAttackMoveWaypoint(float f, float f2) {
        UnitCommand unitCommandAppendWaypoint = appendWaypoint();
        unitCommandAppendWaypoint.setAttackMoveTarget(f, f2);
        return unitCommandAppendWaypoint;
    }

    /* JADX INFO: renamed from: a */
    public boolean isValidNewWaypoint(UnitCommand unitCommand, boolean z) {
        if (unitCommand == null) {
            if (z) {
                GameEngine.logColored("isValidNewWaypoint: Skipping null waypoint");
                return false;
            }
            return false;
        }
        if (unitCommand.getCommandType() == UnitCommandType.build) {
            if (unitCommand.buildUnitType == null) {
                if (z) {
                    GameEngine.logColored("isValidNewWaypoint: Skipping build waypoint with no buildType");
                    return false;
                }
                return false;
            }
            AbstractUnitAction abstractUnitActionFindActionForUnitTypeWithQueueSize = findActionForUnitTypeWithQueueSize(unitCommand.buildUnitType, unitCommand.buildQueueSize, false);
            if (abstractUnitActionFindActionForUnitTypeWithQueueSize == null) {
                if (z) {
                    GameEngine.logColored("Unit '" + r().getUnitTypeDescriptionShort() + "' can not queue build:" + unitCommand.buildUnitType.getUnitTypeDescriptionShort());
                    return false;
                }
                return false;
            }
            if (!unitCommand.isForceMove) {
                if (abstractUnitActionFindActionForUnitTypeWithQueueSize.isNotAvailable(this)) {
                    if (z) {
                        GameEngine.logColored("Builder '" + r().getUnitTypeDescriptionShort() + "' tried to queue a locked building:" + abstractUnitActionFindActionForUnitTypeWithQueueSize.getActionIdString());
                        return false;
                    }
                    return false;
                }
                if (!abstractUnitActionFindActionForUnitTypeWithQueueSize.b(this)) {
                    if (z) {
                        GameEngine.logColored("Builder '" + r().getUnitTypeDescriptionShort() + "' tried to queue a unavailable building:" + abstractUnitActionFindActionForUnitTypeWithQueueSize.getActionIdString());
                        return false;
                    }
                    return false;
                }
                return true;
            }
            return true;
        }
        return true;
    }

    /* JADX INFO: renamed from: d */
    public UnitCommand appendWaypointCopy(UnitCommand unitCommand) {
        UnitCommand unitCommandAppendWaypoint = appendWaypoint();
        unitCommandAppendWaypoint.copyFrom(unitCommand);
        return unitCommandAppendWaypoint;
    }

    /* JADX INFO: renamed from: aq */
    public boolean hasNoCurrentWaypoint() {
        return getCurrentWaypoint() == null;
    }

    /* JADX INFO: renamed from: ar */
    public UnitCommand getCurrentWaypoint() {
        if (this.waypointCount == 0) {
            return null;
        }
        return this.waypoints[0];
    }

    /* JADX INFO: renamed from: as */
    public UnitCommand getNextWaypoint() {
        if (this.waypointCount <= 1) {
            return null;
        }
        return this.waypoints[1];
    }

    /* JADX INFO: renamed from: at */
    public UnitCommand getLastWaypoint() {
        if (this.waypointCount == 0) {
            return null;
        }
        return this.waypoints[this.waypointCount - 1];
    }

    /* JADX INFO: renamed from: au */
    public void removeLastWaypoint() {
        if (this.waypointCount == 0) {
            return;
        }
        if (this.waypointCount == 1) {
            advanceWaypoint();
        } else {
            this.waypointCount--;
        }
    }

    /* JADX INFO: renamed from: k */
    public UnitCommand getWaypointAt(int i) {
        return this.waypoints[i];
    }

    /* JADX INFO: renamed from: av */
    public int getWaypointCount() {
        return this.waypointCount;
    }

    /* JADX INFO: renamed from: aw */
    public boolean isAttackCommandActive() {
        UnitCommand currentWaypoint = getCurrentWaypoint();
        if (currentWaypoint != null && currentWaypoint.commandType == UnitCommandType.attack) {
            return true;
        }
        return false;
    }

    /* JADX INFO: renamed from: a */
    public boolean hasBuildWaypointNear(UnitType unitType, float f, float f2) {
        for (int i = 0; i < this.waypointCount; i++) {
            UnitCommand unitCommand = this.waypoints[i];
            if (unitCommand.commandType == UnitCommandType.build && unitCommand.buildUnitType == unitType && Utility.abs(unitCommand.targetX - f) < 10.0f && Utility.abs(unitCommand.targetY - f2) < 10.0f) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: renamed from: l */
    public void ensurePathPositionCapacityForIndex(int i) {
        if (i >= 120) {
            throw new RuntimeException("PathNode index:" + i + " too large");
        }
        if (this.pathPositions == at) {
            this.pathPositions = new PositionData[120];
        }
    }

    /* JADX INFO: renamed from: m */
    public void ensureWaypointCapacityForIndex(int i) {
        if (i >= 30) {
            throw new RuntimeException("Waypoint index:" + i + " too large");
        }
        if (this.waypoints == O) {
            this.waypoints = new UnitCommand[30];
        }
    }

    /* JADX INFO: renamed from: n */
    public void removeWaypointAt(int i) {
        if (this.waypointCount <= i) {
            throw new IndexOutOfBoundsException("completeWaypoint: waypointsCount:" + this.waypointCount + ", waypointIndex:" + i);
        }
        if (i == 0) {
            advanceWaypoint();
            return;
        }
        if (this.waypoints.length > 0) {
            UnitCommand unitCommand = this.waypoints[i];
            for (int i2 = i; i2 < this.waypointCount - 1; i2++) {
                this.waypoints[i2] = this.waypoints[i2 + 1];
            }
            this.waypoints[this.waypointCount - 1] = unitCommand;
        }
        this.waypointCount--;
    }

    /* JADX INFO: renamed from: ax */
    public void advanceWaypointWithTransportedUnits() {
        syncTransportedUnitsWaypointProgress();
        advanceWaypoint();
    }

    // RWX：作废「走不过去」的当前路径点，并连带丢掉后面指向同一片区域的移动行动。
    // 只动移动类命令，build/attack/reclaim 等原地可执行的命令保留，让单位当场恢复干活。
    private void rwxAbandonStuckWaypoint() {
        UnitCommand unitCommand = getCurrentWaypoint();
        this.rwxStuckTimer = 0.0f;
        this.rwxStuckSampled = false;
        if (unitCommand == null) {
            return;
        }
        float stuckTargetX = unitCommand.getTargetX();
        float stuckTargetY = unitCommand.getTargetY();
        this.aU = null;
        this.pathPositionProvider = null;
        clearPathData();
        this.isPathingActive = false;
        this.repathCooldown = 0.0f;
        if (RW_PATH_STATS) {
            rwUnreachableCancels++;
            // 调试期每次弃点都打一行，方便确认检测点真的被走到；正式用可改回取模。
            GameEngine.log("RWX_PATHSTATS stuckWaypointCancels=" + rwUnreachableCancels + " at tick=" + GameEngine.getInstance().currentTick + " type=" + unitCommand.commandType);
        }
        advanceWaypointWithTransportedUnits();
        for (int guard = 0; guard < 8; guard++) {
            UnitCommand next = getCurrentWaypoint();
            if (next == null) {
                break;
            }
            if (next.commandType != UnitCommandType.move && next.commandType != UnitCommandType.attackMove && next.commandType != UnitCommandType.patrol) {
                break;
            }
            float dx = next.getTargetX() - stuckTargetX;
            float dy = next.getTargetY() - stuckTargetY;
            if (dx * dx + dy * dy > 160000.0f) {
                break;
            }
            advanceWaypointWithTransportedUnits();
        }
    }

    /* JADX INFO: renamed from: ay */
    public void advanceWaypoint() {
        this.wayPointTimer = 0.0f;
        this.waypointDwellTimer = 0.0f;
        this.pathRetryTimer = 0.0f;
        this.isSecondaryRecharging = false;
        this.navigationAngle = -999.0f;
        this.isTargetSearchPending = false;
        this.longRangePathing = 0;
        if (this.waypointCount == 0) {
            clearPathData();
            this.transportRecoveryDelay = 0.0f;
            this.transportRecoveryTime = 0.0f;
            this.moveThrottle = 0.0f;
            return;
        }
        if (this.waypointCount == 1) {
            onCurrentWaypointRemoved(this.waypoints[0]);
            this.waypointCount = 0;
            clearPathData();
            this.transportRecoveryDelay = 0.0f;
            this.transportRecoveryTime = 0.0f;
            this.moveThrottle = 0.0f;
            onCurrentWaypointChanged((UnitCommand) null);
            return;
        }
        if (this.waypoints.length > 0) {
            UnitCommand unitCommand = this.waypoints[0];
            onCurrentWaypointRemoved(unitCommand);
            for (int i = 0; i < this.waypointCount - 1; i++) {
                this.waypoints[i] = this.waypoints[i + 1];
            }
            this.waypoints[this.waypointCount - 1] = unitCommand;
        }
        this.waypointCount--;
        if (this.waypointCount > 0) {
            onCurrentWaypointChanged(this.waypoints[0]);
        } else {
            onCurrentWaypointChanged((UnitCommand) null);
        }
        clearPathData();
    }

    /* JADX INFO: renamed from: az */
    // RWX：玩家/引擎默认调用 = 全部清掉（老师口径：只有 AI 才不许取消模组行动的点）。
    public void clearAllWaypoints() {
        clearAllWaypoints(false);
    }

    // RWX：rwxFromAi=true 时保护队首的模组行动点（WaypointAction / addWaypoint_prepend）。
    public void clearAllWaypoints(boolean rwxFromAi) {
        // RWX：模组行动（WaypointAction）加的路径点带 isForceMove 标记。
        // 只保护「队首」那一个——模组用 addWaypoint_prepend 前插，队首就是它这次要执行的点。
        // 后面堆积的旧点必须清掉：否则模组每行动一次就前插一个，AI 永远拿不回指挥权，
        // 单位被一堆随机偏移点永久钉住（MC 模组 1043 处 addWaypoint，实测会锁死建造）。
        UnitCommand rwxHead = this.waypointCount > 0 ? this.waypoints[0] : null;
        boolean rwxKeepHead = rwxFromAi && rwxHead != null && rwxHead.isForceMove;
        if (rwxKeepHead) {
            for (int rk2 = this.waypointCount - 1; rk2 >= 1; rk2--) {
                removeWaypointAt(rk2);
            }
            if (this.waypointCount <= 1) {
                this.wayPointTimer = 0.0f;
                this.waypointDwellTimer = 0.0f;
            }
            return;
        }
        int i = this.waypointCount;
        if (this.waypointCount > 0) {
            onCurrentWaypointRemoved(this.waypoints[0]);
        }
        this.wayPointTimer = 0.0f;
        this.waypointDwellTimer = 0.0f;
        this.isSecondaryRecharging = false;
        this.navigationAngle = -999.0f;
        this.isTargetSearchPending = false;
        this.waypointCount = 0;
        clearPathData();
        detachTransportedUnits();
        setTransportParent((OrderableUnit) null);
        this.transportRecoveryDelay = 0.0f;
        this.transportRecoveryTime = 0.0f;
        this.moveThrottle = 0.0f;
        this.longRangePathing = 0;
        if (i > 0) {
            onCurrentWaypointChanged((UnitCommand) null);
        }
    }

    /* JADX INFO: renamed from: aA */
    public void removeNonBuildRepairWaypoints() {
        // RWX：倒序删，避免删掉一项后后面的元素前移导致漏删。
        // 队首若是模组行动加的点就留着（模组前插的那一个），其余非 build/repair 一律清。
        for (int i = this.waypointCount - 1; i >= 0; i--) {
            UnitCommand unitCommand = this.waypoints[i];
            if (unitCommand == null) {
                continue;
            }
            if (i == 0 && unitCommand.isForceMove) {
                continue;
            }
            if (unitCommand.commandType != UnitCommandType.build && unitCommand.commandType != UnitCommandType.repair) {
                removeWaypointAt(i);
            }
        }
    }

    /* JADX INFO: renamed from: a */
    public void setTransportParent(OrderableUnit orderableUnit) {
        if (this.transportedBy != null) {
            this.transportedBy.transportedUnitCount--;
        }
        this.transportedBy = orderableUnit;
        if (orderableUnit != null) {
            this.transportedBy.transportedUnitCount++;
        }
    }

    /* JADX INFO: renamed from: aB */
    public void clearTransportState() {
        setTransportParent((OrderableUnit) null);
        this.isTransportAttached = false;
        this.inFormation = false;
        this.transportOffsetX = 0.0f;
        this.transportOffsetY = 0.0f;
        this.waypointSyncGroupId = 0;
        this.moveThrottle = 0.0f;
    }

    /* JADX INFO: renamed from: aC */
    public void syncTransportedUnitsWaypointProgress() {
        if (this.transportedUnitCount == 0) {
            return;
        }
        UnitCommand nextWaypoint = getNextWaypoint();
        BaseUnit[] baseUnitArrA = BaseUnit.bE.a();
        int size = BaseUnit.bE.size();
        for (int i = 0; i < size; i++) {
            BaseUnit baseUnit = baseUnitArrA[i];
            if (baseUnit instanceof OrderableUnit orderableUnit) {
                if (orderableUnit.transportedBy == this) {
                    boolean z = Utility.distanceSq(this.posX, this.posY, orderableUnit.posX, orderableUnit.posY) < 108900.0f;
                    boolean z2 = false;
                    boolean z3 = false;
                    UnitCommand nextWaypoint2 = orderableUnit.getNextWaypoint();
                    if (nextWaypoint != null && nextWaypoint2 != null) {
                        if (nextWaypoint.isSameCommand(nextWaypoint2)) {
                            z2 = true;
                        }
                    } else if (nextWaypoint == null && nextWaypoint2 == null) {
                        z3 = true;
                    }
                    if (z2 && z) {
                        orderableUnit.advanceWaypoint();
                    } else if (!z3) {
                        orderableUnit.setTransportParent((OrderableUnit) null);
                    }
                }
            }
        }
    }

    /* JADX INFO: renamed from: aD */
    public void detachTransportedUnits() {
        UnitCommand currentWaypoint;
        FormationGroup formationGroup;
        OrderableUnit orderableUnit = null;
        if (this.transportedUnitCount == 0) {
            return;
        }
        BaseUnit[] baseUnitArrA = BaseUnit.bE.a();
        int size = BaseUnit.bE.size();
        for (int i = 0; i < size; i++) {
            BaseUnit baseUnit = baseUnitArrA[i];
            if (baseUnit instanceof OrderableUnit) {
                OrderableUnit orderableUnit2 = (OrderableUnit) baseUnit;
                if (orderableUnit2.transportedBy == this) {
                    orderableUnit2.setTransportParent((OrderableUnit) null);
                    orderableUnit = orderableUnit2;
                }
            }
        }
        if (this.transportedUnitCount != 0) {
            this.transportedUnitCount = 0;
        }
        if (orderableUnit != null && (currentWaypoint = orderableUnit.getCurrentWaypoint()) != null && (formationGroup = currentWaypoint.transportTarget) != null) {
            formationGroup.c();
        }
    }

    /* JADX INFO: renamed from: aE */
    public PositionData getCurrentPathPosition() {
        if (this.activePathCount == 0) {
            return null;
        }
        if (this.pathPositionProvider != null) {
            return this.pathPositionProvider.a(this);
        }
        return this.pathPositions[0];
    }

    /* JADX INFO: renamed from: aF */
    public PositionData getNextPathPosition() {
        if (this.activePathCount < 2) {
            return null;
        }
        if (this.pathPositionProvider != null) {
            return this.pathPositionProvider.b(this);
        }
        return this.pathPositions[1];
    }

    /* JADX INFO: renamed from: a */
    public void setPathPosition(int i, float f, float f2) {
        ensurePathPositionCapacityForIndex(i);
        if (this.pathPositions[i] == null) {
            this.pathPositions[i] = new PositionData();
        }
        this.pathPositions[i].posX = f;
        this.pathPositions[i].posY = f2;
    }

    public boolean aG() {
        if (this.pathPositionProvider != null || this.activePathCount < 2) {
            return false;
        }
        if (getMoveSpeed() > 0.5d) {
            if (this.pathRetryTimer > 150.0f || this.previousPathRetryTimer > 150.0f) {
                return true;
            }
            return false;
        }
        if (this.pathRetryTimer > 300.0f || this.previousPathRetryTimer > 300.0f) {
            return true;
        }
        return false;
    }

    /* JADX INFO: renamed from: aH */
    public void clearPathData() {
        // RWX：activePathCount 归零时 provider 若还挂着，getPathPositionAt 会先问 provider——
        // 它里面的游标是旧路径的，会算出越界位置；provider 持有的 Path 也随时可能被复用，一并断开
        this.pathPositionProvider = null;
        this.activePathCount = 0;
        this.isPathIncomplete = false;
        this.totalPathPositions = 0;
        this.repathCooldown = 0.0f;
        this.pathRetryTimer = 0.0f;
        this.previousPathRetryTimer = 0.0f;
        this.pathRetryCount = (byte) 0;
    }

    @Override
    public void rebaseTransferState(float dx, float dy, int timeDelta) {
        super.rebaseTransferState(dx, dy, timeDelta);
        for (int i = 0; i < this.waypointCount; i++) {
            this.waypoints[i].targetX += dx;
            this.waypoints[i].targetY += dy;
        }
        this.pathTargetX += dx;
        this.pathTargetY += dy;
        this.lastPathTargetX += dx;
        this.lastPathTargetY += dy;
        clearPathingState();
        this.pathPositionProvider = null;
        this.lastDamagedBy = null;
        this.nextCollisionQueryTick = 0;
    }

    @Override
    public void rebaseTransferClock(int timeDelta) {
        super.rebaseTransferClock(timeDelta);
        this.lastTransportPathUpdateTick = rebaseTransferStamp(this.lastTransportPathUpdateTick, timeDelta);
        if (this.activeStatusEffects != null) {
            for (Object effect : this.activeStatusEffects) {
                ((com.corrodinggames.rts.game.units.g.AirUnitEffect) effect).rebaseTransferClock(timeDelta);
            }
        }
    }

    /* JADX INFO: renamed from: aI */
    public void clearPathingState() {
        clearPathData();
        this.pathPositions = at;
        this.nearbyCollisionSize = (byte) 0;
        this.nearbyUnits = null;
        this.nearbyUnitDistances = null;
    }

    /* JADX INFO: renamed from: aJ */
    public void advancePathPosition() {
        this.previousPathRetryTimer = this.pathRetryTimer;
        this.pathRetryTimer = 0.0f;
        if (this.pathPositionProvider != null) {
            this.pathPositionProvider.c(this);
            return;
        }
        if (this.activePathCount == 0) {
            return;
        }
        if (this.activePathCount == 1) {
            this.activePathCount = 0;
            return;
        }
        PositionData positionData = this.pathPositions[0];
        for (int i = 0; i < this.activePathCount - 1; i++) {
            this.pathPositions[i] = this.pathPositions[i + 1];
        }
        this.pathPositions[this.activePathCount - 1] = positionData;
        this.activePathCount--;
    }

    /* JADX INFO: renamed from: aK */
    public boolean isCurrentTileBlocked() {
        GameEngine gameEngine = GameEngine.getInstance();
        boolean z = false;
        if (isAirborne()) {
            z = true;
        }
        gameEngine.tileMap.setCursorTileIndexFromWorldPoint(this.posX, this.posY);
        int i = gameEngine.tileMap.cursorTileX;
        int i2 = gameEngine.tileMap.cursorTileY;
        if (gameEngine.pathfindingEngine.isTileBlockedForMovement(getMovementType(), i, i2) && !gameEngine.pathfindingEngine.b(getMovementType(), i, i2)) {
            z = true;
        }
        return z;
    }

    /* JADX INFO: renamed from: a */
    public void requestPathToTarget(float f, float f2, int i, boolean z, boolean z2) {
        GameEngine gameEngine = GameEngine.getInstance();
        PathEngine pathEngine = gameEngine.pathfindingEngine;
        TileMap tileMap = gameEngine.tileMap;
        // RWX：所有寻路请求的唯一出口。目标可能在图外（追到地图外的敌人、编队槽位外扩、
        // AI 共享临时点、玩家点的黑边）。原版会让 Path 静默钳到边缘列，单位就一路开到边界停住。
        // 这里先钳进图内半格再交给求解器，越界情况记一次日志。
        {
            float rwxLimX = tileMap.getWorldWidth();
            float rwxLimY = tileMap.getWorldHeight();
            float rwxOx = f;
            float rwxOy = f2;
            if (rwxLimX > 0.0f) { f = f < 0.0f ? 0.0f : (f > rwxLimX - 1.0f ? rwxLimX - 1.0f : f); }
            if (rwxLimY > 0.0f) { f2 = f2 < 0.0f ? 0.0f : (f2 > rwxLimY - 1.0f ? rwxLimY - 1.0f : f2); }
            if ((f != rwxOx || f2 != rwxOy) && RWX_TARGET_CLAMP_N < 20) {
                RWX_TARGET_CLAMP_N++;
                io.github.rwx.probe.RwxPerf.out("RWX_TARGET_CLAMP " + rwxOx + "," + rwxOy + " -> " + f + "," + f2 + " type=" + getMovementType());
            }
        }
        this.isMoving = true;
        boolean z3 = false;
        boolean z4 = false;
        if (isAirborne()) {
            z3 = true;
        }
        tileMap.setCursorTileIndexFromWorldPoint(this.posX, this.posY);
        int i2 = tileMap.cursorTileX;
        int i3 = tileMap.cursorTileY;
        if (pathEngine.isTileBlockedForMovement(getMovementType(), i2, i3) && !pathEngine.b(getMovementType(), i2, i3)) {
            z3 = true;
            z4 = true;
        }
        if (f != this.lastPathTargetX || this.lastPathTargetY != f2) {
            this.pathRetryCount = (byte) 0;
        }
        this.lastPathTargetX = f;
        this.lastPathTargetY = f2;
        if (z3) {
            this.isPathIncomplete = false;
            this.activePathCount = 0;
            this.pathPositionProvider = null;
            float fClampWorldX = tileMap.clampWorldX(f);
            float fOpenOriginalMapStream = tileMap.clampWorldY(f2);
            if (z4) {
                float angleBetweenPoints = Utility.getAngleBetweenPoints(this.posX, this.posY, fClampWorldX, fOpenOriginalMapStream);
                float fDistance = Utility.distance(this.posX, this.posY, fClampWorldX, fOpenOriginalMapStream);
                if (fDistance > 60.0f) {
                    fDistance = 60.0f;
                    this.isPathIncomplete = true;
                    if (this.repathCooldown > 10.0f) {
                        this.repathCooldown = 10.0f;
                    }
                }
                fClampWorldX = this.posX + (Utility.fastCos(angleBetweenPoints) * fDistance);
                fOpenOriginalMapStream = this.posY + (Utility.fastSin(angleBetweenPoints) * fDistance);
            }
            setPathPosition(this.activePathCount, fClampWorldX, fOpenOriginalMapStream);
            this.activePathCount++;
            this.totalPathPositions = this.activePathCount;
            return;
        }
        int i4 = 0;
        if (z) {
            i4 = 3;
        }
        if (PathfindingUtils.a(getMovementType(), this.posX, this.posY, f, f2, 80, i4, 1)) {
            this.isPathIncomplete = false;
            this.activePathCount = 0;
            this.pathPositionProvider = null;
            float fClampWorldX2 = tileMap.clampWorldX(f);
            float fOpenOriginalMapStream2 = tileMap.clampWorldY(f2);
            float f3 = this.posX;
            float f4 = this.posY;
            float angleBetweenPoints2 = Utility.getAngleBetweenPoints(this.posX, this.posY, fClampWorldX2, fOpenOriginalMapStream2);
            float fDistance2 = Utility.distance(this.posX, this.posY, fClampWorldX2, fOpenOriginalMapStream2);
            float fFastCos = Utility.fastCos(angleBetweenPoints2);
            float fFastSin = Utility.fastSin(angleBetweenPoints2);
            int i5 = (int) ((fDistance2 * 0.05f) - 1.0f);
            int i6 = 1;
            if (i5 < 4) {
                i6 = 0;
            }
            int i7 = 0;
            while (true) {
                if (i7 >= i5) {
                    break;
                }
                f3 += fFastCos * 20.0f;
                f4 += fFastSin * 20.0f;
                if (i6 <= 0) {
                    setPathPosition(this.activePathCount, f3, f4);
                    this.activePathCount++;
                    if (this.activePathCount >= 119) {
                        this.isPathIncomplete = true;
                        break;
                    }
                } else {
                    i6--;
                }
                i7++;
            }
            if (!this.isPathIncomplete) {
                if (this.activePathCount < 119) {
                    setPathPosition(this.activePathCount, fClampWorldX2, fOpenOriginalMapStream2);
                    this.activePathCount++;
                } else {
                    this.isPathIncomplete = true;
                }
            }
            this.totalPathPositions = this.activePathCount;
            return;
        }
        FormationGroup formationGroup = null;
        UnitCommand currentWaypoint = getCurrentWaypoint();
        if (currentWaypoint != null) {
            formationGroup = currentWaypoint.transportTarget;
            if (formationGroup == null) {
            }
        }
        if (formationGroup != null && formationGroup.commandTargets != null) {
            CommandTarget commandTarget = null;
            for (CommandTarget commandTarget2 : formationGroup.commandTargets) {
                if (commandTarget2.path != null && commandTarget2.path.a() != null && Utility.abs(commandTarget2.targetX - f) <= 10.0f && Utility.abs(commandTarget2.targetY - f2) <= 10.0f && commandTarget2.createdTick + SlickToAndroidKeycodes.AndroidCodes.KEYCODE_STB_INPUT >= gameEngine.currentTick && commandTarget2.movementType == getMovementType() && Utility.distanceSq(this.posX, this.posY, commandTarget2.startX, commandTarget2.startY) < 3600.0f) {
                    commandTarget = commandTarget2;
                }
            }
            if (commandTarget != null) {
                this.aU = commandTarget.path;
                // RWX 自测（-Drwx.selftest=1）：把「路径点落在该移动类型不可走的地形上」直接判违规。
                // 这就是「海军路线穿过陆地」的自动检测器，不需要人盯着画面。
                if (RWX_SELFTEST && RWX_AUDIT_N < 40 && this.aU != null && this.aU.a() != null) {
                    java.util.LinkedList rwxPts = this.aU.a();
                    TileMap rwxTm = gameEngine.tileMap;
                    int rwxBad = 0;
                    int rwxChecked = 0;
                    for (Object rwxO : rwxPts) {
                        PathPoint rwxPt = (PathPoint) rwxO;
                        float rwxWX = (rwxPt.tileX * rwxTm.tileWorldSizeX) + rwxTm.halfTileWorldSizeX;
                        float rwxWY = (rwxPt.tileY * rwxTm.tileWorldSizeY) + rwxTm.halfTileWorldSizeY;
                        rwxChecked++;
                        if (com.corrodinggames.rts.gameFramework.utility.GameViewUtils.b(rwxWX, rwxWY, getMovementType()) < 0) {
                            rwxBad++;
                        }
                    }
                    if (rwxBad > 0) {
                        RWX_AUDIT_N++;
                        io.github.rwx.probe.RwxPerf.out("RWX_PATH_AUDIT illegal unit=" + (r() == null ? "?" : r().getUnitTypeDescriptionShort())
                            + " type=" + getMovementType() + " bad=" + rwxBad + "/" + rwxChecked
                            + " goal=" + this.aU.endTileX + "," + this.aU.endTileY + " map=" + rwxTm.tileCountX + "x" + rwxTm.tileCountY);
                    }
                }
                return;
            }
        }
        if (L && i > 2) {
            i = 2;
        }
        // RWX：发起前先做 O(1) 地形组预判——确认「没有任何可通行的地形抵达目的地」就干脆不去麻烦求解器，
        // 直接走与消费端同款的两级反应：第 1~3 次只记账（换目标会清零），第 4 次取消这个路径点。
        // 预判和求解器的孤岛判定读同一份静态分组表，各端一致，锁步安全。
        tileMap.setCursorTileIndexFromWorldPoint(f, f2);
        if (pathEngine.isEndOnSeparateIsland(getMovementType(), i2, i3, tileMap.cursorTileX, tileMap.cursorTileY, i)) {
            this.pathRetryCount = (byte) (this.pathRetryCount + 1);
            if (this.pathRetryCount > 3) {
                if (RW_PATH_STATS) {
                    rwUnreachableCancels++;
                    if (rwUnreachableCancels % 50 == 1) {
                        GameEngine.log("RWX_PATHSTATS unreachableCancels=" + rwUnreachableCancels);
                    }
                }
                this.aU = null;
                this.pathPositionProvider = null;
                clearPathData();
                this.isPathingActive = false;
                advanceWaypoint();
                return;
            }
            // 没到取消的门槛：不排求解器，让单位停在原地等下一次冷却再试
            this.isPathIncomplete = false;
            this.activePathCount = 0;
            this.totalPathPositions = 0;
            this.pathPositionProvider = null;
            this.aU = null;
            return;
        }
        this.aU = createPathToTarget(f, f2, i, z, true, z2);
    }

    /* JADX INFO: renamed from: a */
    public Path createPathToTarget(float f, float f2, int i, boolean z, boolean z2, boolean z3) {
        GameEngine gameEngine = GameEngine.getInstance();
        PathEngine pathEngine = gameEngine.pathfindingEngine;
        TileMap tileMap = gameEngine.tileMap;
        Path pathA = pathEngine.a(z2);
        tileMap.setCursorTileIndexFromWorldPoint(this.posX, this.posY);
        boolean z4 = false;
        if (bb() || this.isRotating) {
            z4 = true;
        }
        pathA.a(getMovementType(), (short) tileMap.cursorTileX, (short) tileMap.cursorTileY, Float.valueOf(this.rotationSpeed), z4);
        tileMap.setCursorTileIndexFromWorldPoint(f, f2);
        pathA.a((short) tileMap.cursorTileX, (short) tileMap.cursorTileY, (short) i);
        pathA.p = z;
        pathA.q = bh();
        pathA.isLowPriority = z3;
        boolean z5 = this.isMoving;
        this.isMoving = true;
        if (z2 && pathA.b()) {
            Iterator it = aV.iterator();
            while (it.hasNext()) {
                Path path = (Path) it.next();
                if (path.creationTick + 60 < gameEngine.currentTick) {
                    it.remove();
                } else if (path.a(pathA)) {
                    return path;
                }
            }
        }
        pathEngine.a(pathA, z2);
        this.isMoving = z5;
        if (z2 && pathA.b()) {
            aV.add(pathA);
        }
        return pathA;
    }

    /* JADX INFO: renamed from: b */
    void applyPendingPathResult(GameEngine gameEngine) {
        if (this.aU != null) {
            TileMap tileMap = gameEngine.tileMap;
            LinkedList linkedListA = this.aU.a();
            if (linkedListA != null) {
                this.pathPositionProvider = this.aU.a(this);
                Path path = this.aU;
                this.activePathCount = 0;
                this.isPathIncomplete = false;
                Iterator it = linkedListA.iterator();
                while (true) {
                    if (!it.hasNext()) {
                        break;
                    }
                    PathPoint pathPoint = (PathPoint) it.next();
                    tileMap.setCursorTileIndexFromTileIndex((int) pathPoint.tileX, (int) pathPoint.tileY);
                    setPathPosition(this.activePathCount, tileMap.cursorTileX + tileMap.halfTileWorldSizeX, tileMap.cursorTileY + tileMap.halfTileWorldSizeY);
                    this.activePathCount++;
                    if (this.activePathCount >= 120) {
                        this.isPathIncomplete = true;
                        break;
                    }
                }
                if (this.activePathCount == 1) {
                    this.pathRetryCount = (byte) (this.pathRetryCount + 1);
                }
                boolean z = false;
                if (linkedListA.size() != 0) {
                    tileMap.setCursorTileIndexFromWorldPoint(this.lastPathTargetX, this.lastPathTargetY);
                    if (!this.isPathIncomplete && ((PathPoint) linkedListA.getLast()).tileX == tileMap.cursorTileX && ((PathPoint) linkedListA.getLast()).tileY == tileMap.cursorTileY) {
                        z = true;
                    }
                }
                if (z) {
                    if (1 == 0) {
                        if (this.activePathCount < 120) {
                            setPathPosition(this.activePathCount, this.lastPathTargetX, this.lastPathTargetY);
                            this.activePathCount++;
                        }
                    } else {
                        if (this.activePathCount == 0) {
                            this.activePathCount++;
                        }
                        setPathPosition(this.activePathCount - 1, this.lastPathTargetX, this.lastPathTargetY);
                    }
                }
                // RWX：终点落在图边 = 典型「奔边界」。记下真实目标与末点，用来分辨是目标本身在边界，
                // 还是求解器把终点搬到了边界（fake-end）。mapSize 一并记，防止钳制范围算错。
                // RWX：路径落点探针。只记录「可疑」的路径：航点里有一个贴到地图边缘（1 格内），
                // 或者最后一个航点不是命令目标，或者判定不可达。目的：老师再遇到「跑向西北角」时，
                // 不用猜，直接从 logs/rwx-prof.log 里读整条航点清单。默认开启，最多 200 行。
                if (RWX_PATH_TRACE && RWX_PATH_TRACE_N < 200) {
                    StringBuilder rwxSb = new StringBuilder();
                    int rwxEdge = 0;
                    int rwxLastX = -1;
                    int rwxLastY = -1;
                    int rwxW = tileMap.tileCountX;
                    int rwxH = tileMap.tileCountY;
                    for (int rwxI = 0; rwxI < this.activePathCount; rwxI++) {
                        PositionData rwxPd = this.pathPositions[rwxI];
                        if (rwxPd == null) { continue; }
                        int rwxTx = (int) (rwxPd.posX * tileMap.tileScaleX);
                        int rwxTy = (int) (rwxPd.posY * tileMap.tileScaleY);
                        if (rwxTx <= 1 || rwxTy <= 1 || rwxTx >= rwxW - 2 || rwxTy >= rwxH - 2) { rwxEdge++; }
                        rwxLastX = rwxTx;
                        rwxLastY = rwxTy;
                        rwxSb.append(rwxTx).append(",").append(rwxTy).append(" ");
                    }
                    boolean rwxSuspicious = rwxEdge > 0 || this.aU.endUnreachable
                        || (rwxLastX >= 0 && (rwxLastX != this.aU.endTileX || rwxLastY != this.aU.endTileY));
                    if (rwxSuspicious) {
                        RWX_PATH_TRACE_N++;
                        io.github.rwx.probe.RwxPerf.out("RWX_PATHTRACE unit=" + (r() == null ? "?" : r().getUnitTypeDescriptionShort())
                            + " mtype=" + getMovementType()
                            + " targetTile=" + this.aU.endTileX + "," + this.aU.endTileY
                            + " startTile=" + this.aU.startTileX + "," + this.aU.startTileY
                            + " map=" + rwxW + "x" + rwxH
                            + " nodes=" + this.activePathCount
                            + " edgeTouches=" + rwxEdge
                            + " unreachable=" + this.aU.endUnreachable
                            + " lastWp=" + rwxLastX + "," + rwxLastY
                            + " wps=[" + rwxSb.toString().trim() + "]");
                    }
                }
                if (!this.isPathIncomplete && this.activePathCount > 0 && RWX_EDGE_PATH_N < 30) {
                    PositionData rwxLast = this.pathPositions[this.activePathCount - 1];
                    if (rwxLast != null) {
                        int rwxLTX = (int) (rwxLast.posX * tileMap.tileScaleX);
                        int rwxLTY = (int) (rwxLast.posY * tileMap.tileScaleY);
                        boolean rwxOnEdge = rwxLTX <= 1 || rwxLTY <= 1 || rwxLTX >= tileMap.tileCountX - 2 || rwxLTY >= tileMap.tileCountY - 2;
                        if (rwxOnEdge) {
                            tileMap.setCursorTileIndexFromWorldPoint(this.lastPathTargetX, this.lastPathTargetY);
                            boolean rwxTargetOnEdge = tileMap.cursorTileX <= 1 || tileMap.cursorTileY <= 1 || tileMap.cursorTileX >= tileMap.tileCountX - 2 || tileMap.cursorTileY >= tileMap.tileCountY - 2;
                            if (!rwxTargetOnEdge) {
                                RWX_EDGE_PATH_N++;
                                io.github.rwx.probe.RwxPerf.out("RWX_EDGE_PATH unit=" + (r() == null ? "?" : r().getUnitTypeDescriptionShort()) + " type=" + getMovementType() + " target=" + (int) this.lastPathTargetX + "," + (int) this.lastPathTargetY + " lastTile=" + rwxLTX + "," + rwxLTY + " map=" + tileMap.tileCountX + "x" + tileMap.tileCountY + " nodes=" + this.activePathCount + " unreachable=" + this.aU.endUnreachable
                                    + " goalTile=" + this.aU.endTileX + "," + this.aU.endTileY
                                    + " startTile=" + this.aU.startTileX + "," + this.aU.startTileY
                                    + " solver=" + System.getProperty("rwx.pathing", "2"));
                            }
                            // RWX：老师要求——路径末点落在边界、真正的目标又不在边界时，不要走到那个边界点再重算，
                            // 直接把这条路径丢掉，让单位立刻重新寻路到目的地。原来会先走过去再停下重算，
                            // 看起来就是一卡一顿（要等 pathRetryTimer 的 40/200/600ms 阈值）。
                            if (this.lastPathTargetX != this.rwxBorderDropLastX || this.lastPathTargetY != this.rwxBorderDropLastY) {
                                this.rwxBorderDropLastX = this.lastPathTargetX;
                                this.rwxBorderDropLastY = this.lastPathTargetY;
                                this.rwxBorderDropCount = 0;
                            }
                            if (this.rwxBorderDropCount < 3) {
                                this.rwxBorderDropCount++;
                                io.github.rwx.probe.RwxPerf.out("RWX_BORDER_DROP unit=" + (r() == null ? "?" : r().getUnitTypeDescriptionShort())
                                    + " lastTile=" + rwxLTX + "," + rwxLTY + " goal=" + this.aU.endTileX + "," + this.aU.endTileY
                                    + " map=" + tileMap.tileCountX + "x" + tileMap.tileCountY + " drop#" + this.rwxBorderDropCount);
                                clearPathData();
                            }
                        }
                            // RWX：老师要求——拿到路径就检查，凡是「离目的地过远」的航点一律取消，
                            // 不要等单位走到那里再重算。判定：航点贴地图边界（1 格内）、或者到目的地的
                            // 距离比「整条路径里离目的地最近的那个航点」还远出 40 像素以上（绕远路）。
                            // 命中就丢掉整条路，下一 tick 立刻从当前位置重新寻路到目的地；同一目标最多 3 次。
                            {
                                float rwxGx = this.lastPathTargetX;
                                float rwxGy = this.lastPathTargetY;
                                float rwxMinD = Float.MAX_VALUE;
                                int rwxMinIdx = 0;
                                boolean rwxDetour = false;
                                for (int rwxK = 0; rwxK < this.activePathCount; rwxK++) {
                                    PositionData rwxP = this.pathPositions[rwxK];
                                    if (rwxP == null) { continue; }
                                    float rwxDx = rwxP.posX - rwxGx;
                                    float rwxDy = rwxP.posY - rwxGy;
                                    float rwxD = rwxDx * rwxDx + rwxDy * rwxDy;
                                    if (rwxD < rwxMinD) { rwxMinD = rwxD; rwxMinIdx = rwxK; }
                                }
                                for (int rwxK = 0; rwxK < this.activePathCount; rwxK++) {
                                    PositionData rwxP = this.pathPositions[rwxK];
                                    if (rwxP == null) { continue; }
                                    int rwxTx = (int) (rwxP.posX * tileMap.tileScaleX);
                                    int rwxTy = (int) (rwxP.posY * tileMap.tileScaleY);
                                    boolean rwxEdgeWp = rwxTx <= 1 || rwxTy <= 1 || rwxTx >= tileMap.tileCountX - 2 || rwxTy >= tileMap.tileCountY - 2;
                                    float rwxDx = rwxP.posX - rwxGx;
                                    float rwxDy = rwxP.posY - rwxGy;
                                    float rwxD = rwxDx * rwxDx + rwxDy * rwxDy;
                                    if (rwxEdgeWp || rwxD > rwxMinD + 40f * 40f) { rwxDetour = true; break; }
                                }
                                if (rwxDetour) {
                                    if (this.lastPathTargetX != this.rwxBorderDropLastX || this.lastPathTargetY != this.rwxBorderDropLastY) {
                                        this.rwxBorderDropLastX = this.lastPathTargetX;
                                        this.rwxBorderDropLastY = this.lastPathTargetY;
                                        this.rwxBorderDropCount = 0;
                                    }
                                    if (this.rwxBorderDropCount < 3) {
                                        this.rwxBorderDropCount++;
                                        // 把 rwxMinIdx 之前的「过远航点」整段删掉，只保留从离目的地最近的航点开始的尾巴。
                                        int rwxKeep = this.activePathCount - rwxMinIdx;
                                        for (int rwxM = 0; rwxM < rwxKeep; rwxM++) {
                                            this.pathPositions[rwxM] = this.pathPositions[rwxMinIdx + rwxM];
                                        }
                                        this.activePathCount = rwxKeep;
                                        io.github.rwx.probe.RwxPerf.out("RWX_BORDER_DROP unit=" + (r() == null ? "?" : r().getUnitTypeDescriptionShort())
                                            + " dropped=" + rwxMinIdx + " kept=" + rwxKeep + " goal=" + this.aU.endTileX + "," + this.aU.endTileY
                                            + " map=" + tileMap.tileCountX + "x" + tileMap.tileCountY + " drop#" + this.rwxBorderDropCount);
                                    }
                                }
                            }
                    }
                }
                // RWX：寻路器确认「没有任何可通行的地形能抵达目标」时不再走向最近可达点空转。
                // 每确认一次加一次 pathRetryCount（换目标时 requestPathToTarget 会清零，不影响重试新目的地）；
                // 第 4 次确认仍不可达就取消这个路径点——与 1869 行既有的放弃行为同款。
                if (this.aU.endUnreachable && !this.isPathIncomplete) {
                    this.pathRetryCount = (byte) (this.pathRetryCount + 1);
                    if (this.pathRetryCount > 3) {
                        if (RW_PATH_STATS) {
                            rwUnreachableCancels++;
                            if (rwUnreachableCancels % 50 == 1) {
                                GameEngine.log("RWX_PATHSTATS unreachableCancels=" + rwUnreachableCancels);
                            }
                        }
                        this.aU = null;
                        // provider 里存着 Path 的引用，Path 会被还回对象池复用，必须断开
                        this.pathPositionProvider = null;
                        clearPathData();
                        this.isPathingActive = false;
                        // RWX：不再丢弃路径点。否则单位停在最近可达点后命令直接消失，
                        // 看起来就是「死死卡在岸边」。保留命令，隔 10 秒再试一次。
                        if (this.repathCooldown < 10.0f) {
                            this.repathCooldown = 10.0f;
                        }
                        return;
                    }
                }
                this.aU = null;
                if (this.activePathCount > 120) {
                    GameEngine.logColored("activePathCount>maxPathNodes: activePathCount:" + this.activePathCount);
                    this.activePathCount = 120;
                }
                this.totalPathPositions = this.activePathCount;
            }
        }
    }

    /* JADX INFO: renamed from: aL */
    public long getPathChecksum() {
        long jFloatToRawIntBits = 0;
        for (int i = 0; i < this.activePathCount; i++) {
            PositionData positionData = this.pathPositions[i];
            if (positionData != null) {
                jFloatToRawIntBits = jFloatToRawIntBits + ((long) Float.floatToRawIntBits(positionData.posX)) + ((long) Float.floatToRawIntBits(positionData.posY));
            }
        }
        return jFloatToRawIntBits;
    }

    /* JADX INFO: renamed from: o */
    PositionData getPathPositionAt(int i) {
        if (this.pathPositionProvider != null) {
            if (i == 0) {
                return getCurrentPathPosition();
            }
            return getNextPathPosition();
        }
        if (i >= this.activePathCount) {
            return null;
        }
        return this.pathPositions[i];
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit, com.corrodinggames.rts.gameFramework.GameObject
    public void d(float f) {
        super.d(f);
    }

    /* JADX INFO: renamed from: aM */
    public float getPathStepScale() {
        return 1.0f;
    }

    /* JADX INFO: renamed from: l */
    public int getSubmergedRenderAlpha(float f) {
        if (f < -0.3f) {
            int i = (int) (((1.0f - ((-f) / 10.0f)) * 130.0f) + 45.0f);
            if (i < 45) {
                i = 45;
            }
            return i;
        }
        return 255;
    }

    /* JADX INFO: renamed from: aN */
    public Paint getRenderPaint() {
        int iA;
        BlendColorFilter blendColorFilter = null;
        if (this.posZ < -0.3f) {
            iA = ArgbColor.a(getSubmergedRenderAlpha(this.posZ), 255, 255, 255);
        } else {
            iA = -1;
        }
        if (this.buildProgress < 1.0f && this.buildProgress < getPathStepScale()) {
            iA = ArgbColor.a((int) (20.0f + ((this.buildProgress / getPathStepScale()) * 220.0f)), 140, 255, 140);
            blendColorFilter = overlayFilterLightGreen;
        }
        if (this.isUnitParalyzed) {
            if (this.isUnitDisabled) {
                iA = ArgbColor.a(200, 20, 255, 20);
                blendColorFilter = overlayFilterGreen;
            }
            if (this.isUnitCapturable) {
                iA = ArgbColor.a(200, 255, 20, 20);
                blendColorFilter = overlayFilterRed;
            }
            if (this.isUnitInvulnerable) {
                iA = ArgbColor.a(50, 70, 70, 245);
                blendColorFilter = overlayFilterBlue;
                if (this.isUnitCapturable) {
                    iA = ArgbColor.a(50, 255, 20, 20);
                    blendColorFilter = overlayFilterRed;
                }
            }
            if (this.isUnitUntargetable) {
                iA = ArgbColor.a(150, 100, 100, 100);
            }
        }
        return a(iA, blendColorFilter, isRenderAntiAliasEnabled());
    }

    /* JADX INFO: renamed from: aO */
    public boolean isRenderAntiAliasEnabled() {
        GameEngine gameEngine = GameEngine.getInstance();
        boolean z = gameEngine.settingsEngine.renderAntiAlias;
        if (!dk()) {
            z = false;
            if (gameEngine.zoom < 1.0f) {
                z = true;
            }
        }
        if (this.isUnitStunned) {
            z = UnitTypeEnum.ag;
        }
        return z;
    }

    public float p(int i) {
        return 1.0f;
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit, com.corrodinggames.rts.gameFramework.GameObject
    public boolean c(float f) {
        GameEngine gameEngine = GameEngine.getInstance();
        GraphicsEngine graphicsEngine = gameEngine.renderGraphicsEngine;
        Paint renderPaint = getRenderPaint();
        float fCD = getRenderScale();
        if (this.ew) {
            PointF pointFCY = getRenderOffset();
            float f2 = (this.posX + pointFCY.x) - gameEngine.viewpointXSnapped;
            float f3 = ((this.posY + pointFCY.y) - gameEngine.viewpointYSnapped) - this.posZ;
            drawShadow();
            if (fCD != 1.0f) {
                graphicsEngine.k();
                graphicsEngine.a(fCD, fCD, f2, f3);
            }
            graphicsEngine.a(this.baseTexture, f2, f3, getRenderRotation(false) - 90.0f, renderPaint);
            if (fCD != 1.0f) {
                graphicsEngine.l();
                return true;
            }
            return true;
        }
        PointF pointFCY2 = getRenderOffset();
        RectF rectFCF = getUnitBounds();
        float f4 = pointFCY2.x;
        float f5 = pointFCY2.y - this.posZ;
        rectFCF.a += f4;
        rectFCF.b += f5;
        rectFCF.c += f4;
        rectFCF.d += f5;
        Rect rectA_ = a_(false);
        float f6 = (rectFCF.a + rectFCF.c) * 0.5f;
        float f7 = (rectFCF.b + rectFCF.d) * 0.5f;
        graphicsEngine.k();
        drawShadow();
        if (fCD != 1.0f) {
            graphicsEngine.a(fCD, fCD, f6, f7);
        }
        graphicsEngine.a(getRenderRotation(false), f6, f7);
        graphicsEngine.a(this.baseTexture, rectA_, rectFCF, renderPaint);
        graphicsEngine.l();
        return true;
    }

    /* JADX INFO: renamed from: F */
    public boolean canDrawShadow() {
        return this.posZ > 0.0f && this.buildProgress >= 1.0f && !this.isUnitInvulnerable;
    }

    /* JADX INFO: renamed from: aP */
    public PointF getShadowOffset() {
        tempPointF1.a(getShadowOffsetX(), getShadowOffsetY());
        return tempPointF1;
    }

    /* JADX INFO: renamed from: G */
    public float getShadowOffsetX() {
        return 0.0f;
    }

    /* JADX INFO: renamed from: H */
    public float getShadowOffsetY() {
        return 0.0f;
    }

    /* JADX INFO: renamed from: aQ */
    public boolean drawShadow() {
        if (this.shadowTexture != null && canDrawShadow()) {
            GameEngine gameEngine = GameEngine.getInstance();
            if (!gameEngine.shouldDrawSmallUnitShadows && this.radius < 18.0f && this.posZ < 0.5d) {
                return true;
            }
            if (!gameEngine.shouldDrawUnitShadows && this.radius < 28.0f && this.posZ < 5.0f) {
                return true;
            }
            PointF shadowOffset = getShadowOffset();
            float f = (this.posX + shadowOffset.x) - gameEngine.viewpointXSnapped;
            float f2 = (this.posY + shadowOffset.y) - gameEngine.viewpointYSnapped;
            float fCD = getRenderScale();
            GraphicsEngine graphicsEngine = gameEngine.renderGraphicsEngine;
            if (fCD != 1.0f) {
                graphicsEngine.k();
                graphicsEngine.a(fCD, fCD, f, f2);
            }
            if (hasShadowFrames()) {
                Rect rectA_ = a_(true);
                RectF rectF = dB;
                rectF.a(f - this.eu, f2 - this.ev, f + this.eu, f2 + this.ev);
                graphicsEngine.k();
                graphicsEngine.a(getRenderRotation(true), f, f2);
                graphicsEngine.a(this.shadowTexture, rectA_, rectF, getSelectionPaint());
                graphicsEngine.l();
            } else {
                graphicsEngine.a(this.shadowTexture, f, f2, getRenderRotation(true) - 90.0f, getSelectionPaint());
            }
            if (fCD != 1.0f) {
                graphicsEngine.l();
                return true;
            }
            return true;
        }
        return false;
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    /* JADX INFO: renamed from: s_ */
    public boolean isVisibleOnScreen() {
        return RectF.a(GameEngine.getInstance().visibleScreenRect, getVisibilityBounds());
    }

    /* JADX INFO: renamed from: aR */
    public boolean canExecuteMovementCommands() {
        AttachmentSlotDefinition attachmentSlotDefinitionDn = dn();
        if (attachmentSlotDefinitionDn != null && !attachmentSlotDefinitionDn.O) {
            return false;
        }
        return I();
    }

    public boolean aS() {
        return canExecuteMovementCommands();
    }

    /* JADX INFO: renamed from: b_ */
    public boolean requiresFacingForActions() {
        return true;
    }

    /* JADX INFO: renamed from: aT */
    public int getDefaultTurretIndex() {
        return -1;
    }

    /* JADX INFO: renamed from: o */
    public float getEngagementRange(BaseUnit baseUnit) {
        if (useVelocityExtendedRange() && baseUnit != null) {
            return m() + this.radius + baseUnit.radius;
        }
        return m();
    }

    /* JADX INFO: renamed from: p */
    public float getPathingEngagementRange(BaseUnit baseUnit) {
        if (useVelocityExtendedRange() && baseUnit != null) {
            return getBasePathingRange() + this.radius + baseUnit.radius;
        }
        return getBasePathingRange();
    }

    /* JADX INFO: renamed from: aU */
    public float getBasePathingRange() {
        return m();
    }

    /* JADX INFO: renamed from: q */
    public int getPathingTargetRadiusTiles(BaseUnit baseUnit) {
        GameEngine gameEngine = GameEngine.getInstance();
        int i = 0;
        float pathingEngagementRange = getPathingEngagementRange(baseUnit);
        if (pathingEngagementRange > 58.0f) {
            i = (int) ((pathingEngagementRange - 41.0f) / (gameEngine.tileMap.tileWorldSizeX * 1.414f));
        }
        return i;
    }

    /* JADX INFO: renamed from: aV */
    public boolean useVelocityExtendedRange() {
        return false;
    }

    public float q(int i) {
        return 0.0f;
    }

    /* JADX INFO: renamed from: aW */
    public void clampMovementLevelRotations() {
        int techLevel = getTechLevel();
        for (int i = 0; i < techLevel; i++) {
            if (i < this.movementLevels.length) {
                UnitMovementData unitMovementData = this.movementLevels[i];
                if (unitMovementData.rotation > b(i)) {
                    unitMovementData.rotation = b(i);
                }
            }
        }
    }


    public ArrayList<UnitStatistics> collectMovementLevelStatistics() {
        ArrayList<UnitStatistics> var1 = new ArrayList();
        if (this.canAttack()) {
            int var2 = this.getTechLevel();

            for (int var3 = 0; var3 < var2; var3++) {
                float var4 = this.q(var3);
                if (var4 != 0.0F) {
                    float var5 = this.b(var3);
                    if (var5 == 9000.0F) {
                        var5 = 0.0F;
                    }

                    boolean var6 = false;

                    for (UnitStatistics var8 : var1) {
                        if (var8.a == var4 && (var8.b == var5 || var5 == 0.0F || var8.b == 0.0F)) {
                            var8.count++;
                            if (var8.b == 0.0F) {
                                var8.b = var5;
                            }

                            var6 = true;
                            break;
                        }
                    }

                    if (!var6) {
                        UnitStatistics var9 = new UnitStatistics();
                        var9.a = var4;
                        var9.b = var5;
                        var9.c = this.e(var3);
                        var1.add(var9);
                    }
                }
            }
        }

        return var1;
    }

    public boolean r(int i) {
        return true;
    }

    public float e(int i) {
        return 0.0f;
    }

    public boolean s(int i) {
        return false;
    }

    public float t(int i) {
        return 0.0f;
    }

    public float f(int i) {
        return 4.0f;
    }

    public boolean u(int i) {
        int linkedTurretIndex = getLinkedTurretIndex(i);
        if (linkedTurretIndex == -1) {
            return this.movementLevels[i].isActive;
        }
        return this.movementLevels[linkedTurretIndex].isActive;
    }

    /* JADX INFO: renamed from: v */
    public int getLinkedTurretIndex(int i) {
        return -1;
    }

    public float B() {
        return -1.0f;
    }

    public float w(int i) {
        return -1.0f;
    }

    public float x(int i) {
        return 5.0f;
    }

    public float y(int i) {
        return w(i);
    }

    public boolean E() {
        return false;
    }

    public boolean aY() {
        return false;
    }

    /* JADX INFO: renamed from: aZ */
    public float getMoveYAxisScaling() {
        return 1.0f;
    }

    /* JADX INFO: renamed from: ba */
    public float getMoveYAxisScalingInverse() {
        return 1.0f;
    }

    public boolean bb() {
        return bc() > 0.95f;
    }

    public float bc() {
        return 0.6f;
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    public float bd() {
        return 0.0f;
    }

    public UnitBehaviorType be() {
        return UnitBehaviorType.normal;
    }

    public boolean bf() {
        return true;
    }

    public boolean bg() {
        return true;
    }

    public int bh() {
        return 0;
    }

    /* JADX INFO: renamed from: C */
    public float getMoveAccelerationSpeed() {
        return 99.0f;
    }

    public float D() {
        return 99.0f;
    }

    /* JADX INFO: renamed from: bi */
    public boolean isSlidingMovement() {
        return false;
    }

    /* JADX INFO: renamed from: bj */
    public boolean isIgnoreMoveOrders() {
        return false;
    }

    public boolean b(int i, float f) {
        return true;
    }

    public boolean bk() {
        return false;
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    /* JADX INFO: renamed from: bl */
    public int getTechLevel() {
        return 1;
    }

    public boolean bm() {
        return true;
    }

    public float g(int i) {
        return 0.0f;
    }

    /* JADX INFO: renamed from: z */
    public float getTurretTargetSearchRange(int i) {
        return 99999.0f;
    }

    public float A(int i) {
        return -1.0f;
    }

    public float B(int i) {
        return 0.0f;
    }

    public float C(int i) {
        if (this.isRotating && bb()) {
            return this.rotationSpeed + 180.0f;
        }
        return this.rotationSpeed;
    }

    public Vector3D bn() {
        int defaultTurretIndex = getDefaultTurretIndex();
        if (defaultTurretIndex == -1) {
            return D(0);
        }
        return D(defaultTurretIndex);
    }

    public Vector3D D(int i) {
        bf.a(E(i));
        return bf;
    }

    public PointF E(int i) {
        UnitMovementData unitMovementData = this.movementLevels[i];
        float fG = g(i);
        float f = E() ? this.rotationSpeed : unitMovementData.targetX;
        PointF pointFG = G(i);
        tempPointF2.a(pointFG.x + (Utility.fastCos(f) * fG), pointFG.y + (Utility.fastSin(f) * fG));
        return tempPointF2;
    }

    public Vector3D F(int i) {
        bi.a(G(i));
        bi.z = 0.0f;
        return bi;
    }

    public PointF G(int i) {
        UnitMovementData unitMovementData = this.movementLevels[i];
        float fFastCos = this.posX;
        float fFastSin = this.posY;
        float fH = H(i);
        if (unitMovementData.rotation != 0.0f && fH != 0.0f) {
            float fI = I(i);
            float fJ = J(i);
            float f = 0.0f;
            float fB = b(i) - unitMovementData.rotation;
            if (fB < fI) {
                f = (fB / fI) * fH;
            } else if (fB < fJ + fI) {
                f = fH - (((fB - fI) / fJ) * fH);
            }
            if (f != 0.0f) {
                fFastCos += Utility.fastCos(unitMovementData.targetX) * f;
                fFastSin += Utility.fastSin(unitMovementData.targetX) * f;
            }
        }
        tempPointF3.a(fFastCos, fFastSin);
        return tempPointF3;
    }

    public float H(int i) {
        return 0.0f;
    }

    public float I(int i) {
        return 4.0f;
    }

    public float J(int i) {
        return 6.0f;
    }

    /* JADX INFO: renamed from: K */
    public PointF getShadowOffsetForLevel(int i) {
        PointF pointF = tempPointF4;
        pointF.a(0.0f, 0.0f);
        UnitMovementData unitMovementData = this.movementLevels[i];
        pointF.x += unitMovementData.shadowOffsetX;
        pointF.y += unitMovementData.shadowOffsetY;
        return pointF;
    }

    public float L(int i) {
        return 0.6f;
    }

    public void M(int i) {
        if (i == -1) {
            int techLevel = getTechLevel();
            for (int i2 = 0; i2 < techLevel; i2++) {
                M(i2);
            }
            return;
        }
        UnitMovementData unitMovementData = this.movementLevels[i];
        unitMovementData.shadowOffsetX = 0.0f;
        unitMovementData.shadowOffsetY = 0.0f;
        if (this.attackTarget != null && L(i) != 0.0f) {
            float fL = this.attackTarget.radius * L(i);
            unitMovementData.shadowOffsetX += Utility.getDeterministicRandomInt((GameObject) this, (int) (-fL), (int) fL, 1 + i);
            unitMovementData.shadowOffsetY += Utility.getDeterministicRandomInt((GameObject) this, (int) (-fL), (int) fL, 2 + i);
        }
    }

    public void a(UnitSize unitSize) {
        a(unitSize, true);
    }

    public void a(UnitSize unitSize, boolean z) {
        Effect effectCreateSmallExplosionInternal;
        GameEngine gameEngine = GameEngine.getInstance();
        if (unitSize == UnitSize.verylargeBuilding) {
            gameEngine.soundEngine.playSound(SoundEngine.buildingExplodeSound, 0.8f, this.posX, this.posY);
            gameEngine.effectManager.createExplosion(this.posX, this.posY, this.posZ);
            gameEngine.effectManager.setOverrideEffectQuality(EffectQuality.critical);
            Effect effectCreateSmallExplosion = gameEngine.effectManager.createSmallExplosion(this.posX, this.posY, this.posZ, -1127220);
            if (effectCreateSmallExplosion != null) {
                effectCreateSmallExplosion.scaleFrom = 0.2f;
                effectCreateSmallExplosion.scaleTo = 2.0f;
                effectCreateSmallExplosion.drawLayer = (short) 2;
                effectCreateSmallExplosion.lifeTimer = 45.0f;
                effectCreateSmallExplosion.lifeMax = effectCreateSmallExplosion.lifeTimer;
                effectCreateSmallExplosion.delayedStartTimer = 0.0f;
            }
        } else if (unitSize == UnitSize.large || unitSize == UnitSize.building || unitSize == UnitSize.buildingNoShockwaveOrSmoke) {
            gameEngine.soundEngine.playSound(SoundEngine.buildingExplodeSound, 0.8f, this.posX, this.posY);
            gameEngine.effectManager.createExplosion(this.posX, this.posY, this.posZ);
        } else if (unitSize == UnitSize.verysmall) {
            gameEngine.soundEngine.playSoundAt(SoundEngine.unitExplodeSound, 0.4f, 1.0f + Utility.randomFloatInRange(-0.07f, 0.07f), this.posX, this.posY);
            gameEngine.effectManager.createSmallExplosion(this.posX, this.posY, this.posZ);
        } else if (unitSize == UnitSize.largeUnit) {
            gameEngine.soundEngine.playSoundAt(SoundEngine.unitExplodeSound, 0.8f, 1.0f + Utility.randomFloatInRange(-0.07f, 0.07f), this.posX, this.posY);
            gameEngine.effectManager.createSmallExplosion(this.posX, this.posY, this.posZ);
            gameEngine.effectManager.setOverrideEffectQuality(EffectQuality.critical);
            Effect effectCreateSmallExplosion2 = gameEngine.effectManager.createSmallExplosion(this.posX, this.posY, this.posZ, -1127220);
            if (effectCreateSmallExplosion2 != null) {
                effectCreateSmallExplosion2.scaleFrom = 0.2f;
                effectCreateSmallExplosion2.scaleTo = 2.0f;
                effectCreateSmallExplosion2.drawLayer = (short) 2;
                effectCreateSmallExplosion2.lifeTimer = 45.0f;
                effectCreateSmallExplosion2.lifeMax = effectCreateSmallExplosion2.lifeTimer;
                effectCreateSmallExplosion2.delayedStartTimer = 0.0f;
            }
        } else {
            gameEngine.soundEngine.playSoundAt(SoundEngine.unitExplodeSound, 0.8f, 1.0f + Utility.randomFloatInRange(-0.07f, 0.07f), this.posX, this.posY);
            gameEngine.effectManager.createSmallExplosion(this.posX, this.posY, this.posZ);
        }
        if (unitSize != UnitSize.verysmall) {
            if (unitSize != UnitSize.buildingNoShockwaveOrSmoke && (effectCreateSmallExplosionInternal = gameEngine.effectManager.createSmallExplosionInternal(this.posX, this.posY, this.posZ, 0)) != null) {
                effectCreateSmallExplosionInternal.alpha = 0.9f;
            }
            if (z) {
                if (!bO()) {
                    bo();
                }
                if (unitSize != UnitSize.buildingNoShockwaveOrSmoke && !isOverLiquid()) {
                    EffectEmitter.createDefaultFireEmitter(this.posX, this.posY);
                    EffectEmitter.createAlternateFireEmitter(this.posX, this.posY);
                    bq();
                }
            }
        }
    }

    public void bo() {
        GameEngine gameEngine = GameEngine.getInstance();
        float f = 1.0f;
        float f2 = 1.0f;
        int iBp = bp();
        if (iBp >= 10) {
            f = 1.2f;
            f2 = 1.4f;
        }
        if (iBp >= 20) {
            f = 1.5f;
            f2 = 1.7f;
        }
        if (this.posZ > -1.0f) {
            for (int i = 0; i < iBp; i++) {
                gameEngine.effectManager.createBloodEffectInternal(this.posX, this.posY, this.posZ, f, f2);
            }
        }
    }

    public int bp() {
        if (isExperimental()) {
            return 8;
        }
        if (bI()) {
            return 7;
        }
        return 4;
    }

    public void bq() {
        if (!isOverLiquid()) {
            ScorchMark.a(this.posX, this.posY);
        }
    }

    public int s() {
        return 15;
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    public void c(boolean z) {
        int iS;
        GameEngine gameEngine = GameEngine.getInstance();
        if (this.transportContainer == null && this.parentEntity == null && (iS = s()) > 0) {
            gameEngine.tileMap.updateFogVisibilityForTeamsAtWorldPoint(this.posX, this.posY, iS, this.team, z);
        }
    }

    public void br() {
        GameEngine gameEngine = GameEngine.getInstance();
        RectF rectF = new RectF();
        rectF.a(cd());
        rectF.b *= gameEngine.tileMap.tileWorldSizeY;
        rectF.d *= gameEngine.tileMap.tileWorldSizeY;
        rectF.a *= gameEngine.tileMap.tileWorldSizeX;
        rectF.c *= gameEngine.tileMap.tileWorldSizeX;
        rectF.a(this.posX, this.posY);
        rectF.a(-getTileOffsetX(), -getTileOffsetY());
        rectF.b -= 10.0f;
        rectF.d += 10.0f;
        rectF.a -= 10.0f;
        rectF.c += 10.0f;
        for (BaseUnit baseUnit : BaseUnit.getGlobalUnitList()) {
            if ((baseUnit instanceof BaseUnit) && baseUnit != this && baseUnit.isWithinRect(rectF)) {
                if ((baseUnit instanceof OrderableUnit) && baseUnit.isDead) {
                    baseUnit.remove();
                }
                if (baseUnit instanceof Tree) {
                    ((Tree) baseUnit).k();
                }
            }
        }
    }

    /* JADX INFO: renamed from: c */
    public boolean canPlaceAtCurrentPosition(PlayerTeam playerTeam) {
        return getPlacementFailureReason(false, playerTeam) == null;
    }

    /* JADX INFO: renamed from: a */
    public boolean canPlaceAtCurrentPositionWithIgnoreUnits(boolean z, PlayerTeam playerTeam) {
        return getPlacementFailureReason(z, playerTeam) == null;
    }

    /* JADX INFO: renamed from: b */
    public String getPlacementFailureReason(boolean z, PlayerTeam playerTeam) {
        String strA;
        GameEngine gameEngine = GameEngine.getInstance();
        PlacementRules placementRulesQ = r().q();
        if (placementRulesQ != null && (strA = placementRulesQ.a(this, this.posX, this.posY)) != null) {
            return strA;
        }
        if (r().p()) {
            gameEngine.tileMap.setCursorTileIndexFromWorldPoint(this.posX, this.posY);
            MapTile pathingOverrideTileAt = gameEngine.tileMap.getPathingOverrideTileAt(gameEngine.tileMap.cursorTileX, gameEngine.tileMap.cursorTileY);
            if (pathingOverrideTileAt == null || !pathingOverrideTileAt.isResourcePool) {
                return "{2}";
            }
        }
        if (!z && hasBlockingUnitNearby((BaseUnit) null, playerTeam)) {
            return "{0}";
        }
        if (!r().p()) {
            Rect rectCd = cd();
            Point pointA = a(gameEngine.tileMap, tempPoint1);
            int i = pointA.worldX;
            int i2 = pointA.worldY;
            UnitType unitTypeR = r();
            UnitMovementType unitMovementTypeO = unitTypeR.o();
            for (int i3 = i + rectCd.a; i3 <= i + rectCd.c; i3++) {
                for (int i4 = i2 + rectCd.b; i4 <= i2 + rectCd.d; i4++) {
                    String strA2 = BaseBuilding.a(this, unitTypeR, unitMovementTypeO, i3, i4, false, playerTeam);
                    if (strA2 != null) {
                        return strA2;
                    }
                }
            }
            return null;
        }
        return null;
    }

    /* JADX INFO: renamed from: N */
    public void drawPlacementOverlay(int i) {
        GameEngine gameEngine = GameEngine.getInstance();
        if (!r().p()) {
            Rect rectCd = cd();
            Point pointA = a(gameEngine.tileMap, tempPoint2);
            int i2 = pointA.worldX;
            int i3 = pointA.worldY;
            r();
            int i4 = i2 + rectCd.a;
            int i5 = i3 + rectCd.b;
            int i6 = i2 + rectCd.c;
            int i7 = i3 + rectCd.d;
            if (i != -2) {
                gameEngine.tileMap.renderBuildPlacementOverlay(this, i4, i5, i6, i7, (int) gameEngine.viewpointXSnapped, (int) gameEngine.viewpointYSnapped, gameEngine.renderGraphicsEngine, true, i);
            }
        }
    }

    /* JADX INFO: renamed from: r */
    public boolean isUnitOverlappingRadius(BaseUnit baseUnit) {
        float fDistanceSq = Utility.distanceSq(this.posX, this.posY, baseUnit.posX, baseUnit.posY);
        float f = 9.0f;
        if (!baseUnit.bI()) {
            f = this.radius + baseUnit.radius;
            if (f < 11.0f) {
                f = 11.0f;
            }
        }
        if (fDistanceSq < f * f) {
            return true;
        }
        return false;
    }

    /* JADX INFO: renamed from: a */
    public boolean hasBlockingUnitNearby(BaseUnit baseUnit, PlayerTeam playerTeam) {
        boolean z = false;
        if (!bI()) {
            z = true;
        }
        float f = this.radius + CustomUnitConfigParser.maxUnitRadius + 10.0f;
        float f2 = this.posX - f;
        float f3 = this.posX + f;
        float f4 = this.posY - f;
        float f5 = this.posY + f;
        BaseUnit[] baseUnitArrA = BaseUnit.bE.a();
        int size = BaseUnit.bE.size();
        for (int i = 0; i < size; i++) {
            BaseUnit baseUnit2 = baseUnitArrA[i];
            float f6 = baseUnit2.posX;
            float f7 = baseUnit2.posY;
            if (f2 <= f6 && f6 <= f3 && f4 <= f7 && f7 <= f5 && baseUnit2 != this && ((z || baseUnit2.bI()) && !baseUnit2.isDead && isUnitOverlappingRadius(baseUnit2) && baseUnit2 != baseUnit && (playerTeam == null || baseUnit2.d(playerTeam)))) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: renamed from: bs */
    public OrderableUnit findNearbySameTypeUnitForPlacement() {
        for (BaseUnit baseUnit : BaseUnit.bE) {
            if (baseUnit != this && (baseUnit instanceof OrderableUnit orderableUnit)) {
                if (!orderableUnit.isDead && orderableUnit.team == this.team && orderableUnit.r() == r() && isCollidingWith(orderableUnit)) {
                    return orderableUnit;
                }
            }
        }
        return null;
    }

    @Override
    // com.corrodinggames.rts.game.units.BaseUnit, com.corrodinggames.rts.gameFramework.SizedObject, com.corrodinggames.rts.gameFramework.GameObject
    /* JADX INFO: renamed from: a */
    public void remove() {
        if (this.pendingPortalTransfer != null) return;
        if (this.parentEntity != null) {
            bx();
        }
        clearAllWaypoints();
        clearPathingState();
        super.remove();
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    public void bt() {
        if (this.pendingPortalTransfer != null) return;
        setTransportParent((OrderableUnit) null);
        this.attackTarget = null;
        clearAllWaypoints();
        clearPathingState();
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    public void bu() {
        if (this.pendingPortalTransfer != null) return;
        if (this.parentEntity != null) {
            bx();
        }
        super.bu();
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    public void bv() {
        super.bv();
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    public int bw() {
        return (((((((((((0 * 31) + super.bw()) * 31) + ((int) (getMoveSpeed() * 100.0f))) * 31) + ((int) (getMaxTurnSpeed() * 100.0f))) * 31) + ((int) (m() * 100.0f))) * 31) + ((int) b(0))) * 31) + ((int) (getMoveAccelerationSpeed() * 100.0f));
    }

    @Override
        // com.corrodinggames.rts.game.units.BaseUnit
        /* JADX INFO: renamed from: m */
    PointF calculateMaxAttackRange(float f) {
        PointF movementDeltaOffset = getMovementDeltaOffset(f);
        dE.a(this.posX + movementDeltaOffset.x, this.posY + movementDeltaOffset.y);
        return dE;
    }

    /* JADX INFO: renamed from: n */
    public PointF getMovementDeltaOffset(float f) {
        float fFastCos = 0.0f;
        float fFastSin = 0.0f;
        if (I() && this.blockedRecoveryTime == 0.0f) {
            if (isSlidingMovement()) {
                fFastCos = this.velocityX * f;
                fFastSin = this.velocityY * f;
            } else if (this.rotation != 0.0f) {
                float f2 = this.rotationSpeed;
                if (isIgnoreMoveOrders()) {
                    f2 = this.targetRotation;
                }
                float moveSpeed = getMoveSpeed() * this.rotation * f;
                fFastCos = Utility.fastCos(f2) * moveSpeed;
                fFastSin = Utility.fastSin(f2) * moveSpeed;
            }
        }
        bm.a(fFastCos, fFastSin);
        return bm;
    }

    public boolean a(CommandType commandType) {
        return false;
    }

    public void a(AbstractUnitAction abstractUnitAction, boolean z, float f, float f2) {
    }

    public boolean a(AbstractUnitAction abstractUnitAction, float f, float f2) {
        return true;
    }

    public void a(BaseUnit baseUnit, float f, int i) {
        this.turretTurnSpeed = Utility.moveTowardsZero(this.turretTurnSpeed, f);
        if (this.turretTurnSpeed == 0.0f) {
            this.turretTurnSpeed = 5.0f;
            if (isVisibleOnScreen()) {
                Vector3D vector3DBn = bn();
                Effect effectCreateEffectInternal = GameEngine.getInstance().effectManager.createEffectInternal(vector3DBn.x, vector3DBn.y, this.posZ + vector3DBn.z, EffectType.custom, false, EffectQuality.low);
                if (effectCreateEffectInternal != null) {
                    float angleBetweenPoints = Utility.getAngleBetweenPoints(vector3DBn.x, vector3DBn.y, (float) (((double) baseUnit.posX) + (-8.0d) + (Utility.random() * 16.0d)), (float) (((double) baseUnit.posY) + (-8.0d) + (Utility.random() * 16.0d)));
                    effectCreateEffectInternal.velocityX = Utility.fastCos(angleBetweenPoints) * Utility.randomFloatInRange(2.0f, 4.0f);
                    effectCreateEffectInternal.velocityY = Utility.fastSin(angleBetweenPoints) * Utility.randomFloatInRange(2.0f, 4.0f);
                    effectCreateEffectInternal.frameIndex = 6;
                    effectCreateEffectInternal.lifeTimer = 20.0f;
                    effectCreateEffectInternal.lifeMax = effectCreateEffectInternal.lifeTimer;
                    effectCreateEffectInternal.fadeIn = true;
                    effectCreateEffectInternal.alpha = 0.8f;
                    effectCreateEffectInternal.scaleFrom = 0.2f;
                    effectCreateEffectInternal.scaleTo = 1.0f;
                }
            }
        }
    }

    public void b(BaseUnit baseUnit, float f, int i) {
        this.turretTurnSpeed = Utility.moveTowardsZero(this.turretTurnSpeed, f);
        if (this.turretTurnSpeed == 0.0f) {
            this.turretTurnSpeed = 5.0f;
            if (isVisibleOnScreen()) {
                PointF pointFE = E(0);
                Effect effectCreateEffectInternal = GameEngine.getInstance().effectManager.createEffectInternal(baseUnit.posX, baseUnit.posY, baseUnit.posZ, EffectType.custom, false, EffectQuality.low);
                if (effectCreateEffectInternal != null) {
                    float angleBetweenPoints = Utility.getAngleBetweenPoints(baseUnit.posX, baseUnit.posY - baseUnit.posZ, (float) (((double) pointFE.x) + (-8.0d) + (Utility.random() * 16.0d)), (float) (((double) pointFE.y) + (-8.0d) + (Utility.random() * 16.0d)));
                    effectCreateEffectInternal.velocityX = Utility.fastCos(angleBetweenPoints) * Utility.randomFloatInRange(2.0f, 4.0f);
                    effectCreateEffectInternal.velocityY = Utility.fastSin(angleBetweenPoints) * Utility.randomFloatInRange(2.0f, 4.0f);
                    effectCreateEffectInternal.frameIndex = 5;
                    effectCreateEffectInternal.lifeTimer = 20.0f;
                    effectCreateEffectInternal.lifeMax = effectCreateEffectInternal.lifeTimer;
                    effectCreateEffectInternal.fadeIn = true;
                    effectCreateEffectInternal.alpha = 0.8f;
                    effectCreateEffectInternal.scaleFrom = 0.2f;
                    effectCreateEffectInternal.scaleTo = 1.0f;
                }
            }
        }
    }

    public TriggerDebugAction a(UnitCommand unitCommand, UnitType unitType, int i, float f, float f2) {
        GameEngine gameEngine = GameEngine.getInstance();
        AbstractUnitAction abstractUnitActionFindActionForUnitTypeWithQueueSize = findActionForUnitTypeWithQueueSize(unitType, i, false);
        if (abstractUnitActionFindActionForUnitTypeWithQueueSize == null) {
            GameEngine.logColored("Unit '" + r().getUnitTypeDescriptionShort() + "' can not build:" + unitType.getUnitTypeDescriptionShort());
            return bn.a();
        }
        if (!unitCommand.isForceMove) {
            if (abstractUnitActionFindActionForUnitTypeWithQueueSize.isNotAvailable(this)) {
                GameEngine.logColored("Builder '" + r().getUnitTypeDescriptionShort() + "' tried to build a locked building:" + abstractUnitActionFindActionForUnitTypeWithQueueSize.getActionIdString());
                return bn.a();
            }
            if (!abstractUnitActionFindActionForUnitTypeWithQueueSize.b(this) && !abstractUnitActionFindActionForUnitTypeWithQueueSize.isActivated(this)) {
                GameEngine.logColored("Builder '" + r().getUnitTypeDescriptionShort() + "' tried to build a unavailable building:" + abstractUnitActionFindActionForUnitTypeWithQueueSize.getActionIdString() + " (add isLocked:false to fix)");
                return bn.a();
            }
        }
        if (!unitType.k() && !abstractUnitActionFindActionForUnitTypeWithQueueSize.isRightClickAction() && this.team.getNonBuildingUnitCountIncludingQueued() >= this.team.getUnitCap()) {
            if (this.team == gameEngine.playerTeam) {
                gameEngine.gameUI.showMediumPriorityMessage(gameEngine.gameUI.interfaceRenderer.unitCapReachedText);
            }
            return bn.a();
        }
        if (BaseUnit.findTurretPosition(unitType) == null) {
            String unitTypeDescriptionShort = "{build is null}";
            if (unitCommand.buildUnitType != null) {
                unitTypeDescriptionShort = unitCommand.buildUnitType.getUnitTypeDescriptionShort();
            }
            GameEngine.log("Build unit type missing: " + unitTypeDescriptionShort);
            return bn.a();
        }
        BaseUnit baseUnitG = BaseBuilding.g(unitType);
        if (!UnitPrice.b(unitType.u(), abstractUnitActionFindActionForUnitTypeWithQueueSize.getPrice()) || !UnitPrice.b(unitType.B(), abstractUnitActionFindActionForUnitTypeWithQueueSize.getAdditionalCost())) {
            baseUnitG.price = abstractUnitActionFindActionForUnitTypeWithQueueSize.getPrice();
            baseUnitG.additionalCost = abstractUnitActionFindActionForUnitTypeWithQueueSize.getAdditionalCost();
        }
        if (abstractUnitActionFindActionForUnitTypeWithQueueSize instanceof FilteredUnitAction) {
            baseUnitG.price = null;
            baseUnitG.additionalCost = null;
        }
        baseUnitG.buildProgress = 0.0f;
        baseUnitG.paidBuildProgress = 0.0f;
        gameEngine.tileMap.updateCursorTileIndexFromWorldPoint((f - baseUnitG.getTileOffsetX()) + 1.0f, (f2 - baseUnitG.getTileOffsetY()) + 1.0f);
        baseUnitG.posX = gameEngine.tileMap.cursorTileX + baseUnitG.getTileOffsetX();
        baseUnitG.posY = gameEngine.tileMap.cursorTileY + baseUnitG.getTileOffsetY();
        baseUnitG.f(this.team);
        baseUnitG.setCommandTargetUnit(this);
        if (i != 1 && (baseUnitG instanceof OrderableUnit)) {
            ((OrderableUnit) baseUnitG).a(i);
        }
        baseUnitG.onUnitSpawned();
        if (baseUnitG instanceof OrderableUnit) {
            OrderableUnit orderableUnit = (OrderableUnit) baseUnitG;
            boolean z = false;
            OrderableUnit orderableUnit2 = null;
            if (isTurretUnit()) {
                orderableUnit2 = this;
            } else if (!this.isAlive && !bI()) {
                orderableUnit2 = this;
            }
            if (orderableUnit.hasBlockingUnitNearby(orderableUnit2, (PlayerTeam) null)) {
                z = true;
            }
            if (!z && !orderableUnit.canPlaceAtCurrentPositionWithIgnoreUnits(true, (PlayerTeam) null)) {
                z = true;
            }
            if (z) {
                baseUnitG.remove();
                TriggerDebugAction triggerDebugActionA = bn.a();
                OrderableUnit orderableUnitFindNearbySameTypeUnitForPlacement = ((OrderableUnit) baseUnitG).findNearbySameTypeUnitForPlacement();
                triggerDebugActionA.targetUnit = orderableUnitFindNearbySameTypeUnitForPlacement;
                triggerDebugActionA.action = abstractUnitActionFindActionForUnitTypeWithQueueSize;
                if (orderableUnitFindNearbySameTypeUnitForPlacement == null) {
                }
                return triggerDebugActionA;
            }
        }
        UnitPrice displayText = abstractUnitActionFindActionForUnitTypeWithQueueSize.getPrice();
        if (unitCommand.isForceMove) {
            displayText = UnitPrice.a;
        }
        if (!displayText.c(this)) {
            baseUnitG.remove();
            TriggerDebugAction triggerDebugActionA2 = bn.a();
            this.lastAttackTick = gameEngine.gameTimeMillis;
            if (this.wayPointTimer < 1000.0f) {
                triggerDebugActionA2.isActive = true;
                BuildPreview buildPreviewFindClosestPreview = BuildPreview.findClosestPreview(this.team, baseUnitG.posX, baseUnitG.posY);
                if (buildPreviewFindClosestPreview != null) {
                    buildPreviewFindClosestPreview.showFoundation = true;
                }
            }
            return triggerDebugActionA2;
        }
        m(baseUnitG);
        if (baseUnitG instanceof OrderableUnit) {
            OrderableUnit orderableUnit3 = (OrderableUnit) baseUnitG;
            orderableUnit3.br();
            if (baseUnitG.bI()) {
                gameEngine.pathfindingEngine.a(orderableUnit3);
            }
        }
        PlayerTeam.c(baseUnitG);
        TriggerDebugAction triggerDebugActionA3 = bn.a();
        triggerDebugActionA3.sourceUnit = baseUnitG;
        triggerDebugActionA3.action = abstractUnitActionFindActionForUnitTypeWithQueueSize;
        return triggerDebugActionA3;
    }

    public boolean a(OrderableUnit orderableUnit, AttachmentSlotDefinition attachmentSlotDefinition) {
        return false;
    }

    public boolean b(OrderableUnit orderableUnit) {
        return false;
    }

    public void bx() {
        if (this.parentEntity == null) {
            return;
        }
        if (this.parentEntity.isDead) {
        }
        if (!this.parentEntity.b(this)) {
            GameEngine.logColored("Deattach failed, forcing deattach. Child:" + getUnitDebugName() + " Parent:" + this.parentEntity.getUnitDebugName());
            this.parentEntity = null;
            this.attachmentData = null;
        }
    }

    public AttachmentSlotDefinition a(short s) {
        return null;
    }

    public static BaseUnit a(OrderableUnit orderableUnit, float f, float f2, float f3, AnimationSet animationSet) {
        if (f3 <= 0.0f) {
            return null;
        }
        bo.checkLineOfSight = true;
        bo.includeNonGroundUnits = false;
        bo.nearestUnit = null;
        bo.closestDistanceSq = f3 * f3;
        bo.animationSetFilter = animationSet;
        bo.searchPosX = f;
        bo.searchPosY = f2;
        GameEngine.getInstance().unitSpatialIndex.a(f, f2, f3, orderableUnit, 0.0f, bo);
        return bo.nearestUnit;
    }

    public UnitPrice by() {
        return UnitPrice.a;
    }

    /* JADX INFO: renamed from: bz */
    public FastArrayList getTransportedUnitList() {
        return bq;
    }

    /* JADX INFO: renamed from: bA */
    public boolean isTransportUnloadingActive() {
        return false;
    }

    /* JADX INFO: renamed from: bB */
    public int getTransportedUnitCount() {
        return 0;
    }

    @Override // com.corrodinggames.rts.game.units.BaseUnit
    /* JADX INFO: renamed from: bC */
    public void updateUnitMovement() {
        UnitPrice unitPriceA;
        UnitPrice repairOrReclaimPriceDelta = getRepairOrReclaimPriceDelta();
        UnitPrice queuedActionPriceDelta = getQueuedActionPriceDelta();
        if (repairOrReclaimPriceDelta == null) {
            unitPriceA = queuedActionPriceDelta;
        } else if (queuedActionPriceDelta == null) {
            unitPriceA = repairOrReclaimPriceDelta;
        } else {
            unitPriceA = UnitPrice.a(repairOrReclaimPriceDelta, queuedActionPriceDelta);
        }
        if (this.unitCustomData == null && unitPriceA == null) {
            return;
        }
        if (this.unitCustomData != null && unitPriceA != null && this.unitCustomData.b(unitPriceA)) {
            return;
        }
        PlayerTeam.b((BaseUnit) this);
        this.unitCustomData = unitPriceA;
        PlayerTeam.c(this);
    }

    /* JADX INFO: renamed from: bD */
    public UnitPrice getQueuedActionPriceDelta() {
        return null;
    }

    /* JADX INFO: renamed from: bE */
    public UnitPrice getRepairOrReclaimPriceDelta() {
        UnitCommand currentWaypoint;
        BaseUnit currentRepairOrReclaimTarget = getCurrentRepairOrReclaimTarget();
        if (currentRepairOrReclaimTarget != null && (currentWaypoint = getCurrentWaypoint()) != null) {
            if (currentWaypoint.commandType == UnitCommandType.repair && currentRepairOrReclaimTarget.buildProgress < 1.0f) {
                UnitPrice repairOrReclaimPrice = getRepairOrReclaimPrice(currentRepairOrReclaimTarget);
                float repairProgressRate = getRepairProgressRate(currentRepairOrReclaimTarget) * 60.0f;
                if (repairOrReclaimPrice != null) {
                    return UnitPrice.a(repairOrReclaimPrice, -repairProgressRate);
                }
            }
            if (currentWaypoint.commandType != UnitCommandType.reclaim) {
                return null;
            }
            if (currentRepairOrReclaimTarget.buildProgress < 1.0f) {
                UnitPrice repairOrReclaimPrice2 = getRepairOrReclaimPrice(currentRepairOrReclaimTarget);
                float reclaimRate = getReclaimRate(currentRepairOrReclaimTarget) * 60.0f;
                if (repairOrReclaimPrice2 != null) {
                    return UnitPrice.a(repairOrReclaimPrice2, reclaimRate);
                }
                return null;
            }
            if (y(currentRepairOrReclaimTarget)) {
                float fZ = calculateUnitSpeed(currentRepairOrReclaimTarget);
                UnitPrice unitDescription = currentRepairOrReclaimTarget.getBuildPrice();
                UnitPrice unitDisplayName = currentRepairOrReclaimTarget.getReclaimPrice();
                if (unitDisplayName != null) {
                    unitDescription = unitDisplayName;
                }
                return UnitPrice.a(unitDescription, (fZ * 60.0f) / currentRepairOrReclaimTarget.maxHealth);
            }
            return null;
        }
        return null;
    }
}
