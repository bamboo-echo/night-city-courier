package com.moji.NightCityCourier.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 背包与义体槽。
 *
 * <p>两条独立的容量约束：
 * <ul>
 *   <li>消耗品按<b>数量</b>堆叠，总量上限 {@link #MAX_STACKS}；</li>
 *   <li>义体按<b>槽位</b>占用，上限 {@link #MAX_RIG_SLOTS}。装满后必须卸下才能换装，
 *       这就把「全买一遍」变成「构筑选择」。</li>
 * </ul>
 *
 * <p>所有变更都返回布尔值而不是抛异常：游戏里买不起、背包满、槽位不够都是正常分支，
 * 不该用异常表达。
 */
public final class Inventory {

    public static final int MAX_STACKS = 12;
    public static final int MAX_RIG_SLOTS = 3;
    public static final int MAX_PER_ITEM = 5;

    private final Map<Item, Integer> consumables = new EnumMap<>(Item.class);
    private final List<Item> rigs = new ArrayList<>();

    // ────────── 消耗品 ──────────

    public int count(Item item) {
        if (item == null || item.isRig()) {
            return 0;
        }
        return consumables.getOrDefault(item, 0);
    }

    public int usedStacks() {
        int total = 0;
        for (int n : consumables.values()) {
            total += n;
        }
        return total;
    }

    public boolean has(Item item) {
        return count(item) > 0;
    }

    /** 加入一个消耗品。背包满或超过单件上限时返回 false。 */
    public boolean add(Item item) {
        if (item == null || item.isRig()) {
            return false;
        }
        int current = consumables.getOrDefault(item, 0);
        if (current >= MAX_PER_ITEM || usedStacks() >= MAX_STACKS) {
            return false;
        }
        consumables.put(item, current + 1);
        return true;
    }

    public boolean remove(Item item) {
        int current = consumables.getOrDefault(item, 0);
        if (current <= 0) {
            return false;
        }
        if (current == 1) {
            consumables.remove(item);
        } else {
            consumables.put(item, current - 1);
        }
        return true;
    }

    /** 消耗品快照，按枚举顺序，供 UI 列表框使用。 */
    public Map<Item, Integer> consumableView() {
        return new LinkedHashMap<>(consumables);
    }

    public boolean isEmpty() {
        return consumables.isEmpty() && rigs.isEmpty();
    }

    // ────────── 义体 ──────────

    public List<Item> rigs() {
        return List.copyOf(rigs);
    }

    public boolean hasRig(Item item) {
        return item != null && rigs.contains(item);
    }

    public boolean rigSlotsFull() {
        return rigs.size() >= MAX_RIG_SLOTS;
    }

    /** 安装义体。已安装、非义体、槽位满三种情况返回 false。 */
    public boolean install(Item item) {
        if (item == null || !item.isRig() || rigs.contains(item)) {
            return false;
        }
        if (rigSlotsFull()) {
            return false;
        }
        rigs.add(item);
        return true;
    }

    public boolean uninstall(Item item) {
        return rigs.remove(item);
    }

    /** 已安装义体提供的效果总量。 */
    public int rigBonus(Item.Effect effect) {
        int total = 0;
        for (Item rig : rigs) {
            if (rig.effect() == effect) {
                total += rig.magnitude();
            }
        }
        return total;
    }

    // ────────── 存档 ──────────

    /** 序列化为 {@code key=value} 片段，键名带 {@code inv.} 前缀避免与其它系统冲突。 */
    public Map<String, String> toSaveEntries() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> e : consumables.entrySet()) {
            out.put("inv." + e.getKey().name(), String.valueOf(e.getValue()));
        }
        StringBuilder rigList = new StringBuilder();
        for (Item rig : rigs) {
            if (rigList.length() > 0) {
                rigList.append(',');
            }
            rigList.append(rig.name());
        }
        out.put("inv.rigs", rigList.toString());
        return out;
    }

    /** 从存档恢复。未知物品名直接跳过——旧版本存档遇到新物品不应整份报废。 */
    public void fromSaveEntries(Map<String, String> entries) {
        consumables.clear();
        rigs.clear();
        if (entries == null) {
            return;
        }
        for (Map.Entry<String, String> e : entries.entrySet()) {
            if (!e.getKey().startsWith("inv.") || "inv.rigs".equals(e.getKey())) {
                continue;
            }
            Item item = Item.byName(e.getKey().substring(4));
            if (item == null || item.isRig()) {
                continue;
            }
            int n = parse(e.getValue(), 0);
            if (n > 0) {
                consumables.put(item, Math.min(n, MAX_PER_ITEM));
            }
        }
        String rigList = entries.getOrDefault("inv.rigs", "");
        for (String name : rigList.split(",")) {
            Item item = Item.byName(name);
            if (item != null && item.isRig() && rigs.size() < MAX_RIG_SLOTS && !rigs.contains(item)) {
                rigs.add(item);
            }
        }
    }

    public void clear() {
        consumables.clear();
        rigs.clear();
    }

    private static int parse(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return "Inventory{stack=" + consumables + ", rigs=" + rigs + "}";
    }
}
