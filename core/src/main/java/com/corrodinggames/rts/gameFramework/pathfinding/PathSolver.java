package com.corrodinggames.rts.gameFramework.pathfinding;

import com.corrodinggames.rts.game.map.TileMap;
import com.corrodinggames.rts.game.units.UnitMovementType;
import com.corrodinggames.rts.gameFramework.GameEngine;
import com.corrodinggames.rts.gameFramework.Utility;
import com.corrodinggames.rts.gameFramework.path.PathEngine;
import com.corrodinggames.rts.gameFramework.utility.Debug;
import com.corrodinggames.rts.gameFramework.utility.Log;
import io.github.rwx.render.canvas.Paint;

import java.util.Iterator;
import java.util.LinkedList;

/* JADX INFO: renamed from: com.corrodinggames.rts.gameFramework.k.o */
/* JADX INFO: loaded from: game-lib.jar:com/corrodinggames/rts/gameFramework/k/o.class */
public final class PathSolver implements Runnable {
    private final PathEngine C;
    public byte[] b;
    public byte[] c;
    public byte[] d;
    public short[] e;
    public byte[] f;
    private Path D;
    int g;
    int h;
    int[][] l;
    byte[][] m;
    PathCostMap n;
    static LinkedList t = new LinkedList();
    static int u;
    int v;
    Thread w;
    long y;
    long z;
    boolean a = true;
    int i = 5;
    int j = 0;
    int k = 0;
    final PathOpenListPool o = new PathOpenListPool();
    final PathOpenListPool p = new PathOpenListPool();
    final PathPoint q = new PathPoint();
    final PathPoint r = new PathPoint();
    public volatile boolean s = true;
    // RWX：RWX_PATH_STATS=1 时每 100 次不可达汇总一行（走法与 DRAW_STATS 一致，默认零开销）
    static final boolean PATH_STATS = System.getenv("RWX_PATH_STATS") != null;
    static int unreachableCount = 0;
    static int solvedCount = 0;
    Object x = new Object();
    Object A = new Object();
    Paint B = new Paint();

    public void a(Path path) {
        if (!this.s) {
            throw new RuntimeException("setupNewPath: last path not yet finished");
        }
        this.s = false;
        a(path.movementType, path);
        path.isSolved = true;
        this.D = path;
    }

    public void a() {
        if (this.w == null) {
            throw new RuntimeException("thread==null");
        }
        synchronized (this.x) {
            this.x.notifyAll();
        }
    }

    @Override // java.lang.Runnable
    public void run() {
        GameEngine.setupUncaughtExceptionHandler();
        while (this.a) {
            synchronized (this.x) {
                if (this.D == null) {
                    try {
                        this.x.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            if (this.D != null) {
                b();
            }
        }
    }

    static {
        t.add(new PathPoint((short) -9, (short) -9));
        t.add(new PathPoint((short) -9, (short) -9));
        t.add(new PathPoint((short) -9, (short) -9));
        u = 0;
    }

    // RWX：默认走新求解器；-Drwx.pathing=1 回退旧实现。首次调用打一行标记，方便日志归因。
    private static final java.util.concurrent.atomic.AtomicBoolean RWX_MODE_LOGGED = new java.util.concurrent.atomic.AtomicBoolean(false);
    private static boolean rwxUseSolver2() {
        // RWX：默认回到旧求解器。新求解器会绕开 Command 里的走廊预规划（rwxPlanCorridor），
        // 且在大图上单条路径平均扩展 2200 节点，实测把帧拖垮；要实验时显式 -Drwx.pathing=2。
        String rwxMode = System.getProperty("rwx.pathing", "1");
        boolean rwxUse2 = !"1".equals(rwxMode == null ? "" : rwxMode.trim());
        if (RWX_MODE_LOGGED.compareAndSet(false, true)) {
            io.github.rwx.probe.RwxPerf.out("RWX_PATH_MODE solver=" + (rwxUse2 ? "2" : "1") + " prop=" + rwxMode);
        }
        return rwxUse2;
    }

    public void b() {
        LinkedList linkedListE;
        if (this.D instanceof DynamicUnitPath) {
            f();
            linkedListE = t;
        } else {
            // RWX：-Drwx.pathing=2 走并行新求解器（A* + 边界安全的吸附策略），默认仍用旧实现。
            linkedListE = rwxUseSolver2() ? RwxPathSolver2.solve(this, this.D) : e();
            if (PATH_STATS) {
                solvedCount++;
            }
        }
        synchronized (this.C.G) {
            this.D.f();
            this.b = null;
            this.c = null;
            this.d = null;
            this.e = null;
            this.f = null;
            this.D.a(linkedListE);
            this.D = null;
            this.s = true;
            this.C.G.notifyAll();
        }
    }

    /* JADX INFO: Access modifiers changed from: package-private */
    public PathSolver(PathEngine pathEngine) {
        this.C = pathEngine;
        int i = u;
        u = i + 1;
        this.v = i;
    }

    public synchronized void c() {
        if (this.w != null) {
            throw new RuntimeException("thread!=null");
        }
        this.w = new Thread(this);
        this.w.setName("PathSolver-" + this.v);
        this.w.setPriority(10);
        this.w.setDaemon(true);
        this.w.start();
    }

    public void a(TileMap tileMap) {
        this.g = tileMap.groundLayer.widthTiles;
        this.h = tileMap.groundLayer.heightTiles;
        this.l = new int[2][this.g * this.h];
        this.m = new byte[2][this.g * this.h];
        d();
    }

    public void d() {
        int i = (this.g * this.h * 42) + 1;
        this.i += i;
        if (this.i > Integer.MAX_VALUE - i || this.i < 0 || 0 != 0) {
            this.i = 5;
            for (int i2 = 0; i2 < 2; i2++) {
                for (int i3 = 0; i3 < this.g; i3++) {
                    for (int i4 = 0; i4 < this.h; i4++) {
                        this.l[i2][(i3 * this.h) + i4] = -1;
                    }
                }
            }
        }
    }

    public final int a(int i, int i2) {
        if (this.b[(i * this.h) + i2] == -1 || this.c[(i * this.h) + i2] == -1 || this.d[(i * this.h) + i2] == -1) {
            return -1;
        }
        return this.b[(i * this.h) + i2] + this.c[(i * this.h) + i2] + (this.d[(i * this.h) + i2] * 10);
    }

    public void a(UnitMovementType unitMovementType, Path path) {
        if (unitMovementType == null) {
            throw new RuntimeException("MovementType is null");
        }
        PathCostMap pathCostMapA = this.C.a(unitMovementType);
        if (pathCostMapA == null) {
            throw new RuntimeException("Could not get costs for:" + unitMovementType.toString());
        }
        this.n = pathCostMapA;
        this.b = path.y;
        this.c = path.z;
        this.d = path.A;
        this.e = path.B;
        this.f = path.C;
        if (this.b == null) {
            throw new RuntimeException("linkToPath failed mapCosts_mapCosts is null");
        }
        if (this.c == null) {
            throw new RuntimeException("linkToPath failed mapCosts_buildingCosts is null");
        }
        if (this.d == null) {
            throw new RuntimeException("linkToPath failed mapCosts_objectCosts is null");
        }
    }

    public void a(UnitMovementType unitMovementType, short s, short s2, Float f) {
        if (PathEngine.a) {
            Log.d("RustedWarfare", "path start is:" + ((int) s) + "," + ((int) s2));
        }
        PathPoint pathPoint = new PathPoint(s, s2);
        pathPoint.a(this, (byte) 0, this.i);
        if (f == null) {
            pathPoint.a(this, (byte) 0, (byte) 0);
            pathPoint.b(this, (byte) 0, true);
        } else {
            pathPoint.a(this, (byte) 0, f.floatValue());
            pathPoint.b(this, (byte) 0, true);
        }
        pathPoint.a(this, (byte) 0, false);
    }

    public void a(short s, short s2, short s3) {
        if (PathEngine.a) {
            Log.d("RustedWarfare", "path end is:" + ((int) s) + "," + ((int) s2) + " size:" + ((int) s3));
        }
        PathPoint pathPoint = new PathPoint(s, s2);
        pathPoint.a(this, (byte) 1, (byte) 0);
        pathPoint.b(this, (byte) 1, true);
        pathPoint.a(this, (byte) 1, this.i);
        pathPoint.a(this, (byte) 1, false);
    }

    static int b(int i, int i2) {
        if (i == i2) {
            return 0;
        }
        int i3 = i - i2;
        if (i3 < 0) {
            i3 = -i3;
        }
        if (i3 > 4) {
            i3 = 8 - i3;
        }
        if (i3 == 1) {
            return 4;
        }
        return i3 == 2 ? 21 : 25;
    }

    static int c(int i, int i2) {
        if (i == i2) {
            return 0;
        }
        int iAbs = Math.abs(i - i2);
        if (iAbs > 4) {
            iAbs = 8 - iAbs;
        }
        if (iAbs == 1) {
            return 4;
        }
        if (iAbs == 2) {
            return 21;
        }
        if (iAbs == 3) {
            return 4;
        }
        return (iAbs == 4 || iAbs == 5) ? 0 : 25;
    }

    // RWX：假终点（求解器自己选的「最近可达点」）离真目标超过这么多格，就不再开过去。
    // 触发场景：运输船装了陆军后被派到岸边/陆地上，WATER 图上陆地全 -1，全图扫描把终点
    // 丢到地图边缘，船一路开到边界停住。宁可原地等冷却，也不要横穿全图。
    private static final int RWX_FAKE_END_MAX_TILES = 16;
    // RWX：clearance 惩罚倍数（越大越远离障碍；1=原版）
    private static final int RWX_CLEARANCE_WEIGHT = 6;
    private static int RWX_FAKEEND_LOG_N = 0;
    public LinkedList e() {
        short s;
        int n;
        int n2;
        int n3;
        int n4;
        int n5;
        if (PathEngine.l && !GameEngine.getInstance().isMenuBackgroundMap) {
            Debug.startMethodTracing((String) "pathTrace", (int) 110000000);
        }
        Path path = this.D;
        int n6 = path.p ? 7 : 1;
        int n7 = path.q;
        if (PathEngine.a) {
            Log.d("RustedWarfare", "starting path for:" + this.n.a.toString());
        }
        PathPoint pathPoint = this.r;
        PathPoint pathPoint2 = this.q;
        short s2 = (short) this.h;
        int n8 = this.g;
        this.y = GameEngine.getCurrentTimeMillis();
        short s3 = path.startTileX;
        short s4 = path.startTileY;
        boolean bl = path.k;
        this.d();
        this.a(path.movementType, path.startTileX, path.startTileY, path.j);
        int n9 = path.endTileX;
        int n10 = path.endTileY;
        short s5 = path.n;
        LinkedList linkedList = new LinkedList();
        // RWX：本次尝试重置不可达标记（超时重排队后可能地形已通，不能沿用上次的结论）
        path.endUnreachable = false;
        // RWX：目标在图外——直接判不可达，不做「钳到边界再走过去」的补救。
        if (path.rwxTargetOutOfMap) {
            path.endUnreachable = true;
            linkedList.clear();
            return linkedList;
        }
        // RWX：-Drwx.astar=1 时走自研 A*（默认开启，失败自动回退原求解器）。
        // 起点/终点取本次求解用的 s3,s4（起点）与 n9,n10（终点）。
        if (!"0".equals(System.getProperty("rwx.astar", "1").trim())) {
            LinkedList rwxA = RwxAStar.find(this, s3, s4, n9, n10);
            if (rwxA != null && rwxA.size() >= 1) {
                return rwxPruneFar(rwxA, n9, n10);
            }
        }
        if (s3 == n9 && s4 == n10) {
            if (PathEngine.a) {
                Log.d("RustedWarfare", "no point pathing when start=end");
            }
            linkedList.clear();
            linkedList.add((Object) new PathPoint((short) n9, (short) n10));
            return linkedList;
        }
        if (this.n.a.equals((Object) UnitMovementType.NONE)) {
            if (PathEngine.a) {
                Log.d("RustedWarfare", "no point pathing for none");
            }
            linkedList.clear();
            return linkedList;
        }
        int n11 = 0;
        int n12 = n9;
        int n13 = n10;
        short s6 = s5;
        if (this.e != null) {
            int n14;
            short[] sArray = this.e;
            n5 = sArray[s3 * s2 + s4];
            n4 = 1;
            if (n5 == -1) {
                n4 = 0;
            } else {
                for (n14 = (int) (n9 - s5); n14 <= n9 + s5; n14 = (short) (n14 + 1)) {
                    for (n3 = (short) (n10 - s5); n3 <= n10 + s5; n3 = (short) (n3 + 1)) {
                        if (n14 < 0 || n14 >= n8 || n3 < 0 || n3 >= s2 || n5 != sArray[n14 * s2 + n3]) continue;
                        n4 = 0;
                    }
                }
            }
            if (n4 != 0) {
                float f;
                int n15;
                if (PathEngine.a) {
                    Log.d("RustedWarfare", "end is blocked on isolated groups");
                }
                // RWX：目标落在另一个孤立地形组 = 这个 movementType 没有任何地形能抵达目的地，
                // 下面只是「走到最近的地方」，不是真的到了。标记出来让单位别再无限重寻路。
                // RWX：目标落在另一个孤立地形组＝这个兵种根本到不了。
                // 原逻辑会在这里找「最近的同组格子」当假终点，而且找不到时从全图 (0,0) 开始扫，
                // 同距离先到先得——落点因此被系统性推到地图西北角（老师实测到的现象）。
                // 现在直接判不可达，返回空路径，不再改终点。
                path.endUnreachable = true;
                linkedList.clear();
                return linkedList;
            }
        }
        boolean bl2 = true;
        block6:
        for (n5 = (int) (n12 - s6); n5 <= n12 + s6; n5 = (short) (n5 + 1)) {
            for (n4 = (int) (n13 - s6); n4 <= n13 + s6; n4 = (short) (n4 + 1)) {
                if (n5 < 0 || n5 >= n8 || n4 < 0 || n4 >= s2 || this.a(n5, n4) == -1) continue;
                bl2 = false;
                break block6;
            }
        }
        if (bl2) {
            // RWX：目标半径内一格都过不去（同上，走的是最近可达点，不是目的地）
            // RWX：目标半径内一格都走不过去＝这里到不了。原逻辑在这里 ±9 格找最近的能走格，
            // 兜底还会从 (0,0) 全图扫一遍，同距离先到先得——落点于是被推到西北角。
            // 现在直接判不可达，返回空路径。
            path.endUnreachable = true;
            linkedList.clear();
            return linkedList;
        }
        if (PathEngine.a && (n12 != n9 || n13 != n10)) {
            Log.d("RustedWarfare", "Moved end to fakeEndX:" + n12 + " fakeEndY:" + n13);
        }
        // RWX：假终点离真目标太远 = 这个 movementType 根本到不了目的地。
        // 原来会一路开到那个「最近可达点」（常常是地图边界），到了再停住，看起来就是船自己跑去撞墙。
        if (n12 != n9 || n13 != n10) {
            int rwxDx = n12 - n9;
            int rwxDy = n13 - n10;
            if (rwxDx * rwxDx + rwxDy * rwxDy > RWX_FAKE_END_MAX_TILES * RWX_FAKE_END_MAX_TILES) {
                if (RWX_FAKEEND_LOG_N < 20) {
                    RWX_FAKEEND_LOG_N++;
                    io.github.rwx.probe.RwxPerf.out("RWX_FAKEEND abandon dist=" + (int) Math.sqrt((double) (rwxDx * rwxDx + rwxDy * rwxDy)) + " realEnd=" + n9 + "," + n10 + " fakeEnd=" + n12 + "," + n13 + " type=" + path.movementType);
                }
                linkedList.clear();
                return linkedList;
            }
        }
        this.o.a(n12, n13);
        this.o.a(0, s3, s4);
        this.p.a(s3, s4);
        this.a((short) n12, (short) n13, s6);
        this.p.a(0, (short) n12, (short) n13);
        bl2 = false;
        n5 = 0;
        n4 = -1;
        short s8 = -1;
        short s9 = -1;
        short s10 = -1;
        int n17 = 400;
        n2 = 0;
        while (!bl2) {
            byte by;
            boolean bl3;
            PathOpenListPool pathOpenListPool;
            PathOpenListNode pathOpenListNode;
            ++n2;
            if (path.w) {
                linkedList.clear();
                return linkedList;
            }
            ++n11;
            if (n17 > 0) {
                --n17;
            } else {
                n5 = n5 == 0 ? 1 : 0;
            }
            byte by2 = 0;
            if (n5 != 0) {
                by2 = 1;
            }
            if ((pathOpenListNode = (pathOpenListPool = n5 == 0 ? this.o : this.p).c()) == null) {
                if (n5 != 0) continue;
                if (!PathEngine.a) break;
                Log.d("RustedWarfare", "listNode==null for normal side ending path");
                break;
            }
            PathPoint pathPoint3 = pathPoint2.a(pathOpenListNode);
            int n18 = pathPoint3.a(this, by2);
            byte by3 = pathPoint3.c(this, by2);
            n = pathPoint3.d(this, by2) ? 1 : 0;
            s = 0;
            if (n5 == 0) {
                if (pathPoint3.tileX == n12 && pathPoint3.tileY == n13) {
                    if (PathEngine.a) {
                        Log.d("RustedWarfare", "Over goal: fakeEnd");
                    }
                    s = 1;
                }
                if (Utility.abs(pathPoint3.tileX - n9) <= s5 && Utility.abs(pathPoint3.tileY - n10) <= s5) {
                    if (PathEngine.a) {
                        Log.d("RustedWarfare", "Over goal: real end");
                    }
                    s = 1;
                }
            }
            if ((bl3 = pathPoint3.b(this, (byte) (1 - by2))) || s != 0) {
                pathPoint.a(pathPoint3);
                if (!bl3) {
                    if (PathEngine.a) {
                        Log.d("RustedWarfare", "Not closedOnOtherSide");
                    }
                    n4 = pathPoint2.tileX;
                    s8 = pathPoint2.tileY;
                    bl2 = true;
                    break;
                }
                PathPoint pathPoint4 = pathPoint.f(this, by2);
                if (pathPoint4 == null) {
                    Log.d("RustedWarfare", "findPath: otherConnection==null");
                    Log.d("RustedWarfare", "currentNode:" + pathPoint2.tileX + "," + pathPoint2.tileY);
                    Log.d("RustedWarfare", "currentNode cost normal:" + pathPoint2.a(this, (byte) 0));
                    Log.d("RustedWarfare", "currentNode cost opposite:" + pathPoint2.a(this, (byte) 1));
                    linkedList.clear();
                    return linkedList;
                }
                if (n5 == 0) {
                    if (PathEngine.a) {
                        Log.d("RustedWarfare", "closing path runFromOppositeSide=false");
                    }
                    s9 = pathPoint2.tileX;
                    s10 = pathPoint2.tileY;
                    n4 = pathPoint4.tileX;
                    s8 = pathPoint4.tileY;
                } else {
                    if (PathEngine.a) {
                        Log.d("RustedWarfare", "closing path runFromOppositeSide=true");
                    }
                    s9 = pathPoint4.tileX;
                    s10 = pathPoint4.tileY;
                    n4 = pathPoint2.tileX;
                    s8 = pathPoint2.tileY;
                }
                bl2 = true;
                break;
            }
            pathPoint3.a(this, by2, true);
            byte by4 = 0;
            byte by5 = 7;
            if (n != 0) {
                by4 = 0;
                by5 = 7;
            } else {
                by = 2;
                if (this.f != null && this.f[pathPoint3.tileX * s2 + pathPoint3.tileY] > 1) {
                    by = 1;
                }
                by4 = (byte) (by3 - by);
                by5 = (byte) (by3 + by);
            }
            for (by = by4; by <= by5; by = (byte) ((byte) (by + 1))) {
                byte by6;
                int n19;
                int n20;
                pathPoint.a(pathPoint3);
                byte by7 = by;
                if (by7 > 7) {
                    by7 = (byte) (by7 - 8);
                }
                if (by7 < 0) {
                    by7 = (byte) (by7 + 8);
                }
                if (by7 == 0) {
                    pathPoint.tileX = (short) (pathPoint.tileX + 1);
                }
                if (by7 == 1) {
                    pathPoint.tileX = (short) (pathPoint.tileX + 1);
                    pathPoint.tileY = (short) (pathPoint.tileY + 1);
                }
                if (by7 == 2) {
                    pathPoint.tileY = (short) (pathPoint.tileY + 1);
                }
                if (by7 == 3) {
                    pathPoint.tileY = (short) (pathPoint.tileY + 1);
                    pathPoint.tileX = (short) (pathPoint.tileX - 1);
                }
                if (by7 == 4) {
                    pathPoint.tileX = (short) (pathPoint.tileX - 1);
                }
                if (by7 == 5) {
                    pathPoint.tileX = (short) (pathPoint.tileX - 1);
                    pathPoint.tileY = (short) (pathPoint.tileY - 1);
                }
                if (by7 == 6) {
                    pathPoint.tileY = (short) (pathPoint.tileY - 1);
                }
                if (by7 == 7) {
                    pathPoint.tileY = (short) (pathPoint.tileY - 1);
                    pathPoint.tileX = (short) (pathPoint.tileX + 1);
                }
                if (pathPoint.tileX < 0 || pathPoint.tileX >= n8 || pathPoint.tileY < 0 || pathPoint.tileY >= s2 || (n20 = pathPoint.a(this)) == -1)
                    continue;
                int n21 = pathPoint.a(this, by2);
                if (pathPoint.b(this, by2)) continue;
                if (pathPoint.tileX != pathPoint3.tileX && pathPoint.tileY != pathPoint3.tileY) {
                    if (this.a(pathPoint.tileX, pathPoint3.tileY) == -1 || this.a(pathPoint3.tileX, pathPoint.tileY) == -1)
                        continue;
                    n19 = n18 + (14 + n20) + 1;
                } else {
                    n19 = n18 + (10 + n20) + 1;
                }
                if (by3 != by7) {
                    if (n == 0) {
                        n19 += PathSolver.b(by3, by7);
                    } else if (n5 == 0) {
                        n19 = bl ? (n19 += PathSolver.c(by3, by7)) : (n19 += PathSolver.b(by3, by7));
                    }
                }
                if (this.f != null) {
                    // RWX：老师要求「离不可通行地形远一些」——clearance 0..4（4=离障≥4格）。
                    // 原来权重 n6(1或7)，贴墙格只比开阔格贵一点，A* 会贴着障碍走。这里放大：
                    // 贴墙(0) = +base*4，离1格 = +base*3 … 开阔(4) = 0。
                    int rwxClear = this.f[pathPoint.tileX * s2 + pathPoint.tileY];
                    n19 += (4 - rwxClear) * n6 * RWX_CLEARANCE_WEIGHT;
                }
                if (n7 > 0 && this.f != null && (by6 = this.f[pathPoint.tileX * s2 + pathPoint.tileY]) <= n7) {
                    n19 += 100;
                }
                if (n21 >= this.i && n19 >= n21) continue;
                pathPoint.a(this, by2, by7);
                pathPoint.a(this, by2, false);
                pathPoint.a(this, by2, n19);
                pathOpenListPool.a(n19 - this.i, pathPoint.tileX, pathPoint.tileY);
            }
        }
        if (PathEngine.a) {
            Log.d("RustedWarfare", "grid path finshed in :" + n2 + " ticks");
            long l = System.currentTimeMillis() - this.y;
            Log.d("RustedWarfare", "grid path done in:" + l);
        }
        long l = System.currentTimeMillis();
        if (!bl2) {
            // RWX：open list 双双耗尽都没碰上——这个 movementType 没有任何地形能到目标。
            if (!path.endUnreachable) {
                path.endUnreachable = true;
                if (PATH_STATS) {
                    unreachableCount++;
                    if (unreachableCount % 100 == 1) {
                        Log.a("RustedWarfare", "RWX_PATHSTATS unreachable=" + unreachableCount + " solved=" + solvedCount);
                    }
                }
            }
            if (PathEngine.a) {
                Log.d("RustedWarfare", "could not find end node");
            }
            // RWX：假终点流程整体取消（老师决定）。目标走不到就是走不到，
            // 不再「改成最近的能站格」——那正是单位莫名开去地图边缘的来源。
            linkedList.clear();
            path.endUnreachable = true;
            return linkedList;
        }
        linkedList.clear();
        if (n4 != -1 && s8 != -1) {
            LinkedList linkedList2 = this.a((byte) 0, (short) n4, s8);
            linkedList.addAll(this.a(linkedList2));
        }
        if (s9 != -1 && s10 != -1) {
            LinkedList linkedList3 = this.a((byte) 1, s9, s10);
            linkedList.addAll(linkedList3);
        }
        if (linkedList.size() > 1) {
            linkedList.remove(0);
        }
        this.z = GameEngine.getCurrentTimeMillis();
        if (PathEngine.a) {
            long l4 = this.z - this.y;
            Log.d("RustedWarfare", "path(" + path.pathId + ") finished in:" + l4);
        }
        if (PathEngine.l && !GameEngine.getInstance().isMenuBackgroundMap) {
            Debug.stopMethodTracing();
            PathEngine.l = false;
        }
        // RWX：返回前就剪掉绕远/贴边的航点。
        return rwxPruneFar(linkedList, n12, n13);
    }


    // RWX：路径一生成就剪掉「离目的地过远」的航点（老师要求：取消要快，别等走到边界）。
    // 判定：航点贴地图边界 1 格内，或到目的地的距离比「本路径里离目的地最近的航点」还远 40 像素以上。
    // 命中则丢掉最近点之前那一段，只保留尾巴。这样单位拿到的路径从一开始就不含绕远的点。
    // RWX：两点之间是否「逐格都能走」。用 Bresenham 走一遍格子，任何一格不可走就判 false。
    // 这是「删航点」的唯一安全前提：删掉 B 之后必然要沿 A->C 直线走，直线穿不过去就不能删。
    boolean rwxLineWalkable(int x0, int y0, int x1, int y1) {
        int dx = Math.abs(x1 - x0), dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1;
        int err = dx - dy, x = x0, y = y0, guard = 0;
        while (guard++ < 8192) {
            if (x < 0 || y < 0 || x >= this.g || y >= this.h) { return false; }
            if (this.a(x, y) == -1) { return false; }
            if (x == x1 && y == y1) { return true; }
            int e2 = err * 2;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
        return false;
    }

    // RWX：路径后处理（老师规则）：
    // 1) 目的地不在边界附近时，边界航点应当删除；
    // 2) 离目的地明显更远的绕远航点也应当删除；
    // 但只有「删掉它之后，前后航点之间逐格都能走」时才删，否则保留——这就是地形所限唯有此路。
    LinkedList rwxPruneFar(LinkedList linkedList, int endX, int endY) {
        if (linkedList == null || linkedList.size() < 3) { return linkedList; }
        boolean rwxEndNearEdge = endX <= 2 || endY <= 2 || endX >= this.g - 3 || endY >= this.h - 3;
        float rwxMinDist = Float.MAX_VALUE;
        for (int i0 = 0; i0 < linkedList.size(); i0++) {
            PathPoint p0 = (PathPoint) linkedList.get(i0);
            float dx0 = p0.tileX - endX, dy0 = p0.tileY - endY;
            float d0 = dx0 * dx0 + dy0 * dy0;
            if (d0 < rwxMinDist) { rwxMinDist = d0; }
        }
        LinkedList out = new LinkedList();
        int removedEdge = 0, removedFar = 0;
        for (int i = 0; i < linkedList.size(); i++) {
            PathPoint cur = (PathPoint) linkedList.get(i);
            boolean isLast = i == linkedList.size() - 1;
            if (isLast) { out.add(cur); continue; }
            boolean edgePt = !rwxEndNearEdge && (cur.tileX <= 1 || cur.tileY <= 1 || cur.tileX >= this.g - 2 || cur.tileY >= this.h - 2);
            boolean farPt = false;
            if (!edgePt) {
                // RWX：基准是「整条路径里离目的地最近的那个航点」，不是最后一个点。
                // 用最后一个点当基准会把几乎每个点都判成“绕远”（实测 removedFar=23/33）。
                float dxe = cur.tileX - endX, dye = cur.tileY - endY;
                float dcur = dxe * dxe + dye * dye;
                farPt = dcur > rwxMinDist + 9.0f;
            }
            if (!edgePt && !farPt) { out.add(cur); continue; }
            PathPoint prev = out.isEmpty() ? null : (PathPoint) out.getLast();
            PathPoint next = (PathPoint) linkedList.get(i + 1);
            if (prev != null && rwxLineWalkable(prev.tileX, prev.tileY, next.tileX, next.tileY)) {
                if (edgePt) { removedEdge++; } else { removedFar++; }
            } else {
                out.add(cur);
            }
        }
        if (out.size() >= 2 && (removedEdge + removedFar) > 0) {
            io.github.rwx.probe.RwxPerf.out("RWX_PRUNE_SAFE removedEdge=" + removedEdge + " removedFar=" + removedFar + " kept=" + out.size() + " end=" + endX + "," + endY);
            return out;
        }
        return linkedList;
    }

    public LinkedList a(byte b, short s, short s2) {
        LinkedList linkedList = new LinkedList();
        PathPoint pathPoint = new PathPoint(s, s2);
        linkedList.add(pathPoint);
        while (true) {
            PathPoint pathPointF = pathPoint.f(this, b);
            if (pathPointF != null) {
                linkedList.add(pathPointF);
                pathPoint = pathPointF;
            } else {
                return linkedList;
            }
        }
    }

    public LinkedList a(LinkedList linkedList) {
        LinkedList linkedList2 = new LinkedList();
        Iterator it = linkedList.iterator();
        while (it.hasNext()) {
            linkedList2.addFirst((PathPoint) it.next());
        }
        return linkedList2;
    }

    public void f() {
        g();
    }

    public void g() {
        final DynamicUnitPath e = (DynamicUnitPath) this.D;
        final FlowField b = new FlowField(this.g, this.h);
        if (PathEngine.l && !GameEngine.getInstance().isMenuBackgroundMap) {
            Debug.startMethodTracing("pathTrace", 110000000);
        }
        final int n = 7;
        final byte b2 = 0;
        if (PathEngine.a) {
            Log.d("RustedWarfare", "starting path for:" + this.n.a.toString());
        }
        final PathPoint r = this.r;
        final PathPoint q = this.q;
        final int h = this.h;
        final int g = this.g;
        this.y = GameEngine.getCurrentTimeMillis();
        final short h2 = e.startTileX;
        final short i = e.startTileY;
        final boolean k = e.k;
        this.d();
        this.a(e.movementType, e.startTileX, e.startTileY, e.j);
        final short l = e.endTileX;
        final short m = e.endTileY;
        final short n2 = e.n;
        final LinkedList list = new LinkedList();
        if (this.n.a.equals(UnitMovementType.NONE)) {
            if (PathEngine.a) {
                Log.d("RustedWarfare", "no point pathing for none");
            }
            return;
        }
        int n3 = 0;
        short n4 = l;
        short n5 = m;
        short short3 = n2;
        if (this.e != null) {
            final short[] e2 = this.e;
            final short integer1 = e2[h2 * h + i];
            boolean b3 = true;
            if (integer1 == -1) {
                b3 = false;
            } else {
                for (short n6 = (short) (l - n2); n6 <= l + n2; ++n6) {
                    for (short n7 = (short) (m - n2); n7 <= m + n2; ++n7) {
                        if (n6 >= 0) {
                            if (n6 < g) {
                                if (n7 >= 0) {
                                    if (n7 < h) {
                                        if (integer1 == e2[n6 * h + n7]) {
                                            b3 = false;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            // RWX：目标落在另一个孤立地形组＝这个兵种到不了。原逻辑 ±25 格找「最近的同组格子」，
        // 找不到就从 (0,0) 全图扫，同距离先到先得——落点被推到地图边缘（老师实测的边缘航点）。
        // 现在直接判不可达，不改终点。
        if (b3) {
            e.endUnreachable = true;
            return;
        }
        }
        int j = 1;
        short integer1;
        Label_0876:
        for (integer1 = (short) (n4 - short3); integer1 <= n4 + short3; ++integer1) {
            for (short integer2 = (short) (n5 - short3); integer2 <= n5 + short3; ++integer2) {
                if (integer1 >= 0) {
                    if (integer1 < g) {
                        if (integer2 >= 0) {
                            if (integer2 < h) {
                                if (this.a(integer1, integer2) != -1) {
                                    j = 0;
                                    break Label_0876;
                                }
                            }
                        }
                    }
                }
            }
        }
        // RWX：目标半径内一格都走不过去＝到不了。原来 ±9 格找最近能走格，兜底再从 (0,0) 全图扫，
        // 落点同样被推到边缘。现在直接判不可达，不改终点。
        if (j != 0) {
            e.endUnreachable = true;
            return;
        }
        if (PathEngine.a && (n4 != l || n5 != m)) {
            Log.d("RustedWarfare", "Moved end to fakeEndX:" + (int) n4 + " fakeEndY:" + (int) n5);
        }
        this.o.a(n4, n5);
        this.o.a(0, h2, i);
        this.p.a(h2, i);
        this.a(n4, n5, short3);
        this.p.a(0, n4, n5);
        j = 0;
        short integer2 = -1;
        short n6 = -1;
        short n7 = -1;
        short n13 = -1;
        short n9 = 0;
        while (j == 0) {
            ++n9;
            if (e.w) {
                return;
            }
            ++n3;
            byte byte2 = 0;
            byte2 = 1;
            final PathOpenListPool p = this.p;
            final PathOpenListNode c = p.c();
            if (c == null) {
                break;
            }
            final PathPoint a = q.a(c);
            final int a2 = a.a(this, byte2);
            final byte integer3 = (byte) (b.a(a) - 1);
            final boolean b4 = b.b(a);
            b.a(a, true);
            byte b5 = 7;
            byte b6;
            if (b4) {
                b6 = 0;
                b5 = 7;
            } else {
                byte b7 = 2;
                if (this.f != null && this.f[a.tileX * h + a.tileY] > 1) {
                    b7 = 1;
                }
                b6 = (byte) (integer3 - b7);
                b5 = (byte) (integer3 + b7);
            }
            for (byte b7 = b6; b7 <= b5; ++b7) {
                r.a(a);
                byte integer4 = b7;
                if (integer4 > 7) {
                    integer4 -= 8;
                }
                if (integer4 < 0) {
                    integer4 += 8;
                }
                if (integer4 == 0) {
                    final PathPoint pathPoint = r;
                    ++pathPoint.tileX;
                }
                if (integer4 == 1) {
                    final PathPoint pathPoint2 = r;
                    ++pathPoint2.tileX;
                    final PathPoint pathPoint3 = r;
                    ++pathPoint3.tileY;
                }
                if (integer4 == 2) {
                    final PathPoint pathPoint4 = r;
                    ++pathPoint4.tileY;
                }
                if (integer4 == 3) {
                    final PathPoint pathPoint5 = r;
                    ++pathPoint5.tileY;
                    final PathPoint pathPoint6 = r;
                    --pathPoint6.tileX;
                }
                if (integer4 == 4) {
                    final PathPoint pathPoint7 = r;
                    --pathPoint7.tileX;
                }
                if (integer4 == 5) {
                    final PathPoint pathPoint8 = r;
                    --pathPoint8.tileX;
                    final PathPoint pathPoint9 = r;
                    --pathPoint9.tileY;
                }
                if (integer4 == 6) {
                    final PathPoint pathPoint10 = r;
                    --pathPoint10.tileY;
                }
                if (integer4 == 7) {
                    final PathPoint pathPoint11 = r;
                    --pathPoint11.tileY;
                    final PathPoint pathPoint12 = r;
                    ++pathPoint12.tileX;
                }
                if (r.tileX >= 0) {
                    if (r.tileX < g) {
                        if (r.tileY >= 0) {
                            if (r.tileY < h) {
                                final int a3 = r.a(this);
                                if (a3 != -1) {
                                    final int a4 = r.a(this, byte2);
                                    if (!b.c(r)) {
                                        int integer5;
                                        if (r.tileX != a.tileX && r.tileY != a.tileY) {
                                            if (this.a(r.tileX, a.tileY) == -1) {
                                                continue;
                                            }
                                            if (this.a(a.tileX, r.tileY) == -1) {
                                                continue;
                                            }
                                            integer5 = a2 + (14 + a3) + 1;
                                        } else {
                                            integer5 = a2 + (10 + a3) + 1;
                                        }
                                        if (integer3 != integer4 && !b4) {
                                            integer5 += b(integer3, integer4);
                                        }
                                        if (this.f != null) {
                                            integer5 += (4 - this.f[r.tileX * h + r.tileY]) * n;
                                        }
                                        if (b2 > 0 && this.f != null && this.f[r.tileX * h + r.tileY] <= b2) {
                                            integer5 += 100;
                                        }
                                        if (a4 < this.i || integer5 < a4) {
                                            b.a(r, (byte) (integer4 + 1));
                                            b.a(r, false);
                                            r.a(this, byte2, integer5);
                                            p.a(integer5 - this.i, r.tileX, r.tileY);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (PathEngine.a) {
            Log.d("RustedWarfare", "grid path finshed in :" + (int) n9 + " ticks");
            final long currentTimeMillis = System.currentTimeMillis() - this.y;
            Log.d("RustedWarfare", "grid path done in:" + currentTimeMillis);
        }
        final long currentTimeMillis = System.currentTimeMillis();
        b.c = n4;
        b.d = n5;
        e.flowField = b;
        PathEngine.e = e;
        this.z = GameEngine.getCurrentTimeMillis();
        if (PathEngine.a) {
            Log.d("RustedWarfare", "path(" + e.pathId + ") finished in:" + (this.z - this.y));
        }
        if (PathEngine.l && !GameEngine.getInstance().isMenuBackgroundMap) {
            Debug.stopMethodTracing();
            PathEngine.l = false;
        }
    }
}
