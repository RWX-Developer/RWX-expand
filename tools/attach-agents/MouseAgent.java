
import java.lang.instrument.Instrumentation;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;

/** RWX：在游戏 JVM 内用 AWT Robot 模拟鼠标点击，并前后截屏存 PNG，用来验证输入是否真的到达游戏。 */
public class MouseAgent {
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> { try { run(); } catch (Throwable e) { log("ERR " + e); } });
        t.setDaemon(true);
        t.start();
    }
    static void log(String s) {
        try { PrintWriter w = new PrintWriter(new FileWriter("logs/rwx-mouse.txt", true)); w.println(s); w.close(); } catch (Throwable t) { }
    }
    static void shot(String name) throws Exception {
        Rectangle r = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
        BufferedImage img = new Robot().createScreenCapture(r);
        ImageIO.write(img, "png", new File("logs/" + name));
        log("shot " + name + " " + img.getWidth() + "x" + img.getHeight());
    }
    static void run() throws Exception {
        Thread.sleep(800);
        Frame target = null;
        for (Frame f : Frame.getFrames()) { if (f.isVisible()) { target = f; break; } }
        if (target == null) { log("NOFRAME"); return; }
        Point loc = target.getLocationOnScreen();
        int w = target.getWidth(), h = target.getHeight();
        log("frame at " + loc.x + "," + loc.y + " size " + w + "x" + h + " title=" + target.getTitle());
        Robot r = new Robot();
        r.setAutoDelay(30);
        int cx = loc.x + w / 2, cy = loc.y + h / 2;
        log("center " + cx + "," + cy);
        // 先点一下，让窗口拿焦点
        r.mouseMove(cx, cy); r.delay(250);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK); r.delay(80); r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(900);
        shot("rwx-mouse-after1.png");
        // 再拖框选（从左上拖到右下）
        int x1 = loc.x + 200, y1 = loc.y + 200, x2 = loc.x + w - 400, y2 = loc.y + h - 300;
        r.mouseMove(x1, y1); r.delay(150);
        r.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        for (int i = 1; i <= 12; i++) { r.mouseMove(x1 + (x2 - x1) * i / 12, y1 + (y2 - y1) * i / 12); r.delay(40); }
        r.delay(150);
        r.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        r.delay(700);
        shot("rwx-mouse-after2.png");
        // 右键点一下下达移动
        int mx = loc.x + w / 2 + 260, my = loc.y + h / 2 + 120;
        r.mouseMove(mx, my); r.delay(200);
        r.mousePress(InputEvent.BUTTON3_DOWN_MASK); r.delay(80); r.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
        r.delay(900);
        shot("rwx-mouse-after3.png");
        log("done");
    }
}
