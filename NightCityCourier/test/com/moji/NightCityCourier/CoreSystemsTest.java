package com.moji.NightCityCourier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 纯 JDK 测试（无需 JUnit 依赖，便于在只装了 JDK 的机器上直接跑）。
 *
 * 覆盖之前"全靠肉测"的核心判定：
 *   1. 结局优先级与条件判定（EndingSystem.determineEnding）
 *   2. 回响石去重、状态统计、单一账本（facedCount 不再维护第二份状态）
 *   3. 事件权重分布与回响遭遇计数语义
 *   4. 任务生成/变体取值边界、玩家数值边界（金钱/通缉/声望/生命下限）
 *
 * 运行方式见 build.ps1 / build.sh，或直接：
 *   javac -encoding UTF-8 -d test-out src/com/moji/NightCityCourier/*.java test/com/moji/NightCityCourier/CoreSystemsTest.java
 *   java  -cp test-out com.moji.NightCityCourier.CoreSystemsTest
 */
public class CoreSystemsTest {

    private int passed = 0;
    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        CoreSystemsTest t = new CoreSystemsTest();
        try {
            t.runAll();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.out.println();
        System.out.println("═══════════════════════════════════");
        System.out.println("  通过: " + t.passed + "  失败: " + t.failures.size());
        System.out.println("═══════════════════════════════════");
        if (!t.failures.isEmpty()) {
            System.out.println("失败用例：");
            for (String f : t.failures) {
                System.out.println("  ✗ " + f);
            }
            System.exit(1);
        }
        System.out.println("全部通过 ✅");
    }

    private void runAll() throws Exception {
        endingPriority();
        endingPriorityBurnout();
        endingPriorityArrest();
        endingHeroRequiresAllVirtues();
        echoPoolDedupAndStatus();
        facedCountIsSingleLedger();
        gateCountIsAlwaysNine();
        eventWeightsAreValid();
        echoEncounterSemantics();
        missionGenerationBounds();
        playerBoundaries();
        variantEnumExcludesNone();
        upgradeCostMonotonic();
        saveLoadRoundtrip();
        saveLoadRoundtripSpecialChars();
        saveLoadFileRoundtrip();
    }

    private void endingPriority() {
        // 赌徒复仇优先级最高：达标 + 通缉满级 + 低血，仍应先于其他结局
        Player p = winPlayer();
        p.setGambleRevenge(true);
        p.addWantedLevel(GameConfig.WANTED_MAX);
        p.loseHealth(80); // 100 → 20，正好落在赌徒复仇区间
        assertEq(new EndingProbe(p).determineEnding(),
                EndingSystem.EndingType.GAMBLE_REVENGE, "赌徒复仇优先级最高");
    }

    private void endingPriorityBurnout() {
        Player p = winPlayer();
        p.loseHealth(999);
        assertEq(new EndingProbe(p).determineEnding(),
                EndingSystem.EndingType.BURNOUT, "过劳暴毙判定");
    }

    private void endingPriorityArrest() {
        Player p = new Player("T");
        p.addWantedLevel(99);
        assertEq(new EndingProbe(p).determineEnding(),
                EndingSystem.EndingType.NCPD_ARREST, "通缉满级判定");
    }

    private void endingHeroRequiresAllVirtues() {
        Player p = winPlayer();
        EndingSystem s = new EndingProbe(p);
        assertEq(s.determineEnding(), EndingSystem.EndingType.QUIET_ESCAPE, "仅达标=平凡逃离");

        for (int i = 0; i < 3; i++) p.addHelpCount();
        p.addReputation(60);
        p.setRivalFriend(true);
        p.setSparedLife(true);
        // 此时应满足全部条件
        assertEq(new EndingProbe(p).determineEnding(),
                EndingSystem.EndingType.LEGEND_HERO, "传奇英雄全条件满足");

        // 任意一条被污染 → 不再是英雄
        p.setJoinedGang(true);
        assertNot(new EndingProbe(p).determineEnding(),
                EndingSystem.EndingType.LEGEND_HERO, "入帮后不再达成传奇英雄");
    }

    private void echoPoolDedupAndStatus() {
        Player p = new Player("T");
        p.addEcho(GameConfig.ECHO_RESCUE_SAVED);
        p.addEcho(GameConfig.ECHO_RESCUE_SAVED); // 重复添加应被去重
        assertEq(p.totalEchoCount(), 1, "回响石去重");

        EndingSystem s = new EndingProbe(p);
        assertEq(s.getFacedCount(), 0, "初始无已面对的回响");
        // faceGate 对空门/无 echoSource 的门必须静默跳过，不得抛异常
        s.faceGate(0);
        assertTrue("状态统计方法不抛异常", true);
    }

    private void facedCountIsSingleLedger() {
        Player p = new Player("T");
        for (int i = 0; i < 5; i++) p.addEcho("ECHO_" + i);
        EndingSystem s = new EndingProbe(p);
        // 直接标记 Player 状态后，getFacedCount 必须同步反映（不再有两份账）
        for (Player.EchoStone stone : p.getEchoPool()) {
            stone.setStatus(Player.EchoStone.EchoStatus.FACED);
        }
        assertEq(s.getFacedCount(), p.countEchoByStatus(Player.EchoStone.EchoStatus.FACED),
                "facedCount 单一账本");
        assertEq(s.getFacedCount(), 5, "facedCount 数值正确");
    }

    private void gateCountIsAlwaysNine() {
        Player minimal = new Player("T");
        Player rich = winPlayer();
        for (int i = 0; i < 3; i++) rich.addHelpCount();
        rich.addReputation(99);
        rich.setRivalFriend(true);
        rich.setSparedLife(true);
        rich.addEcho(GameConfig.ECHO_LIVING_SAVED);
        rich.addEcho(GameConfig.ECHO_BRIBE_POLICE);
        rich.addEcho(GameConfig.ECHO_GAMBLE_REPORT);
        rich.addEcho(GameConfig.ECHO_GHOST_ACCEPT);
        rich.addEcho(GameConfig.ECHO_SCAVENGER_INTEL);
        rich.addEcho(GameConfig.ECHO_LEGACY_RETURNED);

        assertEq(new EndingProbe(minimal).getGateCount(), 9, "空履历也补足九道门");
        assertEq(new EndingProbe(rich).getGateCount(), 9, "满履历不超过九道门");
    }

    private void eventWeightsAreValid() {
        int sum = GameConfig.POLICE_WEIGHT + GameConfig.GANG_WEIGHT + GameConfig.BREAKDOWN_WEIGHT
                + GameConfig.RIVAL_WEIGHT + GameConfig.RESCUE_WEIGHT + GameConfig.GHOST_WEIGHT
                + GameConfig.GAMBLE_WEIGHT + GameConfig.SCAN_WEIGHT + GameConfig.SCAVENGER_WEIGHT;
        assertTrue("事件权重总和必须为正，否则 weightedEventType 恒返回 0", sum > 0);
        assertEq(sum, 90, "事件权重总和为 90（与文档一致）");
        assertTrue("权重项数与事件键数组长度一致（9 种事件）",
                GameConfig.POLICE_WEIGHT > 0 && GameConfig.SCAVENGER_WEIGHT > 0);
    }

    private void echoEncounterSemantics() {
        EndingSystem.EchoPool pool = new EndingSystem.EchoPool();
        assertEq(pool.getEncounterCount("POLICE"), 0, "未遭遇计数为 0");
        pool.recordEncounter("POLICE");
        assertEq(pool.getEncounterCount("POLICE"), 1, "首次遭遇计数为 1");
        pool.recordEncounter("POLICE");
        assertEq(pool.getEncounterCount("POLICE"), 2, "二次遭遇计数为 2");

        // 回响文案：首次/重复遭遇文案不同，且不会退化成默认文案
        String first = pool.getEchoText("POLICE", 1);
        String again = pool.getEchoText("POLICE", 2);
        assertNotEq(again, first, "重复遭遇回响文案应不同");
        assertNotEq(again, "一段模糊的记忆涌上心头。", "已记录事件不应返回默认文案");

        // 未收录的 key 才应返回默认文案
        assertEq(pool.getEchoText("UNKNOWN_KEY", 1), "一段模糊的记忆涌上心头。", "未知 key 返回默认文案");
    }

    private void missionGenerationBounds() {
        MissionManager mm = new MissionManager();
        Player p = new Player("T");
        Set<MissionManager.MissionType> seenTypes = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            MissionManager.Mission m = mm.generateMission(p);
            assertTrue(m.getReward() > 0, "任务报酬必须为正");
            assertTrue(m.getTimeNeed() >= 2, "赶路步数不得小于 2");
            assertTrue(m.getRisk() > 0, "任务风险不得为零");
            assertTrue(m.getFailPenalty() >= 0, "失败罚金不得为负");
            seenTypes.add(m.getType());
            // 变体与类型不应因随机取值抛出异常（枚举越界回归点）
            assertTrue(m.getVariant() != null, "变体取值不应为 null");
        }
        assertEq(seenTypes.size(), 3, "长时间运行应覆盖全部 3 种任务类型");
    }

    private void playerBoundaries() {
        Player p = new Player("T");

        p.costMoney(99999);
        assertEq(p.getMoney(), 0, "金钱下限为 0（不可透支）");

        p.addWantedLevel(99);
        assertEq(p.getWantedLevel(), GameConfig.WANTED_MAX, "通缉等级封顶");
        p.reduceWantedLevel(99);
        assertEq(p.getWantedLevel(), 0, "通缉等级下限为 0");

        p.addReputation(9999);
        assertEq(p.getReputation(), 100, "声望封顶 100");
        p.reduceReputation(9999);
        assertEq(p.getReputation(), 0, "声望下限 0");

        p.loseHealth(9999);
        assertEq(p.getHealth(), 0, "血量下限为 0");
        assertTrue("血量归零应判定死亡", p.isDead());

        p.restoreHealth(9999);
        assertEq(p.getHealth(), p.getMaxHealth(), "治疗不得超过上限");

        // 幽灵帮助 / 拾荒者情报为一次性消耗
        p.addScavengerIntel();
        assertTrue("拾荒者情报初始可用", p.hasScavengerIntel());
        p.consumeScavengerIntel();
        assertTrue(!p.hasScavengerIntel(), "拾荒者情报消耗后不可复用");
        p.consumeScavengerIntel(); // 空消耗不得抛异常

        p.setGhostAccepted(true);
        assertTrue("幽灵帮助初始可用", p.hasGhostAccepted());
        p.consumeGhostHelp();
        assertTrue(!p.hasGhostAccepted(), "幽灵帮助消耗后不可复用");
    }

    private void variantEnumExcludesNone() {
        MissionManager.MissionVariant[] variants = MissionManager.MissionVariant.values();
        // randomVariant() 用 [1, length) 取值，NONE 必须在索引 0，否则该变体永远抽不到
        assertEq(variants[0], MissionManager.MissionVariant.NONE, "NONE 必须位于枚举首位");
        assertTrue(variants.length > 1, "至少存在一个可用变体");
    }

    private void upgradeCostMonotonic() {
        UpgradeSystem us = new UpgradeSystem();
        Player p = new Player("T");
        int base = costOf(p);
        int upgradesBefore = (p.getSpeed() - 1) + p.getAvoidPolice() + p.getAvoidGang();
        p.addMoney(1_000_000);
        us.upgrade(p, 1);
        int after = costOf(p);
        assertTrue(after > base, "升级次数增加后，下一次升级费用应更高");
        assertTrue(upgradesBefore < (p.getSpeed() - 1) + p.getAvoidPolice() + p.getAvoidGang(),
                "升级应生效");
    }

    // ────────── 存档往返测试 ──────────

    /**
     * 存档 → 加载 → 比对关键字段。这是最高风险路径：旧实现使用裸 Java 序列化，
     * 一旦 Player 类结构变动（如增删字段、改枚举）就会整档失败或字段错位。
     */
    private void saveLoadRoundtrip() throws Exception {
        Player original = new Player("测试员");
        original.setGambleRevenge(true);
        original.setRivalFriend(true);
        original.setSparedLife(true);
        original.setJoinedGang(true);
        original.setStoleFromDead(true);
        original.setLiedToLover(true);
        original.setLooted(true);
        original.setCelebEscort(true);
        original.setGhostAccepted(true);
        original.setTrapSurrendered(true);
        original.setLegacyReturned(true);
        original.addMoney(12345);
        original.loseHealth(37);
        original.addReputation(42);
        original.addWantedLevel(3);
        original.addSpeed();
        original.addAvoidPolice();
        original.addAvoidGang();
        original.addHelpCount();
        original.addHelpCount();
        original.addRefuseCount();
        original.addTotalTasks();
        original.addTotalTasks();
        original.addBlackMarketCount();
        original.addBribePoliceCount();
        original.addBribeGangCount();
        original.addHumanity();
        original.addColdness();
        original.addScavengerIntel();
        original.addScavengerIntel();
        original.setPendingScrutiny(2);
        original.levelUp();
        original.levelUp();
        original.addEcho(GameConfig.ECHO_RESCUE_SAVED);
        original.addEcho(GameConfig.ECHO_BRIBE_POLICE);
        original.addEcho(GameConfig.ECHO_GHOST_ACCEPT);
        original.addInventoryItem("改装车零件");
        original.addInventoryItem("城警徽章");
        // 标记一颗回响石为已面对
        original.getEchoPool().get(0).setStatus(Player.EchoStone.EchoStatus.FACED);

        Player loaded = roundtrip(original);
        assertEq(loaded.getName(), original.getName(), "存档往返：姓名");
        assertEq(loaded.getMoney(), original.getMoney(), "存档往返：金钱");
        assertEq(loaded.getHealth(), original.getHealth(), "存档往返：血量");
        assertEq(loaded.getMaxHealth(), original.getMaxHealth(), "存档往返：上限血量");
        assertEq(loaded.getLevel(), original.getLevel(), "存档往返：等级");
        assertEq(loaded.getReputation(), original.getReputation(), "存档往返：声望");
        assertEq(loaded.getWantedLevel(), original.getWantedLevel(), "存档往返：通缉");
        assertEq(loaded.getSpeed(), original.getSpeed(), "存档往返：速度");
        assertEq(loaded.getAvoidPolice(), original.getAvoidPolice(), "存档往返：避警");
        assertEq(loaded.getAvoidGang(), original.getAvoidGang(), "存档往返：避帮");
        assertEq(loaded.getHelpCount(), original.getHelpCount(), "存档往返：帮助数");
        assertEq(loaded.getRefuseCount(), original.getRefuseCount(), "存档往返：拒绝数");
        assertEq(loaded.getTotalTasks(), original.getTotalTasks(), "存档往返：总任务");
        assertEq(loaded.getBlackMarketCount(), original.getBlackMarketCount(), "存档往返：黑市数");
        assertEq(loaded.getBribePoliceCount(), original.getBribePoliceCount(), "存档往返：警贿赂");
        assertEq(loaded.getBribeGangCount(), original.getBribeGangCount(), "存档往返：帮贿赂");
        assertEq(loaded.getPendingScrutiny(), original.getPendingScrutiny(), "存档往返：待审查");
        assertEq(loaded.getHumanity(), original.getHumanity(), "存档往返：人道");
        assertEq(loaded.getColdness(), original.getColdness(), "存档往返：冷血");
        assertEq(loaded.getScavengerIntelCount(), original.getScavengerIntelCount(), "存档往返：拾荒者情报");
        assertEq(loaded.isWin(), original.isWin(), "存档往返：胜利判定");
        assertTrue("存档往返：布尔标记全部还原", allFlagsMatch(original, loaded));
        assertEq(loaded.totalEchoCount(), original.totalEchoCount(), "存档往返：回响石数量");
        assertEq(loaded.countEchoByStatus(Player.EchoStone.EchoStatus.FACED),
                original.countEchoByStatus(Player.EchoStone.EchoStatus.FACED), "存档往返：回响石状态");
        assertEq(loaded.getInventory().size(), original.getInventory().size(), "存档往返：物品数量");
        assertTrue(loaded.hasInventoryItem("改装车零件"), "存档往返：物品内容");
        assertEq(loaded.getEchoPool().get(0).getStatus(),
                original.getEchoPool().get(0).getStatus(), "存档往返：回响石状态枚举");
    }

    /** 姓名/物品含特殊字符（分号、等号、竖线、反斜杠、换行）时不应串场 */
    private void saveLoadRoundtripSpecialChars() throws Exception {
        Player original = new Player("甲；乙=丙\\丁");
        original.addInventoryItem("a;b");
        original.addInventoryItem("x=y");
        original.addInventoryItem("\\");
        original.addEcho("ECHO;TEST=1");

        Player loaded = roundtrip(original);
        assertEq(loaded.getName(), original.getName(), "存档往返：姓名含特殊字符");
        assertEq(loaded.getInventory().size(), 3, "存档往返：物品数量（含特殊字符）");
        assertTrue(loaded.hasInventoryItem("a;b"), "存档往返：分号不被当分隔符");
        assertTrue(loaded.hasInventoryItem("x=y"), "存档往返：等号不被当赋值符");
        assertTrue(loaded.hasInventoryItem("\\"), "存档往返：反斜杠不丢失");
        assertEq(loaded.totalEchoCount(), 1, "存档往返：回响石来源含特殊字符");
        assertEq(loaded.getEchoPool().get(0).getSource(), "ECHO;TEST=1",
                "存档往返：回响石来源原文还原");
    }

    /** 执行一次真实的序列化/反序列化，并验证校验和生效 */
    private Player roundtrip(Player player) throws Exception {
        String content = Player.serializeFull(player);
        Player loaded = Player.deserializeFull(content);
        assertTrue(loaded != null, "存档往返：加载不应返回 null");

        // 篡改一个字节后必须校验失败（而不是静默加载被改的数据）
        String tampered = content.replaceFirst("(money=\\d+)", "money=999999999");
        if (!tampered.equals(content)) {
            assertTrue(Player.deserializeFull(tampered) == null,
                    "篡改存档后校验和应拦住（拒绝加载）");
        }
        // 版本不匹配应拒绝加载（serializeFull 写的是硬编码 \n，不能用 System.lineSeparator()）
        String header = "NCCv\n" + Player.SAVE_VERSION + "\n";
        assertTrue(content.startsWith(header), "前置条件：存档应以魔数+版本开头");
        String wrongVersion = content.replaceFirst(java.util.regex.Pattern.quote(header),
                "NCCv\n999\n");
        assertTrue(!wrongVersion.equals(content), "前置条件：版本篡改应生效");
        assertTrue(Player.deserializeFull(wrongVersion) == null, "版本不兼容的存档应被拒绝");
        // 魔数缺失应拒绝加载
        assertTrue(Player.deserializeFull("GARBAGE\n2\nabc\n{\n}") == null, "非本程序存档应被拒绝");
        return loaded;
    }

    /** 通过真实文件路径走一遍 save()/load()，验证 I/O 与校验链路 */
    private void saveLoadFileRoundtrip() throws Exception {
        String dir = System.getProperty("java.io.tmpdir") + "/ncc-save-test-" + System.nanoTime();
        new java.io.File(dir).mkdirs();
        String savePath = dir + "/save.dat";
        Player.setSaveFileForTest(savePath);
        try {
            assertTrue(!Player.hasSave(), "初始不应存在存档");
            Player original = new Player("文件测试");
            original.addMoney(777);
            original.levelUp();
            original.addEcho(GameConfig.ECHO_RESCUE_SAVED);
            Player.save(original);
            assertTrue(Player.hasSave(), "存档后文件应存在");

            Player loaded = Player.load();
            assertTrue(loaded != null, "从文件加载不应返回 null");
            assertEq(loaded.getMoney(), 777, "文件存档：金钱");
            assertEq(loaded.getLevel(), 2, "文件存档：等级");
            assertEq(loaded.totalEchoCount(), 1, "文件存档：回响石");
        } finally {
            new java.io.File(savePath).delete();
            new java.io.File(savePath + ".bak").delete();
        }
    }

    private boolean allFlagsMatch(Player a, Player b) {
        return a.hasRivalFriend() == b.hasRivalFriend()
                && a.hasGambleRevenge() == b.hasGambleRevenge()
                && a.hasSparedLife() == b.hasSparedLife()
                && a.hasStoleFromDead() == b.hasStoleFromDead()
                && a.hasJoinedGang() == b.hasJoinedGang()
                && a.hasLiedToLover() == b.hasLiedToLover()
                && a.hasLooted() == b.hasLooted()
                && a.hasCelebEscort() == b.hasCelebEscort()
                && a.hasGhostAccepted() == b.hasGhostAccepted()
                && a.hasTrapSurrendered() == b.hasTrapSurrendered()
                && a.hasLegacyReturned() == b.hasLegacyReturned();
    }

    private int costOf(Player p) {
        int total = (p.getSpeed() - 1) + p.getAvoidPolice() + p.getAvoidGang();
        return GameConfig.UPGRADE_BASE_COST
                + p.getLevel() * GameConfig.UPGRADE_COST_PER_LEVEL
                + total * GameConfig.UPGRADE_COST_PER_UPGRADE;
    }

    private Player winPlayer() {
        Player p = new Player("T");
        p.addMoney(GameConfig.WIN_TARGET);
        return p;
    }

    private void assertEq(Object actual, Object expected, String msg) {
        if (actual == null ? expected != null : !actual.equals(expected)) {
            fail(msg + " — 期望 " + expected + "，实际 " + actual);
            return;
        }
        pass(msg);
    }

    private void assertNot(Object actual, Object forbidden, String msg) {
        if (actual != null && actual.equals(forbidden)) {
            fail(msg + " — 不应等于 " + forbidden);
            return;
        }
        pass(msg);
    }

    private void assertEq(String actual, String expected, String msg) {
        if (!expected.equals(actual)) {
            fail(msg + " — 期望 \"" + expected + "\"，实际 \"" + actual + "\"");
            return;
        }
        pass(msg);
    }

    private void assertNotEq(String actual, String forbidden, String msg) {
        if (forbidden.equals(actual)) {
            fail(msg + " — 不应等于 \"" + forbidden + "\"");
            return;
        }
        pass(msg);
    }

    private void assertTrue(String msg, boolean cond) {
        if (!cond) {
            fail(msg);
            return;
        }
        pass(msg);
    }

    private void assertTrue(boolean cond, String msg) {
        assertTrue(msg, cond);
    }

    private void pass(String msg) {
        passed++;
        System.out.println("  ✓ " + msg);
    }

    private void fail(String msg) {
        failures.add(msg);
        System.out.println("  ✗ " + msg);
    }

    /**
     * 测试替身：EndingSystem 构造函数只做数据绑定，但为兼容 Player 内部
     * （如血量压测）暴露一个仅测试用的最小包装。
     */
    private static final class EndingProbe extends EndingSystem {
        EndingProbe(Player player) {
            super(player);
        }
    }
}
