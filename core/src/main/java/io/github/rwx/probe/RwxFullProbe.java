package io.github.rwx.probe;

import com.corrodinggames.rts.gameFramework.GameEngine;

/**
 * RWX 全量诊断探针。默认关闭；通过环境变量 RWX_FULL_PROBE=1 或
 * rwx-flags.txt 中的 RWX_FULL_PROBE=1 打开。
 *
 * 设计目标：一次实机能同时看到「帧间隔 / 逻辑各段 / 渲染各段 / 对象队列 /
 * AI 与触发器访问量 / GC 与堆 / 异常现场」。默认只写少量汇总行：
 *   - RWX_FULL_SUM   每 RWX_FULL_INTERVAL 帧（默认 60）一行窗口汇总
 *   - RWX_FULL_SPIKE 单帧超过 RWX_FULL_SPIKE_MS（默认 50ms）或发生异常时一行
 *   - RWX_FULL_EX    异常栈（最多 3 层 cause，每层 18 帧）
 *   - RWX_FULL_LIFE  生命周期事件（attach/切后端/暂停/恢复/新局）
 * 全部行同时进 rwx.log 与 logs/rwx-full.log，便于和原有 RWX_PROF 行对照。
 *
 * 本类只读计数器，不改列表、不改 tick、不改随机数，不影响锁步确定性。
 */
public final class RwxFullProbe {

    private static volatile boolean enabled;
    private static volatile boolean resolved;
    private static boolean announced;
    private static int intervalFrames = 60;
    private static int spikeMs = 50;
    private static volatile String globalBackend = "unknown";
    private static volatile int globalTextures = -1;
    private static java.io.Writer fileOut;

    private static final class Frame {
        long beginNs;
        long lastBeginNs;
        long logicNs;
        long renderStartNs;
        long renderNs;
        long gapNs;
        long endNs;
        int tick = -1;
        int deltaMs;
        int objects;
        int units;
        int effects;
        int commands;
        int pending;
        int completed;
        int drawCalls;
        int quads;
        int deferredCommands;
        int atlasMisses;
        int inputEvents;
        int textures = -1;
        String backend = "unknown";
        String thread = "?";
        String phase = "frame";
        boolean exception;

        int frames;
        long winLogicNs;
        long winRenderNs;
        long winGapNs;
        long maxLogicNs;
        long maxRenderNs;
        long maxGapNs;
        int winSpikes;
    }

    private static final ThreadLocal<Frame> FRAME = new ThreadLocal<Frame>() {
        @Override
        protected Frame initialValue() {
            return new Frame();
        }
    };

    private RwxFullProbe() {
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static int parseIntEnv(String name, int fallback, int min, int max) {
        String value = trim(System.getenv(name));
        if (value.isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(value);
            return parsed < min ? min : (parsed > max ? max : parsed);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static boolean enabled() {
        if (!resolved) {
            refresh();
        }
        return enabled;
    }

    private static synchronized void refresh() {
        if (resolved) {
            return;
        }
        String env = System.getenv("RWX_FULL_PROBE");
        enabled = "1".equals(trim(env)) || RwxFlags.isOn("RWX_FULL_PROBE");
        intervalFrames = parseIntEnv("RWX_FULL_INTERVAL",
                RwxFlags.getInt("RWX_FULL_INTERVAL", 60, 1, 3600), 1, 3600);
        spikeMs = parseIntEnv("RWX_FULL_SPIKE_MS",
                RwxFlags.getInt("RWX_FULL_SPIKE_MS", 50, 1, 10000), 1, 10000);
        String flagsPath = RwxFlags.loadedFrom();
        if (enabled) {
            RwxPerf.enableFromFullProbe("RWX_FULL_PROBE");
            if (!announced) {
                announced = true;
                Runtime rt = Runtime.getRuntime();
                out("RWX_FULL_INIT enabled=1 interval=" + intervalFrames
                        + " spikeMs=" + spikeMs
                        + " flags=" + flagsPath
                        + " java=" + System.getProperty("java.version")
                        + " os=" + System.getProperty("os.name") + '/' + System.getProperty("os.arch")
                        + " cpus=" + rt.availableProcessors()
                        + " maxHeap=" + (rt.maxMemory() >> 20) + "MB");
            }
        }
        // 文件不存在或已读取成功都算已定案；只有存储根尚未就绪时才继续重试。
        if (flagsPath != null) {
            resolved = true;
        }
    }

    /** 与 RwxPerf.out 同款落盘，但独立文件，避免把全量行淹没在 prof 行里。 */
    public static synchronized void out(String line) {
        GameEngine.log(line);
        try {
            if (fileOut == null) {
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
                        new java.io.FileOutputStream(new java.io.File(dir, "rwx-full.log"), true), "UTF-8");
            }
            fileOut.write(line);
            fileOut.write('\n');
            fileOut.flush();
        } catch (Throwable ignored) {
            // 落盘失败不影响游戏
        }
    }

    public static void beginFrame(int tick, int deltaMs) {
        if (!enabled()) {
            return;
        }
        Frame f = FRAME.get();
        long now = System.nanoTime();
        if (f.lastBeginNs != 0L) {
            f.gapNs = now - f.lastBeginNs;
        }
        f.lastBeginNs = now;
        f.beginNs = now;
        f.endNs = 0L;
        f.logicNs = 0L;
        f.renderStartNs = 0L;
        f.renderNs = 0L;
        f.tick = tick;
        f.deltaMs = deltaMs;
        f.objects = 0;
        f.units = 0;
        f.effects = 0;
        f.commands = 0;
        f.pending = 0;
        f.completed = 0;
        f.drawCalls = 0;
        f.quads = 0;
        f.deferredCommands = 0;
        f.atlasMisses = 0;
        f.inputEvents = 0;
        f.backend = globalBackend;
        f.textures = globalTextures;
        f.thread = Thread.currentThread().getName();
        f.phase = "frame";
        f.exception = false;
    }

    public static void phase(String phase) {
        if (!enabled()) {
            return;
        }
        FRAME.get().phase = safe(phase);
    }

    /** GameLogic.update 正常结束时调用；所有参数都是纯计数，读取不改状态。 */
    public static void endLogic(int tick, int objects, int units, int effects,
                                int commands, int pending, int completed) {
        if (!enabled()) {
            return;
        }
        Frame f = FRAME.get();
        if (f.beginNs == 0L) {
            beginFrame(tick, 0);
        }
        f.tick = tick;
        f.endNs = System.nanoTime();
        f.logicNs = f.endNs - f.beginNs;
        f.objects = objects;
        f.units = units;
        f.effects = effects;
        f.commands = commands;
        f.pending = pending;
        f.completed = completed;
        f.phase = "logicDone";
    }

    /** update 因 outcomeFence 提前返回时也留一条相位痕迹，避免误判成逻辑没跑。 */
    public static void skipLogic(String reason) {
        if (!enabled()) {
            return;
        }
        Frame f = FRAME.get();
        f.phase = "logicSkipped:" + safe(reason);
    }

    public static void beginRender(int tick) {
        if (!enabled()) {
            return;
        }
        Frame f = FRAME.get();
        if (f.beginNs == 0L) {
            beginFrame(tick, 0);
        }
        f.tick = tick;
        f.renderStartNs = System.nanoTime();
        f.phase = "render";
    }

    public static void endRender(int tick) {
        if (!enabled()) {
            return;
        }
        Frame f = FRAME.get();
        if (f.renderStartNs != 0L) {
            f.renderNs = System.nanoTime() - f.renderStartNs;
        }
        f.tick = tick;
        f.phase = "renderDone";
    }

    /** 平台帧循环在 engine.gameLoop 返回后调用；这里才真正写汇总/尖峰。 */
    public static void endFrame(String reason) {
        if (!enabled()) {
            return;
        }
        Frame f = FRAME.get();
        if (f.beginNs == 0L) {
            return;
        }
        f.endNs = System.nanoTime();
        if (f.renderStartNs != 0L && f.renderNs == 0L) {
            f.renderNs = f.endNs - f.renderStartNs;
        }
        long total = f.endNs - f.beginNs;

        f.frames++;
        f.winLogicNs += f.logicNs;
        f.winRenderNs += f.renderNs;
        f.winGapNs += f.gapNs;
        if (f.logicNs > f.maxLogicNs) f.maxLogicNs = f.logicNs;
        if (f.renderNs > f.maxRenderNs) f.maxRenderNs = f.renderNs;
        if (f.gapNs > f.maxGapNs) f.maxGapNs = f.gapNs;

        boolean spike = total > (long) spikeMs * 1000000L;
        if (spike) {
            f.winSpikes++;
        }
        if (spike || f.exception) {
            out(new StringBuilder("RWX_FULL_SPIKE reason=").append(safe(reason))
                    .append(metrics(f)).append(topSections(f)).toString());
        }
        if (f.frames >= intervalFrames) {
            out(new StringBuilder("RWX_FULL_SUM frames=").append(f.frames)
                    .append(" logicAvg=").append(ms(f.winLogicNs / f.frames))
                    .append(" logicMax=").append(ms(f.maxLogicNs))
                    .append(" renderAvg=").append(ms(f.winRenderNs / f.frames))
                    .append(" renderMax=").append(ms(f.maxRenderNs))
                    .append(" gapAvg=").append(ms(f.winGapNs / f.frames))
                    .append(" gapMax=").append(ms(f.maxGapNs))
                    .append(" spikes=").append(f.winSpikes)
                    .append(metrics(f)).append(topSections(f)).toString());
            f.frames = 0;
            f.winLogicNs = 0L;
            f.winRenderNs = 0L;
            f.winGapNs = 0L;
            f.maxLogicNs = 0L;
            f.maxRenderNs = 0L;
            f.maxGapNs = 0L;
            f.winSpikes = 0;
        }
        f.beginNs = 0L;
        f.renderStartNs = 0L;
        f.renderNs = 0L;
        f.phase = "idle";
    }

    public static void onException(Throwable error, String where) {
        if (!enabled() || error == null) {
            return;
        }
        Frame f = FRAME.get();
        f.exception = true;
        StringBuilder sb = new StringBuilder("RWX_FULL_EX where=").append(safe(where))
                .append(metrics(f)).append(topSections(f))
                .append(" ex=").append(error.getClass().getName())
                .append(" msg=").append(safe(String.valueOf(error.getMessage())));
        out(sb.toString());
        Throwable current = error;
        int depth = 0;
        while (current != null && depth < 3) {
            StackTraceElement[] trace = current.getStackTrace();
            int limit = Math.min(trace.length, 18);
            for (int i = 0; i < limit; i++) {
                out("RWX_FULL_EX_AT depth=" + depth + " " + trace[i].toString());
            }
            current = current.getCause();
            depth++;
        }
    }

    public static void onLifecycle(String event, String detail) {
        if (!enabled()) {
            return;
        }
        Frame f = FRAME.get();
        out("RWX_FULL_LIFE event=" + safe(event)
                + " detail=" + safe(detail)
                + " thread=" + Thread.currentThread().getName()
                + " tick=" + f.tick);
    }

    public static void setRenderBackend(String backend, int textures) {
        globalBackend = safe(backend);
        globalTextures = textures;
        Frame f = FRAME.get();
        f.backend = globalBackend;
        f.textures = textures;
    }

    public static void setRenderBackend(String backend) {
        setRenderBackend(backend, globalTextures);
    }

    public static void addDrawStats(int calls, int quads) {
        if (!enabled()) {
            return;
        }
        Frame f = FRAME.get();
        f.drawCalls += Math.max(0, calls);
        f.quads += Math.max(0, quads);
    }

    public static void addDeferredCommands(int commands) {
        if (!enabled()) {
            return;
        }
        Frame f = FRAME.get();
        f.deferredCommands += Math.max(0, commands);
    }

    public static void addAtlasMiss(int misses) {
        if (!enabled()) {
            return;
        }
        Frame f = FRAME.get();
        f.atlasMisses += Math.max(0, misses);
    }

    public static void addInputEvent(int events) {
        if (!enabled()) {
            return;
        }
        Frame f = FRAME.get();
        f.inputEvents += Math.max(0, events);
    }

    private static String metrics(Frame f) {
        long aiHits = 0L;
        for (int i = 0; i < RwxPerf.aiHits.length; i++) {
            aiHits += RwxPerf.aiHits[i];
        }
        return " tick=" + f.tick
                + " delta=" + f.deltaMs + "ms"
                + " gap=" + ms(f.gapNs)
                + " total=" + ms(f.endNs - f.beginNs)
                + " logic=" + ms(f.logicNs)
                + " render=" + ms(f.renderNs)
                + " thread=" + f.thread
                + " phase=" + f.phase
                + " backend=" + f.backend
                + " objects=" + f.objects
                + " units=" + f.units
                + " effects=" + f.effects
                + " commands=" + f.commands
                + " pending=" + f.pending
                + " completed=" + f.completed
                + " drawCalls=" + f.drawCalls
                + " quads=" + f.quads
                + " deferred=" + f.deferredCommands
                + " textures=" + f.textures
                + " atlasMiss=" + f.atlasMisses
                + " input=" + f.inputEvents
                + " aiHits=" + aiHits
                + " trig=" + RwxPerf.trigFramesTick + '/' + RwxPerf.trigEvalTick
                + "/cb" + RwxPerf.cbVisitsTick
                + " heap=" + heapMb() + "MB";
    }

    private static String topSections(Frame f) {
        StringBuilder sb = new StringBuilder(" logicTop=");
        boolean any = false;
        for (int i = 0; i < 38; i++) {
            long ns = RwxPerf.frameSectionNs(i);
            if (ns >= 200000L) {
                sb.append(RwxPerf.sectionName(i)).append('=').append(ms(ns)).append('|');
                any = true;
            }
        }
        if (!any) {
            sb.append("none");
        } else {
            sb.setLength(sb.length() - 1);
        }
        sb.append(" renderTop=");
        any = false;
        for (int i = 0; i < 12; i++) {
            long ns = RwxPerf.renderFrameSectionNs(i);
            if (ns >= 200000L) {
                sb.append(RwxPerf.renderSectionName(i)).append('=').append(ms(ns)).append('|');
                any = true;
            }
        }
        if (!any) {
            sb.append("none");
        } else {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    private static String ms(long ns) {
        return String.format(java.util.Locale.US, "%.2f", ns / 1000000.0);
    }

    private static long heapMb() {
        try {
            Runtime rt = Runtime.getRuntime();
            return (rt.totalMemory() - rt.freeMemory()) >> 20;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return "";
        }
        String clean = value.replace('\r', ' ').replace('\n', ' ');
        return clean.length() > 240 ? clean.substring(0, 240) : clean;
    }
}
