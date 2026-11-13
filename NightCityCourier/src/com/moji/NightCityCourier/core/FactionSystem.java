package com.moji.NightCityCourier.core;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 派系声望。
 *
 * <p>原版只有一根「总声望」，而且只用于结局判定。实际上夜之城里的三方
 * ——城警、帮派、拾荒者网络——是零和的：给警察当线人，帮派就会盯上你。
 *
 * <p>本系统把声望拆成三个独立轴（-100 ~ 100），并让它们产生<b>数值后果</b>：
 * <ul>
 *   <li>对某派系声望低 → 该派系出现的概率上升，贿赂价格上浮；</li>
 *   <li>声望高 → 出现概率下降、贿赂打折，还可能解锁特殊选项；</li>
 *   <li>三方不可能同时讨好，「立场」成为长期策略。</li>
 * </ul>
 */
public final class FactionSystem {

    public enum Faction {
        POLICE("城警", "他们说这座城市需要秩序，然后向你收秩序的钱。"),
        GANG("帮派", "规矩只有一条：别欠他们的。"),
        SCAV("拾荒者", "网络没有总部，只有一堆互相借电的屋顶。");

        private final String displayName;
        private final String motto;

        Faction(String displayName, String motto) {
            this.displayName = displayName;
            this.motto = motto;
        }

        public String displayName() {
            return displayName;
        }

        public String motto() {
            return motto;
        }
    }

    /** 五档关系。阈值集中在此，避免各处硬编码数字。 */
    public enum Tier {
        HOSTILE("敌对", -100, -40),
        WARY("警惕", -40, -10),
        NEUTRAL("中立", -10, 25),
        FRIENDLY("友好", 25, 60),
        ALLY("同盟", 60, 101);

        private final String displayName;
        private final int min;
        private final int max;

        Tier(String displayName, int min, int max) {
            this.displayName = displayName;
            this.min = min;
            this.max = max;
        }

        public String displayName() {
            return displayName;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }

        public static Tier of(int value) {
            for (Tier t : values()) {
                if (value >= t.min && value < t.max) {
                    return t;
                }
            }
            return value < HOSTILE.min ? HOSTILE : ALLY;
        }
    }

    public static final int MIN = -100;
    public static final int MAX = 100;

    private final EnumMap<Faction, Integer> rep = new EnumMap<>(Faction.class);

    public FactionSystem() {
        for (Faction f : Faction.values()) {
            rep.put(f, 0);
        }
    }

    public int rep(Faction faction) {
        return rep.getOrDefault(faction, 0);
    }

    /** 调整声望并裁剪到 [-100, 100]，返回实际变化量。 */
    public int shift(Faction faction, int delta) {
        if (faction == null || delta == 0) {
            return 0;
        }
        int before = rep(faction);
        int after = Math.max(MIN, Math.min(MAX, before + delta));
        rep.put(faction, after);
        return after - before;
    }

    public Tier tier(Faction faction) {
        return Tier.of(rep(faction));
    }

    /** 贿赂价格倍率：敌对 1.5 倍、盟友 0.7 倍，中间线性。 */
    public double bribeMultiplier(Faction faction) {
        int value = rep(faction);
        double factor = 1.0 - value / 200.0;
        return Math.max(0.7, Math.min(1.5, factor));
    }

    /** 该派系事件的密度倍率：越敌对越常见。同盟时仍保留少量遭遇。 */
    public double eventMultiplier(Faction faction) {
        int value = rep(faction);
        double factor = 1.0 - value / 250.0;
        return Math.max(0.55, Math.min(1.45, factor));
    }

    /** 声望最高的派系；全为 0 时返回 null。 */
    public Faction dominant() {
        Faction best = null;
        int bestValue = 0;
        for (Faction f : Faction.values()) {
            if (rep(f) > bestValue) {
                bestValue = rep(f);
                best = f;
            }
        }
        return best;
    }

    /** 声望最低的派系；全为 0 时返回 null。 */
    public Faction mostHated() {
        Faction worst = null;
        int worstValue = 0;
        for (Faction f : Faction.values()) {
            if (rep(f) < worstValue) {
                worstValue = rep(f);
                worst = f;
            }
        }
        return worst;
    }

    public Map<Faction, Integer> view() {
        return new LinkedHashMap<>(rep);
    }

    /** 一行摘要，用于 HUD 与结局。 */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (Faction f : Faction.values()) {
            if (sb.length() > 0) {
                sb.append("  ");
            }
            sb.append(f.displayName()).append(' ').append(tier(f).displayName());
        }
        return sb.toString();
    }

    // ────────── 存档 ──────────

    public Map<String, String> toSaveEntries() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Faction f : Faction.values()) {
            out.put("rep." + f.name(), String.valueOf(rep(f)));
        }
        return out;
    }

    public void fromSaveEntries(Map<String, String> entries) {
        for (Faction f : Faction.values()) {
            rep.put(f, 0);
        }
        if (entries == null) {
            return;
        }
        for (Faction f : Faction.values()) {
            String raw = entries.get("rep." + f.name());
            if (raw == null) {
                continue;
            }
            try {
                rep.put(f, Math.max(MIN, Math.min(MAX, Integer.parseInt(raw.trim()))));
            } catch (Exception ignored) {
                // 坏值按 0 处理
            }
        }
    }

    @Override
    public String toString() {
        return "FactionSystem" + rep;
    }
}
