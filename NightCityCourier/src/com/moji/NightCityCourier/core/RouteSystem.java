package com.moji.NightCityCourier.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 路线系统。
 *
 * <p>原版的赶路只有「走 N 步」——路线不是选择，只是进度条。本系统每次接单生成
 * 三条候选路线，各自在<b>步数 / 风险 / 报酬 / 事件密度</b>四个维度上做不同取舍：
 *
 * <ul>
 *   <li>想快：公司天桥、直穿主干道 —— 步数少，但撞上巡逻的概率高；</li>
 *   <li>想稳：排水渠、后巷穿行 —— 慢，但几乎不会遇到正面冲突；</li>
 *   <li>想赚：夜市外围、旧货运线 —— 绕远，但顺手能接私活，报酬上浮。</li>
 * </ul>
 *
 * <p>这样「赶路」从等待变成了博弈：同样的订单，不同的路线，收益曲线完全不同。
 */
public final class RouteSystem {

    /**
     * 一条候选路线。
     *
     * @param name         路线名
     * @param description  一句话描述
     * @param stepDelta    步数增减（可为负）
     * @param riskDelta    风险增减
     * @param rewardMul    报酬倍率
     * @param encounterMul 事件密度倍率
     * @param tag          短标签，用于 UI 显示
     */
    public record RouteOption(String name, String description, int stepDelta,
                              double riskDelta, double rewardMul, double encounterMul, String tag) {

        /** 给玩家看的收益预期，不暴露具体数字（保留一点不确定感）。 */
        public String hint() {
            String speed = stepDelta <= -2 ? "快" : stepDelta < 0 ? "略快" : stepDelta == 0 ? "正常" : "绕远";
            String risk = riskDelta <= -0.5 ? "很安全" : riskDelta < 0 ? "较安全"
                    : riskDelta == 0 ? "常规" : riskDelta < 0.6 ? "偏险" : "高危";
            String money = rewardMul >= 1.25 ? "报酬丰厚" : rewardMul > 1.0 ? "报酬略高"
                    : rewardMul == 1.0 ? "报酬正常" : "报酬偏低";
            return speed + " / " + risk + " / " + money;
        }
    }

    private static final RouteOption[] POOL = {
            new RouteOption("直穿主干道", "霓虹最亮的地方，也是摄像头最密的地方。", -1, 0.35, 1.00, 1.20, "险·快"),
            new RouteOption("后巷穿行", "垃圾桶、蒸汽管和一地碎玻璃。没人愿意走，也没人看得见你。", 1, -0.45, 0.95, 0.75, "稳·慢"),
            new RouteOption("高架桥下层", "车流在头顶呼啸，风把雨吹成横的。", -2, 0.15, 0.90, 1.00, "极快"),
            new RouteOption("废弃地铁隧道", "信号全无。你只能靠记忆走。", 0, -0.30, 1.00, 0.80, "静默"),
            new RouteOption("夜市外围", "人多，摊子多，机会也多。", 2, -0.10, 1.25, 1.10, "肥单"),
            new RouteOption("公司天桥", "玻璃幕墙反射着你的脸，安保无人机在头顶盘旋。", -2, 0.70, 1.30, 1.40, "赌命"),
            new RouteOption("旧排水渠", "积水到膝盖，气味让人怀疑人生。", 2, -0.60, 0.85, 0.60, "极限稳"),
            new RouteOption("旧货运线", "铁轨旁边散落着上一批货的碎片。", 1, 0.05, 1.15, 1.05, "顺路捞"),
    };

    private RouteSystem() {
    }

    /**
     * 生成三条互不相同的候选路线。
     *
     * <p>不是纯随机三连抽：先按「快 / 稳 / 赚」各锁定一条，再打乱顺序。
     * 这样每次都能给出真正的三选一，而不是「三条都是高危快线」这种伪选择。
     */
    public static List<RouteOption> offer(Rng rng) {
        List<RouteOption> fast = new ArrayList<>();
        List<RouteOption> safe = new ArrayList<>();
        List<RouteOption> rich = new ArrayList<>();
        for (RouteOption o : POOL) {
            if (o.stepDelta() <= -1) {
                fast.add(o);
            }
            if (o.riskDelta() <= -0.2) {
                safe.add(o);
            }
            if (o.rewardMul() >= 1.15) {
                rich.add(o);
            }
        }
        List<RouteOption> picked = new ArrayList<>(3);
        addDistinct(picked, pickOr(rng, fast));
        addDistinct(picked, pickOr(rng, safe));
        addDistinct(picked, pickOr(rng, rich));

        // 极端情况（理论上不会发生）兜底：用随机池补齐
        int guard = 0;
        while (picked.size() < 3 && guard++ < 32) {
            addDistinct(picked, pickOr(rng, List.of(POOL)));
        }
        while (picked.size() < 3) {
            addDistinct(picked, POOL[picked.size()]);
        }
        if (rng != null) {
            RouteOption[] arr = picked.toArray(new RouteOption[0]);
            rng.shuffle(arr);
            picked = new ArrayList<>(List.of(arr));
        }
        return picked;
    }

    /** 全部路线，用于「已走过的路线」统计展示。 */
    public static List<RouteOption> all() {
        return List.of(POOL);
    }

    private static RouteOption pickOr(Rng rng, List<RouteOption> pool) {
        if (pool.isEmpty()) {
            return null;
        }
        return rng == null ? pool.get(0) : rng.pick(pool);
    }

    private static void addDistinct(List<RouteOption> target, RouteOption option) {
        if (option == null || target.size() >= 3) {
            return;
        }
        for (RouteOption existing : target) {
            if (existing.name().equals(option.name())) {
                return;
            }
        }
        target.add(option);
    }
}
