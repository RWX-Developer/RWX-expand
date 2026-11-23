
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.io.*;

/** RWX：枚举所有 PathCostMap，按 UnitMovementType 找 LAND / WATER 两张，导出代价网格。 */
public class CostMapDump {
    static Object f(Object o, String n) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) { try { Field fd = c.getDeclaredField(n); fd.setAccessible(true); return fd.get(o); } catch (NoSuchFieldException e) { c = c.getSuperclass(); } }
        throw new NoSuchFieldException(n);
    }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> { try { run(); } catch (Throwable e) { try { PrintWriter w = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-cost-err.txt"), true); w.println("ERR " + e); for (StackTraceElement el : e.getStackTrace()) w.println("  " + el); w.close(); } catch (Throwable ig) {} } });
        t.setDaemon(true); t.start();
    }
    static void run() throws Exception {
        Class<?> ge = Class.forName("com.corrodinggames.rts.gameFramework.GameEngine");
        Object engine = ge.getMethod("getInstance").invoke(null);
        Object tm = f(engine, "tileMap");
        int w = (Integer) f(tm, "tileCountX"), h = (Integer) f(tm, "tileCountY");
        Object pe = f(engine, "pathfindingEngine");
        Object[] maps = (Object[]) f(pe, "v");
        PrintWriter log = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-cost-maps.txt"), true);
        log.println("map=" + w + "x" + h + " costMaps=" + (maps == null ? "null" : maps.length));
        if (maps == null) { log.close(); return; }
        for (Object cm : maps) {
            if (cm == null) { continue; }
            Object type = f(cm, "a");
            byte[] d = (byte[]) f(cm, "d");
            byte[] e = (byte[]) f(cm, "e");
            byte[] fl = (byte[]) f(cm, "f");
            int blockedD = 0, zeroD = 0, otherD = 0;
            for (byte b : d) { if (b == -1) { blockedD++; } else if (b == 0) { zeroD++; } else { otherD++; } }
            log.println("type=" + type + " len=" + d.length + " d:-1=" + blockedD + ",0=" + zeroD + ",>0=" + otherD);
        }
        log.close();

        // 导出 LAND 与 WATER 两张
        for (String want : new String[]{"LAND", "WATER"}) {
            Object target = null;
            for (Object cm : maps) {
                if (cm == null) { continue; }
                if (String.valueOf(f(cm, "a")).equals(want)) { target = cm; break; }
            }
            if (target == null) { continue; }
            byte[] d = (byte[]) f(target, "d");
            byte[] e = (byte[]) f(target, "e");
            byte[] fl = (byte[]) f(target, "f");
            PrintWriter out = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-grid-cost-" + want + ".csv"), true);
            out.println(w + "," + h);
            for (int y = 0; y < h; y++) {
                StringBuilder sb = new StringBuilder();
                for (int x = 0; x < w; x++) {
                    int i = y * w + x;
                    int v = -1;
                    if (i < d.length) {
                        if (d[i] == -1 || (e != null && i < e.length && e[i] == -1) || (fl != null && i < fl.length && fl[i] == -1)) { v = -2; }
                        else { v = (d[i] & 0xff) + (e != null && i < e.length ? (e[i] & 0xff) : 0) + (fl != null && i < fl.length ? (fl[i] & 0xff) * 10 : 0); }
                    }
                    if (x > 0) { sb.append(','); }
                    sb.append(v);
                }
                out.println(sb);
            }
            out.close();
        }
    }
}
