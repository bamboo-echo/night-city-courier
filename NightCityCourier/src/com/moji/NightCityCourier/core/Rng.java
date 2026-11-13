package com.moji.NightCityCourier.core;

import java.util.List;

/**
 * 可种子化的伪随机数发生器（SplitMix64）。
 *
 * <p>相比 {@code java.util.Random}，本类提供三个原版缺失的能力：
 * <ol>
 *   <li><b>可复现</b>：同一个种子必然产出同一串随机数，因此「种子挑战」与
 *       失败复盘成为可能；</li>
 *   <li><b>可派生</b>：{@link #fork(long)} 由父流派生子流，让「任务生成」与
 *       「事件抽取」互不污染，同一局里改一处不会让整盘棋全变；</li>
 *   <li><b>可注入</b>：系统层持有 Rng 实例而非静态全局变量，测试可以塞入固定种子。</li>
 * </ol>
 *
 * <p>算法为 SplitMix64：状态推进与输出混合分离，周期 2^64，分布质量远好于
 * 线性同余，且实现只有十几行、零依赖。
 */
public final class Rng {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;

    private final long seed;
    private long state;

    public Rng(long seed) {
        this.seed = seed;
        this.state = seed;
    }

    /** 由任意字符串派生稳定种子（FNV-1a 64），用于「今日挑战」这类按日期取种的场景。 */
    public static long seedOf(String text) {
        long hash = 0xCBF29CE484222325L;
        for (int i = 0; i < text.length(); i++) {
            hash ^= text.charAt(i);
            hash *= 0x100000001B3L;
        }
        return hash;
    }

    /** 创建一个由父种子派生的独立子流，互不干扰。 */
    public static Rng forkOf(long seed, long salt) {
        return new Rng(seed ^ (salt * GOLDEN_GAMMA));
    }

    public long seed() {
        return seed;
    }

    /** 当前状态快照，可用于「存档-读档后随机序列继续」或调试回放。 */
    public long state() {
        return state;
    }

    public void restoreState(long savedState) {
        this.state = savedState;
    }

    public long nextLong() {
        long z = (state += GOLDEN_GAMMA);
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** [0, bound) 的均匀整数。bound 必须为正。 */
    public int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound 必须为正数：" + bound);
        }
        return (int) Math.floorMod(nextLong(), bound);
    }

    /** [origin, bound) 的均匀整数。 */
    public int nextInt(int origin, int bound) {
        if (bound <= origin) {
            return origin;
        }
        return origin + nextInt(bound - origin);
    }

    /** [0.0, 1.0) 的均匀小数。取高 53 位保证精度。 */
    public double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /** 以 p 的概率返回 true；p <= 0 恒 false，p >= 1 恒 true。 */
    public boolean chance(double p) {
        if (p <= 0.0) {
            return false;
        }
        if (p >= 1.0) {
            return true;
        }
        return nextDouble() < p;
    }

    /**
     * 按权重抽取下标。权重为负按 0 处理；全为 0 时退化为均匀抽取。
     *
     * @return 命中的下标，数组为空时返回 -1
     */
    public int pickWeighted(int[] weights) {
        if (weights == null || weights.length == 0) {
            return -1;
        }
        long total = 0;
        for (int w : weights) {
            if (w > 0) {
                total += w;
            }
        }
        if (total <= 0) {
            return nextInt(weights.length);
        }
        long roll = Math.floorMod(nextLong(), total);
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= 0) {
                continue;
            }
            roll -= weights[i];
            if (roll < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    /** 按 double 权重抽取下标，用于概率表这类连续权重。 */
    public int pickWeighted(double[] weights) {
        if (weights == null || weights.length == 0) {
            return -1;
        }
        double total = 0;
        for (double w : weights) {
            if (w > 0) {
                total += w;
            }
        }
        if (total <= 0) {
            return nextInt(weights.length);
        }
        double roll = nextDouble() * total;
        for (int i = 0; i < weights.length; i++) {
            if (weights[i] <= 0) {
                continue;
            }
            roll -= weights[i];
            if (roll < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }

    /** 等概率抽一个元素；列表为空返回 null。 */
    public <T> T pick(List<T> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(nextInt(list.size()));
    }

    /** 等概率抽一个元素；数组为空返回 null。 */
    public <T> T pick(T[] arr) {
        if (arr == null || arr.length == 0) {
            return null;
        }
        return arr[nextInt(arr.length)];
    }

    /**
     * 原地洗牌（Fisher–Yates），返回同一数组便于链式调用。
     */
    public <T> T[] shuffle(T[] arr) {
        for (int i = arr.length - 1; i > 0; i--) {
            int j = nextInt(i + 1);
            T tmp = arr[i];
            arr[i] = arr[j];
            arr[j] = tmp;
        }
        return arr;
    }

    @Override
    public String toString() {
        return "Rng{seed=" + seed + ", state=" + state + "}";
    }
}
