
import java.lang.instrument.Instrumentation;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** RWX：先把游戏窗口提到最前并拿焦点，再截图确认，最后点一下游戏画面中央。 */
public class FrontAgent {
    static PrintWriter w;
    static void log(String s) { w.println(s); w.flush(); }
    static void shot(String n) throws Exception {
        Rectangle r = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        ImageIO.write(new Robot().createScreenCapture(r), "png", new File("logs/" + n));
        log("shot " + n);
    }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> {
            try {
                w = new PrintWriter(new FileWriter("logs/rwx-front.txt", true));
                Frame f = null;
                for (Frame fr : Frame.getFrames()) { if (fr.isVisible() && fr.getWidth() > 800) { f = fr; break; } }
                if (f == null) { log("NOFRAME"); w.close(); return; }
                log("frame " + f.getTitle() + " " + f.getLocationOnScreen().x + "," + f.getLocationOnScreen().y + " " + f.getWidth() + "x" + f.getHeight());
                f.setExtendedState(Frame.MAXIMIZED_BOTH);
                f.toFront();
                f.requestFocus();
                Thread.sleep(1500);
                shot("rwx-front-before.png");
                Robot r = new Robot();
                Point loc = f.getLocationOnScreen();
                int cx = loc.x + f.getWidth() / 2, cy = loc.y + f.getHeight() / 2;
                r.mouseMove(cx, cy); r.delay(300);
                r.mousePress(InputEvent.BUTTON1_DOWN_MASK); r.delay(90); r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
                Thread.sleep(1200);
                shot("rwx-front-after.png");
                log("clicked center " + cx + "," + cy);
                w.close();
            } catch (Throwable e) {
                try { PrintWriter e2 = new PrintWriter(new FileWriter("logs/rwx-front.txt", true)); e2.println("ERR " + e); e2.close(); } catch (Throwable ig) { }
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
