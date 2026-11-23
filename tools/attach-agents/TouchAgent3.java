
import java.lang.instrument.Instrumentation;
import java.io.*;
import com.corrodinggames.rts.gameFramework.GameEngine;

/** RWX：用游戏自己新增的 rwxInjectPointerPress/rwxInjectPointerRelease 注入一次点击。 */
public class TouchAgent3 {
    static PrintWriter w;
    static void log(String s) { w.println(s); w.flush(); }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> {
            try {
                w = new PrintWriter(new FileWriter("logs/rwx-touch.txt", true));
                String[] parts = args == null ? new String[0] : args.trim().split(",");
                float px = parts.length > 0 ? Float.parseFloat(parts[0].trim()) : 900f;
                float py = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 500f;
                GameEngine ge = GameEngine.getInstance();
                log("available=" + (ge != null));
                // 等视图就绪（菜单加载期间 activeGameView 还是 null）
                int waits = 0;
                while (!ge.rwxInjectPointerPress(px, py) && waits < 120) { Thread.sleep(500); waits++; }
                log("waited " + (waits * 500) + "ms for view");
                log("isTouchDown before=" + ge.isTouchDown());
                boolean ok = true;
                log("press ok=" + ok + " at " + px + "," + py);
                Thread.sleep(300);
                log("during isTouchDown=" + ge.isTouchDown() + " pointers=" + ge.getTouchPointerCount()
                    + " touch=" + ge.getTouchX() + "," + ge.getTouchY());
                Thread.sleep(300);
                ge.rwxInjectPointerRelease();
                Thread.sleep(300);
                log("after release isTouchDown=" + ge.isTouchDown() + " pointers=" + ge.getTouchPointerCount());
                w.close();
            } catch (Throwable e) {
                try { PrintWriter e2 = new PrintWriter(new FileWriter("logs/rwx-touch.txt", true)); e2.println("ERR " + e); for (StackTraceElement el : e.getStackTrace()) { e2.println("  " + el); } e2.close(); } catch (Throwable ig) { }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
