package io.github.rwx.probe;

import com.corrodinggames.rts.game.PlayerTeam;
import com.corrodinggames.rts.game.ai.AIController;
import com.corrodinggames.rts.game.units.BaseUnit;
import com.corrodinggames.rts.game.units.UnitType;
import com.corrodinggames.rts.game.units.UnitTypeEnum;
import com.corrodinggames.rts.game.units.actions.AbstractUnitAction;
import com.corrodinggames.rts.game.units.actions.ActionId;
import com.corrodinggames.rts.game.units.custom.CustomUnitConfig;
import com.corrodinggames.rts.game.units.custom.condition.StoredResourceEntry;
import com.corrodinggames.rts.game.units.custom.condition.StoredResources;
import com.corrodinggames.rts.game.units.custom.price.UnitPrice;
import com.corrodinggames.rts.gameFramework.GameEngine;
import io.github.rwx.LegacyAssetBridge;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * RWX 观测探针。默认开启，RWX_PROBE=0|off|false 关闭。
 * 观测窗口：开局到一方获胜。
 * 产出：
 *   RWX_PROBE         每 300 tick 双方资源/单位/建筑数
 *   RWX_PROBE_BUILD   每座落成建筑（tick/team/type/cat）
 *   RWX_PROBE_UNIT    每次排产单位（tick/team/type/cat）
 *   RWX_PROBE_BAR     建造栏快照：avail/afford/locked/hidden/aiDisabled
 *   RWX_PROBE_SUMMARY 终局按 team×type 的数量与首末 tick、时间段分布
 *   RWX_PROBE_PRICE   开局模组单位造价表
 *   RWX_PROBE_PHASE   时间段总览：每段各队新建了什么、排产了什么、段末持有多少单位与资源（按注册名）
 *                   段长 RWX_PROBE_PHASE 可调（1000 的整数倍），默认 6000 tick
 */
public final class RwxProbe {

    private static final String RAW = System.getenv("RWX_PROBE");
    private static final boolean ENABLED = RAW == null
            || !(RAW.equals("0") || RAW.equalsIgnoreCase("off") || RAW.equalsIgnoreCase("false"));
    private static final int PERIOD_TICKS = 300;
    private static final int END_CHECK_TICKS = 30;
    private static final int BAR_FIRST_TICK = 90;
    private static final int BAR_PERIOD = 3000;
    private static final int BAR_MAX_SNAPSHOTS = 12;
    private static final int BAR_LIMIT = 160;

    private static int nextStateTick = 0;
    private static int nextEndCheck = 0;
    private static int nextBarTick = BAR_FIRST_TICK;
    private static int barSnapshots = 0;
    private static int nextDumpTick = 30000;
    private static boolean pricesLogged = false;
    private static boolean finished = false;
    private static int lastTick = 0;
    private static final LinkedHashSet<Integer> participants = new LinkedHashSet<Integer>();
    private static final LinkedHashMap<String, TypeStat> stats = new LinkedHashMap<String, TypeStat>();
    private static final Rolling stateBuf = new Rolling(6000, 44000);
    private static final Rolling eventBuf = new Rolling(8000, 92000);
    private static final Rolling priceBuf = new Rolling(48000, 12000);
    // barBuf 每次快照都清空重建，且要回读做计数，所以整段留全（headLimit=0 表示全部进尾部窗口）。
    private static final Rolling barBuf = new Rolling(0, 400000);
    private static final Rolling barTrend = new Rolling(0, 20000);
    private static final Rolling typeBuf = new Rolling(40000, 20000);
    private static final Rolling rosterBuf = new Rolling(0, 80000);

    /** 时间段总览的段长；必须是 1000 的整数倍，才能和 TypeStat.buckets 的粒度对齐。 */
    private static final int PHASE_TICKS = readPhaseTicks();
    private static final int PHASE_MAX = 64;
    private static final ArrayList<PhaseRec> phases = new ArrayList<PhaseRec>();
    private static int nextPhaseTick = PHASE_TICKS;
    private static boolean phaseOpen;
    private static boolean phaseOverflow = false;

    private RwxProbe() {
    }

    public static boolean isFinished() {
        return finished;
    }

    /** 开新局时清空上一局的全部观测状态。 */
    private static void resetAll() {
        stats.clear();
        participants.clear();
        phases.clear();
        stateBuf.reset();
        eventBuf.reset();
        priceBuf.reset();
        barBuf.reset();
        barTrend.reset();
        typeBuf.reset();
        rosterBuf.reset();
        finished = false;
        pricesLogged = false;
        phaseOpen = false;
        phaseOverflow = false;
        barSnapshots = 0;
        lastTick = 0;
        nextStateTick = 0;
        nextEndCheck = 0;
        nextBarTick = BAR_FIRST_TICK;
        nextDumpTick = 30000;
        nextPhaseTick = PHASE_TICKS;
    }

    private static final class TypeStat {
        final int team;
        final String kind;
        final String type;
        final String reg;
        final String file;
        final String cat;
        int count;
        int first = -1;
        int last = -1;
        final TreeMap<Integer, Integer> buckets = new TreeMap<Integer, Integer>();

        TypeStat(int team, String kind, String type, String reg, String file, String cat) {
            this.team = team;
            this.kind = kind;
            this.type = type;
            this.reg = reg;
            this.file = file;
            this.cat = cat;
        }

        void add(int tick) {
            this.count++;
            if (this.first < 0) {
                this.first = tick;
            }
            this.last = tick;
            Integer bucket = Integer.valueOf(tick / 1000);
            Integer old = this.buckets.get(bucket);
            this.buckets.put(bucket, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
        }
    }

    public static void tick(int tick) {
        if (!ENABLED || finished) {
            return;
        }
        GameEngine guardEngine = GameEngine.getInstance();
        if (guardEngine == null) {
            return;
        }
        // isInGameOrLobby() 要 networkEngine.singleplayerServer，无窗口台架（headless）没置位，
        // 直接当门会把整条观测打哑（SETUP/资源曲线/终局报告全丢，只剩 BaseZone 调的事件）。
        // 所以 headless 放行，其余入口仍要求「确实在局内」。
        if (!GameEngine.isHeadlessMode && !guardEngine.isInGameOrLobby()) {
            return;
        }
        String guardMap = guardEngine.currentMapPath;
        if (guardMap != null && guardMap.contains("menu_background")) {
            return;
        }
        // tick 每局从 1 重新开始，这就是「新的一局开始了」的信号。
        // 探针状态全是 static，原来只增不清：一局打完 finished 变 true 就永久哑掉，
        // 沙盒里换图重开下一局什么都不再记；nextDumpTick / nextPhaseTick 这类绝对 tick
        // 阈值也会卡在上一局的进度上，于是整局堆成一个时间段。
        if (tick == 1) {
            resetAll();
        }
        lastTick = tick;
        if (!pricesLogged) {
            pricesLogged = true;
            logPrices();
        }
        if (tick == 1) {
            logSetup(tick);
        }
        if (tick >= nextStateTick) {
            nextStateTick = tick + PERIOD_TICKS;
            logState(tick);
        }
        if (tick >= nextBarTick && barSnapshots < BAR_MAX_SNAPSHOTS) {
            nextBarTick = tick + BAR_PERIOD;
            barSnapshots++;
            barSnapshot(tick);
        }
        if (tick >= nextEndCheck) {
            nextEndCheck = tick + END_CHECK_TICKS;
            checkEnd(tick);
        }
        if (!finished && tick >= nextDumpTick) {
            nextDumpTick += 30000;
            logRoster(tick);
            writeReport(tick, -1, "periodic");
        }
        if (tick >= nextPhaseTick) {
            closePhase(tick);
            nextPhaseTick += PHASE_TICKS;
        }
    }

    public static void buildingBuilt(PlayerTeam team, UnitType unitType, int tick) {
        record("BUILD", team, unitType, tick);
    }

    public static void unitQueued(PlayerTeam team, UnitType unitType, int tick) {
        record("UNIT", team, unitType, tick);
    }

    private static void record(String kind, PlayerTeam team, UnitType unitType, int tick) {
        if (!ENABLED || finished || team == null || unitType == null) {
            return;
        }
        participants.add(Integer.valueOf(team.teamId));
        String name = typeName(unitType);
        String reg = regOf(unitType);
        String file = fileOf(unitType);
        String cat = category(unitType);
        String key = team.teamId + "|" + kind + "|" + reg;
        TypeStat stat = stats.get(key);
        if (stat == null) {
            stat = new TypeStat(team.teamId, kind, name, reg, file, cat);
            stats.put(key, stat);
        }
        stat.add(tick);
        TeamPhase tp = currentPhaseTeam(team, tick);
        if (tp != null) {
            bump("UNIT".equals(kind) ? tp.queued : tp.built, reg);
        }
        StringBuilder line = new StringBuilder("RWX_PROBE_").append(kind).append(" tick=").append(tick)
                .append(" team=").append(team.teamId).append(" reg=").append(reg)
                .append(" type=").append(name).append(" cat=").append(cat);
        GameEngine.log(line.toString());
        appendLine(eventBuf, line);
    }

    private static void logState(int tick) {
        GameEngine engine = GameEngine.getInstance();
        if (engine == null || engine.playerTeam == null) {
            return;
        }
        StringBuilder sb = new StringBuilder("RWX_PROBE tick=").append(tick);
        for (PlayerTeam team : PlayerTeam.getTeams()) {
            if (team == null || team.teamId < 0) {
                continue;
            }
            sb.append(" | T").append(team.teamId).append(team instanceof AIController ? "ai" : "pl")
                    .append('(').append(allyLetter(team)).append(')');
            sb.append(" cr=").append((long) team.credits);
            StoredResources storedResources = team.getCustomResources();
            if (storedResources != null && storedResources.b.size > 0) {
                int n = storedResources.b.size;
                Object[] arr = storedResources.b.a();
                for (int i = 0; i < n; i++) {
                    StoredResourceEntry entry = (StoredResourceEntry) arr[i];
                    if (entry.b == 0.0d) {
                        continue;
                    }
                    sb.append(' ').append(entry.a.b).append('=').append((long) entry.b);
                }
            }
            int units = 0;
            int buildings = 0;
            Iterator it = BaseUnit.bE.iterator();
            while (it.hasNext()) {
                BaseUnit unit = (BaseUnit) it.next();
                if (unit == null || unit.isDead || unit.team != team) {
                    continue;
                }
                if (unit.unitType != null && unit.unitType.isBuildingUnit()) {
                    buildings++;
                } else {
                    units++;
                }
            }
            if (units + buildings > 0) {
                participants.add(Integer.valueOf(team.teamId));
            }
            sb.append(" units=").append(units).append(" bld=").append(buildings);
        }
        GameEngine.log(sb.toString());
        appendLine(stateBuf, sb);
        logAllySums(tick);
    }

    private static void logSetup(int tick) {
        GameEngine engine = GameEngine.getInstance();
        StringBuilder sb = new StringBuilder("RWX_PROBE_SETUP tick=").append(tick);
        try {
            sb.append(" map=").append(engine.currentMapPath);
        } catch (Throwable t) {
        }
        try {
            sb.append(" startingUnits=").append(engine.networkEngine.roomSettings.startingUnits)
                    .append(" sharedControl=").append(engine.networkEngine.roomSettings.sharedControl);
        } catch (Throwable t) {
        }
        TreeMap<String, Integer> sides = new TreeMap<String, Integer>();
        for (PlayerTeam team : PlayerTeam.getTeams()) {
            if (team == null || team.teamId < 0) {
                continue;
            }
            String ally = allyLetter(team);
            sb.append(" | T").append(team.teamId).append("(").append(ally).append(')')
                    .append(team instanceof AIController ? "ai" : "pl");
            if (!team.isTeamWipedOut) {
                Integer old = sides.get(ally);
                sides.put(ally, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
            }
        }
        sb.append(" sides=").append(sides.keySet());
        GameEngine.log(sb.toString());
        appendLine(stateBuf, sb);
    }

    private static void logAllySums(int tick) {
        TreeMap<String, int[]> sums = new TreeMap<String, int[]>();
        TreeMap<String, ArrayList<Integer>> memberMap = new TreeMap<String, ArrayList<Integer>>();
        for (PlayerTeam team : PlayerTeam.getTeams()) {
            if (team == null || team.teamId < 0) {
                continue;
            }
            String ally = allyLetter(team);
            int[] acc = sums.get(ally);
            if (acc == null) {
                acc = new int[]{0, 0, 0, 0};
                sums.put(ally, acc);
                memberMap.put(ally, new ArrayList<Integer>());
            }
            memberMap.get(ally).add(Integer.valueOf(team.teamId));
            acc[0] += (int) team.credits;
            Iterator it = BaseUnit.bE.iterator();
            while (it.hasNext()) {
                BaseUnit unit = (BaseUnit) it.next();
                if (unit == null || unit.isDead || unit.team != team) {
                    continue;
                }
                if (unit.unitType != null && unit.unitType.isBuildingUnit()) {
                    acc[2]++;
                } else {
                    acc[1]++;
                }
            }
        }
        StringBuilder m2 = new StringBuilder();
        for (Map.Entry<String, ArrayList<Integer>> e : memberMap.entrySet()) {
            if (m2.length() > 0) {
                m2.append(' ');
            }
            m2.append(e.getKey()).append(':');
            ArrayList<Integer> slots = e.getValue();
            for (int i = 0; i < slots.size(); i++) {
                if (i > 0) {
                    m2.append(',');
                }
                m2.append('T').append(slots.get(i));
            }
        }
        StringBuilder line = new StringBuilder("RWX_PROBE_ALLY tick=").append(tick).append(" members=").append(m2);
        for (Map.Entry<String, int[]> e : sums.entrySet()) {
            int[] a2 = e.getValue();
            line.append(" | ").append(e.getKey()).append(" cr=").append(a2[0])
                    .append(" units=").append(a2[1]).append(" bld=").append(a2[2]);
        }
        GameEngine.log(line.toString());
        appendLine(stateBuf, line);
    }

    private static void checkEnd(int tick) {
        if (participants.size() < 2) {
            return;
        }
        java.util.TreeMap<String, Integer> groupAlive = new java.util.TreeMap<String, Integer>();
        int alive = 0;
        int aliveTeam = -1;
        for (PlayerTeam team : PlayerTeam.getTeams()) {
            if (team == null || team.teamId < 0) {
                continue;
            }
            if (!participants.contains(Integer.valueOf(team.teamId))) {
                continue;
            }
            if (team.isTeamWipedOut) {
                continue;
            }
            alive++;
            aliveTeam = team.teamId;
            String ally = allyLetter(team);
            Integer old = groupAlive.get(ally);
            groupAlive.put(ally, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
        }
        if (groupAlive.size() <= 1) {
            finish(tick, aliveTeam);
        }
    }

    private static void finish(int tick, int winner) {
        if (finished) {
            return;
        }
        closePhase(tick);
        finished = true;
        barSnapshot(tick);
        logRoster(tick);
        dumpSummary(tick, winner);
        writeReport(tick, winner, "final");
    }

    private static void logRoster(int tick) {
        if (!BAR) {
            return;
        }
        for (PlayerTeam team : PlayerTeam.getTeams()) {
            if (team == null || team.teamId < 0 || team.isTeamWipedOut) {
                continue;
            }
            TreeMap<String, Integer> unitCounts = new TreeMap<String, Integer>();
            TreeMap<String, Integer> bldCounts = new TreeMap<String, Integer>();
            TreeMap<String, Integer> builders = new TreeMap<String, Integer>();
            Iterator it = BaseUnit.bE.iterator();
            while (it.hasNext()) {
                BaseUnit unit = (BaseUnit) it.next();
                if (unit == null || unit.isDead || unit.team != team || unit.unitType == null) {
                    continue;
                }
                String reg = regOf(unit.unitType);
                if (unit.unitType.isBuildingUnit()) {
                    bump(bldCounts, reg);
                } else {
                    bump(unitCounts, reg);
                    try {
                        ArrayList actions = unit.getAvailableActions();
                        if (actions != null && actions.size() > 0) {
                            bump(builders, reg);
                        }
                    } catch (Throwable t) {
                    }
                }
            }
            StringBuilder sb = new StringBuilder("RWX_PROBE_ROSTER tick=").append(tick)
                    .append(" team=").append(team.teamId).append(" ally=").append(allyLetter(team))
                    .append(team instanceof AIController ? " ai" : " pl")
                    .append(" units=").append(list(unitCounts))
                    .append(" | bld=").append(list(bldCounts))
                    .append(" | builders=").append(list(builders));
            GameEngine.log(sb.toString());
            appendLine(rosterBuf, sb);
        }
    }

    /** 当前时间段里这支队伍的记账本；没有队伍或段数超上限时返回 null（宁可少记，不能拖慢对局）。 */
    private static TeamPhase currentPhaseTeam(PlayerTeam team, int tick) {
        if (team == null || team.teamId < 0) {
            return null;
        }
        if (phases.size() >= PHASE_MAX) {
            phaseOverflow = true;
            return null;
        }
        if (!phaseOpen) {
            phases.add(new PhaseRec(tick, tick));
            phaseOpen = true;
        }
        PhaseRec rec = phases.get(phases.size() - 1);
        TeamPhase tp = rec.teams.get(Integer.valueOf(team.teamId));
        if (tp == null) {
            tp = new TeamPhase(team.teamId, allyLetter(team), team instanceof AIController, team.isTeamWipedOut);
            rec.teams.put(Integer.valueOf(team.teamId), tp);
        }
        return tp;
    }

    /** 结算一个时间段：段末扫一遍全场，记下各队现在有什么单位、什么资源。 */
    private static void closePhase(int tick) {
        if (!phaseOpen) {
            return;
        }
        PhaseRec rec = phases.get(phases.size() - 1);
        rec.to = tick;
        phaseOpen = false;
        GameEngine engine = GameEngine.getInstance();
        if (engine != null && engine.playerTeam != null) {
            for (PlayerTeam team : PlayerTeam.getTeams()) {
                if (team == null || team.teamId < 0) {
                    continue;
                }
                TeamPhase tp = rec.teams.get(Integer.valueOf(team.teamId));
                if (tp == null) {
                    tp = new TeamPhase(team.teamId, allyLetter(team), team instanceof AIController, team.isTeamWipedOut);
                    rec.teams.put(Integer.valueOf(team.teamId), tp);
                }
                tp.wiped = team.isTeamWipedOut;
                tp.credits = (long) team.credits;
                StoredResources sr = team.getCustomResources();
                if (sr != null && sr.b.size > 0) {
                    Object[] arr = sr.b.a();
                    for (int i = 0; i < sr.b.size; i++) {
                        StoredResourceEntry en = (StoredResourceEntry) arr[i];
                        if (en != null && en.b != 0.0d && en.a != null) {
                            tp.custom.put(String.valueOf(en.a.b), Long.valueOf((long) en.b));
                        }
                    }
                }
            }
            Iterator it = BaseUnit.bE.iterator();
            while (it.hasNext()) {
                BaseUnit unit = (BaseUnit) it.next();
                if (unit == null || unit.isDead || unit.unitType == null || unit.team == null) {
                    continue;
                }
                TeamPhase tp = rec.teams.get(Integer.valueOf(unit.team.teamId));
                if (tp == null) {
                    continue;
                }
                bump(unit.unitType.isBuildingUnit() ? tp.holdBld : tp.holdUnits, regOf(unit.unitType));
            }
        }
        GameEngine.log("RWX_PROBE_PHASE close tick=" + rec.from + "-" + rec.to + " teams=" + rec.teams.size());
    }

    private static void bump(TreeMap<String, Integer> map, String key) {
        Integer old = map.get(key);
        map.put(key, Integer.valueOf(old == null ? 1 : old.intValue() + 1));
    }

    private static String list(TreeMap<String, Integer> map) {
        if (map.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : map.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey()).append('x').append(e.getValue());
        }
        return sb.toString();
    }

    private static void appendLine(Rolling buf, CharSequence line) {
        buf.add(line);
    }

    /**
     * 滚动行缓冲。旧实现是「攒够 40000 字符就丢弃新行」，长局会把后半局静默吃掉
     * （rwx-probe-final-20261117-011537.log 声称 0 - 44731，资源曲线却只剩到 tick=21001）。
     * 现在改成保留最前 headLimit 字符 + 最后 tailLimit 字符，中间省略多少行写在报告里。
     */
    private static final class Rolling {
        private final int headLimit;
        private final int tailLimit;
        private final StringBuilder head = new StringBuilder();
        private final ArrayList<String> tail = new ArrayList<String>();
        private int headLines;
        private int tailChars;
        private int total;

        Rolling(int headLimit, int tailLimit) {
            this.headLimit = headLimit;
            this.tailLimit = tailLimit;
        }

        void add(CharSequence line) {
            String s = line.toString();
            total++;
            if (head.length() < headLimit) {
                head.append(s).append('\n');
                headLines++;
                return;
            }
            tail.add(s);
            tailChars += s.length() + 1;
            while (tailChars > tailLimit && tail.size() > 1) {
                tailChars -= tail.remove(0).length() + 1;
            }
        }

        /** 重新来一段快照用（建造栏每次都是全量重建）。 */
        void reset() {
            head.setLength(0);
            tail.clear();
            tailChars = 0;
            headLines = 0;
            total = 0;
        }

        String text() {
            StringBuilder sb = new StringBuilder(head.length() + tailChars + 96);
            sb.append(head);
            int dropped = total - headLines - tail.size();
            if (dropped > 0) {
                sb.append("...(共 ").append(total).append(" 行，中间省略 ").append(dropped)
                        .append(" 行：保留最前 ").append(headLines).append(" 行与最后 ").append(tail.size()).append(" 行)...\n");
            }
            for (int i = 0; i < tail.size(); i++) {
                sb.append(tail.get(i)).append('\n');
            }
            return sb.toString();
        }

    }

    private static final class TeamPhase {
        final int team;
        final String ally;
        final boolean ai;
        boolean wiped;
        final TreeMap<String, Integer> built = new TreeMap<String, Integer>();
        final TreeMap<String, Integer> queued = new TreeMap<String, Integer>();
        final TreeMap<String, Integer> holdUnits = new TreeMap<String, Integer>();
        final TreeMap<String, Integer> holdBld = new TreeMap<String, Integer>();
        final TreeMap<String, Long> custom = new TreeMap<String, Long>();
        long credits;

        TeamPhase(int team, String ally, boolean ai, boolean wiped) {
            this.team = team;
            this.ally = ally;
            this.ai = ai;
            this.wiped = wiped;
        }
    }

    private static final class PhaseRec {
        final int from;
        int to;
        final TreeMap<Integer, TeamPhase> teams = new TreeMap<Integer, TeamPhase>();

        PhaseRec(int from, int to) {
            this.from = from;
            this.to = to;
        }
    }

    private static int readPhaseTicks() {
        String v = System.getenv("RWX_PROBE_PHASE");
        if (v != null) {
            try {
                int n = Integer.parseInt(v.trim());
                if (n >= 1000 && n % 1000 == 0) {
                    return n;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return 6000;
    }

    /** 引擎数据根下的 logs；拿不到时回落相对路径（等同旧行为）。 */
    private static String defaultLogDir() {
        try {
            File root = LegacyAssetBridge.storageRootDir();
            if (root != null && root.isDirectory()) {
                return new File(root, "logs").getAbsolutePath();
            }
        } catch (Throwable ignored) {
        }
        return "logs";
    }

    private static void writeReport(int tick, int winner, String reason) {
        StringBuilder r = new StringBuilder(1 << 16);
        String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        r.append("RWX 作战观测报告\n");
        r.append("生成时间: ").append(stamp).append('\n');
        r.append("tick 范围: 0 - ").append(tick).append('\n');
        r.append("报告类型: ").append(reason.equals("final") ? "终局" : "周期性快照（未分胜负）").append('\n');
        r.append("胜者: ").append(winner < 0 ? "无（仍在进行）" : "team=" + winner).append('\n');
        r.append("参战方: ").append(participants.size()).append('\n');
        r.append("关闭探针: 设置环境变量 RWX_PROBE=0\n\n");

        r.append("== 时间段总览（每 ").append(PHASE_TICKS).append(" tick 一段）==\n");
        r.append("段内「新建/排产」是这一段的增量；「现有」是段末全场快照，名字一律用注册名（reg）。\n");
        if (phases.isEmpty()) {
            r.append("（无数据：这段时间里没有任何建造或排产事件）\n");
        }
        for (int pi = 0; pi < phases.size(); pi++) {
            PhaseRec rec = phases.get(pi);
            r.append('\n').append("【").append(rec.from).append(" - ").append(rec.to).append("】\n");
            for (Map.Entry<Integer, TeamPhase> te : rec.teams.entrySet()) {
                TeamPhase tp = te.getValue();
                if (tp.built.isEmpty() && tp.queued.isEmpty() && tp.holdUnits.isEmpty() && tp.holdBld.isEmpty()) {
                    continue;
                }
                r.append("  T").append(tp.team).append('(').append(tp.ally).append(')')
                        .append(tp.ai ? "ai" : "pl").append(tp.wiped ? "[已全灭]" : "").append(' ');
                r.append("新建: ").append(list(tp.built));
                r.append(" | 排产: ").append(list(tp.queued));
                r.append('\n');
                r.append("      现有 单位: ").append(list(tp.holdUnits));
                r.append(" | 建筑: ").append(list(tp.holdBld));
                r.append(" | 资源: cr=").append(tp.credits);
                for (Map.Entry<String, Long> ce : tp.custom.entrySet()) {
                    r.append(' ').append(ce.getKey()).append('=').append(ce.getValue().longValue());
                }
                r.append('\n');
            }
        }
        if (phaseOverflow) {
            r.append('\n').append("...(时间段数超过 ").append(PHASE_MAX).append(" 段，后面的事件不再分段记账；")
                    .append("想全记就调大环境变量 RWX_PROBE_PHASE，例如 RWX_PROBE_PHASE=12000)...\n");
        }

        r.append("\n== 类别汇总（AI 意愿比例）==\n");
        TreeMap<String, Integer> byCat = new TreeMap<String, Integer>();
        TreeMap<Integer, Integer> teamTotal = new TreeMap<Integer, Integer>();
        for (Map.Entry<String, TypeStat> e : stats.entrySet()) {
            TypeStat s = e.getValue();
            String key = s.team + "|" + s.cat;
            Integer old = byCat.get(key);
            byCat.put(key, Integer.valueOf(old == null ? s.count : old.intValue() + s.count));
            Integer tOld = teamTotal.get(Integer.valueOf(s.team));
            teamTotal.put(Integer.valueOf(s.team), Integer.valueOf(tOld == null ? s.count : tOld.intValue() + s.count));
        }
        for (Map.Entry<String, Integer> e : byCat.entrySet()) {
            String[] parts = e.getKey().split("\\|");
            Integer total = teamTotal.get(Integer.valueOf(parts[0]));
            double pct = total == null || total.intValue() == 0 ? 0.0d : 100.0d * e.getValue().intValue() / total.intValue();
            r.append("team=").append(parts[0]).append(' ').append(parts[1]).append(' ').append(e.getValue())
                    .append(" (").append(String.format(java.util.Locale.ROOT, "%.1f%%", pct)).append(")\n");
        }

        r.append("\n== 按类型统计（数量 / 首次 / 末次 / 1000 tick 分段）==\n");
        for (Map.Entry<String, TypeStat> e : stats.entrySet()) {
            TypeStat s = e.getValue();
            r.append("team=").append(s.team).append(' ').append(s.reg).append(' ').append(s.type)
                    .append(" [").append(s.cat).append("] file=").append(s.file).append(" count=")
                    .append(s.count).append(" first=").append(s.first).append(" last=").append(s.last)
                    .append(" ").append(bucketText(s)).append('\n');
        }

        r.append("\n== 双方资源曲线（每 300 tick）==\n").append(stateBuf.text());
        r.append("\n== 建造 / 排产事件 ==\n").append(eventBuf.text());
        r.append("\n== 建造栏变化趋势 ==\n").append(barTrend.text());
        r.append("\n== 建造栏快照（最近一次完整）==\n").append(barBuf.text());
        r.append("\n== 单位造价表 ==\n").append(priceBuf.text());
        r.append("\n== 各队编成（持有单位 / 建筑 / 建造者）==\n").append(rosterBuf.text());
        r.append("\n== 注册型对照表（reg / name / 源文件）==\n").append(typeBuf.text());

        // 落点优先 RWX_PROBE_DIR，其次引擎数据根（跟 rwx.log 同一套目录）。
        // 原来缺省写相对路径 "logs"，那是相对 JVM 的 cwd —— 换启动入口（EXE / 快捷方式 /
        // 启动器）cwd 就变，报告可能掉到谁都不知的目录里。数据根由 DesktopPlatform.defaultRoot()
        // 决定（launch.dir 优先，否则 uber jar 所在目录），跟入口无关。
        String dirName = System.getenv("RWX_PROBE_DIR");
        if (dirName == null || dirName.isEmpty()) {
            dirName = defaultLogDir();
        }
        File dir = new File(dirName);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, "rwx-probe-" + (reason.equals("final") ? "final-" : "run-")
                + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()) + ".log");
        try {
            Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
            try {
                writer.write(r.toString());
            } finally {
                writer.close();
            }
            GameEngine.log("RWX_PROBE_REPORT path=" + file.getAbsolutePath() + " bytes=" + file.length());
        } catch (Throwable t) {
            GameEngine.log("RWX_PROBE_REPORT failed: " + t);
        }
    }

    private static void dumpSummary(int tick, int winner) {
        GameEngine.log("RWX_PROBE_END tick=" + tick + " winner=" + winner
                + " participants=" + participants.size());
        TreeMap<String, Integer> byCat = new TreeMap<String, Integer>();
        for (Map.Entry<String, TypeStat> e : stats.entrySet()) {
            TypeStat s = e.getValue();
            GameEngine.log("RWX_PROBE_SUMMARY team=" + s.team + " reg=" + s.reg + " type=" + s.type + " cat=" + s.cat
                    + " count=" + s.count + " first=" + s.first + " last=" + s.last + " buckets=" + bucketText(s));
            Integer old = byCat.get(s.team + "|" + s.cat);
            byCat.put(s.team + "|" + s.cat, Integer.valueOf(old == null ? s.count : old.intValue() + s.count));
        }
        for (Map.Entry<String, Integer> e : byCat.entrySet()) {
            GameEngine.log("RWX_PROBE_SUMMARY_CAT group=" + e.getKey() + " count=" + e.getValue());
        }
    }

    private static String bucketText(TypeStat s) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Integer, Integer> e : s.buckets.entrySet()) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(e.getKey().intValue() * 1000).append('-').append(e.getKey().intValue() * 1000 + 999)
                    .append(':').append(e.getValue());
        }
        return sb.toString();
    }

    // RWX：bar/roster 快照每单位×每动作×8 个反射式查询，是 3000-tick 周期 ~90ms 停顿的元凶
    // （RWX_SPIKE worst=commands 且全部子段 <0.5ms = 成本在探针自己身上）。诊断使命已完成，
    // 默认关闭；环境变量 RWX_BAR=1 再打开。
    private static final boolean BAR = System.getenv("RWX_BAR") != null && !"0".equals(System.getenv("RWX_BAR").trim());

    private static void barSnapshot(int tick) {
        if (!ENABLED || !BAR) {
            return;
        }
        barBuf.reset();
        for (PlayerTeam team : PlayerTeam.getTeams()) {
            if (team == null || team.teamId < 0 || team.isTeamWipedOut) {
                continue;
            }
            if (!(team instanceof AIController)) {
                continue;
            }
            LinkedHashMap<String, String> seen = new LinkedHashMap<String, String>();
            Iterator it = BaseUnit.bE.iterator();
            while (it.hasNext()) {
                BaseUnit unit = (BaseUnit) it.next();
                if (unit == null || unit.isDead || unit.team != team) {
                    continue;
                }
                ArrayList actions;
                try {
                    actions = unit.getAvailableActions();
                } catch (Throwable t) {
                    continue;
                }
                if (actions == null) {
                    continue;
                }
                for (Object o : actions) {
                    if (!(o instanceof AbstractUnitAction)) {
                        continue;
                    }
                    AbstractUnitAction action = (AbstractUnitAction) o;
                    String id = actionId(action);
                    if (seen.containsKey(id)) {
                        continue;
                    }
                    seen.put(id, "");
                    boolean avail = false;
                    boolean afford = false;
                    boolean locked = false;
                    boolean hidden = false;
                    boolean aiDisabled = false;
                    String disp = "-";
                    UnitType target = null;
                    try {
                        avail = action.isAvailable(unit);
                    } catch (Throwable t) {
                    }
                    try {
                        afford = action.canAfford(unit, false);
                    } catch (Throwable t) {
                    }
                    try {
                        locked = action.isLockedAndDisabled();
                    } catch (Throwable t) {
                    }
                    try {
                        hidden = action.unitAction != null && action.unitAction.a(unit);
                    } catch (Throwable t) {
                    }
                    try {
                        aiDisabled = action.isAiDisabled(unit);
                    } catch (Throwable t) {
                    }
                    try {
                        target = action.getUnitType();
                    } catch (Throwable t) {
                    }
                    try {
                        disp = String.valueOf(action.getDisplayName(unit));
                    } catch (Throwable t) {
                    }
                    StringBuilder barLine = new StringBuilder("RWX_PROBE_BAR tick=" + tick + " team=" + team.teamId
                            + " action=" + id + " type=" + (target == null ? "-" : typeName(target))
                            + " reg=" + (target == null ? "-" : regOf(target))
                            + " by=" + regOf(unit.unitType)
                            + " disp=" + disp + " avail=" + (avail ? 1 : 0)
                            + " afford=" + (afford ? 1 : 0) + " locked=" + (locked ? 1 : 0)
                            + " hidden=" + (hidden ? 1 : 0) + " aiDisabled=" + (aiDisabled ? 1 : 0));
                    GameEngine.log(barLine.toString());
                    appendLine(barBuf, barLine);
                    if (seen.size() >= BAR_LIMIT) {
                        break;
                    }
                }
                if (seen.size() >= BAR_LIMIT) {
                    break;
                }
            }
            int nAvail = 0;
            int nAfford = 0;
            int nLocked = 0;
            int nHidden = 0;
            int nAiOff = 0;
            String[] lines = barBuf.text().split("\n");
            for (String line : lines) {
                if (line.indexOf("team=" + team.teamId + " ") < 0 || line.indexOf("tick=" + tick + " ") < 0) {
                    continue;
                }
                if (line.indexOf("avail=1") >= 0) nAvail++;
                if (line.indexOf("afford=1") >= 0) nAfford++;
                if (line.indexOf("locked=1") >= 0) nLocked++;
                if (line.indexOf("hidden=1") >= 0) nHidden++;
                if (line.indexOf("aiDisabled=1") >= 0) nAiOff++;
            }
            appendLine(barTrend, "RWX_PROBE_BAR_TREND tick=" + tick + " team=" + team.teamId
                    + " actions=" + seen.size() + " avail=" + nAvail + " afford=" + nAfford
                    + " locked=" + nLocked + " hidden=" + nHidden + " aiDisabled=" + nAiOff);
            GameEngine.log("RWX_PROBE_BAR_TOTAL tick=" + tick + " team=" + team.teamId
                    + " actions=" + seen.size());
        }
    }

    private static String actionId(AbstractUnitAction action) {
        try {
            ActionId id = action.getActionId();
            if (id != null && id.getId() != null) {
                return id.getId();
            }
        } catch (Throwable t) {
        }
        return action.getClass().getSimpleName();
    }

    private static String allyLetter(PlayerTeam team) {
        int id = team.teamColorId;
        if (id >= 0 && id < 26) {
            return String.valueOf((char) ('A' + id));
        }
        return "-";
    }

    private static String regOf(UnitType unitType) {
        try {
            if (unitType instanceof CustomUnitConfig) {
                return String.valueOf(((CustomUnitConfig) unitType).v());
            }
            // 原版单位是 UnitTypeEnum 的常量，且每个常量都带匿名类体，
            // getClass().getSimpleName() 于是返回空串（旧报告里 "reg=" 后面就是空的）。
            // 取枚举名当注册名：extractor / t1Tank / commander 这些，稳定且能区分。
            if (unitType instanceof UnitTypeEnum) {
                return ((UnitTypeEnum) unitType).name();
            }
            String simple = unitType.getClass().getSimpleName();
            return simple == null || simple.isEmpty() ? String.valueOf(unitType) : simple;
        } catch (Throwable t) {
            return "?";
        }
    }

    private static String fileOf(UnitType unitType) {
        try {
            if (unitType instanceof CustomUnitConfig) {
                String path = ((CustomUnitConfig) unitType).sourceFilePath;
                if (path != null) {
                    return path;
                }
            }
        } catch (Throwable t) {
        }
        return "-";
    }

    private static String typeName(UnitType unitType) {
        try {
            return String.valueOf(unitType.getUnitTypeDescriptionShort());
        } catch (Throwable t) {
            return unitType.getClass().getSimpleName();
        }
    }

    private static String category(UnitType unitType) {
        if (!(unitType instanceof CustomUnitConfig)) {
            return "vanilla";
        }
        CustomUnitConfig config = (CustomUnitConfig) unitType;
        if (config.isBuildingUnit) {
            if (config.generationTemplate != null) {
                return "bld-resource";
            }
            if (config.isFactory) {
                return "bld-factory";
            }
            if (config.canAttack) {
                return "bld-turret";
            }
            if (config.isBuilder) {
                return "bld-builder";
            }
            return "bld-other";
        }
        if (config.isBuilder) {
            return "unit-builder";
        }
        if (config.canAttack) {
            return "unit-combat";
        }
        return "unit-other";
    }

    private static void logPrices() {
        int custom = 0;
        for (UnitType unitType : UnitTypeEnum.ae) {
            if (!(unitType instanceof CustomUnitConfig)) {
                continue;
            }
            UnitPrice price = unitType.u();
            if (price == null) {
                continue;
            }
            custom++;
            StringBuilder sb = new StringBuilder("RWX_PROBE_PRICE ").append(unitType.getUnitTypeDescriptionShort());
            sb.append(" credits=").append(price.b);
            StoredResources storedResources = price.k;
            if (storedResources != null && storedResources.b.size > 0) {
                int n = storedResources.b.size;
                Object[] arr = storedResources.b.a();
                for (int i = 0; i < n; i++) {
                    StoredResourceEntry entry = (StoredResourceEntry) arr[i];
                    if (entry.b == 0.0d) {
                        continue;
                    }
                    sb.append(' ').append(entry.a.b).append('=').append((long) entry.b);
                }
            }
            GameEngine.log(sb.toString());
            appendLine(priceBuf, sb);
        }
        GameEngine.log("RWX_PROBE_PRICE customUnits=" + custom);
        for (UnitType unitType : UnitTypeEnum.ae) {
            if (!(unitType instanceof CustomUnitConfig)) {
                continue;
            }
            StringBuilder sb = new StringBuilder("RWX_PROBE_TYPE reg=").append(regOf(unitType))
                    .append(" name=").append(typeName(unitType))
                    .append(" file=").append(fileOf(unitType));
            GameEngine.log(sb.toString());
            appendLine(typeBuf, sb);
        }
    }
}
