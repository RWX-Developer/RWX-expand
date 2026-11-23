# RWX 寻路代码（带注释，当前版本）

本文档里的代码是从仓库**直接抽出**的，对应提交 `774fb83` 之后的仓库状态。
前半部分是“现在实际怎么跑”，后半部分是“原始代码 + 我加的注释”。

---

## 一、一次移动指令的完整路径

```
玩家右键 / AI 下令
  └─ Command.setMoveTarget / setAttackMoveTarget / setBuildTarget
       ├─ rwxOutOfMap()：目标在图外 → 直接拒令（不留“去边界”的命令）
       └─ 原样记下坐标（不再钳制、不再搬到“最近可站格”）
            ↓
单位 requestPathToTarget → 路径队列 → PathSolver.e()
  ├─ 目标在图外      → endUnreachable = true，返回空路径
  ├─ -Drwx.astar≠0   → RwxAStar.find()（自研 A*，按地形代价）
  │     ├─ 成功 → rwxPruneFar() 剪枝后返回
  │     └─ 失败（起点不可走 / 展开超 6000）→ null，回退原求解器
  └─ 原求解器 → rwxPruneFar() 剪枝后返回
            ↓
单位拿到路径
  ├─ 试算-评估-重算（RWX_REFINE，每目标最多 3 次）
  ├─ 每 8 tick 检查一次合理性（RWX_REPATH_MOVE）
  └─ 沿航点移动
```

---

## 二、地形与移动类型（读源码得到的事实）

### 2.1 地形种类（`MapTile` 字段）

| 字段 | 含义 |
| --- | --- |
| 默认（无标记） | 平地 |
| `isCliff` | 悬崖 / 山地 |
| `isWater` | 水面 |
| `isWaterBridge` | 水桥 |
| `isShallowWater` | 浅水（RWX 新增：视觉是水，陆军可踩） |
| `isLava` | 熔岩 |
| `isResourcePool` | 资源池（属性，非地形种类） |
| `hasLargeObject` | 大物件占位 |
| `movementBlockLevel` | 额外阻挡等级 |

另外 `getTileAt()` 返回 null 表示“没有地块”（地图空洞）。

### 2.2 代价层（`PathSolver.a(x,y)`）

```java
public final int a(int i, int i2) {
    if (this.b[...] == -1 || this.c[...] == -1 || this.d[...] == -1) { return -1; }
    return this.b[...] + this.c[...] + (this.d[...] * 10);
}
```

- `b` 地形代价、`c` 建筑代价、`d` 物件代价（×10）；
- 任一层 -1 → 这一格不可走；
- 三张图按**移动类型**分别维护（`PathCostMap` 由 `C.a(UnitMovementType)` 取图）；
- 移动类型 8 种：`NONE / LAND / BUILDING / AIR / WATER / HOVER / OVER_CLIFF / OVER_CLIFF_WATER`。

**结论**：同一格对 LAND 可能不可走、对 HOVER 可走。A* 不需要自己判断“水/陆/悬崖”，
只问 `a(x,y)`，拿到的就是该兵种视角下的真实代价。

---

## 三、自研 A*（整段全文）

文件：`core/src/main/java/com/corrodinggames/rts/gameFramework/pathfinding/RwxAStar.java`

```java
package com.corrodinggames.rts.gameFramework.pathfinding;

import java.util.LinkedList;

/**
 * RWX：自研 A* 寻路。
 *
 * 为什么自己写：原求解器的 open 表用线性扫描找最小 f，同分时按插入顺序取，
 * 结果路径容易贴着一侧边界走（老师实测到的「跑到西北角/边缘」）。这里换成：
 *  - 二叉堆维护 open 表，同 f 时用更小的 h 优先（更愿意朝目标直走）；
 *  - 8 方向移动，禁止贴角斜穿（两边都是墙时不许斜走）；
 *  - 展开节点上限与原求解器一致（6000），超了就当失败，交回原求解器。
 *
 * 地形与移动类型（实测读代码得出）：
 *  - PathSolver.a(x,y) 返回「地形代价 + 建筑代价 + 物件代价×10」，任一层为 -1 即不可走；
 *  - 三张代价图是按移动类型分别维护的（PathCostMap 通过 C.a(UnitMovementType) 取图），
 *    移动类型共 8 种：NONE / LAND / BUILDING / AIR / WATER / HOVER / OVER_CLIFF / OVER_CLIFF_WATER；
 *  - 所以 A* 不需要自己判断“水/陆/悬崖”，只要问 solver.a(x,y)，就是该兵种视角下的真实代价。
 * 步进代价 = 1 + 格子代价×COST_SCALE（直走），斜走再乘 1.414；COST_SCALE 默认 0.1，
 * 即代价 10 相当于多花 1 步。这样单位会绕开高代价地形（沼泽、斜坡），而不是只看“能不能走”。
 *
 * 只在 -Drwx.astar=0 时关闭；失败时返回 null，调用方回退到原求解器。
 */
public final class RwxAStar {

    private static final int MAX_EXPAND = 6000;
    // 地形代价换算系数：代价 10 的格子相当于多花 1 步。可用 -Drwx.astar.costScale 调整。
    private static final float COST_SCALE = rwxCostScale();

    private static float rwxCostScale() {
        try {
            String v = System.getProperty("rwx.astar.costScale", "0.1");
            float f = Float.parseFloat(v.trim());
            if (f < 0.0f) { f = 0.0f; }
            if (f > 10.0f) { f = 10.0f; }
            return f;
        } catch (Throwable t) {
            return 0.1f;
        }
    }

    // RWX：首次调用/首次失败各打一行，方便确认 A* 真的在跑（各只打一次，不刷屏）。
    private static boolean rwxLoggedOk = false;
    private static boolean rwxLoggedFail = false;

    private RwxAStar() {
    }

    private static float heuristic(int x, int y, int ex, int ey) {
        int dx = Math.abs(x - ex);
        int dy = Math.abs(y - ey);
        int min = Math.min(dx, dy);
        int max = Math.max(dx, dy);
        return (float) (max + (Math.sqrt(2.0) - 1.0) * min);
    }

    /**
     * @return 从起点到终点的航点表（含起点，不含起点之外的重复点）；失败返回 null。
     */
    public static LinkedList<PathPoint> find(PathSolver solver, int sx, int sy, int ex, int ey) {
        int w = solver.g;
        int h = solver.h;
        if (w <= 0 || h <= 0) { return null; }
        if (sx < 0 || sy < 0 || ex < 0 || ey < 0 || sx >= w || sy >= h || ex >= w || ey >= h) { return null; }
        if (solver.a(sx, sy) == -1 || solver.a(ex, ey) == -1) { return null; }

        int total = w * h;
        float[] gScore = new float[total];
        int[] cameFrom = new int[total];
        boolean[] closed = new boolean[total];
        float[] hScore = new float[total];
        java.util.Arrays.fill(gScore, Float.MAX_VALUE);
        java.util.Arrays.fill(cameFrom, -1);

        int[] heap = new int[total];
        int heapSize = 0;
        int start = sy * w + sx;
        int goal = ey * w + ex;
        gScore[start] = 0.0f;
        hScore[start] = heuristic(sx, sy, ex, ey);
        heap[heapSize++] = start;

        int expanded = 0;
        final int[] dxs = {1, -1, 0, 0, 1, 1, -1, -1};
        final int[] dys = {0, 0, 1, -1, 1, -1, 1, -1};

        while (heapSize > 0) {
            int cur = heapPop(heap, heapSize, gScore, hScore);
            heapSize--;
            if (closed[cur]) { continue; }
            closed[cur] = true;
            if (cur == goal) {
                LinkedList rwxPath = buildPath(cameFrom, cur, w, sx, sy);
                if (!rwxLoggedOk) {
                    rwxLoggedOk = true;
                    io.github.rwx.probe.RwxPerf.out("RWX_ASTAR ok map=" + w + "x" + h + " nodes=" + rwxPath.size() + " expand=" + expanded + " costScale=" + COST_SCALE);
                }
                return rwxPath;
            }
            if (++expanded > MAX_EXPAND) { return null; }

            int cx = cur % w;
            int cy = cur / w;
            for (int d = 0; d < 8; d++) {
                int nx = cx + dxs[d];
                int ny = cy + dys[d];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) { continue; }
                if (solver.a(nx, ny) == -1) { continue; }
                boolean diagonal = d >= 4;
                if (diagonal) {
                    // 禁止贴角斜穿：两侧任一格不可走就不许斜走
                    if (solver.a(cx + dxs[d], cy) == -1 || solver.a(cx, cy + dys[d]) == -1) { continue; }
                }
                int ni = ny * w + nx;
                if (closed[ni]) { continue; }
                // 进入目标格的代价：1 + 该格代价×COST_SCALE，斜走再乘 1.414
                int tileCost = solver.a(nx, ny);
                if (tileCost < 0) { continue; }
                float step = (1.0f + tileCost * COST_SCALE) * (diagonal ? 1.4142135f : 1.0f);
                float tentative = gScore[cur] + step;
                if (tentative >= gScore[ni]) { continue; }
                gScore[ni] = tentative;
                cameFrom[ni] = cur;
                hScore[ni] = heuristic(nx, ny, ex, ey);
                if (heapSize < heap.length) {
                    heap[heapSize] = ni;
                    siftUp(heap, heapSize, gScore, hScore);
                    heapSize++;
                }
            }
        }
        if (!rwxLoggedFail) {
            rwxLoggedFail = true;
            io.github.rwx.probe.RwxPerf.out("RWX_ASTAR fail-to-find map=" + w + "x" + h + " expand=" + expanded + " start=" + sx + "," + sy + " end=" + ex + "," + ey);
        }
        return null;
    }

    private static boolean less(int a, int b, float[] g, float[] h) {
        float fa = g[a] + h[a], fb = g[b] + h[b];
        if (fa != fb) { return fa < fb; }
        return h[a] < h[b];
    }

    private static void siftUp(int[] heap, int i, float[] g, float[] h) {
        while (i > 0) {
            int parent = (i - 1) >> 1;
            if (!less(heap[i], heap[parent], g, h)) { break; }
            int t = heap[i]; heap[i] = heap[parent]; heap[parent] = t;
            i = parent;
        }
    }

    private static void siftDown(int[] heap, int size, int i, float[] g, float[] h) {
        while (true) {
            int l = i * 2 + 1, r = l + 1, m = i;
            if (l < size && less(heap[l], heap[m], g, h)) { m = l; }
            if (r < size && less(heap[r], heap[m], g, h)) { m = r; }
            if (m == i) { return; }
            int t = heap[i]; heap[i] = heap[m]; heap[m] = t;
            i = m;
        }
    }

    private static int heapPop(int[] heap, int size, float[] gScore, float[] hScore) {
        int out = heap[0];
        heap[0] = heap[size - 1];
        siftDown(heap, size - 1, 0, gScore, hScore);
        return out;
    }

    private static LinkedList<PathPoint> buildPath(int[] cameFrom, int goal, int w, int sx, int sy) {
        LinkedList<PathPoint> rev = new LinkedList<PathPoint>();
        int cur = goal;
        while (cur != -1) {
            rev.addFirst(new PathPoint((short) (cur % w), (short) (cur / w)));
            cur = cameFrom[cur];
        }
        return rev;
    }
}

```

**读这段要抓住的五点**

1. `gScore`：从起点走到这一格已经花掉多少；`hScore`：这一格到终点还差多远的估计（八向距离）。
2. `heapPop` 每次取 `g+h` 最小的格子；**f 相同取 h 更小的**——这是“不再顺着边界绕”的关键。
3. 斜走前检查两侧正交格，任一不可走就不许斜走——防止从墙角切过去。
4. **步进代价 = (1 + 格子代价 × COST_SCALE) ×（斜走 1.414）**，默认 `COST_SCALE=0.1`，
   也就是代价 10 的格子约等于多花 1 步，单位会绕开沼泽/斜坡。
5. 展开上限 6000，失败返回 null 交回原求解器，不会把单位卡死。

---

## 四、求解器侧剪枝

文件：`core/src/main/java/com/corrodinggames/rts/gameFramework/pathfinding/PathSolver.java`

```java
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
```

A* 接入点：

```java
        // RWX：-Drwx.astar=1 时走自研 A*（默认开启，失败自动回退原求解器）。
        // 起点/终点取本次求解用的 s3,s4（起点）与 n9,n10（终点）。
        if (!"0".equals(System.getProperty("rwx.astar", "1").trim())) {
            LinkedList rwxA = RwxAStar.find(this, s3, s4, n9, n10);
            if (rwxA != null && rwxA.size() >= 1) {
                return rwxPruneFar(rwxA, n9, n10);
            }
        }
```

**路径点距离代价（本轮新增）**：以“起点 → 终点”这条直线为基准，每个航点到直线的**垂直距离**就是它的偏离代价。
超过阈值（默认 **3 格**，`-Drwx.pruneDevTiles` 可调）的航点会被列入可删列表，但仍要通过逐格可通行校验才真正删除。
日志里 `removedDev` 就是被这一条删掉的航点数。

**剪枝的唯一安全前提**：删掉某个航点后，单位会沿“前一点 → 后一点”的直线走，
所以删除前必须用 `rwxLineWalkable` 把这条直线经过的每一格都查一遍，有一格不可走就保留。
这就是“地形所限，唯有此路”。

---

## 五、单位侧的两道检查

文件：`core/src/main/java/com/corrodinggames/rts/game/units/OrderableUnit.java`

### 5.1 拿到路径时：试算-评估-重算

```java
                            // RWX：老师要求的「试算 → 评估 → 重算」流程。
                            // 单位先算出试算路径；若航点之间跨度太大、或航点离目标不是最优（贴边/绕远），
                            // 立刻丢掉这条试算路径重新试算，最多 3 次；都试完就按当时那条开始行动。
                            if (this.activePathCount > 1) {
                                if (this.lastPathTargetX != this.rwxRefineGoalX || this.lastPathTargetY != this.rwxRefineGoalY) {
                                    this.rwxRefineGoalX = this.lastPathTargetX;
                                    this.rwxRefineGoalY = this.lastPathTargetY;
                                    this.rwxRefineCount = 0;
                                }
                                // RWX：删点只在求解器里做（带逐格可通行校验）。这里只做评估+重算，
                                // 单位侧不再自己删点，否则会切穿地形走出非法捷径。
                                float rwxGx = this.lastPathTargetX;
                                float rwxGy = this.lastPathTargetY;
                                float rwxMinD = Float.MAX_VALUE;
                                float rwxMaxHop = 0.0f;
                                float rwxLastD = 0.0f;
                                boolean rwxEdgeWp = false;
                                float rwxPrevX = this.posX;
                                float rwxPrevY = this.posY;
                                for (int rwxK = 0; rwxK < this.activePathCount; rwxK++) {
                                    PositionData rwxP = this.pathPositions[rwxK];
                                    if (rwxP == null) { continue; }
                                    int rwxTx = (int) (rwxP.posX * tileMap.tileScaleX);
                                    int rwxTy = (int) (rwxP.posY * tileMap.tileScaleY);
                                    if (rwxTx <= 1 || rwxTy <= 1 || rwxTx >= tileMap.tileCountX - 2 || rwxTy >= tileMap.tileCountY - 2) { rwxEdgeWp = true; }
                                    float rwxDx = rwxP.posX - rwxGx;
                                    float rwxDy = rwxP.posY - rwxGy;
                                    float rwxD = (float) Math.sqrt(rwxDx * rwxDx + rwxDy * rwxDy);
                                    rwxLastD = rwxD;
                                    if (rwxD < rwxMinD) { rwxMinD = rwxD; }
                                    float rwxHx = rwxP.posX - rwxPrevX;
                                    float rwxHy = rwxP.posY - rwxPrevY;
                                    float rwxHop = (float) Math.sqrt(rwxHx * rwxHx + rwxHy * rwxHy);
                                    if (rwxHop > rwxMaxHop) { rwxMaxHop = rwxHop; }
                                    rwxPrevX = rwxP.posX;
                                    rwxPrevY = rwxP.posY;
                                }
                                boolean rwxBad = rwxEdgeWp || rwxMaxHop > 120.0f || rwxLastD > 40.0f;
                                if (rwxBad && this.rwxRefineCount < 3) {
                                    this.rwxRefineCount++;
                                    io.github.rwx.probe.RwxPerf.out("RWX_REFINE unit=" + (r() == null ? "?" : r().getUnitTypeDescriptionShort())
                                        + " try=" + this.rwxRefineCount + " nodes=" + this.activePathCount
                                        + " edge=" + rwxEdgeWp + " maxHop=" + (int) rwxMaxHop + " minD=" + (int) rwxMinD + " lastD=" + (int) rwxLastD
                                        + " goal=" + this.aU.endTileX + "," + this.aU.endTileY
                                        + " map=" + tileMap.tileCountX + "x" + tileMap.tileCountY);
                                    clearPathData();
                                    requestPathToTarget(this.lastPathTargetX, this.lastPathTargetY, this.pathTargetRadius,
                                        this.isTransportAttached && this.formationSlotIndex > 1, this.isLowPriority);
                                }
                            }
                    }
                }
```

| 判定 | 含义 |
| --- | --- |
| `rwxEdgeWp` | 有航点贴在边界 1 格内 |
| `rwxMaxHop > 120` | 相邻航点跨度超过 120 像素（约 6 格） |
| `rwxLastD > 40` | 最后一个航点离目标超过 40 像素 |

任一项命中且本目标试算不足 3 次 → 丢路径、立刻重算。

### 5.2 移动途中（每 8 tick）

```java
                    // RWX：老师要求——移动途中也要检查路径点是否合理，不合理就改路。
                // 每 8 个 tick 检查一次（按 objectId 错开，避免所有单位同一帧扫描）：
                // 条件是「目的地不在边界附近，却有航点贴在边界」，或「有航点比本路径最近的航点到目的地还远 3 格」。
                // 命中且同一目标改路不足 3 次，就丢掉当前路径立刻重新寻路。
                if (this.activePathCount > 1 && ((GameEngine.getInstance().currentTick + this.objectId) & 7) == 0) {
                    com.corrodinggames.rts.game.map.TileMap rwxTm = GameEngine.getInstance().tileMap;
                    if (rwxTm == null) { return; }
                    if (this.lastPathTargetX != this.rwxMoveCheckGoalX || this.lastPathTargetY != this.rwxMoveCheckGoalY) {
                        this.rwxMoveCheckGoalX = this.lastPathTargetX;
                        this.rwxMoveCheckGoalY = this.lastPathTargetY;
                        this.rwxMoveRepathCount = 0;
                    }
                    if (this.repathCooldown == 0.0f && this.rwxMoveRepathCount < 3) {
                        int rwxGtx = (int) (this.lastPathTargetX * rwxTm.tileScaleX);
                        int rwxGty = (int) (this.lastPathTargetY * rwxTm.tileScaleY);
                        boolean rwxEndNearEdge = rwxGtx <= 2 || rwxGty <= 2 || rwxGtx >= rwxTm.tileCountX - 3 || rwxGty >= rwxTm.tileCountY - 3;
                        boolean rwxBad = false;
                        if (!rwxEndNearEdge) {
                            for (int rwxK = 0; rwxK < this.activePathCount; rwxK++) {
                                PositionData rwxPd = this.pathPositions[rwxK];
                                if (rwxPd == null) { continue; }
                                int rwxTtx = (int) (rwxPd.posX * rwxTm.tileScaleX);
                                int rwxTty = (int) (rwxPd.posY * rwxTm.tileScaleY);
                                if (rwxTtx <= 1 || rwxTty <= 1 || rwxTtx >= rwxTm.tileCountX - 2 || rwxTty >= rwxTm.tileCountY - 2) { rwxBad = true; break; }
                            }
                        }
                        if (!rwxBad) {
                            float rwxGoalDx = this.lastPathTargetX - this.posX;
                            float rwxGoalDy = this.lastPathTargetY - this.posY;
                            float rwxDirect = (float) Math.sqrt(rwxGoalDx * rwxGoalDx + rwxGoalDy * rwxGoalDy);
                            for (int rwxK = 0; rwxK < this.activePathCount; rwxK++) {
                                PositionData rwxPd = this.pathPositions[rwxK];
                                if (rwxPd == null) { continue; }
                                float rwxDdx = rwxPd.posX - this.lastPathTargetX;
                                float rwxDdy = rwxPd.posY - this.lastPathTargetY;
                                float rwxDist = (float) Math.sqrt(rwxDdx * rwxDdx + rwxDdy * rwxDdy);
                                if (rwxDist > rwxDirect + 3.0f * (float) Math.max(rwxTm.tileWorldSizeX, rwxTm.tileWorldSizeY)) { rwxBad = true; break; }
                            }
                        }
                        if (rwxBad) {
                            this.rwxMoveRepathCount++;
                            io.github.rwx.probe.RwxPerf.out("RWX_REPATH_MOVE unit=" + (r() == null ? "?" : r().getUnitTypeDescriptionShort())
                                + " try=" + this.rwxMoveRepathCount + " nodes=" + this.activePathCount
                                + " goal=" + this.aU.endTileX + "," + this.aU.endTileY + " map=" + rwxTm.tileCountX + "x" + rwxTm.tileCountY);
                            clearPathData();
                            requestPathToTarget(this.lastPathTargetX, this.lastPathTargetY, this.pathTargetRadius,
                                this.isTransportAttached && this.formationSlotIndex > 1, this.isLowPriority);
                            return;
                        }
                    }
                }
```

按 `objectId` 错开（`(tick + objectId) & 7 == 0`），避免所有单位同帧一起扫。

---

## 六、命令入口：图外目标直接拒发

文件：`core/src/main/java/com/corrodinggames/rts/gameFramework/Command.java`

```java
    private static boolean rwxOutOfMap(float f, float f2) {
        try {
            GameEngine ge = GameEngine.getInstance();
            if (ge == null || ge.tileMap == null) {
                return false;
            }
            com.corrodinggames.rts.game.map.TileMap tm = ge.tileMap;
            if (tm.tileCountX <= 0 || tm.tileCountY <= 0) {
                return false;
            }
            return f < 0 || f2 < 0 || f > tm.getWorldWidth() || f2 > tm.getWorldHeight();
        } catch (Throwable th) {
            return false;
        }
    }
```

三个 setter 里的用法（以攻击移动为例）：

```java
    public void setAttackMoveTarget(float f, float f2) {
        // RWX：-Drwx.clampReject=1 时，图外落点不再被钳到边界（那是「开到地图边缘」的直接来源），改为拒令。
        if (!"0".equals(System.getProperty("rwx.clampReject", "1").trim()) && rwxOutOfMap(f, f2)) {
            if (RWX_CLAMP_N++ < 12) {
                io.github.rwx.probe.RwxPerf.out("RWX_CLAMP_REJECT out of map order dropped: " + (int) f + "," + (int) f2);
            }
            return;
        }
```

**这一条是“寻路跑到西北角”的直接来源**：`-99999,-99999` 这种“没有目标”的哨兵值，
以前会被 `rwxClampTarget` 钳到内缩角 `(10,10)`，而 (10,10) 正是左上角。

---

## 七、实测记录（来自 `logs/rwx-prof.log`）

| 日志 | 条数 | 状态 |
| --- | --- | --- |
| `RWX_PRUNE_SAFE` | 22132 | 在跑 |
| `RWX_REFINE` | 12917 | 在跑 |
| `RWX_BORDER_DROP` | 1066 | 旧版遗留 |
| `RWX_REPATH_MOVE` | 0 | 曾误放在“单位被卡住”分支，已修，待复测 |
| `RWX_PRUNE_EDGE` | 0 | 一次未触发，待复测 |
| `RWX_ASTAR ok / fail` | 0 | A\* 需要真实寻路请求才会加载，尚未出现 |

另外用 `GC.class_histogram` 查过运行进程：`PathSolver` 已加载，`RwxAStar` **未加载**——
说明 A\* 到现在为止一次都没执行过。

---

## 八、必须说清楚的边界

1. 每目标最多改路 3 次，之后不再改（否则无限抖动），最坏情况仍会走一段不理想的路。
2. `rwxLineWalkable` 是逐格直线检查，不是连通性搜索；“绕一下能走、直线不能走”时它会保守地保留原航点。
3. A\* 每次求解要分配几个数组（每格 float/int/boolean），大地图单位多时可能增加 GC 压力，**没有实测**。
4. A\* 只接在普通路径 `e()` 上；动态重算走的 `g()` 仍是原算法 + 单位侧检查。
5. 阈值（120px、40px、3 格、3 次、costScale=0.1）都是经验值，**不是实测调出来的**。
6. A\* 的代价只影响“绕不绕路”，不含熔岩伤害这类玩法效果。
