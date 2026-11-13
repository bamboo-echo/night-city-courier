package com.moji.NightCityCourier.core;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 世界记忆：把「你做过什么」变成可查询的事实台账。
 *
 * <p>这是 v2.2 的核心。之前的系统是彼此孤立的：支线靠掷骰推进，派系只影响倍率，
 * 变体与事件互不相干——玩家做的每个选择都被消费掉，然后消失。城市不会记得。
 *
 * <p>本类做三件事：
 * <ol>
 *   <li><b>记账</b>：每个有道德或立场意义的选择，写一条 {@link Fact}，并保留发生顺序；</li>
 *   <li><b>画像</b>：由事实聚合出 {@link Archetype}——街面上怎么看你。这不是数值加成，
 *       而是<b>内容开关</b>：敌对的帮派会派打手，冷血的人会被寻仇，圣人会不断被求助；</li>
 *   <li><b>可回溯</b>：结局时能逐条念出你做过的事，以及它们如何改变了后续。</li>
 * </ol>
 *
 * <p>设计约束：事实只增不减。不提供「撤销」接口——夜之城里没有后悔药，
 * 这也保证了同一份存档在任何时候重新读入都能得到相同的画像。
 */
public final class Memory {

    /** 事实类型。{@code weight} 用于画像强度，{@code polarity} 决定善恶倾向。 */
    public enum Fact {
        // ── 善意 ──
        SAVED_LIFE("救了一个人", 3, Polarity.KIND),
        FREED_PET("放走了一只活体货物", 2, Polarity.KIND),
        KEPT_PET("把活体货物带回了家", 2, Polarity.KIND),
        GAVE_MONEY("给过陌生人钱", 1, Polarity.KIND),
        HELPED_SCAV("帮拾荒者做过事", 2, Polarity.KIND),
        REFUSED_CORPO("拒绝了公司的收买", 3, Polarity.KIND),
        LISTENED_GHOST("听了那个不明信号的话", 2, Polarity.KIND),
        DEFUSED_BOMB("拆掉了一枚炸弹", 3, Polarity.KIND),
        JOINED_WEDDING("去陌生人的婚礼坐了坐", 1, Polarity.KIND),
        SPARED_RIVAL("放过了竞争对手", 2, Polarity.KIND),

        // ── 冷血 / 交易 ──
        SOLD_ORGANS("把器官卖到了黑市", 4, Polarity.CRUEL),
        SOLD_OUT_COLLEAGUE("把同行卖给了公司", 5, Polarity.CRUEL),
        PAWNED_GIFT("把婚礼礼服当了", 2, Polarity.CRUEL),
        DUMPED_BOMB("把定时件扔进了排水渠", 3, Polarity.CRUEL),
        KILLED_RIVAL("让竞争对手彻底消失", 4, Polarity.CRUEL),
        IGNORED_CRY("没有理会求救", 2, Polarity.CRUEL),
        COPIED_DATA("偷看过客户的数据", 2, Polarity.CRUEL),
        BETRAYED_GANG("出卖了帮派", 3, Polarity.CRUEL),

        // ── 中性行为（只记录，不参与画像） ──
        GAMBLED("坐上了赌局", 0, Polarity.NEUTRAL),
        TUNED_BODY("做过神经调校", 0, Polarity.NEUTRAL),
        BRIBED_POLICE("给城警塞过钱", 1, Polarity.NEUTRAL),
        BRIBED_GANG("给帮派塞过钱", 1, Polarity.NEUTRAL),
        USED_GHOST_HELP("用过不明通讯的协助", 0, Polarity.NEUTRAL),
        RAN_CONTRABAND("跑过黑市件", 0, Polarity.NEUTRAL);

        public enum Polarity {
            KIND, CRUEL, NEUTRAL
        }

        private final String label;
        private final int weight;
        private final Polarity polarity;

        Fact(String label, int weight, Polarity polarity) {
            this.label = label;
            this.weight = weight;
            this.polarity = polarity;
        }

        public String label() {
            return label;
        }

        public int weight() {
            return weight;
        }

        public Polarity polarity() {
            return polarity;
        }
    }

    /**
     * 名声画像。它不只是称号，还决定三类内容行为：
     * 事件池偏向、NPC 开场白、结局走向。
     */
    public enum Archetype {
        NOBODY("无名之辈", "街上还没人认得你。", "你还没有名字。"),
        PRAGMATIST("现实主义者", "你活下来了，这就够了。", "他们说你靠得住，也说你谁都靠不住。"),
        SAINT("夜之城的圣人", "有人开始拿你当例子讲给孩子听。", "他们提起你的时候会压低声音，像在说一个传说。"),
        BUTCHER("冷血动物", "你的名字被用来吓唬新人。", "他们不敢在你面前说你的名字。"),
        COMPANY_MAN("公司的人", "灰西装们开始对你点头。", "你有了工牌，也有了别人看你的眼神。"),
        ROOF_DWELLER("屋顶上的", "拾荒者给你留了一条只有你能走的路。", "屋顶的孩子们会为你放哨。"),
        GHOST("幽灵", "你的行踪只有通讯器里的声音知道。", "没人见过你，但很多人都收到过你的提醒。");

        private final String title;
        private final String streetTalk;
        private final String endingLine;

        Archetype(String title, String streetTalk, String endingLine) {
            this.title = title;
            this.streetTalk = streetTalk;
            this.endingLine = endingLine;
        }

        public String title() {
            return title;
        }

        /** 街面上的说法，用于事件开场与档案面板。 */
        public String streetTalk() {
            return streetTalk;
        }

        /** 结局里的一句判词。 */
        public String endingLine() {
            return endingLine;
        }
    }

    private final EnumMap<Fact, Integer> counts = new EnumMap<>(Fact.class);
    private final List<Fact> timeline = new ArrayList<>();

    // ────────── 记账 ──────────

    public void record(Fact fact) {
        if (fact == null) {
            return;
        }
        counts.merge(fact, 1, Integer::sum);
        timeline.add(fact);
        if (timeline.size() > 400) {
            timeline.remove(0);
        }
    }

    public int count(Fact fact) {
        return counts.getOrDefault(fact, 0);
    }

    public boolean has(Fact fact) {
        return count(fact) > 0;
    }

    public int totalKind() {
        int sum = 0;
        for (Map.Entry<Fact, Integer> e : counts.entrySet()) {
            if (e.getKey().polarity() == Fact.Polarity.KIND) {
                sum += e.getValue();
            }
        }
        return sum;
    }

    public int totalCruel() {
        int sum = 0;
        for (Map.Entry<Fact, Integer> e : counts.entrySet()) {
            if (e.getKey().polarity() == Fact.Polarity.CRUEL) {
                sum += e.getValue();
            }
        }
        return sum;
    }

    /** 善意行为的权重和（次数 × 权重）。判定画像用它，避免「给十次钱」压过「救一个人」。 */
    public int weightedKind() {
        int sum = 0;
        for (Map.Entry<Fact, Integer> e : counts.entrySet()) {
            if (e.getKey().polarity() == Fact.Polarity.KIND) {
                sum += e.getKey().weight() * e.getValue();
            }
        }
        return sum;
    }

    /** 冷血行为的权重和。 */
    public int weightedCruel() {
        int sum = 0;
        for (Map.Entry<Fact, Integer> e : counts.entrySet()) {
            if (e.getKey().polarity() == Fact.Polarity.CRUEL) {
                sum += e.getKey().weight() * e.getValue();
            }
        }
        return sum;
    }

    /** 善恶权重差，正数偏善。用于画像判定与结局倾向。 */
    public int moralScore() {
        int score = 0;
        for (Map.Entry<Fact, Integer> e : counts.entrySet()) {
            int w = e.getKey().weight();
            int n = e.getValue();
            switch (e.getKey().polarity()) {
                case KIND -> score += w * n;
                case CRUEL -> score -= w * n;
                default -> {
                }
            }
        }
        return score;
    }

    public List<Fact> timeline() {
        return List.copyOf(timeline);
    }

    public int totalFacts() {
        return timeline.size();
    }

    /**
     * 当前画像。判定顺序即优先级：先看极端，再看特殊身份，最后落到中间态。
     *
     * <p>特殊身份（公司的人 / 屋顶上的 / 幽灵）优先于纯善恶，因为它们是
     * 「做过某类事」而不是「比较好或比较坏」，更能定义一个人的活法。
     */
    public Archetype archetype() {
        if (totalFacts() == 0) {
            return Archetype.NOBODY;
        }
        if (has(Fact.SOLD_OUT_COLLEAGUE) && count(Fact.BRIBED_POLICE) >= 2) {
            return Archetype.COMPANY_MAN;
        }
        if (has(Fact.SOLD_OUT_COLLEAGUE) && totalCruel() >= 4) {
            return Archetype.COMPANY_MAN;
        }
        if (count(Fact.HELPED_SCAV) >= 2 || (has(Fact.FREED_PET) && has(Fact.JOINED_WEDDING))) {
            return Archetype.ROOF_DWELLER;
        }
        if (count(Fact.LISTENED_GHOST) >= 1 && has(Fact.DEFUSED_BOMB)) {
            return Archetype.GHOST;
        }
        // 用「权重和」而非「次数」判定：救一个人（3）比给一次钱（1）更重。
        int kind = weightedKind();
        int cruel = weightedCruel();
        if (cruel >= 6 && cruel >= kind * 2) {
            return Archetype.BUTCHER;
        }
        if (kind >= 6 && kind >= cruel * 2) {
            return Archetype.SAINT;
        }
        return Archetype.PRAGMATIST;
    }

    public String epithet() {
        return archetype().title();
    }

    /**
     * 事件池偏移：返回某个事件的额外权重。这是「名声改变世界」的第一层——
     * 不是改数值，而是改你遇到什么。
     */
    public int weightShift(String eventKey) {
        Archetype a = archetype();
        int shift = 0;
        switch (eventKey) {
            case "CORPO" -> shift += (a == Archetype.BUTCHER ? 8 : 0) + (a == Archetype.COMPANY_MAN ? -6 : 0);
            case "RESCUE" -> shift += (a == Archetype.SAINT ? 8 : 0) + (a == Archetype.BUTCHER ? -6 : 0);
            case "PICKPOCKET" -> shift += (a == Archetype.SAINT ? -4 : 0) + (a == Archetype.BUTCHER ? 3 : 0);
            case "SCAVENGER" -> shift += (a == Archetype.ROOF_DWELLER ? 10 : 0);
            case "GHOST" -> shift += (a == Archetype.GHOST ? 8 : 0);
            case "POLICE" -> shift += (a == Archetype.COMPANY_MAN ? -6 : 0)
                    + (a == Archetype.BUTCHER ? 5 : 0) + (a == Archetype.SAINT ? -2 : 0);
            case "GANG" -> shift += (has(Fact.BETRAYED_GANG) ? 10 : 0) + (count(Fact.BRIBED_GANG) >= 2 ? -4 : 0);
            default -> {
            }
        }
        return shift;
    }

    // ────────── 存档 ──────────

    public Map<String, String> toSaveEntries() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<Fact, Integer> e : counts.entrySet()) {
            out.put("mem." + e.getKey().name(), String.valueOf(e.getValue()));
        }
        StringBuilder order = new StringBuilder();
        for (Fact f : timeline) {
            if (order.length() > 0) {
                order.append(',');
            }
            order.append(f.name());
        }
        out.put("mem.order", order.toString());
        return out;
    }

    public void fromSaveEntries(Map<String, String> entries) {
        counts.clear();
        timeline.clear();
        if (entries == null) {
            return;
        }
        for (Fact f : Fact.values()) {
            int n = parseInt(entries.get("mem." + f.name()), 0);
            if (n > 0) {
                counts.put(f, n);
            }
        }
        String order = entries.getOrDefault("mem.order", "");
        for (String name : order.split(",")) {
            if (name.isBlank()) {
                continue;
            }
            try {
                timeline.add(Fact.valueOf(name.trim()));
            } catch (IllegalArgumentException ignored) {
                // 旧存档里已删除的事实名，忽略
            }
        }
        // 若计数存在但顺序缺失（旧存档），用计数重建一个近似时间线
        if (timeline.isEmpty() && !counts.isEmpty()) {
            for (Map.Entry<Fact, Integer> e : counts.entrySet()) {
                for (int i = 0; i < e.getValue(); i++) {
                    timeline.add(e.getKey());
                }
            }
        }
    }

    /** 结局用的因果回顾：按发生顺序念出最多 n 条，并标注代价。 */
    public List<String> recap(int maxLines) {
        List<String> lines = new ArrayList<>();
        List<Fact> shown = timeline.size() <= maxLines
                ? timeline
                : timeline.subList(timeline.size() - maxLines, timeline.size());
        for (Fact f : shown) {
            String prefix = switch (f.polarity()) {
                case KIND -> "· 你";
                case CRUEL -> "· 你";
                default -> "· 你";
            };
            lines.add(prefix + f.label() + "。");
        }
        return lines;
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
        return "Memory{" + archetype().title() + ", facts=" + timeline.size()
                + ", moral=" + moralScore() + "}";
    }
}
