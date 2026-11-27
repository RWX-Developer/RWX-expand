package io.github.rwx.pathing;

/**
 * RWX：单位路线巡检的运行时配置。
 *
 * <p>全部由系统属性 / 环境变量控制，缺省即启用（可在不重新编译的前提下关掉做 A/B）：
 *
 * <p><table>
 *   <tr><th>系统属性</th><th>环境变量</th><th>缺省</th><th>含义</th></tr>
 *   <tr><td>rwx.routeCheck</td><td>RWX_ROUTE_CHECK</td><td>5</td><td>每多少个 tick 巡检一次路线，0 = 关闭</td></tr>
 *   <tr><td>rwx.routeCooldown</td><td>RWX_ROUTE_COOLDOWN</td><td>60</td><td>两次巡检重算之间的最小间隔（tick）</td></tr>
 *   <tr><td>rwx.routeProbe</td><td>RWX_ROUTE_PROBE</td><td>8</td><td>每次向前抽查多少个航点</td></tr>
 *   <tr><td>rwx.routeMigrate</td><td>RWX_ROUTE_MIGRATE</td><td>1</td><td>终点不可站时是否迁移终点，0 = 关闭</td></tr>
 *   <tr><td>rwx.routeMigrateAttack</td><td>RWX_ROUTE_MIGRATE_ATTACK</td><td>1</td><td>攻击指令的目标格不可站时是否也迁移（近距离攻击打不出去就靠这个）</td></tr>
 *   <tr><td>rwx.routeMigrateTries</td><td>RWX_ROUTE_MIGRATE_TRIES</td><td>3</td><td>迁移终点的最大尝试次数（每次扩大搜索半径）</td></tr>
 *   <tr><td>rwx.routeMigrateRadius</td><td>RWX_ROUTE_MIGRATE_RADIUS</td><td>24</td><td>迁移搜索的最大半径（格）</td></tr>
 *   <tr><td>rwx.routeRepathMax</td><td>RWX_ROUTE_REPATH_MAX</td><td>6</td><td>同一指令内最多自动重算几次，防打转</td></tr>
 *   <tr><td>rwx.routeLog</td><td>RWX_ROUTE_LOG</td><td>120</td><td>日志最多打多少行，0 = 不打</td></tr>
 * </table>
 *
 * <p>巡检只看地面/水面单位，飞行单位不受地形限制，直接跳过。
 */
public final class RwxRouteTuning {

    /** 每多少个 tick 巡检一次路线；0 表示关闭整个巡检。 */
    public static final int CHECK_TICKS;

    /** 两次巡检重算之间的最小间隔（tick）。 */
    public static final int REPATH_COOLDOWN_TICKS;

    /** 每次巡检向前抽查的航点数量。 */
    public static final int PROBE_WAYPOINTS;

    /** 终点不可站时是否迁移到最近可站格。 */
    public static final boolean MIGRATE_ENDPOINT;

    /** 攻击指令的终点（目标单位所在格）不可站时，是否也迁到最近可站格。 */
    public static final boolean MIGRATE_ATTACK;

    /** 迁移终点的最大尝试次数。 */
    public static final int MIGRATE_TRIES;

    /** 迁移搜索的最大半径（格）。 */
    public static final int MIGRATE_MAX_RADIUS;

    /** 同一指令内自动重算的次数上限。 */
    public static final int REPATH_MAX_PER_ORDER;

    /** 日志行数上限。 */
    public static final int LOG_LIMIT;

    static {
        CHECK_TICKS = intValue("rwx.routeCheck", "RWX_ROUTE_CHECK", 5, 0, 120);
        REPATH_COOLDOWN_TICKS = intValue("rwx.routeCooldown", "RWX_ROUTE_COOLDOWN", 60, 0, 6000);
        PROBE_WAYPOINTS = intValue("rwx.routeProbe", "RWX_ROUTE_PROBE", 8, 0, 40);
        MIGRATE_ENDPOINT = intValue("rwx.routeMigrate", "RWX_ROUTE_MIGRATE", 1, 0, 1) != 0;
        MIGRATE_ATTACK = intValue("rwx.routeMigrateAttack", "RWX_ROUTE_MIGRATE_ATTACK", 1, 0, 1) != 0;
        MIGRATE_TRIES = intValue("rwx.routeMigrateTries", "RWX_ROUTE_MIGRATE_TRIES", 3, 0, 16);
        MIGRATE_MAX_RADIUS = intValue("rwx.routeMigrateRadius", "RWX_ROUTE_MIGRATE_RADIUS", 24, 1, 96);
        REPATH_MAX_PER_ORDER = intValue("rwx.routeRepathMax", "RWX_ROUTE_REPATH_MAX", 6, 0, 64);
        LOG_LIMIT = intValue("rwx.routeLog", "RWX_ROUTE_LOG", 120, 0, 100000);
    }

    private static int intValue(String property, String env, int fallback, int min, int max) {
        int value = fallback;
        String fromProp = System.getProperty(property);
        if (fromProp != null && !fromProp.trim().isEmpty()) {
            value = parseInt(fromProp, value);
        } else {
            String fromEnv = System.getenv(env);
            if (fromEnv != null && !fromEnv.trim().isEmpty()) {
                value = parseInt(fromEnv, value);
            } else {
                // 安卓端设不了环境变量：补一个 flags 文件入口（Key 与环境变量同名）
                String fromFlag = io.github.rwx.probe.RwxFlags.get(env);
                if (fromFlag != null && !fromFlag.trim().isEmpty()) {
                    value = parseInt(fromFlag, value);
                }
            }
        }
        if (value < min) {
            value = min;
        }
        if (value > max) {
            value = max;
        }
        return value;
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private RwxRouteTuning() {
    }
}
