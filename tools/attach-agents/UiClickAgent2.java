
import java.lang.instrument.Instrumentation;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;

/** RWX：用 Toolkit.postEvent 走 AWT 事件队列，把点击投给 Compose 覆盖层内部组件。 */
public class UiClickAgent2 {
    static PrintWriter w;
    static void log(String s) { w.println(s); w.flush(); }
    static Component best(Container c) {
        Component fallback = null;
        for (Component k : c.getComponents()) {
            String n = k.getClass().getName();
            if (n.contains("hierarchyRoot")) { return k; }
            if (n.equals("androidx.compose.ui.awt.ComposePanel")) { fallback = k; }
            if (k instanceof Container) { Component r = best((Container) k); if (r != null && r.getClass().getName().contains("hierarchyRoot")) { return r; } }
        }
        return fallback;
    }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> {
            try {
                w = new PrintWriter(new FileWriter("logs/rwx-uiclick.txt", true));
                String[] p = args == null ? new String[0] : args.trim().split(",");
                int x = p.length > 0 ? Integer.parseInt(p[0].trim()) : 869;
                int y = p.length > 1 ? Integer.parseInt(p[1].trim()) : 473;
                Frame frame = null;
                for (Frame f : Frame.getFrames()) { if (f.isVisible() && f.getWidth() > 800) { frame = f; break; } }
                Component target = frame == null ? null : best(frame);
                log("target=" + (target == null ? "null" : target.getClass().getName()));
                if (target == null) { w.close(); return; }
                EventQueue eq = Toolkit.getDefaultToolkit().getSystemEventQueue();
                long now = System.currentTimeMillis();
                eq.postEvent(new MouseEvent(target, MouseEvent.MOUSE_ENTERED, now, 0, x, y, 0, false));
                eq.postEvent(new MouseEvent(target, MouseEvent.MOUSE_MOVED, now, 0, x, y, 0, false));
                Thread.sleep(120);
                eq.postEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), InputEvent.BUTTON1_DOWN_MASK, x, y, 1, false, MouseEvent.BUTTON1));
                Thread.sleep(120);
                eq.postEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
                eq.postEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, System.currentTimeMillis(), 0, x, y, 1, false, MouseEvent.BUTTON1));
                log("posted to " + target.getClass().getSimpleName() + " at " + x + "," + y);
                Thread.sleep(1600);
                io.github.rwx.slick.RwxInputBridge.INSTANCE.screenshot();
                Thread.sleep(2000);
                w.close();
            } catch (Throwable e) {
                try { PrintWriter e2 = new PrintWriter(new FileWriter("logs/rwx-uiclick.txt", true)); e2.println("ERR " + e); e2.close(); } catch (Throwable ig) { }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
