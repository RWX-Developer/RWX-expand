package io.github.rwx.probe;

import com.corrodinggames.rts.gameFramework.GameEngine;

/**
 * RWX 帧剖析器。默认关闭，RWX_PROF=1 开启。
 * 逻辑线程每 600 tick、渲染线程每 600 帧各写一行 avg/max 毫秒到 rwx.log。
 * 两条线程分开计数，互不干扰。开销：每段两次 nanoTime，可忽略。
 */
public final class RwxPerf {

    private static boolean isEnv0(String name) {
        String v = System.getenv(name);
        return v != null && "0".equals(v.trim());
    }

    // RWX：桌面客户端里 GameEngine.log 走 System.err，用 start 启动就没人收集；
    // 剖析行额外落一份到 logs/rwx-prof.log（工作目录相对路径，即整合包目录）。
    private static java.io.Writer fileOut;

    public static synchronized void out(String line) {
        GameEngine.log(line);
        try {
            if (fileOut == null) {
                // RWX：与 RwxProbe 同一套落点逻辑：引擎数据根/logs，取不到回落相对 logs
                java.io.File dir = null;
                try {
                    java.io.File root = io.github.rwx.LegacyAssetBridge.storageRootDir();
                    if (root != null && root.isDirectory()) {
                        dir = new java.io.File(root, "logs");
                    }
                } catch (Throwable ignored) {
                }
                if (dir == null) {
                    dir = new java.io.File("logs");
                }
                if (!dir.isDirectory()) {
                    dir.mkdirs();
                }
                fileOut = new java.io.OutputStreamWriter(
                        new java.io.FileOutputStream(new java.io.File(dir, "rwx-prof.log"), true), "UTF-8");
            }
            fileOut.write(line);
            fileOut.write("\n");
            fileOut.flush();
        } catch (Throwable th) {
            // 落盘失败不影响游戏
        }
    }

    public static final int COMMANDS = 0;
    public static final int OBJECTS = 1;
    public static final int SPATIAL = 2;
    public static final int COLLISION = 3;
    public static final int CUSTOM = 4;
    public static final int TEAMS = 5;
    public static final int EFFECTS = 6;
    public static final int MINIMAP = 7;
    public static final int PATHING = 8;
    public static final int TEAM_AI = 9;
    public static final int TEAM_DEFECT = 10;
    public static final int TRIGGERS = 11;
    public static final int AI_LOGIC = 12;
    public static final int AI_CORE = 13;
    public static final int AI_ZONES = 14;
    public static final int AI_SPECIAL = 15;
    public static final int AI_MODE = 16;
    public static final int TOTAL = 17;
    public static final int FX_MGR = 18;
    public static final int FX_CLOUD = 19;
    public static final int AI_SCAN = 20;
    public static final int AI_STRAT = 21;
    public static final int AI_ATK = 22;
    public static final int AI_TRYB = 23;
    public static final int AI_BLD = 24;
    public static final int C_DRAIN = 25;
    public static final int C_EXEC = 26;
    public static final int C_REPLAY = 27;
    public static final int C_MODS = 28;
    public static final int C_TEAMSG = 29;
    public static final int C_FOG = 30;
    public static final int AI_FAB = 31;
    public static final int AI_GBB = 32;
    public static final int AI_TBU = 33;
    public static final int C_NET = 34;
    public static final int AI_CANB = 35;
    public static final int AI_CNT = 36;
    public static final int AI_FCR = 37;
    private static final int SECTIONS = 38;
    private static final String[] NAMES = {
            "commands", "objects", "spatial", "collision", "custom",
            "teams", "effects", "minimap", "pathing", "teamAI", "defeat",
            "triggers", "aiLogic", "aiCore", "aiZones", "aiSpecial", "aiMode", "TOTAL", "fxMgr", "fxCloud",
            "aiScan", "aiStrat", "aiAtk", "aiTryB", "aiBld",
            "cDrain", "cExec", "cReplay", "cMods", "cTeamSG", "cFog",
            "aiFAB", "aiGBB", "aiTBU", "cNet",
            "aiCanB", "aiCnt", "aiFCR"};

    // RWX：剖析默认开启（老师双击 RWX.exe 时没有环境变量，上一局数据就是这么丢的）；
    // 关掉用 RWX_PROF=0。每 600 tick/帧各写一行到 logs\rwx-prof.log，尖峰帧多一行，量很小。
    private static final boolean ON = !isEnv0("RWX_PROF");

    // 逻辑线程
    private static final long[] acc = new long[SECTIONS];
    private static final long[] max = new long[SECTIONS];
    // RWX：本帧各段耗时（beginFrame 清零），尖峰帧当场写日志用
    private static final long[] frameAcc = new long[SECTIONS];
    private static final long SPIKE_NS = 50_000_000L; // 50ms

    // RWX：尖峰归因 —— 本 tick 内触发求值帧数 / 回调访问数（beginFrame 清零）
    public static long trigFramesTick;
    public static long trigEvalTick;
    public static long cbVisitsTick;
    private static long frameStart;
    private static int frames;

    // 渲染线程
    private static long renderAcc;
    private static long renderMax;
    private static int renderFrames;
    private static long renderGapAcc;
    private static long renderGapMax;

    private static long gcCountLast = -1;
    private static long gcTimeLast = -1;

    /** GC 与堆占用（仅 ON 时取，一次 MXBeans 遍历，每窗口一次） */
    private static String gcInfo() {
        try {
            long c = 0;
            long t = 0;
            for (java.lang.management.GarbageCollectorMXBean b : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
                if (b.getCollectionCount() >= 0) { c += b.getCollectionCount(); }
                if (b.getCollectionTime() >= 0) { t += b.getCollectionTime(); }
            }
            Runtime rt = Runtime.getRuntime();
            long usedMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
            if (gcCountLast < 0) {
                gcCountLast = c;
                gcTimeLast = t;
                return " gc=-/-ms heap=" + usedMb + "MB";
            }
            String r = " gc=" + (c - gcCountLast) + "/" + (t - gcTimeLast) + "ms heap=" + usedMb + "MB";
            gcCountLast = c;
            gcTimeLast = t;
            return r;
        } catch (Throwable th) {
            return "";
        }
    }

    public static long beginFrame() {
        if (!ON) {
            return 0L;
        }
        java.util.Arrays.fill(frameAcc, 0L);
        trigFramesTick = 0; trigEvalTick = 0; cbVisitsTick = 0;
        frameStart = System.nanoTime();
        return frameStart;
    }

    /** 纯取时：不动 frameStart，供帧内子段计时用 */
    public static long now() {
        return ON ? System.nanoTime() : 0L;
    }

    /** 段结束：累计 now-last 到 id，返回 now 作为下一段起点 */
    public static long mark(int id, long last) {
        if (!ON || last == 0L) {
            return last;
        }
        long now = System.nanoTime();
        long d = now - last;
        acc[id] += d;
        frameAcc[id] += d;
        if (d > max[id]) {
            max[id] = d;
        }
        return now;
    }

    /** 帧末调用：TOTAL 从帧起点算，够一个窗口写一行 */
    public static void endFrame(long last, int tick, int objectCount, int gid) {
        if (!ON) {
            return;
        }
        long now = System.nanoTime();
        long d = now - last;
        acc[PATHING] += d;
        if (d > max[PATHING]) {
            max[PATHING] = d;
        }
        long total = now - frameStart;
        acc[TOTAL] += total;
        if (total > max[TOTAL]) {
            max[TOTAL] = total;
        }
        // RWX：单帧超 50ms 当场把本帧各段写出来——窗口 max 只说「有尖峰」，这里给「尖峰长什么样」
        if (total > SPIKE_NS) {
            int worst = -1;
            long prevMax = 0;
            StringBuilder ss = new StringBuilder("RWX_SPIKE tick=").append(tick)
                    .append(" trigN=").append(trigFramesTick).append('/').append(trigEvalTick).append("/cb").append(cbVisitsTick)
                    .append(" objects=").append(objectCount)
                    .append(" gid=").append(gid)
                    .append(" total=").append(String.format("%.1f", total / 1000000.0)).append("ms");
            for (int i = 0; i < SECTIONS; i++) {
                if (i == TOTAL) {
                    continue;
                }
                if (frameAcc[i] >= 500_000L) { // 只写 ≥0.5ms 的段
                    ss.append(' ').append(NAMES[i]).append('=').append(String.format("%.1f", frameAcc[i] / 1000000.0));
                }
                if (frameAcc[i] > prevMax) {
                    prevMax = frameAcc[i];
                    worst = i;
                }
            }
            ss.append(" worst=").append(worst >= 0 ? NAMES[worst] : "?");
            ss.append(gcInfo());
            out(ss.toString());
        }
        frames++;
        if (tick % 600 != 0 || frames == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder("RWX_PROF tick=").append(tick)
                .append(" objects=").append(objectCount)
                .append(" frames=").append(frames)
                .append(" gid=").append(gid);
        for (int i = 0; i < SECTIONS; i++) {
            double avg = (acc[i] / 1000000.0) / frames;
            double mx = max[i] / 1000000.0;
            sb.append(' ').append(NAMES[i]).append('=')
                    .append(String.format("%.2f/%.1f", avg, mx)).append("ms");
        }
        sb.append(gcInfo());
        out(sb.toString());
        java.util.Arrays.fill(acc, 0L);
        java.util.Arrays.fill(max, 0L);
        frames = 0;
    }

    // ===== 渲染线程细分（draw() 在渲染线程里跑，不能碰逻辑侧数组）=====
    public static final int RD_LOCK = 0;     // 等 drawLock + 进入
    public static final int RD_COLLECT = 1;  // shouldDraw 收集与列表交换
    public static final int RD_SORT = 2;     // Collections.sort 渲染表
    public static final int RD_MAP = 3;      // 裁剪/水/地图瓦片
    public static final int RD_UNITS = 4;    // 单位绘制主循环（含阴影腿脚）
    public static final int RD_FX = 5;       // effectManager 各层
    public static final int RD_UI = 6;       // drawUI
    public static final int RD_TAIL = 7;     // 收尾/其余
    public static final int RD_SDRAW = 8;    // var26.d 需要重绘的单位
    public static final int RD_EDRAW = 9;    // var27.e 主绘制
    public static final int RD_LAYER = 10;   // var28.c 分层
    public static final int RD_ADRAW = 11;   // var29.a 附加层
    private static final String[] RD_NAMES = {
            "lock", "collect", "sort", "map", "units", "fx", "ui", "tail",
            "u_sdraw", "u_edraw", "u_layer", "u_adraw"};
    private static final long[] racc = new long[RD_NAMES.length];
    private static final long[] rmax = new long[RD_NAMES.length];
    private static long rLast;
    private static long rPrevBegin;
    private static boolean rActive = false;

    public static String uMode = "A_base";
    public static int renderObjects;

    public static long beginRender() {
        if (!ON) {
            return 0L;
        }
        long now = System.nanoTime();
        if (rPrevBegin != 0L) {
            long gap = now - rPrevBegin;
            renderGapAcc += gap;
            if (gap > renderGapMax) {
                renderGapMax = gap;
            }
        }
        rPrevBegin = now;
        rLast = now;
        rActive = true;
        return now;
    }

    /** 渲染段结束：now-rLast 记到 id，rLast 推到 now */
    public static void rendSection(int id) {
        if (!ON || !rActive) {
            return;
        }
        long now = System.nanoTime();
        long d = now - rLast;
        racc[id] += d;
        if (d > rmax[id]) {
            rmax[id] = d;
        }
        rLast = now;
    }

    public static void endRender(long last, int tick, int gid) {
        if (!ON || last == 0L) {
            return;
        }
        rendSection(RD_TAIL); // 帧尾段：先结算，再关开关
        rActive = false;
        long d = System.nanoTime() - last;
        renderAcc += d;
        if (d > renderMax) {
            renderMax = d;
        }
        renderFrames++;
        if (tick % 600 != 0 || renderFrames == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder("RWX_PROF_RENDER tick=" + tick
                + " frames=" + renderFrames
                + " gid=" + gid
                + " draw=" + String.format("%.2f/%.1f", (renderAcc / 1000000.0) / renderFrames, renderMax / 1000000.0) + "ms"
                + " gap=" + String.format("%.2f/%.1f", (renderGapAcc / 1000000.0) / renderFrames, renderGapMax / 1000000.0) + "ms");
        for (int i = 0; i < RD_NAMES.length; i++) {
            sb.append(' ').append(RD_NAMES[i]).append('=')
                    .append(String.format("%.2f/%.1f", (racc[i] / 1000000.0) / renderFrames, rmax[i] / 1000000.0)).append("ms");
            racc[i] = 0L;
            rmax[i] = 0L;
        }
        Runtime rt = Runtime.getRuntime();
        sb.append(" objs=").append(renderObjects).append(" umode=").append(uMode).append(" heap=").append((rt.totalMemory() - rt.freeMemory()) >> 20).append("MB");
        out(sb.toString());
        renderAcc = 0L;
        renderMax = 0L;
        renderFrames = 0;
        renderGapAcc = 0L;
        renderGapMax = 0L;
    }
}
