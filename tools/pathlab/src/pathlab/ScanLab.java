package pathlab;

import com.corrodinggames.rts.gameFramework.pathfinding.PathPoint;
import com.corrodinggames.rts.gameFramework.pathfinding.PathSolver;
import com.corrodinggames.rts.gameFramework.pathfinding.RwxAStar;

import java.io.*;
import java.util.*;

/** 读游戏里 g() 的记录（logs/rwx-scan.csv），用真实代价图上的 A* 重放同一批起终点做对照。 */
public class ScanLab {
    public static void main(String[] args) throws Exception {
        String csv = args.length > 0 ? args[0] : "data/rwx-scan.csv";
        Map<String, PathSolver> solvers = new HashMap<>();
        for (String t : new String[]{"LAND", "WATER", "HOVER", "AIR"}) {
            File f = new File("data/real-" + t + ".csv");
            if (f.exists()) { solvers.put(t, new PathSolver(PathLab.load(f.getPath()))); }
        }
        File f = new File(csv);
        if (!f.exists()) { System.out.println("no scan log yet: " + csv); return; }
        BufferedReader br = new BufferedReader(new FileReader(f));
        String line = br.readLine();
        int total = 0, early = 0, astarOk = 0, astarFailUnreach = 0, astarFailBudget = 0, innerPaths = 0, innerBorder = 0;
        Map<String, Integer> byType = new TreeMap<>();
        while ((line = br.readLine()) != null) {
            String[] c = line.split(",");
            if (c.length < 8) { continue; }
            total++;
            String tag = c[0], mtype = c[7];
            byType.merge(mtype, 1, Integer::sum);
            if (c[3].equals("-1")) { early++; continue; }
            PathSolver s = solvers.get(mtype);
            if (s == null) { s = solvers.get("LAND"); }
            if (s == null) { continue; }
            int sx = Integer.parseInt(c[3]), sy = Integer.parseInt(c[4]), ex = Integer.parseInt(c[5]), ey = Integer.parseInt(c[6]);
            if (sx < 0 || sy < 0 || ex < 0 || ey < 0 || sx >= s.g || ex >= s.g || sy >= s.h || ey >= s.h) { continue; }
            List<PathPoint> path = RwxAStar.find(s, sx, sy, ex, ey);
            if (path == null) {
                if (PathLab.reachable(s, sx, sy, ex, ey)) { astarFailBudget++; } else { astarFailUnreach++; }
                continue;
            }
            astarOk++;
            boolean endNear = PathLab.nearBorder(sx, sy, s.g, s.h) || PathLab.nearBorder(ex, ey, s.g, s.h);
            if (!endNear) {
                innerPaths++;
                for (PathPoint pt : path) { if (PathLab.nearBorder(pt.tileX, pt.tileY, s.g, s.h)) { innerBorder++; } }
            }
        }
        br.close();
        System.out.println("g() 记录=" + total + " early(-1)=" + early + " 类型分布=" + byType);
        System.out.println("A* 重放: ok=" + astarOk + " fail_unreachable=" + astarFailUnreach + " fail_budget=" + astarFailBudget
            + " 内点路径=" + innerPaths + " 其中贴边航点=" + innerBorder);
    }
}
