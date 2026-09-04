package com.moji.NightCityCourier;

/**
 * 任务管理器，负责随机生成任务（类型、变体、参数）。
 * 包含任务类型枚举和变体枚举的定义。
 */
public class MissionManager {

    /** 任务类型枚举 */
    public enum MissionType {
        NORMAL("📦 普通快递", 1.0, 1.0, 0),
        BLACK_MARKET("💀 黑市件", 2.0, 1.3, 0),
        URGENT("⚡ 紧急件", 2.2, 1.3, 50);

        public final String displayName;
        /** 报酬倍率 */
        public final double rewardMultiplier;
        /** 风险倍率 */
        public final double riskMultiplier;
        /** 失败罚金 */
        public final int failPenalty;

        MissionType(String displayName, double rewardMultiplier, double riskMultiplier, int failPenalty) {
            this.displayName = displayName;
            this.rewardMultiplier = rewardMultiplier;
            this.riskMultiplier = riskMultiplier;
            this.failPenalty = failPenalty;
        }
    }

    /** 任务变体枚举，每种变体对应不同的特殊剧情 */
    public enum MissionVariant {
        NONE(null),
        TRAP("陷阱件"),
        LIVING("活体件"),
        CELEBRITY("名人件"),
        LEGACY("遗物件"),
        RACE("竞速件"),
        GANG_TRAP("圈套件"),
        LIFESAVER("救命件"),
        REROUTE("错送件"),
        CONFESSION("告白件"),
        EVIDENCE("证据件");

        public final String displayName;

        MissionVariant(String displayName) {
            this.displayName = displayName;
        }
    }

    /** 按权重随机生成任务类型：50%普通件，30%黑市件，20%紧急件 */
    private MissionType randomMissionType() {
        int roll = GameConfig.RANDOM.nextInt(100);
        if (roll < 50) {
            return MissionType.NORMAL;
        } else if (roll < 80) {
            return MissionType.BLACK_MARKET;
        } else {
            return MissionType.URGENT;
        }
    }

    /** 按 VARIANT_CHANCE 概率随机分配变体，排除 NONE 从索引1开始取值 */
    private MissionVariant randomVariant() {
        if (GameConfig.RANDOM.nextDouble() >= GameConfig.VARIANT_CHANCE) {
            return MissionVariant.NONE;
        }
        MissionVariant[] variants = MissionVariant.values();
        try {
            int index = 1 + GameConfig.RANDOM.nextInt(variants.length - 1);
            return variants[index];
        } catch (IllegalArgumentException e) {
            return MissionVariant.NONE;
        }
    }

    /**
     * 生成一个随机任务。
     *
     * @param player 玩家对象（用于计算速度对赶路步数的减免）
     * @return 随机生成的任务实例
     */
    public Mission generateMission(Player player) {
        MissionType type = randomMissionType();
        MissionVariant variant = randomVariant();
        int baseRisk = GameConfig.RANDOM.nextInt(3) + 1;
        int baseReward = baseRisk * 100 + GameConfig.RANDOM.nextInt(150);
        int finalReward = (int) (baseReward * type.rewardMultiplier);
        double effectiveRisk = baseRisk * type.riskMultiplier;
        int reduction = player.getSpeed() / 2;
        int timeNeed = Math.max(2, GameConfig.RANDOM.nextInt(8) + 3 - reduction);
        return new Mission(type, variant, effectiveRisk, finalReward, timeNeed, type.failPenalty);
    }

    /** 任务数据类，封装一次任务的所有属性 */
    public static class Mission {
        private final MissionType type;
        private final MissionVariant variant;
        /** 风险星级（可为小数） */
        private double risk;
        /** 完成报酬（€） */
        private int reward;
        /** 需要赶路的步数 */
        private int timeNeed;
        /** 失败扣除金额（仅紧急件非零） */
        private int failPenalty;

        public Mission(MissionType type, MissionVariant variant, double risk, int reward, int timeNeed, int failPenalty) {
            this.type = type;
            this.variant = variant;
            this.risk = risk;
            this.reward = reward;
            this.timeNeed = timeNeed;
            this.failPenalty = failPenalty;
        }

        public MissionType getType() { return type; }
        public MissionVariant getVariant() { return variant; }
        public double getRisk() { return risk; }
        public int getReward() { return reward; }

        /** 设置任务报酬（用于事件影响） */
        public void setReward(int reward) {
            this.reward = reward;
        }
        public int getTimeNeed() { return timeNeed; }
        public int getFailPenalty() { return failPenalty; }

        /** 降低任务风险值，最低降至0.1 */
        public void reduceRisk(double amount) {
            this.risk = Math.max(0.1, this.risk - amount);
        }

        /** 免除失败罚金（幽灵帮助效果） */
        public void clearFailPenalty() {
            this.failPenalty = 0;
        }

        /** 增加赶路步数（扫描绕路效果） */
        public void addTimeNeed(int extra) {
            this.timeNeed += extra;
        }

        /** 获取风险的显示文本 */
        public String getRiskDisplay() {
            return String.format("%.1f星", risk);
        }
    }
}