package pathlab;

import com.corrodinggames.rts.gameFramework.pathfinding.PathPoint;
import com.corrodinggames.rts.gameFramework.pathfinding.PathSolver;
import com.corrodinggames.rts.gameFramework.pathfinding.RwxAStar;

import java.io.*;
import java.util.*;

/** 寻路实验台：完全脱离游戏，喂真实代价网格，跑真 A*，出统计和叠加图。 */
public class PathLab {
    static int[][] load(String path) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(path));
        String[] hw = br.readLine().split(",");
        int w = Integer.parseInt(hw[0].trim()), h = Integer.parseInt(hw[1].trim());
        int[][] grid = new int[h][w];
        for (int y = 0; y < h; y++) {
            String[] cells = br.readLine().split(",");
            for (int x = 0; x < w; x++) { grid[y][x] = Integer.parseInt(cells[x].trim()); }
        }
        br.close();
        return grid;
    }

    static boolean nearBorder(int x, int y, int w, int h) {
        return x <= 2 || y <= 2 || x >= w - 3 || y >= h - 3;
    }

    /** 4 邻接泛洪：判断两点在网格上是否连通（用来区分「真不可达」和「A* 放弃了」）。 */
    static boolean reachable(PathSolver s, int sx, int sy, int ex, int ey) {
        if (s.a(sx, sy) == -1 || s.a(ex, ey) == -1) { return false; }
        boolean[] seen = new boolean[s.g * s.h];
        int[] stack = new int[s.g * s.h];
        int sp = 0;
        stack[sp++] = sy * s.g + sx;
        seen[sy * s.g + sx] = true;
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        while (sp > 0) {
            int cur = stack[--sp];
            int cx = cur % s.g, cy = cur / s.g;
            if (cx == ex && cy == ey) { return true; }
            for (int d = 0; d < 4; d++) {
                int nx = cx + dx[d], ny = cy + dy[d];
                if (nx < 0 || ny < 0 || nx >= s.g || ny >= s.h) { continue; }
                int ni = ny * s.g + nx;
                if (seen[ni] || s.a(nx, ny) == -1) { continue; }
                seen[ni] = true;
                stack[sp++] = ni;
            }
        }
        return false;
    }

    static void writePpm(String file, PathSolver s, List<PathPoint> path, int sx, int sy, int ex, int ey) throws IOException {
        int w = s.g, h = s.h;
        PrintWriter pw = new PrintWriter(new FileWriter(file));
        pw.println("P3");
        pw.println(w + " " + h);
        pw.println("255");
        boolean[][] onPath = new boolean[h][w];
        if (path != null) { for (PathPoint p : path) { onPath[p.tileY][p.tileX] = true; } }
        for (int y = 0; y < h; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = 0; x < w; x++) {
                int r = 200, g = 200, b = 205;
                if (s.a(x, y) == -1) { r = 32; g = 32; b = 40; }
                if (onPath[y][x]) { r = 224; g = 48; b = 48; }
                if (x == sx && y == sy) { r = 48; g = 192; b = 64; }
                if (x == ex && y == ey) { r = 48; g = 80; b = 224; }
                row.append(r).append(' ').append(g).append(' ').append(b).append(' ');
            }
            pw.println(row);
        }
        pw.close();
    }

    static String stats(PathSolver s, List<PathPoint> path, int sx, int sy, int ex, int ey) {
        int borderHits = 0;
        double maxDev = 0;
        double ldx = ex - sx, ldy = ey - sy;
        double len = Math.sqrt(ldx * ldx + ldy * ldy);
        for (PathPoint p : path) {
            if (nearBorder(p.tileX, p.tileY, s.g, s.h)) { borderHits++; }
            if (len > 1e-6) {
                double dev = Math.abs((p.tileX - sx) * ldy - (p.tileY - sy) * ldx) / len;
                if (dev > maxDev) { maxDev = dev; }
            }
        }
        return "nodes=" + path.size() + " borderHits=" + borderHits + " maxDev=" + String.format("%.1f", maxDev);
    }

    public static void main(String[] args) throws Exception {
        PathSolver solver = new PathSolver(load(args[0]));
        System.out.println("map=" + solver.g + "x" + solver.h);

        if (args.length >= 5) {
            int sx = Integer.parseInt(args[1]), sy = Integer.parseInt(args[2]);
            int ex = Integer.parseInt(args[3]), ey = Integer.parseInt(args[4]);
            List<PathPoint> path = RwxAStar.find(solver, sx, sy, ex, ey);
            System.out.println(path == null ? "FAIL no path (reachable=" + reachable(solver, sx, sy, ex, ey) + ")" : stats(solver, path, sx, sy, ex, ey));
            writePpm("out/path.ppm", solver, path, sx, sy, ex, ey);
            System.out.println("overlay=out/path.ppm");
            return;
        }

        Random rnd = new Random(20261123L);
        int n = 300, ok = 0, failUnreachable = 0, failAstar = 0, totalNodes = 0, totalBorder = 0, pairsEndNearBorder = 0;
        double worstDev = 0;
        int wsx = 0, wsy = 0, wex = 0, wey = 0;
        List<PathPoint> worstPath = null;
        List<PathPoint> borderCase = null;
        int bcx = 0, bcy = 0, bcEx = 0, bcEy = 0;
        for (int i = 0; i < n; i++) {
            int sx, sy, ex, ey;
            do { sx = rnd.nextInt(solver.g); sy = rnd.nextInt(solver.h); } while (solver.a(sx, sy) == -1);
            do { ex = rnd.nextInt(solver.g); ey = rnd.nextInt(solver.h); } while (solver.a(ex, ey) == -1);
            boolean endNear = nearBorder(sx, sy, solver.g, solver.h) || nearBorder(ex, ey, solver.g, solver.h);
            if (endNear) { pairsEndNearBorder++; }
            List<PathPoint> path = RwxAStar.find(solver, sx, sy, ex, ey);
            if (path == null) {
                if (reachable(solver, sx, sy, ex, ey)) { failAstar++; } else { failUnreachable++; }
                continue;
            }
            ok++;
            totalNodes += path.size();
            int bh = 0;
            double ldx = ex - sx, ldy = ey - sy;
            double len = Math.sqrt(ldx * ldx + ldy * ldy);
            double md = 0;
            for (PathPoint p : path) {
                if (nearBorder(p.tileX, p.tileY, solver.g, solver.h)) { bh++; }
                if (len > 1e-6) {
                    double dev = Math.abs((p.tileX - sx) * ldy - (p.tileY - sy) * ldx) / len;
                    if (dev > md) { md = dev; }
                }
            }
            totalBorder += bh;
            if (bh > 0 && !endNear && borderCase == null) { borderCase = path; bcx = sx; bcy = sy; bcEx = ex; bcEy = ey; }
            if (md > worstDev) { worstDev = md; wsx = sx; wsy = sy; wex = ex; wey = ey; worstPath = path; }
        }
        System.out.println("cases=" + n + " ok=" + ok + " fail_unreachable=" + failUnreachable + " fail_astar=" + failAstar
            + " avgNodes=" + (ok == 0 ? 0 : totalNodes / ok) + " borderHitsTotal=" + totalBorder + " pairsEndNearBorder=" + pairsEndNearBorder);
        System.out.println("worstDeviation=" + String.format("%.1f", worstDev) + " case=" + wsx + "," + wsy + " -> " + wex + "," + wey);
        writePpm("out/worst.ppm", solver, worstPath, wsx, wsy, wex, wey);
        System.out.println("worstOverlay=out/worst.ppm");
        if (borderCase != null) {
            writePpm("out/border.ppm", solver, borderCase, bcx, bcy, bcEx, bcEy);
            System.out.println("borderCase=" + bcx + "," + bcy + " -> " + bcEx + "," + bcEy + " " + stats(solver, borderCase, bcx, bcy, bcEx, bcEy));
        }
    }
}
