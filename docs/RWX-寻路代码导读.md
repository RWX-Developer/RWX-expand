# RWX 寻路代码导读（给不写代码的人看的版本）

源码原样放在 `docs/寻路源码/`，和游戏里跑的是同一份。这篇只做两件事：说清每个文件在干嘛，以及哪里可能拖慢。

---

## 一、寻路到底在做什么

单位要走到某个点，中间有山、水、建筑挡着。游戏做的事是：

1. **把地图切成格子**（一格约 15 像素），每格记录「这个兵种能不能走、走进去多贵」。
2. **在格子上找一条最便宜的路**（A* 算法）。
3. **把格子路径变成单位能跟随的航点**，然后单位每帧朝下一个航点挪。

一句话：**地图 → 代价图 → A* 搜索 → 航点表 → 单位跟随**。

---

## 二、文件地图

| 文件 | 行数 | 干什么 |
| --- | --- | --- |
| `path/PathEngine.java` | 685 | 总调度。每帧处理寻路请求队列、决定谁现在算、缓存结果 |
| `pathfinding/PathSolver.java` | 1070 | 旧版 A* 求解器（默认走这个） |
| `pathfinding/RwxPathSolver2.java` | 300 | RWX 新写的求解器（`-Drwx.pathing=2` 才用），目标不可达时只在小半径找替代点 |
| `pathfinding/PathCostMap.java` | 507 | 代价图：每格「能不能走 + 多贵」，按兵种/地形分组 |
| `pathfinding/FastNodeQueue.java` | 246 | A* 的「待探索清单」，每次取代价最小的格子 |
| `pathfinding/BucketedNodeQueue.java` | 102 | 另一种清单实现（按代价分桶，取最小更快） |
| `pathfinding/Path.java` | 286 | 一条寻路请求：起点、终点、兵种、结果状态 |
| `pathfinding/DynamicUnitPath.java` | 180 | 动态路径：目标会动的单位（追敌）用的路径 |
| `pathfinding/PathPoint.java` | 182 | 单个航点（格子坐标） |
| `pathfinding/FlowField.java` + `FlowFieldPathPositionProvider.java` | 53 + 102 | 流场：一大群单位去同一个地方时，预先算好整张「方向地图」 |
| `pathfinding/DirectAccessPathNodeArrayList.java` | 422 | 高性能数组列表（避免装箱开销） |
| `pathfinding/PathOpenListPool.java` / `PathOpenListNode.java` | 66 / 47 | 开放表的对象池，减少 GC |

---

## 三、主流程（新求解器，代码是真的）

```java
public static LinkedList<PathPoint> solve(PathSolver solver, Path path) {
    int startX = path.startTileX, startY = path.startTileY;
    int endX = path.endTileX,     endY = path.endTileY;
    ...
    // 起点走不了（卡在建筑里）→ 只在小半径内挪一格，不扫全图
    if (cost(solver, startX, startY) == -1) {
        int[] s = nearestWalkable(solver, startX, startY, RWX_START_SNAP_RADIUS, false);
        if (s == null) { path.endUnreachable = true; failed++; return out; }
        startX = s[0]; startY = s[1];
    }
```

**白话**：单位卡住了，以前会满地图找一个能站的格子（很贵），现在只找身边 2 格，找不到就直接报「去不了」。这是 RWX 之前的改动。

```java
// 主循环：从清单里取出代价最小的格子，向 8 个方向扩张
for (int d = 0; d < 8; d++) {
    int nx = x + DX[d], ny = y + DY[d];
    if (d >= 4) {
        // 不切角：走对角时，两个直角方向的格子都必须通
        if (cost(solver, x + DX[d], y) == -1 || cost(solver, x, y + DY[d]) == -1) continue;
    }
    int nc = cost(solver, nx, ny);      // 这一格多贵（-1 = 不能走）
    if (nc == -1) continue;
    int step = (d >= 4 ? 14 : 10) + nc; // 直走 10、斜走 14（近似真实距离）
    int ng = g + step;
    ...
    heap[heapSize++] = pack(ng + heuristic(nx, ny, endX, endY), nIdx);
}
```

**白话**：每一步都在问「走这边会不会更便宜」。`heuristic` 是「离目标大概还有多远」的估计，加上它才知道先试哪边。`-Drwx` 里 `RWX_MAX_EXPAND = 6000` 是单条路径的探索上限——**防止一条烂路径占住整帧**。

---

## 四、它每帧被谁调用

`GameLogic.update()` 里有一行：

```java
this.pathfindingEngine.b(f);   // PathEngine 每帧处理寻路
```

profiler 里对应 `pathing=` 那一段。你们的 1 万单位局里，这一段通常是 0.0–0.1ms（**不是主要瓶颈**），真正贵的是 `objects=`（单位更新）和 `triggers=`。

---

## 五、可以想对策的五个点

1. **每帧算多少条**：`PathEngine` 有每帧预算，超了就排到下一帧。把这个预算做成按当前帧耗时自适应，卡的时候少算几条。
2. **同目的地合批（流场）**：500 个单位点同一个坐标时，不该算 500 次 A*。`FlowField` 已经存在，问题是什么时候触发它——按「同目的地单位数 > N」自动切过去。
3. **结果缓存**：起点格 + 终点格 + 兵种 相同就复用。现在有没有真的命中，要看 `PathEngine` 里缓存键怎么算的。
4. **动态路径降频**：追人的单位（`DynamicUnitPath`）每帧重算太贵，可以每 N 帧或目标移动超过 X 格才重算。
5. **代价图增量更新**：`PathCostMap` 现在整块重算的话，改成只更新变化的地块（有单位死亡/建筑倒塌时）。

---

## 六、建议的阅读顺序

1. `path/PathEngine.java` —— 先看它每帧做什么（不用看懂细节）。
2. `pathfinding/Path.java` —— 一条请求长什么样。
3. `pathfinding/PathCostMap.java` —— 「能不能走、多贵」怎么算的。
4. `pathfinding/RwxPathSolver2.java`（300 行，最短）—— 完整看一遍 A* 长什么样。
5. 最后再翻 `PathSolver.java`（1070 行，旧版）。

---

## 七、必须说清楚的边界

- `pathing=` 在实测的 1 万单位局里只占 0.0–0.1ms，**先优化它收益有限**。
- 上面第五节的五条是「可能的」方向，不是已验证的结论；每条都要先测再改。
- 旧求解器 `PathSolver.java` 是反编译产物，方法名是 `a/b/c/d` 这类，读起来会费劲，这是原始状态，不是我改坏的。
