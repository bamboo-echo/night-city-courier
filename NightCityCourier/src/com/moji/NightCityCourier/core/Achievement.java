package com.moji.NightCityCourier.core;

/**
 * 成就定义表。
 *
 * <p>成就刻意覆盖三种完全不同的玩法路径，逼玩家去尝试平时不会选的选项：
 * <ul>
 *   <li><b>效率类</b>：连续成功、零伤害通关、单笔最高收入；</li>
 *   <li><b>道德类</b>：一路行善 / 一路冷血，两条都对，但互斥；</li>
 *   <li><b>猎奇类</b>：把某类事件刷到极致、在通缉满级时活下来。</li>
 * </ul>
 *
 * <p>{@code hidden} 为 true 的成就在解锁前只显示「???」，给探索留一点惊喜。
 */
public enum Achievement {

    FIRST_DELIVERY("第一单", "完成第一次派送。夜之城欢迎你，用一种不太客气的方式。", false),
    FIRST_DEATH("第一次死", "你在夜之城死过一次。这座城市不会记得，但你会。", false),

    STREAK_5("连成一串", "连续 5 单无失败。运气也是实力的一部分。", false),
    STREAK_10("十连", "连续 10 单无失败。街面上开始有人念你的名字。", false),

    FLAWLESS("毫发无伤", "在单局中完成 10 单且从未受伤。", true),
    RICH("攒够了", "个人资产达到 5000€。", false),
    WHALE("一条大单", "单笔报酬超过 1000€。", false),

    SAINT("夜之城的圣人", "善意选择累计 10 次。有人说你活不长。", false),
    BUTCHER("冷血动物", "冷血选择累计 10 次。有人说你会活到最后。", false),

    GHOST_FRIEND("幽灵的号码", "接受过至少一次不明通讯的协助。", true),
    GAMBLER("赌徒", "参与 5 次赌局。", false),
    BLACK_MARKET_REGULAR("黑市常客", "完成 5 单黑市件。", false),
    BRIBER("润滑剂", "累计行贿 5 次。这座城市靠这个运转。", false),

    MAX_WANTED_SURVIVE("通缉满级", "在五级通缉下完成一单。全城的灯都在找你。", true),
    STREET_KNOWLEDGE("街面通", "把三条不同路线都走一遍。", false),
    SCAV_TRUST("屋顶的朋友", "拾荒者声望达到「同盟」。", true),

    NETRUNNER("网潜", "把网潜者技能点到满级。", true),
    FULL_RIG("三件套", "同时装备三件义体。", false),
    ALMOST("差一点", "生命值 1 点时完成一单。", true),

    ESCAPE_ARTIST("逃亡艺术家", "累计成功逃脱 10 次。", false),
    NIGHT_OWL("夜行者", "在深夜时段完成 20 单。", true),
    STORM_RIDER("风雨无阻", "在酸雨或血月中完成 10 单。", true),

    LEGEND("传奇", "达成「传奇英雄」结局。", true),
    QUIET_LEAVE("安静地离开", "达成「平凡逃离」结局。", false);

    private final String title;
    private final String description;
    private final boolean hidden;

    Achievement(String title, String description, boolean hidden) {
        this.title = title;
        this.description = description;
        this.hidden = hidden;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public boolean isHidden() {
        return hidden;
    }

    public String displayText(boolean unlocked) {
        if (unlocked) {
            return "★ " + title + " —— " + description;
        }
        if (hidden) {
            return "☆ ??? —— 隐藏成就";
        }
        return "☆ " + title + " —— " + description;
    }
}
