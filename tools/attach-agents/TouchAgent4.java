
import java.lang.instrument.Instrumentation;
import java.io.*;
import java.awt.*;
import com.corrodinggames.rts.gameFramework.GameEngine;

/** RWX：先确认游戏主循环是否在跑（currentTick 是否推进），再注入点击。 */
public class TouchAgent4 {
    static PrintWriter w;
    static void log(String s) { w.println(s); w.flush(); }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> {
            try {
                w = new PrintWriter(new FileWriter("logs/rwx-touch.txt", true));
                GameEngine ge = GameEngine.getInstance();
                int waits = 0;
                while (waits < 120 && !ge.rwxInjectPointerPress(0f, 0f)) { Thread.sleep(500); waits++; }
                ge.rwxInjectPointerRelease();
                log("view ready after " + (waits * 500) + "ms");
                long t1 = ge.currentTick;
                Thread.sleep(1200);
                long t2 = ge.currentTick;
                log("tick " + t1 + " -> " + t2 + " delta=" + (t2 - t1));
                Frame f = null;
                for (Frame fr : Frame.getFrames()) { if (fr.isVisible() && fr.getWidth() > 800) { f = fr; break; } }
                if (f != null) { f.toFront(); f.requestFocus(); log("focus requested: " + f.getTitle()); Thread.sleep(800); }
                long t3 = ge.currentTick;
                Thread.sleep(1000);
                long t4 = ge.currentTick;
                log("tick after focus " + t3 + " -> " + t4 + " delta=" + (t4 - t3));
                ge.rwxInjectPointerPress(960f, 540f);
                Thread.sleep(600);
                log("during press isTouchDown=" + ge.isTouchDown() + " pointers=" + ge.getTouchPointerCount() + " touch=" + ge.getTouchX() + "," + ge.getTouchY());
                ge.rwxInjectPointerRelease();
                Thread.sleep(400);
                log("after release isTouchDown=" + ge.isTouchDown());
                w.close();
            } catch (Throwable e) {
                try { PrintWriter e2 = new PrintWriter(new FileWriter("logs/rwx-touch.txt", true)); e2.println("ERR " + e); for (StackTraceElement el : e.getStackTrace()) { e2.println("  " + el); } e2.close(); } catch (Throwable ig) { }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
