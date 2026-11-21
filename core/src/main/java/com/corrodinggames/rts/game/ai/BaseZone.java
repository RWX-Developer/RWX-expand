package com.corrodinggames.rts.game.ai;

import com.corrodinggames.rts.game.map.MapTile;
import com.corrodinggames.rts.game.units.*;
import com.corrodinggames.rts.game.units.actions.AbstractUnitAction;
import com.corrodinggames.rts.game.units.buildings.BaseBuilding;
import com.corrodinggames.rts.game.units.buildings.CommandCenter;
import com.corrodinggames.rts.game.units.buildings.FactoryQueueInterface;
import com.corrodinggames.rts.game.units.custom.AnimationSet;
import com.corrodinggames.rts.game.units.custom.AnimationTag;
import com.corrodinggames.rts.game.units.custom.CustomUnitConfig;
import com.corrodinggames.rts.game.units.custom.price.UnitPrice;
import com.corrodinggames.rts.game.units.management.UnitListIterator;
import com.corrodinggames.rts.gameFramework.Command;
import com.corrodinggames.rts.gameFramework.GameEngine;
import com.corrodinggames.rts.gameFramework.Utility;
import com.corrodinggames.rts.gameFramework.network.GameInputStream;
import com.corrodinggames.rts.gameFramework.network.GameOutputStream;
import com.corrodinggames.rts.gameFramework.utility.FastArrayList;
import com.corrodinggames.rts.gameFramework.utility.GameViewUtils;
import com.corrodinggames.rts.gameFramework.utility.SlickToAndroidKeycodes;
import com.corrodinggames.rts.gameFramework.utility.UnitList;
import io.github.rwx.geometry.PointF;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

/* JADX INFO: renamed from: com.corrodinggames.rts.game.a.i */
/* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/game/a/i.class */
public class BaseZone extends AIStrategyNode {

    /* JADX INFO: renamed from: a */
    float resourceScore;

    /* JADX INFO: renamed from: b */
    BaseZoneStage stage;

    /* JADX INFO: renamed from: c */
    BaseZoneType zoneType;

    /* JADX INFO: renamed from: d */
    float reclaimScore;

    /* JADX INFO: renamed from: e */
    float defensiveScore;

    /* JADX INFO: renamed from: f */
    float lastFoundBuildingPriority;

    /* JADX INFO: renamed from: g */
    float updateTimer;

    /* JADX INFO: renamed from: h */
    int lastAttackedTimer;

    /* JADX INFO: renamed from: i */
    float landUnitsScore;

    /* JADX INFO: renamed from: j */
    float airUnitsScore;

    /* JADX INFO: renamed from: k */
    float waterUnitsScore;

    /* JADX INFO: renamed from: l */
    float extractorScore;

    /* JADX INFO: renamed from: m */
    float lastTimeAttackedByEnemy;

    /* JADX INFO: renamed from: n */
    boolean hasResources;

    /* JADX INFO: renamed from: o */
    boolean hasLandAccess;

    /* JADX INFO: renamed from: p */
    FastArrayList unitTypesInZone;

    /* JADX INFO: renamed from: q */
    UnitList unitsInZone;

    /* JADX INFO: renamed from: r */
    UnitList buildingsInZone;

    /* JADX INFO: renamed from: s */
    boolean isUnderAttack;

    /* JADX INFO: renamed from: t */
    boolean isContested;

    /* JADX INFO: renamed from: u */
    float lastTimeScouted;

    /* JADX INFO: renamed from: v */
    float lastTimeReclaimed;

    /* JADX INFO: renamed from: w */
    PointF tempPoint1;

    /* JADX INFO: renamed from: x */
    PointF tempPoint2;

    /* JADX INFO: renamed from: y */
    int lastTimeBuilt;

    /* JADX INFO: renamed from: z */
    UnitType lastBuiltUnitType;

    /* JADX INFO: renamed from: A */
    UnitPrice lastBuiltCustomUnit;

    /* JADX INFO: renamed from: B */
    UnitPrice lastBuiltCustomUnit2;

    /* JADX INFO: renamed from: C */
    int numberOfLandUnits;

    /* JADX INFO: renamed from: D */
    int numberOfAirUnits;

    /* JADX INFO: renamed from: E */
    String debugText;

    /* JADX INFO: renamed from: F */
    int numberOfWaterUnits;

    /* JADX INFO: renamed from: G */
    int numberOfBuildings;

    /* JADX INFO: renamed from: H */
    boolean isInitialized;

    /* JADX INFO: renamed from: I */
    int numberOfExtractors;

    /* JADX INFO: renamed from: J */
    int numberOfFactories;

    /* JADX INFO: renamed from: K */
    int numberOfCombatUnits;

    /* JADX INFO: renamed from: L */
    int numberOfIdleCombatUnits;

    /* JADX INFO: renamed from: M */
    boolean isPrimary;

    /* JADX INFO: renamed from: N */
    ArrayList rallyPoints;

    /* JADX INFO: renamed from: O */
    UnitType lastBuiltFactoryType;

    /* JADX INFO: renamed from: P */
    UnitPrice lastBuiltFactoryCustomUnit;

    @Override // com.corrodinggames.rts.game.ai.AIStrategyNode, com.corrodinggames.rts.gameFramework.Serializable
    public void a(GameOutputStream gameOutputStream) throws IOException {
        gameOutputStream.writeEnumOrdinal(this.stage);
        gameOutputStream.writeEnumOrdinal(this.zoneType);
        gameOutputStream.writeFloat(this.reclaimScore);
        gameOutputStream.writeFloat(this.defensiveScore);
        gameOutputStream.writeFloat(this.lastFoundBuildingPriority);
        gameOutputStream.writeFloat(this.updateTimer);
        gameOutputStream.writeFloat(this.landUnitsScore);
        gameOutputStream.writeFloat(this.airUnitsScore);
        gameOutputStream.writeFloat(this.waterUnitsScore);
        gameOutputStream.writeFloat(this.extractorScore);
        gameOutputStream.writeByte(4);
        gameOutputStream.writeFloat(this.lastTimeReclaimed);
        gameOutputStream.writeFloat(this.lastTimeAttackedByEnemy);
        gameOutputStream.writeBoolean(this.hasResources);
        gameOutputStream.writeBoolean(this.hasLandAccess);
        gameOutputStream.writeInt(this.lastAttackedTimer);
        super.a(gameOutputStream);
    }

    @Override // com.corrodinggames.rts.game.ai.AIStrategyNode
    /* JADX INFO: renamed from: a */
    public void readFromInputStream(GameInputStream gameInputStream) throws IOException {
        this.stage = (BaseZoneStage) gameInputStream.readEnumOrdinalOrNull(BaseZoneStage.class);
        this.zoneType = (BaseZoneType) gameInputStream.readEnumOrdinalOrNull(BaseZoneType.class);
        this.reclaimScore = gameInputStream.readFloat();
        this.defensiveScore = gameInputStream.readFloat();
        this.lastFoundBuildingPriority = gameInputStream.readFloat();
        this.updateTimer = gameInputStream.readFloat();
        this.landUnitsScore = gameInputStream.readFloat();
        this.airUnitsScore = gameInputStream.readFloat();
        this.waterUnitsScore = gameInputStream.readFloat();
        this.extractorScore = gameInputStream.readFloat();
        byte b = gameInputStream.readByte();
        if (b >= 1) {
            this.lastTimeReclaimed = gameInputStream.readFloat();
        }
        if (b >= 2) {
            this.lastTimeAttackedByEnemy = gameInputStream.readFloat();
        }
        if (b >= 3) {
            this.hasResources = gameInputStream.readBoolean();
            this.hasLandAccess = gameInputStream.readBoolean();
        }
        if (b >= 4) {
            this.lastAttackedTimer = gameInputStream.readInt();
        }
        super.readFromInputStream(gameInputStream);
    }

    public BaseZone(AIController aIController, float f, float f2) {
        super(aIController, f, f2);
        this.reclaimScore = -1.0f;
        this.updateTimer = 100.0f;
        this.landUnitsScore = 50.0f;
        this.airUnitsScore = 50.0f;
        this.unitTypesInZone = new FastArrayList();
        this.unitsInZone = new UnitList();
        this.buildingsInZone = new UnitList();
        this.lastTimeReclaimed = 0.0f;
        this.tempPoint1 = new PointF();
        this.tempPoint2 = new PointF();
        this.isInitialized = false;
        this.rallyPoints = new ArrayList();
    }

    /* JADX INFO: renamed from: a */
    // RWX：getBestBuildingToBuild 的建筑类型循环里每型最多调 3 次本方法，每次扫
    // (2×半径格+25)² 个格子（约 1.1 万格 × getPathingOverride/BaseBuilding 查询）——
    // 实测 GBB 一次 50~60ms，是 AI_ZONES 尖峰的主源。结果只跟 zone 位置与地图资源态
    // 有关，与本 tick 内调了几次无关，改成按 tick 记忆：每 zone 每 tick 至多扫一遍。
    // 注意 tempPoint1 是本对象复用的 PointF，同 tick 内所有命中返回同一引用（原版语义相同）。
    private int rwxResTick = -1;
    private PointF rwxResCache;

    public PointF findClosestResource() {
        MapTile pathingOverrideTileAt;
        GameEngine gameEngine = GameEngine.getInstance();
        long tFcr = io.github.rwx.probe.RwxPerf.now();
        if (this.rwxResTick == gameEngine.currentTick) {
            io.github.rwx.probe.RwxPerf.mark(io.github.rwx.probe.RwxPerf.AI_FCR, tFcr);
            return this.rwxResCache;
        }
        PointF pointF = null;
        int i = (int) (this.radius * gameEngine.tileMap.tileScaleX);
        // RWX：资源池不总落在 zone 半径内（MC 的矿场必须建在资源池上）。
        // 向外多扫一圈，避免因为「半径内恰好没有池子」就把整类建筑判死。
        int iExtra = i + 12;
        gameEngine.tileMap.setCursorTileIndexFromWorldPoint(this.posX, this.posY);
        int i2 = gameEngine.tileMap.cursorTileX;
        int i3 = gameEngine.tileMap.cursorTileY;
        for (int i4 = i2 - iExtra; i4 <= i2 + iExtra; i4++) {
            for (int i5 = i3 - iExtra; i5 <= i3 + iExtra; i5++) {
                if (gameEngine.tileMap.isInBounds(i4, i5) && (pathingOverrideTileAt = gameEngine.tileMap.getPathingOverrideTileAt(i4, i5)) != null && pathingOverrideTileAt.isResourcePool) {
                    BaseUnit baseUnitB = BaseBuilding.b(i4, i5);
                    boolean z = false;
                    if (baseUnitB == null) {
                        z = true;
                    }
                    if (baseUnitB != null && (baseUnitB instanceof OrderableUnit) && !((OrderableUnit) baseUnitB).r().p()) {
                        z = true;
                    }
                    if (z) {
                        gameEngine.tileMap.setCursorTileIndexFromTileIndex(i4, i5);
                        if (pointF == null || Utility.getRandomIntInRange(0, 100) < 50) {
                            this.tempPoint1.a(gameEngine.tileMap.cursorTileX + gameEngine.tileMap.halfTileWorldSizeX, gameEngine.tileMap.cursorTileY + gameEngine.tileMap.halfTileWorldSizeY);
                            pointF = this.tempPoint1;
                        }
                    }
                }
            }
        }
        this.rwxResTick = gameEngine.currentTick;
        this.rwxResCache = pointF;
        io.github.rwx.probe.RwxPerf.mark(io.github.rwx.probe.RwxPerf.AI_FCR, tFcr);
        return pointF;
    }

    /* JADX INFO: renamed from: a */
    public void removeUnit(OrderableUnit orderableUnit) {
        this.unitsInZone.remove(orderableUnit);
        rwxBucketRemove(orderableUnit);
    }

    /* JADX INFO: renamed from: b */
    public void updateUnitsInZone() {
        this.unitTypesInZone.clear();
        this.unitsInZone.clear();
        rwxResetBuckets();
        // RWX：updateUnitsInZone：谓词要求 team == aiController，走本队快照
        BaseUnit[] baseUnitArrA = this.aiController.rwxTeamUnits();
        int size = Math.min(this.aiController.rwxSnapSize, baseUnitArrA.length);
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_SNAP, size);
        for (int i = 0; i < size; i++) {
            BaseUnit baseUnit = baseUnitArrA[i];
            if (baseUnit.team == this.aiController && !baseUnit.isDead && !baseUnit.u() && countUnitsForStrategy(baseUnit)) {
                this.unitsInZone.add(baseUnit);
                rwxBucketAdd(baseUnit); // RWX：不变式——unitsInZone 每次 add 都要同步填桶
                UnitType unitType = baseUnit.unitType;
                if (!this.unitTypesInZone.contains(unitType)) {
                    this.unitTypesInZone.add(unitType);
                }
            }
        }
    }

    /* JADX INFO: renamed from: a */
    public boolean hasBuilderFor(UnitType unitType) {
        if (findFactoryForUnitType(unitType, false, true) != null) {
            return true;
        }
        return false;
    }

    /* JADX INFO: renamed from: b */
    // RWX：canBuild = 「本 zone 里任一工厂能否造 unitType」。它只依赖 zone 自身的 unitsInZone，
    // 而 getBestBuildingToBuild 每调用一次要对 ~120 个建筑类型各跑一遍（每型 units×types 双层扫），
    // 实测占 GBB 的 97%（CanB 52ms / 尖峰 56ms）。unitsInZone 每 tick 只在 rwxZoneScan 刷新一次，
    // 于是按 (建筑类型 → 结果) 记忆一个 tick：同 tick 内每型只算一次。
    // RWX：canBuild =「本 zone 任一工厂可造该型」，原实现是 类型×unitsInZone×动作 三层嵌套，
    // GBB 每调一次把它对 ~120 个建筑类型各跑一遍——实测 CanB 占尖峰 92%（52/56ms）。
    // 改成每 tick 用「工厂的动作表」直接物化可造类型集合：unitsInZone 每 tick 才刷新，
    // 集合与逐点查询逻辑等价（谓词仍是 canUseActionForUnitType(型,true)），代价 O(工厂×动作)。
    // RWX：区域化名单（-Drwx.zoneBuckets，默认开；设 0 全部回退原路径）。
    // 实测（bench-seed 4000 单位, tick=1800, RWX_PROF aiWin）每 tick 11727 次
    // zone 名单访问里 78%（fact9167）来自 findFactoryForUnitType——它在整份
    // unitsInZone（含全部战斗单位）里找那几座工厂；20%（idle2328）来自
    // countIdleCombatUnits 的 12 连发。
    // 做法：unitsInZone 每次重建时顺手把成员按兵种切成三条「保序子序列」桶，
    // 每条只是原名单的子集、顺序与原名单一致；消费循环仍逐字执行原来的整条
    // 谓词链（含 isActive/canAfford 这些同 tick 内会变的），所以选中的对象、
    // lastTimeBuilt 的自增次数、RNG 消耗次数与改前完全一致。桶在单位存活期间
    // 不会过期（isCombatCustomUnit / instanceof 都是单位类型上的静态性质）。
    public static final boolean rwxZoneBuckets = !"0".equals(System.getProperty("rwx.zoneBuckets", "1"));
    private final UnitList rwxFactoryUnits = new UnitList();
    private final UnitList rwxIdleCandidateUnits = new UnitList();
    private final UnitList rwxHarvesterUnits = new UnitList();

    /** 与 unitsInZone 同步重建；谓词必须是各消费循环外层谓词链的超集 */
    private void rwxResetBuckets() {
        this.rwxFactoryUnits.clear();
        this.rwxIdleCandidateUnits.clear();
        this.rwxHarvesterUnits.clear();
    }

    private void rwxBucketAdd(BaseUnit baseUnit) {
        if (!rwxZoneBuckets) {
            return;
        }
        // 工厂桶：与 findFactoryForUnitType 链里那条 instanceof 判定同一条件，纯类型
        if (baseUnit instanceof FactoryQueueInterface && baseUnit instanceof OrderableUnit) {
            this.rwxFactoryUnits.add(baseUnit);
        }
        // 战斗单位桶：isCombatCustomUnit 的链要求 !canUnitAttack()（该方法有 4 处重写：
        // OrderableUnit=r().m()、CustomUnit=unitConfig.useAsBuilder、FireUnit/Tree=false，
        // 全是兵种/配置级常量，tick 内不变）。这里直接调同一个虚方法做分桶，
        // 桶与链的过滤集合逐元素同源，不依赖跨类等价推导。
        if (baseUnit instanceof OrderableUnit && !((OrderableUnit) baseUnit).canUnitAttack()) {
            this.rwxIdleCandidateUnits.add(baseUnit);
        }
        // 采集桶：r().n()（isHarvester）与两个消费循环里的判定同一条件
        if (baseUnit.r().n()) {
            this.rwxHarvesterUnits.add(baseUnit);
        }
    }

    private void rwxBucketRemove(BaseUnit baseUnit) {
        if (!rwxZoneBuckets) {
            return;
        }
        this.rwxFactoryUnits.remove(baseUnit);
        this.rwxIdleCandidateUnits.remove(baseUnit);
        this.rwxHarvesterUnits.remove(baseUnit);
    }

    // RWX：等价性自检（-Drwx.zoneVerify=1，默认关）。
    // 跨进程同种子跑两遍来「对答案」在这台机器上行不通：地图与兵营生成还有
    // 没被 -Drwx.seed 覆盖的随机源，两遍本来就不一致。所以改成在同一次运行里
    // 检查这条不变式：
    //    桶 == unitsInZone 按「消费链里那条静态谓词」现场过滤出的保序子序列
    // 消费循环仍逐字执行原来的整条谓词链，链里含该静态谓词，
    // 因此不变式成立 ⇒ 通过链的元素集合与顺序都不变 ⇒ 行为逐位一致
    // （包括 RNG 消耗次数：RNG 只对通过链前段的元素消耗，前段集合未变）。
    public static final boolean rwxZoneVerify = "1".equals(System.getProperty("rwx.zoneVerify", "0"));
    public static long RWX_ZB_CHECKS = 0;
    public static long RWX_ZB_MISMATCH = 0;

    private void rwxVerifyBucket(UnitList bucket, int kind, String name) {
        if (!rwxZoneVerify) {
            return;
        }
        RWX_ZB_CHECKS++;
        if ((RWX_ZB_CHECKS & 4095L) == 1L) {
            io.github.rwx.probe.RwxPerf.out("RWX_ZB checks=" + RWX_ZB_CHECKS + " mismatch=" + RWX_ZB_MISMATCH
                    + " tick=" + GameEngine.getInstance().currentTick);
        }
        BaseUnit[] src = this.unitsInZone.a();
        int n = this.unitsInZone.size();
        BaseUnit[] got = bucket.a();
        int m = bucket.size();
        int k = 0;
        for (int i = 0; i < n; i++) {
            BaseUnit u = src[i];
            boolean want;
            if (kind == 0) {
                want = (u instanceof FactoryQueueInterface) && (u instanceof OrderableUnit);
            } else if (kind == 1) {
                want = (u instanceof OrderableUnit) && !((OrderableUnit) u).canUnitAttack();
            } else {
                want = u.r().n();
            }
            if (!want) {
                continue;
            }
            if (k >= m || got[k] != u) {
                RWX_ZB_MISMATCH++;
                if (RWX_ZB_MISMATCH <= 3) {
                    io.github.rwx.probe.RwxPerf.out("RWX_ZB_MISMATCH bucket=" + name
                            + " idx=" + k + " m=" + m + " n=" + n + " tick=" + GameEngine.getInstance().currentTick);
                }
                return;
            }
            k++;
        }
        if (k != m) {
            RWX_ZB_MISMATCH++;
            if (RWX_ZB_MISMATCH <= 3) {
                io.github.rwx.probe.RwxPerf.out("RWX_ZB_MISMATCH bucket=" + name + " length " + k + "!=" + m
                        + " tick=" + GameEngine.getInstance().currentTick);
            }
        }
    }

    private UnitList rwxFactories() {
        return rwxZoneBuckets ? this.rwxFactoryUnits : this.unitsInZone;
    }

    private UnitList rwxIdleCandidates() {
        return rwxZoneBuckets ? this.rwxIdleCandidateUnits : this.unitsInZone;
    }

    private UnitList rwxHarvesters() {
        return rwxZoneBuckets ? this.rwxHarvesterUnits : this.unitsInZone;
    }

    /** 消费点入口：验证桶与原名单现场过滤结果一致（关闭时只一次布尔判断） */
    private UnitList rwxChecked(UnitList bucket, int kind, String name) {
        if (rwxZoneVerify && bucket != this.unitsInZone) {
            rwxVerifyBucket(bucket, kind, name);
        }
        return bucket;
    }

    private int rwxCanBTick = -1;
    private final HashSet rwxCanBSet = new HashSet();

    public boolean canBuild(UnitType unitType) {
        GameEngine gameEngine = GameEngine.getInstance();
        if (gameEngine.isDemo && !unitType.isAvailableInDemo()) {
            return false;
        }
        if (unitType.w()) {
            return false;
        }
        this.rwxRefreshBuildableSet(gameEngine.currentTick);
        return this.rwxCanBSet.contains(unitType);
    }

    private void rwxRefreshBuildableSet(int tick) {
        if (this.rwxCanBTick == tick) {
            return;
        }
        this.rwxCanBTick = tick;
        this.rwxCanBSet.clear();
        // RWX：区域化名单——只关心工厂，走工厂桶
        UnitList rwxListC = rwxChecked(rwxFactories(), 0, "factory");
        BaseUnit[] baseUnitArrA = rwxListC.a();
        int size = rwxListC.size();
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_LIST, size);
        for (int i = 0; i < size; i++) {
            BaseUnit baseUnit = baseUnitArrA[i];
            // 候选只从工厂类单位的生产动作里取（attached 型），谓词仍走 canUseActionForUnitType，
            // 与原三层循环（unitsInZone × unitTypesInZone × 动作匹配）判定逐字等价。
            if (!(baseUnit instanceof FactoryQueueInterface) || !(baseUnit instanceof OrderableUnit)) {
                continue;
            }
            OrderableUnit builder = (OrderableUnit) baseUnit;
            if (builder.r() == null) {
                continue;
            }
            ArrayList actions = builder.getAvailableActions();
            if (actions == null) {
                continue;
            }
            for (Object o : actions) {
                AbstractUnitAction action = (AbstractUnitAction) o;
                UnitType t = action.getAttachedUnitType();
                if (t != null && !this.rwxCanBSet.contains(t) && builder.canUseActionForUnitType(t, true)) {
                    this.rwxCanBSet.add(t);
                }
            }
        }
    }

    // RWX: RWX_AI_STATS=1 时统计 AI 建筑选择分布（默认零开销）；RWX_AI_LEGACY=1 回退旧 0.05 平权重行为
    static final boolean RWX_AI_STATS = System.getenv("RWX_AI_STATS") != null;
    static final boolean RWX_AI_LEGACY = System.getenv("RWX_AI_LEGACY") != null;
    private static final HashMap rwPickCounts = new HashMap();
    private static int rwPickTotal = 0;
    private static final HashMap rwUnitStats = new HashMap();
    private static int rwUnitAttempts = 0;

    private static void rwNoteUnit(String str) {
        if (!RWX_AI_STATS) {
            return;
        }
        Integer num = (Integer) rwUnitStats.get(str);
        rwUnitStats.put(str, num == null ? 1 : num.intValue() + 1);
        if ((++rwUnitAttempts) % 100 == 0) {
            GameEngine.log("RWX_AISTATS_UNITS n=" + rwUnitAttempts + " " + rwUnitStats);
        }
    }

    /* JADX INFO: renamed from: c */
    public UnitType getBestBuildingToBuild() {
        GameEngine gameEngine = GameEngine.getInstance();
        if (this.aiController.isAggressive) {
            return null;
        }
        int iCountEnemyUnitsInRange = countUnitsForBuildStrategy(this.aiController.airFactoryUnitBuildStrategy);
        UnitType unitType = null;
        float f = -1.0f;
        for (UnitType unitType2 : UnitTypeEnum.ae) {
            if (!unitType2.isBuildingUnit()) continue;
            long tCanB = io.github.rwx.probe.RwxPerf.now();
            boolean rwxCanB = canBuild(unitType2);
            io.github.rwx.probe.RwxPerf.mark(io.github.rwx.probe.RwxPerf.AI_CANB, tCanB);
            if (rwxCanB) {
                long tCnt = io.github.rwx.probe.RwxPerf.now();
                int iAssignTaskToUnitTypeOnCommand = this.aiController.assignTaskToUnitTypeOnCommand(unitType2, UnitFilterMode.include);
                int iCountUnitsOfType = countUnitsOfType(unitType2);
                boolean z = false;
                if (unitType2 instanceof CustomUnitConfig) {
                    z = true;
                    CustomUnitConfig customUnitConfig = (CustomUnitConfig) unitType2;
                    if (customUnitConfig.relatedUnits.size() != 0) {
                        for (UnitType unitType3 : customUnitConfig.relatedUnits) {
                            iAssignTaskToUnitTypeOnCommand += this.aiController.assignTaskToUnitTypeOnCommand(unitType3, UnitFilterMode.include);
                            iCountUnitsOfType += countUnitsOfType(unitType3);
                        }
                    }
                io.github.rwx.probe.RwxPerf.mark(io.github.rwx.probe.RwxPerf.AI_CNT, tCnt);
                }
                float f2 = -2.0f;
                if (unitType2.p() && !z) {
                    int i = iCountUnitsOfType;
                    if (findClosestResource() != null && Utility.getRandomIntInRange(0, 100) < 90) {
                        if (i == 0) {
                            if (this.aiController.credits < 5000.0d) {
                                f2 = 0.98f;
                            } else {
                                f2 = 0.58f;
                            }
                        }
                        if (i == 1) {
                            f2 = 0.55f;
                        }
                        if (i == 2) {
                            f2 = 0.4f;
                        }
                        if (i >= 3) {
                            f2 = 0.25f / i;
                        }
                        if (iAssignTaskToUnitTypeOnCommand >= 3) {
                            f2 = (float) (((double) f2) * 0.6d);
                        }
                    }
                }
                if (unitType2 == UnitTypeEnum.landFactory && (iAssignTaskToUnitTypeOnCommand < 5 || iCountEnemyUnitsInRange == 0)) {
                    if (iAssignTaskToUnitTypeOnCommand == 0) {
                        f2 = 0.8f;
                    } else if (iCountUnitsOfType < 2) {
                        f2 = 0.46f / (iAssignTaskToUnitTypeOnCommand + (iCountUnitsOfType * 2));
                    }
                }
                if (unitType2 == UnitTypeEnum.seaFactory && this.aiController.isPathfindingOverloaded() && (iAssignTaskToUnitTypeOnCommand < 5 || iCountEnemyUnitsInRange == 0)) {
                    if (iAssignTaskToUnitTypeOnCommand == 0) {
                        f2 = 0.3f;
                    } else if (iCountUnitsOfType < 1) {
                        f2 = 0.1f / (iAssignTaskToUnitTypeOnCommand + (iCountUnitsOfType * 2));
                    }
                }
                if (unitType2 == UnitTypeEnum.airFactory && (iAssignTaskToUnitTypeOnCommand < 5 || iCountEnemyUnitsInRange == 0)) {
                    if (iAssignTaskToUnitTypeOnCommand == 0) {
                        f2 = 0.48f;
                    } else if (iCountUnitsOfType < 2) {
                        f2 = 0.29f / (iAssignTaskToUnitTypeOnCommand + iCountUnitsOfType);
                    }
                }
                if (unitType2 == UnitTypeEnum.turret) {
                    if (iCountUnitsOfType == 0) {
                        f2 = 0.47f;
                    } else if (iCountUnitsOfType < 3) {
                        f2 = 0.35f / iCountUnitsOfType;
                    } else if (iCountUnitsOfType < 4) {
                        f2 = 0.025f / iCountUnitsOfType;
                    }
                }
                if (unitType2 == UnitTypeEnum.laserDefence && iCountUnitsOfType == 0) {
                    f2 = 0.018f;
                }
                if (unitType2 == UnitTypeEnum.repairbay && iCountUnitsOfType == 0) {
                    f2 = 0.02f;
                }
                if (unitType2 == UnitTypeEnum.antiAirTurret) {
                    if (iCountUnitsOfType == 0) {
                        f2 = 0.42f;
                    } else if (this.aiController.isInsaneDifficulty()) {
                        if (iCountUnitsOfType < 4) {
                            f2 = 0.3f / iCountUnitsOfType;
                        }
                    } else if (iCountUnitsOfType < 3) {
                        f2 = 0.3f / iCountUnitsOfType;
                    } else if (iCountUnitsOfType < 4) {
                        f2 = 0.02f / iCountUnitsOfType;
                    }
                }
                if (unitType2 == UnitTypeEnum.fabricator && this.aiController.credits > 2000.0d && iCountUnitsOfType < 5) {
                    if (iAssignTaskToUnitTypeOnCommand == 0) {
                        f2 = 0.11f;
                    } else {
                        f2 = 0.07f / ((0.2f * iAssignTaskToUnitTypeOnCommand) + iCountUnitsOfType);
                    }
                }
                if (unitType2 == UnitTypeEnum.AntiNukeLaucher && ((!gameEngine.isInGameOrLobby() || !gameEngine.networkEngine.roomSettings.noNukes) && this.aiController.credits > 2200.0d && iAssignTaskToUnitTypeOnCommand < 4)) {
                    if (iAssignTaskToUnitTypeOnCommand == 0) {
                        f2 = 0.06f;
                    } else if (iCountUnitsOfType < 1) {
                        f2 = 0.05f / (iAssignTaskToUnitTypeOnCommand + (iCountUnitsOfType * 2));
                    }
                }
                if (z) {
                    CustomUnitConfig customUnitConfig2 = (CustomUnitConfig) unitType2;
                    if (customUnitConfig2.disableUse) {
                        f2 = -2.0f;
                    } else if (!RWX_AI_LEGACY && customUnitConfig2.rwAiBuildDefaultsUntouched) {
                        // RWX: 没写 [ai] 键的模组建筑按原版同角色阶梯打分（原来只有 0.05 平权重，
                        // 与原版矿场 0.98/工厂 0.8/炮塔 0.47 差 16~20 倍，AI 永远先挑原版建筑）。
                        if (unitType2.p()) {
                            // 矿场：与 mineFactory 分支同构
                            if (findClosestResource() == null) {
                                f2 = -2.0f;
                            } else if (Utility.getRandomIntInRange(0, 100) < 90) {
                                int i3 = iCountUnitsOfType;
                                if (i3 == 0) {
                                    f2 = this.aiController.credits < 5000.0d ? 0.98f : 0.58f;
                                }
                                if (i3 == 1) {
                                    f2 = 0.55f;
                                }
                                if (i3 == 2) {
                                    f2 = 0.4f;
                                }
                                if (i3 >= 3) {
                                    f2 = 0.25f / i3;
                                }
                                if (iAssignTaskToUnitTypeOnCommand >= 3) {
                                    f2 = f2 * 0.6f;
                                }
                            }
                        } else if (customUnitConfig2.isFactory) {
                            // 工厂：与 landFactory 分支同构；experimental 工厂取中间档，避免开局砸空家底
                            if (customUnitConfig2.experimental) {
                                if (iAssignTaskToUnitTypeOnCommand == 0 && this.aiController.credits > 8000.0d) {
                                    f2 = 0.25f;
                                }
                            } else if (iAssignTaskToUnitTypeOnCommand < 5 || iCountEnemyUnitsInRange == 0) {
                                if (iAssignTaskToUnitTypeOnCommand == 0) {
                                    f2 = 0.8f;
                                } else if (iCountUnitsOfType < 2) {
                                    f2 = 0.46f / (iAssignTaskToUnitTypeOnCommand + (iCountUnitsOfType * 2));
                                }
                            }
                        } else if (customUnitConfig2.canAttack) {
                            // 攻击建筑：与 turret 分支同构
                            if (iCountUnitsOfType == 0) {
                                f2 = 0.47f;
                            } else if (iCountUnitsOfType < 3) {
                                f2 = 0.35f / iCountUnitsOfType;
                            } else if (iCountUnitsOfType < 4) {
                                f2 = 0.025f / iCountUnitsOfType;
                            }
                        } else if (customUnitConfig2.canRepairUnits || customUnitConfig2.canRepairBuildings) {
                            // 维修建筑：与 repairbay 分支同构
                            if (iCountUnitsOfType == 0) {
                                f2 = 0.02f;
                            }
                        } else {
                            f2 = customUnitConfig2.buildPriority;
                            if (iCountUnitsOfType == 0) {
                                f2 += customUnitConfig2.nonInBaseExtraPriority;
                            }
                            if (iAssignTaskToUnitTypeOnCommand == 0) {
                                f2 += customUnitConfig2.nonGlobalExtraPriority;
                            }
                        }
                    } else if ((iAssignTaskToUnitTypeOnCommand < customUnitConfig2.maxGlobal || customUnitConfig2.maxGlobal == -1) && (iCountUnitsOfType < customUnitConfig2.maxEachBase || customUnitConfig2.maxEachBase == -1)) {
                        // 作者写过 [ai] 键：保持原行为，不破坏已有平衡
                        f2 = customUnitConfig2.buildPriority;
                        if (iCountUnitsOfType < customUnitConfig2.recommendedInEachBaseNum) {
                            f2 = customUnitConfig2.recommendedInEachBasePriorityIfUnmet;
                        }
                        if (iCountUnitsOfType == 0) {
                            f2 += customUnitConfig2.nonInBaseExtraPriority;
                        }
                        if (iAssignTaskToUnitTypeOnCommand == 0) {
                            f2 += customUnitConfig2.nonGlobalExtraPriority;
                        }
                        if (unitType2.p() && findClosestResource() == null) {
                            // RWX：不再永久排除。留一个极小权重，让建造流程还有机会换个位置放下去，
                            // 而不是「附近没池子 = 这类建筑永远不建」（MC 矿场就是这样被锁死的）。
                            f2 = 0.002f;
                        }
                    }
                }
                if (this.aiController.isHardOrAboveDifficulty() && unitType2 == UnitTypeEnum.experimentalLandFactory && this.aiController.credits > 15000.0d) {
                    if (iAssignTaskToUnitTypeOnCommand == 0) {
                        f2 = 0.04f;
                    }
                    if (this.aiController.credits > 55000.0d && iAssignTaskToUnitTypeOnCommand == 1) {
                        f2 = 0.03f;
                    }
                }
                if (f2 >= 0.0f && (f2 > f || Utility.randomFloatInRange(0.0f, 1.0f) < 0.01d)) {
                    f = f2;
                    unitType = unitType2;
                }
            }
        }
        this.lastFoundBuildingPriority = f;
        if (RWX_AI_STATS) {
            String strKey;
            if (unitType == null) {
                strKey = "<none>";
            } else if (unitType instanceof CustomUnitConfig) {
                CustomUnitConfig customUnitConfig3 = (CustomUnitConfig) unitType;
                strKey = unitType.getUnitTypeDescriptionShort() + (customUnitConfig3.rwAiBuildDefaultsUntouched && !RWX_AI_LEGACY ? "|mod-ladder" : "|mod-tuned");
            } else {
                strKey = unitType.getUnitTypeDescriptionShort() + "|core";
            }
            Integer num = (Integer) rwPickCounts.get(strKey);
            rwPickCounts.put(strKey, num == null ? 1 : num.intValue() + 1);
            if ((++rwPickTotal) % 150 == 0) {
                GameEngine.log("RWX_AISTATS total=" + rwPickTotal + " " + rwPickCounts);
            }
        }
        return unitType;
    }

    /* JADX INFO: renamed from: a */
    public int countUnitsForBuildStrategy(UnitBuildStrategy unitBuildStrategy) {
        int iCountUnitsOfType = 0;
        Iterator it = unitBuildStrategy.unitPriorities.iterator();
        while (it.hasNext()) {
            iCountUnitsOfType += countUnitsOfType(((UnitBuildPriority) it.next()).unitType);
        }
        return iCountUnitsOfType;
    }

    /* JADX INFO: renamed from: c */
    public int countUnitsOfType(UnitType unitType) {
        int i = 0;
        UnitList unitList = this.unitsInZone;
        BaseUnit[] baseUnitArrA = unitList.a();
        int size = unitList.size();
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_LIST, size);
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_TYPE, size);
        for (int i2 = 0; i2 < size; i2++) {
            BaseUnit baseUnit = baseUnitArrA[i2];
            if (baseUnit.team == this.aiController && baseUnit.unitType == unitType && countUnitsForStrategy(baseUnit)) {
                i++;
            }
        }
        return i;
    }

    /* JADX INFO: renamed from: d */
    public int countIdleCombatUnits() {
        int i = 0;
        // RWX：区域化名单——isCombatCustomUnit 要求 !canUnitAttack() == !r().m()（静态），
        // 所以「非建造兵种」是它的必要条件；桶保序，谓词链原样再跑一遍。
        UnitList unitList = rwxChecked(rwxIdleCandidates(), 1, "idle");
        BaseUnit[] baseUnitArrA = unitList.a();
        int size = unitList.size();
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_LIST, size);
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_IDLE, size);
        for (int i2 = 0; i2 < size; i2++) {
            BaseUnit baseUnit = baseUnitArrA[i2];
            if (baseUnit.team == this.aiController && (baseUnit instanceof OrderableUnit)) {
                OrderableUnit orderableUnit = (OrderableUnit) baseUnit;
                if (belongsToZoneUnit(orderableUnit, false) && !orderableUnit.isActive && orderableUnit.aB == null && this.aiController.isCombatCustomUnit(orderableUnit) && this.aiController.isEligibleUnitForRandomSelection(orderableUnit)) {
                    i++;
                }
            }
        }
        return i;
    }

    /* JADX INFO: renamed from: e */
    public int getNumberOfCombatUnits() {
        return this.numberOfCombatUnits;
    }

    /* JADX INFO: renamed from: f */
    public boolean hasEnemyUnits() {
        return countEnemyUnits() != 0;
    }

    /* JADX INFO: renamed from: g */
    public BaseUnit getClosestEnemyUnit() {
        // RWX：原实现每调用扫一遍全场 bE（约 1100 单位 × 重谓词），是大后期 aiStrat 尖峰主源之一。
        // 改为空间索引局部查询：b(posX,posY,radius+120) 的通过集是原盒判定的超集（|dx|<=R+r_u ⊇ |dx|<R），
        // 谓词链逐字保留，再在命中者里取真·最近（原名 closest，但旧实现返回的是 bE 序第一个，顺手修正）。
        float f = this.radius + 120.0f;
        BaseUnit best = null;
        float bestDs = Float.MAX_VALUE;
        for (BaseUnit baseUnit : GameEngine.getInstance().unitSpatialIndex.b(this.posX, this.posY, f)) {
            if (baseUnit.posX + f > this.posX && baseUnit.posX - f < this.posX && baseUnit.posY + f > this.posY && baseUnit.posY - f < this.posY && baseUnit.team != this.aiController && isUnitInRange(baseUnit, 120.0f) && baseUnit.team.c(this.aiController) && this.aiController.isUnitAllowedForSelection(baseUnit)) {
                float ds = Utility.distanceSq(baseUnit.posX, baseUnit.posY, this.posX, this.posY);
                if (ds < bestDs) {
                    bestDs = ds;
                    best = baseUnit;
                }
            }
        }
        return best;
    }

    /* JADX INFO: renamed from: h */
    public int countEnemyUnits() {
        return countEnemyUnitsInRange(60.0f);
    }

    /* JADX INFO: renamed from: a */
    public int countEnemyUnitsInRange(float f) {
        // RWX：同上——空间索引超集 + 原谓词链逐字保留（含 canAttack / 可见性判定）。
        int i = 0;
        float f2 = this.radius + f;
        for (BaseUnit baseUnit : GameEngine.getInstance().unitSpatialIndex.b(this.posX, this.posY, f2)) {
            if (baseUnit.posX + f2 > this.posX && baseUnit.posX - f2 < this.posX && baseUnit.posY + f2 > this.posY && baseUnit.posY - f2 < this.posY && baseUnit.team != this.aiController && isUnitInRange(baseUnit, f) && baseUnit.team.c(this.aiController) && baseUnit.canAttack() && this.aiController.isUnitAllowedForSelection(baseUnit)) {
                i++;
            }
        }
        return i;
    }

    /* JADX INFO: renamed from: i */
    public void commandUnitsToAttackClosestEnemy() {
        GameEngine gameEngine = GameEngine.getInstance();
        BaseUnit closestEnemyUnit = getClosestEnemyUnit();
        if (closestEnemyUnit != null) {
            Command commandNewCommandForTeam = gameEngine.commandController.newCommandForTeam(this.aiController);
            // RWX：谓词要求 team == aiController，走本队快照
            BaseUnit[] baseUnitArrA = this.aiController.rwxTeamUnits();
            int size = Math.min(this.aiController.rwxSnapSize, baseUnitArrA.length);
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_SNAP, size);
            for (int i = 0; i < size; i++) {
                BaseUnit baseUnit = baseUnitArrA[i];
                if (baseUnit instanceof OrderableUnit) {
                    OrderableUnit orderableUnit = (OrderableUnit) baseUnit;
                    if (baseUnit.team == this.aiController && this.aiController.canUnitEngageTarget(baseUnit, closestEnemyUnit) && this.aiController.isEligibleUnitForRandomSelection(orderableUnit) && orderableUnit.hasNoCurrentWaypoint()) {
                        if (!baseUnit.isActive) {
                            if (AIController.isPathPossibleForUnit(baseUnit, this.posX, this.posY, 800.0f)) {
                                commandNewCommandForTeam.addUnitToCommand(orderableUnit);
                            }
                        } else if (AIController.isPathPossibleForUnit(baseUnit, this.posX, this.posY, 540.0f)) {
                            commandNewCommandForTeam.addUnitToCommand(orderableUnit);
                        }
                    }
                }
            }
            commandNewCommandForTeam.setAttackTarget(closestEnemyUnit);
        }
    }

    /* JADX INFO: renamed from: a */
    public boolean countUnitsForStrategy(BaseUnit baseUnit) {
        return belongsToZone(baseUnit, false);
    }

    /* JADX INFO: renamed from: a */
    public boolean belongsToZone(BaseUnit baseUnit, boolean z) {
        if (baseUnit instanceof OrderableUnit) {
            OrderableUnit rwxOrderable = (OrderableUnit) baseUnit;
            if (rwxOrderable.aC == this) {
                if (!z || isUnitInside(baseUnit)) {
                    return true;
                }
                return false;
            }
            // RWX：aC 还没被分配时按位置归属。否则「单位明明站在 zone 里，
            // 却因为这个引用没写上而被当成别家的」，建造者永远找不到。
            if (rwxOrderable.aC == null && isUnitInside(baseUnit)) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: renamed from: a */
    public boolean belongsToZoneUnit(OrderableUnit orderableUnit, boolean z) {
        if (orderableUnit.aC == this) {
            if (!z || isUnitInside(orderableUnit)) {
                return true;
            }
            return false;
        }
        // RWX：同上，aC 未分配时按位置归属。
        if (orderableUnit.aC == null && isUnitInside(orderableUnit)) {
            return true;
        }
        return false;
    }

    /* JADX INFO: renamed from: j */
    public int countEnemyUnitsInsideZone() {
        int i = 0;
        for (Object zoneUnit : getUnitsInZoneIterator()) {
            BaseUnit baseUnit = (BaseUnit) zoneUnit;
            if (this.aiController != baseUnit.team && baseUnit.team.c(this.aiController) && (baseUnit instanceof OrderableUnit) && isUnitInside(baseUnit)) {
                i++;
            }
        }
        return i;
    }

    /* JADX INFO: renamed from: k */
    public UnitListIterator getUnitsInZoneIterator() {
        return GameEngine.getInstance().unitSpatialIndex.b(this.posX, this.posY, this.radius);
    }

    /* JADX INFO: renamed from: x */
    private OrderableUnit findIdleCombatUnit() {
        return findBuilderForUnitType((UnitType) null, (PointF) null, true);
    }

    /**
     * RWX 通用判定：这个单位能不能承担建造任务。
     * 不看阵营、不看单位名，只看引擎能力字段——所以任何模组的任何单位都适用。
     */
    private static boolean rwxIsBuilderCapable(BaseUnit baseUnit) {
        // 克隆自官方 1.15：com.corrodinggames.rts.game.a.a$13.a(com.corrodinggames.rts.game.units.as)
        // 官方字节码：
        //   u.m()                                   -> useAsBuilder
        //   u instanceof CustomUnitConfig && c.fw   -> disableUse，命中就排除
        //   u.o() == ao.e                           -> 移动类型等于 WATER，命中就排除
        // 结论：官方只问「是不是建造者」，建筑照样可以，唯一排除的是禁用项和水面单位。
        UnitType unitType = baseUnit.r();
        if (!unitType.m()) {
            return false;
        }
        if (unitType instanceof CustomUnitConfig && ((CustomUnitConfig) unitType).disableUse) {
            return false;
        }
        return unitType.o() != UnitMovementType.WATER;
    }

    public OrderableUnit findAvailableBuilderForDiag() {
        return findAvailableBuilderForUnitType(null);
    }

    public int unitsInZoneSize() {
        return this.unitsInZone.size();
    }

    public String zoneUnitNames() {
        StringBuilder sb = new StringBuilder();
        for (Object o : this.unitsInZone) {
            if (!(o instanceof OrderableUnit)) {
                continue;
            }
            OrderableUnit u = (OrderableUnit) o;
            sb.append(u.r().getUnitTypeDescriptionShort());
            sb.append(u.aC == this ? "(here)" : u.aC == null ? "(aC=null)" : "(otherZone)");
            sb.append(u.r().m() ? "[B]" : "[-]");
            sb.append(u.r() instanceof CustomUnitConfig ? (((CustomUnitConfig) u.r()).useAsBuilder ? "U" : "u") : "?");
            sb.append(' ');
        }
        return sb.toString().trim();
    }

    /* JADX INFO: renamed from: y */
    private OrderableUnit findAvailableBuilder() {
        return findAvailableBuilderForUnitType(null);
    }

    private static int rwxDiagCounter = 0;

    /* JADX INFO: renamed from: f */
    private OrderableUnit findAvailableBuilderForUnitType(UnitType unitType) {
        return findBuilderForUnitType(unitType, (PointF) null, false);
    }

    /* JADX INFO: renamed from: a */
    private OrderableUnit findBuilderForUnitType(UnitType unitType, PointF pointF, boolean z) {
        // RWX：诊断实测 combat=0 就是最后一刀。
        // 这一行要求「zone 里至少有一个 useAsBuilder 单位」，否则直接返回 null。
        // 纯建筑型基地（如 MC 基地）在 aC 归属/统计口径上算不进来，于是永远找不到建造者。
        // 能否建造由下面的 rwxIsBuilderCapable 判断，不再用这个计数做前置闸门。
        this.lastTimeBuilt = 0;
        float f = Float.MAX_VALUE;
        OrderableUnit orderableUnit = null;
        GameEngine gameEngine = GameEngine.getInstance();
        if (unitType != null && ((gameEngine.isDemo && !unitType.isAvailableInDemo()) || unitType.w())) {
            return null;
        }
        // RWX：findBuilderForUnitType：谓词要求 team == aiController，走本队快照
        BaseUnit[] baseUnitArrA = this.aiController.rwxTeamUnits();
        int size = Math.min(this.aiController.rwxSnapSize, baseUnitArrA.length);
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_SNAP, size);
        for (int i = 0; i < size; i++) {
            BaseUnit baseUnit = baseUnitArrA[i];
            if (baseUnit.team == this.aiController && (countUnitsForStrategy(baseUnit) || isUnitInside(baseUnit)) && baseUnit.transportContainer == null && rwxIsBuilderCapable(baseUnit) && (baseUnit instanceof OrderableUnit) && (this.aiController.isEligibleUnitForRandomSelection(baseUnit) || rwxIsBuilderCapable(baseUnit))) {
                OrderableUnit orderableUnit2 = (OrderableUnit) baseUnit;
                if ((AIUnitActionUtils.isIdleOrReclaiming(orderableUnit2) || rwxIsBuilderCapable(baseUnit)) && (!z || orderableUnit2.I())) {
                    this.lastTimeBuilt++;
                    if (unitType == null || orderableUnit2.canUseActionForUnitType(unitType, true)) {
                        boolean z2 = false;
                        float fDistanceSq = -1.0f;
                        if (pointF != null) {
                            fDistanceSq = Utility.distanceSq(pointF.x, pointF.y, baseUnit.posX, baseUnit.posY);
                        }
                        if (orderableUnit == null) {
                            z2 = true;
                        } else {
                            if (pointF != null && fDistanceSq < f) {
                                z2 = true;
                            }
                            if (Utility.randomFloatInRange(0.0f, 1.0f) < 0.2d) {
                                z2 = true;
                            }
                        }
                        if (z2) {
                            orderableUnit = (OrderableUnit) baseUnit;
                            if (pointF != null) {
                                f = fDistanceSq;
                            }
                        }
                    }
                }
            }
        }
        return orderableUnit;
    }


    /* JADX INFO: renamed from: a */
    private OrderableUnit findHarvesterForBuildTarget(BaseUnit am, PointF pointF, boolean boolean3) {
        if (this.numberOfIdleCombatUnits == 0) {
            return null;
        } else {
            float var4 = Float.MAX_VALUE;
            OrderableUnit var5 = null;
            GameEngine var6 = GameEngine.getInstance();
            // RWX：区域化名单——本循环要求 r().n()（采集兵种），直接走采集桶
            UnitList rwxHarv = rwxChecked(rwxHarvesters(), 2, "harvester");
            BaseUnit[] var7 = rwxHarv.a();
            int var8 = 0;

            io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_BLD, rwxHarv.size());
            for (int var9 = rwxHarv.size(); var8 < var9; var8++) {
                BaseUnit var10 = var7[var8];
                if (var10.team == this.aiController && this.countUnitsForStrategy(var10) && var10.transportContainer == null) {
                    UnitType var11 = var10.r();
                    if (var11.n() && var10 instanceof OrderableUnit && this.aiController.isEligibleUnitForRandomSelection(var10)) {
                        OrderableUnit var12 = (OrderableUnit) var10;
                        boolean var13 = AIUnitActionUtils.isIdle(var12);
                        if (var13 && (!boolean3 || var12.I()) && (am == null || var12.h(am, true))) {
                            boolean var14 = false;
                            if (var11 instanceof CustomUnitConfig) {
                                CustomUnitConfig var15 = (CustomUnitConfig) var11;
                                if (var15.onlyUseAsHarvester_ifBaseHasUnitTagged != null && !this.hasUnitWithAnimationTag(var15.onlyUseAsHarvester_ifBaseHasUnitTagged)) {
                                    continue;
                                }
                            }

                            float var16 = -1.0F;
                            if (pointF != null) {
                                var16 = Utility.distanceSq(pointF.x, pointF.y, var10.posX, var10.posY);
                            }

                            if (var5 == null) {
                                var14 = true;
                            } else {
                                if (pointF != null && var16 < var4) {
                                    var14 = true;
                                }

                                if (Utility.randomFloatInRange(0.0F, 1.0F) < 0.2) {
                                    var14 = true;
                                }
                            }

                            if (var14) {
                                var5 = (OrderableUnit) var10;
                                if (pointF != null) {
                                    var4 = var16;
                                }
                            }
                        }
                    }
                }
            }

            return var5;
        }
    }

    /* JADX INFO: renamed from: g */
    private boolean tryBuildUnit(UnitType unitType) {
        PointF pointFE;
        OrderableUnit orderableUnitA;
        this.lastBuiltUnitType = unitType;
        this.lastBuiltCustomUnit = null;
        this.lastBuiltCustomUnit2 = null;
        GameEngine gameEngine = GameEngine.getInstance();
        if (unitType.p()) {
            pointFE = findClosestResource();
        } else {
            pointFE = findBuildLocation(unitType);
        }
        if (pointFE == null || (orderableUnitA = findBuilderForUnitType(unitType, pointFE, false)) == null) {
            return false;
        }
        if (unitType == UnitTypeEnum.seaFactory) {
            int iC = GameViewUtils.c(pointFE.x, pointFE.y, UnitMovementType.WATER);
            this.aiController.getClass();
            if (iC < 3000) {
                return false;
            }
        }
        int queueSize = 1;
        AbstractUnitAction abstractUnitActionFindActionForUnitType = orderableUnitA.findActionForUnitType(unitType, true);
        if (abstractUnitActionFindActionForUnitType != null) {
            queueSize = abstractUnitActionFindActionForUnitType.getQueueSize();
        } else {
            GameEngine.logColored("buildBuilding: could not find getBuildUnitAction for builder this shouldn't happen:" + unitType.getUnitTypeDescriptionShort());
        }
        if (!abstractUnitActionFindActionForUnitType.b(orderableUnitA) || !abstractUnitActionFindActionForUnitType.canAfford((BaseUnit) orderableUnitA, false)) {
            if (!this.aiController.isPathPossibleBetweenPoints(abstractUnitActionFindActionForUnitType.getPrice(), orderableUnitA)) {
                this.lastBuiltCustomUnit = abstractUnitActionFindActionForUnitType.getPrice();
                this.lastBuiltCustomUnit2 = this.lastBuiltCustomUnit.i(orderableUnitA);
                return true;
            }
            return true;
        }
        if (abstractUnitActionFindActionForUnitType.usesActionTarget()) {
            Command commandNewCommandForTeam = gameEngine.commandController.newCommandForTeam(this.aiController);
            commandNewCommandForTeam.addUnitToCommand(orderableUnitA);
            commandNewCommandForTeam.setActionTarget(abstractUnitActionFindActionForUnitType.getActionId(), pointFE, (BaseUnit) null);
            return true;
        }
        Command commandNewCommandForTeam2 = gameEngine.commandController.newCommandForTeam(this.aiController);
        commandNewCommandForTeam2.addUnitToCommand(orderableUnitA);
        commandNewCommandForTeam2.setBuildTarget(pointFE.x, pointFE.y, unitType, queueSize);
        io.github.rwx.probe.RwxProbe.buildingBuilt(this.aiController, unitType, gameEngine.currentTick);
        return true;
    }

    /* JADX INFO: renamed from: z */
    private boolean hasUnitWithHighPriorityAction() {
        // RWX：hasUnitWithHighPriorityAction：谓词要求 team == aiController，走本队快照
        BaseUnit[] baseUnitArrA = this.aiController.rwxTeamUnits();
        int size = Math.min(this.aiController.rwxSnapSize, baseUnitArrA.length);
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_SNAP, size);
        for (int i = 0; i < size; i++) {
            BaseUnit baseUnit = baseUnitArrA[i];
            if (baseUnit.team == this.aiController && countUnitsForStrategy(baseUnit) && baseUnit.isAlive() && !baseUnit.u() && (baseUnit instanceof OrderableUnit) && ((OrderableUnit) baseUnit).hasHighPriorityAction()) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: renamed from: a */
    public boolean hasUnitWithAnimationTag(AnimationSet animationSet) {
        AnimationSet unitCombatAnimation;
        BaseUnit[] baseUnitArrA = this.unitsInZone.a();
        int size = this.unitsInZone.size();
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_LIST, size);
        for (int i = 0; i < size; i++) {
            BaseUnit baseUnit = baseUnitArrA[i];
            if (baseUnit.team == this.aiController && baseUnit.isAlive() && (unitCombatAnimation = baseUnit.getTags()) != null && AnimationTag.a(animationSet, unitCombatAnimation)) {
                return true;
            }
        }
        return false;
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference fix 'apply assigned field type' failed
    java.lang.UnsupportedOperationException: ArgType.getObject(), call class: class jadx.core.dex.instructions.args.ArgType$UnknownArg
    	at jadx.core.dex.instructions.args.ArgType.getObject(ArgType.java:593)
    	at jadx.core.dex.attributes.nodes.ClassTypeVarsAttr.getTypeVarsMapFor(ClassTypeVarsAttr.java:35)
    	at jadx.core.dex.nodes.utils.TypeUtils.replaceClassGenerics(TypeUtils.java:177)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.insertExplicitUseCast(FixTypesVisitor.java:397)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.tryFieldTypeWithNewCasts(FixTypesVisitor.java:359)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.applyFieldType(FixTypesVisitor.java:309)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.visit(FixTypesVisitor.java:94)
     */
    /* JADX INFO: renamed from: a */
    private OrderableUnit findFactoryForUnitType(UnitType unitType, boolean z, boolean z2) {
        // RWX：区域化名单——只扫工厂桶（原链里就有这两条 instanceof，桶是其超集且保序）
        UnitList rwxList = rwxChecked(rwxFactories(), 0, "factory");
        BaseUnit[] baseUnitArrA = rwxList.a();
        int size = rwxList.size();
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_LIST, size);
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_FACT, size);
        for (int i = 0; i < size; i++) {
            BaseUnit fireUnit = baseUnitArrA[i];
            if (fireUnit.team == this.aiController && fireUnit.isAlive() && this.aiController.isEligibleUnitForRandomSelection(fireUnit) && (fireUnit instanceof FactoryQueueInterface) && (fireUnit instanceof OrderableUnit)) {
                OrderableUnit orderableUnit = (OrderableUnit) fireUnit;
                FactoryQueueInterface factoryQueueInterface = (FactoryQueueInterface) fireUnit;
                AbstractUnitAction unitAction = fireUnit.getUnitAction(unitType);
                if (unitAction != null && ((factoryQueueInterface.dy() || !z) && !unitAction.isAiDisabled(fireUnit) && unitAction.b(orderableUnit) && unitAction.canAfford((BaseUnit) orderableUnit, false) && ((!(fireUnit instanceof CommandCenter) || unitType.m() || getNumberOfExtractors() <= 2 || this.isUnderAttack || !z) && (!z2 || orderableUnit.aD)))) {
                    return orderableUnit;
                }
            }
        }
        return null;
    }

    /* JADX INFO: renamed from: a */
    private boolean tryBuildFromStrategy(UnitBuildStrategy unitBuildStrategy, boolean z) {
        Iterator it = unitBuildStrategy.getShuffledUnits().iterator();
        while (it.hasNext()) {
            if (buildUnit(((UnitBuildPriority) it.next()).unitType, z)) {
                return true;
            }
        }
        return false;
    }

    /* JADX INFO: renamed from: a */
    private boolean buildUnit(UnitType unitType, boolean z) {
        return buildUnit(unitType, z, 1);
    }

    /* JADX INFO: renamed from: a */
    private boolean buildUnit(UnitType unitType, boolean z, int i) {
        if (i < 1) {
            GameEngine.log("AI", "buildUnit: quantity cannot be < 1");
            return false;
        }
        OrderableUnit orderableUnitA = findFactoryForUnitType(unitType, true, z);
        if (orderableUnitA == null) {
        }
        if (orderableUnitA == null) {
            rwNoteUnit(unitType.getUnitTypeDescriptionShort() + (unitType instanceof CustomUnitConfig ? "|mod|nofactory" : "|core|nofactory"));
            return false;
        }
        GameEngine gameEngine = GameEngine.getInstance();
        AbstractUnitAction abstractUnitActionE = orderableUnitA.getUnitAction(unitType);
        if (abstractUnitActionE == null) {
            GameEngine.log("AI", "buildUnit: action is null!");
            return false;
        }
        if (!abstractUnitActionE.b(orderableUnitA)) {
            GameEngine.log("AI", "buildUnit: isAvailable==false");
            return false;
        }
        if (!abstractUnitActionE.canAfford((BaseUnit) orderableUnitA, false)) {
            rwNoteUnit(unitType.getUnitTypeDescriptionShort() + (unitType instanceof CustomUnitConfig ? "|mod|noafford" : "|core|noafford"));
            GameEngine.log("AI", "buildUnit: isActive==false");
            return false;
        }
        if (abstractUnitActionE.isNotAvailable(orderableUnitA)) {
            return false;
        }
        if (abstractUnitActionE.isQueuable() && gameEngine.isDemo) {
            return false;
        }
        for (int i2 = 0; i2 < i; i2++) {
            Command commandNewCommandForTeam = gameEngine.commandController.newCommandForTeam(this.aiController);
            commandNewCommandForTeam.addUnitToCommand(orderableUnitA);
            commandNewCommandForTeam.setActionId(abstractUnitActionE.getQueueId());
        }
        io.github.rwx.probe.RwxProbe.unitQueued(this.aiController, unitType, gameEngine.currentTick);
        return true;
    }

    BaseZone findNearestSupportZone() {
        BaseZone baseZone;
        float f = -1.0f;
        BaseZone baseZone2 = null;
        for (AIStrategyNode aIStrategyNode : this.aiController.activeStrategies) {
            if ((aIStrategyNode instanceof BaseZone) && (baseZone = (BaseZone) aIStrategyNode) != this && baseZone.getNumberOfCombatUnits() > 1) {
                float fA = baseZone.getDistanceSqToZone(this);
                if (baseZone2 == null || fA < f) {
                    f = fA;
                    baseZone2 = baseZone;
                }
            }
        }
        return baseZone2;
    }

    /* JADX INFO: renamed from: m */
    public void requestReinforcement() {
        OrderableUnit orderableUnitX;
        GameEngine gameEngine = GameEngine.getInstance();
        BaseZone baseZoneL = findNearestSupportZone();
        if (baseZoneL != null && baseZoneL.getNumberOfCombatUnits() > 1 && (orderableUnitX = baseZoneL.findIdleCombatUnit()) != null) {
            PointF pointFW = getRandomPointInside();
            if (GameViewUtils.a(orderableUnitX, pointFW.x, pointFW.y)) {
                boolean zIsPathPossibleForUnit = this.aiController.isPathPossibleForUnit(orderableUnitX, pointFW.x, pointFW.y);
                if (zIsPathPossibleForUnit || this.aiController.activeTransporterGroupCount != 0) {
                    Command commandNewCommandForTeam = gameEngine.commandController.newCommandForTeam(this.aiController);
                    commandNewCommandForTeam.addUnitToCommand(orderableUnitX);
                    commandNewCommandForTeam.setMoveTarget(pointFW.x, pointFW.y);
                    this.lastAttackedTimer++;
                    this.updateTimer = Utility.getRandomIntInRange(1800, 2500);
                    if (this.lastAttackedTimer >= 2) {
                        this.updateTimer += 11000.0f;
                    }
                    baseZoneL.numberOfCombatUnits--;
                    if (!zIsPathPossibleForUnit) {
                        boolean z = true;
                        if (orderableUnitX.aB != null) {
                            if (!orderableUnitX.aB.isActive()) {
                                orderableUnitX.aB.removeUnit(orderableUnitX);
                            } else {
                                z = false;
                                if (!orderableUnitX.aB.unitsNeedingTransport.contains(orderableUnitX)) {
                                    orderableUnitX.aB.unitsNeedingTransport.add(orderableUnitX);
                                }
                            }
                        }
                        if (z) {
                            RallyGroup rallyGroup = new RallyGroup(this.aiController);
                            rallyGroup.addUnitNeedingTransport(orderableUnitX);
                            rallyGroup.posX = pointFW.x;
                            rallyGroup.posY = pointFW.y;
                        }
                        this.updateTimer = Utility.getRandomIntInRange(12000, 14000);
                    }
                }
            }
        }
    }

    /* JADX INFO: renamed from: A */
    private BaseUnit findRepairTarget() {
        // RWX：findRepairTarget：谓词要求 team == aiController，走本队快照
        BaseUnit[] baseUnitArrA = this.aiController.rwxTeamUnits();
        int size = Math.min(this.aiController.rwxSnapSize, baseUnitArrA.length);
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_SNAP, size);
        for (int i = 0; i < size; i++) {
            BaseUnit baseUnit = baseUnitArrA[i];
            if (baseUnit.team == this.aiController && belongsToZone(baseUnit, true) && baseUnit.bI() && (baseUnit.currentHealth < baseUnit.maxHealth - 1.0f || baseUnit.buildProgress < 1.0f)) {
                return baseUnit;
            }
        }
        return null;
    }

    /* JADX INFO: renamed from: n */
    public void buildRecommendedHarvesters() {
        GameEngine.getInstance();
        if (this.buildingsInZone.size() == 0 || this.isUnderAttack) {
            return;
        }
        rwxHarvesterCalls++;
        for (int i = 0; i < 8; i++) {
            UnitType randomUnitType = this.aiController.extractorUnitBuildStrategy.getRandomUnitType();
            if (randomUnitType == null) {
                rwxHarvesterNoType++;
                continue;
            }
            if (!hasBuilderFor(randomUnitType)) {
                rwxHarvesterNoBuilder++;
                continue;
            }
            if (tryBuildRecommendedHarvester(randomUnitType)) {
                return;
            }
        }
        if (RWX_AI_STATS && rwxHarvesterCalls % 100 == 0) {
            GameEngine.log("RWX_HARVESTERSTATS calls=" + rwxHarvesterCalls + " built=" + rwxHarvesterBuilt
                + " noType=" + rwxHarvesterNoType + " noBuilder=" + rwxHarvesterNoBuilder
                + " pool=" + this.aiController.extractorUnitBuildStrategy.unitPriorities.size()
                + " tick=" + GameEngine.getInstance().currentTick);
        }
    }


    // RWX：模组没声明 whenUsingAsHarvester_* 时不再一概拒绝。
    // 实测整个整合包 0 处配置，原逻辑让「建资源采集者」这条路对所有单位都关着，
    // 资源建筑只能靠常规队列碰运气。这里给未声明的类型一个每基地默认配额（RWX_HARVESTER_PER_BASE，默认 2）。
    private static final int RWX_DEFAULT_HARVESTER_PER_BASE = rwxReadHarvesterPerBase();
    private static int rwxHarvesterBuilt = 0;
    private static int rwxHarvesterCalls = 0;
    private static int rwxHarvesterNoType = 0;
    private static int rwxHarvesterNoBuilder = 0;

    private static int rwxReadHarvesterPerBase() {
        try {
            String s = System.getenv("RWX_HARVESTER_PER_BASE");
            if (s != null) {
                int v = Integer.parseInt(s.trim());
                if (v >= 0) {
                    return v;
                }
            }
        } catch (Throwable th) {
        }
        return 2;
    }

    public boolean tryBuildRecommendedHarvester(UnitType as) {
        if (!(as instanceof CustomUnitConfig)) {
            // RWX：原版资源单位过去在这里被直接拒绝，等于原版提取器永远进不了采集者策略。
            // 交给 buildUnit 自己判定资源与位置。
            return this.buildUnit(as, true);
        } else {
            CustomUnitConfig var2 = (CustomUnitConfig) as;
            int rwxPerBase = var2.whenUsingAsHarvester_recommendedInEachBase;
            int rwxGlobal = var2.whenUsingAsHarvester_recommendedGlobal;
            if (rwxPerBase == -1 && rwxGlobal == -1) {
                rwxPerBase = RWX_DEFAULT_HARVESTER_PER_BASE;
            }
            if (rwxPerBase == -1 && rwxGlobal == -1) {
                return false;
            } else {
                int var3 = 0;
                int var4 = 0;
                boolean var5 = var2.whenUsingAsHarvester_includeOtherHarvesterCounts;
                // RWX：countUnitsForBuildStrategy：谓词要求 team == aiController，走本队快照
                BaseUnit[] var6 = this.aiController.rwxTeamUnits();
                int var7 = 0;
                io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_SNAP, Math.min(this.aiController.rwxSnapSize, var6.length));
                for (int var8 = Math.min(this.aiController.rwxSnapSize, var6.length); var7 < var8; var7++) {
                    BaseUnit var9 = var6[var7];
                    if (var9.team == this.aiController && var9.transportContainer == null && var9 instanceof OrderableUnit && this.aiController.isEligibleUnitForRandomSelection(var9)) {
                        OrderableUnit var10 = (OrderableUnit) var9;
                        UnitType var11 = var9.r();
                        if (var5 ? var11.n() : var11 == var2 || var2.relatedUnits.contains(var11)) {
                            var4++;
                            if (this.countUnitsForStrategy(var9)) {
                                var3++;
                            }
                        }
                    }
                }

                if (rwxPerBase != -1 && var3 >= rwxPerBase) {
                    return false;
                } else {
                    boolean rwxBuilt = rwxGlobal == -1 || var4 < rwxGlobal ? this.buildUnit(var2, true) : false;
                    if (rwxBuilt && RWX_AI_STATS) {
                        rwxHarvesterBuilt++;
                        GameEngine.log("RWX_HARVESTERSTATS built=" + rwxHarvesterBuilt + " type=" + var2.v() + " perBase=" + rwxPerBase + " global=" + rwxGlobal + " tick=" + GameEngine.getInstance().currentTick);
                    }
                    return rwxBuilt;
                }
            }
        }
    }

    /* JADX INFO: renamed from: o */
    public void sendHarvesterToReclaim() {
        BaseUnit baseUnitR;
        if (this.buildingsInZone.size() != 0 && (baseUnitR = getRandomBuilding()) != null) {
            this.tempPoint2.a(baseUnitR.posX, baseUnitR.posY);
            OrderableUnit orderableUnitA = findHarvesterForBuildTarget(baseUnitR, this.tempPoint2, true);
            if (orderableUnitA != null) {
                issueReclaimCommand(orderableUnitA, baseUnitR);
            }
        }
    }

    /* JADX INFO: renamed from: q */
    public void retargetHarvestersToBetterBuilding() {
        OrderableUnit orderableUnit;
        UnitCommand currentWaypoint;
        BaseUnit targetUnit;
        if (this.buildingsInZone.size() != 0 && this.lastBuiltCustomUnit2 != null) {
            // RWX：区域化名单——判据含 baseUnit.r().n()，走采集桶
            UnitList rwxListR = rwxChecked(rwxHarvesters(), 2, "harvester");
            BaseUnit[] baseUnitArrA = rwxListR.a();
            int size = rwxListR.size();
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_LIST, size);
            for (int i = 0; i < size; i++) {
                BaseUnit baseUnit = baseUnitArrA[i];
                if (baseUnit.team == this.aiController && countUnitsForStrategy(baseUnit) && baseUnit.transportContainer == null && baseUnit.r().n() && (baseUnit instanceof OrderableUnit) && this.aiController.isEligibleUnitForRandomSelection(baseUnit) && Utility.randomFloatInRange(0.0f, 1.0f) <= 0.3d && (currentWaypoint = (orderableUnit = (OrderableUnit) baseUnit).getCurrentWaypoint()) != null && currentWaypoint.getCommandType() == UnitCommandType.reclaim && (targetUnit = currentWaypoint.getTargetUnit()) != null && targetUnit.getResourceRate() > 0.0f && !this.lastBuiltCustomUnit2.c(targetUnit.getBuildPrice())) {
                    issueReclaimCommand(orderableUnit, getRandomBuilding());
                    return;
                }
            }
        }
    }

    /* JADX INFO: renamed from: r */
    public BaseUnit getRandomBuilding() {
        BaseUnit baseUnit = null;
        for (int i = 0; i < 20; i++) {
            baseUnit = this.buildingsInZone.get(Utility.getRandomIntInRange(0, this.buildingsInZone.size() - 1));
            if (baseUnit == null || this.lastBuiltCustomUnit2 == null || this.lastBuiltCustomUnit2.c(baseUnit.getBuildPrice())) {
                break;
            }
        }
        return baseUnit;
    }

    /* JADX INFO: renamed from: a */
    public void issueReclaimCommand(OrderableUnit orderableUnit, BaseUnit baseUnit) {
        GameEngine gameEngine = GameEngine.getInstance();
        if (orderableUnit.g(baseUnit, true)) {
            Command commandNewCommandForTeam = gameEngine.commandController.newCommandForTeam(this.aiController);
            commandNewCommandForTeam.addUnitToCommand(orderableUnit);
            commandNewCommandForTeam.setReclaimTarget(baseUnit);
        }
    }

    /* JADX INFO: renamed from: s */
    public void repairDamagedBuildings() {
        GameEngine gameEngine = GameEngine.getInstance();
        BaseUnit baseUnitM112A = findRepairTarget();
        if (baseUnitM112A != null) {
            this.tempPoint2.a(baseUnitM112A.posX, baseUnitM112A.posY);
            OrderableUnit orderableUnitA = findBuilderForUnitType((UnitType) null, this.tempPoint2, true);
            if (orderableUnitA != null && orderableUnitA.canRepairTarget(baseUnitM112A) && baseUnitM112A.e(orderableUnitA) < 2) {
                Command commandNewCommandForTeam = gameEngine.commandController.newCommandForTeam(this.aiController);
                commandNewCommandForTeam.addUnitToCommand(orderableUnitA);
                commandNewCommandForTeam.setRepairTarget(baseUnitM112A);
            }
        }
    }

    /* JADX INFO: renamed from: b */
    public void updateZone(float f) {
        // RWX 诊断：直接打印 AI zone 里「有没有建造者」「有多少可动单位」，不再靠事后推断。
        rwxDiagCounter++;
        // RWX：refreshUnitCounts + updateUnitsInZone + hasUnitWithHighPriorityAction 原先各扫一遍
        // BaseUnit.bE（每周期 3 次全场遍历），合并成一次 rwxZoneScan，判据逐一保留。
        this.hasResources = rwxZoneScan();
        int i = this.numberOfFactories;
        int i2 = this.numberOfExtractors;
        if (this.hasResources) {
            this.hasLandAccess = true;
        }
        if (i >= 1) {
            repairDamagedBuildings();
        }
        // RWX：不再用「本区已有提取者」当门。全自定义资源的模组常常一座提取器都不标，
        // 这个门会把经济建筑整条链锁死（T0 有 26 万 EMC 却 45000 tick 不动作）。
        if (this.isPrimary) {
            buildRecommendedHarvesters();
            retargetHarvestersToBetterBuilding();
            sendHarvesterToReclaim();
        }
        if (i < 2 && this.landUnitsScore == 0.0f) {
            this.landUnitsScore = 300.0f;
            int iShouldWriteForUnitType = this.aiController.countUnitsForBuildStrategyWithFilter(this.aiController.fabricatorUnitBuildStrategy, UnitFilterMode.include);
            if (!this.isUnderAttack || iShouldWriteForUnitType <= 2) {
                boolean z = Utility.getRandomIntInRange(0, 100) < 5;
                if (!z && tryBuildFromStrategy(this.aiController.fabricatorUnitBuildStrategy, true)) {
                    this.isInitialized = false;
                    this.landUnitsScore = 900.0f;
                } else {
                    if (!z) {
                        this.isInitialized = true;
                    }
                    if (!this.isUnderAttack && this.lastTimeReclaimed == 0.0f && i < 1 && this.updateTimer == 0.0f) {
                        requestReinforcement();
                    }
                }
            }
        }
        int iJ = countEnemyUnitsInsideZone();
        if (i == 0 && i2 == 0) {
            this.waterUnitsScore += f;
            if (iJ > 2) {
                this.waterUnitsScore += 2.0f * f;
            }
            if (iJ > 5) {
                this.waterUnitsScore += 4.0f * f;
            }
        } else {
            this.waterUnitsScore = Utility.moveTowardsZero(this.waterUnitsScore, f);
        }
        if (this.waterUnitsScore > 11000.0f) {
            destroy();
        }
        if (this.stage == BaseZoneStage.Pre && ((i != 0 && i2 != 0) || (i2 > 5 && iJ == 0))) {
            this.extractorScore += f;
            if (this.extractorScore > 2000.0f) {
                this.stage = BaseZoneStage.Active;
            }
        }
        fixOverlaps();
    }

    /* JADX INFO: renamed from: t */
    public void fixOverlaps() {
        if (this.stage == null) {
            GameEngine.logErrorColored("fixOverlaps: this.state==null");
            GameEngine.logErrorColored("id:" + this.strategyId);
            GameEngine.logErrorColored("x:" + this.posX);
            GameEngine.logErrorColored("y:" + this.posY);
            GameEngine.logErrorColored("radius:" + this.radius);
            if (this.aiController != null) {
                GameEngine.logErrorColored("team:" + this.aiController.teamId);
                return;
            }
            return;
        }
        for (AIStrategyNode aIStrategyNode : this.aiController.strategyNodes) {
            if ((aIStrategyNode instanceof BaseZone) && aIStrategyNode != this) {
                BaseZone baseZone = (BaseZone) aIStrategyNode;
                if (Utility.distanceSq(this.posX, this.posY, baseZone.posX, baseZone.posY) < 400.0f) {
                    if (baseZone.stage == null) {
                        GameEngine.logErrorColored("fixOverlaps: targetBase.state==null");
                    } else if (baseZone.stage.getOrdinal() < this.stage.getOrdinal()) {
                        baseZone.destroy();
                    } else {
                        destroy();
                    }
                }
            }
        }
    }

    /* JADX INFO: renamed from: u */
    public int getNumberOfExtractors() {
        return this.numberOfExtractors;
    }

    /* JADX WARN: Multi-variable type inference failed */
    /* JADX WARN: Type inference fix 'apply assigned field type' failed
    java.lang.UnsupportedOperationException: ArgType.getObject(), call class: class jadx.core.dex.instructions.args.ArgType$UnknownArg
    	at jadx.core.dex.instructions.args.ArgType.getObject(ArgType.java:593)
    	at jadx.core.dex.attributes.nodes.ClassTypeVarsAttr.getTypeVarsMapFor(ClassTypeVarsAttr.java:35)
    	at jadx.core.dex.nodes.utils.TypeUtils.replaceClassGenerics(TypeUtils.java:177)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.insertExplicitUseCast(FixTypesVisitor.java:397)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.tryFieldTypeWithNewCasts(FixTypesVisitor.java:359)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.applyFieldType(FixTypesVisitor.java:309)
    	at jadx.core.dex.visitors.typeinference.FixTypesVisitor.visit(FixTypesVisitor.java:94)
     */
    /**
     * RWX：一趟遍历同时完成 updateUnitsInZone、refreshUnitCounts 的计数循环和
     * hasUnitWithHighPriorityAction 的判定。各谓词与原实现逐字一致：
     *  m1 = countUnitsForStrategy（aC==this，或 aC==null 且在圈内）→ unitsInZone / 高优先动作
     *  m2 = belongsToZoneUnit(false) || isUnitInside，再加 isAlive/!u()/资格 → 四个计数
     */
    private boolean rwxZoneScan() {
        this.numberOfExtractors = 0;
        this.numberOfFactories = 0;
        this.numberOfIdleCombatUnits = 0;
        this.numberOfCombatUnits = 0;
        this.isPrimary = false;
        this.buildingsInZone.clear();
        for (Object o : getUnitsInZoneIterator()) {
            BaseUnit baseUnit = (BaseUnit) o;
            if ((baseUnit.getResourceRate() > 0.0f || rwxIsBuilderCapable(baseUnit)) && isUnitInside(baseUnit)) {
                this.isPrimary = true;
                this.buildingsInZone.add(baseUnit);
            }
        }
        this.unitTypesInZone.clear();
        this.unitsInZone.clear();
        rwxResetBuckets();
        boolean highPriority = false;
        // RWX：判据要求 team == aiController，直接走本队快照，不再全场遍历
        BaseUnit[] baseUnitArrA = this.aiController.rwxTeamUnits();
        int size = Math.min(this.aiController.rwxSnapSize, baseUnitArrA.length);
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_SNAP, size);
        for (int i = 0; i < size; i++) {
            BaseUnit baseUnit = baseUnitArrA[i];
            if (baseUnit.team != this.aiController || !(baseUnit instanceof OrderableUnit)) {
                continue;
            }
            OrderableUnit orderableUnit = (OrderableUnit) baseUnit;
            boolean inside = isUnitInside(baseUnit);
            boolean m1 = orderableUnit.aC == this || (orderableUnit.aC == null && inside);
            boolean m2 = orderableUnit.aC == this || inside;
            if (m1 && !baseUnit.isDead && !baseUnit.u()) {
                this.unitsInZone.add(baseUnit);
                rwxBucketAdd(baseUnit);
                UnitType unitType = baseUnit.unitType;
                if (!this.unitTypesInZone.contains(unitType)) {
                    this.unitTypesInZone.add(unitType);
                }
                if (!highPriority && baseUnit.isAlive() && orderableUnit.hasHighPriorityAction()) {
                    highPriority = true;
                }
            }
            if (m2 && baseUnit.isAlive() && !baseUnit.u() && (this.aiController.isEligibleUnitForRandomSelection(baseUnit) || rwxIsBuilderCapable(baseUnit))) {
                UnitType unitTypeR = baseUnit.r();
                if (unitTypeR.isBuildingUnit()) {
                    this.numberOfExtractors++;
                }
                if (unitTypeR.m()) {
                    this.numberOfFactories++;
                }
                if ((unitTypeR.m() || rwxIsBuilderCapable(baseUnit)) && (AIUnitActionUtils.isIdleOrReclaiming(orderableUnit) || rwxIsBuilderCapable(baseUnit))) {
                    this.numberOfCombatUnits++;
                }
                if (unitTypeR.n()) {
                    this.numberOfIdleCombatUnits++;
                }
                if (baseUnit instanceof FactoryQueueInterface) {
                    this.numberOfFactories += ((FactoryQueueInterface) baseUnit).h(UnitTypeEnum.builder);
                }
            }
        }
        return highPriority;
    }

    /* JADX INFO: renamed from: c */
    public void refreshUnitCounts(float f) {
        this.numberOfExtractors = 0;
        this.numberOfFactories = 0;
        this.numberOfIdleCombatUnits = 0;
        this.numberOfCombatUnits = 0;
        this.isPrimary = false;
        this.buildingsInZone.clear();
        for (Object o : getUnitsInZoneIterator()) {
            BaseUnit baseUnit = (BaseUnit) o;
            // RWX：primary 原来只认「产资源的单位」。纯建筑型基地（MC 这类）自己产 0 资源，
            // 于是 isPrimary 永远 false，updateZone 里整块建造逻辑被跳过。
            // 现在「能承担建造的单位」也算 primary——能源/建筑都不靠硬编码名字判断。
            if ((baseUnit.getResourceRate() > 0.0f || rwxIsBuilderCapable(baseUnit)) && isUnitInside(baseUnit)) {
                this.isPrimary = true;
                this.buildingsInZone.add(baseUnit);
            }
        }
        // RWX：refreshUnitCounts：谓词要求 team == aiController，走本队快照
        BaseUnit[] baseUnitArrA = this.aiController.rwxTeamUnits();
        int size = Math.min(this.aiController.rwxSnapSize, baseUnitArrA.length);
        io.github.rwx.probe.RwxPerf.aiHitAdd(io.github.rwx.probe.RwxPerf.HIT_SNAP, size);
        for (int i = 0; i < size; i++) {
            BaseUnit fireUnit = baseUnitArrA[i];
            if (fireUnit.team == this.aiController && (fireUnit instanceof OrderableUnit)) {
                OrderableUnit orderableUnit = (OrderableUnit) fireUnit;
                if ((belongsToZoneUnit(orderableUnit, false) || isUnitInside(fireUnit)) && fireUnit.isAlive() && (this.aiController.isEligibleUnitForRandomSelection(fireUnit) || rwxIsBuilderCapable(fireUnit)) && !fireUnit.u()) {
                    UnitType unitTypeR = fireUnit.r();
                    if (unitTypeR.isBuildingUnit()) {
                        this.numberOfExtractors++;
                    }
                    if (unitTypeR.m()) {
                        this.numberOfFactories++;
                    }
                    // RWX：空闲且「能承担建造」的单位都计入，不再只认 useAsBuilder。
                    // 否则纯建筑基地会让 combat 恒为 0，建造流程整条被封。
                    // RWX：建造者不要求「完全空闲」。基地这类 useAsBuilder 建筑常常一直在忙
                    // （移动/旧命令），严格要求空闲会让它永远算不进，建造入口整条封死。
                    if ((unitTypeR.m() || rwxIsBuilderCapable(fireUnit)) && (AIUnitActionUtils.isIdleOrReclaiming(orderableUnit) || rwxIsBuilderCapable(fireUnit))) {
                        this.numberOfCombatUnits++;
                    }
                    if (unitTypeR.n()) {
                        this.numberOfIdleCombatUnits++;
                    }
                    if (fireUnit instanceof FactoryQueueInterface) {
                        this.numberOfFactories += ((FactoryQueueInterface) fireUnit).h(UnitTypeEnum.builder);
                    }
                }
            }
        }
    }

    /* JADX INFO: renamed from: d */
    public void updateZoneStrategy(float f) {
        UnitType bestBuildingToBuild;
        this.isUnderAttack = hasEnemyUnits();
        this.isContested = this.isUnderAttack;
        if (this.isUnderAttack) {
            this.lastTimeReclaimed += f;
            this.lastTimeScouted = 100.0f;
        } else {
            this.lastTimeReclaimed = 0.0f;
        }
        if (this.lastTimeReclaimed > 6000.0f) {
            this.isUnderAttack = false;
        }
        this.lastTimeAttackedByEnemy = Utility.moveTowardsZero(this.lastTimeAttackedByEnemy, f);
        this.defensiveScore = Utility.moveTowardsZero(this.defensiveScore, f);
        this.updateTimer = Utility.moveTowardsZero(this.updateTimer, f);
        this.landUnitsScore = Utility.moveTowardsZero(this.landUnitsScore, f);
        this.airUnitsScore = Utility.moveTowardsZero(this.airUnitsScore, f);
        if (this.isUnderAttack && this.airUnitsScore == 0.0f) {
            this.airUnitsScore = 100 + (this.strategyId % 15);
            if (!this.aiController.isAggressive) {
                long rwxAtkT = io.github.rwx.probe.RwxPerf.now();
                commandUnitsToAttackClosestEnemy();
                io.github.rwx.probe.RwxPerf.mark(io.github.rwx.probe.RwxPerf.AI_ATK, rwxAtkT);
            }
        }
        if (this.defensiveScore <= 0.0f) {
            this.defensiveScore = 270 + (this.strategyId % 15);
            if (this.aiController.isInsaneDifficulty()) {
                this.defensiveScore = SlickToAndroidKeycodes.AndroidCodes.KEYCODE_BUTTON_3 + (this.strategyId % 15);
            }
            if (this.lastFoundBuildingPriority < 0.2d) {
                this.defensiveScore += 180.0f;
            }
            if (this.lastFoundBuildingPriority < 0.08d) {
                this.defensiveScore += 180.0f;
            }
            OrderableUnit rwxBuilder = null;
            { long t0 = io.github.rwx.probe.RwxPerf.now(); rwxBuilder = findAvailableBuilder(); io.github.rwx.probe.RwxPerf.mark(io.github.rwx.probe.RwxPerf.AI_FAB, t0); }
            UnitType rwxBestB = null;
            if (rwxBuilder != null) { long t0 = io.github.rwx.probe.RwxPerf.now(); rwxBestB = getBestBuildingToBuild(); io.github.rwx.probe.RwxPerf.mark(io.github.rwx.probe.RwxPerf.AI_GBB, t0); }
            if (rwxBestB != null && ((this.lastFoundBuildingPriority > 0.8d || this.aiController.hasCredits(1300.0d)) && ((this.lastFoundBuildingPriority > 0.4d || this.aiController.hasCredits(1700.0d)) && ((this.lastFoundBuildingPriority > 0.2d || this.aiController.hasCredits(2100.0d)) && ((this.lastFoundBuildingPriority > 0.1d || this.aiController.hasCredits(2800.0d)) && ((this.lastFoundBuildingPriority > 0.05d || this.aiController.hasCredits(3100.0d)) && (this.lastFoundBuildingPriority > 0.01d || this.aiController.hasCredits(4800.0d)))))))) {
                this.numberOfLandUnits++;
                long tTbu = io.github.rwx.probe.RwxPerf.now();
                boolean rwxBuiltOk = tryBuildUnit(rwxBestB);
                io.github.rwx.probe.RwxPerf.mark(io.github.rwx.probe.RwxPerf.AI_TBU, tTbu);
                if (!rwxBuiltOk) {
                    this.defensiveScore -= 120.0f;
                    this.numberOfAirUnits++;
                }
            }
        }
        float fU = getNumberOfExtractors() / 3.0f;
        if (fU < 1.0f) {
            fU = 1.0f;
        }
        // RWX：生产节奏整体提速——所有积累速率翻倍，配合上限 15 与每单位扣 0.5。
        // 这条分数陆/空/海三军共用，改一处四类兵种同时受益。
        if (this.isUnderAttack) {
            this.reclaimScore = (float) (((double) this.reclaimScore) + (((double) f) * 0.03d));
        }
        if (this.lastFoundBuildingPriority < 0.6d) {
            if (this.aiController.unitCount < 2) {
                this.reclaimScore = (float) (((double) this.reclaimScore) + (((double) f) * 1.4E-3d * ((double) fU)));
            } else if (this.aiController.hasCredits(1200.0d)) {
                this.reclaimScore = (float) (((double) this.reclaimScore) + (((double) f) * 2.0E-4d * ((double) fU)));
            }
            if (this.aiController.hasCredits(1600.0d)) {
                this.reclaimScore = (float) (((double) this.reclaimScore) + (((double) f) * 0.002d));
            }
            if (this.aiController.hasCredits(2200.0d)) {
                this.reclaimScore = (float) (((double) this.reclaimScore) + (((double) f) * 0.002d));
            }
            if (this.aiController.hasCredits(2600.0d)) {
                this.reclaimScore = (float) (((double) this.reclaimScore) + (((double) f) * 0.002d));
            }
            if (this.aiController.hasCredits(8000.0d)) {
                this.reclaimScore = (float) (((double) this.reclaimScore) + (((double) f) * 0.01d));
            }
            if (this.aiController.hasCredits(9000.0d)) {
                this.reclaimScore = (float) (((double) this.reclaimScore) + (((double) f) * 0.02d));
            }
            if (this.aiController.hasCredits(10100.0d)) {
                this.reclaimScore = (float) (((double) this.reclaimScore) + (((double) f) * 0.02d));
            }
            if (this.aiController.hasCredits(30000.0d)) {
                this.reclaimScore = (float) (((double) this.reclaimScore) + (((double) f) * 0.1d));
            }
        }
        if (this.aiController.hasCredits(5000.0d)) {
            this.reclaimScore = (float) (((double) this.reclaimScore) + (((double) f) * 0.002d));
        }
        // RWX：上限抬到 15 后，穷困压制也要跟着抬，否则一没钱就被压回 1.2，攒不到 15。
        if (!this.aiController.hasCredits(800.0d) && !this.isUnderAttack && this.reclaimScore > 5.0f) {
            this.reclaimScore = 5.0f;
        }
        if (this.reclaimScore > 15.0f) {
            this.reclaimScore = 15.0f;
        }
        long rwxTbT = io.github.rwx.probe.RwxPerf.now(); // RWX：aiTryB=12 连发 tryBuildCombatUnits 合计
        for (int i = 0; i < 12; i++) {
            tryBuildCombatUnits();
            if (this.reclaimScore < 3.0f) {
                io.github.rwx.probe.RwxPerf.mark(io.github.rwx.probe.RwxPerf.AI_TRYB, rwxTbT);
                return;
            }
        }
        io.github.rwx.probe.RwxPerf.mark(io.github.rwx.probe.RwxPerf.AI_TRYB, rwxTbT);
    }

    /* JADX INFO: renamed from: a */
    public void addRandomUnitTypesForBuild(ArrayList arrayList, UnitBuildStrategy unitBuildStrategy, UnitMovementType unitMovementType, int i) {
        this.rallyPoints.clear();
        for (int i2 = 0; i2 < i; i2++) {
            UnitType randomUnitTypeByMovement = unitBuildStrategy.getRandomUnitTypeByMovement(unitMovementType);
            if (randomUnitTypeByMovement != null && !this.rallyPoints.contains(randomUnitTypeByMovement)) {
                this.rallyPoints.add(randomUnitTypeByMovement);
            }
        }
        arrayList.addAll(this.rallyPoints);
    }

    /* JADX INFO: renamed from: v */
    public void tryBuildCombatUnits() {
        UnitMovementType unitMovementType;
        BaseUnit closestEnemyUnit;
        int iCountIdleCombatUnits = countIdleCombatUnits();
        int i = 12;
        int i2 = 50;
        if (this.aiController.isInsaneDifficulty()) {
            i2 = 65;
            i = 16;
        }
        boolean zA = this.aiController.hasCredits(25000.0d);
        ArrayList arrayList = new ArrayList();
        boolean zIsAttackBlockedByConditions = this.aiController.isAttackBlockedByConditions();
        boolean zShouldLaunchAttack = this.aiController.shouldLaunchAttack();
        float f = 0.4f;
        float f2 = 0.3f;
        if (!this.aiController.isReadyForAttack) {
            f = 0.1f;
            f2 = 0.5f;
        }
        if (!this.aiController.hasSufficientUnitsForAttack) {
            f = 0.2f;
            f2 = 0.1f;
        }
        float fRandomFloatInRange = Utility.randomFloatInRange(0.0f, 1.0f);
        if (fRandomFloatInRange < f) {
            unitMovementType = UnitMovementType.LAND;
        } else if (fRandomFloatInRange < f + f2) {
            unitMovementType = UnitMovementType.HOVER;
        } else {
            unitMovementType = UnitMovementType.AIR;
        }
        if ((this.aiController.hasCredits(1300.0d) && this.reclaimScore >= 1.0f) || (this.aiController.hasCredits(300.0d) && this.reclaimScore >= 3.0f)) {
            if (this.aiController.isPathfindingOverloaded() && this.aiController.buildingCount < i && Utility.getRandomInt(100) < 35) {
                addRandomUnitTypesForBuild(arrayList, this.aiController.seaUnitBuildStrategy, null, 2);
                if (zA) {
                }
            }
            if (iCountIdleCombatUnits < 3 && this.aiController.unitProductionTimer < i2) {
                if (unitMovementType == UnitMovementType.LAND) {
                    addRandomUnitTypesForBuild(arrayList, this.aiController.builderUnitBuildStrategy, null, 4);
                    if (zA) {
                    }
                } else if (unitMovementType == UnitMovementType.HOVER) {
                    addRandomUnitTypesForBuild(arrayList, this.aiController.landUnitBuildStrategy, null, 4);
                    if (zA) {
                    }
                } else {
                    addRandomUnitTypesForBuild(arrayList, this.aiController.airUnitBuildStrategy, null, 4);
                    if (zA) {
                    }
                }
            }
            if (this.reclaimScore >= 1.0f && zIsAttackBlockedByConditions && this.lastTimeAttackedByEnemy == 0.0f) {
                int iShouldWriteForUnitType = this.aiController.countUnitsForBuildStrategyWithFilter(this.aiController.baseDefenseUnitBuildStrategy, UnitFilterMode.include);
                int iShouldWriteForUnitType2 = iShouldWriteForUnitType + this.aiController.countUnitsForBuildStrategyWithFilter(this.aiController.scoutUnitBuildStrategy, UnitFilterMode.include);
                int iCountUnitsInGroups = this.aiController.countUnitsInGroups();
                if ((this.aiController.hasCredits(1700.0d) || iCountUnitsInGroups > 10 || (this.aiController.attackCooldownTimer == 0 && iCountUnitsInGroups >= 1 && iShouldWriteForUnitType == 0)) && (iShouldWriteForUnitType2 < 3 || (iCountUnitsInGroups > 20 && iShouldWriteForUnitType2 < 5))) {
                    if (zShouldLaunchAttack && iShouldWriteForUnitType2 < 2) {
                        addRandomUnitTypesForBuild(arrayList, this.aiController.antiNukeUnitBuildStrategy, null, 2);
                    } else {
                        addRandomUnitTypesForBuild(arrayList, this.aiController.antiNukeUnitBuildStrategy, UnitMovementType.AIR, 2);
                    }
                }
            }
        }
        if (arrayList.size() == 0) {
            this.numberOfWaterUnits++;
        }
        while (arrayList.size() != 0) {
            UnitType unitType = (UnitType) arrayList.remove(arrayList.size() - 1);
            BaseUnit baseUnitFindAttackDamageSource = BaseUnit.findAttackDamageSource(unitType);
            boolean z = true;
            if (this.isUnderAttack && Utility.getRandomInt(100) < 10 && (closestEnemyUnit = getClosestEnemyUnit()) != null && !this.aiController.canUnitEngageTarget(baseUnitFindAttackDamageSource, closestEnemyUnit)) {
                this.numberOfWaterUnits++;
                z = false;
            }
            if (z) {
                long rwxBdT = io.github.rwx.probe.RwxPerf.now();
                boolean rwBuilt = buildUnit(unitType, false);
                io.github.rwx.probe.RwxPerf.mark(io.github.rwx.probe.RwxPerf.AI_BLD, rwxBdT);
                rwNoteUnit(unitType.getUnitTypeDescriptionShort() + (unitType instanceof CustomUnitConfig ? "|mod|" : "|core|") + (rwBuilt ? "ok" : "fail"));
                if (rwBuilt) {
                    this.numberOfWaterUnits++;
                    this.aiController.buildPreferenceCache.invalidateUnitType(unitType);
                    this.reclaimScore -= 0.5f;
                    if (this.aiController.isNonCombatCustomUnit(baseUnitFindAttackDamageSource)) {
                        this.lastTimeAttackedByEnemy = 1000.0f;
                        this.aiController.attackCooldownTimer++;
                        return;
                    }
                    return;
                }
                this.numberOfBuildings++;
            }
        }
    }

    /* JADX INFO: renamed from: a */
    public void recordBuiltFactoryUnit(OrderableUnit orderableUnit, UnitPrice unitPrice, boolean z) {
        this.lastBuiltFactoryType = orderableUnit.r();
        if (z) {
            this.lastBuiltFactoryCustomUnit = null;
            this.lastBuiltCustomUnit2 = null;
        } else {
            this.lastBuiltFactoryCustomUnit = unitPrice;
            this.lastBuiltCustomUnit2 = unitPrice.i(orderableUnit);
        }
    }
}
