
import java.lang.instrument.Instrumentation;
import java.io.*;
import com.corrodinggames.rts.gameFramework.GameEngine;

public class TickAgent2 {
    public static void main2() { }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> {
            try {
                PrintWriter w = new PrintWriter(new FileWriter("logs/rwx-tick.txt", true));
                GameEngine ge = GameEngine.getInstance();
                w.println("tick1=" + ge.currentTick + " isStopped=" + ge.isStopped);
                Thread.sleep(1500);
                w.println("tick2=" + ge.currentTick);
                w.println("bridgeReady=" + io.github.rwx.slick.RwxInputBridge.INSTANCE.isReady());
                Thread.sleep(1500);
                w.println("tick3=" + ge.currentTick);
                w.println("shotReq=" + io.github.rwx.slick.RwxInputBridge.INSTANCE.screenshot());
                Thread.sleep(3000);
                w.println("tick4=" + ge.currentTick);
                w.close();
            } catch (Throwable e) {
                try { PrintWriter e2 = new PrintWriter(new FileWriter("logs/rwx-tick.txt", true)); e2.println("ERR " + e); e2.close(); } catch (Throwable ig) { }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
