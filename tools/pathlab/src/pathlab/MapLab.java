package pathlab;

import com.corrodinggames.rts.gameFramework.pathfinding.PathPoint;
import com.corrodinggames.rts.gameFramework.pathfinding.PathSolver;
import com.corrodinggames.rts.gameFramework.pathfinding.RwxAStar;

import java.io.*;
import java.util.*;

/** 在合成代价地图上测 A*：成功率、航点数、代价感知（会不会绕开贵地形）。 */
public class MapLab {
    static int[][] blind(int[][] g) {
        int h = g.length, w = g[0].length;
        int[][] b = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) { b[y][x] = g[y][x] == MapGen.BLOCK ? MapGen.BLOCK : 0; }
        }
        return b;
    }

    static int pathCost(int[][] g, List<PathPoint> path) {
        int sum = 0;
        for (PathPoint p : path) { int v = g[p.tileY][p.tileX]; if (v > 0) { sum += v; } }
        return sum;
    }

    static int swampTiles(int[][] g, List<PathPoint> path) {
        int n = 0;
        for (PathPoint p : path) { if (g[p.tileY][p.tileX] == MapGen.SWAMP) { n++; } }
        return n;
    }

    static int steps(List<PathPoint> path) {
        int s = 0;
        for (int i = 1; i < path.size(); i++) {
            PathPoint a = path.get(i - 1), b = path.get(i);
            int dx = Math.abs(a.tileX - b.tileX), dy = Math.abs(a.tileY - b.tileY);
            s += (dx > 1 || dy > 1) ? (int) Math.round(Math.sqrt(dx * dx + dy * dy)) : Math.max(dx, dy);
        }
        return s;
    }

    static void writePpm(String file, int[][] g, List<PathPoint> path, int sx, int sy, int ex, int ey, int scale) throws IOException {
        int h = g.length, w = g[0].length;
        boolean[][] onPath = new boolean[h][w];
        if (path != null) { for (PathPoint p : path) { onPath[p.tileY][p.tileX] = true; } }
        PrintWriter pw = new PrintWriter(new FileWriter(file));
        pw.println("P3");
        pw.println((w * scale) + " " + (h * scale));
        pw.println("255");
        for (int y = 0; y < h * scale; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < w * scale; x++) {
                int gx = x / scale, gy = y / scale;
                int v = g[gy][gx];
                int r = 200, gg = 200, b = 205;
                if (v == MapGen.BLOCK) { r = 32; gg = 32; b = 40; }
                else if (v == MapGen.FOREST) { r = 120; gg = 165; b = 110; }
                else if (v == MapGen.SWAMP) { r = 90; gg = 110; b = 80; }
                if (onPath[gy][gx]) { r = 224; gg = 48; b = 48; }
                if (gx == sx && gy == sy) { r = 48; gg = 192; b = 64; }
                if (gx == ex && gy == ey) { r = 48; gg = 80; b = 224; }
                row.append(r).append(' ').append(gg).append(' ').append(b).append(' ');
            }
            pw.println(row);
        }
        pw.close();
    }

    /** 在最大连通块里随机取点，保证两端可达。 */
    static int[] pickPair(int[][] g, Random rnd) {
        int h = g.length, w = g[0].length;
        for (int tries = 0; tries < 10000; tries++) {
            int sx = rnd.nextInt(w), sy = rnd.nextInt(h);
            int ex = rnd.nextInt(w), ey = rnd.nextInt(h);
            if (g[sy][sx] == MapGen.BLOCK || g[ey][ex] == MapGen.BLOCK) { continue; }
            return new int[]{sx, sy, ex, ey};
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        String[] presets = {"roads", "canyon", "swampfield", "river", "archipelago"};
        int W = 230, H = 230, N = 120;
        Random rnd = new Random(20261124L);
        for (String preset : presets) {
            int[][] g = MapGen.generate(preset, W, H, 7L + preset.hashCode());
            MapGen.writeCsv("data/gen-" + preset + ".csv", g);
            int comp = MapGen.largestComponent(g);
            int blocked = 0, forest = 0, swamp = 0;
            for (int[] row : g) { for (int v : row) { if (v == MapGen.BLOCK) { blocked++; } else if (v == MapGen.FOREST) { forest++; } else if (v == MapGen.SWAMP) { swamp++; } } }
            PathSolver solver = new PathSolver(g);
            PathSolver solverBlind = new PathSolver(blind(g));

            int ok = 0, failUnreach = 0, failAstar = 0;
            long nodes = 0, stepsSum = 0, costSum = 0, swampSum = 0;
            long blindSteps = 0, blindCost = 0, blindSwamp = 0;
            double worstDev = 0;
            int[] worst = null;
            for (int i = 0; i < N; i++) {
                int[] pp = pickPair(g, rnd);
                if (pp == null) { continue; }
                List<PathPoint> path = RwxAStar.find(solver, pp[0], pp[1], pp[2], pp[3]);
                if (path == null) {
                    if (PathLab.reachable(solver, pp[0], pp[1], pp[2], pp[3])) { failAstar++; } else { failUnreach++; }
                    continue;
                }
                List<PathPoint> pathBlind = RwxAStar.find(solverBlind, pp[0], pp[1], pp[2], pp[3]);
                ok++;
                nodes += path.size();
                int st = steps(path);
                stepsSum += st;
                int pc = pathCost(g, path);
                costSum += pc;
                swampSum += swampTiles(g, path);
                if (pathBlind != null) {
                    blindSteps += steps(pathBlind);
                    blindCost += pathCost(g, pathBlind);
                    blindSwamp += swampTiles(g, pathBlind);
                }
                double ldx = pp[2] - pp[0], ldy = pp[3] - pp[1];
                double len = Math.sqrt(ldx * ldx + ldy * ldy);
                double md = 0;
                for (PathPoint p : path) {
                    if (len > 1e-6) {
                        double dev = Math.abs((p.tileX - pp[0]) * ldy - (p.tileY - pp[1]) * ldx) / len;
                        if (dev > md) { md = dev; }
                    }
                }
                if (md > worstDev) { worstDev = md; worst = pp; }
            }
            System.out.println("== " + preset + " ==");
            System.out.println("  blocked=" + blocked + " forest=" + forest + " swamp=" + swamp
                + " largestComponent=" + comp + "/" + (W * H));
            System.out.println("  ok=" + ok + " fail_unreachable=" + failUnreach + " fail_astar=" + failAstar
                + (ok > 0 ? (" avgNodes=" + nodes / ok + " avgSteps=" + stepsSum / ok + " avgCost=" + costSum / ok + " avgSwampTiles=" + swampSum / ok) : ""));
            if (ok > 0) {
                System.out.println("  cost-blind baseline: avgSteps=" + blindSteps / ok + " avgCost=" + blindCost / ok + " avgSwampTiles=" + blindSwamp / ok);
            }
            System.out.println("  worstDeviation=" + String.format("%.1f", worstDev) + (worst == null ? "" : (" case=" + worst[0] + "," + worst[1] + " -> " + worst[2] + "," + worst[3])));
            if (worst != null) {
                List<PathPoint> wp = RwxAStar.find(solver, worst[0], worst[1], worst[2], worst[3]);
                writePpm("out/gen-" + preset + ".ppm", g, wp, worst[0], worst[1], worst[2], worst[3], 4);
            }
        }
    }
}
