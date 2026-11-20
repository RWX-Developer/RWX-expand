package com.corrodinggames.rts.game.ai;

import com.corrodinggames.rts.game.units.UnitType;
import com.corrodinggames.rts.game.units.custom.CustomUnitConfig;
import com.corrodinggames.rts.game.units.custom.condition.StoredResourceEntry;
import com.corrodinggames.rts.game.units.custom.condition.StoredResources;
import com.corrodinggames.rts.game.units.custom.price.UnitPrice;

import java.util.HashMap;

/**
 * RWX 经济感知层。
 *
 * 目的：让 AI 不再只靠「候选池里有没有这个单位」来决定造什么，而是看
 * <ul>
 *   <li>手里现有多少资源（credits + 全部自定义资源，名字从运行时表动态读，不写死）</li>
 *   <li>造这个单位要多少资源（UnitPrice.b 与 UnitPrice.k）</li>
 *   <li>本队当前的收入率（credits 与自定义资源）</li>
 * </ul>
 *
 * 所有资源名都来自 {@link StoredResourceEntry#a}，因此对任何模组都成立，
 * 不需要预先把资源名字注册进引擎。
 */
public final class AiEconomy {

    private AiEconomy() {
    }

    /** 每个队伍的资源快照缓存；名字 -> 数量。 */
    private static final HashMap<Integer, HashMap<String, Double>> rwxStockByTeam = new HashMap<Integer, HashMap<String, Double>>();
    private static final HashMap<Integer, Long> rwxStockTickByTeam = new HashMap<Integer, Long>();

    /** 快照有效期（tick）。资源变化很快，取短一点，但别每单位都重建。 */
    private static final long RWX_STOCK_TTL = 30L;

    /** 读某队当前的全部自定义资源。名字来自运行时表，与模组无关。 */
    public static HashMap<String, Double> rwxStock(AIController ai, long tick) {
        Integer key = Integer.valueOf(ai.teamId);
        Long cachedAt = rwxStockTickByTeam.get(key);
        if (cachedAt != null && tick - cachedAt.longValue() < RWX_STOCK_TTL) {
            HashMap<String, Double> cached = rwxStockByTeam.get(key);
            if (cached != null) {
                return cached;
            }
        }
        HashMap<String, Double> map = new HashMap<String, Double>();
        try {
            StoredResources sr = ai.getCustomResources();
            if (sr != null && sr.b.size > 0) {
                Object[] arr = sr.b.a();
                int n = sr.b.size;
                for (int i = 0; i < n; i++) {
                    StoredResourceEntry e = (StoredResourceEntry) arr[i];
                    if (e == null || e.a == null) {
                        continue;
                    }
                    map.put(e.a.b, Double.valueOf(e.b));
                }
            }
        } catch (Throwable th) {
        }
        rwxStockByTeam.put(key, map);
        rwxStockTickByTeam.put(key, Long.valueOf(tick));
        return map;
    }

    /**
     * 造这个单位还差多少资源。返回 0 表示现在就能造；正数越大越造不起。
     * credits 与自定义资源分别累加缺口。
     */
    public static double rwxDeficit(AIController ai, UnitType type, int queue, long tick) {
        UnitPrice price;
        try {
            price = type.d(queue);
        } catch (Throwable th) {
            return 0.0d;
        }
        if (price == null) {
            return 0.0d;
        }
        double deficit = 0.0d;
        double credits = ai.credits;
        if (price.b > credits) {
            deficit += (price.b - credits);
        }
        StoredResources need = price.k;
        if (need != null && need.b.size > 0) {
            HashMap<String, Double> have = rwxStock(ai, tick);
            Object[] arr = need.b.a();
            int n = need.b.size;
            for (int i = 0; i < n; i++) {
                StoredResourceEntry e = (StoredResourceEntry) arr[i];
                if (e == null || e.a == null || e.b == 0.0d) {
                    continue;
                }
                Double h = have.get(e.a.b);
                double d = e.b - (h == null ? 0.0d : h.doubleValue());
                if (d > 0.0d) {
                    deficit += d;
                }
            }
        }
        return deficit;
    }

    /** 这个单位是不是资源建筑（两种标记任一成立）。 */
    public static boolean rwxProducesResource(UnitType type) {
        if (type == null) {
            return false;
        }
        if (type.n()) {
            return true;
        }
        return type instanceof CustomUnitConfig && ((CustomUnitConfig) type).resourceRate > 0.0f;
    }

    /**
     * 给建造权重乘的系数。
     * <ul>
     *   <li>造不起：压到 2%，不完全排除，让 AI 偶尔攒钱而不是死等</li>
     *   <li>资源建筑：收入越低加权越高（原来所有单位都是 10.0 平权重，等于没有经济判断）</li>
     * </ul>
     */
    public static float rwxPriorityMultiplier(AIController ai, UnitType type, int queue, long tick) {
        float mult = 1.0f;
        try {
            if (rwxDeficit(ai, type, queue, tick) > 0.0d) {
                mult *= 0.02f;
            }
            if (rwxProducesResource(type)) {
                int income = ai.getEconomyScaledIncomeRate();
                if (income < 200) {
                    mult *= 3.0f;
                } else if (income < 800) {
                    mult *= 2.0f;
                } else if (income < 2000) {
                    mult *= 1.4f;
                }
            }
        } catch (Throwable th) {
        }
        return mult;
    }
}
