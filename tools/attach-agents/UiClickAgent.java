
import java.lang.instrument.Instrumentation;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.swing.*;

/** RWX：把鼠标事件直接投递给 Compose 覆盖层（菜单 UI 走这条路，不经过 Slick 的指针状态）。 */
public class UiClickAgent {
    static PrintWriter w;
    static void log(String s) { w.println(s); w.flush(); }
    static Component findCompose(Container c) {
        for (Component k : c.getComponents()) {
            if (k.getClass().getName().equals("androidx.compose.ui.awt.ComposePanel")) { return k; }
            if (k instanceof Container) { Component r = findCompose((Container) k); if (r != null) { return r; } }
        }
        return null;
    }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> {
            try {
                w = new PrintWriter(new FileWriter("logs/rwx-uiclick.txt", true));
                String[] p = args == null ? new String[0] : args.trim().split(",");
                int x = p.length > 0 ? Integer.parseInt(p[0].trim()) : 875;
                int y = p.length > 1 ? Integer.parseInt(p[1].trim()) : 491;
                Frame frame = null;
                for (Frame f : Frame.getFrames()) { if (f.isVisible() && f.getWidth() > 800) { frame = f; break; } }
                if (frame == null) { log("no frame"); w.close(); return; }
                Component compose = findCompose(frame);
                log("compose=" + (compose == null ? "null" : compose.getClass().getName()));
                if (compose == null) { w.close(); return; }
                final Component target = compose;
                SwingUtilities.invokeAndWait(() -> {
                    long now = System.currentTimeMillis();
                    target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_MOVED, now, 0, x, y, 0, false, MouseEvent.NOBUTTON));
                    target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_PRESSED, now, 0, x, y, 1, false, MouseEvent.BUTTON1));
                    target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_RELEASED, now + 90, 0, x, y, 1, false, MouseEvent.BUTTON1));
                    target.dispatchEvent(new MouseEvent(target, MouseEvent.MOUSE_CLICKED, now + 90, 0, x, y, 1, false, MouseEvent.BUTTON1));
                });
                log("dispatched LMB at " + x + "," + y);
                Thread.sleep(1500);
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
