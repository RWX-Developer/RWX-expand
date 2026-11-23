
import java.lang.instrument.Instrumentation;
import java.lang.reflect.*;
import java.awt.*;
import java.io.*;

/** RWX：绕开非公开类的方法反射——沿字段往下找 MultiTouchPointerState，再注入一次点击。 */
public class TouchAgent2 {
    static PrintWriter logw;
    static void log(String s) { logw.println(s); logw.flush(); }
    static Object fld(Object o, String n) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) { try { Field f = c.getDeclaredField(n); f.setAccessible(true); return f.get(o); } catch (NoSuchFieldException e) { c = c.getSuperclass(); } }
        return null;
    }
    static void put(Object o, String n, Object v) throws Exception {
        Class<?> c = o.getClass();
        while (c != null) { try { Field f = c.getDeclaredField(n); f.setAccessible(true); f.set(o, v); return; } catch (NoSuchFieldException e) { c = c.getSuperclass(); } }
        throw new NoSuchFieldException(n);
    }
    static Object deepFind(Object o, String typeName, int depth) {
        if (o == null || depth <= 0) { return null; }
        Class<?> c = o.getClass();
        if (c.getName().equals(typeName)) { return o; }
        Class<?> k = c;
        while (k != null) {
            for (Field f : k.getDeclaredFields()) {
                if (f.getType().isPrimitive()) { continue; }
                try {
                    f.setAccessible(true);
                    Object v = f.get(o);
                    if (v == null) { continue; }
                    if (v.getClass().getName().equals(typeName)) { return v; }
                    if (depth > 1 && !v.getClass().getName().startsWith("java.")) {
                        Object r = deepFind(v, typeName, depth - 1);
                        if (r != null) { return r; }
                    }
                } catch (Throwable t) { }
            }
            k = k.getSuperclass();
        }
        return null;
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
        Object view = fld(engine, "activeGameView");
        log("view=" + (view == null ? "null" : view.getClass().getName()));
        Object st = deepFind(engine, "io.github.rwx.input.MultiTouchPointerState", 3);
        log("pointerState=" + (st == null ? "NOT FOUND" : st.getClass().getName()));
        if (st == null) { logw.close(); return; }
        float[] xs = (float[]) fld(st, "x");
        float[] ys = (float[]) fld(st, "y");
        log("hasArrays=" + (xs != null && ys != null) + " len=" + (xs == null ? -1 : xs.length));
        boolean before = (Boolean) ge.getMethod("isTouchDown").invoke(engine);
        log("isTouchDown before=" + before);
        xs[0] = 900f; ys[0] = 500f;
        put(st, "isDown", Boolean.TRUE);
        put(st, "numPointers", 1);
        Thread.sleep(250);
        boolean during = (Boolean) ge.getMethod("isTouchDown").invoke(engine);
        int cnt = (Integer) ge.getMethod("getTouchPointerCount").invoke(engine);
        float tx = (Float) ge.getMethod("getTouchX").invoke(engine);
        float ty = (Float) ge.getMethod("getTouchY").invoke(engine);
        log("during: isTouchDown=" + during + " pointers=" + cnt + " touch=" + tx + "," + ty);
        put(st, "isDown", Boolean.FALSE);
        put(st, "numPointers", 0);
        Thread.sleep(250);
        boolean after = (Boolean) ge.getMethod("isTouchDown").invoke(engine);
        log("after release isTouchDown=" + after);
        logw.close();
    }
}
