package com.moji.NightCityCourier.core;

/**
 * 天气与时段系统。
 *
 * <p>原版每一单面对的都是同一个夜之城：概率恒定，久了就没有「今天不太对劲」的感觉。
 * 引入天气后，同样的路线在酸雨夜和断电夜会给出完全不同的风险收益——
 * 玩家开始需要读环境做判断，而不是套一个最优解。
 *
 * <p>天气只影响<b>倍率</b>，不直接改判定，因此不会与既有事件逻辑打架；
 * 所有数值集中在枚举里，调平衡只改一处。
 */
public final class WeatherSystem {

    /** 天气。三个维度：遭遇倍率、报酬倍率、通缉压力。 */
    public enum Weather {
        CLEAR("霓虹晴夜", "雨刚停，整条街的路面像湿透的电路板。", 1.00, 1.00, 0),
        ACID_RAIN("酸雨", "酸雨落在义体外壳上，发出细碎的滋滋声。", 1.15, 0.95, 1),
        NEON_FOG("霓虹雾", "雾把街区泡成一杯发光的牛奶，五米外什么都看不清。", 1.20, 1.00, 1),
        BLACKOUT("全城断电", "整片街区陷入黑暗，只剩应急灯在血红的节奏里闪。", 0.85, 1.30, -1),
        BLOOD_MOON("血月", "血月低低地压在城市上方，所有人都显得不太正常。", 1.35, 1.15, 2),
        ASH_FALL("灰烬雨", "旧工业区的烟囱又开始吐灰，落下来像一场慢动作的雪。", 1.05, 1.10, 0);

        private final String displayName;
        private final String flavor;
        private final double encounterMul;
        private final double rewardMul;
        private final int wantedPressure;

        Weather(String displayName, String flavor, double encounterMul, double rewardMul, int wantedPressure) {
            this.displayName = displayName;
            this.flavor = flavor;
            this.encounterMul = encounterMul;
            this.rewardMul = rewardMul;
            this.wantedPressure = wantedPressure;
        }

        public String displayName() {
            return displayName;
        }

        public String flavor() {
            return flavor;
        }

        public double encounterMul() {
            return encounterMul;
        }

        public double rewardMul() {
            return rewardMul;
        }

        public int wantedPressure() {
            return wantedPressure;
        }
    }

    /** 时段。深夜事件多但报酬高，清晨安静但单子便宜——同样是取舍。 */
    public enum Phase {
        DAWN("凌晨", 0.85, 1.15),
        MORNING("上午", 0.95, 0.95),
        DUSK("黄昏", 1.05, 1.00),
        NIGHT("深夜", 1.25, 1.10);

        private final String displayName;
        private final double encounterMul;
        private final double rewardMul;

        Phase(String displayName, double encounterMul, double rewardMul) {
            this.displayName = displayName;
            this.encounterMul = encounterMul;
            this.rewardMul = rewardMul;
        }

        public String displayName() {
            return displayName;
        }

        public double encounterMul() {
            return encounterMul;
        }

        public double rewardMul() {
            return rewardMul;
        }
    }

    private Weather weather = Weather.CLEAR;
    private Phase phase = Phase.NIGHT;

    public Weather weather() {
        return weather;
    }

    public Phase phase() {
        return phase;
    }

    /** 每次接单刷新环境。天气与时段独立抽取，组合出 24 种开局。 */
    public void roll(Rng rng) {
        if (rng == null) {
            return;
        }
        weather = rng.pick(Weather.values());
        phase = rng.pick(Phase.values());
    }

    /** 手动指定，便于测试与「剧情固定天气」的关卡。 */
    public void set(Weather w, Phase p) {
        if (w != null) {
            weather = w;
        }
        if (p != null) {
            phase = p;
        }
    }

    public double encounterMultiplier() {
        return weather.encounterMul() * phase.encounterMul();
    }

    public double rewardMultiplier() {
        return weather.rewardMul() * phase.rewardMul();
    }

    public int wantedPressure() {
        return weather.wantedPressure();
    }

    /** 一句话环境描述，接单时播报。 */
    public String flavor() {
        return weather.flavor() + "（" + phase.displayName() + "·" + weather.displayName() + "）";
    }

    // ────────── 存档 ──────────

    public java.util.Map<String, String> toSaveEntries() {
        java.util.Map<String, String> out = new java.util.LinkedHashMap<>();
        out.put("env.weather", weather.name());
        out.put("env.phase", phase.name());
        return out;
    }

    public void fromSaveEntries(java.util.Map<String, String> entries) {
        if (entries == null) {
            return;
        }
        try {
            weather = Weather.valueOf(entries.getOrDefault("env.weather", "CLEAR"));
        } catch (Exception ignored) {
            weather = Weather.CLEAR;
        }
        try {
            phase = Phase.valueOf(entries.getOrDefault("env.phase", "NIGHT"));
        } catch (Exception ignored) {
            phase = Phase.NIGHT;
        }
    }

    @Override
    public String toString() {
        return "WeatherSystem{" + phase.displayName() + "·" + weather.displayName() + "}";
    }
}
