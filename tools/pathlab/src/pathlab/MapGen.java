package pathlab;

import java.util.*;

/**
 * 合成代价地图生成器（为将来地图编辑器积累数据格式经验）。
 * 输出：int[h][w]，-2 = 不可走，其余 = 该格代价（0 平地 / 2 林地 / 6 沼泽）。
 * 约定与游戏导出的 cost-LAND.csv 完全一致，实验台可直接读。
 */
public final class MapGen {
    public static final int BLOCK = -2;
    public static final int GRASS = 0;
    public static final int FOREST = 2;
    public static final int SWAMP = 6;

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

    /** 一条从 (x0,y0) 到 (x1,y1) 的带状区域，宽度 halfW。 */
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

    /** 随机散步的河，把地图切开，桥上留可走。 */
    static void river(int[][] g, Random rnd, int v) {
        int h = g.length, w = g[0].length;
        int y = h / 3 + rnd.nextInt(h / 3);
        for (int x = 0; x < w; x++) {
            y += rnd.nextInt(3) - 1;
            y = Math.max(2, Math.min(h - 3, y));
            int halfW = 2 + rnd.nextInt(2);
            for (int dy = -halfW; dy <= halfW; dy++) {
                int yy = y + dy;
                if (yy < 0 || yy >= h) { continue; }
                g[yy][x] = v;
            }
        }
        // 三座桥：把河带改成可走
        for (int b = 0; b < 3; b++) {
            int bx = (w / 4) * (b + 1) - 10;
            bx = Math.max(1, Math.min(w - 2, bx + rnd.nextInt(18)));
            for (int dy = -6; dy <= 6; dy++) {
                int yy = y + dy;
                if (yy < 0 || yy >= h) { continue; }
                g[yy][bx] = GRASS;
            }
        }
    }

    public static int[][] generate(String preset, int w, int h, long seed) {
        Random rnd = new Random(seed);
        int[][] g = new int[h][w];
        fill(g, GRASS);

        if (preset.equals("roads")) {
            // 大片林地 + 沼泽，中间一张稀疏路网（代价 0）
            for (int i = 0; i < (w * h) / 220; i++) {
                blob(g, rnd.nextInt(w), rnd.nextInt(h), 4 + rnd.nextInt(10), FOREST, rnd);
            }
            for (int i = 0; i < (w * h) / 900; i++) {
                blob(g, rnd.nextInt(w), rnd.nextInt(h), 3 + rnd.nextInt(6), SWAMP, rnd);
            }
            for (int x = 20; x < w; x += 30) { band(g, x, 0, x, h - 1, 1, GRASS, 0, rnd); }
            for (int y = 20; y < h; y += 30) { band(g, 0, y, w - 1, y, 1, GRASS, 0, rnd); }
            // 一点点障碍，让路网有意义
            for (int i = 0; i < 120; i++) {
                int bx = rnd.nextInt(w), by = rnd.nextInt(h);
                g[by][bx] = BLOCK;
            }
        } else if (preset.equals("canyon")) {
            // 一道横贯地图的山脉，只有 2 个隘口
            fill(g, FOREST);
            int midY = h / 2;
            for (int x = 0; x < w; x++) {
                int thick = 8 + rnd.nextInt(6);
                for (int dy = -thick; dy <= thick; dy++) {
                    int y = midY + dy;
                    if (y < 0 || y >= h) { continue; }
                    g[y][x] = BLOCK;
                }
            }
            int pass1 = w / 4, pass2 = (3 * w) / 4;
            for (int x = pass1 - 3; x <= pass1 + 3; x++) {
                for (int y = midY - 12; y <= midY + 12; y++) {
                    if (x >= 0 && x < w && y >= 0 && y < h) { g[y][x] = SWAMP; }
                }
            }
            for (int x = pass2 - 2; x <= pass2 + 2; x++) {
                for (int y = midY - 12; y <= midY + 12; y++) {
                    if (x >= 0 && x < w && y >= 0 && y < h) { g[y][x] = GRASS; }
                }
            }
        } else if (preset.equals("swampfield")) {
            // 棋盘状沼泽：最优解必须走折线，直连必穿沼泽
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    boolean even = ((x / 12) + (y / 12)) % 2 == 0;
                    g[y][x] = even ? SWAMP : GRASS;
                }
            }
        } else if (preset.equals("archipelago")) {
            // 散岛：大片不可走，测「真不可达」和「A* 放弃」的区分
            fill(g, BLOCK);
            for (int i = 0; i < 14; i++) {
                int cx = rnd.nextInt(w), cy = rnd.nextInt(h);
                int r = 10 + rnd.nextInt(16);
                blob(g, cx, cy, r, GRASS, rnd);
            }
        } else if (preset.equals("river")) {
            river(g, rnd, BLOCK);
            for (int i = 0; i < (w * h) / 300; i++) {
                blob(g, rnd.nextInt(w), rnd.nextInt(h), 3 + rnd.nextInt(7), FOREST, rnd);
            }
        } else {
            throw new IllegalArgumentException("unknown preset: " + preset);
        }
        return g;
    }

    /** 确保至少存在一个连通块（起点可用）。返回最大的可走连通块大小。 */
    public static int largestComponent(int[][] g) {
        int h = g.length, w = g[0].length;
        boolean[] seen = new boolean[w * h];
        int best = 0;
        int[] stack = new int[w * h];
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                if (seen[i] || g[y][x] == BLOCK) { continue; }
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
                        if (seen[ni] || g[ny][nx] == BLOCK) { continue; }
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
        int h = g.length, w = g[0].length;
        java.io.PrintWriter pw = new java.io.PrintWriter(new java.io.FileWriter(path));
        pw.println(w + "," + h);
        for (int y = 0; y < h; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < w; x++) {
                if (x > 0) { sb.append(','); }
                sb.append(g[y][x]);
            }
            pw.println(sb);
        }
        pw.close();
    }
}
