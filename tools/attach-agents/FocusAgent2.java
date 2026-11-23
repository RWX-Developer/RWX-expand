
import java.lang.instrument.Instrumentation;
import java.awt.*;
import java.io.*;
import com.corrodinggames.rts.gameFramework.GameEngine;

/** RWX：把游戏窗口强制提到最前（含 always-on-top），解除渲染/逻辑冻结。 */
public class FocusAgent2 {
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> {
            try {
                PrintWriter w = new PrintWriter(new FileWriter("logs/rwx-focus.txt", true));
                Frame f = null;
                for (Frame fr : Frame.getFrames()) { if (fr.isVisible() && fr.getWidth() > 800) { f = fr; break; } }
                if (f == null) { w.println("no frame"); w.close(); return; }
                f.setAlwaysOnTop(true);
                f.toFront();
                f.requestFocus();
                try { f.setState(Frame.NORMAL); } catch (Throwable t2) { }
                w.println("fronted; tick1=" + GameEngine.getInstance().currentTick);
                Thread.sleep(2000);
                w.println("tick2=" + GameEngine.getInstance().currentTick);
                f.setAlwaysOnTop(false);
                w.close();
            } catch (Throwable e) { try { PrintWriter e2 = new PrintWriter(new FileWriter("logs/rwx-focus.txt", true)); e2.println("ERR " + e); e2.close(); } catch (Throwable ig) { } }
        });
        t.setDaemon(true);
        t.start();
    }
}
