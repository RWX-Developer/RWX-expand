package pathlab;

import com.corrodinggames.rts.gameFramework.pathfinding.PathPoint;
import com.corrodinggames.rts.gameFramework.pathfinding.PathSolver;
import com.corrodinggames.rts.gameFramework.pathfinding.RwxAStar;

import java.io.*;
import java.util.*;

/** 在合成地形上按移动类型测 A*：成功率、航点数、代价感知。 */
public class MapLab {
    static int[][] blind(int[][] cost) {
        int h = cost.length, w = cost[0].length;
        int[][] b = new int[h][w];
        for (int y = 0; y < h; y++) { for (int x = 0; x < w; x++) { b[y][x] = cost[y][x] == MapGen.BLOCK ? MapGen.BLOCK : 0; } }
        return b;
    }

    static int pathCost(int[][] c, List<PathPoint> path) {
        int s = 0;
        for (PathPoint p : path) { int v = c[p.tileY][p.tileX]; if (v > 0) { s += v; } }
        return s;
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

    static void writePpm(String file, int[][] terrain, int[][] cost, List<PathPoint> path, int sx, int sy, int ex, int ey, int scale) throws IOException {
        int h = terrain.length, w = terrain[0].length;
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
                int t = terrain[gy][gx];
                int r, g, b;
                if (cost[gy][gx] == MapGen.BLOCK) { r = 40; g = 40; b = 48; }
                else if (t == MapGen.WATER) { r = 60; g = 110; b = 200; }
                else if (t == MapGen.BRIDGE) { r = 170; g = 130; b = 70; }
                else if (t == MapGen.LAVA) { r = 200; g = 70; b = 40; }
                else if (t == MapGen.FOREST) { r = 120; g = 165; b = 110; }
                else if (t == MapGen.SWAMP) { r = 90; g = 110; b = 80; }
                else if (t == MapGen.CLIFF) { r = 70; g = 70; b = 70; }
                else { r = 200; g = 200; b = 205; }
                if (onPath[gy][gx]) { r = 240; g = 60; b = 60; }
                if (gx == sx && gy == sy) { r = 40; g = 220; b = 80; }
                if (gx == ex && gy == ey) { r = 60; g = 90; b = 240; }
                row.append(r).append(' ').append(g).append(' ').append(b).append(' ');
            }
            pw.println(row);
        }
        pw.close();
    }

    public static void main(String[] args) throws Exception {
        String[] presets = {"roads", "canyon", "swampfield", "river", "lava", "archipelago"};
        String[] types = {"LAND", "WATER", "HOVER"};
        int W = 230, H = 230, N = 100;
        Random rnd = new Random(20261125L);
        FileWriter csv = new FileWriter("data/map-summary.csv");
        csv.write("preset,movement,largestComponent,pairs,ok,fail_astar,fail_unreachable,avgNodes,avgSteps,avgCost,blindSteps,blindCost,worstDeviation\n");
        for (String preset : presets) {
            int[][] terrain = MapGen.generate(preset, W, H, 11L + preset.hashCode());
            for (String type : types) {
                int[][] cost = MapGen.costGrid(terrain, type);
                PathSolver solver = new PathSolver(cost);
                PathSolver solverBlind = new PathSolver(blind(cost));
                int comp = MapGen.largestComponent(cost);
                int pairs = 0, ok = 0, failA = 0, failU = 0;
                long nodes = 0, st = 0, cst = 0, bst = 0, bcst = 0;
                double worstDev = 0;
                int[] worst = null;
                List<PathPoint> worstPath = null;
                for (int i = 0; i < N; i++) {
                    int sx, sy, ex, ey, tries = 0;
                    do {
                        sx = rnd.nextInt(W); sy = rnd.nextInt(H); ex = rnd.nextInt(W); ey = rnd.nextInt(H);
                        tries++;
                        if (tries > 4000) { break; }
                    } while (cost[sy][sx] == MapGen.BLOCK || cost[ey][ex] == MapGen.BLOCK);
                    if (tries > 4000) { continue; }
                    if (!PathLab.reachable(solver, sx, sy, ex, ey)) { continue; }
                    pairs++;
                    List<PathPoint> path = RwxAStar.find(solver, sx, sy, ex, ey);
                    if (path == null) { failA++; continue; }
                    ok++;
                    nodes += path.size();
                    st += steps(path);
                    cst += pathCost(cost, path);
                    List<PathPoint> bp = RwxAStar.find(solverBlind, sx, sy, ex, ey);
                    if (bp != null) { bst += steps(bp); bcst += pathCost(cost, bp); }
                    double ldx = ex - sx, ldy = ey - sy;
                    double len = Math.sqrt(ldx * ldx + ldy * ldy);
                    double md = 0;
                    for (PathPoint p : path) {
                        if (len > 1e-6) {
                            double dev = Math.abs((p.tileX - sx) * ldy - (p.tileY - sy) * ldx) / len;
                            if (dev > md) { md = dev; }
                        }
                    }
                    if (md > worstDev) { worstDev = md; worst = new int[]{sx, sy, ex, ey}; worstPath = path; }
                }
                System.out.println("== " + preset + " / " + type + " == component=" + comp + " pairs=" + pairs + " ok=" + ok
                    + " fail_astar=" + failA
                    + (ok > 0 ? (" avgNodes=" + nodes / ok + " avgSteps=" + st / ok + " avgCost=" + cst / ok
                        + " blindCost=" + bcst / ok + " blindSteps=" + bst / ok + " worstDev=" + String.format("%.1f", worstDev)) : ""));
                csv.write(preset + "," + type + "," + comp + "," + pairs + "," + ok + "," + failA + "," + failU + ","
                    + (ok == 0 ? 0 : nodes / ok) + "," + (ok == 0 ? 0 : st / ok) + "," + (ok == 0 ? 0 : cst / ok) + ","
                    + (ok == 0 ? 0 : bst / ok) + "," + (ok == 0 ? 0 : bcst / ok) + "," + String.format("%.1f", worstDev) + "\n");
                if (worst != null && (preset.equals("river") || preset.equals("lava")) && (type.equals("WATER") || type.equals("HOVER"))) {
                    writePpm("out/comb-" + preset + "-" + type + ".ppm", terrain, cost, worstPath, worst[0], worst[1], worst[2], worst[3], 4);
                }
            }
        }
        csv.close();
        System.out.println("summary=data/map-summary.csv");
    }
}
