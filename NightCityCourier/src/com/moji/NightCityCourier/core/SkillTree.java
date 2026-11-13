package com.moji.NightCityCourier.core;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 技能树。
 *
 * <p>原版升级是「等级到了自动加血上限」，玩家没有决策权。技能树把升级改成
 * <b>资源分配</b>：每升一级得 1 点技能点，点在哪个分支决定了这一局怎么打——
 * 潜行流少打架、议价流少花钱、铁胃流续航长。技能可多级，点数总量有限，
 * 因此不可能全点满。
 *
 * <p>设计上刻意不做「技能重置」：选择要有代价，跑偏了就是这一局的剧本。
 */
public final class SkillTree {

    /** 技能定义。{@code maxRank} 为可点上限，{@code costPerRank} 为每级消耗点数。 */
    public enum Skill {
        GHOST_STEP("潜行步法", "事件自动闪避 +6%/级", 3, 1),
        SILVER_TONGUE("银舌头", "贿赂与治疗费用 -10%/级", 3, 1),
        WRENCH_SENSE("扳手直觉", "义体故障自愈概率 +25%/级", 2, 1),
        STREET_EYE("街头之眼", "接单时可见隐藏变体与真实风险", 1, 2),
        IRON_STOMACH("铁胃", "任务成功额外回血 +8/级", 3, 1),
        HAGGLER("议价高手", "任务报酬 +8%/级", 3, 1),
        LAST_STAND("最后一搏", "生命低于 20 时受伤 -20%/级", 2, 1),
        NETRUNNER("网潜者", "扫描/无人机事件免疫并生成情报", 1, 3);

        private final String displayName;
        private final String description;
        private final int maxRank;
        private final int costPerRank;

        Skill(String displayName, String description, int maxRank, int costPerRank) {
            this.displayName = displayName;
            this.description = description;
            this.maxRank = maxRank;
            this.costPerRank = costPerRank;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }

        public int maxRank() {
            return maxRank;
        }

        public int costPerRank() {
            return costPerRank;
        }
    }

    private final Map<Skill, Integer> ranks = new LinkedHashMap<>();
    private int points;

    public int points() {
        return points;
    }

    public void addPoints(int n) {
        if (n > 0) {
            points += n;
        }
    }

    public boolean spendPoint(Skill skill) {
        return spendPoints(skill, 1);
    }

    /**
     * 连点多级。点数不足或已达上限时，能点几级点几级，返回是否至少成功一级。
     */
    public boolean spendPoints(Skill skill, int times) {
        if (skill == null || times <= 0) {
            return false;
        }
        boolean any = false;
        for (int i = 0; i < times; i++) {
            int current = rank(skill);
            if (current >= skill.maxRank()) {
                break;
            }
            int cost = skill.costPerRank();
            if (points < cost) {
                break;
            }
            points -= cost;
            ranks.merge(skill, 1, Integer::sum);
            any = true;
        }
        return any;
    }

    public int rank(Skill skill) {
        return ranks.getOrDefault(skill, 0);
    }

    public boolean has(Skill skill) {
        return rank(skill) > 0;
    }

    public int totalRanks() {
        int total = 0;
        for (int r : ranks.values()) {
            total += r;
        }
        return total;
    }

    /** 可点技能列表（未达上限且点数够），供 UI 灰显判断。 */
    public Map<Skill, Boolean> availability() {
        Map<Skill, Boolean> map = new LinkedHashMap<>();
        for (Skill s : Skill.values()) {
            map.put(s, rank(s) < s.maxRank() && points >= s.costPerRank());
        }
        return map;
    }

    // ────────── 派生数值（供核心循环查询，避免把公式散落在各处） ──────────

    /** 事件自动闪避的额外概率。 */
    public double evasionBonus() {
        return rank(Skill.GHOST_STEP) * 0.06;
    }

    /** 费用折扣比例（0.1 = 打九折）。 */
    public double costDiscount() {
        return Math.min(0.3, rank(Skill.SILVER_TONGUE) * 0.10);
    }

    /** 义体故障自愈概率。 */
    public double selfRepairChance() {
        return Math.min(0.9, rank(Skill.WRENCH_SENSE) * 0.25);
    }

    /** 任务成功额外回血。 */
    public int bonusHeal() {
        return rank(Skill.IRON_STOMACH) * 8;
    }

    /** 报酬加成比例。 */
    public double rewardBonus() {
        return rank(Skill.HAGGLER) * 0.08;
    }

    /** 低血量时的减伤比例。 */
    public double lastStandReduction() {
        return Math.min(0.6, rank(Skill.LAST_STAND) * 0.20);
    }

    public boolean canSeeHiddenInfo() {
        return has(Skill.STREET_EYE);
    }

    public boolean isNetrunner() {
        return has(Skill.NETRUNNER);
    }

    // ────────── 存档 ──────────

    public Map<String, String> toSaveEntries() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("skill.points", String.valueOf(points));
        for (Map.Entry<Skill, Integer> e : ranks.entrySet()) {
            out.put("skill." + e.getKey().name(), String.valueOf(e.getValue()));
        }
        return out;
    }

    public void fromSaveEntries(Map<String, String> entries) {
        ranks.clear();
        points = 0;
        if (entries == null) {
            return;
        }
        points = parse(entries.get("skill.points"), 0);
        for (Skill s : Skill.values()) {
            int r = parse(entries.get("skill." + s.name()), 0);
            if (r > 0) {
                ranks.put(s, Math.min(r, s.maxRank()));
            }
        }
    }

    private static int parse(String text, int fallback) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return "SkillTree{points=" + points + ", ranks=" + ranks + "}";
    }
}
