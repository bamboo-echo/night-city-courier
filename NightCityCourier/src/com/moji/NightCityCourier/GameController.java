package com.moji.NightCityCourier;

import com.moji.NightCityCourier.core.Achievement;
import com.moji.NightCityCourier.core.Contacts;
import com.moji.NightCityCourier.core.DailyChallenge;
import com.moji.NightCityCourier.core.FactionSystem;
import com.moji.NightCityCourier.core.Memory;
import com.moji.NightCityCourier.core.StorylineSystem;
import com.moji.NightCityCourier.core.CourierProfile;
import com.moji.NightCityCourier.core.GameLog;
import com.moji.NightCityCourier.core.GameStats;
import com.moji.NightCityCourier.core.Inventory;
import com.moji.NightCityCourier.core.Item;
import com.moji.NightCityCourier.core.Rng;
import com.moji.NightCityCourier.core.RouteSystem;
import com.moji.NightCityCourier.core.SaveStore;
import com.moji.NightCityCourier.core.SkillTree;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;

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

    /** v2.0 成长层：背包/义体/技能/派系/成就/统计，与 Player 的旧存档完全解耦。 */
    private final CourierProfile profile;
    /** 种子化随机源；固定种子即可复现整局，用于挑战模式与失败复盘。 */
    private final Rng rng;
    /** 成长层存档仓库（v3 多槽位 + 原子写 + 备份轮转）。 */
    private final SaveStore profileStore;

    public GameController(Player player, MissionManager missionManager, UpgradeSystem upgradeSystem, EventSystem eventSystem, GameWindow gameWindow) {
        this(player, missionManager, upgradeSystem, eventSystem, gameWindow, System.nanoTime());
    }

    /** 可指定随机种子的构造函数，供「每日挑战」与测试使用。 */
    public GameController(Player player, MissionManager missionManager, UpgradeSystem upgradeSystem,
                          EventSystem eventSystem, GameWindow gameWindow, long seed) {
        this.player = player;
        this.missionManager = missionManager;
        this.upgradeSystem = upgradeSystem;
        this.eventSystem = eventSystem;
        this.gameWindow = gameWindow;
        this.echoPool = new EndingSystem.EchoPool();
        this.rng = new Rng(seed);
        this.profileStore = new SaveStore(Path.of(System.getProperty("user.home"), ".nightcity"));
        this.profile = loadProfile();
        // 事件系统与主控共享同一份世界记忆：事件里做的选择，会立刻影响后续事件的口吻与池子
        this.eventSystem.setMemory(this.profile.memory());
        initTerminalState();
    }

    public CourierProfile profile() {
        return profile;
    }

    private CourierProfile loadProfile() {
        CourierProfile loaded = new CourierProfile();
        profileStore.loadWithFallback("profile").ifPresent(doc -> {
            loaded.fromSaveEntries(doc.entries());
            GameLog.info("成长档案已加载（存档 v" + doc.version() + "）。");
        });
        return loaded;
    }

    /** 与 Player.save 并列调用，两种格式各存各的，互不影响。 */
    private void saveProfile() {
        if (profileStore.save("profile", profile.toSaveEntries())) {
            System.out.println("【确认】成长档案已保存（技能/义体/成就/统计）");
        }
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
                case 7 -> {
                    Player.save(player);
                    saveProfile();
                    flushAchievementToasts();
                }
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
        printCausalRecap();

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
            profile.weather().roll(rng);
            System.out.println("🌆 " + profile.weather().flavor());
            MissionManager.Mission mission = missionManager.generateMission(player);
            boolean contraband = mission.getType() == MissionManager.MissionType.BLACK_MARKET;
            int scaledReward = (int) Math.round(mission.getReward() * profile.rewardMultiplier(contraband));
            mission.setReward(scaledReward);
            System.out.println("类型：" + mission.getType().displayName);
            System.out.println("风险：" + mission.getRiskDisplay());
            System.out.println("报酬：" + mission.getReward() + "€" + rewardBreakdownHint(scaledReward));
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
            int healthBeforeDelivery = player.getHealth();
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
                profile.evaluateAchievements(player.getMoney(), player.getHealth(),
                        player.getWantedLevel(), player.getTotalTasks());
                flushAchievementToasts();
                maybeAdvanceStoryline();
                return;
            } else {
                player.addTotalTasks();
                int rawLoss = (int) (mission.getRisk() * 20);
                double reduction = profile.damageReduction(player.getHealth());
                int healthLoss = Math.max(1, (int) Math.round(rawLoss * (1.0 - reduction)));
                player.loseHealth(healthLoss);
                System.out.println("❌ 任务失败！血量 -" + healthLoss
                        + (reduction > 0 ? "（装备减伤 " + Math.round(reduction * 100) + "%）" : ""));
                System.out.println("─────────────────────────────");
                triggerVisualUpdate();
                int penalty = 0;
                if (mission.getFailPenalty() > 0) {
                    penalty = Math.max(GameConfig.FAIL_PENALTY_MIN, Math.min(mission.getFailPenalty(), player.getMoney()));
                    player.costMoney(penalty);
                    System.out.println("❌ 紧急件超时！被扣 " + penalty + "€");
                } else {
                    System.out.println("❌ 任务失败！一无所获。");
                }
                profile.recordFailure(penalty, healthLoss, contraband);
                flushAchievementToasts();
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
        int variantTriggerStep = hasVariant ? 2 + rng.nextInt(Math.max(1, mission.getTimeNeed() - 2)) : Integer.MAX_VALUE;
        while (step <= mission.getTimeNeed()) {
            if (!earlyVariantShown && step >= variantTriggerStep) {
                handleMissionVariantEarly(mission.getVariant());
                earlyVariantShown = true;
            }
            if (!reputationHelpChecked && player.getReputation() >= GameConfig.REPUTATION_VETERAN && rng.nextDouble() < 0.05) {
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
                int skip = rng.nextInt(2) + 1;
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
                System.out.println(scenes[rng.nextInt(scenes.length)] + "扣10血，你省下了 " + skip + " 步路程。");
                step += skip;
                if (step >= mission.getTimeNeed()) break;
            }

            double baseChance = 0.4 + mission.getRisk() * GameConfig.ENCOUNTER_RISK_FACTOR;
            // v2.0：天气/时段与干扰器共同决定事件密度
            baseChance *= profile.encounterMultiplier();
            if (player.getPendingScrutiny() > 0) {
                baseChance *= 1.5;
            }
            baseChance = Math.min(baseChance, GameConfig.ENCOUNTER_CHANCE_CAP);
            if (rng.nextDouble() < baseChance) {
                if (player.hasGambleRevenge() && rng.nextDouble() < 0.3) {
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
                if (rng.nextDouble() < player.getAvoidPolice() * GameConfig.AVOID_EVENT_CHANCE_PER_LEVEL) {
                    System.out.println("🛡️ 反追踪装置提前探测到警方扫描，你绕道避开了。");
                    return 1;
                }
                boolean eventSuccess = eventSystem.threatEvent(player, "POLICE");
                if (!eventSuccess) return -999;
                return stepDelta;
            }
            case 1 -> {
                if (player.hasJoinedGang() && rng.nextDouble() < 0.5) {
                    System.out.println("🛡️ 帮派成员看到你的徽章，让开了路。");
                    return 1;
                }
                if (rng.nextDouble() < player.getAvoidGang() * GameConfig.AVOID_EVENT_CHANCE_PER_LEVEL) {
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
            case 9 -> {
                // 黑冰破解器 / 网潜者：直接吃掉无人机的扫描信号
                if (profile.hasBlackIce() || profile.skills().isNetrunner()) {
                    System.out.println("🛸 无人机的扫描被你截下来，它转身去追自己的影子。");
                    profile.stats().inc("stat.droneBypass");
                    return stepDelta;
                }
                eventSystem.droneScanEvent(player);
                return stepDelta;
            }
            case 10 -> {
                int docResult = eventSystem.ripperdocEvent(player);
                if (docResult == 2) {
                    profile.skills().addPoints(1);
                }
                return stepDelta;
            }
            case 12 -> {
                eventSystem.streetPerformerEvent(player);
                profile.recordKindAct();
                return stepDelta;
            }
            case 13 -> {
                eventSystem.pickpocketEvent(player);
                return stepDelta;
            }
            case 14 -> {
                eventSystem.exLoverEvent(player);
                return stepDelta;
            }
            case 16 -> {
                return npcEncounter(Contacts.Who.OLD_K);
            }
            case 17 -> {
                return npcEncounter(Contacts.Who.XIAOMAN);
            }
            case 18 -> {
                return npcEncounter(Contacts.Who.GRAY);
            }
            case 19 -> {
                return npcEncounter(Contacts.Who.WEI);
            }
            case 20 -> {
                return npcEncounter(Contacts.Who.DOC);
            }
            case 15 -> {
                boolean joined = eventSystem.corpoOfferEvent(player);
                if (joined) {
                    profile.recordCruelAct();
                } else {
                    profile.recordKindAct();
                }
                return stepDelta;
            }
            case 11 -> {
                int radioResult = eventSystem.radioEvent(player);
                if (radioResult == 1) {
                    mission.reduceRisk(0.3);
                    System.out.println("【系统】你记下了巡逻表，本次任务风险下降 0.3 星。");
                } else if (radioResult == 2) {
                    profile.factions().shift(com.moji.NightCityCourier.core.FactionSystem.Faction.SCAV, 4);
                }
                return stepDelta;
            }
            default -> {
                System.out.println("未知事件类型，继续前进。");
            }
        }
        return stepDelta;
    }

    private int weightedEventType() {
        int[] base = {
            GameConfig.POLICE_WEIGHT, GameConfig.GANG_WEIGHT, GameConfig.BREAKDOWN_WEIGHT,
            GameConfig.RIVAL_WEIGHT, GameConfig.RESCUE_WEIGHT,
            GameConfig.GHOST_WEIGHT, GameConfig.GAMBLE_WEIGHT, GameConfig.SCAN_WEIGHT,
            GameConfig.SCAVENGER_WEIGHT,
            GameConfig.DRONE_WEIGHT, GameConfig.RIPPERDOC_WEIGHT, GameConfig.RADIO_WEIGHT,
            GameConfig.PERFORMER_WEIGHT, GameConfig.PICKPOCKET_WEIGHT,
            GameConfig.EXLOVER_WEIGHT, GameConfig.CORPO_WEIGHT
        };
        String[] poolKeys = {"POLICE", "GANG", "BREAKDOWN", "RIVAL", "RESCUE", "GHOST", "GAMBLE",
                "SCAN", "SCAVENGER", "DRONE", "RIPPERDOC", "RADIO", "PERFORMER",
                "PICKPOCKET", "EXLOVER", "CORPO"};

        // v2.2：名声改写事件池。不是改数值，而是改「你会遇到什么」——
        // 冷血的人更容易撞上公司招聘与寻仇，圣人更容易被求助，屋顶的人更容易遇到拾荒者。
        for (int i = 0; i < base.length; i++) {
            base[i] = Math.max(0, base[i] + profile.memory().weightShift(poolKeys[i]));
        }

        // v2.3：具名人物专属遭遇。权重来自关系值——关系越极端，越容易在路上碰见。
        Contacts.Who[] whos = Contacts.Who.values();
        int[] weights = new int[base.length + whos.length];
        System.arraycopy(base, 0, weights, 0, base.length);
        for (int i = 0; i < whos.length; i++) {
            weights[base.length + i] = profile.contacts().encounterWeight(whos[i]);
        }

        int total = 0;
        for (int w : weights) total += w;

        if (total <= 0) {
            System.out.println("[警告] 事件权重总和为 " + total + "，使用默认事件");
            return 0;
        }
        int roll = rng.nextInt(total);
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
            case ORGAN -> handleVariantOrgan();
            case DATA -> handleVariantData();
            case PET -> handleVariantPet();
            case WEDDING -> handleVariantWedding();
            case BOMB -> handleVariantBomb();
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
                if (rng.nextDouble() < base) {
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

            int value = 200 + rng.nextInt(301);
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
            if (rng.nextDouble() < GameConfig.RACE_WIN_BASE + player.getSpeed() * GameConfig.RACE_WIN_PER_SPEED) {
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

    // ══════════════════════════════════════════════════════════════
    //  v2.0 新增：成长面板 / 路线 / 商店 / 成就
    // ══════════════════════════════════════════════════════════════

    /**
     * 成长中心。原版只有一个「花钱加属性」的弹窗，现在扩展为四个模块，
     * 让钱与经验都有多条互相竞争的用法。
     */
    private void openUpgrade() {
        while (true) {
            System.out.println("\n\n\n");
            System.out.println("========================================");
            System.out.println("  成长中心   现金 " + player.getMoney() + "€  技能点 " + profile.skills().points()
                    + "  等级 " + profile.level());
            System.out.println("========================================");
            int choice = gameWindow.showChoiceDialog("成长",
                    "要处理哪个模块？",
                    new String[]{"属性升级", "技能树", "黑市商店", "义体与背包", "离开"});
            switch (choice) {
                case 0 -> openAttributeUpgrade();
                case 1 -> openSkillTree();
                case 2 -> openShop();
                case 3 -> openInventory();
                default -> {
                    return;
                }
            }
        }
    }

    /** 原版升级逻辑，保留不变，只是把花费记进档案。 */
    private void openAttributeUpgrade() {
        System.out.println("\n\n\n");
        System.out.println("========================================");
        System.out.println("  升级装备");
        System.out.println("========================================");
        int choice = gameWindow.showChoiceDialog("升级", "选择升级项目", new String[]{"速度", "反追踪", "帮派威慑", "取消"});
        if (choice >= 0 && choice < 3) {
            int before = player.getMoney();
            upgradeSystem.upgrade(player, choice + 1);
            profile.recordSpend(Math.max(0, before - player.getMoney()));
            triggerVisualUpdate();
        }
    }

    /** 技能树：一次点一级，点完刷新成就。 */
    private void openSkillTree() {
        SkillTree.Skill[] all = SkillTree.Skill.values();
        String[] options = new String[all.length + 1];
        for (int i = 0; i < all.length; i++) {
            SkillTree.Skill s = all[i];
            options[i] = s.displayName() + " " + profile.skills().rank(s) + "/" + s.maxRank()
                    + " · " + s.description() + "（" + s.costPerRank() + " 点）";
        }
        options[all.length] = "返回";
        System.out.println("\n\n\n");
        System.out.println("【技能点】" + profile.skills().points() + " 点可用");
        int pick = gameWindow.showChoiceDialog("技能树", "选择要提升的技能", options);
        if (pick < 0 || pick >= all.length) {
            return;
        }
        SkillTree.Skill skill = all[pick];
        if (profile.skills().spendPoint(skill)) {
            System.out.println("✅ " + skill.displayName() + " 提升至 " + profile.skills().rank(skill) + " 级。");
        } else {
            System.out.println("技能点不足，或该技能已满级。");
        }
        profile.evaluateAchievements(player.getMoney(), player.getHealth(),
                player.getWantedLevel(), player.getTotalTasks());
        flushAchievementToasts();
    }

    /** 黑市商店：消耗品与义体同一个列表，义体占槽。 */
    private void openShop() {
        List<Item> stock = Item.shopStock();
        String[] options = new String[stock.size() + 1];
        for (int i = 0; i < stock.size(); i++) {
            Item item = stock.get(i);
            String owned = item.isRig()
                    ? (profile.inventory().hasRig(item) ? "【已装】" : "")
                    : "【持有 " + profile.inventory().count(item) + "】";
            options[i] = item.displayName() + " " + item.price() + "€ " + owned + " — " + item.description();
        }
        options[stock.size()] = "不买了";
        System.out.println("\n\n\n");
        System.out.println("【黑市】现金 " + player.getMoney() + "€   义体槽 "
                + profile.inventory().rigs().size() + "/" + Inventory.MAX_RIG_SLOTS);
        int pick = gameWindow.showChoiceDialog("黑市", "买点什么？", options);
        if (pick < 0 || pick >= stock.size()) {
            return;
        }
        Item item = stock.get(pick);
        if (player.getMoney() < item.price()) {
            System.out.println("钱不够。这件要 " + item.price() + "€，你只有 " + player.getMoney() + "€。");
            return;
        }
        boolean ok;
        if (item.isRig()) {
            ok = profile.inventory().install(item);
            if (!ok) {
                System.out.println("义体槽已满（" + Inventory.MAX_RIG_SLOTS + " 个）。先卸下一件再装。");
                return;
            }
        } else {
            ok = profile.inventory().add(item);
            if (!ok) {
                System.out.println("背包已满（" + Inventory.MAX_STACKS + " 格）。先用掉一些。");
                return;
            }
        }
        player.costMoney(item.price());
        profile.recordSpend(item.price());
        System.out.println("✅ 购入 " + item.displayName() + "。");
        profile.evaluateAchievements(player.getMoney(), player.getHealth(),
                player.getWantedLevel(), player.getTotalTasks());
        flushAchievementToasts();
        triggerVisualUpdate();
    }

    /** 义体与背包：查看已装义体、使用消耗品、卸下义体。 */
    private void openInventory() {
        while (true) {
            System.out.println("\n\n\n");
            System.out.println("【义体槽】" + profile.inventory().rigs().size() + "/" + Inventory.MAX_RIG_SLOTS);
            if (profile.inventory().rigs().isEmpty()) {
                System.out.println("  （空）");
            } else {
                for (Item rig : profile.inventory().rigs()) {
                    System.out.println("  · " + rig.displayName() + " — " + rig.description());
                }
            }
            Map<Item, Integer> consumables = profile.inventory().consumableView();
            System.out.println("【消耗品】");
            if (consumables.isEmpty()) {
                System.out.println("  （空）");
            } else {
                for (Map.Entry<Item, Integer> e : consumables.entrySet()) {
                    System.out.println("  · " + e.getKey().displayName() + " ×" + e.getValue()
                            + " — " + e.getKey().description());
                }
            }

            List<String> options = new ArrayList<>();
            List<Runnable> actions = new ArrayList<>();
            for (Map.Entry<Item, Integer> e : consumables.entrySet()) {
                Item item = e.getKey();
                options.add("使用 " + item.displayName());
                actions.add(() -> useConsumable(item));
            }
            for (Item rig : profile.inventory().rigs()) {
                options.add("卸下 " + rig.displayName());
                actions.add(() -> {
                    profile.inventory().uninstall(rig);
                    System.out.println("已卸下 " + rig.displayName() + "（不退款）。");
                });
            }
            options.add("返回");
            actions.add(() -> {
            });

            int pick = gameWindow.showChoiceDialog("背包", "选择操作", options.toArray(new String[0]));
            if (pick < 0 || pick >= actions.size() - 1) {
                return;
            }
            actions.get(pick).run();
        }
    }

    /** 消耗品生效。状态类效果（护盾/干扰）写入 profile 供主循环读取。 */
    private void useConsumable(Item item) {
        if (!profile.inventory().remove(item)) {
            return;
        }
        switch (item.effect()) {
            case HEAL -> {
                player.restoreHealth(item.magnitude());
                System.out.println("💉 恢复 " + item.magnitude() + " 点生命，当前 "
                        + player.getHealth() + "/" + player.getMaxHealth());
            }
            case CLEAR_WANTED -> {
                player.reduceWantedLevel(item.magnitude());
                System.out.println("🪪 通缉等级降至 " + player.getWantedLevel() + " 级。");
            }
            case SHIELD -> {
                profile.stats().inc("stat.shieldCharges");
                System.out.println("🥃 身体烧起来了。本单失败时会被扛过去一次。");
            }
            case JAM -> {
                profile.stats().inc("stat.jammerActive");
                System.out.println("📡 干扰器已开启，本单遭遇率减半。");
            }
            case SMOKE -> {
                profile.stats().inc("stat.smokeCharges");
                System.out.println("💨 烟雾弹上膛，下次威胁必定脱身。");
            }
            default -> System.out.println("这件装备是被动的，装上即生效。");
        }
        triggerVisualUpdate();
    }

    /** 三选一路线。返回玩家选择；取消时由导航代选第一条。 */
    private RouteSystem.RouteOption chooseRoute() {
        List<RouteSystem.RouteOption> routes = RouteSystem.offer(rng);
        String[] options = new String[routes.size() + 1];
        for (int i = 0; i < routes.size(); i++) {
            RouteSystem.RouteOption r = routes.get(i);
            options[i] = r.name() + "（" + r.tag() + "） " + r.hint();
        }
        options[routes.size()] = "交给导航决定";
        int pick = gameWindow.showChoiceDialog("路线", "走哪条路？", options);
        RouteSystem.RouteOption chosen;
        if (pick < 0 || pick >= routes.size()) {
            chosen = routes.get(0);
            System.out.println(">> 导航替你选了：" + chosen.name());
        } else {
            chosen = routes.get(pick);
            System.out.println(">> 你选择了：" + chosen.name());
        }
        System.out.println("   " + chosen.description());
        profile.recordRoute(chosen.name());
        return chosen;
    }

    /** 报酬上浮/下调的透明说明，避免玩家怀疑「钱怎么少了」。 */
    private String rewardBreakdownHint(int scaledReward) {
        double mul = profile.rewardMultiplier(false);
        if (mul > 1.02) {
            return "（天气与技能加成 ×" + String.format("%.2f", mul) + "）";
        }
        if (mul < 0.98) {
            return "（天气折价 ×" + String.format("%.2f", mul) + "）";
        }
        return "";
    }

    /** 档案面板：把成就、统计、派系、义体一次摊开。 */
    private void showProfilePanel() {
        gameWindow.showStats(player);
        System.out.println("\n\n\n");
        System.out.println("═══════════ 跑单档案 ═══════════");
        System.out.println("等级 " + profile.level() + "   经验 " + profile.xp() + "/" + profile.xpToNext()
                + "   连胜 " + profile.streak() + "（最高 " + profile.bestStreak() + "）");
        System.out.println("成功率 " + Math.round(profile.stats().successRate() * 100) + "%"
                + "   净收入 " + profile.stats().netIncome() + "€"
                + "   累计花费 " + profile.totalSpent() + "€");
        System.out.println("街面名声：" + profile.memory().epithet() + " —— "
                + profile.memory().archetype().streetTalk());
        System.out.println("善意 " + profile.memory().totalKind() + " / 冷血 " + profile.memory().totalCruel()
                + "（" + profile.memory().totalFacts() + " 条记录）");
        System.out.println("关系网：" + profile.contacts().summary());
        System.out.println("派系：" + profile.factions().summary());
        System.out.println("义体：" + (profile.inventory().rigs().isEmpty()
                ? "无"
                : profile.inventory().rigs().stream().map(Item::displayName).toList()));
        System.out.println("成就：" + profile.achievements().unlockedCount() + "/"
                + profile.achievements().totalCount());
        System.out.println("─────────────────────────────");
        int choice = gameWindow.showChoiceDialog("档案", "查看详情？",
                new String[]{"成就列表", "统计明细", "人物关系", "支线进度", "导出 / 导入档案", "今日挑战", "返回"});
        switch (choice) {
            case 0 -> {
                for (String line : profile.achievements().renderList()) {
                    System.out.println(line);
                }
            }
            case 1 -> {
                for (Map.Entry<String, Long> e : profile.stats().nonZeroView().entrySet()) {
                    System.out.println("  " + statLabel(e.getKey()) + "：" + e.getValue());
                }
            }
            case 2 -> {
                for (Contacts.Who w : Contacts.Who.values()) {
                    if (!profile.contacts().hasMet(w)) {
                        System.out.println("  ? " + w.displayName() + " —— 尚未相识（" + w.intro() + "）");
                        continue;
                    }
                    Contacts.Bond b = profile.contacts().bond(w);
                    System.out.println("  " + w.displayName() + "  " + b.stage().displayName()
                            + "（" + b.relation() + "）  见面 " + b.meetings() + " 次");
                    for (Map.Entry<Memory.Fact, Integer> e : b.sharedMemories().entrySet()) {
                        System.out.println("      · " + e.getKey().label() + " ×" + e.getValue());
                    }
                }
            }
            case 3 -> {
                System.out.println("支线：" + profile.storylines().summary());
                System.out.println("已完成 " + profile.storylines().totalBeats() + "/"
                        + profile.storylines().totalPossible() + " 拍。");
            }
            case 4 -> openArchiveMenu();
            case 5 -> showDailyChallenge();
            default -> {
            }
        }
    }

    private String statLabel(String key) {
        return switch (key) {
            case GameStats.DELIVERIES -> "完成派送";
            case GameStats.FAILURES -> "失败次数";
            case GameStats.MONEY_EARNED -> "累计收入";
            case GameStats.MONEY_LOST -> "累计损失";
            case GameStats.BEST_HAUL -> "单笔最高";
            case GameStats.DAMAGE_TAKEN -> "累计受伤";
            case GameStats.EVENTS_SEEN -> "遭遇事件";
            case GameStats.STEPS_TRAVELED -> "赶路步数";
            case GameStats.BRIBERY -> "行贿次数";
            case GameStats.BLACK_MARKET -> "黑市件";
            case GameStats.GAMBLES -> "赌局";
            case GameStats.KIND_ACTS -> "善意选择";
            case GameStats.CRUEL_ACTS -> "冷血选择";
            case GameStats.ROUTES_TAKEN -> "走过的路线";
            case "stat.escapes" -> "成功逃脱";
            case "stat.nightDeliveries" -> "深夜派送";
            case "stat.stormDeliveries" -> "恶劣天气派送";
            default -> key.replace("stat.", "");
        };
    }

    /** 把新解锁的成就播报出来并清空队列。 */
    private void flushAchievementToasts() {
        for (Achievement a : profile.achievements().drainToasts()) {
            System.out.println("🏆 成就解锁：" + a.title() + " —— " + a.description());
        }
    }

    // ══════════════════════════════════════════════════════════════

    // ══════════════════════════════════════════════════════════════
    //  v2.0 变体扩展 · 支线剧情 · 档案导入导出 · 每日挑战
    // ══════════════════════════════════════════════════════════════

    /** 器官件：保质期倒计时，三种处理方式对应三种代价。 */
    private void handleVariantOrgan() {
        System.out.println("\n🫀 器官件");
        System.out.println("冷藏箱的指示灯开始闪橙。箱子很轻，但一直在微微发热。");
        int choice = gameWindow.showChoiceDialog("器官件",
                "箱子里的东西还有 40 分钟保质期。",
                new String[]{"按规矩送到", "中途卖给黑市", "撬开看一眼"});
        if (choice == 0) {
            player.addReputation(4);
            profile.recordKindAct();
            System.out.println(">> 你没有打开它。有些事不知道比较轻松。");
        } else if (choice == 1) {
            player.addMoney(650);
            player.addWantedLevel(1);
            profile.recordCruelAct();
            recordFact(Memory.Fact.SOLD_ORGANS);
            profile.factions().shift(FactionSystem.Faction.GANG, 10);
            System.out.println(">> 你在后巷完成了交易。钱很厚，手感很凉。");
            System.out.println("【系统】+650€，通缉 +1，帮派声望 +10，冷血 +1");
        } else {
            player.loseHealth(8);
            System.out.println(">> 你撬开一条缝。里面的东西看了你一眼。");
            System.out.println(">> 你合上盖子，手在抖。");
        }
    }

    /** 数据件：看不看，是不同的活法。 */
    private void handleVariantData() {
        System.out.println("\n💾 数据件");
        System.out.println("这枚芯片比看上去重。没有加密，也没有标签。");
        int choice = gameWindow.showChoiceDialog("数据件",
                "客户只说了三个字：别插网。",
                new String[]{"原样交付", "复制一份", "格式化掉"});
        if (choice == 0) {
            player.addReputation(3);
            System.out.println(">> 你把它封进防静电袋，一路没再碰。");
        } else if (choice == 1) {
            player.addWantedLevel(1);
            profile.stats().inc("stat.dataCopied");
            recordFact(Memory.Fact.COPIED_DATA);
            System.out.println(">> 你把它插进备用终端，拷了三秒就拔出来。");
            System.out.println(">> 屏幕上闪过一个名字：你自己的。");
            System.out.println("【系统】你知道了某个人的底细（后续订单情报↑），但通缉 +1。");
        } else {
            player.costMoney(120);
            System.out.println(">> 你把它消了磁。客户收货时会知道，但没人能拿它威胁你。");
            System.out.println("【系统】赔付 120€，通缉未上升。");
        }
    }

    /** 活体宠物件：最不像货物的一件货物。 */
    private void handleVariantPet() {
        System.out.println("\n🐈 活体宠物件");
        System.out.println("箱子里传来抓挠声。透气孔后面是一双很小的眼睛。");
        int choice = gameWindow.showChoiceDialog("活体宠物件",
                "客户要求「全程恒温，不得开箱」。",
                new String[]{"按规矩送到", "放它走", "带回家"});
        if (choice == 0) {
            player.addReputation(2);
            System.out.println(">> 你一路没开箱。交货时，客户连声道谢。");
        } else if (choice == 1) {
            player.costMoney(150);
            player.addReputation(6);
            profile.recordKindAct();
            recordFact(Memory.Fact.FREED_PET);
            System.out.println(">> 你在一个有大片屋顶的地方停下，打开箱子。");
            System.out.println(">> 它犹豫了两秒，然后跑掉了。");
            System.out.println("【系统】赔付 150€，声望 +6，善意 +1");
        } else {
            profile.recordKindAct();
            profile.stats().inc("stat.petsKept");
            recordFact(Memory.Fact.KEPT_PET);
            System.out.println(">> 你把它带回了住处。它现在睡在你的头盔里。");
            System.out.println("【系统】你多了一个室友。它不会付房租。");
        }
    }

    /** 婚礼件：整座城里最不赛博朋克的一单。 */
    private void handleVariantWedding() {
        System.out.println("\n💍 婚礼件");
        System.out.println("一整套礼服，附一张手写请柬。字很好看，墨是手磨的。");
        int choice = gameWindow.showChoiceDialog("婚礼件",
                "请柬上写着：「如果你愿意，也来坐坐。」",
                new String[]{"送到就走", "顺路参加", "把礼服卖掉"});
        if (choice == 0) {
            player.addReputation(2);
            System.out.println(">> 你把礼服交到门口，没进去。里面在放一首很老的歌。");
        } else if (choice == 1) {
            player.costMoney(150);
            player.addReputation(8);
            profile.recordKindAct();
            profile.stats().inc("stat.weddings");
            recordFact(Memory.Fact.JOINED_WEDDING);
            System.out.println(">> 你坐在最后一排，随了 150€ 的礼。");
            System.out.println(">> 没有人问你做什么工作。那一刻你只是个客人。");
            System.out.println("【系统】声望 +8，善意 +1");
        } else {
            player.addMoney(260);
            player.addReputation(-5);
            profile.recordCruelAct();
            recordFact(Memory.Fact.PAWNED_GIFT);
            System.out.println(">> 礼服卖了 260€。你没看那张请柬第二眼。");
        }
    }

    /** 定时件：倒计时把「赶路」变成真正的压力。 */
    private void handleVariantBomb() {
        System.out.println("\n⏱️ 定时件");
        System.out.println("箱子侧面有一块红色倒计时。发货人没提过这件事。");
        int choice = gameWindow.showChoiceDialog("定时件",
                "剩余 00:09:41。还在走。",
                new String[]{"全速冲过去（扣血）", "找地方拆", "直接丢掉"});
        if (choice == 0) {
            player.loseHealth(15);
            profile.recordRoute("全速冲线");
            System.out.println(">> 你把车开到不该开的速度。倒计时在 00:00:12 停下。");
            System.out.println("【系统】血量 -15，货物送达。");
        } else if (choice == 1) {
            if (rng.chance(0.5)) {
                player.addMoney(300);
                profile.stats().inc("stat.bombsDefused");
                recordFact(Memory.Fact.DEFUSED_BOMB);
                System.out.println(">> 你找到一根不该接的线，剪断了。倒计时灭了。");
                System.out.println("【系统】拆解成功，+300€");
            } else {
                player.loseHealth(28);
                System.out.println(">> 你剪错了。");
                System.out.println("【系统】血量 -28，货没了，但你还在。");
            }
        } else {
            player.costMoney(200);
            player.addReputation(-4);
            profile.recordCruelAct();
            recordFact(Memory.Fact.DUMPED_BOMB);
            System.out.println(">> 你把箱子扔进了排水渠，然后开出去三个街区才敢停。");
            System.out.println("【系统】赔付 200€，声望 -4。");
        }
    }

    /**
     * 支线推进：每完成一单有约 28% 概率推进一拍。
     * 玩家无法主动刷剧情，只能一直在城里跑，等它找上来。
     */
    /**
     * 支线推进的两条通道：
     * <ol>
     *   <li><b>行为驱动</b>：{@link #recordFact(Memory.Fact)} 命中某条线的主题时，立即推进；</li>
     *   <li><b>自然推进</b>：每单结束后 12% 概率推进一条未完成的线，避免玩家「做好事不成就卡住」。</li>
     * </ol>
     * 前者是因果，后者是余地——但主导权在玩家手里。
     */
    private void maybeAdvanceStoryline() {
        if (profile.storylines().allComplete() || !rng.chance(0.12)) {
            return;
        }
        advanceStoryline(profile.storylines().pickIncomplete(rng));
    }

    /** 记录一条世界事实，并立刻检查它是否推动某条支线。 */
    private void recordFact(Memory.Fact fact) {
        if (fact == null) {
            return;
        }
        profile.memory().record(fact);
        // 同一件事，对不同的人意义不同：卖同行让格雷欣赏你，却让老K 恨你。
        List<Contacts.Who> touched = profile.contacts().distribute(fact);
        if (!touched.isEmpty()) {
            StringBuilder who = new StringBuilder();
            for (Contacts.Who w : touched) {
                who.append(w.displayName()).append('·')
                        .append(profile.contacts().stage(w).displayName()).append("  ");
            }
            System.out.println("【关系】" + who.toString().trim());
        }
        StorylineSystem.Line line = StorylineSystem.lineFor(fact);
        if (line != null) {
            advanceStoryline(line);
        }
        profile.evaluateAchievements(player.getMoney(), player.getHealth(),
                player.getWantedLevel(), player.getTotalTasks());
        flushAchievementToasts();
    }

    /** 具名人物遭遇入口：先记一次见面，再按关系值演出对应桥段。 */
    private int npcEncounter(Contacts.Who who) {
        profile.contacts().meet(who);
        eventSystem.npcEncounter(player, who, profile.contacts().relation(who));
        return 1;
    }

    /** 推进指定支线并播报这一拍。 */
    private void advanceStoryline(StorylineSystem.Line line) {
        if (line == null || profile.storylines().isComplete(line)) {
            return;
        }
        StorylineSystem.Beat beat = profile.storylines().advance(line);
        if (beat == null) {
            return;
        }
        System.out.println();
        System.out.println("┄┄┄ 支线 · " + beat.line() + " " + beat.index() + "/4 · " + beat.title() + " ┄┄┄");
        System.out.println(beat.text());
        if (beat.money() != 0) {
            player.addMoney(beat.money());
            System.out.println("【系统】" + (beat.money() > 0 ? "+" : "") + beat.money() + "€");
        }
        if (beat.skillPoints() > 0) {
            profile.skills().addPoints(beat.skillPoints());
            System.out.println("【系统】获得 " + beat.skillPoints() + " 点技能点");
        }
        if (beat.faction() != null && beat.rep() != 0) {
            profile.factions().shift(beat.faction(), beat.rep());
            System.out.println("【系统】" + beat.faction().displayName() + " 声望 "
                    + (beat.rep() > 0 ? "+" : "") + beat.rep());
        }
    }

    /** 档案导出 / 导入。导出到 ~/.nightcity/profile.dat.export-<时间戳>。 */
    private void openArchiveMenu() {
        int choice = gameWindow.showChoiceDialog("档案", "选择操作",
                new String[]{"导出当前档案", "导入最新导出", "返回"});
        if (choice == 0) {
            String suffix = "export-" + System.currentTimeMillis();
            if (profileStore.snapshot("profile", suffix)) {
                System.out.println("✅ 已导出：" + profileStore.root().resolve("profile.dat." + suffix));
            } else {
                System.out.println("导出失败：没有可导出的档案（先存一次档）。");
            }
        } else if (choice == 1) {
            importLatestArchive();
        }
    }

    private void importLatestArchive() {
        try (var stream = Files.list(profileStore.root())) {
            Path latest = stream
                    .filter(p -> p.getFileName().toString().startsWith("profile.dat.export-"))
                    .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                    .orElse(null);
            if (latest == null) {
                System.out.println("目录里没有导出文件。");
                return;
            }
            var doc = profileStore.loadFrom(latest);
            if (doc.isEmpty()) {
                System.out.println("导入失败：文件不可读或已损坏。");
                return;
            }
            profile.fromSaveEntries(doc.get().entries());
            System.out.println("✅ 已导入：" + latest.getFileName());
            System.out.println("   等级 " + profile.level() + "，成就 " + profile.achievements().unlockedCount()
                    + "/" + profile.achievements().totalCount());
        } catch (IOException e) {
            System.out.println("导入失败：" + e.getMessage());
        }
    }

    /**
     * 结局时念出「世界记得你做过什么」。
     *
     * <p>这是因果链的收口：前面所有选择都写进 {@link Memory}，此处按发生顺序回述，
     * 再给出画像判词。玩家看到的不是一句「你的结局是 X」，而是一条能追溯的路。
     */
    private void printCausalRecap() {
        Memory memory = profile.memory();
        if (memory.totalFacts() == 0) {
            System.out.println("\n─── 世界几乎没有注意到你 ───");
            System.out.println("你在夜之城跑过，但没有留下能被记住的痕迹。");
            return;
        }
        System.out.println("\n─── 世界记得你做过这些 ───");
        for (String line : memory.recap(8)) {
            System.out.println(line);
        }
        System.out.println();
        List<String> bonds = profile.contacts().recap();
        if (!bonds.isEmpty()) {
            System.out.println("\n─── 有人记得你 ───");
            for (String b : bonds) {
                System.out.println(b);
            }
        }
        System.out.println();
        System.out.println("街面上，他们叫你「" + memory.epithet() + "」。");
        System.out.println(memory.archetype().endingLine());
        System.out.println("善意 " + memory.totalKind() + " 次，冷血 " + memory.totalCruel() + " 次，天平 " + memory.moralScore() + "。");
    }

    /** 今日挑战信息与七天预告。 */
    private void showDailyChallenge() {
        DailyChallenge.Spec today = DailyChallenge.today();
        System.out.println("\n" + today.headline());
        System.out.println("  种子：" + today.seed());
        System.out.println("  规则：报酬 ×" + String.format("%.2f", today.rewardMul())
                + "  风险 ×" + String.format("%.2f", today.riskMul()));
        if (today.isForcedWeather()) {
            System.out.println("  强制天气：" + today.forced().displayName());
        }
        long score = DailyChallenge.score(player.getMoney(), profile.stats().get(GameStats.DELIVERIES),
                profile.stats().get(GameStats.FAILURES), profile.achievements().unlockedCount());
        System.out.println("  当前评分：" + score + "（" + DailyChallenge.grade(score) + "）");
        System.out.println("\n未来七天：");
        for (DailyChallenge.Spec s : DailyChallenge.upcoming()) {
            System.out.println("  " + s.dateKey() + "  " + s.ruleName() + " —— " + s.ruleDesc());
        }
    }

}
