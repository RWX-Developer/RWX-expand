# RWX 寻路代码（带注释）

这份文档是**现在实际在跑的代码**，从仓库里直接抽出来的，不是示意伪代码。
读法建议：先看第一节的流程，再看后面的整段代码对照。

---

## 一、一次移动指令会经过哪些代码

```
玩家右键 / AI 下令
  └─ Command.setMoveTarget / setAttackMoveTarget
       ├─ rwxOutOfMap(...)          图外？直接拒令（不留“去边界”的命令）
       └─ unitCommand.setMoveTarget 原样记下目标坐标
            ↓
单位 requestPathToTarget → 引擎路径队列 → PathSolver.e()（普通路径）或 g()（动态重算）
  ├─ 目标在图外            → endUnreachable = true，返回空路径
  ├─ -Drwx.astar 开（默认）→ RwxAStar.find(...) 自研 A*
  │     └─ 失败（起点不可走 / 展开超 6000）→ 返回 null，回退原求解器
  └─ 求解结果
        └─ rwxPruneFar(...)  逐格校验后，删掉贴边/绕远的航点
             ↓
单位拿到路径
  ├─ 试算-评估-重算（最多 3 次，RWX_REFINE）
  ├─ 移动途中每 8 tick 检查一次（RWX_REPATH_MOVE）
  └─ 沿航点走
```

---

## 二、自研 A*（新文件，整段）

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
 * 只在 -Drwx.astar=1 时启用；失败时返回 null，调用方回退到原求解器。
 */
public final class RwxAStar {

    private static final int MAX_EXPAND = 6000;

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
            if (cur == goal) { return buildPath(cameFrom, cur, w, sx, sy); }
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
                float step = diagonal ? 1.4142135f : 1.0f;
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

**读这段要抓住的四个点**

1. `gScore`：从起点走到这一格已经花掉多少（斜走算 1.414，直走算 1.0）。
2. `hScore`：这一格到终点“还差多远”的估计，用的八向距离。
3. `heapPop` 每次取 `g+h` 最小的格子展开；**f 相同的时候取 h 更小的**——这一句就是“不再顺着边界绕”的关键：同样代价下优先靠近目标的格子。
4. 斜走之前要检查两侧正交格，任一不可走就不许斜——不然单位会从墙角“切”过去。

---

## 三、求解器里的剪枝（贴边 / 绕远的航点）

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
        LinkedList out = new LinkedList();
        int removedEdge = 0, removedFar = 0;
        for (int i = 0; i < linkedList.size(); i++) {
            PathPoint cur = (PathPoint) linkedList.get(i);
            boolean isLast = i == linkedList.size() - 1;
            if (isLast) { out.add(cur); continue; }
            boolean edgePt = !rwxEndNearEdge && (cur.tileX <= 1 || cur.tileY <= 1 || cur.tileX >= this.g - 2 || cur.tileY >= this.h - 2);
            boolean farPt = false;
            if (!edgePt) {
                PathPoint lastP = (PathPoint) linkedList.get(linkedList.size() - 1);
                float dxc = cur.tileX - lastP.tileX, dyc = cur.tileY - lastP.tileY;
                float dxe = lastP.tileX - endX, dye = lastP.tileY - endY;
                farPt = (dxc * dxc + dyc * dyc) > (dxe * dxe + dye * dye) + 64.0f;
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

**为什么删点前要逐格走一遍**：删掉中间某个航点，单位就会沿着“前一个点 → 后一个点”的直线走。那条直线如果穿过水、墙，看起来就是“不认地形”。`rwxLineWalkable` 就是拿 Bresenham 把这条直线经过的每一格都查一次，有一格不能走就保留原航点——这就是“地形所限，唯有此路”。

A* 的接入点：

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

---

## 三点五、地形类型与代价层（更正一处措辞）

之前说「地形属性」时把两件事混在一起了，这里分开：

**地图格子真正携带的属性**（`MapTile` 源码字段）：

| 字段 | 含义 |
| --- | --- |
| 默认（无标记） | 平地 |
| `isCliff` | 悬崖/山地 |
| `isWater` | 水面 |
| `isWaterBridge` | 水桥 |
| `isShallowWater` | 浅水（RWX 新增：视觉是水，陆军能踩） |
| `isLava` | 熔岩 |
| `isResourcePool` | 资源池（属性，不是地形种类） |
| `hasLargeObject` | 大物件占位 |
| `movementBlockLevel` | 额外阻挡等级 |

也就是说：**地形种类就是平地、山地、水面、水桥、熔岩**（外加 RWX 的浅水、以及“没有地块”的空洞），老师的记忆没错。

**代价层**是另一回事：

| 层 | 作用 |
| --- | --- |
| 地形代价 `b` | 这一格对某移动类型的基本代价 |
| 建筑代价 `c` | 建筑占位 |
| 物件代价 `d` | 单位/物件占位，×10 |

`PathSolver.a(x,y) = b + c + d×10`，任一层 -1 即不可走。三张图按 8 种移动类型分别维护，
所以同一格对 LAND 不可走、对 HOVER 可走——这是“代价层”，不是“地形种类”。

运行时统计工具：`logs/rwx-terrain-attr.txt`（逐格铺开，图例：`. 平地 ~ 水 B 水桥 s 浅水 L 熔岩 # 悬崖 R 资源池 O 大物件`）。

---

## 四、单位侧的两次检查

文件：`core/src/main/java/com/corrodinggames/rts/game/units/OrderableUnit.java`

### 4.1 拿到路径时：试算-评估-重算

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

三个判定：

| 判定 | 含义 |
| --- | --- |
| `rwxEdgeWp` | 有航点贴在边界 1 格内 |
| `rwxMaxHop > 120` | 相邻航点跨度超过 120 像素（约 6 格） |
| `rwxLastD > 40` | 最后一个航点离目标超过 40 像素 |

任一项命中且本目标试算不足 3 次 → 丢路径、立刻重算。

### 4.2 移动途中：每 8 tick 检查一次

```java
                // RWX：老师要求——移动途中也要检查路径点是否合理，不合理就改路。
                // 每 8 个 tick 检查一次（按 objectId 错开，避免所有单位同一帧扫描）：
                // 条件是「目的地不在边界附近，却有航点贴在边界」，或「有航点比本路径最近的航点到目的地还远 3 格」。
                // 命中且同一目标改路不足 3 次，就丢掉当前路径立刻重新寻路。
                if (this.activePathCount > 1 && ((gameEngine.currentTick + this.objectId) & 7) == 0) {
                    com.corrodinggames.rts.game.map.TileMap rwxTm = GameEngine.getInstance().tileMap;
                    if (rwxTm == null) { rwxTm = gameEngine.tileMap; }
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

按 `objectId` 错开（`(tick + objectId) & 7 == 0`），避免所有单位同一帧一起扫路径。

---

## 五、命令入口：图外目标直接拒发

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

配合三个 setter 里的这段（以 `setAttackMoveTarget` 为例）：

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

**这一条是“寻路跑到西北角”的直接来源**：以前 `-99999,-99999` 这种“没有目标”的哨兵值会被 `rwxClampTarget` 钳到内缩角 `(10,10)`，而 `(10,10)` 就是左上角。现在在钳制之前就判图外并拒令。

---

## 六、必须说清楚的边界

1. `RWX_REPATH_MOVE` / `RWX_REFINE` 每个目标最多改路 3 次，之后就不再改，否则会无限抖动；最坏情况仍会走一段不理想的路。
2. `rwxLineWalkable` 用的是逐格直线检查，不是真正的连通性搜索；如果“绕一下能走、直线不能走”，它会选择保留原航点（保守，不会穿地形，但可能少剪一点）。
3. A* 每次求解都会分配几个数组（每格一个 float/int/boolean），大地图上单位密集时可能增加 GC 压力；目前**没有实测数据**。
4. A* 只接在普通路径（`e()`）上；动态重算走的 `g()` 仍是原算法 + 单位侧检查。
5. 这些阈值（120px、40px、3 格、3 次）是按经验设的初始值，**不是实测调出来的**。
