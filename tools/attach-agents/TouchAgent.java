
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.awt.*;
import java.io.*;

/** RWX：不经过操作系统，直接在 JVM 里改 MultiTouchPointerState，模拟一次点击。 */
public class TouchAgent {
    static PrintWriter logw;
    static void log(String s) { logw.println(s); logw.flush(); }
    static Object f(Object o, String n) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) { try { Field fd = c.getDeclaredField(n); fd.setAccessible(true); return fd.get(o); } catch (NoSuchFieldException e) { c = c.getSuperclass(); } }
        throw new NoSuchFieldException(n);
    }
    static void set(Object o, String n, Object v) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) { try { Field fd = c.getDeclaredField(n); fd.setAccessible(true); fd.set(o, v); return; } catch (NoSuchFieldException e) { c = c.getSuperclass(); } }
        throw new NoSuchFieldException(n);
    }
    public static void agentmain(String args, Instrumentation inst) {
        Thread t = new Thread(() -> { try { run(args); } catch (Throwable e) { try { PrintWriter w = new PrintWriter(new FileWriter("logs/rwx-touch.txt", true)); w.println("ERR " + e); for (StackTraceElement el : e.getStackTrace()) { w.println("  " + el); } w.close(); } catch (Throwable ig) {} } });
        t.setDaemon(true);
        t.start();
    }
    static void run(String args) throws Exception {
        logw = new PrintWriter(new FileWriter("logs/rwx-touch.txt", true));
        Class<?> ge = Class.forName("com.corrodinggames.rts.gameFramework.GameEngine");
        Object engine = ge.getMethod("getInstance").invoke(null);
        Object view = f(engine, "activeGameView");
        log("view=" + (view == null ? "null" : view.getClass().getName()));
        if (view == null) { logw.close(); return; }
        Object settings = call(view, "getSettings");
        log("settings=" + settings.getClass().getName());
        Class<?> gs = ge.getMethod("isTouchDown").getReturnType();
        // 窗口信息
        Frame frame = null;
        for (Frame fr : Frame.getFrames()) { if (fr.isVisible()) { frame = fr; break; } }
        if (frame == null) { log("NOFRAME"); logw.close(); return; }
        frame.toFront();
        Point loc = frame.getLocationOnScreen();
        int w = frame.getWidth(), h = frame.getHeight();
        float px = w / 2f, py = h / 2f;   // 引擎读的是窗口内坐标
        log("frame " + loc.x + "," + loc.y + " " + w + "x" + h + " click=" + px + "," + py);

        boolean before = (Boolean) call(engine, "isTouchDown");
        log("isTouchDown before=" + before);
        log("settings has x/y/isDown/numPointers: " + has(settings, "x") + "/" + has(settings, "y") + "/" + has(settings, "isDown") + "/" + has(settings, "numPointers"));
        logw.close();
    }
    static Object call(Object o, String name) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) { try { Method m = c.getDeclaredMethod(name); m.setAccessible(true); return m.invoke(o); } catch (NoSuchMethodException e) { c = c.getSuperclass(); } }
        throw new NoSuchMethodException(name);
    }
    static boolean has(Object o, String n) {
        Class<?> c = o.getClass();
        while (c != null) { try { c.getDeclaredField(n); return true; } catch (NoSuchFieldException e) { c = c.getSuperclass(); } }
        return false;
    }
}
