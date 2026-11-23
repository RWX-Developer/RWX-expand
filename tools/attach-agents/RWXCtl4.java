
import java.lang.instrument.Instrumentation;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import javax.swing.*;

/**
 * RWX 常驻控制代理：轮询 logs/rwx-ctl.txt，执行点击/截图/拖拽。
 * 命令（一行一条）：
 *   click X Y          Compose 菜单点击（AWT 事件队列）
 *   wclick X Y [ms]    世界点击（Slick 指针注入，按下 ms 毫秒）
 *   drag X1 Y1 X2 Y2   世界拖拽（框选）
 *   shot               游戏内截图（等价 F12）
 */
public class RWXCtl4 {
    static PrintWriter out;
    static long offset = 0;
    static void log(String s) { out.println(s); out.flush(); }
    static Component find(Container c, String want) {
        for (Component k : c.getComponents()) {
            if (k.getClass().getName().contains(want)) { return k; }
            if (k instanceof Container) { Component r = find((Container) k, want); if (r != null) { return r; } }
        }
        return null;
    }
    static Component uiTarget() {
        for (Frame f : Frame.getFrames()) {
            if (!f.isVisible() || f.getWidth() < 800) { continue; }
            Component t = find(f, "hierarchyRoot");
            if (t != null) { return t; }
            t = find(f, "ComposePanel");
            if (t != null) { return t; }
        }
        return null;
    }
    static void post(Component t, int id, int mask, int x, int y, int clicks, int button) {
        EventQueue q = Toolkit.getDefaultToolkit().getSystemEventQueue();
        q.postEvent(new MouseEvent(t, id, System.currentTimeMillis(), mask, x, y, clicks, false, button));
    }
    static void click(int x, int y) throws Exception {
        Component t = uiTarget();
        if (t == null) { log("click " + x + "," + y + " FAILED no ui target"); return; }
        post(t, MouseEvent.MOUSE_ENTERED, 0, x, y, 0, MouseEvent.NOBUTTON);
        post(t, MouseEvent.MOUSE_MOVED, 0, x, y, 0, MouseEvent.NOBUTTON);
        Thread.sleep(100);
        post(t, MouseEvent.MOUSE_PRESSED, InputEvent.BUTTON1_DOWN_MASK, x, y, 1, MouseEvent.BUTTON1);
        Thread.sleep(110);
        post(t, MouseEvent.MOUSE_RELEASED, 0, x, y, 1, MouseEvent.BUTTON1);
        post(t, MouseEvent.MOUSE_CLICKED, 0, x, y, 1, MouseEvent.BUTTON1);
        log("click " + x + "," + y + " -> " + t.getClass().getSimpleName());
    }
    static void worldClick(int x, int y, int hold) throws Exception {
        io.github.rwx.slick.RwxInputBridge.INSTANCE.press(x, y);
        Thread.sleep(hold);
        io.github.rwx.slick.RwxInputBridge.INSTANCE.release(x, y);
        log("wclick " + x + "," + y + " hold=" + hold + " probe=" + io.github.rwx.slick.RwxInputBridge.INSTANCE.probe());
    }
    static void worldDrag(int x1, int y1, int x2, int y2) throws Exception {
        io.github.rwx.slick.RwxInputBridge.INSTANCE.press(x1, y1);
        Thread.sleep(120);
        int steps = 10;
        for (int i = 1; i <= steps; i++) {
            float x = x1 + (x2 - x1) * i / (float) steps;
            float y = y1 + (y2 - y1) * i / (float) steps;
            io.github.rwx.slick.RwxInputBridge.INSTANCE.press(x, y);
            Thread.sleep(40);
        }
        Thread.sleep(80);
        io.github.rwx.slick.RwxInputBridge.INSTANCE.release(x2, y2);
        log("drag " + x1 + "," + y1 + " -> " + x2 + "," + y2);
    }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> {
            try {
                out = new PrintWriter(new FileWriter("logs/rwx-ctl4-out.txt", true));
                int waits = 0;
                while (waits < 200 && !io.github.rwx.slick.RwxInputBridge.INSTANCE.isReady()) { Thread.sleep(500); waits++; }
                log("ctl ready after " + (waits * 500) + "ms");
                Path cmd = Paths.get("logs/rwx-ctl4.txt");
                if (Files.exists(cmd)) { offset = Files.size(cmd); }
                while (true) {
                    Thread.sleep(200);
                    if (!Files.exists(cmd)) { continue; }
                    long size = Files.size(cmd);
                    if (size <= offset) { if (size < offset) { offset = 0; } continue; }
                    try (RandomAccessFile raf = new RandomAccessFile(cmd.toFile(), "r")) {
                        raf.seek(offset);
                        String line;
                        while ((line = raf.readLine()) != null) {
                            line = new String(line.getBytes("ISO-8859-1"), "UTF-8").trim();
                            if (line.isEmpty()) { continue; }
                            try { exec(line); } catch (Throwable e) { log("ERR on '" + line + "': " + e); }
                        }
                        offset = raf.getFilePointer();
                    }
                }
            } catch (Throwable e) { }
        });
        t.setDaemon(true);
        t.start();
    }
    static void exec(String line) throws Exception {
        String[] p = line.split("\\s+");
        switch (p[0]) {
            case "click": click(Integer.parseInt(p[1]), Integer.parseInt(p[2])); break;
            case "wclick": worldClick(Integer.parseInt(p[1]), Integer.parseInt(p[2]), p.length > 3 ? Integer.parseInt(p[3]) : 120); break;
            case "drag": worldDrag(Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]), Integer.parseInt(p[4])); break;
            case "start": {
                io.github.rwx.ui.CoreUiEventQueue.requestBattleRoomStartGame();
                log("requestBattleRoomStartGame sent");
                break;
            }
            case "tick": {
                com.corrodinggames.rts.gameFramework.GameEngine ge = com.corrodinggames.rts.gameFramework.GameEngine.getInstance();
                log("tick=" + ge.currentTick + " isStopped=" + ge.isStopped);
                break;
            }
            case "dump": {
                for (Frame fr : Frame.getFrames()) {
                    if (!fr.isVisible() || fr.getWidth() < 800) { continue; }
                    Container cp = (fr instanceof javax.swing.RootPaneContainer) ? ((javax.swing.RootPaneContainer) fr).getContentPane() : fr;
                    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(Math.max(1, cp.getWidth()), Math.max(1, cp.getHeight()), java.awt.image.BufferedImage.TYPE_INT_RGB);
                    Graphics2D g = img.createGraphics();
                    try { cp.printAll(g); } catch (Throwable e) { log("dump paint ERR " + e); }
                    g.dispose();
                    try (java.io.FileOutputStream fo = new java.io.FileOutputStream("logs/rwx-dump.png")) {
                        javax.imageio.ImageIO.write(img, "png", fo);
                    }
                    log("dump wrote " + img.getWidth() + "x" + img.getHeight() + " from " + cp.getClass().getName());
                    break;
                }
                break;
            }
            case "shot":
                boolean ok = io.github.rwx.slick.RwxInputBridge.INSTANCE.screenshot();
                log("shot requested=" + ok);
                break;
            default: log("unknown: " + line);
        }
    }
}
