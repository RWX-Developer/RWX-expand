
import java.lang.instrument.Instrumentation;
import java.io.*;

/** RWX：调游戏自己的截图接口（等价 F12），并把截图目录写出来。 */
public class ShotAgent {
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> {
            try {
                PrintWriter w = new PrintWriter(new FileWriter("logs/rwx-shot.txt", true));
                int waits = 0;
                while (waits < 120 && !io.github.rwx.slick.RwxInputBridge.INSTANCE.isReady()) { Thread.sleep(500); waits++; }
                w.println("dir=" + io.github.rwx.slick.RwxInputBridge.INSTANCE.screenshotDir());
                boolean ok = io.github.rwx.slick.RwxInputBridge.INSTANCE.screenshot();
                w.println("requested=" + ok);
                Thread.sleep(2500);
                w.println("done");
                w.close();
            } catch (Throwable e) {
                try { PrintWriter e2 = new PrintWriter(new FileWriter("logs/rwx-shot.txt", true)); e2.println("ERR " + e); e2.close(); } catch (Throwable ig) { }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
