package com.corrodinggames.rts.gameFramework;

import com.corrodinggames.rts.game.PlayerTeam;
import com.corrodinggames.rts.game.map.TileMap;
import com.corrodinggames.rts.game.units.*;
import com.corrodinggames.rts.game.units.actions.AbstractUnitAction;
import com.corrodinggames.rts.game.units.actions.ActionId;
import com.corrodinggames.rts.game.units.actions.PingMapAction;
import com.corrodinggames.rts.game.units.buildings.FactoryQueueInterface;
import com.corrodinggames.rts.game.units.custom.logicBooleans.VariableScope;
import com.corrodinggames.rts.gameFramework.network.GameInputStream;
import com.corrodinggames.rts.gameFramework.network.GameOutputStream;
import com.corrodinggames.rts.gameFramework.network.NetworkEngine;
import com.corrodinggames.rts.gameFramework.ui.LagHidingManager;
import com.corrodinggames.rts.gameFramework.utility.FastArrayList;
import io.github.rwx.geometry.PointF;
import io.github.rwx.mod.registry.TeamActionRegistry;

import java.io.IOException;
import java.util.AbstractList;
import java.util.Iterator;

/* JADX INFO: renamed from: com.corrodinggames.rts.gameFramework.e */
/* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/gameFramework/e.class */
public class Command {

    /* JADX INFO: renamed from: a */
    public boolean isReplayCommand;

    /* JADX INFO: renamed from: b */
    public String debugStackTrace;

    /* JADX INFO: renamed from: c */
    public int scheduledTick;

    /* JADX INFO: renamed from: d */
    public int createdTick;

    /* JADX INFO: renamed from: i */
    public PlayerTeam team;

    /* JADX INFO: renamed from: j */
    public UnitCommand unitCommand;

    /* JADX INFO: renamed from: l */
    public PointF targetPoint;

    /* JADX INFO: renamed from: m */
    public BaseUnit targetUnit;

    /* JADX INFO: renamed from: n */
    public AttackMode attackMode;

    /* JADX INFO: renamed from: z */
    private PointF rallyPoint;

    /* RWX 阵型状态：只在本地派发命令时用，不参与网络/存档 */
    private transient boolean rwxFormationActive;
    private transient float rwxFormationCenterX;
    private transient float rwxFormationCenterY;
    private transient int rwxFormationIndex;
    private transient float rwxFormationSpread;
    private transient float rwxFormationSpacing;
    // RWX：集结点 —— 在目的地往回一小段的开阔处收拢，再展开阵形，
    // 不是像上一版那样在出发点绕圈
    private transient boolean rwxStagingReady;
    private transient float rwxStagingX;
    private transient float rwxStagingY;
    // RWX：路径点先行 —— 中间拐点（tile 坐标），派发时逐段追加为 move waypoint
    private transient int[] rwxCorridorX;
    private transient int[] rwxCorridorY;
    private transient int rwxCorridorCount;
    // RWX：走廊搜索用的共享缓存 —— 每条命令都会 new 一个 Command，
    // 这些数组必须 static，否则每次右键都分配约 1.5 MB，GC 卡顿比省下的还多。
    private static int[] rwxPathG;
    private static int[] rwxPathFrom;
    private static int[] rwxPathStamp;
    private static int rwxPathGen;
    // RWX：体积膨胀缓存 —— 某格能否放下一艘半径 r 的船（周围一圈都得是通航格）
    private static float rwxCorridorLen;
    private static com.corrodinggames.rts.game.units.UnitMovementType rwxCorridorType;
    private static int rwxCorridorEndTX;
    private static int rwxCorridorEndTY;
    private static int[] rwxTFreeStamp;
    private static byte[] rwxTFreeVal;
    private static int rwxTFreeGen;
    private static int rwxTFreeR; // 保留给调试; 实际判据用 rwxTFreePx
    private static float rwxTFreePx;

    /* JADX INFO: renamed from: p */
    public PlayerTeam sourceTeam;

    /* JADX INFO: renamed from: q */
    public short allowedTeamMask;

    /* JADX INFO: renamed from: r */
    public boolean isSystemAction;

    /* JADX INFO: renamed from: s */
    public float gameSpeedChange;

    /* JADX INFO: renamed from: t */
    public float systemFloat;

    /* JADX INFO: renamed from: u */
    public int systemActionType;

    public io.github.rwx.map.multiplayer.LinkedWorldOperation linkedWorldOperation;

    /* JADX INFO: renamed from: y */
    final /* synthetic */ CommandController commandController;

    /* JADX INFO: renamed from: e */
    public boolean isQueued = false;

    /* JADX INFO: renamed from: f */
    public boolean isInstantCommand = false;

    /* JADX INFO: renamed from: g */
    public boolean stopCurrentAction = false;

    /* JADX INFO: renamed from: h */
    public boolean isHighPriority = false;

    /* JADX INFO: renamed from: k */
    public ActionId actionId = AbstractUnitAction.NONE_ACTION_ID;

    /* JADX INFO: renamed from: o */
    public boolean clearExistingOrders = false;

    // RWX：这条命令来自 AI 时置 true，移动/进军落点会先校正到单位真能走上去的位置。
    public boolean rwxFromAi = false;
    // RWX：不可达拒令的日志计数（有上限）
    private static int RWX_UNREACHABLE_N = 0;
    // RWX：落点被钳回地图内的次数（日志只打前 8 条）
    private static int RWX_CLAMP_N = 0;
    // RWX：三类拒令总开关；RWX_AI_GUARD=0 时全部退回原版行为
    private static final boolean RWX_ORDER_GUARD = !("0".equals((System.getenv("RWX_AI_GUARD") == null ? "" : System.getenv("RWX_AI_GUARD").trim())));
    private static int RWX_REFUSE_TOTAL = 0;

    private float[] rwxWalkableTarget(float f, float f2) {
        if (!RWX_ORDER_GUARD) {
            return new float[]{f, f2};
        }
        // RWX：老师要求「不能过的地方不能下达命令」——旧版只查目标格「地块不挡」，
        // 不查单位和目标是不是同一个连通组；隔水/隔山的点单位照样接到命令，一路蹭到假终点。
        // 现在判据换成引擎自己的可达性（连通组 ID，与 isPathPossibleBetweenPoints 同源），
        // 沿回头线采样仍不可达就返回 null —— 调用方不建 unitCommand，等于这道命令不发。
        if (this.selectedUnits == null) {
            return new float[]{f, f2};
        }
        OrderableUnit probe = null;
        for (OrderableUnit u : this.selectedUnits) {
            probe = u;
            break;
        }
        if (probe == null) {
            return new float[]{f, f2};
        }
        try {
            GameEngine ge = GameEngine.getInstance();
            com.corrodinggames.rts.game.units.UnitMovementType mt = probe.getMovementType();
            if (mt == com.corrodinggames.rts.game.units.UnitMovementType.AIR
                    || mt == com.corrodinggames.rts.game.units.UnitMovementType.NONE) {
                return new float[]{f, f2};
            }
            short rwxG0 = com.corrodinggames.rts.gameFramework.utility.GameViewUtils.b(probe.posX, probe.posY, mt);
            if (rwxG0 < 0) {
                // 出发点自己都不在可通行层上，没资格拦这道命令，放行
                return new float[]{f, f2};
            }
            for (int s = 0; s <= 12; s++) {
                float t = (s / 12.0f) * 0.5f;
                float px = f + (probe.posX - f) * t;
                float py = f2 + (probe.posY - f2) * t;
                if (com.corrodinggames.rts.gameFramework.utility.GameViewUtils.b(px, py, mt) == rwxG0) {
                    return new float[]{px, py};
                }
            }
            if (this.rwxFromAi) {
                if (RWX_UNREACHABLE_N++ < 20) {
                    io.github.rwx.probe.RwxPerf.out("RWX_AI_UNREACHABLE order refused: cmd=" + this.unitCommand
                        + " type=" + mt + " target=" + (int) f + "," + (int) f2 + " from=" + (int) probe.posX + "," + (int) probe.posY);
                }
                return null;
            }
        } catch (Throwable th) {
        }
        // RWX：玩家命令找不到可走点也照发（落回钳过的目标），只有 AI 才拒令。
        return new float[]{f, f2};
    }

    // RWX：把落点钳进地图世界坐标范围，并向内缩半格。
    // 这就是「单位一路开到地图边缘」的第一道闸：图外目标进寻路前先收回最近的可站立列。
    private static float[] rwxClampTarget(float f, float f2) {
        try {
            GameEngine ge = GameEngine.getInstance();
            if (ge == null || ge.tileMap == null) {
                return new float[]{f, f2};
            }
            com.corrodinggames.rts.game.map.TileMap tm = ge.tileMap;
            if (tm.tileCountX <= 0 || tm.tileCountY <= 0 || tm.tileWorldSizeX <= 0 || tm.tileWorldSizeY <= 0) {
                return new float[]{f, f2};
            }
            float hx = tm.halfTileWorldSizeX;
            float hy = tm.halfTileWorldSizeY;
            float maxX = tm.getWorldWidth() - hx;
            float maxY = tm.getWorldHeight() - hy;
            float cx = f < hx ? hx : (f > maxX ? maxX : f);
            float cy = f2 < hy ? hy : (f2 > maxY ? maxY : f2);
            if (cx != f || cy != f2) {
                if (RWX_CLAMP_N++ < 8) {
                    io.github.rwx.probe.RwxPerf.out("RWX_CLAMP out of map: " + (int) f + "," + (int) f2
                        + " -> " + (int) cx + "," + (int) cy + " map=" + tm.tileCountX + "x" + tm.tileCountY);
                }
            }
            return new float[]{cx, cy};
        } catch (Throwable th) {
            return new float[]{f, f2};
        }
    }

    // RWX：老师要求「避免 AI 向空气下命令」——攻击目标为 null、已死、非敌对、
    // 不可选中（如未进入视野/中立建筑）时，这道攻击令直接拒发。
    private boolean rwxAttackTargetValid(BaseUnit baseUnit) {
        if (!RWX_ORDER_GUARD) {
            return true;
        }
        if (!this.rwxFromAi) {
            return true;
        }
        if (baseUnit == null || baseUnit.isDead || !baseUnit.isAlive || baseUnit.team == null || this.team == null) {
            rwxRefuse("attack-target-null-or-dead");
            return false;
        }
        if (baseUnit.team == this.team || !baseUnit.team.c(this.team)) {
            rwxRefuse("attack-target-not-enemy");
            return false;
        }
        if (this.team instanceof com.corrodinggames.rts.game.ai.AIController
                && !((com.corrodinggames.rts.game.ai.AIController) this.team).isUnitAllowedForSelection(baseUnit)) {
            rwxRefuse("attack-target-unselectable");
            return false;
        }
        return true;
    }

    // RWX：老师要求「不能过的地方不能下达命令」+「建造者不主动跑到敌方密集区」。
    // 建造落点：对首个选中建造者做组可达校验；落点 250px 内存活敌方地面单位
    // 达到 4 个就视为敌方密集区，拒发这道建造令（引擎稍后也会判 invalid placement）。
    private boolean rwxBuildPlacementOk(float f, float f2) {
        if (!RWX_ORDER_GUARD) {
            return true;
        }
        if (!this.rwxFromAi || this.selectedUnits == null || this.selectedUnits.isEmpty()) {
            return true;
        }
        OrderableUnit probe = null;
        for (OrderableUnit u : this.selectedUnits) {
            probe = u;
            break;
        }
        if (probe == null) {
            return true;
        }
        if (rwxWalkableTarget(f, f2) == null) {
            return false;
        }
        try {
            GameEngine ge = GameEngine.getInstance();
            int rwxHostile = 0;
            for (BaseUnit baseUnit : ge.unitSpatialIndex.b(f, f2, 250.0f)) {
                if (baseUnit == null || baseUnit.isDead || !baseUnit.isAlive || baseUnit.team == null) {
                    continue;
                }
                if (baseUnit.team != this.team && baseUnit.team.c(this.team)
                        && baseUnit.getMovementType() != com.corrodinggames.rts.game.units.UnitMovementType.AIR
                        && baseUnit.canAttack()) {
                    rwxHostile++;
                    if (rwxHostile >= 4) {
                        rwxRefuse("build-in-enemy-cluster");
                        return false;
                    }
                }
            }
        } catch (Throwable th) {
        }
        return true;
    }

    // RWX：拒令计数与限流日志
    private static void rwxRefuse(String reason) {
        RWX_REFUSE_TOTAL++;
        if (RWX_REFUSE_TOTAL % 25 == 0) {
            io.github.rwx.probe.RwxPerf.out("RWX_AI_REFUSE total=" + RWX_REFUSE_TOTAL + " last=" + reason);
        }
    }

    /* JADX INFO: renamed from: A */
    private FastArrayList pendingUnitIds = new FastArrayList();

    /* JADX INFO: renamed from: v */
    FastArrayList<OrderableUnit> selectedUnits = new FastArrayList();

    /* JADX INFO: renamed from: w */
    FastArrayList commandTargets = new FastArrayList();

    /* JADX INFO: renamed from: x */
    public boolean hasProcessedTargets = false;

    public Command(CommandController commandController) {
        this.commandController = commandController;
    }

    /* JADX INFO: renamed from: a */
    public boolean hasValidCommandTargetPaths() {
        Iterator it = this.commandTargets.iterator();
        while (it.hasNext()) {
            if (((CommandTarget) it.next()).path.a() == null) {
                return false;
            }
        }
        return true;
    }

    /* JADX INFO: renamed from: b */
    public void precomputeCommandTargets() {
        resolvePendingUnitIds();
        if (hasUnavailableTarget()) return;
        GameEngine gameEngine = GameEngine.getInstance();
        this.hasProcessedTargets = true;
        FormationGroup formationGroupC = gameEngine.formationEngine.c();
        Iterator it = this.selectedUnits.iterator();
        while (it.hasNext()) {
            formationGroupC.units.add((OrderableUnit) it.next());
        }
        if (this.unitCommand != null) {
            float targetX = this.unitCommand.getTargetX();
            float targetY = this.unitCommand.getTargetY();
            if (this.unitCommand.getCommandType() != UnitCommandType.move && this.unitCommand.getCommandType() != UnitCommandType.attackMove && this.unitCommand.getCommandType() != UnitCommandType.attack) {
                return;
            }
            for (OrderableUnit orderableUnit : formationGroupC.a(targetX, targetY, this.unitCommand.isQueued)) {
                if (!orderableUnit.isCurrentTileBlocked() && orderableUnit.I() && (!this.isQueued || orderableUnit.getCurrentWaypoint() == null)) {
                    int pathingTargetRadiusTiles = 0;
                    if (this.unitCommand.getCommandType() == UnitCommandType.attack) {
                        pathingTargetRadiusTiles = orderableUnit.getPathingTargetRadiusTiles(this.unitCommand.getTargetUnit());
                    }
                    CommandTarget commandTarget = new CommandTarget();
                    commandTarget.unitId = orderableUnit.objectId;
                    commandTarget.startX = orderableUnit.posX;
                    commandTarget.startY = orderableUnit.posY;
                    commandTarget.targetX = targetX;
                    commandTarget.targetY = targetY;
                    commandTarget.createdTick = gameEngine.currentTick;
                    commandTarget.movementType = orderableUnit.getMovementType();
                    commandTarget.path = orderableUnit.createPathToTarget(targetX, targetY, pathingTargetRadiusTiles, true, false, false);
                    commandTarget.path.remainingTimeMs = 120.0f;
                    commandTarget.path.allowedDelayMs = commandTarget.path.remainingTimeMs;
                    commandTarget.path.isTimedOut = true;
                    this.commandTargets.add(commandTarget);
                }
            }
        }
    }

    /* JADX INFO: renamed from: c */
    public PlayerTeam getTeam() {
        return this.team;
    }

    /* JADX INFO: renamed from: d */
    public int getAffectedUnitCount() {
        return this.pendingUnitIds.size() + this.selectedUnits.size();
    }

    /* JADX INFO: renamed from: e */
    public boolean isSystemCommand() {
        if (!AbstractUnitAction.isActionIdSpecified(this.actionId) && getAffectedUnitCount() == 0) {
            return true;
        }
        return false;
    }

    /* JADX INFO: renamed from: f */
    public synchronized Command cloneCommand() {
        try {
            GameOutputStream gameOutputStream = new GameOutputStream();
            serializeCommand(gameOutputStream);
            GameInputStream gameInputStream = new GameInputStream(gameOutputStream.toByteArray());
            Command command = new Command(this.commandController);
            command.scheduledTick = this.scheduledTick;
            command.deserializeCommand(gameInputStream);
            return command;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /* JADX INFO: renamed from: g */
    public void prepareForNetworkTransfer() {
        if (this.unitCommand != null) {
            Iterator it = this.selectedUnits.iterator();
            while (it.hasNext()) {
                this.pendingUnitIds.add(Long.valueOf(((OrderableUnit) it.next()).objectId));
            }
            this.selectedUnits.clear();
            this.unitCommand.updateTargetUnitIdFromUnit();
        }
    }

    /* JADX INFO: renamed from: a */
    public synchronized void serializeCommand(GameOutputStream gameOutputStream) throws IOException {
        gameOutputStream.startBlock("c");
        gameOutputStream.writeByte(this.team.teamId);
        gameOutputStream.writeBoolean(this.unitCommand != null);
        if (this.unitCommand != null) {
            this.unitCommand.serialize(gameOutputStream);
        }
        gameOutputStream.writeBoolean(this.isQueued);
        gameOutputStream.writeBoolean(this.stopCurrentAction);
        gameOutputStream.writeInt(-1);
        gameOutputStream.writeEnumOrdinal(this.attackMode);
        gameOutputStream.writeBoolean(this.rallyPoint != null);
        if (this.rallyPoint != null) {
            gameOutputStream.writeFloat(this.rallyPoint.x);
            gameOutputStream.writeFloat(this.rallyPoint.y);
        }
        gameOutputStream.writeBoolean(this.clearExistingOrders);
        gameOutputStream.writeInt(this.selectedUnits.size() + this.pendingUnitIds.size());
        Iterator it = this.selectedUnits.iterator();
        while (it.hasNext()) {
            gameOutputStream.writeLong(((OrderableUnit) it.next()).objectId);
        }
        Iterator it2 = this.pendingUnitIds.iterator();
        while (it2.hasNext()) {
            gameOutputStream.writeLong(((Long) it2.next()).longValue());
        }
        gameOutputStream.writeBoolean(this.sourceTeam != null);
        if (this.sourceTeam != null) {
            gameOutputStream.writeTeamIdByte(this.sourceTeam);
        }
        gameOutputStream.writeBoolean(this.targetPoint != null);
        if (this.targetPoint != null) {
            gameOutputStream.writeFloat(this.targetPoint.x);
            gameOutputStream.writeFloat(this.targetPoint.y);
        }
        gameOutputStream.writeUnitIdOrNullBaseUnit(this.targetUnit);
        gameOutputStream.writeStringUTF(this.actionId.getId());
        gameOutputStream.writeBoolean(this.isInstantCommand);
        gameOutputStream.writeShort(this.allowedTeamMask);
        gameOutputStream.writeBoolean(this.isSystemAction);
        if (this.isSystemAction) {
            gameOutputStream.writeByte(this.linkedWorldOperation == null ? 0 : 1);
            gameOutputStream.writeFloat(this.gameSpeedChange);
            gameOutputStream.writeFloat(this.systemFloat);
            gameOutputStream.writeInt(this.systemActionType);
            if (this.linkedWorldOperation != null)
                gameOutputStream.writeBytesWithLength(this.linkedWorldOperation.encode());
        }
        gameOutputStream.writeInt(this.commandTargets.size());
        for (int i = 0; i < this.commandTargets.size(); i++) {
            ((CommandTarget) this.commandTargets.get(i)).serialize(gameOutputStream);
        }
        gameOutputStream.writeBoolean(this.isHighPriority);
        gameOutputStream.endBlock("c");
    }

    /* JADX INFO: renamed from: a */
    public void deserializeCommand(GameInputStream gameInputStream) throws IOException {
        gameInputStream.startBlockNamed("c");
        this.team = PlayerTeam.k(gameInputStream.readByte());
        if (this.team == null) {
            throw new IOException("team==null");
        }
        if (gameInputStream.readBoolean()) {
            this.unitCommand = new UnitCommand();
            this.unitCommand.deserialize(gameInputStream);
        }
        this.isQueued = gameInputStream.readBoolean();
        this.stopCurrentAction = gameInputStream.readBoolean();
        this.actionId = ActionId.intern(String.valueOf(gameInputStream.readInt()));
        this.attackMode = (AttackMode) gameInputStream.readEnumOrdinalOrNull(AttackMode.class);
        if (gameInputStream.readBoolean()) {
            this.rallyPoint = new PointF();
            this.rallyPoint.x = gameInputStream.readFloat();
            this.rallyPoint.y = gameInputStream.readFloat();
        }
        this.clearExistingOrders = gameInputStream.readBoolean();
        int i = gameInputStream.readInt();
        for (int i2 = 0; i2 < i; i2++) {
            this.pendingUnitIds.add(gameInputStream.readUnitId());
        }
        if (gameInputStream.getProtocolVersion() >= 16) {
            this.sourceTeam = null;
            if (gameInputStream.readBoolean()) {
                this.sourceTeam = gameInputStream.readOptionalPlayerTeam();
            }
        }
        if (gameInputStream.getProtocolVersion() >= 29) {
            if (gameInputStream.readBoolean()) {
                this.targetPoint = new PointF();
                this.targetPoint.x = gameInputStream.readFloat();
                this.targetPoint.y = gameInputStream.readFloat();
            }
            this.targetUnit = gameInputStream.readBaseUnit();
        }
        if (gameInputStream.getProtocolVersion() >= 33) {
            this.actionId = ActionId.intern(gameInputStream.readUTF());
        }
        if (gameInputStream.getProtocolVersion() >= 37) {
            this.isInstantCommand = gameInputStream.readBoolean();
        }
        if (gameInputStream.getProtocolVersion() >= 52) {
            this.allowedTeamMask = gameInputStream.readShortValue();
        }
        if (gameInputStream.getProtocolVersion() >= 53) {
            this.isSystemAction = gameInputStream.readBoolean();
            if (this.isSystemAction) {
                int systemVersion = gameInputStream.readByte();
                if (systemVersion < 0 || systemVersion > 1) throw new IOException("Unsupported system command version");
                this.gameSpeedChange = gameInputStream.readFloat();
                this.systemFloat = gameInputStream.readFloat();
                this.systemActionType = gameInputStream.readInt();
                if (systemVersion == 1) {
                    int length = gameInputStream.readInt();
                    if (length < 1 || length > 1024 * 1024) throw new IOException("Invalid linked command size");
                    byte[] payload = new byte[length];
                    new java.io.DataInputStream(gameInputStream.getActiveInputStream()).readFully(payload);
                    this.linkedWorldOperation = io.github.rwx.map.multiplayer.LinkedWorldOperation.decode(payload);
                }
            }
            int i3 = gameInputStream.readInt();
            this.commandTargets.clear();
            for (int i4 = 0; i4 < i3; i4++) {
                CommandTarget commandTarget = new CommandTarget();
                commandTarget.deserialize(gameInputStream);
                this.commandTargets.add(commandTarget);
            }
        }
        if (gameInputStream.getProtocolVersion() >= 80) {
            this.isHighPriority = gameInputStream.readBoolean();
        }
        gameInputStream.d("c");
    }

    /* JADX INFO: renamed from: a */
    public void addUnitsToCommand(AbstractList abstractList) {
        Iterator it = abstractList.iterator();
        while (it.hasNext()) {
            addUnitToCommand((OrderableUnit) it.next());
        }
    }

    /* JADX INFO: renamed from: a */
    public void addUnitToCommand(OrderableUnit orderableUnit) {
        if (orderableUnit == null) {
            throw new RuntimeException("unit cannot be null");
        }
        if (orderableUnit.team != this.team) {
        }
        if (this.team.isTeamSpectator) {
            if (orderableUnit.team != this.team && GameEngine.getInstance().playerTeam != this.team) {
                GameEngine.log("CommandController", "Warning AI: " + this.team.teamId + " gave an order to unit with team:" + orderableUnit.team.teamId + " type:" + orderableUnit.r().getUnitTypeDescriptionShort());
                GameEngine.logWarningAndStack(VariableScope.nullOrMissingString);
            }
            if (orderableUnit.canNotBeGivenOrdersByPlayer()) {
                GameEngine.log("CommandController", "Warning AI: " + this.team.teamId + " gave an order to unit with canNotBeGivenOrdersByPlayer: " + orderableUnit.r().getUnitTypeDescriptionShort());
            }
        }
        this.selectedUnits.add(orderableUnit);
    }

    /* JADX INFO: renamed from: h */
    public void setClearExistingOrders() {
        this.clearExistingOrders = true;
    }

    /* JADX INFO: renamed from: a */
    public void setMoveTarget(float f, float f2) {
        // RWX：落点先钳进地图（半格内缩），再校正到最近的可站立点。
        // 只有 AI 的不可达命令会拿到 null；玩家命令一律照发，绝不因为落点不好而吞掉。
        float[] rwxC = rwxClampTarget(f, f2);
        f = rwxC[0];
        f2 = rwxC[1];
        float[] rwxP = rwxWalkableTarget(f, f2);
        if (rwxP == null) {
            // RWX：AI 命令落点不可达 —— 不建 unitCommand，这道命令不下（拒发）
            return;
        }
        f = rwxP[0];
        f2 = rwxP[1];
        this.unitCommand = new UnitCommand();
        this.unitCommand.setMoveTarget(f, f2);
    }

    /* JADX INFO: renamed from: b */
    public void setAttackMoveTarget(float f, float f2) {
        // RWX：落点先钳进地图（半格内缩），再校正到最近的可站立点。
        // 只有 AI 的不可达命令会拿到 null；玩家命令一律照发，绝不因为落点不好而吞掉。
        float[] rwxC = rwxClampTarget(f, f2);
        f = rwxC[0];
        f2 = rwxC[1];
        float[] rwxP = rwxWalkableTarget(f, f2);
        if (rwxP == null) {
            // RWX：AI 命令落点不可达 —— 不建 unitCommand，这道命令不下（拒发）
            return;
        }
        f = rwxP[0];
        f2 = rwxP[1];
        this.unitCommand = new UnitCommand();
        this.unitCommand.setAttackMoveTarget(f, f2);
    }

    /* JADX INFO: renamed from: a */
    public void setAttackTarget(BaseUnit baseUnit) {
        // RWX：向空气下命令的拦截点（单参版）
        if (!rwxAttackTargetValid(baseUnit)) {
            return;
        }
        this.unitCommand = new UnitCommand();
        this.unitCommand.setAttackTarget(baseUnit);
    }

    /* JADX INFO: renamed from: a */
    public void setAttackMoveTarget(float f, float f2, boolean z) {
        // RWX：落点先钳进地图（半格内缩），再校正到最近的可站立点。
        // 只有 AI 的不可达命令会拿到 null；玩家命令一律照发，绝不因为落点不好而吞掉。
        float[] rwxC = rwxClampTarget(f, f2);
        f = rwxC[0];
        f2 = rwxC[1];
        float[] rwxP = rwxWalkableTarget(f, f2);
        if (rwxP == null) {
            // RWX：AI 命令落点不可达 —— 不建 unitCommand，这道命令不下（拒发）
            return;
        }
        f = rwxP[0];
        f2 = rwxP[1];
        this.unitCommand = new UnitCommand();
        this.unitCommand.setAttackMoveTarget(f, f2);
        this.unitCommand.isQueued = z;
    }

    /* JADX INFO: renamed from: a */
    public void setAttackTarget(BaseUnit baseUnit, boolean z) {
        // RWX：向空气下命令的拦截点（带队列版）
        if (!rwxAttackTargetValid(baseUnit)) {
            return;
        }
        this.unitCommand = new UnitCommand();
        this.unitCommand.setAttackTarget(baseUnit);
        this.unitCommand.isQueued = z;
    }

    /* JADX INFO: renamed from: a */
    public void setBuildTarget(float f, float f2, UnitType unitType, int i) {
        // RWX：建造落点校验（可达 + 敌方密集区）
        if (!rwxBuildPlacementOk(f, f2)) {
            return;
        }
        this.unitCommand = new UnitCommand();
        this.unitCommand.setBuildCommand(f, f2, unitType, i);
    }

    /* JADX INFO: renamed from: b */
    public void setRepairTarget(BaseUnit baseUnit) {
        this.unitCommand = new UnitCommand();
        this.unitCommand.setRepairCommand(baseUnit);
    }

    /* JADX INFO: renamed from: c */
    public void setGuardTarget(BaseUnit baseUnit) {
        this.unitCommand = new UnitCommand();
        this.unitCommand.setGuardCommand(baseUnit);
    }

    /* JADX INFO: renamed from: c */
    public void setPatrolTarget(float f, float f2) {
        this.unitCommand = new UnitCommand();
        this.unitCommand.setPatrolPoint(f, f2);
    }

    /* JADX INFO: renamed from: d */
    public void setReclaimTarget(BaseUnit baseUnit) {
        this.unitCommand = new UnitCommand();
        this.unitCommand.setReclaimTargetUnit(baseUnit);
    }

    /* JADX INFO: renamed from: e */
    public void setLoadIntoTarget(BaseUnit baseUnit) {
        this.unitCommand = new UnitCommand();
        this.unitCommand.setLoadIntoTargetUnit(baseUnit);
    }

    /* JADX INFO: renamed from: f */
    public void setLoadUpTarget(BaseUnit baseUnit) {
        this.unitCommand = new UnitCommand();
        this.unitCommand.setLoadUpTargetUnit(baseUnit);
    }

    /* JADX INFO: renamed from: a */
    public void setActionId(ActionId actionId) {
        this.actionId = actionId;
    }

    /* JADX INFO: renamed from: a */
    public void setActionTarget(ActionId actionId, PointF pointF, BaseUnit baseUnit) {
        this.actionId = actionId;
        this.targetPoint = pointF;
        this.targetUnit = baseUnit;
    }

    /* JADX INFO: renamed from: a */
    public void setAttackMode(AttackMode attackMode) {
        this.attackMode = attackMode;
    }

    /* JADX INFO: renamed from: a */
    public void setRallyPoint(PointF pointF) {
        this.rallyPoint = pointF;
    }

    /* JADX INFO: renamed from: i */
    public synchronized void resolvePendingUnitIds() {
        Iterator it = this.pendingUnitIds.iterator();
        while (it.hasNext()) {
            OrderableUnit orderableUnitB = GameObject.b(((Long) it.next()).longValue(), true);
            if (orderableUnitB != null) {
                this.selectedUnits.add(orderableUnitB);
            }
        }
        this.pendingUnitIds.clear();
        Iterator it2 = this.selectedUnits.iterator();
        while (it2.hasNext()) {
            OrderableUnit unit = (OrderableUnit) it2.next();
            if (unit.isDead || unit.isTransferUnavailable()) {
                it2.remove();
            }
        }
    }

    /* JADX INFO: renamed from: j */
    public void applyCommandToUnits() {
        resolvePendingUnitIds();
        if (hasUnavailableTarget()) return;
        if (AbstractUnitAction.isActionIdSpecified(this.actionId)) {
            for (OrderableUnit orderableUnit : this.selectedUnits) {
                orderableUnit.stopMoving(orderableUnit.validateActionId(this.actionId), this.stopCurrentAction);
            }
        }
    }

    private boolean hasUnavailableTarget() {
        if (this.targetUnit != null && this.targetUnit.isTransferUnavailable()) return true;
        if (this.unitCommand != null) {
            this.unitCommand.resolveTargetUnitFromId();
            BaseUnit target = this.unitCommand.getTargetUnit();
            return target != null && target.isTransferUnavailable();
        }
        return false;
    }

    /* RWX: 多单位移动时，按各单位当前相对位置算出一圈偏移，保持队形推进。
       单位挤成一坨（间距 < 1 像素）时退化成方阵排布。 */
    private boolean rwxPrepareFormation() {
        this.rwxFormationActive = false;
        this.rwxFormationIndex = 0;
        if (this.unitCommand == null || this.rwxFromAi) {
            return false;
        }
        UnitCommandType unitCommandType = this.unitCommand.getCommandType();
        if (unitCommandType != UnitCommandType.move && unitCommandType != UnitCommandType.attackMove) {
            return false;
        }
        float f = 0.0f;
        float f2 = 0.0f;
        int i = 0;
        for (OrderableUnit orderableUnit : this.selectedUnits) {
            if (!orderableUnit.isDead && !orderableUnit.isTransportAttached) {
                f += orderableUnit.posX;
                f2 += orderableUnit.posY;
                i++;
            }
        }
        if (i < 2) {
            return false;
        }
        this.rwxFormationCenterX = f / i;
        this.rwxFormationCenterY = f2 / i;
        float fMax = 0.0f;
        for (OrderableUnit orderableUnit : this.selectedUnits) {
            if (!orderableUnit.isDead && !orderableUnit.isTransportAttached) {
                float fDistanceSq = Utility.distanceSq(orderableUnit.posX, orderableUnit.posY, this.rwxFormationCenterX, this.rwxFormationCenterY);
                if (fDistanceSq > fMax) {
                    fMax = fDistanceSq;
                }
            }
        }
        this.rwxFormationSpread = (float) Math.sqrt(fMax);
        // RWX：方阵间距跟着最大半径走 —— 战列舰(radius 42)按 34 排布会立刻互相重叠，
        // 软碰撞再把它们推开，队形就散了。取 max(34, 2*maxRadius+6)。
        float rwxMaxRadius = 0.0f;
        for (OrderableUnit orderableUnit : this.selectedUnits) {
            if (!orderableUnit.isDead && !orderableUnit.isTransportAttached && orderableUnit.radius > rwxMaxRadius) {
                rwxMaxRadius = orderableUnit.radius;
            }
        }
        this.rwxFormationSpacing = Math.max(34.0f, (rwxMaxRadius * 2.0f) + 6.0f);
        this.rwxFormationActive = true;
        return true;
    }

    private void rwxApplyFormationOffset(UnitCommand unitCommand, OrderableUnit orderableUnit) {
        int idx = this.rwxFormationIndex;
        this.rwxFormationIndex = idx + 1;
        int n = this.selectedUnits.size();
        int cols = Math.max(1, (int) Math.ceil(Math.sqrt(n)));
        int rows = Math.max(1, (n + cols - 1) / cols);
        int row = idx / cols;
        int col = idx % cols;
        float spacing = this.rwxFormationSpacing;
        float targetX = unitCommand.getTargetX();
        float targetY = unitCommand.getTargetY();
        float dx = targetX - this.rwxFormationCenterX;
        float dy = targetY - this.rwxFormationCenterY;
        float len = (float) Math.sqrt((dx * dx) + (dy * dy));
        float ux = 1.0f;
        float uy = 0.0f;
        if (len > 1.0f) {
            ux = dx / len;
            uy = dy / len;
        }
        float sideX = -uy;
        float sideY = ux;
        float forward = (row - ((rows - 1) * 0.5f)) * spacing;
        float side = (col - ((cols - 1) * 0.5f)) * spacing;
        unitCommand.rwxSetFormationOffset((targetX + (sideX * side)) + (ux * forward), (targetY + (sideY * side)) + (uy * forward));
    }

    /* ================= RWX：路径点先行寻路 =================
       原版一次长距离 A* 只有几百毫秒预算，大图绕远路经常算不完，
       单位就退化成「朝最近可达点走」，看起来卡在岸边不动。
       这里改成：先用视线贪心在可通行格子上探出一条走廊，
       把走廊拐点作为一串短路径点先排进队列，最后才是真实目标。
       每段都很短，A* 预算绰绰有余。 */

    private static final boolean RWX_RP_ENABLED = System.getenv("RWX_RP") == null || !"0".equals(System.getenv("RWX_RP").trim());
    // RWX：默认开（老师常用 RWX.exe 直启，没有环境变量）；RWX_RP_DEBUG=0 关闭
    private static final boolean RWX_RP_DEBUG = !("0".equals((System.getenv("RWX_RP_DEBUG") == null ? "" : System.getenv("RWX_RP_DEBUG").trim())));
    private static final int RWX_RP_MAX_WAYPOINTS = 16;
    // RWX：走廊探测跑在命令派发线程上，必须给它硬上限，宁可退回原行为也不能卡帧。
    private static final int RWX_RP_NODE_BUDGET = 60000;
    private static final long RWX_RP_TIME_MS = 16;
    // RWX：单位足迹膨胀系数（相对 radius），越大越远离障碍/越不容易蹭撞；1.0=旧版 0.8
    private static final float RWX_FOOTPRINT_SCALE = 1.15f;
    // RWX：AI 走廊调试行的总条数上限（防止日志爆炸）
    private static final int RWX_RP_DBG_AI_BUDGET = 200;
    private static int RWX_RP_DIAG = 0;
    private static final int budgetIdx=0, gateIdx=1, cmdTypeIdx=2, noLeadIdx=3, moveIdx=4, sameTileIdx=5, snapIdx=6, footprintIdx=7, res0aIdx=8, res0bIdx=9, narrow0Idx=10;
    private static int RWX_RP_DBG_AI_COUNT = 0;
    // RWX：走廊现在对 AI 也启用。AI 一回合可能下几十上百条命令，若每条都跑 16ms 会直接卡帧，
    // 所以给「单个 tick 内 AI 走廊总耗时」一个总预算；用完就退回引擎原路径（宁可慢一点也不卡）。
    private static final long RWX_AI_CORRIDOR_TICK_BUDGET_MS = 4;
    private static int rwxAiCorridorTick = -1;
    private static long rwxAiCorridorUsedNs = 0L;

    private boolean rwxTileFree(com.corrodinggames.rts.game.units.UnitMovementType unitMovementType, int i, int i2) {
        return !GameEngine.getInstance().pathfindingEngine.isTileBlockedForMovement(unitMovementType, i, i2);
    }

    /* RWX：按单位半径膨胀后的可通行判定。船的中心停在这一格时，
       周围 rTiles 圈格子必须全都通航，否则大船会蹭到岸。结果带缓存，
       同一次走廊探测里每格只算一次。 */
    /* RWX：按单位实际足迹判定。船/车中心在格心，圆半径 rpx（世界像素）；
       任一格障碍到中心的距离 ≤ rpx 就不放行。旧版按 ceil 成整格圈膨胀：
       12px 半径的坦克要 3×3=60px 净空、25px 的船要 5×5=100px 净空，
       比真实足迹保守好几倍，短通道整条被否掉——这就是绕远路的主因。 */
    private boolean rwxTileFits(com.corrodinggames.rts.game.units.UnitMovementType unitMovementType, int i, int i2) {
        float rpx = this.rwxTFreePx;
        if (rpx <= 0.5f) {
            return rwxTileFree(unitMovementType, i, i2);
        }
        TileMap rwxMap = GameEngine.getInstance().tileMap;
        int rwxH = rwxMap.groundLayer.heightTiles;
        int idx2 = (i * rwxH) + i2;
        int rwxN = this.rwxTFreeStamp == null ? -1 : this.rwxTFreeStamp.length;
        if (idx2 >= 0 && idx2 < rwxN && this.rwxTFreeStamp[idx2] == this.rwxTFreeGen) {
            return this.rwxTFreeVal[idx2] == 1;
        }
        int tile = Math.max(1, rwxMap.tileWorldSizeX);
        boolean fits = rwxTileFree(unitMovementType, i, i2);
        if (fits) {
            int ring = (int) Math.ceil(rpx / (float) tile) + 1;
            if (ring > 3) {
                ring = 3;
            }
            float r2 = rpx * rpx;
            for (int dx = -ring; dx <= ring && fits; dx++) {
                for (int dy = -ring; dy <= ring; dy++) {
                    if (dx == 0 && dy == 0) {
                        continue;
                    }
                    if (rwxTileFree(unitMovementType, i + dx, i2 + dy)) {
                        continue;
                    }
                    // 障碍方格到中心的间隙：max(0,|偏移|-0.5)*格宽，中心在格心
                    float gx = ((float) Math.abs(dx) - 0.5f) * tile;
                    float gy = ((float) Math.abs(dy) - 0.5f) * tile;
                    if (gx < 0.0f) {
                        gx = 0.0f;
                    }
                    if (gy < 0.0f) {
                        gy = 0.0f;
                    }
                    if ((gx * gx) + (gy * gy) < r2) {
                        fits = false;
                        break;
                    }
                }
            }
        }
        if (idx2 >= 0 && idx2 < rwxN) {
            this.rwxTFreeStamp[idx2] = this.rwxTFreeGen;
            this.rwxTFreeVal[idx2] = fits ? (byte) 1 : (byte) 0;
        }
        return fits;
    }

    /* RWX：终点吸附 —— 老师的要求：路径终点必须落在「离目的地最近的可通过地形」上，且吸附距离有限。
       做法：以目标格为圆心逐圈外扩（切比雪夫环），取第一个放得下单位足迹的格子；
       超过 RWX_RP_SNAP_TILES 仍找不到就放弃走廊，交回引擎原逻辑。
       旧版是「目标格不通航就直接放弃走廊」，靠岸/靠墙点往往一格之差导致整条走廊失效，
       单位于是贴着引擎默认路径蹭过去——绕远路与蹭岸都来自这里。 */
    // RWX：老师指正区间：一格约 20 世界像素，吸附半径取 500px = 25 格（老师说 420~500 的范围）
    private static final int RWX_RP_SNAP_TILES = 25;

    /** 终点吸附：从目标格逐圈外扩，第一个放得下足迹的格即终点。成功返回 true 并写入 out[0..1] */
    private boolean rwxSnapEndTile(UnitMovementType rwxMoveType, int rwxEX, int rwxEY, int[] out) {
        TileMap rwxMap = GameEngine.getInstance().tileMap;
        int rwxW = rwxMap.groundLayer.widthTiles;
        int rwxH = rwxMap.groundLayer.heightTiles;
        if (rwxEX >= 0 && rwxEY >= 0 && rwxEX < rwxW && rwxEY < rwxH && rwxTileFits(rwxMoveType, rwxEX, rwxEY)) {
            out[0] = rwxEX;
            out[1] = rwxEY;
            return true;
        }
        for (int ring = 1; ring <= RWX_RP_SNAP_TILES; ring++) {
            for (int d = -ring; d <= ring; d++) {
                if (trySnap(rwxMoveType, rwxEX + d, rwxEY - ring, rwxW, rwxH, out)
                        || trySnap(rwxMoveType, rwxEX + d, rwxEY + ring, rwxW, rwxH, out)) {
                    return true;
                }
            }
            for (int d = -ring + 1; d <= ring - 1; d++) {
                if (trySnap(rwxMoveType, rwxEX - ring, rwxEY + d, rwxW, rwxH, out)
                        || trySnap(rwxMoveType, rwxEX + ring, rwxEY + d, rwxW, rwxH, out)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean trySnap(UnitMovementType rwxMoveType, int x, int y, int w, int h, int[] out) {
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return false;
        }
        if (!rwxTileFits(rwxMoveType, x, y)) {
            return false;
        }
        out[0] = x;
        out[1] = y;
        return true;
    }

    /* Bresenham 直线：任一格不可通行就返回 false */
    private boolean rwxLineClear(com.corrodinggames.rts.game.units.UnitMovementType unitMovementType, int i, int i2, int i3, int i4) {
        // RWX：重写为标准 Bresenham。旧版 steps=1+dx+dy 且对角多减一次，对角线段会提前退出：
        // 端点与最后几格根本没检查就报「通视」，拉直据此删拐点，单位实际走直线撞障碍只能绕路。
        int rwxSxDx = Utility.abs(i3 - i);
        int rwxSxDy = Utility.abs(i4 - i2);
        int rwxSx = i3 > i ? 1 : -1;
        int rwxSy = i4 > i2 ? 1 : -1;
        int rwxErr = rwxSxDx - rwxSxDy;
        int x = i;
        int y = i2;
        for (; ; ) {
            if (!rwxTileFits(unitMovementType, x, y)) {
                return false;
            }
            if (x == i3 && y == i4) {
                return true;
            }
            int e2 = rwxErr * 2;
            if (e2 > -rwxSxDy) {
                rwxErr -= rwxSxDy;
                x += rwxSx;
            }
            if (e2 < rwxSxDx) {
                rwxErr += rwxSxDx;
                y += rwxSy;
            }
        }
    }

    /* 先寻路（带节点预算的 A*），再把格子路径拉直成 <=10 个拐点。
       拐点保证两两通视，每段都是短距离，引擎 A* 预算必然够。 */
    /* 返回：>0 拐点个数；0 不需要走廊；-1 这个体积档位下无路可走 */
    private int rwxBuildCorridor(UnitMovementType unitMovementType, int i, int i2, int i3, int i4, int rwxR) {
        if (this.rwxCorridorX == null) {
            this.rwxCorridorX = new int[RWX_RP_MAX_WAYPOINTS];
            this.rwxCorridorY = new int[RWX_RP_MAX_WAYPOINTS];
        }
        this.rwxCorridorCount = 0;
        // RWX：rwxR = 足迹半径（世界像素，<=0 表示引擎原始判定）
        this.rwxTFreeR = rwxR;
        this.rwxTFreePx = (float) rwxR;
        this.rwxTFreeGen++;
        if (i == i3 && i2 == i4) {
            return 0;
        }
        if (rwxLineClear(unitMovementType, i, i2, i3, i4)) {
            return 0;
        }
        TileMap rwxMap = GameEngine.getInstance().tileMap;
        int rwxW = rwxMap.groundLayer.widthTiles;
        int rwxH = rwxMap.groundLayer.heightTiles;
        int rwxN = rwxW * rwxH;
        if (rwxN <= 0 || rwxN > 4000000) {
            return -1;
        }
        if (this.rwxPathG == null || this.rwxPathG.length != rwxN) {
            this.rwxPathG = new int[rwxN];
            this.rwxPathFrom = new int[rwxN];
            this.rwxPathStamp = new int[rwxN];
        }
        int rwxGen = this.rwxPathGen + 1;
        this.rwxPathGen = rwxGen;
        int rwxStartIdx = (i * rwxH) + i2;
        int rwxEndIdx = (i3 * rwxH) + i4;
        this.rwxPathStamp[rwxStartIdx] = rwxGen;
        this.rwxPathG[rwxStartIdx] = 0;
        this.rwxPathFrom[rwxStartIdx] = -1;
        java.util.PriorityQueue<long[]> rwxOpen = new java.util.PriorityQueue<>((a, b) -> Long.compare(a[1], b[1]));
        rwxOpen.add(new long[]{rwxStartIdx, rwxHeuristic(rwxStartIdx, rwxH, i3, i4)});
        int rwxNodes = 0;
        boolean rwxReached = false;
        long rwxDeadline = System.nanoTime() + (RWX_RP_TIME_MS * 1000000L);
        boolean rwxTimeout = false;
        while (!rwxOpen.isEmpty()) {
            long[] rwxTop = rwxOpen.poll();
            int rwxCur = (int) rwxTop[0];
            if (this.rwxPathStamp[rwxCur] != rwxGen) {
                continue;
            }
            int rwxG = this.rwxPathG[rwxCur];
            if (rwxTop[1] < rwxG + rwxHeuristic(rwxCur, rwxH, i3, i4)) {
                continue;
            }
            rwxNodes++;
            if (rwxNodes > RWX_RP_NODE_BUDGET || System.nanoTime() > rwxDeadline) {
                rwxTimeout = true;
                break;
            }
            if (rwxCur == rwxEndIdx) {
                rwxReached = true;
                break;
            }
            int rwxCx = rwxCur / rwxH;
            int rwxCy = rwxCur % rwxH;
            for (int rwxD = 0; rwxD < 8; rwxD++) {
                int rwxNx = rwxCx + RWX_DX[rwxD];
                int rwxNy = rwxCy + RWX_DY[rwxD];
                if (rwxNx < 0 || rwxNy < 0 || rwxNx >= rwxW || rwxNy >= rwxH) {
                    continue;
                }
                if (!rwxTileFits(unitMovementType, rwxNx, rwxNy)) {
                    continue;
                }
                if (rwxD >= 4 && (!rwxTileFits(unitMovementType, rwxNx, rwxCy) || !rwxTileFits(unitMovementType, rwxCx, rwxNy))) {
                    continue;
                }
                int rwxNIdx = (rwxNx * rwxH) + rwxNy;
                int rwxNg = rwxG + (rwxD >= 4 ? 14 : 10);
                if (this.rwxPathStamp[rwxNIdx] == rwxGen && this.rwxPathG[rwxNIdx] <= rwxNg) {
                    continue;
                }
                this.rwxPathStamp[rwxNIdx] = rwxGen;
                this.rwxPathG[rwxNIdx] = rwxNg;
                this.rwxPathFrom[rwxNIdx] = rwxCur;
                rwxOpen.add(new long[]{rwxNIdx, rwxNg + rwxHeuristic(rwxNIdx, rwxH, i3, i4)});
            }
        }
        if (!rwxReached) {
            // 超时或无路：都返回 -1 让上层换档；最后一档还不行就退回原行为
            return -1;
        }
        int[] rwxCells = new int[RWX_RP_NODE_BUDGET];
        int rwxCellCount = 0;
        for (int rwxK = rwxEndIdx; rwxK >= 0 && rwxCellCount < rwxCells.length; rwxK = this.rwxPathFrom[rwxK]) {
            rwxCells[rwxCellCount++] = rwxK;
        }
        for (int rwxA = 0; rwxA < rwxCellCount / 2; rwxA++) {
            int rwxT = rwxCells[rwxA];
            rwxCells[rwxA] = rwxCells[rwxCellCount - 1 - rwxA];
            rwxCells[rwxCellCount - 1 - rwxA] = rwxT;
        }
        // 拉直：锚点尽量向前延伸，第一个不通视的位置之前那格才是拐点
        int rwxAnchor = 0;
        int rwxProbe = 1;
        while (rwxProbe < rwxCellCount) {
            int rwxAX = rwxCells[rwxAnchor] / rwxH;
            int rwxAY = rwxCells[rwxAnchor] % rwxH;
            int rwxBX = rwxCells[rwxProbe] / rwxH;
            int rwxBY = rwxCells[rwxProbe] % rwxH;
            if (!rwxLineClear(unitMovementType, rwxAX, rwxAY, rwxBX, rwxBY)) {
                int rwxAdd = rwxProbe - 1 != rwxAnchor ? rwxProbe - 1 : rwxProbe;
                if (this.rwxCorridorCount >= RWX_RP_MAX_WAYPOINTS) {
                    break;
                }
                this.rwxCorridorX[this.rwxCorridorCount] = rwxCells[rwxAdd] / rwxH;
                this.rwxCorridorY[this.rwxCorridorCount] = rwxCells[rwxAdd] % rwxH;
                this.rwxCorridorCount++;
                rwxAnchor = rwxAdd;
            }
            rwxProbe++;
        }
        rwxSmoothCorridor(unitMovementType, i, i2, i3, i4);
        // RWX：折线链长（格数），给宽/窄档比较用
        float rwxLen = 0.0f;
        int rwxPX = i;
        int rwxPY = i2;
        for (int rwxK = 0; rwxK < this.rwxCorridorCount; rwxK++) {
            float rwxDX = (float) (this.rwxCorridorX[rwxK] - rwxPX);
            float rwxDY = (float) (this.rwxCorridorY[rwxK] - rwxPY);
            rwxLen += (float) Math.sqrt((rwxDX * rwxDX) + (rwxDY * rwxDY));
            rwxPX = this.rwxCorridorX[rwxK];
            rwxPY = this.rwxCorridorY[rwxK];
        }
        float rwxEX = (float) (i3 - rwxPX);
        float rwxEY = (float) (i4 - rwxPY);
        rwxCorridorLen = rwxLen + (float) Math.sqrt((rwxEX * rwxEX) + (rwxEY * rwxEY));
        return this.rwxCorridorCount;
    }

    /* RWX：拐点平滑 —— 大船转弯半径大，拐点太密或转角太小会 overshoot 再折回来，
       看起来就是频繁摆头。转角 < 30 度或前段 < 5 格的拐点，只要前后两格通视就删掉。 */
    private void rwxSmoothCorridor(UnitMovementType unitMovementType, int i, int i2, int i3, int i4) {
        if (this.rwxCorridorCount == 0) {
            return;
        }
        int n = this.rwxCorridorCount + 2;
        int[] xs = new int[n];
        int[] ys = new int[n];
        xs[0] = i;
        ys[0] = i2;
        for (int k = 0; k < this.rwxCorridorCount; k++) {
            xs[k + 1] = this.rwxCorridorX[k];
            ys[k + 1] = this.rwxCorridorY[k];
        }
        xs[n - 1] = i3;
        ys[n - 1] = i4;
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int k = 1; k < n - 1; k++) {
                int ax = xs[k - 1];
                int ay = ys[k - 1];
                int bx = xs[k];
                int by = ys[k];
                int cx = xs[k + 1];
                int cy = ys[k + 1];
                float v1x = bx - ax;
                float v1y = by - ay;
                float v2x = cx - bx;
                float v2y = cy - by;
                float l1 = (float) Math.sqrt((v1x * v1x) + (v1y * v1y));
                float l2 = (float) Math.sqrt((v2x * v2x) + (v2y * v2y));
                if (l1 < 0.01f || l2 < 0.01f) {
                    n = rwxRemoveAt(xs, ys, n, k);
                    changed = true;
                    break;
                }
                float cos = (v1x * v2x + v1y * v2y) / (l1 * l2);
                if (cos > 1.0f) {
                    cos = 1.0f;
                } else if (cos < -1.0f) {
                    cos = -1.0f;
                }
                float angle = (float) Math.toDegrees(Math.acos(cos));
                if ((angle < 30.0f || l1 < 5.0f) && rwxLineClear(unitMovementType, ax, ay, cx, cy)) {
                    n = rwxRemoveAt(xs, ys, n, k);
                    changed = true;
                    break;
                }
            }
        }
        this.rwxCorridorCount = n - 2;
        for (int k = 0; k < this.rwxCorridorCount; k++) {
            this.rwxCorridorX[k] = xs[k + 1];
            this.rwxCorridorY[k] = ys[k + 1];
        }
    }

    private static int rwxRemoveAt(int[] xs, int[] ys, int n, int k) {
        for (int j = k; j < n - 1; j++) {
            xs[j] = xs[j + 1];
            ys[j] = ys[j + 1];
        }
        return n - 1;
    }

    private static final int[] RWX_DX = {1, -1, 0, 0, 1, 1, -1, -1};
    private static final int[] RWX_DY = {0, 0, 1, -1, 1, -1, 1, -1};

    private static int rwxHeuristic(int idx, int h, int ex, int ey) {
        int x = idx / h;
        int y = idx % h;
        int dx = x - ex;
        if (dx < 0) {
            dx = -dx;
        }
        int dy = y - ey;
        if (dy < 0) {
            dy = -dy;
        }
        // RWX：octile —— 步成本是 10（直行）/14（斜行），一致的估价 = 10*max + 4*min。
        // 旧的曼哈顿×10 对斜线高估（每步多算 6），A* 不再保证走廊是最短链，偶尔绕远。
        int hi = dx > dy ? dx : dy;
        int lo = dx > dy ? dy : dx;
        return (hi * 10) + (lo * 4);
    }

    /* 每条命令探一次走廊：从队形中心（或首个单位）到目标格 */
    private boolean rwxPrepareCorridor() {
        // RWX：AI 命令走总预算闸门，避免一回合几十条命令各跑 16ms 把帧打穿。
        if (this.rwxFromAi) {
            int rwxTick = com.corrodinggames.rts.gameFramework.GameEngine.getInstance().currentTick;
            if (rwxTick != rwxAiCorridorTick) {
                rwxAiCorridorTick = rwxTick;
                rwxAiCorridorUsedNs = 0L;
            }
            if (rwxAiCorridorUsedNs > RWX_AI_CORRIDOR_TICK_BUDGET_MS * 1000000L) {
                return false;
            }
        }
        this.rwxCorridorCount = 0;
        // RWX：每条命令重算前清零，否则上一条的集结点会漏到这一条
        this.rwxStagingReady = false;
        // RWX：老师要求「寻路始终启用」——AI 命令也走走廊，不再按 rwxFromAi 跳过。
        if (!RWX_RP_ENABLED || this.unitCommand == null || this.selectedUnits == null) {
            return false;
        }
        UnitCommandType unitCommandType = this.unitCommand.getCommandType();
        if (unitCommandType != UnitCommandType.move && unitCommandType != UnitCommandType.attackMove) {
            return false;
        }
        OrderableUnit rwxLead = null;
        for (OrderableUnit orderableUnit : this.selectedUnits) {
            if (!orderableUnit.isDead && !orderableUnit.isTransportAttached) {
                rwxLead = orderableUnit;
                break;
            }
        }
        if (rwxLead == null) {
            return false;
        }
        GameEngine rwxEngine = GameEngine.getInstance();
        TileMap rwxTileMap = rwxEngine.tileMap;
        float rwxFromX = rwxFormationActive ? this.rwxFormationCenterX : rwxLead.posX;
        float rwxFromY = rwxFormationActive ? this.rwxFormationCenterY : rwxLead.posY;
        com.corrodinggames.rts.game.units.UnitMovementType rwxMoveType = rwxLead.getMovementType();
        if (rwxMoveType == com.corrodinggames.rts.game.units.UnitMovementType.AIR
                || rwxMoveType == com.corrodinggames.rts.game.units.UnitMovementType.NONE) {
            return false;
        }
        int rwxTileSize = Math.max(1, rwxTileMap.tileWorldSizeX);
        // RWX：老师要求「寻路过程中避免碰撞、离不可通行地形远一些」。
        // 原来 0.8 倍半径等于允许船头船尾蹭着岸走；这里提到 1.15 倍，并要求整圈通航。
        float rwxRTiles = rwxLead.radius * RWX_FOOTPRINT_SCALE;
        float rwxRMin = rwxTileSize * 0.75f;
        if (rwxRTiles < rwxRMin) { rwxRTiles = rwxRMin; }
        float rwxRCap = rwxTileSize * 3.0f;
        if (rwxRTiles > rwxRCap) {
            rwxRTiles = rwxRCap;
        }
        int rwxMapN = rwxTileMap.groundLayer.widthTiles * rwxTileMap.groundLayer.heightTiles;
        if (rwxMapN > 0 && rwxMapN <= 4000000 && (this.rwxTFreeStamp == null || this.rwxTFreeStamp.length != rwxMapN)) {
            this.rwxTFreeStamp = new int[rwxMapN];
            this.rwxTFreeVal = new byte[rwxMapN];
            java.util.Arrays.fill(this.rwxTFreeStamp, -1);
        }
        rwxTileMap.setCursorTileIndexFromWorldPoint(rwxFromX, rwxFromY);
        int rwxStartX = rwxTileMap.cursorTileX;
        int rwxStartY = rwxTileMap.cursorTileY;
        rwxTileMap.setCursorTileIndexFromWorldPoint(this.unitCommand.getTargetX(), this.unitCommand.getTargetY());
        int rwxEndX = rwxTileMap.cursorTileX;
        int rwxEndY = rwxTileMap.cursorTileY;
        if (rwxStartX == rwxEndX && rwxStartY == rwxEndY) {
            return false;
        }
        this.rwxTFreeR = 0;
        this.rwxTFreePx = rwxRTiles;
        this.rwxTFreeGen++;
        // RWX：终点吸附 —— 目标格放不下单位足迹时，找离它最近的可通行格（限 RWX_RP_SNAP_TILES），
        // 走廊终点就落在吸附点上；吸附不到才交回引擎原逻辑。
        int[] rwxEndOut = new int[2];
        if (!rwxSnapEndTile(rwxMoveType, rwxEndX, rwxEndY, rwxEndOut)) {
            return false;
        }
        rwxEndX = rwxEndOut[0];
        rwxEndY = rwxEndOut[1];
        if (rwxRTiles > 0 && !rwxTileFits(rwxMoveType, rwxStartX, rwxStartY)) {
            // 出发点挤在障碍里：足迹放不下就交回引擎（单位会先脱困再走）
            return false;
        }
        // RWX：分层回退 —— 先按体积找宽路；窄水道放不下就退回引擎原始判定（R=0），
        // 保证走廊结果不会比原版更差，只是可能蹭岸。
        // RWX：AI 走廊总预算计时起点
        long rwxCorridorT0 = this.rwxFromAi ? System.nanoTime() : 0L;
        int rwxRes = rwxBuildCorridor(rwxMoveType, rwxStartX, rwxStartY, rwxEndX, rwxEndY, (int) Math.ceil(rwxRTiles));
        if (this.rwxFromAi) { rwxAiCorridorUsedNs += System.nanoTime() - rwxCorridorT0; rwxCorridorT0 = System.nanoTime(); }
        if (rwxRes == 0) {
            this.rwxStagingReady = false;
            return false;
        }
        if (rwxRes < 0 && rwxRTiles > 0) {
            rwxRes = rwxBuildCorridor(rwxMoveType, rwxStartX, rwxStartY, rwxEndX, rwxEndY, 0);
            if (this.rwxFromAi) { rwxAiCorridorUsedNs += System.nanoTime() - rwxCorridorT0; rwxCorridorT0 = System.nanoTime(); }
            if (rwxRes == 0) {
                this.rwxStagingReady = false;
                return false;
            }
        } else if (rwxRes > 0 && rwxRTiles > 0 && rwxCorridorLen > 10.0f) {
            // 链很短（<=10 格）不值得再搜一次，省掉常见短命令的双份预算
            // RWX：宽档绕远检查（图一另一半成因）—— 宽档宁可绕一大圈也不穿窄缝，
            // 窄档（引擎同判定）若能直连就干脆不要走廊，明显更短就用窄链。
            int[] rwxSaveX = java.util.Arrays.copyOf(this.rwxCorridorX, RWX_RP_MAX_WAYPOINTS);
            int[] rwxSaveY = java.util.Arrays.copyOf(this.rwxCorridorY, RWX_RP_MAX_WAYPOINTS);
            int rwxSaveCount = this.rwxCorridorCount;
            float rwxWideLen = rwxCorridorLen;
            int rwxNarrow = rwxBuildCorridor(rwxMoveType, rwxStartX, rwxStartY, rwxEndX, rwxEndY, 0);
            if (this.rwxFromAi) { rwxAiCorridorUsedNs += System.nanoTime() - rwxCorridorT0; rwxCorridorT0 = System.nanoTime(); }
            if (rwxNarrow == 0) {
                this.rwxStagingReady = false;
                return false;
            }
            // RWX：窄档 -1（超时/无路）也留宽档；>0 且明显更短才换窄链
            if (rwxNarrow > 0 && (rwxCorridorLen * 1.3f) < rwxWideLen) {
                rwxRes = rwxNarrow;
            } else {
                System.arraycopy(rwxSaveX, 0, this.rwxCorridorX, 0, rwxSaveCount);
                System.arraycopy(rwxSaveY, 0, this.rwxCorridorY, 0, rwxSaveCount);
                this.rwxCorridorCount = rwxSaveCount;
                rwxCorridorLen = rwxWideLen;
            }
        }
        rwxPlanStaging(rwxMoveType, rwxTileMap, rwxFromX, rwxFromY, rwxStartX, rwxStartY, rwxEndX, rwxEndY);
        if (rwxRes > 0) {
            rwxCorridorType = rwxMoveType;
            rwxCorridorEndTX = rwxEndX;
            rwxCorridorEndTY = rwxEndY;
        }
        // RWX：走廊调试（RWX_RP_DEBUG=1）：起终点、档位、链长、拐点表
        // RWX：AI 也走走廊后，调试行会暴涨（每条 AI 命令一行）。给 AI 限个流，玩家命令照旧。
        if (RWX_RP_DEBUG && rwxRes > 0 && (!this.rwxFromAi || RWX_RP_DBG_AI_COUNT < RWX_RP_DBG_AI_BUDGET)) {
            if (this.rwxFromAi) { RWX_RP_DBG_AI_COUNT++; }
            StringBuilder rwxDbg = new StringBuilder("RWX_RP corridor ").append(rwxStartX).append(",").append(rwxStartY)
                    .append(" -> ").append(rwxEndX).append(",").append(rwxEndY)
                    .append(" r=").append((int) rwxRTiles).append("px n=").append(this.rwxCorridorCount)
                    .append(" len=").append((int) rwxCorridorLen).append(" tiles [");
            for (int rwxK = 0; rwxK < this.rwxCorridorCount; rwxK++) {
                rwxDbg.append(this.rwxCorridorX[rwxK]).append(':').append(this.rwxCorridorY[rwxK]).append(' ');
            }
            rwxDbg.append(']');
            io.github.rwx.probe.RwxPerf.out(rwxDbg.toString());
        }
        return rwxRes > 0;
    }

    /* RWX：目的地附近集结 —— 老师指正：真要集结也该在距离目的地不远处收拢，
       不是在出发点绕圈。条件：阵形激活、队形比理想展开散得多、目标够远、
       集结点按体积放得下且到目标视线通畅；否则不集结，直接走目标。 */
    private void rwxPlanStaging(com.corrodinggames.rts.game.units.UnitMovementType rwxMoveType, TileMap rwxTileMap,
                                float rwxFromX, float rwxFromY, int rwxStartX, int rwxStartY, int rwxEndX, int rwxEndY) {
        this.rwxStagingReady = false;
        if (!rwxFormationActive) {
            return;
        }
        float spread = this.rwxFormationSpread;
        float spacing = this.rwxFormationSpacing;
        if (spread <= spacing * 2.2f) {
            // 队形本身不算散，不值得多一个路径点
            return;
        }
        float tvx = this.unitCommand.getTargetX() - rwxFromX;
        float tvy = this.unitCommand.getTargetY() - rwxFromY;
        float tlen = (float) Math.sqrt((tvx * tvx) + (tvy * tvy));
        if (tlen <= spread) {
            // 目标就在队形边缘，直接展开
            return;
        }
        float back = spread * 0.5f;
        if (back < spacing * 2.0f) {
            back = spacing * 2.0f;
        }
        if (back > 260.0f) {
            back = 260.0f;
        }
        if (back >= tlen * 0.7f) {
            // 收拢点会退到半路而不是目的地附近，放弃集结
            return;
        }
        float stgX = this.unitCommand.getTargetX() - ((tvx / tlen) * back);
        float stgY = this.unitCommand.getTargetY() - ((tvy / tlen) * back);
        rwxTileMap.setCursorTileIndexFromWorldPoint(stgX, stgY);
        int stgTX = rwxTileMap.cursorTileX;
        int stgTY = rwxTileMap.cursorTileY;
        if (stgTX == rwxStartX && stgTY == rwxStartY) {
            return;
        }
        // 集结点按当前档位体积放得下（rwxTFreeR 是走廊成功时用的档位）
        if (!rwxTileFits(rwxMoveType, stgTX, stgTY)) {
            return;
        }
        // 收拢完到展开位必须视线通畅，否则等于又塞一次寻路
        if (!rwxLineClear(rwxMoveType, stgTX, stgTY, rwxEndX, rwxEndY)) {
            return;
        }
        this.rwxStagingX = rwxTileMap.cursorTileX + rwxTileMap.halfTileWorldSizeX;
        this.rwxStagingY = rwxTileMap.cursorTileY + rwxTileMap.halfTileWorldSizeY;
        this.rwxStagingReady = true;
    }

    private void rwxApplyRegroupOffset(UnitCommand unitCommand) {
        int idx = this.rwxFormationIndex;
        int n = Math.max(1, this.selectedUnits.size());
        float angle = (6.2831855f * idx) / n;
        // RWX：收拢环也跟着单位体积走，半径 42 的战列舰挤在 40 的环上等于没集结
        float spacing = this.rwxFormationSpacing;
        float radius = spacing * 0.6f + ((idx % 3) * spacing * 0.45f);
        unitCommand.rwxSetFormationOffset(this.rwxStagingX + (Utility.fastCos(angle) * radius), this.rwxStagingY + (Utility.fastSin(angle) * radius));
    }

    /* JADX INFO: renamed from: k */
    public void executeCommand() {
        UnitCommand lastWaypoint;
        String str;
        GameEngine gameEngine = GameEngine.getInstance();
        if (gameEngine.replayEngine.j() && !this.isReplayCommand) {
            return;
        }
        if (gameEngine.missionEngine != null && gameEngine.missionEngine.linkedWorldState != null
                && this.linkedWorldOperation == null) {
            if (gameEngine.missionEngine.linkedWorldState.isOutcomeFenced()) {
                gameEngine.missionEngine.linkedWorldState.deferCommand(this);
                return;
            }
            if (this.team != null && gameEngine.missionEngine.linkedWorldState.getSurrendered().contains(this.team.teamId))
                return;
        }
        resolvePendingUnitIds();
        if (this.isSystemAction) {
            if (this.linkedWorldOperation != null) {
                if (gameEngine.missionEngine == null || gameEngine.missionEngine.linkedWorldState == null)
                    throw new IllegalStateException("Linked command has no world state");
                gameEngine.missionEngine.linkedWorldState.apply(this.linkedWorldOperation);
                return;
            }
            if (this.gameSpeedChange != 0.0f) {
                GameEngine.log("issueCommand: changeStepRate:" + this.gameSpeedChange);
                gameEngine.networkEngine.applyChangedSetup(this.gameSpeedChange, "command");
                return;
            }
            if (this.systemActionType != 0) {
                GameEngine.log("system action:" + this.systemActionType);
                if (this.systemActionType == 1) {
                    GameEngine.log("new DebugDesyncDetector");
                    new UnitSpawner(false).setUnitTeam(PlayerTeam.TEAM_ALL);
                    return;
                }
                if (this.systemActionType == 2) {
                    GameEngine.log("new DebugDesyncDetector (stress test)");
                    UnitSpawner unitSpawner = new UnitSpawner(false);
                    unitSpawner.setUnitTeam(PlayerTeam.TEAM_ALL);
                    unitSpawner.stressTestMode = true;
                    return;
                }
                if (this.systemActionType == 100) {
                    GameEngine.log("team surrender");
                    if (this.team == null) {
                        GameEngine.log("team not found");
                        return;
                    }
                    if (gameEngine.networkEngine.isServer) {
                        gameEngine.networkEngine.j("'" + this.team.teamName + "' has surrendered");
                    }
                    if (gameEngine.missionEngine != null && gameEngine.missionEngine.linkedWorldState != null) {
                        gameEngine.missionEngine.linkedWorldState.surrender(this.team.teamId);
                        return;
                    }
                    this.team.isTeamVictory = true;
                    for (Object o : BaseUnit.bE) {
                        BaseUnit baseUnit = (BaseUnit) o;
                        if (baseUnit.team == this.team && (baseUnit instanceof OrderableUnit)) {
                            ((OrderableUnit) baseUnit).c(false);
                        }
                    }
                    return;
                }
                if (this.systemActionType == 200) {
                    GameEngine.log("queue quick resync");
                    gameEngine.networkEngine.quickResyncRequested = true;
                    return;
                }
                if (this.systemActionType == TeamActionRegistry.SYSTEM_ACTION_TYPE) {
                    if (this.team == null || this.actionId == null) {
                        GameEngine.log("mod team action command missing team or action id");
                        return;
                    }
                    TeamActionRegistry.execute(
                            this.team,
                            this.actionId.getId(),
                            this.targetPoint,
                            this.systemFloat
                    );
                    return;
                }
                if (this.systemActionType == 5) {
                    GameEngine.log("system command spawn");
                    if (this.unitCommand == null || this.unitCommand.getCommandType() != UnitCommandType.build || this.unitCommand.getBuildUnitType() == null) {
                        GameEngine.log("system command spawn - failed");
                        return;
                    }
                    int buildQueueSize = this.unitCommand.getBuildQueueSize();
                    UnitType buildUnitType = this.unitCommand.getBuildUnitType();
                    boolean z = false;
                    if (this.team != null && this.team == gameEngine.playerTeam && gameEngine.playerTeam.getUnitCount(false, false) == 0) {
                        z = true;
                    }
                    BaseUnit baseUnitA = buildUnitType.a();
                    baseUnitA.posX = this.unitCommand.getTargetX();
                    baseUnitA.posY = this.unitCommand.getTargetY();
                    if (this.team != null) {
                        baseUnitA.f(this.team);
                    } else {
                        baseUnitA.f(PlayerTeam.TEAM_ALL);
                    }
                    baseUnitA.setCommandTargetUnit(null);
                    if (buildQueueSize != 1 && (baseUnitA instanceof OrderableUnit)) {
                        ((OrderableUnit) baseUnitA).a(buildQueueSize);
                    }
                    baseUnitA.onUnitSpawned();
                    if (baseUnitA instanceof OrderableUnit) {
                        OrderableUnit orderableUnit = (OrderableUnit) baseUnitA;
                        orderableUnit.br();
                        if (baseUnitA.bI()) {
                            gameEngine.pathfindingEngine.a(orderableUnit);
                        }
                    }
                    PlayerTeam.c(baseUnitA);
                    if (gameEngine.playerTeam == baseUnitA.team && baseUnitA.team != PlayerTeam.TEAM_ALL && !baseUnitA.u() && z) {
                        gameEngine.centerViewpoint(baseUnitA.posX, baseUnitA.posY);
                        gameEngine.gameUI.selectUnit(baseUnitA);
                        return;
                    }
                    return;
                }
                GameEngine.log("issueCommand: unknown system action:" + this.systemActionType);
                return;
            }
            GameEngine.log("issueCommand: Null System action");
            return;
        }
        if (hasUnavailableTarget()) return;
        if (this.sourceTeam != null) {
            this.sourceTeam.teamLastConnectionTime = System.currentTimeMillis();
            this.sourceTeam.teamPingCount = gameEngine.gameTimeMillis;
        }
        if (this.sourceTeam != null) {
            String str2 = null;
            OrderableUnit orderableUnit2 = null;
            Iterator it = this.selectedUnits.iterator();
            while (it.hasNext()) {
                OrderableUnit orderableUnit3 = (OrderableUnit) it.next();
                if (orderableUnit3.team != this.sourceTeam && !canIssueOrdersToTeam(this.sourceTeam, orderableUnit3.team)) {
                    if (str2 == null) {
                        str = VariableScope.nullOrMissingString;
                    } else {
                        str = str2 + ", ";
                    }
                    if (orderableUnit2 == null) {
                        orderableUnit2 = orderableUnit3;
                    }
                    str2 = str + orderableUnit3.objectId;
                    it.remove();
                } else if (orderableUnit3.canNotBeGivenOrdersByPlayer()) {
                    CommandController.logWithRateLimit("Warning unit: " + orderableUnit3.objectId + " has canNotBeGivenOrdersByPlayer set");
                    it.remove();
                }
            }
            if (str2 != null) {
                NetworkEngine.reportDesyncVerbose("Player(" + this.sourceTeam.teamId + ") " + this.sourceTeam.teamName + " cannot control units: " + str2, true);
                if (orderableUnit2 != null) {
                    String str3 = VariableScope.nullOrMissingString;
                    if (orderableUnit2.team != null) {
                        str3 = str3 + " targetUnitTeamId: " + orderableUnit2.team.teamId + " targetUnitTeamName: " + orderableUnit2.team.teamName;
                    }
                    CommandController.logWithRateLimit(str3);
                }
            }
        }
        if (this.clearExistingOrders) {
            for (OrderableUnit orderableUnit4 : this.selectedUnits) {
                orderableUnit4.clearAllWaypoints();
                orderableUnit4.attackTarget = null;
            }
        }
        if (this.unitCommand != null) {
            this.unitCommand.resolveTargetUnitFromId();
            FormationGroup formationGroupB = gameEngine.formationEngine.b();
            formationGroupB.commandTargets = this.commandTargets;
            boolean rwxFormationActive = rwxPrepareFormation();
            boolean rwxCorridorReady = rwxPrepareCorridor();
            int i = 0;
            while (i <= 1) {
                boolean z2 = i == 1;
                for (OrderableUnit orderableUnit5 : this.selectedUnits) {
                    if (orderableUnit5.isTransportAttached == z2) {
                        if (this.isInstantCommand) {
                            orderableUnit5.removeNonBuildRepairWaypoints();
                        } else if (!this.isQueued) {
                            orderableUnit5.clearAllWaypoints();
                        } else if (this.isHighPriority && this.unitCommand != null && (lastWaypoint = orderableUnit5.getLastWaypoint()) != null && this.unitCommand.isApproximatelySameTarget(lastWaypoint) && (lastWaypoint.getCommandType() == UnitCommandType.attackMove || lastWaypoint.getCommandType() == UnitCommandType.move)) {
                            if (this.unitCommand.getCommandType() == UnitCommandType.attackMove || this.unitCommand.getCommandType() == UnitCommandType.move) {
                                orderableUnit5.removeLastWaypoint();
                            }
                        }
                    }
                }
                i++;
            }
            for (OrderableUnit orderableUnit6 : this.selectedUnits) {
                if (!orderableUnit6.isValidNewWaypoint(this.unitCommand, CommandController.logRateLimitCounter < 5)) {
                    String str4 = VariableScope.nullOrMissingString;
                    if (this.sourceTeam != null) {
                        str4 = "Player(" + this.sourceTeam.teamId + ") " + this.sourceTeam.teamName + ": ";
                    }
                    CommandController.logWithRateLimit(str4 + "isValidNewWaypoint==false on: " + orderableUnit6.getUnitShortName());
                } else {
                    // RWX：旧版「队形散就不塞走廊」的抑制去掉了 —— 散队由集结点管，
                    // 走廊照旧全程有效，否则长距离又退回原版卡岸的行为
                    if (rwxCorridorReady) {
                        // RWX：先把走廊拐点排进队列，最后一段才是带阵型偏移的真实目标
                        TileMap rwxTileMap = gameEngine.tileMap;
                        // 逐单位起点：跳过比单位自己更远离目标的拐点 ——
                        // 已经冲过头（或从侧翼赶上）的单位不再被拽回走廊头，这就是「绕远」的主因
                        float rwxUX = orderableUnit6.posX;
                        float rwxUY = orderableUnit6.posY;
                        float rwxTX = this.unitCommand.getTargetX();
                        float rwxTY = this.unitCommand.getTargetY();
                        float rwxUT = Utility.distanceSq(rwxUX, rwxUY, rwxTX, rwxTY);
                        rwxTileMap.setCursorTileIndexFromWorldPoint(rwxUX, rwxUY);
                        int rwxUTX = rwxTileMap.cursorTileX;
                        int rwxUTY = rwxTileMap.cursorTileY;
                        // RWX：走廊是按领队位置算的。本单位若与目标视线直连，
                        // 拐点链对它纯属绕远 —— 直接走目标（图一「绕一大圈到近处」的成因）
                        // 只用本单位局部标志，别动循环外的 rwxCorridorReady
                        // RWX：视线检查按本单位半径膨胀后判定（旧版吃走廊探测留下的 rwxTFreeR，
                        // 窄档跑完恒为 0，大船判成直连就跳过走廊，实际蹭岸只能绕）。
                        float rwxUR = Math.min(orderableUnit6.radius * 0.8f, rwxTileMap.tileWorldSizeX * 3.0f);
                        if (rwxTFreePx != rwxUR) {
                            rwxTFreePx = rwxUR;
                            rwxTFreeGen++; // 半径档变了，足迹缓存作废
                        }
                        boolean rwxUseCorridor = !rwxLineClear(rwxCorridorType, rwxUTX, rwxUTY, rwxCorridorEndTX, rwxCorridorEndTY);
                        int rwxFrom = 0;
                        float rwxBest = Float.MAX_VALUE;
                        boolean rwxFound = false;
                        for (int rwxK = 0; rwxK < this.rwxCorridorCount; rwxK++) {
                            float rwxWX = (this.rwxCorridorX[rwxK] * rwxTileMap.tileWorldSizeX) + rwxTileMap.halfTileWorldSizeX;
                            float rwxWY = (this.rwxCorridorY[rwxK] * rwxTileMap.tileWorldSizeY) + rwxTileMap.halfTileWorldSizeY;
                            if (Utility.distanceSq(rwxWX, rwxWY, rwxTX, rwxTY) >= rwxUT) {
                                continue;
                            }
                            float rwxDU = Utility.distanceSq(rwxWX, rwxWY, rwxUX, rwxUY);
                            if (!rwxFound || rwxDU < rwxBest) {
                                rwxBest = rwxDU;
                                rwxFrom = rwxK;
                                rwxFound = true;
                            }
                        }
                        // 注意：边界只对本单位生效，别动 rwxCorridorReady（循环外变量，改了会连累别的单位）
                        int rwxTo = (rwxFound && rwxUseCorridor) ? this.rwxCorridorCount : 0;
                        for (int rwxI = rwxFrom; rwxI < rwxTo; rwxI++) {
                            UnitCommand rwxWp = orderableUnit6.appendWaypointCopy(this.unitCommand);
                            rwxTileMap.setCursorTileIndexFromTileIndex(this.rwxCorridorX[rwxI], this.rwxCorridorY[rwxI]);
                            rwxWp.rwxSetFormationOffset(rwxTileMap.cursorTileX + rwxTileMap.halfTileWorldSizeX, rwxTileMap.cursorTileY + rwxTileMap.halfTileWorldSizeY);
                        }
                    }
                    // RWX：出发点不再绕集结圈；真要收拢，只在目的地附近来一次
                    if (rwxStagingReady) {
                        UnitCommand rwxStagingCmd = orderableUnit6.appendWaypointCopy(this.unitCommand);
                        rwxApplyRegroupOffset(rwxStagingCmd);
                        formationGroupB.a(orderableUnit6, rwxStagingCmd);
                        orderableUnit6.a(rwxStagingCmd);
                    }
                    UnitCommand unitCommandAppendWaypointCopy = orderableUnit6.appendWaypointCopy(this.unitCommand);
                    if (rwxFormationActive) {
                        rwxApplyFormationOffset(unitCommandAppendWaypointCopy, orderableUnit6);
                    }
                    formationGroupB.a(orderableUnit6, unitCommandAppendWaypointCopy);
                    orderableUnit6.a(unitCommandAppendWaypointCopy);
                }
            }
            formationGroupB.b();
            return;
        }
        if (AbstractUnitAction.isActionIdSpecified(this.actionId)) {
            for (OrderableUnit orderableUnit7 : this.selectedUnits) {
                AbstractUnitAction abstractUnitActionA = orderableUnit7.validateActionId(this.actionId);
                if (abstractUnitActionA == null) {
                    CommandController.logWithRateLimit("Could not find specialAction:" + this.actionId.getId() + " on " + orderableUnit7.r().getUnitTypeDescriptionShort());
                } else if (!abstractUnitActionA.b(orderableUnit7)) {
                    CommandController.logWithRateLimit("!isAvailable specialAction:" + this.actionId.getId() + " on " + orderableUnit7.r().getUnitTypeDescriptionShort() + " (action being skipped)");
                    if (CommandController.DEBUG_TRACE_ENABLED) {
                        CommandController.logWithRateLimit("Command source:" + this.debugStackTrace);
                    }
                } else {
                    orderableUnit7.a(abstractUnitActionA);
                    LagHidingManager.onUnitActionStarted(orderableUnit7, abstractUnitActionA);
                    orderableUnit7.a(abstractUnitActionA, this.stopCurrentAction, this.targetPoint, this.targetUnit);
                }
            }
            PingMapAction pingMapActionA = PingMapAction.a(this.actionId);
            if (pingMapActionA != null) {
                if (gameEngine.playerTeam != null && this.team != null) {
                    if (this.team.d(gameEngine.playerTeam)) {
                        gameEngine.gameUI.sendMapPing(this.targetPoint.x, this.targetPoint.y, this.team, pingMapActionA);
                    }
                } else {
                    CommandController.logWithRateLimit("PingMapAction failed: game.playerTeam==null or this.team==null");
                }
            }
        }
        if (this.attackMode != null) {
            Iterator it2 = this.selectedUnits.iterator();
            while (it2.hasNext()) {
                ((OrderableUnit) it2.next()).attackMode = this.attackMode;
            }
        }
        if (this.rallyPoint != null) {
            for (Object obj : this.selectedUnits) {
                if (obj instanceof FactoryQueueInterface) {
                    ((FactoryQueueInterface) obj).a(this.rallyPoint);
                }
            }
        }
    }

    /* JADX INFO: renamed from: a */
    public boolean canIssueOrdersToTeam(PlayerTeam playerTeam, PlayerTeam playerTeam2) {
        if (playerTeam != null && playerTeam2 != null && playerTeam2.d(playerTeam) && (this.allowedTeamMask & (1 << playerTeam2.teamId)) != 0) {
            return true;
        }
        return false;
    }

    /* JADX INFO: renamed from: l */
    public boolean prepareAndValidateCommand() {
        UnitType buildUnitType;
        BaseUnit baseUnitFindTurretPosition;
        this.allowedTeamMask = (short) 0;
        for (int i = 0; i < PlayerTeam.TEAM_NEUTRAL; i++) {
            PlayerTeam playerTeamK = PlayerTeam.k(i);
            if (playerTeamK != null && playerTeamK.isSharedControlEnabled()) {
                this.allowedTeamMask = (short) (this.allowedTeamMask | (1 << i));
            }
        }
        if (GameEngine.getInstance().getVersionCode(true) < 127 && this.unitCommand != null && this.unitCommand.getCommandType() == UnitCommandType.build && (buildUnitType = this.unitCommand.getBuildUnitType()) != null && (baseUnitFindTurretPosition = BaseUnit.findTurretPosition(buildUnitType)) != null && !(baseUnitFindTurretPosition instanceof OrderableUnit)) {
            GameEngine.log("Rejecting non OrderableUnit build order: " + buildUnitType.getUnitTypeDescriptionShort());
            return false;
        }
        if (this.unitCommand != null && this.unitCommand.isForceMove) {
            GameEngine.log("Rejecting waypoint with addedByAction true");
            return false;
        }
        return true;
    }
}
