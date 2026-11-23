
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.io.*;

/** RWX：从运行中的进程导出地形可通行网格（寻路用的那份数据）。 */
public class TerrainDump {
    static Object f(Object o, String n) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) { try { Field fd = c.getDeclaredField(n); fd.setAccessible(true); return fd.get(o); } catch (NoSuchFieldException e) { c = c.getSuperclass(); } }
        throw new NoSuchFieldException(n);
    }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> { try { run(); } catch (Throwable e) { try { PrintWriter w = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-terrain.txt"), true); w.println("ERR " + e); w.close(); } catch (Throwable ig) {} } });
        t.setDaemon(true); t.start();
    }
    static void run() throws Exception {
        Class<?> ge = Class.forName("com.corrodinggames.rts.gameFramework.GameEngine");
        Object engine = ge.getMethod("getInstance").invoke(null);
        Object tm = f(engine, "tileMap");
        int w = (Integer) f(tm, "tileCountX"), h = (Integer) f(tm, "tileCountY");
        Object pe = f(engine, "pathfindingEngine");
        PrintWriter out = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-terrain.txt"), true);
        out.println("map=" + w + "x" + h + " engine=" + (pe != null));
        String[] names = {"landCostMap", "noneCostMap", "buildingCostMap", "airCostMap"};
        for (String nm : names) {
            Object cm = f(pe, nm);
            if (cm == null) { out.println("-- " + nm + " null"); continue; }
            byte[] d = (byte[]) f(cm, "d");
            out.println("-- " + nm + " bytes=" + d.length);
            out.println("# = 不可走, . = 可走");
            for (int y = 0; y < h; y++) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    sb.append(i < d.length && d[i] == (byte) -1 ? '#' : '.');
                }
                out.println(sb);
            }
        }
        out.close();
    }
}
