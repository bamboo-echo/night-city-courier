package com.moji.NightCityCourier.core;

/**
 * 事件口吻改写器。
 *
 * <p>同一个事件，对不同的人应该有不同的开场。这不是随机换文案，
 * 而是<b>世界对你之前行为的回应</b>：
 * <ul>
 *   <li>你出卖过同行 → 帮派在巷口等你的不是一个人；</li>
 *   <li>你救过人 → 求救的人看见你会松一口气；</li>
 *   <li>你成了公司的人 → 城警会抬高警戒线；</li>
 *   <li>你在屋顶有名声 → 拾荒者会主动给你放哨。</li>
 * </ul>
 *
 * <p>实现上保持纯函数：给定 {@link Memory} 与事件键，返回一句前置叙述（可能为空）。
 * 不修改状态、不掷骰，因此可被测试直接断言。
 */
public final class EventVoice {

    private EventVoice() {
    }

    /**
     * 事件开场前的「世界记忆」一句话。
     *
     * @param eventKey 事件键，如 POLICE / RESCUE / CORPO / GANG / SCAVENGER / GHOST
     * @param memory   世界记忆，可为 null
     * @return 一句话；没有特殊记忆时返回空串
     */
    public static String opening(String eventKey, Memory memory) {
        if (eventKey == null || memory == null) {
            return "";
        }
        Memory.Archetype a = memory.archetype();
        return switch (eventKey) {
            case "POLICE" -> police(a, memory);
            case "GANG" -> gang(a, memory);
            case "RESCUE" -> rescue(a, memory);
            case "CORPO" -> corpo(a, memory);
            case "SCAVENGER" -> scavenger(a, memory);
            case "GHOST" -> ghost(a, memory);
            case "PICKPOCKET" -> pickpocket(a, memory);
            case "EXLOVER" -> exLover(a, memory);
            default -> "";
        };
    }

    private static String police(Memory.Archetype a, Memory m) {
        if (a == Memory.Archetype.COMPANY_MAN) {
            return "城警看了你一眼，又看了眼你胸前的工牌，把警戒线抬高了一寸。";
        }
        if (a == Memory.Archetype.BUTCHER) {
            return "两个城警看见你，其中一个下意识把手放到了枪套上。";
        }
        if (a == Memory.Archetype.SAINT) {
            return "城警拦下你，愣了一下：「……是你啊。」他犹豫了两秒，挥手让你过去。";
        }
        if (m.count(Memory.Fact.BRIBED_POLICE) >= 2) {
            return "车窗被敲了两下。你还没开口，对方先说：「老规矩？」";
        }
        return "";
    }

    private static String gang(Memory.Archetype a, Memory m) {
        if (m.has(Memory.Fact.BETRAYED_GANG)) {
            return "巷口的人比上次多。他们不是来收过路费的。";
        }
        if (m.count(Memory.Fact.BRIBED_GANG) >= 2) {
            return "领头的笑了：「老熟人。」但那笑没到眼睛。";
        }
        if (a == Memory.Archetype.BUTCHER) {
            return "他们看见你，互相看了一眼，然后让开了半步——不是怕，是不想麻烦。";
        }
        if (a == Memory.Archetype.ROOF_DWELLER) {
            return "有人压低声音提醒：「屋顶上那位，绕开这条街。」";
        }
        return "";
    }

    private static String rescue(Memory.Archetype a, Memory m) {
        if (a == Memory.Archetype.BUTCHER) {
            return "求救声从巷子里传出来。那人看见你的脸，声音忽然低了下去。";
        }
        if (a == Memory.Archetype.SAINT) {
            return "有人在喊救命。他看见你，像是看见了唯一能指望的东西。";
        }
        if (m.count(Memory.Fact.IGNORED_CRY) >= 2) {
            return "又是求救声。这次你没有立刻减速——上一次你也没有。";
        }
        return "";
    }

    private static String corpo(Memory.Archetype a, Memory m) {
        if (a == Memory.Archetype.COMPANY_MAN) {
            return "灰西装这次直接叫出了你的名字，没有递名片。";
        }
        if (a == Memory.Archetype.BUTCHER) {
            return "灰西装递名片的手很稳：「像你这样的人，我们一直有位置。」";
        }
        if (a == Memory.Archetype.SAINT) {
            return "灰西装开口前先笑了一下：「你的名声……很有意思。」";
        }
        return "";
    }

    private static String scavenger(Memory.Archetype a, Memory m) {
        if (a == Memory.Archetype.ROOF_DWELLER) {
            return "一个拾荒者从通风管里探出头，冲你比了个只有自己人才懂的手势。";
        }
        if (m.has(Memory.Fact.HELPED_SCAV)) {
            return "屋顶上有人朝你扔下来一瓶水，没说话，指了指前面封住的路。";
        }
        return "";
    }

    private static String ghost(Memory.Archetype a, Memory m) {
        if (a == Memory.Archetype.GHOST) {
            return "通讯器先响了，然后才是危险。你已经习惯这个顺序。";
        }
        if (m.count(Memory.Fact.USED_GHOST_HELP) >= 2) {
            return "那个频段又出现了，比上次更清晰一点。";
        }
        return "";
    }

    private static String pickpocket(Memory.Archetype a, Memory m) {
        if (a == Memory.Archetype.SAINT) {
            return "那只手伸到一半，忽然停住。孩子抬头看见你，转身就跑。";
        }
        if (a == Memory.Archetype.BUTCHER) {
            return "今天凑上来的是两个人。他们显然知道你是谁，但还是来了。";
        }
        return "";
    }

    private static String exLover(Memory.Archetype a, Memory m) {
        if (a == Memory.Archetype.BUTCHER) {
            return "她看见你，没有叫住你，只是把烟按灭，转身进了店。";
        }
        if (a == Memory.Archetype.SAINT) {
            return "她远远就认出了你：「听说你最近在这儿做好事？」";
        }
        return "";
    }

    /** 别人怎么称呼你。用于对话中插入代词，让 NPC 说出你的名声。 */
    public static String address(Memory memory) {
        if (memory == null || memory.totalFacts() == 0) {
            return "跑单的";
        }
        return switch (memory.archetype()) {
            case SAINT -> "那位圣人";
            case BUTCHER -> "那个屠夫";
            case COMPANY_MAN -> "公司的人";
            case ROOF_DWELLER -> "屋顶上的";
            case GHOST -> "那个幽灵";
            case PRAGMATIST -> "老江湖";
            default -> "跑单的";
        };
    }
}
