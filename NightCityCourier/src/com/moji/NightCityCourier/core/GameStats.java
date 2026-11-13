package com.moji.NightCityCourier.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 局内统计。
 *
 * <p>原版只记了一个 {@code totalTasks}，玩家在结局时拿不到任何「我这一局到底怎么过的」
 * 反馈。本类用稀疏计数器记录过程中的所有关键事件，既不预设字段（便于持续扩内容），
 * 也保证零值时不会污染存档。
 *
 * <p>键名统一 {@code stat.} 前缀，直接序列化为存档片段。
 */
public final class GameStats {

    public static final String DELIVERIES = "stat.deliveries";
    public static final String FAILURES = "stat.failures";
    public static final String MONEY_EARNED = "stat.moneyEarned";
    public static final String MONEY_LOST = "stat.moneyLost";
    public static final String BEST_HAUL = "stat.bestHaul";
    public static final String DAMAGE_TAKEN = "stat.damageTaken";
    public static final String EVENTS_SEEN = "stat.eventsSeen";
    public static final String CLOSE_CALLS = "stat.closeCalls";
    public static final String STEPS_TRAVELED = "stat.stepsTraveled";
    public static final String BRIBERY = "stat.bribery";
    public static final String BLACK_MARKET = "stat.blackMarket";
    public static final String GAMBLES = "stat.gambles";
    public static final String KIND_ACTS = "stat.kindActs";
    public static final String CRUEL_ACTS = "stat.cruelActs";
    public static final String ROUTES_TAKEN = "stat.routesTaken";

    private final Map<String, Long> counters = new LinkedHashMap<>();

    public long get(String key) {
        return counters.getOrDefault(key, 0L);
    }

    public void inc(String key) {
        add(key, 1);
    }

    public void add(String key, long delta) {
        if (key == null || delta == 0) {
            return;
        }
        long next = get(key) + delta;
        if (next <= 0) {
            counters.remove(key);
        } else {
            counters.put(key, next);
        }
    }

    /** 记录「最高值」这类只升不降的指标。 */
    public void max(String key, long value) {
        if (value > get(key)) {
            counters.put(key, value);
        }
    }

    public void reset() {
        counters.clear();
    }

    // ────────── 派生指标 ──────────

    public long totalRuns() {
        return get(DELIVERIES) + get(FAILURES);
    }

    /** 成功率，0~1；从未出车时返回 0。 */
    public double successRate() {
        long total = totalRuns();
        return total == 0 ? 0.0 : (double) get(DELIVERIES) / total;
    }

    /** 净收入。 */
    public long netIncome() {
        return get(MONEY_EARNED) - get(MONEY_LOST);
    }

    public Map<String, Long> view() {
        return new LinkedHashMap<>(counters);
    }

    /** 只暴露非零计数，用于 UI 与结局摘要。 */
    public Map<String, Long> nonZeroView() {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : counters.entrySet()) {
            if (e.getValue() != 0) {
                out.put(e.getKey(), e.getValue());
            }
        }
        return out;
    }

    // ────────── 存档 ──────────

    public Map<String, String> toSaveEntries() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Long> e : counters.entrySet()) {
            out.put(e.getKey(), String.valueOf(e.getValue()));
        }
        return out;
    }

    public void fromSaveEntries(Map<String, String> entries) {
        counters.clear();
        if (entries == null) {
            return;
        }
        for (Map.Entry<String, String> e : entries.entrySet()) {
            if (!e.getKey().startsWith("stat.")) {
                continue;
            }
            try {
                long v = Long.parseLong(e.getValue().trim());
                if (v > 0) {
                    counters.put(e.getKey(), v);
                }
            } catch (Exception ignored) {
                // 单个坏值不应让整份存档失效
            }
        }
    }

    @Override
    public String toString() {
        return "GameStats" + counters;
    }
}
