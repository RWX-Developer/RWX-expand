package io.github.rwx.probe;

/** 实验台用日志桩：直接打到标准输出。 */
public final class RwxPerf {
    public static void out(String s) {
        System.out.println("[log] " + s);
    }
}
