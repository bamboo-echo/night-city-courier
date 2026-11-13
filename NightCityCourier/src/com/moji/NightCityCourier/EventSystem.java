package com.moji.NightCityCourier;

import com.moji.NightCityCourier.core.Contacts;
import com.moji.NightCityCourier.core.EventVoice;
import com.moji.NightCityCourier.core.Memory;

/**
 * 随机事件系统，处理送货途中所有随机遭遇事件。
 * 包含9种事件类型：城警/帮派威胁、义体故障、竞争对手、路人求救、
 * 神秘通讯、赌局、扫描、拾荒者、黑市奖励、赌徒复仇。
 * <p>
 * 每个事件通过 {@link #readChoiceWithEcho} 展示选项并获取玩家输入。
 */
public class EventSystem {

    private final GameWindow gameWindow;

    /**
     * 世界记忆。所有道德/立场选择都会写进这里，并反过来改写后续事件的
     * 开场白与事件池权重。默认给一个空实例，保证不注入时也能独立运行。
     */
    private Memory memory = new Memory();

    public EventSystem(GameWindow gameWindow) {
        this.gameWindow = gameWindow;
    }

    /** 注入共享的世界记忆（由 GameController 在初始化时调用）。 */
    public void setMemory(Memory memory) {
        if (memory != null) {
            this.memory = memory;
        }
    }

    /** 打印一句「世界记得你」的前置叙述；没有特殊记忆时什么也不打印。 */
    private void printVoice(String eventKey) {
        String voice = EventVoice.opening(eventKey, memory);
        if (!voice.isEmpty()) {
            System.out.println("· " + voice);
        }
    }

    /** 记录一条事实。集中在此，便于单点审计。 */
    private void remember(Memory.Fact fact) {
        memory.record(fact);
    }

    /**
     * 威胁事件：城警或帮派拦截。
     * 玩家可选择逃逸（根据速度/避警/避帮属性计算成功率）或贿赂。
     *
     * @param player     玩家对象
     * @param threatType "POLICE" 或 "GANG"
     * @return true 表示成功通过，false 表示任务失败
     */
    public boolean threatEvent(Player player, String threatType) {
        boolean isPolice = "POLICE".equals(threatType);
        printVoice(isPolice ? "POLICE" : "GANG");
        if (isPolice) {
            System.out.println("\n\n\n");
            System.out.println("🚨 ─── 遭遇城警 ─── 🚨");
            System.out.println("前方警灯闪烁。城警的巡逻车堵在巷口，蓝色光束扫过你的挡风玻璃。");
        } else {
            System.out.println("\n\n\n");
            System.out.println("💀 ─── 遭遇帮派 ─── 💀");
            System.out.println("巷子里走出几个人。义体改装的臂膀在霓虹下泛着冷光。\"留下货物，或者留下点什么。\"");
        }
        int bribeCost;
        if (isPolice) {
            bribeCost = GameConfig.POLICE_BRIBE_BASE + player.getLevel() * GameConfig.POLICE_BRIBE_PER_LEVEL;
            if (player.getWantedLevel() >= 3) {
                bribeCost = (int)(bribeCost * 1.5);
            }
        } else {
            bribeCost = GameConfig.GANG_BRIBE_BASE + player.getLevel() * GameConfig.GANG_BRIBE_PER_LEVEL;
        }
        int choice = readChoiceWithEcho(
                "遭遇威胁，选择行动：",
                "拧满油门钻入小巷。赌一把。",
                String.format("掏出钞票。这次用钱摆平。（%d€）", bribeCost)
        );
        if (choice == 1) {
            System.out.println(">> 你拧满油门钻入小巷。");
            double chance;
            if (isPolice) {
                chance = GameConfig.BASE_ESCAPE_CHANCE
                        + player.getSpeed() * GameConfig.ESCAPE_PER_SPEED
                        + player.getAvoidPolice() * GameConfig.ESCAPE_PER_AVOID_POLICE;
                if (player.getWantedLevel() >= 3) {
                    chance -= 0.1;
                }
            } else {
                chance = GameConfig.BASE_FIGHT_CHANCE
                        + player.getAvoidGang() * GameConfig.FIGHT_PER_AVOID_GANG;
            }
            if (isPolice) {
                if (tryOptionA(chance,
                        "警灯在身后远去。你从另一条街钻出来，心跳还没平。",
                        "警车封住了去路，你被拦下。任务失败。")) {
                    return true;
                }
                return false;
            } else {
                if (tryOptionA(chance,
                        "你凭一股狠劲冲了过去！他们闪开了。轮胎擦出一串火花。",
                        "对方人多势众，你被打成重伤，货物也被抢走了！任务失败。")) {
                    return true;
                }
                return false;
            }
        } else {
            System.out.println(">> 你掏出钞票，递了过去。");
            if (isPolice) {
                player.addBribePoliceCount();
                remember(Memory.Fact.BRIBED_POLICE);
            } else {
                player.addBribeGangCount();
                remember(Memory.Fact.BRIBED_GANG);
            }
            if (isPolice) {
                player.addEcho(GameConfig.ECHO_BRIBE_POLICE);
            } else {
                player.addEcho(GameConfig.ECHO_BRIBE_GANG);
            }
            if (isPolice) {
                System.out.println("【系统】城警追踪信号增强。建议规避主路。");
            }
            return payOff(player, bribeCost,
                    "花费", (isPolice ? "没钱贿赂，货物被扣。" : "没钱交，货物被抢。"));
        }
    }

    /**
     * 义体故障事件：玩家可选停车修理（无损失）或硬撑（扣10血继续）。
     *
     * @param player 玩家对象
     * @return true 表示强行通过（无延迟），false 表示停车修理（有延迟）
     */
    public boolean breakdownEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🔧 ─── 义体故障 ─── 🔧");
        System.out.println("义体过热警告。仪表盘闪烁红灯，速度骤降...");
        int choice = readChoiceWithEcho(
                "义体故障，选择行动：",
                "靠边停下慢慢修。",
                "不管了，硬撑到底。（扣10血）"
        );
        if (choice == 1) {
            System.out.println(">> 你靠边停下，打开引擎盖。");
            System.out.println("你花了点时间把故障修好。夜之城的雨打在背上。");
            System.out.println("【系统】修理完成。耗费了额外时间。");
            return false;
        } else {
            System.out.println(">> 你咬咬牙，强行继续。");
            player.loseHealth(10);
            System.out.println("义体过载的灼痛传遍全身。但你节省了时间。");
            return true;
        }
    }

    /**
     * 竞争对手事件：竞速（速度决定胜率）、协商平分、或主动让出。
     * 平分会设置 rivalFriend 标记，影响结局判定。
     *
     * @param player 玩家对象
     * @return true 表示胜出或平分（报酬保留），false 表示输掉或退让（报酬减少或归零）
     */
    public boolean rivalCourierEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🏍️ ─── 竞争对手 ─── 🏍️");
        System.out.println("一辆破摩托从巷子里窜出来，一个快递员冲你喊道：\"是我的单子！\"");
        int choice = readChoiceWithEcho(
                "竞争对手出现，选择：",
                "油门到底。路上见真章。",
                "提议平分这单。",
                "让他先走。"
        );
        if (choice == 1) {
            System.out.println(">> 你一脚油门，和他并排冲了出去。");
            double winChance = 0.4 + player.getSpeed() * 0.1;

            System.out.printf("胜率：%.0f%%\n", Math.min(winChance * 100, 100));

            boolean won = GameConfig.RANDOM.nextDouble() < winChance;
            if (won) {
                System.out.println("你抢先一步到达客户面前！报酬翻倍！");
                return true;
            } else {
                System.out.println("对方技高一筹，你输掉了这单。报酬归零。");
                return false;
            }
        } else if (choice == 2) {
            System.out.println(">> 你冲他喊：\"平分怎么样？\"他愣了一下，点了点头。");
            player.setRivalFriend(true);
            player.addEcho(GameConfig.ECHO_RIVAL_COOPERATE);
            player.addHumanity();
            System.out.println("你们握手言和。以后他可能会在关键时刻帮你一把。");
            return true;
        } else {
            System.out.println(">> 你挥挥手，让他先走。");
            player.addEcho(GameConfig.ECHO_RIVAL_GIVEUP);
            player.addReputation(10);
            System.out.println("你退到路边。");
            return false;
        }
    }

    /**
     * 路人求救事件：救人（可能获得赏金或拾荒者情报）、翻口袋（获得金钱/物品但扣声望）、或无视。
     * 翻口袋会设置 looted 标记，影响结局。
     *
     * @param player 玩家对象
     * @return 1 表示救人，0 表示翻口袋或无视
     */
    public int rescueEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🆘 ─── 路人求救 ─── 🆘");
        printVoice("RESCUE");
        System.out.println("巷口传来微弱的呼救声。一个陌生人靠墙坐着，身上的刀伤还在渗血。");
        System.out.println("\"帮帮我……我有钱……\"");
        int choice = readChoiceWithEcho(
                "遇到求救者，选择：",
                "把他扶上车。送诊所。",
                "翻他的口袋。拿钱走人。",
                "当没看见。"
        );
        if (choice == 1) {
            System.out.println(">> 你蹲下身，把他扶上了车。");
            player.addHelpCount();
            player.addEcho(GameConfig.ECHO_RESCUE_SAVED);
            remember(Memory.Fact.SAVED_LIFE);
            player.addHumanity();
            System.out.println("他活下来了。你继续赶路。雨打在挡风玻璃上。");
            if (GameConfig.RANDOM.nextBoolean()) {
                int reward = 100 + GameConfig.RANDOM.nextInt(401);
                player.addMoney(reward);
                System.out.println("他如约付给你 " + reward + "€。");
            } else {
                System.out.println("他没有钱，但给了你一个重要情报：下个任务的客户会额外付小费。");
                player.addScavengerIntel();
            }
            return 1;
        } else if (choice == 2) {
            System.out.println(">> 你蹲下来，翻开了他的口袋。");
            player.setLooted(true);
            player.addEcho(GameConfig.ECHO_RESCUE_LOOT);
            remember(Memory.Fact.IGNORED_CRY);
            player.addColdness();
            System.out.println("【系统】道德协议已绕过。记录中。");
            player.reduceReputation(15);
            double roll = GameConfig.RANDOM.nextDouble();
            if (roll < 0.30) {
                int cash = 80 + GameConfig.RANDOM.nextInt(71);
                player.addMoney(cash);
                System.out.println("口袋里有几张皱钞。还有一张全家福。你没看。");
            } else if (roll < 0.55) {
                int cash = 200 + GameConfig.RANDOM.nextInt(151);
                player.addMoney(cash);
                System.out.println("一叠钞票。够你跑几单了。");
            } else if (roll < 0.75) {
                player.addInventoryItem("rare_part");
                System.out.println("你发现了一个军用级零件。");
            } else if (roll < 0.90) {
                player.addInventoryItem("black_market_intel");
                System.out.println("你找到一条黑市情报。");
            } else {
                System.out.println("翻了半天，什么值钱的都没有。");
            }
            return 0;
        } else {
            System.out.println(">> 你拉紧衣领，加快了脚步。");
            System.out.println("你假装没听见。夜之城不需要多余的同情心。");
            remember(Memory.Fact.IGNORED_CRY);
            return 0;
        }
    }

    /**
     * 神秘通讯事件：接受幽灵帮助（设置 ghostAccepted 标记但加通缉）、挂断、或反向追踪（30%概率成功）。
     * 幽灵帮助可在后续任务中免去失败罚金。
     *
     * @param player 玩家对象
     */
    public void ghostEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("👻 ─── 神秘通讯 ─── 👻");
        printVoice("GHOST");
        System.out.println("通讯器突然被杂音占据。一个合成语音从干扰中说：\"我可以修改你下次任务的记录……\"");
        int choice = readChoiceWithEcho(
                "神秘通讯，选择：",
                "接受帮助。（+1星通缉）",
                "询问条件。",
                "反向追踪信号。"
        );
        if (choice == 1) {
            System.out.println(">> \"成交。\"你说。");
            player.setGhostAccepted(true);
            player.addEcho(GameConfig.ECHO_GHOST_ACCEPT);
            player.addWantedLevel(1);
            remember(Memory.Fact.LISTENED_GHOST);
            remember(Memory.Fact.USED_GHOST_HELP);
            System.out.println("【系统】检测到未授权的数据链接。通缉信号已标记。");
            System.out.println("\"下次任务失败时，我会帮你摆平。\"通讯挂断。");
        } else if (choice == 2) {
            System.out.println(">> \"什么条件?\"你问。");
            System.out.println("\"我帮你免除一次任务失败惩罚。作为交换，你的通缉等级+1。\"");
            int confirm = readChoiceWithEcho(
                    "是否接受交易？",
                    "接受。（+1星通缉）",
                    "拒绝。"
            );
            if (confirm == 1) {
                System.out.println(">> \"成交。\"你说。");
                player.setGhostAccepted(true);
                player.addEcho(GameConfig.ECHO_GHOST_ACCEPT);
                player.addWantedLevel(1);
                System.out.println("【系统】检测到未授权的数据链接。通缉信号已标记。");
                System.out.println("\"下次任务失败时，我会帮你摆平。\"通讯挂断。");
            } else {
                System.out.println(">> \"算了。\"你按下挂断键。");
                System.out.println("\"随你便。\"通讯恢复安静。");
            }
        } else {
            System.out.println(">> 你打开追踪器，开始反向定位信号源。");
            if (GameConfig.RANDOM.nextDouble() < 0.3) {
                player.addEcho(GameConfig.ECHO_GHOST_TRACKED);
                System.out.println("追踪成功。你发现一个隐藏的黑客窝点。获得永久情报加成！");
                player.addReputation(10);
            } else {
                System.out.println("追踪失败，对方反向定位了你。电击传遍全身，损失 15 点血。");
                player.loseHealth(15);
            }
        }
    }

    /**
     * 地下赌局事件：押注50€（40%胜率赢100€）、无视、或报警（增加声望和人性，可能引发后续复仇）。
     *
     * @param player 玩家对象
     */
    public void gambleEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🎲 ─── 地下赌局 ─── 🎲");
        System.out.println("路边一群混混围着一块屏幕，正在赌赛博格斗。血溅在屏幕上。");
        int choice = readChoiceWithEcho(
                "地下赌局，选择：",
                String.format("押一把。赢了会所嫩模。（%d€）", 50),
                "摇摇头走人。",
                "悄悄报警。"
        );
        if (choice == 1) {
            if (player.getMoney() < 50) {
                System.out.println("钱不够。混混们哄笑起来。");
                return;
            }
            System.out.println(">> 你掏出 " + 50 + "€，押了下去。");
            player.costMoney(50);
            if (GameConfig.RANDOM.nextDouble() < 0.4) {
                player.addMoney(100);
                System.out.println("你押的选手赢了！");
            } else {
                System.out.println("你押的选手被一击KO。");
            }
        } else if (choice == 2) {
            System.out.println(">> 你摇摇头，绕开他们继续走。");
            System.out.println("夜之城的赌局，不沾为妙。");
        } else {
            System.out.println(">> 你退到暗处，打开了报警终端。");
            player.setGambleRevenge(true);
            player.addReputation(5);
            player.addEcho(GameConfig.ECHO_GAMBLE_REPORT);
            player.addHumanity();
            System.out.println("【系统】本地执法频道已收到匿名举报。");
            System.out.println("你按下发送键。然后删除了记录。");
            if (GameConfig.RANDOM.nextDouble() < 0.3) {
                System.out.println("但你注意到，有人看到了你报警的动作……");
            }
        }
    }

    /**
     * 城警扫描事件：减速通过（通缉等级越高越可能被检测）、花钱干扰（150€）、或绕路。
     *
     * @param player 玩家对象
     * @return true 表示绕路（耗费额外时间），false 表示通过或干扰（无额外延迟）
     */
    public boolean scanEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🔍 ─── 城警扫描 ─── 🔍");
        System.out.println("前方亮起蓝色光栅——城警自动扫描点。所有过往车辆逐一接受扫描。");
        int choice = readChoiceWithEcho(
                "城警扫描，选择：",
                "减速通过。相信扫描器。",
                String.format("花钱干扰信号。（%d€）", 150),
                "绕远路避开。"
        );
        if (choice == 1) {
            System.out.println(">> 你放慢速度，驶入扫描区域。");
            double detectChance = player.getWantedLevel() * 0.1;
            if (GameConfig.RANDOM.nextDouble() < detectChance) {
                player.addWantedLevel(1);
                System.out.println("扫描器发出警报！你的通缉等级上升了。");
            } else {
                System.out.println("绿色光束扫过车身。无事发生。");
            }
            return false;
        } else if (choice == 2) {
            System.out.println(">> 你启动了信号干扰器。");
            if (player.getMoney() < 150) {
                System.out.println("钱不够，只能硬着头皮接受扫描。");
                return false;
            }
            player.costMoney(150);
            double failChance = 0.1 + player.getWantedLevel() * 0.08;
            if (GameConfig.RANDOM.nextDouble() < failChance) {
                player.addWantedLevel(1);
                System.out.println("干扰失败！扫描器发出警报。");
            } else {
                System.out.println("干扰成功。扫描器显示误报，你安全通过。");
            }
            return false;
        } else {
            System.out.println(">> 你打转方向，拐进旁边的小巷。");
            player.addEcho(GameConfig.ECHO_SCAN_DETOUR);
            System.out.println("你绕了远路。多花了 1 小时到达目的地。");
            return true;
        }
    }

    /**
     * 拾荒者事件：购买情报（50€获得拾荒者情报，降低下个任务风险）、无视、或赶走（扣声望）。
     *
     * @param player 玩家对象
     */
    public void scavengerEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🗑️ ─── 拾荒者 ─── 🗑️");
        printVoice("SCAVENGER");
        System.out.println("废弃工厂里探出一个拾荒者：\"嘿，要不要看看我刚找到的宝贝？\"");
        int choice = readChoiceWithEcho(
                "拾荒者，选择：",
                String.format("花钱买他的情报。（%d€）", 50),
                "无视他。",
                "赶他滚蛋。"
        );
        if (choice == 1) {
            if (player.getMoney() < 50) {
                System.out.println("钱不够。拾荒者失望地缩回了工厂。");
                return;
            }
            System.out.println(">> 你掏出 " + 50 + "€，递了过去。");
            player.costMoney(50);
            player.addScavengerIntel();
            player.addEcho(GameConfig.ECHO_SCAVENGER_INTEL);
            remember(Memory.Fact.HELPED_SCAV);
            System.out.println("拾荒者递给你一张皱巴巴的地图：\"这条路警察巡逻少多了。\"");
            System.out.println("下个任务风险降低1星。");
        } else if (choice == 2) {
            System.out.println(">> 你没有停步，径直走过。");
            System.out.println("\"下次别后悔！\"拾荒者在身后喊道。");
        } else {
            System.out.println(">> \"滚远点。\"你头也不回地说。");
            player.addEcho(GameConfig.ECHO_SCAVENGER_DISMISS);
            player.reduceReputation(5);
            System.out.println("拾荒者沉默了。");
        }
    }

    /**
     * 黑市奖励事件：黑市件完成后触发，可选择接受1.5倍报酬（但后续2个任务城警扫描概率翻倍）。
     *
     * @param player  玩家对象
     * @param mission 当前任务（用于计算50%额外报酬）
     */
    public void blackMarketBonusEvent(Player player, MissionManager.Mission mission) {
        System.out.println("\n\n\n");
        System.out.println("💼 ─── 黑市交易 ─── 💼");
        System.out.println("买家打量着货物，报出一个价格。");
        int choice = gameWindow.showChoiceDialog("黑市交易", "买家出价1.5倍，但后续2个任务中城警扫描概率翻倍。你要接受吗？", new String[]{"接受", "拒绝"});
        if (choice < 0) choice = 1;
        if (choice == 0) {
            int bonus = (int)(mission.getReward() * 0.5);
            player.addMoney(bonus);
            player.setPendingScrutiny(2);
            System.out.println("你点了点头。\"成交。\"买家笑了。");
            System.out.println("【系统】检测到未注册交易标记。执法关注度已提升。");
        } else {
            System.out.println("你摇摇头。\"明智的选择。\"买家收起钱，消失在巷子里。");
        }
    }

    /**
     * 赌徒复仇事件：当 gambleRevenge 标记为 true 时触发，赌徒前来复仇。
     * 可选赔钱200€消灾或硬拼（50%胜率，失败扣15血）。
     *
     * @param player 玩家对象
     */
    public void gambleRevengeEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🔥 ─── 赌徒复仇 ─── 🔥");
        System.out.println("暗巷里闪出几个黑影。领头的正是那天被你报警的赌徒。");
        System.out.println("\"以为报警就没事了？\"他手里转着一颗骰子。");
        int choice = readChoiceWithEcho(
                "赌徒复仇，选择：",
                String.format("赔钱消灾。（%d€）", 200),
                "硬拼。"
        );
        if (choice == 1) {
            System.out.println(">> 你掏出钱扔在地上。");
            if (player.getMoney() >= 200) {
                player.costMoney(200);
                System.out.println("他们捡起钱走了。\"下次别让我见到你。\"");
            } else {
                System.out.println("你掏遍口袋凑不够钱。");
                player.loseHealth(15);
                System.out.println("他们揍了你一顿才走。");
            }
        } else {
            System.out.println(">> 你握紧拳头，准备动手。");
            if (GameConfig.RANDOM.nextDouble() < 0.5) {
                System.out.println("你一拳撂倒领头的，其他人一哄而散。");
            } else {
                player.loseHealth(15);
                System.out.println("对方人多，你被揍得不轻。");
            }
        }
    }

    /**
     * 通用选项读取方法，将按钮显示为 "[1] 选项文本" 格式并返回1-based的选择索引。
     *
     * @param prompt      提示文本（标题用）
     * @param optionTexts 各选项文案数组
     * @return 用户选择：1=第一个选项，2=第二个选项，…；关闭对话框默认返回1
     */
    private int readChoiceWithEcho(String prompt, String... optionTexts) {
        int maxOption = optionTexts.length;
        String[] buttonTexts = new String[maxOption];
        for (int i = 0; i < maxOption; i++) {
            buttonTexts[i] = "[" + (i+1) + "] " + optionTexts[i];
        }
        int choice = gameWindow.showChoiceDialog("选择", prompt, buttonTexts);
        if (choice < 0) return 1;
        return choice + 1;
    }

    /**
     * 无人机临检（v2.0 新增）。
     *
     * <p>与城警拦路不同，无人机不谈判：它只认标签。三条路——躲、冲、出示
     * 一份不够真的证件。躲考验速度，冲一定留案底，证件则是纯粹的钱换平安。
     *
     * @return true 表示安全通过
     */
    public boolean droneScanEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🛸 ─── 无人机临检 ─── 🛸");
        System.out.println("三架六旋翼从楼缝里降下来，机腹的扫描灯在你脸上划出一道白线。");
        System.out.println("机械音响起：\"请配合身份核验。\"");
        int fakeIdCost = 60 + player.getWantedLevel() * 40;
        int choice = readChoiceWithEcho(
                "无人机临检，选择行动：",
                "压低身体钻进垃圾箱后面的阴影。",
                "踩油门从扫描带里冲出去。（通缉 +1）",
                String.format("摸出影子证件扫一下。（%d€）", fakeIdCost)
        );
        switch (choice) {
            case 1 -> {
                System.out.println(">> 你贴着墙根挪进阴影。");
                double chance = 0.35 + player.getSpeed() * 0.12 + player.getAvoidPolice() * 0.1;
                if (tryOptionA(chance,
                        "扫描灯从你头顶滑过去，停了两秒，然后抬起。走了。",
                        "扫描灯锁定了你。无人机降下来，喷出一层定位粉尘。")) {
                    return true;
                }
                player.addWantedLevel(1);
                System.out.println("【系统】定位粉尘沾在衣服上。通缉等级 +1。");
                return false;
            }
            case 2 -> {
                System.out.println(">> 你把油门拧到底，撞开扫描带。");
                player.loseHealth(8);
                player.addWantedLevel(1);
                System.out.println("轮胎碾过一道减速带，车身跳起来。后视镜里三架无人机同时转向。");
                System.out.println("【系统】你甩掉了它们，但档案里多了一条。通缉 +1，血量 -8。");
                return true;
            }
            default -> {
                return payOff(player, fakeIdCost,
                        "证件上的照片和你一点都不像，但数据库说它是真的。",
                        "你翻遍口袋，连一张假证件都没有。无人机记下了你的脸。");
            }
        }
    }

    /**
     * 义体医生（v2.0 新增）。
     *
     * <p>夜之城唯一不问你钱从哪来的人。可以修，也可以买「更快的自己」，
     * 但后者要贵得多——这是把「治疗」和「成长」放在同一个界面里竞争的设计。
     *
     * @return 0=什么都没做，1=修好了，2=买了神经调校（应发技能点）
     */
    public int ripperdocEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🩺 ─── 义体医生 ─── 🩺");
        System.out.println("卷帘门半开着，里面亮着一盏手术灯。一个老太太正在给机械臂上油。");
        System.out.println("\"坐吧。修哪里？还是想变得更快点？\"");
        int healCost = 80 + player.getLevel() * 10;
        int tuneCost = 260 + player.getLevel() * 30;
        int choice = readChoiceWithEcho(
                "义体医生，选择：",
                String.format("把伤口焊上。（%d€，恢复 45 生命）", healCost),
                String.format("做一次神经调校。（%d€，获得 1 技能点）", tuneCost),
                "摇摇头，走出去。"
        );
        if (choice == 1) {
            if (player.getMoney() < healCost) {
                System.out.println("老太太看了一眼你的账户，把手术灯关了。\"下次吧。\"");
                return 0;
            }
            player.costMoney(healCost);
            player.restoreHealth(45);
            System.out.println("焊接的焦味散开。你活动了一下手指——都还在。");
            System.out.println("【系统】恢复 45 生命，当前 " + player.getHealth() + "/" + player.getMaxHealth());
            return 1;
        }
        if (choice == 2) {
            if (player.getMoney() < tuneCost) {
                System.out.println("\"这活儿不便宜。\" 她把针管收回去。");
                return 0;
            }
            player.costMoney(tuneCost);
            remember(Memory.Fact.TUNED_BODY);
            System.out.println("针管推进颈侧。世界忽然变慢了半拍，你能看清每一滴雨。");
            System.out.println("【系统】神经调校完成，获得 1 技能点。");
            return 2;
        }
        System.out.println(">> 你退了出去。她在你背后喊：\"别死太快，缝合线涨价了。\"");
        return 0;
    }

    /**
     * 地下电台（v2.0 新增）。
     *
     * <p>不赚钱，只给两种长期价值：情报（本次任务风险下降）与名气（声望上升）。
     * 在「马上要钱」和「以后好过」之间再插一个选择点。
     *
     * @return 0=没听，1=听了情报（调用方应降风险），2=点了歌（声望上升）
     */
    public int radioEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("📻 ─── 地下电台 ─── 📻");
        System.out.println("车载电台跳到一个没有编号的频段。一个沙哑的声音正在念今晚的巡逻表。");
        System.out.println("\"……三号街区、七号桥、旧货场。听不听随你。\"");
        int choice = readChoiceWithEcho(
                "地下电台，选择：",
                "把频道记下来，等会儿绕开这些地方。",
                "打进去，点一首歌，报上自己的代号。",
                "关掉电台。"
        );
        if (choice == 1) {
            System.out.println(">> 你把这几个地名刻进脑子里。");
            System.out.println("【系统】本次任务风险下降。");
            return 1;
        }
        if (choice == 2) {
            System.out.println(">> 你按下通话键，报上代号，点了一首老歌。");
            System.out.println("对面沉默了两秒：\"……行。今晚全城都听得见你。\"");
            player.addReputation(6);
            System.out.println("【系统】声望 +6。有人开始记得你了。");
            return 2;
        }
        System.out.println(">> 你伸手关掉电台。车厢里只剩下雨声。");
        return 0;
    }

    /**
     * 街头艺人（v2.0）。
     *
     * <p>没有威胁，也没有报酬。只是提醒你：这座城市里还有人在做不赚钱的事。
     *
     * @return 0=路过，1=打赏（声望上升）
     */
    public int streetPerformerEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🎻 ─── 街头艺人 ─── 🎻");
        printVoice("PERFORMER");
        System.out.println("一个老人坐在配电箱上拉琴。琴弓的毛掉了一半，音准也不太对。");
        System.out.println("但整条街的人都放慢了脚步。");
        int choice = readChoiceWithEcho(
                "街头艺人，选择：",
                "停下来听完这一曲。",
                "往他的琴盒里放点钱。（50€）",
                "继续赶路。"
        );
        if (choice == 1) {
            System.out.println(">> 你把车停在路边，靠着墙听完。");
            System.out.println("曲子结束时，老人冲你点了一下头。你忽然想起自己为什么来这座城市。");
            player.restoreHealth(5);
            System.out.println("【系统】恢复 5 点生命。有些东西比义体管用。");
            return 0;
        }
        if (choice == 2) {
            if (player.getMoney() < 50) {
                System.out.println(">> 你摸遍口袋，只有几枚硬币。老人笑了笑，继续拉。");
                return 0;
            }
            player.costMoney(50);
            player.addReputation(4);
            remember(Memory.Fact.GAVE_MONEY);
            System.out.println(">> 硬币落进琴盒，声音很轻。");
            System.out.println("【系统】声望 +4。");
            return 1;
        }
        System.out.println(">> 你没有停。琴声在身后拐了个弯，消失了。");
        return 0;
    }

    /**
     * 小孩扒手（v2.0）。
     *
     * <p>抓与不抓都有代价：抓回来拿回钱但心里不舒服；放过则损失一点现金。
     *
     * @return true 表示钱被追回
     */
    public boolean pickpocketEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🧤 ─── 小孩扒手 ─── 🧤");
        printVoice("PICKPOCKET");
        System.out.println("一只手从侧面撞过来。你下意识按住口袋——少了一叠。");
        System.out.println("一个不到十岁的孩子正往巷子里钻，跑得很熟练。");
        int stolen = Math.min(player.getMoney(), 60 + player.getLevel() * 10);
        int choice = readChoiceWithEcho(
                String.format("小孩扒手，选择：（损失 %d€）", stolen),
                "追上去。",
                "算了，让他跑。",
                "喊住他，给他点吃的钱。"
        );
        if (choice == 1) {
            player.costMoney(stolen);
            if (rng2() < 0.65) {
                player.addMoney(stolen);
                System.out.println(">> 你追了半条街，在死胡同里把他堵住。");
                System.out.println("他把钱扔在地上就跑了。你捡起来，一张都没少。");
                System.out.println("【系统】钱已追回。");
                return true;
            }
            System.out.println(">> 你追丢了。巷子太窄，他太熟。");
            System.out.println("【系统】损失 " + stolen + "€。");
            return false;
        }
        if (choice == 2) {
            player.costMoney(stolen);
            System.out.println(">> 你停下来，看着那个背影消失在通风管里。");
            System.out.println("【系统】损失 " + stolen + "€。");
            return false;
        }
        player.costMoney(stolen + 30);
        player.addReputation(4);
        remember(Memory.Fact.GAVE_MONEY);
        System.out.println(">> 你喊了一声。他停住了，肩膀绷得很紧。");
        System.out.println(">> 你把钱拿回来，又塞给他 30€：\"去买点热的。\"");
        System.out.println("【系统】声望 +4。他记住了你的脸。");
        return true;
    }

    /**
     * 旧情人（v2.0）。
     *
     * <p>纯粹的情绪事件：给钱、听她说完、或者走开。没有最优解。
     *
     * @return 0=离开，1=叙旧（声望/回血），2=给钱
     */
    public int exLoverEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🌧️ ─── 旧相识 ─── 🌧️");
        printVoice("EXLOVER");
        System.out.println("便利店门口，有人叫住你。你花了三秒才认出她。");
        System.out.println("\"还在跑单？\" 她把烟按灭在雨里，\"我以为你早离开这儿了。\"");
        int choice = readChoiceWithEcho(
                "旧相识，选择：",
                "把车熄火，陪她站一会儿。",
                "塞给她一些钱。（200€）",
                "说还有单，先走了。"
        );
        if (choice == 1) {
            System.out.println(">> 你们在雨里站了十分钟，聊了三件无关紧要的事。");
            System.out.println(">> 临走时她说：\"别死在这儿。\"");
            player.restoreHealth(12);
            player.addReputation(3);
            System.out.println("【系统】恢复 12 点生命，声望 +3。");
            return 1;
        }
        if (choice == 2) {
            if (player.getMoney() < 200) {
                System.out.println(">> 你掏口袋的动作停住了。她看见了，笑了一下：\"不用。\"");
                return 0;
            }
            player.costMoney(200);
            player.addReputation(6);
            remember(Memory.Fact.GAVE_MONEY);
            System.out.println(">> 她没推辞，把钱折好放进内袋。\"当我借的。\"");
            System.out.println("【系统】声望 +6。");
            return 2;
        }
        System.out.println(">> 你说还有单。她点头，转身走进便利店。");
        System.out.println(">> 后视镜里，她在挑一瓶最便宜的水。");
        return 0;
    }

    /**
     * 公司 HR（v2.0）。
     *
     * <p>整座城最像陷阱的一个机会：出卖同行换一张工牌。
     *
     * @return true 表示接受了交易
     */
    public boolean corpoOfferEvent(Player player) {
        System.out.println("\n\n\n");
        System.out.println("🏢 ─── 公司招聘 ─── 🏢");
        printVoice("CORPO");
        System.out.println("一个穿灰西装的人拦住你，递来一张名片。纸质很好，边缘烫金。");
        System.out.println("\"我们注意到你的路线数据。想不想换个活法？\"");
        System.out.println("\"条件很简单：告诉我们谁在跑黑市件。\"");
        int choice = readChoiceWithEcho(
                "公司招聘，选择：",
                "把同行的名字给他。",
                "拒绝，把名片还回去。",
                "假装同意，套他的话。"
        );
        if (choice == 1) {
            player.addMoney(900);
            player.addReputation(-8);
            player.addWantedLevel(-1);
            remember(Memory.Fact.SOLD_OUT_COLLEAGUE);
            System.out.println(">> 他记下了三个名字，然后把一张工牌放在你手里。");
            System.out.println(">> 第二天，那三个人常去的两个点被封了。");
            System.out.println("【系统】+900€，通缉 -1，声望 -8。");
            return true;
        }
        if (choice == 2) {
            player.addReputation(7);
            remember(Memory.Fact.REFUSED_CORPO);
            System.out.println(">> 你把名片推回去：\"我跑单的，不卖人。\"");
            System.out.println(">> 他收回名片，笑容一点没变：\"那祝你长命。\"");
            System.out.println("【系统】声望 +7。");
            return false;
        }
        player.addWantedLevel(1);
        player.addReputation(2);
        System.out.println(">> 你含糊地应了两句，套出一个编号和一条内线。");
        System.out.println(">> 转身之后，你听见他在打电话。");
        System.out.println("【系统】你拿到了公司内线情报（后续贿赂更便宜），但通缉 +1。");
        return false;
    }

    // ══════════════════════════════════════════════════════════
    //  具名人物遭遇：关系决定他们出现时是帮你还是害你
    // ══════════════════════════════════════════════════════════

    /**
     * 具名人物遭遇。
     *
     * <p>同一个老K，在你救过他之后递给你一桩好活；在你出卖过同行之后，
     * 他站在雨里告诉你他把你的行踪卖给了公司。分支完全由关系值决定。
     *
     * @param who      遇到谁
     * @param relation 当前关系值（-100 ~ 100）
     */
    public void npcEncounter(Player player, Contacts.Who who, int relation) {
        switch (who) {
            case OLD_K -> oldKEncounter(player, relation);
            case XIAOMAN -> xiaomanEncounter(player, relation);
            case GRAY -> grayEncounter(player, relation);
            case WEI -> weiEncounter(player, relation);
            case DOC -> docEncounter(player, relation);
        }
    }

    private void oldKEncounter(Player player, int relation) {
        System.out.println("\n\n\n");
        System.out.println("🕶️ ─── 老K ─── 🕶️");
        if (relation >= 40) {
            System.out.println("老K 在路边冲你抬了抬下巴：\"有个活儿，别人接不走。\"");
            int choice = readChoiceWithEcho(
                    "老K 的活儿：",
                    "接。",
                    "让他自己接。",
                    "问他这些年去哪儿了。");
            if (choice == 1) {
                player.addMoney(450);
                player.addScavengerIntel();
                System.out.println(">> 他把一个地址和一小包东西塞进你手里。");
                System.out.println("【系统】+450€，并获得一条路线情报。");
            } else if (choice == 2) {
                System.out.println(">> \"你这人就是太客气。\"他把烟掐了。");
                player.addReputation(3);
            } else {
                System.out.println(">> 他沉默了几秒：\"有些事说了，你会睡不着。\"");
                System.out.println(">> 他还是说了。三年前那个冬天，和一个不该接的地址。");
            }
        } else if (relation <= -45) {
            System.out.println("老K 站在雨里，等你走近。");
            System.out.println("\"你那几单，我卖给公司了。\"他说得很平静，\"你不会怪我吧。\"");
            player.addWantedLevel(1);
            System.out.println("【系统】通缉 +1。老K 已经不在你这边了。");
        } else {
            System.out.println("老K 靠着墙抽烟，看见你，只是抬了下手。");
            System.out.println("\"最近不太顺。\"他说。没说是什么事。");
        }
    }

    private void xiaomanEncounter(Player player, int relation) {
        System.out.println("\n\n\n");
        System.out.println("🧒 ─── 小满 ─── 🧒");
        if (relation >= 40) {
            System.out.println("通风管里探出一张小脸。小满倒挂着，冲你比了个手势：上面，安全的。");
            player.addScavengerIntel();
            System.out.println(">> 她在管道里敲了三下。前面的封锁，她帮你绕过去了。");
            System.out.println("【系统】获得路线情报，本次任务风险下降。");
        } else if (relation <= -45) {
            System.out.println("通风管口蹲着一个小小的影子。小满看着你，没动。");
            System.out.println("\"他们说你卖过人。\"她说，\"所以我们不给你留门。\"");
            player.addWantedLevel(1);
            System.out.println("【系统】屋顶的路对你关闭，通缉 +1。");
        } else {
            System.out.println("小满从垃圾堆后面探出头，看了你一眼，又缩回去了。");
            System.out.println("她没说话，但在地上用手指画了一个箭头，指向反方向。");
        }
    }

    private void grayEncounter(Player player, int relation) {
        System.out.println("\n\n\n");
        System.out.println("🕴️ ─── 格雷 ─── 🕴️");
        if (relation >= 40) {
            System.out.println("灰西装在街角等你，伞下干干净净。");
            System.out.println("\"公司有一批长期单，稳定、高价、不查来源。\"");
            int choice = readChoiceWithEcho(
                    "格雷的提议：",
                    "接下长期单。",
                    "拒绝。",
                    "反过来问：公司为什么盯上我？");
            if (choice == 1) {
                player.addMoney(600);
                player.addWantedLevel(1);
                System.out.println(">> 他把一枚新的芯片放在你掌心。");
                System.out.println("【系统】+600€，但芯片里始终有一个信标。通缉 +1。");
            } else if (choice == 2) {
                player.addReputation(5);
                System.out.println(">> \"可惜。\"他把芯片收回去，\"可惜了。\"");
            } else {
                System.out.println(">> 他的笑第一次停了半秒。");
                System.out.println("\"因为好用的人不多。\"");
            }
        } else if (relation <= -45) {
            System.out.println("两个灰西装站在你车前。其中一个礼貌地敲了敲引擎盖。");
            System.out.println("\"格雷先生想聊聊。\"他说，\"关于你最近做的选择。\"");
            player.addWantedLevel(1);
            System.out.println("【系统】通缉 +1。公司开始盯你了。");
        } else {
            System.out.println("格雷在咖啡店窗后朝你举了举杯。");
            System.out.println("隔着玻璃，你读不出那是邀请还是警告。");
        }
    }

    private void weiEncounter(Player player, int relation) {
        System.out.println("\n\n\n");
        System.out.println("🌂 ─── 薇 ─── 🌂");
        if (relation >= 40) {
            System.out.println("便利店门口，薇 撑着伞等你。她把一个纸袋塞给你。");
            System.out.println("\"老地方的钥匙。\"她说，\"别弄丢了。\"");
            player.restoreHealth(15);
            player.addScavengerIntel();
            System.out.println(">> 纸袋里有一瓶热水、一卷绷带，和一张地址。");
            System.out.println("【系统】恢复 15 点生命，获得一条情报。");
        } else if (relation <= -45) {
            System.out.println("薇 看见你，往相反的方向走了两步，然后停下来。");
            System.out.println("\"我以为你至少还认得我的名字。\"");
            System.out.println("她没再说别的。雨伞很快被雨打翻。");
        } else {
            System.out.println("薇 站在灯下，看见你，只是把手插进口袋。");
            System.out.println("\"路过而已。\"她说。你不知道是不是真的。");
        }
    }

    private void docEncounter(Player player, int relation) {
        System.out.println("\n\n\n");
        System.out.println("🩺 ─── Doc ─── 🩺");
        if (relation >= 40) {
            System.out.println("Doc 的卷帘门开着一半。她头也不抬：\"进来，别挡光。\"");
            player.restoreHealth(45);
            System.out.println(">> 她给你补了三处接口，收了象征性的一点。");
            System.out.println("【系统】恢复 45 生命。Doc 没问你要钱。");
        } else if (relation <= -45) {
            System.out.println("Doc 看见你，把工具放下了。\"今天不营业。\"");
            System.out.println("\"而且你上次付的是命，不是钱。\"");
        } else {
            System.out.println("Doc 正在给一个客人换关节，抬头看了你一眼。");
            System.out.println("\"排队。\"她说，\"或者带钱来插队。\"");
        }
    }

    /** 供事件内部使用的轻量随机源，避免依赖 GameConfig 的静态实例。 */
    private double rng2() {
        return GameConfig.RANDOM.nextDouble();
    }

    /**
     * 尝试性操作：按成功概率判定并输出对应消息。
     *
     * @param successChance 成功率 (0.0~1.0)
     * @param successMsg    成功时显示的消息
     * @param failMsg       失败时显示的消息
     * @return true 表示成功，false 表示失败
     */
    private boolean tryOptionA(double successChance, String successMsg, String failMsg) {
        System.out.printf("成功率：%.0f%%\n", Math.min(successChance * 100, 100));

        boolean confirmed = GameConfig.RANDOM.nextDouble() < successChance;
        if (confirmed) {
            System.out.println(successMsg);
        } else {
            System.out.println(failMsg);
        }
        return confirmed;
    }

    /**
     * 付费操作：检查金额是否足够，足够则扣款并成功，不足则输出失败消息。
     *
     * @param player     玩家对象
     * @param cost       需要支付的金额
     * @param successMsg 成功时显示的消息前缀
     * @param failMsg    失败时显示的消息
     * @return true 表示付费成功，false 表示余额不足
     */
    private boolean payOff(Player player, int cost, String successMsg, String failMsg) {
        if (player.getMoney() >= cost) {
            player.costMoney(cost);
            System.out.println(successMsg + " " + cost + "€ 摆平。");
            return true;
        } else {
            System.out.println(failMsg);
            return false;
        }
    }
}