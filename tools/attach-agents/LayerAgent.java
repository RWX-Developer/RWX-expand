
import java.lang.instrument.Instrumentation;
import java.io.*;
import com.corrodinggames.rts.gameFramework.GameEngine;
import io.github.rwx.slick.RwxInputBridge;

/** RWX：走平台层的 RwxInputBridge 注入一次点击，并回读引擎的触摸状态。 */
public class LayerAgent {
    static PrintWriter w;
    static void log(String s) { w.println(s); w.flush(); }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> {
            try {
                w = new PrintWriter(new FileWriter("logs/rwx-layer.txt", true));
                String[] p = args == null ? new String[0] : args.trim().split(",");
                float px = p.length > 0 ? Float.parseFloat(p[0].trim()) : 960f;
                float py = p.length > 1 ? Float.parseFloat(p[1].trim()) : 540f;
                int waits = 0;
                while (!io.github.rwx.slick.RwxInputBridge.INSTANCE.isReady() && waits < 120) { Thread.sleep(500); waits++; }
                log("bridge ready=" + io.github.rwx.slick.RwxInputBridge.INSTANCE.isReady() + " after " + (waits * 500) + "ms");
                GameEngine ge = GameEngine.getInstance();
                log("before press isTouchDown=" + ge.isTouchDown() + " pointers=" + ge.getTouchPointerCount());
                boolean ok = io.github.rwx.slick.RwxInputBridge.INSTANCE.press(px, py);
                log("press=" + ok + " at " + px + "," + py);
                log("probe right after press: " + io.github.rwx.slick.RwxInputBridge.INSTANCE.probe());
                Thread.sleep(500);
                log("during press isTouchDown=" + ge.isTouchDown() + " pointers=" + ge.getTouchPointerCount()
                    + " touch=" + ge.getTouchX() + "," + ge.getTouchY()
                    + " probe: " + io.github.rwx.slick.RwxInputBridge.INSTANCE.probe());
                Thread.sleep(300);
                io.github.rwx.slick.RwxInputBridge.INSTANCE.release(px, py);
                Thread.sleep(400);
                log("after release isTouchDown=" + ge.isTouchDown() + " pointers=" + ge.getTouchPointerCount());
                w.close();
            } catch (Throwable e) {
                try { PrintWriter e2 = new PrintWriter(new FileWriter("logs/rwx-layer.txt", true)); e2.println("ERR " + e); for (StackTraceElement el : e.getStackTrace()) { e2.println("  " + el); } e2.close(); } catch (Throwable ig) { }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
