
import java.lang.instrument.Instrumentation;
import java.awt.*;
import java.io.*;

/** RWX：打印游戏窗口的组件树，用来找 Compose 覆盖层 / Slick 画布。 */
public class TreeAgent {
    static void walk(Component c, int depth, PrintWriter w) {
        w.println("  ".repeat(depth) + c.getClass().getName() + " " + c.getBounds().x + "," + c.getBounds().y
            + " " + c.getWidth() + "x" + c.getHeight() + (c.isFocusable() ? " focusable" : ""));
        if (c instanceof Container) { for (Component k : ((Container) c).getComponents()) { walk(k, depth + 1, w); } }
    }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> {
            try {
                PrintWriter w = new PrintWriter(new FileWriter("logs/rwx-tree.txt", true));
                for (Frame f : Frame.getFrames()) { if (f.isVisible()) { walk(f, 0, w); } }
                w.close();
            } catch (Throwable e) { }
        });
        t.setDaemon(true);
        t.start();
    }
}
