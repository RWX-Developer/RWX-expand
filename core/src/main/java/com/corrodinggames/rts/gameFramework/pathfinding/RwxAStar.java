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
