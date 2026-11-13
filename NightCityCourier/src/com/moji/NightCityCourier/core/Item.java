package com.moji.NightCityCourier.core;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品与义体定义表。
 *
 * <p>原版的成长只有「花钱加三点属性」，缺少取舍：钱永远只有一种用法。
 * 本表把「钱」拆成三条互相竞争的花法——消耗品（一次性保险）、义体（永久薄利）、
 * 技能（等级门槛），玩家必须在「现在活下来」和「以后更省力」之间做选择。
 *
 * <p>物品分为两类：
 * <ul>
 *   <li>{@link Category#CONSUMABLE} 消耗品：使用即消耗，效果是单次或瞬时的；</li>
 *   <li>{@link Category#RIG} 义体/装备：买入后占用一个义体槽，效果永久生效。</li>
 * </ul>
 */
public enum Item {

    // ────────── 消耗品 ──────────

    MEDKIT("急救针", Category.CONSUMABLE, 120,
            "立刻恢复 40 点生命", Effect.HEAL, 40),

    STIM("战斗鸡尾酒", Category.CONSUMABLE, 180,
            "本单免疫一次任务失败，并回复 15 点生命", Effect.SHIELD, 1),

    COUNTERFEIT_ID("影子证件", Category.CONSUMABLE, 220,
            "立刻降低 1 级通缉", Effect.CLEAR_WANTED, 1),

    JAMMER("信号干扰器", Category.CONSUMABLE, 260,
            "本单事件遭遇率减半", Effect.JAM, 50),

    SMOKE("烟雾弹", Category.CONSUMABLE, 90,
            "本单下次威胁事件必定逃脱", Effect.SMOKE, 1),

    // ────────── 义体 / 装备 ──────────

    COLD_PACK("冷藏箱", Category.RIG, 400,
            "黑市件与器官件报酬 +20%", Effect.REWARD_BONUS, 20),

    GHOST_DECK("幽灵协处理器", Category.RIG, 650,
            "所有事件的闪避与逃脱判定 +10%", Effect.EVASION, 10),

    SUBCUT_ARMOR("皮下装甲", Category.RIG, 700,
            "受到的伤害 -25%", Effect.DAMAGE_REDUCE, 25),

    TURBO_KIT("涡轮套件", Category.RIG, 800,
            "每单赶路步数 -1（最低 2 步）", Effect.STEP_SAVE, 1),

    NEURAL_LINK("神经链路", Category.RIG, 900,
            "完成任务额外获得 12 点经验", Effect.XP_BONUS, 12),

    BLACK_ICE("黑冰破解器", Category.RIG, 1000,
            "扫描与无人机事件中自动绕开，且不增加通缉", Effect.BLACK_ICE, 1);

    public enum Category {
        CONSUMABLE, RIG
    }

    /** 效果类型。用枚举而非字符串，避免拼写漂移导致「买了没用」。 */
    public enum Effect {
        HEAL, SHIELD, CLEAR_WANTED, JAM, SMOKE,
        REWARD_BONUS, EVASION, DAMAGE_REDUCE, STEP_SAVE, XP_BONUS, BLACK_ICE
    }

    private final String displayName;
    private final Category category;
    private final int price;
    private final String description;
    private final Effect effect;
    private final int magnitude;

    Item(String displayName, Category category, int price, String description, Effect effect, int magnitude) {
        this.displayName = displayName;
        this.category = category;
        this.price = price;
        this.description = description;
        this.effect = effect;
        this.magnitude = magnitude;
    }

    public String displayName() {
        return displayName;
    }

    public Category category() {
        return category;
    }

    public int price() {
        return price;
    }

    public String description() {
        return description;
    }

    public Effect effect() {
        return effect;
    }

    public int magnitude() {
        return magnitude;
    }

    public boolean isRig() {
        return category == Category.RIG;
    }

    /** 商店里买得到的东西：消耗品与义体，按价格升序。 */
    public static List<Item> shopStock() {
        List<Item> list = new ArrayList<>(List.of(values()));
        list.sort((a, b) -> Integer.compare(a.price, b.price));
        return list;
    }

    /** 按名字查物品，忽略大小写与首尾空白；找不到返回 null。 */
    public static Item byName(String name) {
        if (name == null) {
            return null;
        }
        String target = name.trim();
        for (Item item : values()) {
            if (item.name().equalsIgnoreCase(target) || item.displayName.equals(target)) {
                return item;
            }
        }
        return null;
    }
}
