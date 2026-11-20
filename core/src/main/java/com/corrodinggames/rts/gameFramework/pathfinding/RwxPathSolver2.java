package com.corrodinggames.rts.gameFramework.pathfinding;

import com.corrodinggames.rts.game.units.UnitMovementType;
import com.corrodinggames.rts.gameFramework.path.PathEngine;
import java.util.LinkedList;

/**
 * RWX 并行求解器 v2（-Drwx.pathing=2 启用，默认仍走旧 PathSolver.e()）。
 *
 * 设计取舍：
 * - 只读旧求解器的数据源（b/c/d 成本图、e 地形组、f 净空、g/h 尺寸），不改旧文件内部；
 * - 自己维护 A* 的 open/closed/parent，不碰旧求解器的 l/m 簿记数组；
 * - 目标策略改成显式：目标不可达时只在半径内找可达点，且**不把地图边界环当选点**；
 *   半径内没有就判失败（path.endUnreachable=true，返回空表），不再扫全图找「最近可达点」。
 *
 * 8 向移动；禁止切角（对角要求两侧正交格都可走）；启发式用 octile 10/14（可采纳）。
 */
public final class RwxPathSolver2 {

    private RwxPathSolver2() {
    }

    /** 目标吸附半径（格）。 */
    private static final int RWX_GOAL_SNAP_RADIUS = 4;
    /** 起点脱困半径（格）。 */
    private static final int RWX_START_SNAP_RADIUS = 2;
    /** 单条路径最大扩展节点数，超了判定失败，避免一条烂路径拖住整帧。 */
    private static final int RWX_MAX_EXPAND = 40000;

    private static int solved;
    private static int snapped;
    private static int failed;
    private static long totalExpanded;

    private static final int[] DX = {-1, 1, 0, 0, -1, -1, 1, 1};
    private static final int[] DY = {0, 0, -1, 1, -1, 1, -1, 1};

    public static LinkedList<PathPoint> solve(PathSolver solver, Path path) {
        LinkedList<PathPoint> out = new LinkedList<PathPoint>();
        int w = solver.g;
        int h = solver.h;
        int startX = path.startTileX;
        int startY = path.startTileY;
        int endX = path.endTileX;
        int endY = path.endTileY;

        if (path.movementType == null || path.movementType == UnitMovementType.NONE) {
            return out;
        }
        if (startX == endX && startY == endY) {
            out.add(new PathPoint((short) endX, (short) endY));
            return out;
        }

        // 起点：走不了就在小半径内挪一格（不挪到边界环）
        if (cost(solver, startX, startY) == -1) {
            int[] s = nearestWalkable(solver, startX, startY, RWX_START_SNAP_RADIUS, false);
            if (s == null) {
                path.endUnreachable = true;
                failed++;
                return out;
            }
            startX = s[0];
            startY = s[1];
        }

        // 目标：不可走/越界时只在半径内找，边界环不当候选
        boolean goalSnapped = false;
        if (endX < 0 || endY < 0 || endX >= w || endY >= h || cost(solver, endX, endY) == -1) {
            boolean goalNearBorder = endX <= 2 || endY <= 2 || endX >= w - 3 || endY >= h - 3;
            int[] g2 = nearestWalkable(solver, clamp(endX, 0, w - 1), clamp(endY, 0, h - 1), RWX_GOAL_SNAP_RADIUS, !goalNearBorder);
            if (g2 == null) {
                path.endUnreachable = true;
                failed++;
                return out;
            }
            endX = g2[0];
            endY = g2[1];
            goalSnapped = true;
        }

        int total = w * h;
        int[] gScore = new int[total];
        int[] parent = new int[total];
        int[] stamp = new int[total];
        long[] heap = new long[1024];
        int heapSize = 0;
        int curStamp = ++stampCounter;
        if (curStamp == 0) {
            java.util.Arrays.fill(stamp, 0);
            curStamp = ++stampCounter;
        }
        java.util.Arrays.fill(parent, -1);

        int startIdx = startX * h + startY;
        int goalIdx = endX * h + endY;
        gScore[startIdx] = 0;
        stamp[startIdx] = curStamp;
        heap[heapSize++] = pack(heuristic(startX, startY, endX, endY), startIdx);

        int expanded = 0;
        int found = -1;
        boolean closedReached = false;
        int[] closedStamp = new int[total];
        int closedCur = 1;

        while (heapSize > 0) {
            long top = pop(heap, heapSize--);
            int idx = (int) (top & 0xFFFFFFFFL);
            int g = gScore[idx];
            if (idx == goalIdx) {
                found = idx;
                break;
            }
            if (closedStamp[idx] == closedCur) {
                continue;
            }
            closedStamp[idx] = closedCur;
            if (++expanded > RWX_MAX_EXPAND) {
                closedReached = true;
                break;
            }
            int x = idx / h;
            int y = idx - x * h;
            for (int d = 0; d < 8; d++) {
                int nx = x + DX[d];
                int ny = y + DY[d];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) {
                    continue;
                }
                if (d >= 4) {
                    // 不切角：对角要求两侧正交格都通
                    if (cost(solver, x + DX[d], y) == -1 || cost(solver, x, y + DY[d]) == -1) {
                        continue;
                    }
                }
                int nc = cost(solver, nx, ny);
                if (nc == -1) {
                    continue;
                }
                int nIdx = nx * h + ny;
                if (closedStamp[nIdx] == closedCur) {
                    continue;
                }
                int step = (d >= 4 ? 14 : 10) + nc;
                int ng = g + step;
                if (stamp[nIdx] == curStamp && ng >= gScore[nIdx]) {
                    continue;
                }
                stamp[nIdx] = curStamp;
                gScore[nIdx] = ng;
                parent[nIdx] = idx;
                if (heapSize + 1 >= heap.length) {
                    heap = java.util.Arrays.copyOf(heap, heap.length * 2);
                }
                heap[heapSize++] = pack(ng + heuristic(nx, ny, endX, endY), nIdx);
            }
        }

        totalExpanded += expanded;
        solved++;
        if (found < 0) {
            path.endUnreachable = true;
            failed++;
            maybeLog(expanded, w, h, endX, endY, closedReached);
            return out;
        }

        // 重建：goal -> start，再反转成 start 优先，最后去掉起点（与旧实现的输出约定一致）
        LinkedList<PathPoint> rev = new LinkedList<PathPoint>();
        int cur = found;
        int guard = total + 4;
        while (cur >= 0 && guard-- > 0) {
            int x = cur / h;
            int y = cur - x * h;
            rev.add(new PathPoint((short) x, (short) y));
            if (cur == startIdx) {
                break;
            }
            cur = parent[cur];
        }
        for (PathPoint p : rev) {
            out.addFirst(p);
        }
        if (out.size() > 1) {
            out.remove(0);
        }
        path.endUnreachable = goalSnapped;
        if (goalSnapped) {
            snapped++;
        }
        maybeLog(expanded, w, h, endX, endY, false);
        return out;
    }

    private static void maybeLog(int expanded, int w, int h, int endX, int endY, boolean budget) {
        if (solved % 50 != 0) {
            return;
        }
        io.github.rwx.probe.RwxPerf.out("RWX_PATH2 solved=" + solved + " snapped=" + snapped + " failed=" + failed
                + " avgExpand=" + (totalExpanded / Math.max(1, solved)) + " last=" + expanded
                + " map=" + w + "x" + h + " end=" + endX + "," + endY + (budget ? " BUDGET" : ""));
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private static int stampCounter = 0;

    private static long pack(int f, int idx) {
        return ((long) f << 32) | (idx & 0xFFFFFFFFL);
    }

    private static int heuristic(int x, int y, int gx, int gy) {
        int dx = Math.abs(x - gx);
        int dy = Math.abs(y - gy);
        int max = Math.max(dx, dy);
        int min = Math.min(dx, dy);
        return 10 * max + 4 * min;
    }

    private static long pop(long[] heap, int size) {
        if (size < 0) {
            return -1L;
        }
        long top = heap[0];
        long last = heap[size];
        heap[0] = last;
        int i = 0;
        int n = size;
        while (true) {
            int l = i * 2 + 1;
            if (l >= n) {
                break;
            }
            int r = l + 1;
            int c = (r < n && heap[r] < heap[l]) ? r : l;
            if (heap[c] >= heap[i]) {
                break;
            }
            long tmp = heap[i];
            heap[i] = heap[c];
            heap[c] = tmp;
            i = c;
        }
        return top;
    }

    private static int cost(PathSolver solver, int x, int y) {
        int w = solver.g;
        int h = solver.h;
        if (x < 0 || y < 0 || x >= w || y >= h) {
            return -1;
        }
        int idx = x * h + y;
        byte mb = solver.b[idx];
        byte bb = solver.c[idx];
        byte ob = solver.d[idx];
        if (mb == -1 || bb == -1 || ob == -1) {
            return -1;
        }
        return mb + bb + (ob * 10);
    }

    /**
     * 在半径内找最近的可行格。excludeBorderRing=true 时不接受地图最外一圈，
     * 避免「最近可达点」正好是边界格（旧的边缘路径问题就是这么来的）。
     */
    private static int[] nearestWalkable(PathSolver solver, int cx, int cy, int radius, boolean excludeBorderRing) {
        int w = solver.g;
        int h = solver.h;
        int best = -1;
        int bestX = -1;
        int bestY = -1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int x = cx + dx;
                int y = cy + dy;
                if (x < 0 || y < 0 || x >= w || y >= h) {
                    continue;
                }
                if (excludeBorderRing && (x == 0 || y == 0 || x == w - 1 || y == h - 1)) {
                    continue;
                }
                if (cost(solver, x, y) == -1) {
                    continue;
                }
                int d = dx * dx + dy * dy;
                if (best == -1 || d < best) {
                    best = d;
                    bestX = x;
                    bestY = y;
                }
            }
        }
        return best == -1 ? null : new int[]{bestX, bestY};
    }
}
