
import java.lang.instrument.Instrumentation;
import java.io.*;

/** RWX：在平台层注入一次点击（x,y 为游戏窗口内坐标），点完自动截一张图。 */
public class ClickAgent {
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> {
            try {
                PrintWriter w = new PrintWriter(new FileWriter("logs/rwx-click.txt", true));
                String[] p = args == null ? new String[0] : args.trim().split(",");
                float x = p.length > 0 ? Float.parseFloat(p[0].trim()) : 875f;
                float y = p.length > 1 ? Float.parseFloat(p[1].trim()) : 491f;
                int hold = p.length > 2 ? Integer.parseInt(p[2].trim()) : 120;
                int waits = 0;
                while (waits < 120 && !io.github.rwx.slick.RwxInputBridge.INSTANCE.isReady()) { Thread.sleep(500); waits++; }
                io.github.rwx.slick.RwxInputBridge.INSTANCE.press(x, y);
                w.println("press " + x + "," + y + " hold=" + hold + "ms");
                Thread.sleep(hold);
                io.github.rwx.slick.RwxInputBridge.INSTANCE.release(x, y);
                w.println("release; probe=" + io.github.rwx.slick.RwxInputBridge.INSTANCE.probe());
                Thread.sleep(1500);
                io.github.rwx.slick.RwxInputBridge.INSTANCE.screenshot();
                w.println("screenshot requested");
                Thread.sleep(2000);
                w.close();
            } catch (Throwable e) {
                try { PrintWriter e2 = new PrintWriter(new FileWriter("logs/rwx-click.txt", true)); e2.println("ERR " + e); e2.close(); } catch (Throwable ig) { }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
