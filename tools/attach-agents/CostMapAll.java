
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.io.*;

/** RWX：把 8 张 PathCostMap 全部导出成 CSV（-2 不可走，其余代价）。 */
public class CostMapAll {
    static Object f(Object o, String n) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) { try { Field fd = c.getDeclaredField(n); fd.setAccessible(true); return fd.get(o); } catch (NoSuchFieldException e) { c = c.getSuperclass(); } }
        throw new NoSuchFieldException(n);
    }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> { try { run(); } catch (Throwable e) { try { PrintWriter w = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-costall-err.txt"), true); w.println("ERR " + e); w.close(); } catch (Throwable ig) {} } });
        t.setDaemon(true); t.start();
    }
    static void run() throws Exception {
        Class<?> ge = Class.forName("com.corrodinggames.rts.gameFramework.GameEngine");
        Object engine = ge.getMethod("getInstance").invoke(null);
        Object tm = f(engine, "tileMap");
        int w = (Integer) f(tm, "tileCountX"), h = (Integer) f(tm, "tileCountY");
        Object pe = f(engine, "pathfindingEngine");
        Object[] maps = (Object[]) f(pe, "v");
        PrintWriter log = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-costall.txt"), true);
        log.println("map=" + w + "x" + h + " count=" + (maps == null ? 0 : maps.length));
        if (maps == null) { log.close(); return; }
        for (Object cm : maps) {
            if (cm == null) { continue; }
            String type = String.valueOf(f(cm, "a"));
            byte[] d = (byte[]) f(cm, "d");
            byte[] e = (byte[]) f(cm, "e");
            byte[] fl = (byte[]) f(cm, "f");
            int blocked = 0;
            PrintWriter out = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-cost-" + type + ".csv"), true);
            out.println(w + "," + h);
            for (int y = 0; y < h; y++) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    int v;
                    if (i >= d.length) { v = -2; }
                    else if (d[i] == -1 || (e != null && i < e.length && e[i] == -1) || (fl != null && i < fl.length && fl[i] == -1)) { v = -2; }
                    else { v = (d[i] & 0xff) + (e != null && i < e.length ? (e[i] & 0xff) : 0) + (fl != null && i < fl.length ? (fl[i] & 0xff) * 10 : 0); }
                    if (v == -2) { blocked++; }
                    if (x > 0) { sb.append(','); }
                    sb.append(v);
                }
                out.println(sb);
            }
            out.close();
            log.println(type + " blocked=" + blocked + "/" + (w * h));
        }
        log.close();
    }
}
