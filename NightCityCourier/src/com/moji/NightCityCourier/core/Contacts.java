package com.moji.NightCityCourier.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 具名人物关系网。
 *
 * <p>上一版的名声是「标签」：你被整座城叫作冷血动物或圣人，但没有人真正认识你。
 * 这一层补上<b>具体的人</b>：老K、小满、格雷、薇、Doc——他们各自记得你做过什么，
 * 对你有自己的态度，并且会在日常事件里再次出现。
 *
 * <p>和 {@link Memory} 的分工：
 * <ul>
 *   <li>{@link Memory} 记的是「世界怎么看」——一个全局画像；</li>
 *   <li>{@link Contacts} 记的是「具体的人怎么看」——五份互相独立的关系账本。</li>
 * </ul>
 *
 * <p>关键在于两者<b>不一致</b>才是有戏的：你可以在整座城面前是圣人，
 * 却欠着老K 一条命没还；你可以在名声上是冷血动物，却让小满只认你一个人。
 * 关系阶段不同步，正是人物弧线的来源。
 *
 * <p>所有数值都是离散的手感值：每条事实对每个人的影响写死在
 * {@link #impact(Who, Memory.Fact)} 里，便于逐条调平衡，也便于测试断言。
 */
public final class Contacts {

    /** 五个具名人物。 */
    public enum Who {
        OLD_K("老K", "以前一起跑单的人，三年前消失，最近又出现了。"),
        XIAOMAN("小满", "拾荒者网络里的孩子，负责在通风管里传递消息。"),
        GRAY("格雷", "公司的猎头。穿灰西装，说话时永远带着礼貌的笑。"),
        WEI("薇", "以前的相识。你们在雨里认识，也各自消失在雨里。"),
        DOC("Doc", "开义体诊所的老太太。她的针管比城警的枪准。");

        private final String displayName;
        private final String intro;

        Who(String displayName, String intro) {
            this.displayName = displayName;
            this.intro = intro;
        }

        public String displayName() {
            return displayName;
        }

        public String intro() {
            return intro;
        }
    }

    /** 关系阶段。由态度值派生，不单独存储。 */
    public enum Stage {
        ENEMY("敌对", -100, -45),
        COLD("冷淡", -45, -12),
        STRANGER("陌生", -12, 12),
        ACQUAINTED("熟识", 12, 40),
        TRUSTED("信任", 40, 72),
        BONDED("生死之交", 72, 101);

        private final String displayName;
        private final int min;
        private final int max;

        Stage(String displayName, int min, int max) {
            this.displayName = displayName;
            this.min = min;
            this.max = max;
        }

        public String displayName() {
            return displayName;
        }

        /** 该阶段的下界（含）。 */
        public int min() {
            return min;
        }

        /** 该阶段的上界（不含）。 */
        public int max() {
            return max;
        }

        public static Stage of(int relation) {
            for (Stage s : values()) {
                if (relation >= s.min && relation < s.max) {
                    return s;
                }
            }
            return relation < ENEMY.min ? ENEMY : BONDED;
        }
    }

    /** 单个 NPC 的关系账本。 */
    public static final class Bond {
        private int relation;
        private int meetings;
        private boolean met;
        private final EnumMap<Memory.Fact, Integer> shared = new EnumMap<>(Memory.Fact.class);

        public int relation() {
            return relation;
        }

        public int meetings() {
            return meetings;
        }

        public boolean hasMet() {
            return met;
        }

        public Stage stage() {
            return Stage.of(relation);
        }

        public int count(Memory.Fact fact) {
            return shared.getOrDefault(fact, 0);
        }

        public Map<Memory.Fact, Integer> sharedMemories() {
            return new LinkedHashMap<>(shared);
        }
    }

    public static final int MIN = -100;
    public static final int MAX = 100;

    private final EnumMap<Who, Bond> bonds = new EnumMap<>(Who.class);

    public Contacts() {
        for (Who w : Who.values()) {
            bonds.put(w, new Bond());
        }
    }

    public Bond bond(Who who) {
        return bonds.get(who);
    }

    public int relation(Who who) {
        Bond b = bonds.get(who);
        return b == null ? 0 : b.relation();
    }

    public Stage stage(Who who) {
        return Stage.of(relation(who));
    }

    public boolean hasMet(Who who) {
        Bond b = bonds.get(who);
        return b != null && b.met;
    }

    /** 调整态度并裁剪到 [-100, 100]，返回实际变化量。 */
    public int shift(Who who, int delta) {
        if (who == null || delta == 0) {
            return 0;
        }
        Bond b = bonds.get(who);
        int before = b.relation();
        int after = Math.max(MIN, Math.min(MAX, before + delta));
        b.relation = after;
        return after - before;
    }

    /** 记一次见面。 */
    public void meet(Who who) {
        Bond b = bonds.get(who);
        b.met = true;
        b.meetings++;
    }

    /**
     * 事实 → 好感度影响表。
     *
     * <p>这是整个系统的核心数据：同一件事对不同的人意义完全不同。
     * 卖同行会让格雷欣赏你（他有业绩），却会让老K 和小满恨你（他们认识那些人）。
     */
    public static int impact(Who who, Memory.Fact fact) {
        if (who == null || fact == null) {
            return 0;
        }
        return switch (who) {
            case OLD_K -> switch (fact) {
                case SAVED_LIFE -> 12;
                case REFUSED_CORPO -> 20;
                case DEFUSED_BOMB -> 15;
                case SPARED_RIVAL -> 10;
                case GAVE_MONEY -> 8;
                case JOINED_WEDDING -> 6;
                case LISTENED_GHOST -> 6;
                case SOLD_OUT_COLLEAGUE -> -35;
                case BETRAYED_GANG -> -30;
                case SOLD_ORGANS -> -15;
                case IGNORED_CRY -> -12;
                case BRIBED_POLICE -> -8;
                case DUMPED_BOMB -> -8;
                default -> 0;
            };
            case XIAOMAN -> switch (fact) {
                case FREED_PET -> 22;
                case HELPED_SCAV -> 25;
                case DEFUSED_BOMB -> 12;
                case KEPT_PET -> 14;
                case GAVE_MONEY -> 10;
                case REFUSED_CORPO -> 15;
                case SOLD_OUT_COLLEAGUE -> -25;
                case SOLD_ORGANS -> -12;
                case IGNORED_CRY -> -10;
                case DUMPED_BOMB -> -10;
                case BETRAYED_GANG -> -20;
                default -> 0;
            };
            case GRAY -> switch (fact) {
                case SOLD_OUT_COLLEAGUE -> 28;
                case COPIED_DATA -> 12;
                case BRIBED_POLICE -> 6;
                case SOLD_ORGANS -> 8;
                case REFUSED_CORPO -> -32;
                case SAVED_LIFE -> -10;
                case FREED_PET -> -8;
                case HELPED_SCAV -> -12;
                case BETRAYED_GANG -> -20;
                default -> 0;
            };
            case WEI -> switch (fact) {
                case GAVE_MONEY -> 16;
                case SAVED_LIFE -> 10;
                case JOINED_WEDDING -> 14;
                case FREED_PET -> 8;
                case REFUSED_CORPO -> 12;
                case IGNORED_CRY -> -16;
                case PAWNED_GIFT -> -22;
                case SOLD_ORGANS -> -14;
                default -> 0;
            };
            case DOC -> switch (fact) {
                case TUNED_BODY -> 10;
                case DEFUSED_BOMB -> 12;
                case SAVED_LIFE -> 8;
                case FREED_PET -> 8;
                case GAVE_MONEY -> 6;
                case HELPED_SCAV -> 5;
                case SOLD_ORGANS -> -14;
                case IGNORED_CRY -> -10;
                case COPIED_DATA -> -6;
                case PAWNED_GIFT -> -6;
                default -> 0;
            };
        };
    }

    /**
     * 把一条世界事实分发给所有相关人物。
     *
     * @return 态度确实变化了的人
     */
    public List<Who> distribute(Memory.Fact fact) {
        List<Who> touched = new ArrayList<>();
        if (fact == null) {
            return touched;
        }
        for (Who w : Who.values()) {
            int delta = impact(w, fact);
            if (delta == 0) {
                continue;
            }
            Bond b = bonds.get(w);
            int before = b.relation();
            shift(w, delta);
            if (b.relation != before) {
                touched.add(w);
            }
            b.shared.merge(fact, 1, Integer::sum);
            b.met = true;
        }
        return touched;
    }

    // ────────── 派生查询 ──────────

    /** 好感最高的人；无人认识时返回 null。 */
    public Who closest() {
        Who best = null;
        int bestValue = 0;
        for (Who w : Who.values()) {
            if (!hasMet(w)) {
                continue;
            }
            if (relation(w) > bestValue) {
                bestValue = relation(w);
                best = w;
            }
        }
        return best;
    }

    /** 关系最差的人；没有敌对关系时返回 null。 */
    public Who enemy() {
        Who worst = null;
        int worstValue = -12;
        for (Who w : Who.values()) {
            if (relation(w) < worstValue) {
                worstValue = relation(w);
                worst = w;
            }
        }
        return worst;
    }

    /** 生死之交。 */
    public List<Who> bonded() {
        List<Who> list = new ArrayList<>();
        for (Who w : Who.values()) {
            if (relation(w) >= Stage.BONDED.min) {
                list.add(w);
            }
        }
        return list;
    }

    /** 敌人。 */
    public List<Who> enemies() {
        List<Who> list = new ArrayList<>();
        for (Who w : Who.values()) {
            if (relation(w) <= Stage.ENEMY.max) {
                list.add(w);
            }
        }
        return list;
    }

    /** 已经认识的人数。 */
    public int metCount() {
        int n = 0;
        for (Who w : Who.values()) {
            if (hasMet(w)) {
                n++;
            }
        }
        return n;
    }

    /**
     * 某人物的专属遭遇事件权重。
     *
     * <p>关系好 → 更容易遇到他帮你；关系差 → 更容易遇到他害你。
     * 两极都会增加遭遇频率，中间地带的人反而很少出现——跟你没关系的人不会总出现在你眼前。
     */
    public int encounterWeight(Who who) {
        int r = relation(who);
        if (!hasMet(who)) {
            // 还没见过面：给一个小的「初次相遇」权重，否则这个人永远不会出现在你的路线上
            return 3;
        }
        if (r >= Stage.TRUSTED.min) {
            return 10;
        }
        if (r >= Stage.ACQUAINTED.min) {
            return 5;
        }
        if (r <= Stage.ENEMY.max) {
            return 9;
        }
        if (r <= Stage.COLD.max) {
            return 4;
        }
        return 1;
    }

    /** 结局用的关系回顾。 */
    public List<String> recap() {
        List<String> lines = new ArrayList<>();
        for (Who w : Who.values()) {
            if (!hasMet(w)) {
                continue;
            }
            Bond b = bonds.get(w);
            lines.add("· " + w.displayName() + "：" + b.stage().displayName()
                    + "（" + b.relation() + "），见过 " + b.meetings() + " 次");
        }
        return lines;
    }

    /** 一行摘要，用于档案面板。 */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (Who w : Who.values()) {
            if (!hasMet(w)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("   ");
            }
            sb.append(w.displayName()).append(' ').append(stage(w).displayName());
        }
        return sb.length() == 0 ? "（还没有人认识你）" : sb.toString();
    }

    // ────────── 存档 ──────────

    public Map<String, String> toSaveEntries() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Who w : Who.values()) {
            Bond b = bonds.get(w);
            out.put("npc." + w.name() + ".rel", String.valueOf(b.relation));
            out.put("npc." + w.name() + ".meet", String.valueOf(b.meetings));
            out.put("npc." + w.name() + ".seen", String.valueOf(b.met));
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Memory.Fact, Integer> e : b.shared.entrySet()) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                sb.append(e.getKey().name()).append('=').append(e.getValue());
            }
            out.put("npc." + w.name() + ".shared", sb.toString());
        }
        return out;
    }

    public void fromSaveEntries(Map<String, String> entries) {
        for (Who w : Who.values()) {
            bonds.put(w, new Bond());
        }
        if (entries == null) {
            return;
        }
        for (Who w : Who.values()) {
            Bond b = bonds.get(w);
            b.relation = clamp(parseInt(entries.get("npc." + w.name() + ".rel"), 0));
            b.meetings = Math.max(0, parseInt(entries.get("npc." + w.name() + ".meet"), 0));
            b.met = Boolean.parseBoolean(entries.getOrDefault("npc." + w.name() + ".seen", "false"));
            String shared = entries.getOrDefault("npc." + w.name() + ".shared", "");
            for (String pair : shared.split(",")) {
                if (pair.isBlank()) {
                    continue;
                }
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                try {
                    Memory.Fact f = Memory.Fact.valueOf(pair.substring(0, eq).trim());
                    b.shared.put(f, Math.max(0, parseInt(pair.substring(eq + 1), 0)));
                    b.met = true;
                } catch (IllegalArgumentException ignored) {
                    // 旧存档里已删除的事实名，忽略
                }
            }
        }
    }

    private static int clamp(int v) {
        return Math.max(MIN, Math.min(MAX, v));
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    @Override
    public String toString() {
        return "Contacts{" + summary() + "}";
    }
}
