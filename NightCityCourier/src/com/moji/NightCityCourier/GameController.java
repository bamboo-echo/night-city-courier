package com.moji.NightCityCourier;
import javax.swing.*;
import java.awt.*;
import java.util.*;

/**
 * 游戏主控制器，负责游戏主循环、订单管理、送货逻辑、事件调度和结局触发。
 * 核心流程：接单→赶路(逐步触发事件/回响/变体)→完成送货→触发送货后变体→检查结局/死亡条件→循环。
 */
public class GameController {

    private static final int CHOICE_ACCEPT = 0;
    private static final int CHOICE_REFRESH = 1;
    private static final int CHOICE_EXIT = 2;

    private Player player;
    private final MissionManager missionManager;
    private final UpgradeSystem upgradeSystem;
    private final EventSystem eventSystem;
    private final GameWindow gameWindow;
    private GameWindow.TerminalStateManager terminalStateManager;
    private EndingSystem.EchoPool echoPool;

    public GameController(Player player, MissionManager missionManager, UpgradeSystem upgradeSystem, EventSystem eventSystem, GameWindow gameWindow) {
        this.player = player;
        this.missionManager = missionManager;
        this.upgradeSystem = upgradeSystem;
        this.eventSystem = eventSystem;
        this.gameWindow = gameWindow;
        this.echoPool = new EndingSystem.EchoPool();
        initTerminalState();
    }

    private void initTerminalState() {
        gameWindow.setPlayer(player);
        if (gameWindow.getHudPanel() != null) {
            gameWindow.getHudPanel().setPlayer(player);
        }
        if (gameWindow.getParticlePanel() != null && gameWindow.getScanlineOverlay() != null) {
            terminalStateManager = gameWindow.new TerminalStateManager(gameWindow, gameWindow.getParticlePanel(), gameWindow.getScanlineOverlay());
            terminalStateManager.setPlayer(player);
        }
    }

    public void triggerVisualUpdate() {
        if (terminalStateManager != null) {
            terminalStateManager.triggerUpdate();
        }
    }

    /**
     * 游戏主循环入口，运行在一个单独的线程中。
     * 持续循环：检查结局→显示订单→等待玩家选择→执行操作。
     */
    public void run() {
        while (true) {
            gameWindow.showStatus(player);
            System.out.println("[使用底部按钮选择操作]");
            int choice = gameWindow.getMenuChoice();
            switch (choice) {
                case 1 -> doMission();
                case 2 -> openUpgrade();
                case 3 -> showTarget();
                case 4 -> heal();
                case 5 -> gameWindow.showStats(player);
                case 6 -> {
                    if (!player.isWin()) {
                        System.out.println("你还没攒够 " + GameConfig.WIN_TARGET + "€，不能离开。继续送货吧。");
                    } else {
                        if (handleLastDelivery()) continue;
                        return;
                    }
                }
                case 7 -> Player.save(player);
                case 8 -> {
                    System.out.println("\n再见，" + player.getName() + "。夜之城不会忘记你。");
                    Main.shutdownSequence(gameWindow);
                    System.exit(0);
                }
                default -> System.out.println("无效选项");
            }

            if (player.isDead()) {
                if (triggerEnding()) continue;
                return;
            }

            if (player.hasGambleRevenge() && player.getHealth() <= GameConfig.HEALTH_CRITICAL) {
                if (triggerEnding()) continue;
                return;
            }

            if (player.isWantedMax()) {
                if (triggerEnding()) continue;
                return;
            }
        }
    }

    /**
     * 触发结局流程：生成结局文本→显示结局弹窗→玩家选择退出或重新开始。
     * @return true 表示玩家选择重新开始，false 表示退出
     */
    private boolean triggerEnding() {
        EndingSystem endingSystem = createEndingGenerator();
        System.out.println(endingSystem.generate());

        String echoSummary = endingSystem.buildEchoSummary();
        if (!echoSummary.isEmpty()) {
            System.out.println(echoSummary);
        }

        Main.shutdownSequence(gameWindow);

        String endType = endingSystem.getEndingTypeName();
        Color endColor = endingSystem.getEndingColor();
        String subtitle = endingSystem.getEndingSubtitle();

        boolean exitChosen = gameWindow.showEndingDialog(endType, subtitle, endColor);
        if (exitChosen) {
            System.out.println("\n再见，" + player.getName() + "。夜之城不会忘记你。");
            Main.shutdownSequence(gameWindow);
            System.exit(0);
            return false;
        }
        restartGame();
        return true;
    }

    private void restartGame() {
        String name = player.getName();
        player = new Player(name);
        echoPool = new EndingSystem.EchoPool();
        initTerminalState();
        gameWindow.showTitle();
        System.out.println("\n欢迎回到夜之城，" + name + "。");
        System.out.println("活下来，攒够钱，离开这里。\n");
    }

    private EndingSystem createEndingGenerator() {
        return new EndingSystem(player);
    }

    /**
     * 接单送货的入口方法，负责显示送货动画、执行赶路、处理送货后逻辑。
     * 成功后发放报酬、检查升级、在线存档、处理变体和黑市奖励。
     */
    private void doMission() {
        while (true) {
            System.out.println("\n\n\n");
            MissionManager.Mission mission = missionManager.generateMission(player);
            System.out.println("类型：" + mission.getType().displayName);
            System.out.println("风险：" + mission.getRiskDisplay());
            System.out.println("报酬：" + mission.getReward() + "€");
            System.out.println("赶路次数：" + mission.getTimeNeed());
            if (mission.getFailPenalty() > 0) {
                System.out.println("⚠️ 注意：此任务失败将扣除 " + mission.getFailPenalty() + "€");
            }
            if (player.hasScavengerIntel()) {
                System.out.println("🔍 你有拾荒者情报，可以消耗以降低本次任务风险。");
            }
            if (player.hasGhostAccepted()) {
                System.out.println("👻 你有幽灵帮助，可以消耗以免除本次任务失败惩罚。");
            }

            int selected = gameWindow.showChoiceDialog("接单", "接受订单？", new String[]{"接受", "刷新订单", "退出"});
            if (selected == CHOICE_EXIT || selected < 0) {
                System.out.println(">> 你选择了退出。");
                System.out.println("\n\n\n");
                System.out.println("放弃订单");
                return;
            }
            if (selected == CHOICE_REFRESH) {
                System.out.println(">> 你选择了刷新订单。");
                continue;
            }
            System.out.println(">> 你选择了接受订单。");

            if (player.hasScavengerIntel()) {
                player.consumeScavengerIntel();
                mission.reduceRisk(0.15);
                System.out.println("🔍 拾荒者的情报发挥了作用！本次任务风险降低。");
            }
            if (player.hasGhostAccepted()) {
                player.consumeGhostHelp();
                mission.clearFailPenalty();
                System.out.println("👻 通讯器里传来一个声音：\"这次，我帮你看着。\"失败惩罚已免除。");
            }

            System.out.println("\n\n\n");
            System.out.println("开始送货……");
            boolean success = delivery(mission);

            System.out.println("\n\n\n");
            System.out.println("═══════════════════════════════");

            if (success) {
                player.addMoney(mission.getReward());
                player.restoreHealth(GameConfig.TASK_SUCCESS_HEAL);
                player.addTotalTasks();
                if (player.getTotalTasks() % 5 == 0) {
                    player.levelUp();
                    System.out.println("🔼 升级！最大血量提升，当前血量上限：" + player.getMaxHealth());
                }
                System.out.println("✅ 交货成功！获得 " + mission.getReward() + "€，生命恢复" + GameConfig.TASK_SUCCESS_HEAL + "点");
                System.out.println("─────────────────────────────");
                triggerVisualUpdate();
                if (mission.getType() == MissionManager.MissionType.BLACK_MARKET) {
                    player.addBlackMarketCount();
                    player.addEcho(GameConfig.ECHO_BLACK_MARKET);
                    player.addColdness();
                    eventSystem.blackMarketBonusEvent(player, mission);
                }
                if (player.getPendingScrutiny() > 0) {
                    player.decrementPendingScrutiny();
                }
                if (mission.getVariant() != MissionManager.MissionVariant.NONE) {
                    handleMissionVariant(mission.getVariant(), mission);
                }
                return;
            } else {
                player.addTotalTasks();
                int healthLoss = (int)(mission.getRisk() * 20);
                player.loseHealth(healthLoss);
                System.out.println("❌ 任务失败！血量 -" + healthLoss);
                System.out.println("─────────────────────────────");
                triggerVisualUpdate();
                if (mission.getFailPenalty() > 0) {
                    int penalty = Math.max(GameConfig.FAIL_PENALTY_MIN, Math.min(mission.getFailPenalty(), player.getMoney()));
                    player.costMoney(penalty);
                    System.out.println("❌ 紧急件超时！被扣 " + penalty + "€");
                } else {
                    System.out.println("❌ 任务失败！一无所获。");
                }
                return;
            }
        }
    }

    /**
     * 执行一次送货任务。包含赶路循环、事件触发、回响触发和变体触发。
     *
     * @param mission 要执行的任务
     * @return true 表示送货完成成功，false 表示失败
     */
    private boolean delivery(MissionManager.Mission mission) {
        int step = 1;
        boolean reputationHelpChecked = false;
        boolean earlyVariantShown = false;
        boolean hasVariant = mission.getVariant() != MissionManager.MissionVariant.NONE;
        int variantTriggerStep = hasVariant ? 2 + GameConfig.RANDOM.nextInt(Math.max(1, mission.getTimeNeed() - 2)) : Integer.MAX_VALUE;
        while (step <= mission.getTimeNeed()) {
            if (!earlyVariantShown && step >= variantTriggerStep) {
                handleMissionVariantEarly(mission.getVariant());
                earlyVariantShown = true;
            }
            if (!reputationHelpChecked && player.getReputation() >= GameConfig.REPUTATION_VETERAN && GameConfig.RANDOM.nextDouble() < 0.05) {
                System.out.println("一个路人认出了你：\"我听说过你做的事。拿着，夜之城需要更多你这样的人。\"");
                player.restoreHealth(10);
                System.out.println("恢复 10 点血量。");
            }
            reputationHelpChecked = true;

            System.out.println("\n\n\n");
            System.out.println("───────── 赶路中 " + step + "/" + mission.getTimeNeed() + " ─────────");
            int act = gameWindow.showChoiceDialog("赶路", "选择行动", new String[]{"正常走", "抄近道"});
            if (act < 0) {
                System.out.println("已取消选择，默认正常走。");
                act = 0;
            }
            boolean rush = act == 1;
            System.out.println(">> 你选择了" + (rush ? "抄近道" : "正常走") + "。");

            if (rush) {
                int skip = GameConfig.RANDOM.nextInt(2) + 1;
                player.loseHealth(10);
                String[] scenes = {
                    "🏃 你翻过一道铁丝网，手被划了一道口子。",
                    "🏃 你钻进废弃管道，锈铁割破了手臂。",
                    "🏃 你穿过一片碎玻璃覆盖的仓库，脚被扎了。",
                    "🏃 你爬上消防梯捷径，护栏松动差点摔下去。",
                    "🏃 你闯进一片施工区，钢筋擦伤了肩膀。",
                    "🏃 你从屋顶一跃跳到对面，膝盖狠狠磕了一下。",
                    "🏃 你冲进一条污水横流的暗巷，滑倒擦伤了一大片。",
                    "🏃 你穿过一片带电警戒线，侥幸没触电但还是被灼了一下。"
                };
                System.out.println(scenes[GameConfig.RANDOM.nextInt(scenes.length)] + "扣10血，你省下了 " + skip + " 步路程。");
                step += skip;
                if (step >= mission.getTimeNeed()) break;
            }

            double baseChance = 0.4 + mission.getRisk() * GameConfig.ENCOUNTER_RISK_FACTOR;
            if (player.getPendingScrutiny() > 0) {
                baseChance *= 1.5;
            }
            baseChance = Math.min(baseChance, GameConfig.ENCOUNTER_CHANCE_CAP);
            if (GameConfig.RANDOM.nextDouble() < baseChance) {
                if (player.hasGambleRevenge() && GameConfig.RANDOM.nextDouble() < 0.3) {
                    eventSystem.gambleRevengeEvent(player);
                    step++;
                    continue;
                }
                int eventType = weightedEventType();
                String[] eventKeys = {"POLICE", "GANG", "BREAKDOWN", "RIVAL", "RESCUE", "GHOST", "GAMBLE", "SCAN", "SCAVENGER"};
                String actualKey = eventKeys[eventType];
                echoPool.recordEncounter(actualKey);
                // 回响：同一类事件第二次以上遭遇时，按当前遭遇次数取回响文案
                if (echoPool.shouldTriggerEcho(actualKey, 0.08) && echoPool.getEncounterCount(actualKey) > 1) {
                    System.out.println(echoPool.getEchoText(actualKey, echoPool.getEncounterCount(actualKey)));
                }
                int stepDelta = processEncounter(mission, step, eventType);
                if (stepDelta == -999) return false;
                step += stepDelta;
                continue;
            }

            if (!rush) {
                step++;
            }
        }
        return true;
    }

    /**
     * 根据事件类型分派到具体的处理方法。
     *
     * @param mission   当前任务
     * @param step      当前步骤数
     * @param eventType 事件类型索引，对应 weightedEventType 的返回值
     * @return 0=正常（事件可能是步步数增减），>0=成功额外步数奖励
     */
    private int processEncounter(MissionManager.Mission mission, int step, int eventType) {
        int stepDelta = 1;

        switch (eventType) {
            case 0 -> {
                if (GameConfig.RANDOM.nextDouble() < player.getAvoidPolice() * GameConfig.AVOID_EVENT_CHANCE_PER_LEVEL) {
                    System.out.println("🛡️ 反追踪装置提前探测到警方扫描，你绕道避开了。");
                    return 1;
                }
                boolean eventSuccess = eventSystem.threatEvent(player, "POLICE");
                if (!eventSuccess) return -999;
                return stepDelta;
            }
            case 1 -> {
                if (player.hasJoinedGang() && GameConfig.RANDOM.nextDouble() < 0.5) {
                    System.out.println("🛡️ 帮派成员看到你的徽章，让开了路。");
                    return 1;
                }
                if (GameConfig.RANDOM.nextDouble() < player.getAvoidGang() * GameConfig.AVOID_EVENT_CHANCE_PER_LEVEL) {
                    System.out.println("🛡️ 你在帮派出没区域提前察觉埋伏，绕道避开了。");
                    return 1;
                }
                boolean eventSuccess = eventSystem.threatEvent(player, "GANG");
                if (!eventSuccess) return -999;
                return stepDelta;
            }
            case 2 -> {
                boolean rushFix = eventSystem.breakdownEvent(player);
                if (!rushFix) {
                    System.out.println("原地修复故障，没有前进...");
                    stepDelta = 0;
                }
                return stepDelta;
            }
            case 3 -> {
                boolean eventSuccess = eventSystem.rivalCourierEvent(player);
                if (!eventSuccess) {
                    mission.setReward(0);
                    System.out.println("【系统】竞争对手抢走了这单。本次任务报酬已归零。");
                } else {
                    int bonus = mission.getReward();
                    mission.setReward(mission.getReward() + bonus);
                    System.out.println("【系统】你赢过了竞争对手！本次任务报酬翻倍。");
                }
                return stepDelta;
            }
            case 4 -> {
                int rescueDelta = eventSystem.rescueEvent(player);
                stepDelta += rescueDelta;
                return stepDelta;
            }
            case 5 -> {
                eventSystem.ghostEvent(player);
                return stepDelta;
            }
            case 6 -> {
                eventSystem.gambleEvent(player);
                return stepDelta;
            }
            case 7 -> {
                if (eventSystem.scanEvent(player)) {
                    int extraSteps = player.getWantedLevel() >= 3 ? 3 : 2;
                    mission.addTimeNeed(extraSteps);
                    System.out.println("【系统】绕路导致任务步数增加 " + extraSteps + " 步。");
                    stepDelta = 1;
                } else {
                    stepDelta = 1;
                }
                return stepDelta;
            }
            case 8 -> {
                eventSystem.scavengerEvent(player);
                return stepDelta;
            }
            default -> {
                System.out.println("未知事件类型，继续前进。");
            }
        }
        return stepDelta;
    }

    private int weightedEventType() {
        int[] weights = {
            GameConfig.POLICE_WEIGHT, GameConfig.GANG_WEIGHT, GameConfig.BREAKDOWN_WEIGHT,
            GameConfig.RIVAL_WEIGHT, GameConfig.RESCUE_WEIGHT,
            GameConfig.GHOST_WEIGHT, GameConfig.GAMBLE_WEIGHT, GameConfig.SCAN_WEIGHT,
            GameConfig.SCAVENGER_WEIGHT
        };
        int total = 0;
        for (int w : weights) total += w;

        if (total <= 0) {
            System.out.println("[警告] 事件权重总和为 " + total + "，使用默认事件");
            return 0;
        }
        int roll = GameConfig.RANDOM.nextInt(total);
        int accum = 0;
        for (int i = 0; i < weights.length; i++) {
            accum += weights[i];
            if (roll < accum) return i;
        }
        return 0;
    }

    /**
     * 送货完成后触发的变体结算，包含所有变体类型的金钱/声望奖惩和状态标记。
     *
     * @param variant 任务变体类型
     * @param mission 当前任务（用于计算基于任务报酬的奖惩金额）
     */
    private void handleMissionVariant(MissionManager.MissionVariant variant, MissionManager.Mission mission) {
        switch (variant) {
            case TRAP -> handleVariantTrap();
            case LIVING -> handleVariantLiving();
            case CELEBRITY -> handleVariantCelebrity();
            case LEGACY -> handleVariantLegacy();
            case GANG_TRAP -> handleVariantGangTrap();
            case CONFESSION -> handleVariantConfession();
            case EVIDENCE -> handleVariantEvidence();
            case RACE -> handleVariantRace(mission);
            case REROUTE -> handleVariantReroute(mission);
            case LIFESAVER -> handleVariantLifesaver(mission);
            default -> {}
        }
    }

    /**
     * 送货途中触发的变体处理（竞速件、绕道件、救命件），仅做叙事铺垫，
     * 不涉及金钱奖惩。真正奖惩在送货完成后的 handleMissionVariant 中执行。
     *
     * @param variant 任务变体类型
     */
    private void handleMissionVariantEarly(MissionManager.MissionVariant variant) {
        switch (variant) {
            case RACE -> {
                System.out.println("\n\n\n");
                System.out.println("⚡ ─── 突发状况：竞速件 ─── ⚡");
                System.out.println("你到达时发现另一个快递员也在送同一单！");
                System.out.println("你们对视一眼，开始竞速。");
                double base = 0.4 + player.getSpeed() * 0.1;
                if (GameConfig.RANDOM.nextDouble() < base) {
                    System.out.println("你领先一步！");
                    player.addReputation(10);
                } else {
                    System.out.println("对方先到一步。你只能排在后面。");
                    player.addReputation(5);
                }
            }
            case REROUTE -> {
                System.out.println("\n\n\n");
                System.out.println("🔄 ─── 突发状况：绕道件 ─── 🔄");
                System.out.println("你发现了一条捷径，可以省下一段路程。");
                System.out.println("但这条路要穿过帮派地盘，有风险。");
                int choice = gameWindow.showChoiceDialog("绕道", "选择", new String[]{"走捷径", "原路走"});
                if (choice == 0) {
                    System.out.println("你拐进捷径。路上遇到几个帮派分子，他们看了你一眼，没拦你。");
                    player.reduceReputation(10);
                } else {
                    System.out.println("你选择原路走。稳妥，但多花了一些时间。");
                }
            }
            case LIFESAVER -> {
                System.out.println("\n\n\n");
                System.out.println("🆘 ─── 突发状况：救命件 ─── 🆘");
                System.out.println("路边有人呼救！但你赶时间。");
                int choice = gameWindow.showChoiceDialog("求救", "选择", new String[]{"停下来救人", "继续赶路"});
                if (choice == 0) {
                    player.addHelpCount();
                    player.addHumanity();
                    System.out.println("你停下救人。那人递给你一瓶能量饮料。");
                    player.addReputation(15);
                } else {
                    player.addColdness();
                    System.out.println("你加快了脚步。呼救声越来越远。");
                }
            }
            default -> {}
        }
    }

    private void handleVariantTrap() {
        System.out.println("\n\n\n");
        System.out.println("🚔 ─── 陷阱件 ─── 🚔");
        System.out.println("你刚放下货物，四周突然亮起警灯——收货人是城警卧底！");
        int choice = gameWindow.showChoiceDialog("陷阱件", "选择", new String[]{"交出货物", "逃跑"});
        if (choice < 0) {
            System.out.println(">> 你犹豫了。货还在原地，警察也还没下决定。这次什么都不算。");
            return;
        }
        if (choice == 1) {
            player.addRefuseCount();
            player.addWantedLevel(GameConfig.TRAP_WANTED_INCREASE);
            System.out.println("你抱起货物跳窗而逃。通缉 +" + GameConfig.TRAP_WANTED_INCREASE + "，但报酬保住了。");
        } else {
            player.setTrapSurrendered(true);
            player.addEcho(GameConfig.ECHO_TRAP_SURRENDER);
            int fine = player.getMoney() / 10;
            player.costMoney(fine);
            System.out.println("你交出货物和报酬。城警收了货，又罚了你 " + fine + "€ 作为运输违禁品的手续费。");
        }
    }

    private void handleVariantLiving() {
        System.out.println("\n\n\n");
        System.out.println("🐕 ─── 活体件 ─── 🐕");
        System.out.println("你打开包裹，里面跳出一只改造赛博犬！客户紧张地问：\"它还活着吗？\"");
        if (player.getHealth() >= 50) {
            player.addEcho(GameConfig.ECHO_LIVING_SAVED);
            player.addHumanity();
            player.addMoney(100);
            System.out.println("赛博犬活蹦乱跳。客户大喜，额外给你一笔钱！");
        } else {
            player.addEcho(GameConfig.ECHO_LIVING_DIED);
            player.addColdness();
            player.reduceReputation(10);
            System.out.println("赛博犬因为你在途中过于颠簸而虚弱不堪。客户拒付并给了差评。");
            player.costMoney(player.getMoney() / 5);
        }
    }

    private void handleVariantCelebrity() {
        System.out.println("\n\n\n");
        System.out.println("🌟 ─── 名人件 ─── 🌟");
        System.out.println("收货人竟然是夜之城小有名气的明星！一群粉丝蜂拥而至。");
        int choice = gameWindow.showChoiceDialog("名人件", "选择", new String[]{"护送", "丢下货物"});
        if (choice < 0) choice = 1;
        if (choice == 0) {
            player.setCelebEscort(true);
            player.addEcho(GameConfig.ECHO_CELEB_ESCORT);
            player.addEcho(GameConfig.ECHO_CELEB_ESCORT_BOND);
            player.addHumanity();
            player.addMoney(80);
            System.out.println("你挡开粉丝，护着她进了楼。她微笑着塞给你一笔小费。");
        } else {
            System.out.println("你把货物放在门口就走了，身后传来粉丝的尖叫声。");
        }
    }

    private void handleVariantLegacy() {
        System.out.println("\n\n\n");
        System.out.println("📦 ─── 遗物件 ─── 📦");
        System.out.println("收货地址是一间废弃公寓。邻居告诉你：收货人上周已经去世了。");
        int choice = gameWindow.showChoiceDialog("遗物件", "选择", new String[]{"退给家属", "自行保留"});
        if (choice < 0) choice = 0;
        if (choice == 0) {
            player.setLegacyReturned(true);
            player.addEcho(GameConfig.ECHO_LEGACY_RETURNED);
            player.addHumanity();
            player.addReputation(10);
            System.out.println("你把遗物送回寄件人家中。家属泣不成声地感谢你。");
            System.out.println("【系统】物品已交付至指定亲属。道德协议已履行。");
        } else {
            player.setStoleFromDead(true);
            player.addEcho(GameConfig.ECHO_LEGACY_KEPT);
            player.addColdness();

            int value = 200 + GameConfig.RANDOM.nextInt(301);
            player.addMoney(value);
            System.out.println("你打开包裹，里面值一些钱。你把它卖了。没人知道。");
            System.out.println("【系统】物品已转移至黑市渠道。");
        }
    }

    /**
     * 竞速件结算：公平竞速或暗中使绊，影响报酬和回响石。
     *
     * @param mission 当前任务
     */
    private void handleVariantRace(MissionManager.Mission mission) {
        System.out.println("\n\n\n");
        System.out.println("🏍️ ─── 竞速件 ─── 🏍️");
        System.out.println("你到达时发现另一个快递员也刚到——你们送的竟然是同一地址的件！");
        System.out.println("客户笑着说：\"先到的拿双倍，后到的拿一半。\"");
        int choice = gameWindow.showChoiceDialog("竞速件", "选择", new String[]{"公平竞速", "暗中使绊"});
        if (choice < 0) choice = 0;
        if (choice == 0) {
            if (GameConfig.RANDOM.nextDouble() < GameConfig.RACE_WIN_BASE + player.getSpeed() * GameConfig.RACE_WIN_PER_SPEED) {
                int bonus = (int)(mission.getReward() * GameConfig.RACE_BONUS_RATIO);
                player.addMoney(bonus);
                System.out.println("你抢先一步！客户额外给了你一笔钱！");
            } else {
                System.out.println("对方更快。你只拿到了一半报酬。");
                player.costMoney((int)(mission.getReward() * GameConfig.RACE_PENALTY_RATIO));
            }
        } else {
            player.addEcho(GameConfig.ECHO_RACE_HOG);
            player.addColdness();
            System.out.println("你趁对方不备，偷偷放了他的车气。你赢了双倍。");
            int bonus = (int)(mission.getReward() * GameConfig.RACE_BONUS_RATIO);
            player.addMoney(bonus);
        }
    }

    private void handleVariantGangTrap() {
        System.out.println("\n\n\n");
        System.out.println("🔪 ─── 圈套件 ─── 🔪");
        System.out.println("收货人身后站着几个凶神恶煞的帮派成员。他说：\"加入我们，这单翻倍。\"");
        int choice = gameWindow.showChoiceDialog("圈套件", "选择", new String[]{"加入", "拒绝"});
        if (choice < 0) choice = 1;
        if (choice == 0) {
            player.setJoinedGang(true);
            player.addWantedLevel(2);
            System.out.println("你接过染血的徽章。通缉 +" + 2 + "，但你在帮派地盘的通行证已到手。");
            System.out.println("【系统】检测到未经授权的组织归属。");
        } else {
            player.costMoney(player.getMoney() / 5);
            System.out.println("\"那这单你不用想了。\"你被搜刮一空赶出门外。");
        }
    }

    /**
     * 救命件结算：加价三倍或原价，涉及道德选择，影响结局。
     *
     * @param mission 当前任务
     */
    private void handleVariantLifesaver(MissionManager.Mission mission) {
        System.out.println("\n\n\n");
        System.out.println("💊 ─── 救命件 ─── 💊");
        System.out.println("包裹里是稀有药物。收货人奄奄一息躺在沙发上，旁边是他哭泣的女儿。");
        int choice = gameWindow.showChoiceDialog("救命件", "选择", new String[]{"加价三倍", "原价给他"});
        if (choice < 0) choice = 1;
        if (choice == 0) {
            player.addEcho(GameConfig.ECHO_LIFESAVER_GOUGED);
            player.addColdness();
            int extra = (int)(mission.getReward() * GameConfig.LIFESAVER_GOUGE_RATIO);
            player.addMoney(extra);
            System.out.println("\"……\"女儿的眼神让你永生难忘。你赚了一笔，但心里沉甸甸的。");
            System.out.println("【系统】超额利润已记录。");
        } else {
            player.setSparedLife(true);
            player.addEcho(GameConfig.ECHO_LIFESAVER_SPARED);
            player.addHumanity();
            player.addReputation(15);
            System.out.println("你默默放下药。女儿哭着道谢。你走出门，夜之城的雨打在脸上。");
            System.out.println("【系统】生命信号已稳定。道德协议已履行。");
        }
    }

    private void handleVariantReroute(MissionManager.Mission mission) {
        System.out.println("\n\n\n");
        System.out.println("📍 ─── 错送件 ─── 📍");
        System.out.println("通讯器响起：\"是我的包裹！送错地址了！送回来我给你双倍！\"");
        int choice = gameWindow.showChoiceDialog("错送件", "选择", new String[]{"回去送", "不回去"});
        if (choice < 0) choice = 1;
        if (choice == 0) {
            int bonus = (int)(mission.getReward() * GameConfig.REROUTE_BONUS_RATIO);
            player.addMoney(bonus);
            player.reduceReputation(10);
            System.out.println("你返回去送了。赚了一笔，但当前收货人给了你一个差评。");
        } else {
            System.out.println("\"你欠我的！\"通讯器那头发来怒火。但你这边已经完成了单子。");
        }
    }

    private void handleVariantConfession() {
        System.out.println("\n\n\n");
        System.out.println("💌 ─── 告白件 ─── 💌");
        System.out.println("一个年轻人让你送一封情书。对方看后冷淡地摇头：\"不感兴趣。\"");
        int choice = gameWindow.showChoiceDialog("告白件", "选择", new String[]{"诚实安慰", "骗他"});
        if (choice < 0) choice = 0;
        if (choice == 0) {
            player.addEcho(GameConfig.ECHO_CONFESSION_HONEST);
            player.addHumanity();
            player.addReputation(5);
            System.out.println("\"她会找到更好的人的。\"你拍拍他的肩。他苦笑了一下。");
        } else {
            player.setLiedToLover(true);
            player.addColdness();
            System.out.println("\"是吗？！\"他眼睛亮起来。你点点头，转身离去，不知道这是对是错。");
        }
    }

    private void handleVariantEvidence() {
        System.out.println("\n\n\n");
        System.out.println("📼 ─── 证据件 ─── 📼");
        System.out.println("包裹里是一段视频芯片，揭露了公司的黑幕。收货人是记者。");
        System.out.println("就在此时，城警破门而入！\"交出芯片！\"");
        int choice = gameWindow.showChoiceDialog("证据件", "选择", new String[]{"交给城警", "交给记者"});
        if (choice < 0) choice = 1;
        if (choice == 0) {
            player.addEcho(GameConfig.ECHO_EVIDENCE_BETRAY);
            player.addColdness();
            player.reduceWantedLevel(1);
            player.reduceReputation(20);
            System.out.println("你交出芯片。城警点点头，离开了。记者在身后冷冷地看着你。");
        } else {
            player.addReputation(20);
            player.addHumanity();
            player.addWantedLevel(2);
            System.out.println("你把芯片塞进记者手中。\"跑！\"记者喊了一声，消失在门后。");
        }
    }

    private boolean handleLastDelivery() {
        EndingSystem gateSystem = new EndingSystem(player);
        System.out.println("\n\n\n");
        System.out.println("=======================================");
        System.out.println("  最后一单 —— 九道门");
        System.out.println("========================================");
        System.out.println("你来到城门前。面前是一条隧道，九道门并排而立。");
        System.out.println("每道门后都是你曾经的记忆——你敢面对吗？");

        int totalGates = gateSystem.getGateCount();

        for (int i = 0; i < totalGates; i++) {
            System.out.println("\n\n\n");
            String scene = gateSystem.enterGateSafe(i);
            System.out.println("[" + (i + 1) + "] " + scene);

            int action = gameWindow.showChoiceDialog("门后", "你选择...", new String[]{"正面面对", "绕过", "击碎"});
            if (action < 0) action = 1;
            switch (action) {
                case 0 -> {
                    gateSystem.faceGate(i);
                    System.out.println(gateSystem.getFaceMessage(i));
                }
                case 1 -> {
                    gateSystem.bypassGate(i);
                    System.out.println(gateSystem.getBypassMessage(i));
                }
                case 2 -> {
                    gateSystem.shatterGate(i);
                    System.out.println(gateSystem.getShatterMessage(i));
                }
            }
        }

        System.out.println("\n\n\n");
        System.out.println(gateSystem.enterGate(gateSystem.getGateCount()));

        String ending = gateSystem.generate();
        System.out.println(ending);

        String echoSummary = gateSystem.buildEchoSummary();
        if (!echoSummary.isEmpty()) {
            System.out.println(echoSummary);
        }

        System.out.println(gateSystem.getTerminalAttitude());

        Main.shutdownSequence(gameWindow);

        String endType = gateSystem.getEndingTypeName();
        Color endColor = gateSystem.getEndingColor();
        String subtitle = gateSystem.getEndingSubtitle();

        boolean exitChosen = gameWindow.showEndingDialog(endType, subtitle, endColor);
        if (exitChosen) {
            System.out.println("\n再见，" + player.getName() + "。夜之城不会忘记你。");
            Main.shutdownSequence(gameWindow);
            System.exit(0);
            return false;
        }
        restartGame();
        return true;
    }

    private void showTarget() {
        System.out.println("\n\n\n");
        System.out.println("========================================");
        System.out.println("  目标：攒够 " + GameConfig.WIN_TARGET + "€ 逃离夜之城");
        System.out.println("  当前进度：" + player.getMoney() + "€");
        int remaining = player.getTargetMoney() - player.getMoney();
        if (remaining > 0) {
            System.out.println("  还差：" + remaining + "€");
        } else {
            System.out.println("  ✅ 目标已达成！可以离开夜之城了。");
        }
        System.out.println("========================================");
    }

    private void heal() {
        int cost = GameConfig.HEAL_BASE_COST + player.getLevel() * GameConfig.HEAL_COST_PER_LEVEL;
        System.out.println("\n\n\n");
        System.out.println("治疗费用：" + cost + "€");
        int choice = gameWindow.showChoiceDialog("治疗", "是否治疗？(恢复" + GameConfig.HEAL_AMOUNT + "点血量)", new String[]{"治疗", "取消"});
        if (choice == 0) {
            if (player.getMoney() < cost) {
                System.out.println("钱不够！治疗需要 " + cost + "€");
            } else if (player.getHealth() >= player.getMaxHealth()) {
                System.out.println("血量已满，不需要治疗。");
            } else {
                player.costMoney(cost);
                player.restoreHealth(GameConfig.HEAL_AMOUNT);
                System.out.println("治疗成功！当前血量：" + player.getHealth() + "/" + player.getMaxHealth());
                triggerVisualUpdate();
            }
        }
    }

    private void openUpgrade() {
        System.out.println("\n\n\n");
        System.out.println("========================================");
        System.out.println("  升级装备");
        System.out.println("========================================");
        int choice = gameWindow.showChoiceDialog("升级", "选择升级项目", new String[]{"速度", "反追踪", "帮派威慑", "取消"});
        if (choice >= 0 && choice < 3) {
            upgradeSystem.upgrade(player, choice + 1);
            triggerVisualUpdate();
        }
    }
}

