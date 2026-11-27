package io.github.rwx.probe;

/**
 * RWX：手机上的「运行期开关入口」。
 *
 * <p>安卓端设不了环境变量，之前所有 RWX_* 开关（剖析、寻路巡检、批处理参数）在真机上等于摆设。
 * 这里补一个文件入口：游戏数据根目录下放一个 {@code rwx-flags.txt}，每行 {@code KEY=VALUE}，
 * {@code #} 开头为注释。读取一次后缓存，改完重启游戏生效。
 *
 * <p>典型内容：
 * <pre>
 * RWX_PROF=1
 * RWX_ROUTE_CHECK=2
 * RWX_BATCH_QUADS=512
 * RWX_MAP_CELL_PX=768
 * </pre>
 */
public final class RwxFlags {

    private static boolean loaded;
    private static final java.util.HashMap<String, String> VALUES = new java.util.HashMap<String, String>();
    private static int failures;
    private static String sourcePath;

    private RwxFlags() {
    }

    private static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        try {
            java.io.File root = io.github.rwx.LegacyAssetBridge.storageRootDir();
            if (root == null) {
                failures++;
                loaded = failures > 5;
                return;
            }
            java.io.File file = new java.io.File(root, "rwx-flags.txt");
            if (!file.isFile()) {
                return;
            }
            sourcePath = file.getAbsolutePath();
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(file));
            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    int eq = line.indexOf('=');
                    if (eq <= 0) {
                        continue;
                    }
                    String key = line.substring(0, eq).trim();
                    String value = line.substring(eq + 1).trim();
                    if (!key.isEmpty()) {
                        VALUES.put(key, value);
                    }
                }
            } finally {
                reader.close();
            }
        } catch (Throwable ignored) {
            // 开关文件读不到不该影响游戏。注意：容器（Koin）还没起来时 storageRootDir 会抛，
            // 这种情况不能把 loaded 卡死；但也不能每帧重试，5 次后放弃。
            failures++;
            loaded = failures > 5;
        }
    }

    /** 取一个开关值，没有则返回 null。 */
    public static String get(String key) {
        ensureLoaded();
        return VALUES.get(key);
    }

    /** 开关是否等于 1 / true。 */
    public static boolean isOn(String key) {
        String value = get(key);
        if (value == null) {
            return false;
        }
        value = value.trim();
        return "1".equals(value) || "true".equalsIgnoreCase(value);
    }

    /** 开关是否开着；键缺失时返回给定的默认值。 */
    public static boolean isOnDefault(String key, boolean defaultValue) {
        String value = get(key);
        if (value == null) {
            return defaultValue;
        }
        value = value.trim();
        if ("1".equals(value) || "true".equalsIgnoreCase(value)) {
            return true;
        }
        if ("0".equals(value) || "false".equalsIgnoreCase(value)) {
            return false;
        }
        return defaultValue;
    }

    /** 取整数开关，缺失或非法返回 fallback，并按 [min,max] 夹紧。 */
    public static int getInt(String key, int fallback, int min, int max) {
        String value = get(key);
        if (value == null) {
            return fallback;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
        if (parsed < min) {
            parsed = min;
        }
        if (parsed > max) {
            parsed = max;
        }
        return parsed;
    }

    /** 已经处理过开关文件（读到或确认不存在）时返回路径，文件不存在返回空串，尚未尝试返回 null。 */
    public static String loadedFrom() {
        ensureLoaded();
        if (!loaded) {
            return null;
        }
        return sourcePath == null ? "" : sourcePath;
    }
}
