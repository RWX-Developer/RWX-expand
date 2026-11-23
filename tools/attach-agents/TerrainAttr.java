
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.io.*;

/** RWX：统计当前地图每一格的地形属性，确认地形类型到底有几种。 */
public class TerrainAttr {
    static Object f(Object o, String n) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) { try { Field fd = c.getDeclaredField(n); fd.setAccessible(true); return fd.get(o); } catch (NoSuchFieldException e) { c = c.getSuperclass(); } }
        throw new NoSuchFieldException(n);
    }
    static boolean b(Object o, String n) throws Exception { Object v = f(o, n); return v != null && (Boolean) v; }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> { try { run(); } catch (Throwable e) { try { PrintWriter w = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-terrain-attr.txt"), true); w.println("ERR " + e); w.close(); } catch (Throwable ig) {} } });
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
        PrintWriter out = new PrintWriter(new FileWriter("D:/Game/RWX-整合包/logs/rwx-terrain-attr.txt"), true);
        out.println("map=" + w + "x" + h + " getTileAt=" + (getTile != null));
        long none = 0, water = 0, bridge = 0, shallow = 0, lava = 0, cliff = 0, resource = 0, obj = 0, noBlock = 0;
        StringBuilder grid = new StringBuilder();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                Object tile = getTile.invoke(layer, x, y);
                char c = '.';
                if (tile == null) { none++; c = ' '; }
                else {
                    if (b(tile, "isWater")) { water++; c = '~'; }
                    if (b(tile, "isWaterBridge")) { bridge++; c = 'B'; }
                    if (b(tile, "isShallowWater")) { shallow++; c = 's'; }
                    if (b(tile, "isLava")) { lava++; c = 'L'; }
                    if (b(tile, "isCliff")) { cliff++; c = '#'; }
                    if (b(tile, "isResourcePool")) { resource++; if (c == '.') c = 'R'; }
                    if (b(tile, "hasLargeObject")) { obj++; if (c == '.') c = 'O'; }
                    Object lvl = f(tile, "movementBlockLevel");
                    if (lvl != null && ((Number) lvl).intValue() > 0) { noBlock++; }
                }
                grid.append(c);
            }
            grid.append('\n');
        }
        out.println("tiles=" + ((long) w * h) + " noTile=" + none + " water=" + water + " waterBridge=" + bridge
            + " shallow=" + shallow + " lava=" + lava + " cliff=" + cliff + " resourcePool=" + resource + " largeObject=" + obj
            + " movementBlocked=" + noBlock);
        out.println("图例: . 平地  ~ 水  B 水桥  s 浅水  L 熔岩  # 悬崖  R 资源池  O 大物件  空 无地块");
        out.print(grid.toString());
        out.close();
    }
}
