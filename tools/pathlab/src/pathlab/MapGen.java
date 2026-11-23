package pathlab;

import java.util.*;

/**
 * 合成地图生成器：先生成「地形码」，再按移动类型换算成代价网格。
 * 地形码：0 平地 / 1 林地 / 2 沼泽 / 3 水面 / 4 水桥 / 5 岩浆 / 6 悬崖(不可走)
 * 换算规则（代价 10 = 多走 1 步）：
 *   LAND   ：平地0 林地2 沼泽6 水桥0 | 水面、岩浆、悬崖 不可走
 *   WATER  ：水面0 水桥0           | 其余全部不可走
 *   HOVER  ：平地0 林地1 沼泽3 水面0 水桥0 | 岩浆、悬崖 不可走
 *   AIR    ：全部 0（不封路）
 */
public final class MapGen {
    public static final int GRASS = 0, FOREST = 1, SWAMP = 2, WATER = 3, BRIDGE = 4, LAVA = 5, CLIFF = 6;
    public static final int BLOCK = -2;

    private MapGen() {
    }

    static void fill(int[][] g, int v) {
        for (int[] row : g) { Arrays.fill(row, v); }
    }

    static void blob(int[][] g, int cx, int cy, int r, int v, Random rnd) {
        int h = g.length, w = g[0].length;
        for (int y = Math.max(0, cy - r); y < Math.min(h, cy + r); y++) {
            for (int x = Math.max(0, cx - r); x < Math.min(w, cx + r); x++) {
                int dx = x - cx, dy = y - cy;
                if (dx * dx + dy * dy <= r * r + rnd.nextInt(r + 1)) { g[y][x] = v; }
            }
        }
    }

    static void band(int[][] g, int x0, int y0, int x1, int y1, int halfW, int v, double wobble, Random rnd) {
        int h = g.length, w = g[0].length;
        int steps = Math.max(1, Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double px = x0 + (x1 - x0) * t;
            double py = y0 + (y1 - y0) * t;
            if (wobble > 0) { py += Math.sin(t * Math.PI * 3.0) * wobble; }
            for (int dy = -halfW; dy <= halfW; dy++) {
                for (int dx = -halfW; dx <= halfW; dx++) {
                    int x = (int) Math.round(px) + dx, y = (int) Math.round(py) + dy;
                    if (x < 0 || y < 0 || x >= w || y >= h) { continue; }
                    if (dx * dx + dy * dy <= halfW * halfW) { g[y][x] = v; }
                }
            }
        }
    }

    /** 横向切一刀：v 是填充物（水面或岩浆），桥上换成 BRIDGE。返回切口的中心行。 */
    static int cutAcross(int[][] g, int v, int bridges, Random rnd) {
        int h = g.length, w = g[0].length;
        int midY = h / 2 + rnd.nextInt(h / 5) - h / 10;
        for (int x = 0; x < w; x++) {
            int thick = 6 + rnd.nextInt(8);
            for (int dy = -thick; dy <= thick; dy++) {
                int y = midY + dy;
                if (y < 0 || y >= h) { continue; }
                g[y][x] = v;
            }
        }
        for (int b = 0; b < bridges; b++) {
            int bx = (w / (bridges + 1)) * (b + 1);
            bx = Math.max(1, Math.min(w - 2, bx + rnd.nextInt(12) - 6));
            for (int y = Math.max(0, midY - 14); y < Math.min(h, midY + 14); y++) {
                g[y][bx] = BRIDGE;
                if (bx + 1 < w) { g[y][bx + 1] = BRIDGE; }
            }
        }
        return midY;
    }

    public static int[][] generate(String preset, int w, int h, long seed) {
        Random rnd = new Random(seed);
        int[][] g = new int[h][w];
        fill(g, GRASS);

        if (preset.equals("roads")) {
            for (int i = 0; i < (w * h) / 220; i++) { blob(g, rnd.nextInt(w), rnd.nextInt(h), 4 + rnd.nextInt(10), FOREST, rnd); }
            for (int i = 0; i < (w * h) / 900; i++) { blob(g, rnd.nextInt(w), rnd.nextInt(h), 3 + rnd.nextInt(6), SWAMP, rnd); }
            for (int x = 20; x < w; x += 30) { band(g, x, 0, x, h - 1, 1, GRASS, 0, rnd); }
            for (int y = 20; y < h; y += 30) { band(g, 0, y, w - 1, y, 1, GRASS, 0, rnd); }
            for (int i = 0; i < 120; i++) { g[rnd.nextInt(h)][rnd.nextInt(w)] = CLIFF; }
        } else if (preset.equals("canyon")) {
            fill(g, FOREST);
            cutAcross(g, CLIFF, 0, rnd);
            int midY = h / 2;
            int pass1 = w / 4, pass2 = (3 * w) / 4;
            for (int x = pass1 - 3; x <= pass1 + 3; x++) { for (int y = midY - 12; y <= midY + 12; y++) { if (x >= 0 && x < w && y >= 0 && y < h) { g[y][x] = SWAMP; } } }
            for (int x = pass2 - 2; x <= pass2 + 2; x++) { for (int y = midY - 12; y <= midY + 12; y++) { if (x >= 0 && x < w && y >= 0 && y < h) { g[y][x] = GRASS; } } }
        } else if (preset.equals("swampfield")) {
            for (int y = 0; y < h; y++) { for (int x = 0; x < w; x++) { g[y][x] = (((x / 12) + (y / 12)) % 2 == 0) ? SWAMP : GRASS; } }
        } else if (preset.equals("river")) {
            cutAcross(g, WATER, 3, rnd);
            for (int i = 0; i < (w * h) / 300; i++) { blob(g, rnd.nextInt(w), rnd.nextInt(h), 3 + rnd.nextInt(7), FOREST, rnd); }
        } else if (preset.equals("lava")) {
            fill(g, FOREST);
            cutAcross(g, LAVA, 2, rnd);
            for (int i = 0; i < 6; i++) { blob(g, rnd.nextInt(w), rnd.nextInt(h), 3 + rnd.nextInt(5), WATER, rnd); }
            for (int i = 0; i < (w * h) / 400; i++) { blob(g, rnd.nextInt(w), rnd.nextInt(h), 3 + rnd.nextInt(6), SWAMP, rnd); }
        } else if (preset.equals("archipelago")) {
            fill(g, WATER);
            for (int i = 0; i < 14; i++) { blob(g, rnd.nextInt(w), rnd.nextInt(h), 10 + rnd.nextInt(16), GRASS, rnd); }
            for (int i = 0; i < 3; i++) { band(g, rnd.nextInt(w), 30, rnd.nextInt(w), h - 30, 1, BRIDGE, 0, rnd); }
        } else {
            throw new IllegalArgumentException("unknown preset: " + preset);
        }
        return g;
    }

    /** 按移动类型把地形码换成代价网格。语义与游戏导出的一致：-2 不可走，其余是代价。 */
    public static int[][] costGrid(int[][] terrain, String movementType) {
        int h = terrain.length, w = terrain[0].length;
        int[][] c = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int t = terrain[y][x];
                int v;
                switch (movementType) {
                    case "LAND":
                        switch (t) {
                            case GRASS: v = 0; break;
                            case FOREST: v = 2; break;
                            case SWAMP: v = 6; break;
                            case BRIDGE: v = 0; break;
                            default: v = BLOCK;
                        }
                        break;
                    case "WATER":
                        v = (t == WATER || t == BRIDGE) ? 0 : BLOCK;
                        break;
                    case "HOVER":
                        switch (t) {
                            case GRASS: v = 0; break;
                            case FOREST: v = 1; break;
                            case SWAMP: v = 3; break;
                            case WATER: v = 0; break;
                            case BRIDGE: v = 0; break;
                            default: v = BLOCK;
                        }
                        break;
                    case "AIR":
                        v = 0;
                        break;
                    default:
                        v = BLOCK;
                }
                c[y][x] = v;
            }
        }
        return c;
    }

    public static int largestComponent(int[][] cost) {
        int h = cost.length, w = cost[0].length;
        boolean[] seen = new boolean[w * h];
        int best = 0;
        int[] stack = new int[w * h];
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (seen[i] || cost[y][x] == BLOCK) { continue; }
                int sp = 0, size = 0;
                stack[sp++] = i;
                seen[i] = true;
                while (sp > 0) {
                    int cur = stack[--sp];
                    size++;
                    int cx = cur % w, cy = cur / w;
                    for (int d = 0; d < 4; d++) {
                        int nx = cx + dx[d], ny = cy + dy[d];
                        if (nx < 0 || ny < 0 || nx >= w || ny >= h) { continue; }
                        int ni = ny * w + nx;
                        if (seen[ni] || cost[ny][nx] == BLOCK) { continue; }
                        seen[ni] = true;
                        stack[sp++] = ni;
                    }
                }
                if (size > best) { best = size; }
            }
        }
        return best;
    }

    public static void writeCsv(String path, int[][] g) throws java.io.IOException {
        java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(path));
        pw.println(g[0].length + "," + g.length);
        for (int[] row : g) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < row.length; i++) { if (i > 0) { sb.append(','); } sb.append(row[i]); }
            pw.println(sb);
        }
        pw.close();
    }
}
