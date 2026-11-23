package com.corrodinggames.rts.gameFramework.pathfinding;

/**
 * 实验台用的 PathSolver 桩。
 * 真引擎里 a(x,y) = 地形层 + 建筑层 + 物体层*10，任一为 -1 就返回 -1。
 * 这里直接把导出的代价网格喂进来，语义一致：-1 = 不可走，其余 = 该格代价。
 */
public class PathSolver {
    public int g;
    public int h;
    public int[][] cost;

    public PathSolver(int[][] cost) {
        this.cost = cost;
        this.h = cost.length;
        this.g = cost[0].length;
    }

    public int a(int x, int y) {
        if (x < 0 || y < 0 || x >= this.g || y >= this.h) { return -1; }
        int v = this.cost[y][x];
        return v == -2 ? -1 : v;
    }
}
