package com.moji.NightCityCourier.core;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 每日挑战。
 *
 * <p>核心是「同一个种子，所有人的夜之城都一样」：日期决定种子，种子决定天气、时段、
 * 路线与事件抽取。于是同一天开局的人面对的是<b>同一座城</b>，
 * 分数才有可比性——这是把单机游戏变成可讨论话题的最低成本做法。
 *
 * <p>每天额外附加一条「城市规则」修正（如全城酸雨、报酬打折），
 * 让固定流程也需要重新想打法。
 */
public final class DailyChallenge {

    private static final DateTimeFormatter KEY = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 一天挑战的完整规格。
     *
     * @param dateKey   日期键，YYYY-MM-DD
     * @param seed      由日期派生的种子
     * @param ruleName  当日规则名
     * @param ruleDesc  规则说明
     * @param rewardMul 报酬倍率
     * @param riskMul   风险倍率
     * @param forced    强制天气（null 表示随机）
     */
    public record Spec(String dateKey, long seed, String ruleName, String ruleDesc,
                       double rewardMul, double riskMul, WeatherSystem.Weather forced) {

        public boolean isForcedWeather() {
            return forced != null;
        }

        /** 给玩家看的一行说明。 */
        public String headline() {
            return "【每日挑战 " + dateKey + "】" + ruleName + " —— " + ruleDesc;
        }
    }

    private static final Object[][] RULES = {
            {"风平浪静", "今天什么都不会发生。这本身就是个坏消息。", 1.00, 1.00, null},
            {"酸雨封城", "全城酸雨。路面打滑，义体过热，报酬略微上浮。", 1.10, 1.05,
                    WeatherSystem.Weather.ACID_RAIN},
            {"血月当空", "血月。所有人都不太正常，事件密度显著上升。", 1.20, 1.20,
                    WeatherSystem.Weather.BLOOD_MOON},
            {"断电之夜", "全城断电，摄像头瞎了，但黑市更活跃。", 1.25, 0.95,
                    WeatherSystem.Weather.BLACKOUT},
            {"霓虹雾障", "雾把街区糊成一片。你只能信任自己的记忆。", 1.05, 1.10,
                    WeatherSystem.Weather.NEON_FOG},
            {"灰烬满天", "旧工业区又开工了。灰落进每一口呼吸里。", 1.15, 1.00,
                    WeatherSystem.Weather.ASH_FALL},
            {"无人区", "街上出奇地空。可惜单子也少。", 0.90, 0.85, null},
            {"涨价日", "全城物价上浮，报酬也跟着涨，风险加倍。", 1.30, 1.25, null},
            {"限行令", "城警封了主干道。所有路线都要绕远。", 1.00, 1.15, null},
            {"怀旧夜", "旧城区的灯全亮了，像二十年前那样。", 1.10, 0.90, null},
    };

    private DailyChallenge() {
    }

    /** 指定日期生成规格。规则按日期序号轮换，保证长期不重复。 */
    public static Spec forDate(LocalDate date) {
        LocalDate d = date == null ? LocalDate.now() : date;
        String key = d.format(KEY);
        long seed = Rng.seedOf("nightcity-" + key);
        int idx = (int) Math.floorMod(d.toEpochDay(), RULES.length);
        Object[] rule = RULES[idx];
        return new Spec(
                key,
                seed,
                (String) rule[0],
                (String) rule[1],
                (Double) rule[2],
                (Double) rule[3],
                (WeatherSystem.Weather) rule[4]);
    }

    public static Spec today() {
        return forDate(LocalDate.now());
    }

    /** 未来七天的挑战预告，用于「明天会是什么」的期待感。 */
    public static List<Spec> upcoming() {
        List<Spec> list = new ArrayList<>();
        LocalDate base = LocalDate.now();
        for (int i = 1; i <= 7; i++) {
            list.add(forDate(base.plusDays(i)));
        }
        return list;
    }

    /** 由分数给出评级，用于本地记录。 */
    public static String grade(long score) {
        if (score >= 20000) {
            return "S · 传奇";
        }
        if (score >= 12000) {
            return "A · 老手";
        }
        if (score >= 6000) {
            return "B · 站稳了";
        }
        if (score >= 2000) {
            return "C · 还在爬";
        }
        return "D · 下次吧";
    }

    /** 挑战分数：净收入 × 成功率加权 + 成就加成。 */
    public static long score(int money, long deliveries, long failures, int achievementCount) {
        long base = Math.max(0, money);
        long total = deliveries + failures;
        double rate = total == 0 ? 0.0 : (double) deliveries / total;
        return (long) Math.round(base * (0.6 + rate * 0.8)) + (long) achievementCount * 300L;
    }
}
