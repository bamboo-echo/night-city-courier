package com.moji.NightCityCourier.core;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支线剧情链。
 *
 * <p>单次事件是「一锤子买卖」：看完就忘。真正让人记住一座城的，是<b>连续</b>的东西
 * ——同一个人反复出现，每次多透露一点，最后收束成一个交代。
 *
 * <p>本系统维护三条线，每条 4 拍：
 * <ul>
 *   <li><b>幽灵</b>：一个只通过通讯器出现的声音，最后你见到它的主人；</li>
 *   <li><b>屋顶</b>：拾荒者网络从戒备到接纳，最后给你一把钥匙；</li>
 *   <li><b>旧友</b>：一个不断「顺路」出现的老朋友，最后一次他真的需要你。</li>
 * </ul>
 *
 * <p>推进方式不是「接任务」，而是<b>每完成若干单随机推进一拍</b>——
 * 你无法主动刷剧情，只能一直在城里跑，等着它找上你。
 */
public final class StorylineSystem {

    public enum Line {
        GHOST("幽灵"),
        ROOF("屋顶"),
        OLD_FRIEND("旧友");

        private final String displayName;

        Line(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    /** 支线的一拍。money/skillPoints 为即时奖励，faction/rep 为派系声望变化。 */
    public record Beat(String line, int index, String title, String text,
                       int money, int skillPoints, FactionSystem.Faction faction, int rep) {
    }

    private static final Map<Line, Beat[]> SCRIPT = new EnumMap<>(Line.class);

    static {
        SCRIPT.put(Line.GHOST, new Beat[]{
                new Beat("幽灵", 1, "不明信号",
                        "通讯器自己响了。没有来电显示，只有一段被压得很扁的声音：\n"
                                + "\"别走七号桥。今晚别走。\"\n"
                                + "你回头看了一眼，街上没有人。", 0, 0, null, 0),
                new Beat("幽灵", 2, "第二个提醒",
                        "同一个频段又出现了。这次它报出了一串坐标，正好是你下一单的收货点。\n"
                                + "\"那里有三个人在等货。别带钱进去。\"\n"
                                + "你照做了。出来时，你听见玻璃碎裂的声音。", 120, 0, null, 0),
                new Beat("幽灵", 3, "前任",
                        "信号终于有了来源：旧城区一栋废弃调度楼。\n"
                                + "楼里只有一台还在运行的终端，和一个坐在椅子上的人形——\n"
                                + "确切地说，是半个。他的下半身接着一颗备用电源。\n"
                                + "\"我以前也跑单，\"他说，\"跑到第十一年，跑不动了。\"", 0, 1, null, 0),
                new Beat("幽灵", 4, "交出频道",
                        "他把自己的频段写在一张纸上递给你。\n"
                                + "\"以后你听见这个声音，就是我还没死透。\"\n"
                                + "你走出调度楼，通讯器安静了。\n"
                                + "但你把它调到了那个频段，一直没关。", 300, 0, FactionSystem.Faction.SCAV, 12)
        });

        SCRIPT.put(Line.ROOF, new Beat[]{
                new Beat("屋顶", 1, "试探",
                        "一个拾荒者小孩跟着你走了两条街。\n"
                                + "最后他跑上来，塞给你一块电池：\"姐姐说，看你跑了三天没坑人。\"\n"
                                + "说完就钻进通风管跑了。", 0, 0, FactionSystem.Faction.SCAV, 8),
                new Beat("屋顶", 2, "借道",
                        "屋顶上有人冲你招手。他们放下一条绳梯。\n"
                                + "\"下面那条街封了。走上面，快二十分钟。\"\n"
                                + "你爬上去，发现整片屋顶都被他们接成了通路——用废铁和光缆。", 0, 0,
                        FactionSystem.Faction.SCAV, 12),
                new Beat("屋顶", 3, "代价",
                        "他们开口了：\"有条街的配电箱被公司锁了。整片屋顶会断电。\"\n"
                                + "\"你跑公司单的时候，能顺手带个东西进去吗？\"\n"
                                + "你答应了。你不知道那个东西是什么，但他们第一次请你帮忙。", 80, 0,
                        FactionSystem.Faction.SCAV, 15),
                new Beat("屋顶", 4, "钥匙",
                        "第二天，屋顶的路全亮了。\n"
                                + "小孩递给你一把用铜线拧成的钥匙：\"这是我们的。上哪儿都行。\"\n"
                                + "你把它挂在脖子上。它比任何义体都轻。", 200, 1, FactionSystem.Faction.SCAV, 30)
        });

        SCRIPT.put(Line.OLD_FRIEND, new Beat[]{
                new Beat("旧友", 1, "顺路",
                        "有人在摊位边上叫你的旧外号。\n"
                                + "是老K。以前一起跑过单，后来他消失了三年。\n"
                                + "\"最近怎么样？\"他笑着问，好像昨天才见过。", 0, 0, null, 0),
                new Beat("旧友", 2, "借钱",
                        "老K又出现了，这次在你的车旁边等。\n"
                                + "\"借我两百，下周还。\"\n"
                                + "他瘦了很多，义体接口那块皮肤是新的。", -200, 0, null, 0),
                new Beat("旧友", 3, "还钱",
                        "他准时出现了，把钱塞回来，多给了五十。\n"
                                + "\"我说了会还。\"他顿了顿，\"我以前没说过，但这次是真的。\"\n"
                                + "你注意到他一直在看街角那辆车。", 0, 0, null, 0),
                new Beat("旧友", 4, "最后一单",
                        "凌晨三点，他打来电话，背景全是雨声。\n"
                                + "\"帮我跑最后一单。送到城南，别问是什么。\"\n"
                                + "你答应了。货很轻，轻得不像活物。\n"
                                + "送到之后，你再也没接到过他的电话。", 500, 1, null, 0)
        });
    }

    private final EnumMap<Line, Integer> progress = new EnumMap<>(Line.class);

    public StorylineSystem() {
        for (Line l : Line.values()) {
            progress.put(l, 0);
        }
    }

    public int progress(Line line) {
        return progress.getOrDefault(line, 0);
    }

    public boolean isComplete(Line line) {
        return progress(line) >= SCRIPT.get(line).length;
    }

    public boolean allComplete() {
        for (Line l : Line.values()) {
            if (!isComplete(l)) {
                return false;
            }
        }
        return true;
    }

    /** 已完成的总拍数，用于 UI 显示进度。 */
    public int totalBeats() {
        int total = 0;
        for (Line l : Line.values()) {
            total += progress(l);
        }
        return total;
    }

    public int totalPossible() {
        int total = 0;
        for (Line l : Line.values()) {
            total += SCRIPT.get(l).length;
        }
        return total;
    }

    /** 挑一条未完成的线；全完成返回 null。 */
    public Line pickIncomplete(Rng rng) {
        java.util.List<Line> candidates = new java.util.ArrayList<>();
        for (Line l : Line.values()) {
            if (!isComplete(l)) {
                candidates.add(l);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        return rng == null ? candidates.get(0) : rng.pick(candidates);
    }

    /**
     * 由一条行为事实决定该推进哪条支线。
     *
     * <p>这是 v2.2 「行为驱动」的关键：不再靠 28% 掷骰，而是你做了什么，
     * 就走进哪条线。放走活体货物的人会遇到屋顶的孩子，听劝的人会被幽灵找上，
     * 给过钱的人会被旧友记住。
     *
     * @return 对应的支线；该行为不关联支线时返回 null
     */
    public static Line lineFor(Memory.Fact fact) {
        if (fact == null) {
            return null;
        }
        return switch (fact) {
            case FREED_PET, HELPED_SCAV, JOINED_WEDDING -> Line.ROOF;
            case LISTENED_GHOST, DEFUSED_BOMB -> Line.GHOST;
            case GAVE_MONEY, SAVED_LIFE, SPARED_RIVAL -> Line.OLD_FRIEND;
            default -> null;
        };
    }

    /** 按行为推进对应支线；该线已完成或行为无关联时返回 null。 */
    public Beat advanceBy(Memory.Fact fact) {
        Line line = lineFor(fact);
        if (line == null) {
            return null;
        }
        return advance(line);
    }

    /** 推进一条线并返回新的一拍；已完成返回 null。 */
    public Beat advance(Line line) {
        if (line == null || isComplete(line)) {
            return null;
        }
        int next = progress(line);
        Beat[] beats = SCRIPT.get(line);
        progress.put(line, next + 1);
        return beats[next];
    }

    /** 直接推进下一拍（随机挑一条未完成的线）。 */
    public Beat advanceAny(Rng rng) {
        Line line = pickIncomplete(rng);
        return line == null ? null : advance(line);
    }

    public Map<String, String> toSaveEntries() {
        Map<String, String> out = new LinkedHashMap<>();
        for (Line l : Line.values()) {
            out.put("story." + l.name(), String.valueOf(progress(l)));
        }
        return out;
    }

    public void fromSaveEntries(Map<String, String> entries) {
        for (Line l : Line.values()) {
            progress.put(l, 0);
        }
        if (entries == null) {
            return;
        }
        for (Line l : Line.values()) {
            try {
                int v = Integer.parseInt(entries.getOrDefault("story." + l.name(), "0").trim());
                progress.put(l, Math.max(0, Math.min(v, SCRIPT.get(l).length)));
            } catch (Exception ignored) {
                progress.put(l, 0);
            }
        }
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        for (Line l : Line.values()) {
            if (sb.length() > 0) {
                sb.append("   ");
            }
            sb.append(l.displayName()).append(' ').append(progress(l)).append('/').append(SCRIPT.get(l).length);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Storyline{" + summary() + "}";
    }
}
