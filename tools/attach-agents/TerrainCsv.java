
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.io.*;

/** RWX：导出地形属性网格 + 寻路代价网格（CSV），供画热力图。 */
public class TerrainCsv {
    static Object f(Object o, String n) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) { try { Field fd = c.getDeclaredField(n); fd.setAccessible(true); return fd.get(o); } catch (NoSuchFieldException e) { c = c.getSuperclass(); } }
        throw new NoSuchFieldException(n);
    }
    static boolean b(Object o, String n) throws Exception { Object v = f(o, n); return v != null && (Boolean) v; }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> { try { run(); } catch (Throwable e) { try { PrintWriter w = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-grid-err.txt"), true); w.println("ERR " + e); for (StackTraceElement el : e.getStackTrace()) w.println("  " + el); w.close(); } catch (Throwable ig) {} } });
        t.setDaemon(true); t.start();
    }
    static void run() throws Exception {
        Class<?> ge = Class.forName("com.corrodinggames.rts.gameFramework.GameEngine");
        Object engine = ge.getMethod("getInstance").invoke(null);
        Object tm = f(engine, "tileMap");
        int w = (Integer) f(tm, "tileCountX"), h = (Integer) f(tm, "tileCountY");
        Object layer = f(tm, "groundLayer");
        Method getTile = null;
        for (Method m : layer.getClass().getMethods()) {
            if (m.getName().equals("getTileAt") && m.getParameterCount() == 2) { getTile = m; break; }
        }
        Object pe = f(engine, "pathfindingEngine");
        PrintWriter terr = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-grid-terrain.csv"), true);
        terr.println(w + "," + h);
        for (int y = 0; y < h; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < w; x++) {
                Object tile = getTile.invoke(layer, x, y);
                int code = 0;
                if (tile == null) { code = 6; }
                else if (b(tile, "isLava")) { code = 4; }
                else if (b(tile, "isCliff")) { code = 5; }
                else if (b(tile, "isWaterBridge")) { code = 2; }
                else if (b(tile, "isShallowWater")) { code = 3; }
                else if (b(tile, "isWater")) { code = 1; }
                if (x > 0) { sb.append(','); }
                sb.append(code);
            }
            terr.println(sb);
        }
        terr.close();

        // 代价网格：按陆军那张图（b=地形, c=建筑, d=物件*10）
        Object cm = f(pe, "landCostMap");
        byte[] cb = (byte[]) f(cm, "b");
        byte[] cc = (byte[]) f(cm, "c");
        byte[] cd = (byte[]) f(cm, "d");
        PrintWriter cost = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-grid-cost.csv"), true);
        cost.println(w + "," + h);
        for (int y = 0; y < h; y++) {
            StringBuilder sb = new StringBuilder();
            for (int x = 0; x < w; x++) {
                int i = y * w + x;
                int v = -1;
                if (cb != null && cc != null && cd != null && i < cb.length) {
                    v = (cb[i] == -1 || cc[i] == -1 || cd[i] == -1) ? -1 : (cb[i] & 0xff) + (cc[i] & 0xff) + (cd[i] & 0xff) * 10;
                }
                if (x > 0) { sb.append(','); }
                sb.append(v);
            }
            cost.println(sb);
        }
        cost.close();
    }
}
