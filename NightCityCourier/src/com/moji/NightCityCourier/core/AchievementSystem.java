package com.moji.NightCityCourier.core;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 成就解锁管理。
 *
 * <p>只负责「哪些已解锁」与「解锁时怎么通知」，不负责判断条件——
 * 条件由调用方（{@link CourierProfile}）在关键事件后调用 {@link #unlock(Achievement)}。
 * 这样成就的触发点可以分散在各处，而状态与持久化只有一份。
 */
public final class AchievementSystem {

    private final Set<Achievement> unlocked = EnumSet.noneOf(Achievement.class);
    private final List<Achievement> pendingToasts = new ArrayList<>();
    private Consumer<Achievement> listener;

    public boolean isUnlocked(Achievement achievement) {
        return achievement != null && unlocked.contains(achievement);
    }

    /**
     * 解锁成就。已解锁的重复调用不会重复通知。
     *
     * @return 本次是否「新」解锁
     */
    public boolean unlock(Achievement achievement) {
        if (achievement == null || unlocked.contains(achievement)) {
            return false;
        }
        unlocked.add(achievement);
        pendingToasts.add(achievement);
        Consumer<Achievement> l = listener;
        if (l != null) {
            l.accept(achievement);
        }
        return true;
    }

    /** 批量尝试解锁，返回本次新解锁的数量。 */
    public int unlockAll(Achievement... achievements) {
        int count = 0;
        for (Achievement a : achievements) {
            if (unlock(a)) {
                count++;
            }
        }
        return count;
    }

    public int unlockedCount() {
        return unlocked.size();
    }

    public int totalCount() {
        return Achievement.values().length;
    }

    public double completionRatio() {
        return (double) unlocked.size() / totalCount();
    }

    public Set<Achievement> all() {
        return Set.copyOf(unlocked);
    }

    /** 取出并清空待播报的解锁列表（UI 每帧取一次）。 */
    public List<Achievement> drainToasts() {
        List<Achievement> copy = List.copyOf(pendingToasts);
        pendingToasts.clear();
        return copy;
    }

    public void setListener(Consumer<Achievement> listener) {
        this.listener = listener;
    }

    /** 成就列表文本，按解锁状态分组，供面板展示。 */
    public List<String> renderList() {
        List<String> lines = new ArrayList<>();
        for (Achievement a : Achievement.values()) {
            lines.add(a.displayText(isUnlocked(a)));
        }
        return lines;
    }

    // ────────── 存档 ──────────

    public Map<String, String> toSaveEntries() {
        Map<String, String> out = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        for (Achievement a : unlocked) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(a.name());
        }
        out.put("ach.list", sb.toString());
        return out;
    }

    public void fromSaveEntries(Map<String, String> entries) {
        unlocked.clear();
        pendingToasts.clear();
        if (entries == null) {
            return;
        }
        String raw = entries.getOrDefault("ach.list", "");
        for (String name : raw.split(",")) {
            if (name.isBlank()) {
                continue;
            }
            try {
                unlocked.add(Achievement.valueOf(name.trim()));
            } catch (IllegalArgumentException ignored) {
                // 旧存档里已删除的成就名，静默忽略
            }
        }
    }

    @Override
    public String toString() {
        return "AchievementSystem{" + unlocked.size() + "/" + totalCount() + "}";
    }
}
