package com.moji.NightCityCourier;

/**
 * 随机事件系统，处理送货途中所有随机遭遇事件。
 * 包含9种事件类型：城警/帮派威胁、义体故障、竞争对手、路人求救、
 * 神秘通讯、赌局、扫描、拾荒者、黑市奖励、赌徒复仇。
 * <p>
 * 每个事件通过 {@link #readChoiceWithEcho} 展示选项并获取玩家输入。
 */
public class EventSystem {

    private final GameWindow gameWindow;

    public EventSystem(GameWindow gameWindow) {
        this.gameWindow = gameWindow;
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
            } else {
                player.addBribeGangCount();
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
        System.out.println("废弃工厂里探出一个拾荒者：\"嘿，要不要看看我刚找到的宝贝？\"");
        int choice = readChoiceWithEcho(
                "拾荒者，选择：",
                String.format("花钱买他的情报。（%d€）", 50),
                "无视他。",
                "赶他滚蛋。"
        );
        if (choice == 1) {
            System.out.println(">> 你掏出 " + 50 + "€，递了过去。");
            if (player.getMoney() < 50) {
                System.out.println("钱不够。拾荒者失望地缩回了工厂。");
                return;
            }
            player.costMoney(50);
            player.addScavengerIntel();
            player.addEcho(GameConfig.ECHO_SCAVENGER_INTEL);
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