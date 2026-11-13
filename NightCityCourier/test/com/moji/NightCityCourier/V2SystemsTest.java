package com.moji.NightCityCourier;

import com.moji.NightCityCourier.core.Achievement;
import com.moji.NightCityCourier.core.AchievementSystem;
import com.moji.NightCityCourier.core.CourierProfile;
import com.moji.NightCityCourier.core.DailyChallenge;
import com.moji.NightCityCourier.core.FactionSystem;
import com.moji.NightCityCourier.core.GameStats;
import com.moji.NightCityCourier.core.Inventory;
import com.moji.NightCityCourier.core.Item;
import com.moji.NightCityCourier.core.Contacts;
import com.moji.NightCityCourier.core.EventVoice;
import com.moji.NightCityCourier.core.Memory;
import com.moji.NightCityCourier.core.Rng;
import com.moji.NightCityCourier.core.RouteSystem;
import com.moji.NightCityCourier.core.SaveCodec;
import com.moji.NightCityCourier.core.SaveStore;
import com.moji.NightCityCourier.core.SkillTree;
import com.moji.NightCityCourier.core.StorylineSystem;
import com.moji.NightCityCourier.core.WeatherSystem;
import com.moji.NightCityCourier.ui.Theme;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * v2.0 新增系统的纯逻辑测试。
 *
 * <p>与 {@link CoreSystemsTest} 同样的「零第三方依赖」风格：不引入 JUnit，
 * 只装 JDK 就能跑。覆盖三个层面：
 * <ol>
 *   <li><b>基础设施</b>：随机流可复现、存档编解码的往返/篡改/截断/迁移、备份轮转；</li>
 *   <li><b>玩法系统</b>：背包容量、技能点数、派系阈值、路线三选一、天气倍率；</li>
 *   <li><b>聚合层</b>：经验升级发点、成就去重、档案整体序列化往返。</li>
 * </ol>
 */
public class V2SystemsTest {

    private int passed = 0;
    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        V2SystemsTest t = new V2SystemsTest();
        try {
            t.runAll();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
        System.out.println();
        System.out.println("═══════════════════════════════════");
        System.out.println("  v2 系统测试 通过: " + t.passed + "  失败: " + t.failures.size());
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
        rngReproducible();
        rngDistribution();
        rngForkIndependence();
        rngBoundsAndChance();
        saveCodecRoundtrip();
        saveCodecEscaping();
        saveCodecTamperDetection();
        saveCodecTruncationDetection();
        saveCodecLegacyMigration();
        saveStoreBackupRotation();
        saveStoreCorruptQuarantine();
        inventoryStackLimits();
        inventoryRigSlots();
        inventoryUnknownItemTolerance();
        skillTreeSpending();
        skillTreeBonuses();
        factionThresholds();
        factionMultipliers();
        routeOfferShape();
        weatherMultipliers();
        achievementDedup();
        achievementPersistence();
        profileLevelAndPoints();
        profileSerializationRoundtrip();
        missionAdjustBounds();
        profileRewardMultiplier();
        dailyChallengeStability();
        storylineProgression();
        contentCoverage();
        memoryArchetypes();
        memoryPersistence();
        memoryShiftsEventPool();
        eventVoiceReactsToMemory();
        storylineDrivenByFacts();
        causalChainScenario();
        contactsImpactsDifferPerPerson();
        contactsPersistence();
        contactsArcScenario();
    }

    // ══════════════ 随机流 ══════════════

    private void rngReproducible() {
        Rng a = new Rng(42L);
        Rng b = new Rng(42L);
        boolean same = true;
        for (int i = 0; i < 200; i++) {
            if (a.nextLong() != b.nextLong()) {
                same = false;
                break;
            }
        }
        assertTrue("同种子应产出完全相同的序列", same);

        Rng c = new Rng(43L);
        Rng d = new Rng(42L);
        boolean differs = false;
        for (int i = 0; i < 50; i++) {
            if (c.nextLong() != d.nextLong()) {
                differs = true;
                break;
            }
        }
        assertTrue("不同种子应产出不同序列", differs);
        assertEq(42L, new Rng(42L).seed(), "seed() 应返回原始种子");
        assertEq(Rng.seedOf("2026-11-13"), Rng.seedOf("2026-11-13"), "字符串取种应稳定");
        assertNotEq(Rng.seedOf("2026-11-13"), Rng.seedOf("2026-11-14"), "不同日期应产出不同种子");
    }

    private void rngDistribution() {
        Rng rng = new Rng(7L);
        int[] buckets = new int[6];
        for (int i = 0; i < 6000; i++) {
            buckets[rng.nextInt(6)]++;
        }
        for (int i = 0; i < buckets.length; i++) {
            assertTrue("第 " + i + " 桶应有样本（实际 " + buckets[i] + "）", buckets[i] > 600);
        }
        double mean = 0;
        for (int i = 0; i < 5000; i++) {
            mean += new Rng(i).nextDouble();
        }
        mean /= 5000;
        assertTrue("nextDouble 均值应接近 0.5，实际 " + mean, mean > 0.42 && mean < 0.58);
    }

    private void rngForkIndependence() {
        Rng parent = new Rng(99L);
        Rng forkA = Rng.forkOf(parent.seed(), 1);
        Rng forkB = Rng.forkOf(parent.seed(), 2);
        assertNotEq(forkA.nextLong(), forkB.nextLong(), "不同盐派生的子流首个值应不同");
        Rng forkA2 = Rng.forkOf(parent.seed(), 1);
        long first = forkA2.nextLong();
        long second = Rng.forkOf(parent.seed(), 1).nextLong();
        assertTrue("同一盐派生的子流应可复现", first == second);
    }

    private void rngBoundsAndChance() {
        Rng rng = new Rng(123L);
        boolean allInRange = true;
        for (int i = 0; i < 2000; i++) {
            int v = rng.nextInt(10, 20);
            if (v < 10 || v >= 20) {
                allInRange = false;
                break;
            }
        }
        assertTrue("nextInt(origin,bound) 应落在 [origin,bound)", allInRange);
        assertTrue("chance(0) 恒 false", !new Rng(1L).chance(0.0));
        assertTrue("chance(1) 恒 true", new Rng(1L).chance(1.0));

        int[] weights = {1, 0, 4, 0, 5};
        Set<Integer> hit = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            int idx = new Rng(i).pickWeighted(weights);
            hit.add(idx);
        }
        assertTrue("零权重下标不应被抽到", !hit.contains(1) && !hit.contains(3));
        assertTrue("正权重下标应被抽到", hit.contains(0) && hit.contains(2) && hit.contains(4));

        int[] zeros = {0, 0, 0};
        int zeroIdx = new Rng(5L).pickWeighted(zeros);
        assertTrue("全零权重应退化为合法下标", zeroIdx >= 0 && zeroIdx < 3);
        assertEq(-1, new Rng(5L).pickWeighted(new int[0]), "空权重应返回 -1");
    }

    // ══════════════ 存档编解码 ══════════════

    private void saveCodecRoundtrip() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("player.name", "V");
        data.put("player.money", "1234");
        data.put("player.health", "88");
        data.put("flag.ghost", "true");
        data.put("nested.key_with-dash", "value");
        String encoded = SaveCodec.encode(data, 1700000000000L);
        SaveCodec.DecodeResult result = SaveCodec.decode(encoded);
        assertTrue("编码后应能解码：" + result.error(), result.ok());
        assertEq(5, result.document().entries().size(), "条目数应一致");
        assertEq("V", result.document().get("player.name", ""), "字符串值往返");
        assertEq(1234, result.document().getInt("player.money", 0), "整数值往返");
        assertTrue("布尔值往返", result.document().getBool("flag.ghost", false));
        assertEq(1700000000000L, result.document().createdAt(), "时间戳往返");
        assertEq(3, result.detectedVersion(), "版本应为 3");
    }

    private void saveCodecEscaping() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("text.multiline", "第一行\n第二行\n第三行");
        data.put("text.equals", "a=b=c");
        data.put("text.backslash", "C:\\Users\\V\\save.dat");
        data.put("text.empty", "");
        SaveCodec.DecodeResult result = SaveCodec.decode(SaveCodec.encode(data));
        assertTrue("含特殊字符应能解码：" + result.error(), result.ok());
        assertEq("第一行\n第二行\n第三行", result.document().get("text.multiline", ""), "换行应还原");
        assertEq("a=b=c", result.document().get("text.equals", ""), "等号应还原");
        assertEq("C:\\Users\\V\\save.dat", result.document().get("text.backslash", ""), "反斜杠应还原");
        assertEq("", result.document().get("text.empty", "x"), "空值应还原为空");
    }

    private void saveCodecTamperDetection() {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("player.money", "9999");
        String encoded = SaveCodec.encode(data, 1L);
        String tampered = encoded.replace("9999", "9998");
        SaveCodec.DecodeResult result = SaveCodec.decode(tampered);
        assertTrue("篡改金额必须被检测出来", !result.ok());
        assertTrue("错误信息应提到校验：" + result.error(),
                result.error().contains("校验") || result.error().contains("长度"));

        SaveCodec.DecodeResult notOurs = SaveCodec.decode("hello world");
        assertTrue("非本程序存档应被拒绝", !notOurs.ok());
        assertTrue("空内容应被拒绝", !SaveCodec.decode("").ok());
    }

    private void saveCodecTruncationDetection() {
        Map<String, String> data = new LinkedHashMap<>();
        for (int i = 0; i < 30; i++) {
            data.put("k" + i, "value-" + i);
        }
        String encoded = SaveCodec.encode(data, 2L);
        String truncated = encoded.substring(0, encoded.length() - 40);
        SaveCodec.DecodeResult result = SaveCodec.decode(truncated);
        assertTrue("截断存档必须被拒绝", !result.ok());
        assertTrue("截断应能定位到长度问题：" + result.error(),
                result.error().contains("长度") || result.error().contains("校验") || result.error().contains("截断"));
    }

    private void saveCodecLegacyMigration() {
        String payload = "player.name=V\nplayer.money=500\n";
        String legacy = "NCCv\n2\n" + sha256(payload) + "\n" + payload;
        SaveCodec.DecodeResult result = SaveCodec.decode(legacy);
        assertTrue("v2 存档应能被识别：" + result.error(), result.ok());
        assertEq(2, result.detectedVersion(), "应识别为 v2");
        assertEq(500, result.document().getInt("player.money", 0), "v2 数值应正确解析");

        String upgraded = SaveCodec.upgradeLegacy(legacy);
        assertTrue("v2 应能升级为 v3 文本", upgraded != null && upgraded.startsWith("NCCv3"));
        SaveCodec.DecodeResult after = SaveCodec.decode(upgraded);
        assertTrue("升级后的 v3 应可读：" + after.error(), after.ok());
        assertEq(500, after.document().getInt("player.money", 0), "升级后数值不变");

        String badLegacy = "NCCv\n2\ndeadbeef\n" + payload;
        assertTrue("校验和错误的 v2 应被拒绝", !SaveCodec.decode(badLegacy).ok());
    }

    private void saveStoreBackupRotation() throws Exception {
        Path dir = Files.createTempDirectory("ncc-save-");
        try {
            SaveStore store = new SaveStore(dir);
            String[] payloads = {"A", "B", "C", "D"};
            for (String p : payloads) {
                assertTrue("保存应成功：" + p, store.save(SaveStore.SLOT_MAIN, Map.of("mark", p)));
            }
            Optional<SaveCodec.Document> current = store.load(SaveStore.SLOT_MAIN);
            assertTrue("当前存档应可读", current.isPresent());
            assertEq("D", current.get().get("mark", ""), "当前存档应为最后一次写入");

            assertEq("C", readMark(dir.resolve("main.dat.bak1")), "bak1 应为上一次内容");
            assertEq("B", readMark(dir.resolve("main.dat.bak2")), "bak2 应为上上次内容");
            assertEq("A", readMark(dir.resolve("main.dat.bak3")), "bak3 应为最旧内容");

            List<String> slots = store.listSlots();
            assertTrue("槽位列表应包含 main", slots.contains("main"));
            assertTrue("槽位列表不应包含备份文件", slots.stream().noneMatch(s -> s.contains("bak")));
        } finally {
            deleteRecursively(dir);
        }
    }

    private void saveStoreCorruptQuarantine() throws Exception {
        Path dir = Files.createTempDirectory("ncc-corrupt-");
        try {
            SaveStore store = new SaveStore(dir);
            store.save(SaveStore.SLOT_MAIN, Map.of("mark", "OK"));
            Files.writeString(dir.resolve("main.dat"), "NCCv3\nversion=3\nbroken", StandardCharsets.UTF_8);
            Optional<SaveCodec.Document> loaded = store.load(SaveStore.SLOT_MAIN);
            assertTrue("损坏的当前存档不应被当作有效数据", loaded.isEmpty());
            boolean quarantined = Files.list(dir).anyMatch(p -> p.getFileName().toString().contains(".corrupt-"));
            assertTrue("损坏文件应被隔离保留而不是删除", quarantined);

            // 隔离后仍能从备份恢复（此前只写过一次，因此无备份 → 空）
            assertTrue("无备份时应返回空", store.loadWithFallback(SaveStore.SLOT_MAIN).isEmpty());
        } finally {
            deleteRecursively(dir);
        }
    }

    // ══════════════ 背包 ══════════════

    private void inventoryStackLimits() {
        Inventory inv = new Inventory();
        for (int i = 0; i < Inventory.MAX_PER_ITEM; i++) {
            assertTrue("第 " + (i + 1) + " 支急救针应能放入", inv.add(Item.MEDKIT));
        }
        assertTrue("超过单件上限应被拒绝", !inv.add(Item.MEDKIT));
        assertEq(Inventory.MAX_PER_ITEM, inv.count(Item.MEDKIT), "数量应为上限");

        // 堆到总格数上限
        int guard = 0;
        while (inv.usedStacks() < Inventory.MAX_STACKS && guard++ < 100) {
            if (!inv.add(Item.SMOKE) && !inv.add(Item.JAMMER) && !inv.add(Item.STIM)) {
                break;
            }
        }
        assertEq(Inventory.MAX_STACKS, inv.usedStacks(), "总格数应到上限");
        assertTrue("满包后新物品应被拒绝", !inv.add(Item.COUNTERFEIT_ID));

        assertTrue("使用应能减少数量", inv.remove(Item.MEDKIT));
        assertEq(Inventory.MAX_PER_ITEM - 1, inv.count(Item.MEDKIT), "移除后数量减一");
        assertTrue("义体不能进消耗品背包", !inv.add(Item.COLD_PACK));
    }

    private void inventoryRigSlots() {
        Inventory inv = new Inventory();
        assertTrue("第一个义体应能装", inv.install(Item.COLD_PACK));
        assertTrue("重复安装应被拒绝", !inv.install(Item.COLD_PACK));
        assertTrue("第二个义体应能装", inv.install(Item.SUBCUT_ARMOR));
        assertTrue("第三个义体应能装", inv.install(Item.TURBO_KIT));
        assertEq(Inventory.MAX_RIG_SLOTS, inv.rigs().size(), "应装满三槽");
        assertTrue("槽满后应拒绝", !inv.install(Item.GHOST_DECK));
        assertTrue("卸下应成功", inv.uninstall(Item.TURBO_KIT));
        assertTrue("卸下后应能装新义体", inv.install(Item.GHOST_DECK));

        Inventory reduction = new Inventory();
        reduction.install(Item.SUBCUT_ARMOR);
        reduction.install(Item.GHOST_DECK);
        assertEq(25, reduction.rigBonus(Item.Effect.DAMAGE_REDUCE), "减伤义体应生效");
        assertEq(10, reduction.rigBonus(Item.Effect.EVASION), "闪避义体应生效");
        assertEq(0, reduction.rigBonus(Item.Effect.STEP_SAVE), "未装节流义体应为 0");
    }

    private void inventoryUnknownItemTolerance() {
        Inventory inv = new Inventory();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("inv.MEDKIT", "3");
        data.put("inv.NOT_A_REAL_ITEM", "9");
        data.put("inv.COLD_PACK", "1");
        data.put("inv.rigs", "SUBCUT_ARMOR,NOT_A_RIG,TURBO_KIT");
        inv.fromSaveEntries(data);
        assertEq(3, inv.count(Item.MEDKIT), "已知消耗品应恢复");
        assertEq(2, inv.rigs().size(), "未知义体应被跳过");
        assertTrue("未知物品不应导致异常", inv.count(Item.SMOKE) == 0);
    }

    // ══════════════ 技能 ══════════════

    private void skillTreeSpending() {
        SkillTree tree = new SkillTree();
        assertEq(0, tree.points(), "初始无点数");
        assertTrue("无点数时不能加点", !tree.spendPoint(SkillTree.Skill.GHOST_STEP));

        tree.addPoints(5);
        assertTrue("有点数应能加点", tree.spendPoint(SkillTree.Skill.GHOST_STEP));
        assertEq(1, tree.rank(SkillTree.Skill.GHOST_STEP), "等级应为 1");
        assertEq(4, tree.points(), "点数应减少");

        tree.spendPoints(SkillTree.Skill.GHOST_STEP, 10);
        assertEq(SkillTree.Skill.GHOST_STEP.maxRank(), tree.rank(SkillTree.Skill.GHOST_STEP), "不应超过上限");
        tree.addPoints(10);
        assertTrue("高消耗技能应能加点", tree.spendPoint(SkillTree.Skill.NETRUNNER));
        assertEq(9, tree.points(), "网潜者消耗 3 点后应剩 9");

        Map<SkillTree.Skill, Boolean> avail = tree.availability();
        assertTrue("满级技能应标记为不可点", !avail.get(SkillTree.Skill.GHOST_STEP));
        assertTrue("点数充足的未满技能应可点", avail.get(SkillTree.Skill.HAGGLER));
    }

    private void skillTreeBonuses() {
        SkillTree tree = new SkillTree();
        tree.addPoints(20);
        tree.spendPoints(SkillTree.Skill.GHOST_STEP, 3);
        tree.spendPoints(SkillTree.Skill.SILVER_TONGUE, 3);
        tree.spendPoints(SkillTree.Skill.LAST_STAND, 2);
        assertTrue("满级闪避应为 0.18：" + tree.evasionBonus(), Math.abs(tree.evasionBonus() - 0.18) < 1e-9);
        assertTrue("满级折扣应为 0.30：" + tree.costDiscount(), Math.abs(tree.costDiscount() - 0.30) < 1e-9);
        assertTrue("满级最后一搏应为 0.40：" + tree.lastStandReduction(),
                Math.abs(tree.lastStandReduction() - 0.40) < 1e-9);
        assertTrue("折扣不应超过 30%", tree.costDiscount() <= 0.3);
    }

    // ══════════════ 派系 ══════════════

    private void factionThresholds() {
        FactionSystem f = new FactionSystem();
        assertEq(0, f.rep(FactionSystem.Faction.POLICE), "初始中立");
        assertEq(FactionSystem.Tier.NEUTRAL, f.tier(FactionSystem.Faction.POLICE), "0 应为中立");

        f.shift(FactionSystem.Faction.POLICE, -45);
        assertEq(FactionSystem.Tier.HOSTILE, f.tier(FactionSystem.Faction.POLICE), "-45 应为敌对");
        f.shift(FactionSystem.Faction.POLICE, 300);
        assertEq(FactionSystem.MAX, f.rep(FactionSystem.Faction.POLICE), "声望上限应裁剪到 100");
        assertEq(FactionSystem.Tier.NEUTRAL, f.tier(FactionSystem.Faction.GANG), "未操作的派系应仍中立");

        f.shift(FactionSystem.Faction.SCAV, 90);
        assertEq(FactionSystem.Tier.ALLY, f.tier(FactionSystem.Faction.SCAV), "满值应为同盟");
        assertEq(FactionSystem.Faction.POLICE, f.dominant(), "最高声望派系应正确");
        assertTrue("无人敌对时 mostHated 应为空", f.mostHated() == null);

        f.shift(FactionSystem.Faction.GANG, -80);
        assertEq(FactionSystem.Faction.GANG, f.mostHated(), "最低声望派系应正确");
    }

    private void factionMultipliers() {
        FactionSystem f = new FactionSystem();
        double neutralBribe = f.bribeMultiplier(FactionSystem.Faction.GANG);
        assertTrue("中立贿赂倍率应接近 1：" + neutralBribe, Math.abs(neutralBribe - 1.0) < 1e-9);

        f.shift(FactionSystem.Faction.GANG, -100);
        double hostileBribe = f.bribeMultiplier(FactionSystem.Faction.GANG);
        assertTrue("敌对应涨价：" + hostileBribe, hostileBribe > 1.4);
        assertTrue("敌对事件密度应上升：" + f.eventMultiplier(FactionSystem.Faction.GANG),
                f.eventMultiplier(FactionSystem.Faction.GANG) > 1.3);

        FactionSystem ally = new FactionSystem();
        ally.shift(FactionSystem.Faction.POLICE, 100);
        assertTrue("同盟应打折：" + ally.bribeMultiplier(FactionSystem.Faction.POLICE),
                ally.bribeMultiplier(FactionSystem.Faction.POLICE) < 0.75);
        assertTrue("倍率应有下界", ally.eventMultiplier(FactionSystem.Faction.POLICE) >= 0.55);
    }

    // ══════════════ 路线 / 天气 ══════════════

    private void routeOfferShape() {
        boolean alwaysThree = true;
        boolean alwaysDistinct = true;
        boolean hasFast = false;
        boolean hasSafe = false;
        boolean hasRich = false;
        for (int seed = 0; seed < 200; seed++) {
            List<RouteSystem.RouteOption> routes = RouteSystem.offer(new Rng(seed));
            if (routes.size() != 3) {
                alwaysThree = false;
            }
            Set<String> names = new HashSet<>();
            for (RouteSystem.RouteOption r : routes) {
                names.add(r.name());
                if (r.stepDelta() <= -1) {
                    hasFast = true;
                }
                if (r.riskDelta() <= -0.2) {
                    hasSafe = true;
                }
                if (r.rewardMul() >= 1.15) {
                    hasRich = true;
                }
            }
            if (names.size() != 3) {
                alwaysDistinct = false;
            }
        }
        assertTrue("每次都应给出三条路线", alwaysThree);
        assertTrue("三条路线不应重复", alwaysDistinct);
        assertTrue("路线池应包含快线", hasFast);
        assertTrue("路线池应包含稳线", hasSafe);
        assertTrue("路线池应包含肥单", hasRich);
        assertEq(8, RouteSystem.all().size(), "路线池应有 8 条");
        for (RouteSystem.RouteOption r : RouteSystem.all()) {
            assertTrue("路线提示不应为空：" + r.name(), !r.hint().isBlank());
        }
    }

    private void weatherMultipliers() {
        WeatherSystem w = new WeatherSystem();
        w.set(WeatherSystem.Weather.CLEAR, WeatherSystem.Phase.NIGHT);
        double clearNight = w.encounterMultiplier();
        w.set(WeatherSystem.Weather.BLOOD_MOON, WeatherSystem.Phase.NIGHT);
        assertTrue("血月事件率应高于晴夜：" + w.encounterMultiplier(),
                w.encounterMultiplier() > clearNight);
        w.set(WeatherSystem.Weather.BLACKOUT, WeatherSystem.Phase.DAWN);
        assertTrue("断电凌晨事件率应最低：" + w.encounterMultiplier(), w.encounterMultiplier() < 1.0);
        assertTrue("断电报酬应上浮：" + w.rewardMultiplier(), w.rewardMultiplier() > 1.2);
        assertTrue("环境描述不应为空", !w.flavor().isBlank());

        w.roll(new Rng(11L));
        WeatherSystem w2 = new WeatherSystem();
        w2.roll(new Rng(11L));
        assertEq(w.weather(), w2.weather(), "同种子应抽到同一天气");
        assertEq(w.phase(), w2.phase(), "同种子应抽到同一时段");
    }

    // ══════════════ 成就 ══════════════

    private void achievementDedup() {
        AchievementSystem sys = new AchievementSystem();
        assertEq(0, sys.unlockedCount(), "初始无成就");
        assertTrue("首次解锁应成功", sys.unlock(Achievement.FIRST_DELIVERY));
        assertTrue("重复解锁应返回 false", !sys.unlock(Achievement.FIRST_DELIVERY));
        assertEq(1, sys.unlockedCount(), "重复解锁不应计数");
        assertEq(1, sys.drainToasts().size(), "应有一条待播报");
        assertEq(0, sys.drainToasts().size(), "播报队列应被清空");
        assertTrue("已解锁应可查询", sys.isUnlocked(Achievement.FIRST_DELIVERY));
        assertTrue("未解锁应查询为 false", !sys.isUnlocked(Achievement.LEGEND));
        assertEq(Achievement.values().length, sys.totalCount(), "总数应等于枚举数量");
        assertTrue("完成度应在 0~1 之间", sys.completionRatio() > 0 && sys.completionRatio() <= 1);

        List<String> list = sys.renderList();
        assertEq(Achievement.values().length, list.size(), "成就列表长度应匹配");
        assertTrue("隐藏成就未解锁时应显示 ???",
                list.stream().anyMatch(s -> s.contains("???")));
    }

    private void achievementPersistence() {
        AchievementSystem sys = new AchievementSystem();
        sys.unlock(Achievement.RICH);
        sys.unlock(Achievement.GAMBLER);
        Map<String, String> saved = sys.toSaveEntries();
        AchievementSystem restored = new AchievementSystem();
        restored.fromSaveEntries(saved);
        assertEq(2, restored.unlockedCount(), "应恢复两个成就");
        assertTrue("应恢复具体成就", restored.isUnlocked(Achievement.RICH));

        Map<String, String> junk = new LinkedHashMap<>();
        junk.put("ach.list", "RICH,NOT_AN_ACHIEVEMENT,GAMBLER");
        AchievementSystem tolerant = new AchievementSystem();
        tolerant.fromSaveEntries(junk);
        assertEq(2, tolerant.unlockedCount(), "未知成就名应被忽略");
    }

    // ══════════════ 档案 ══════════════

    private void profileLevelAndPoints() {
        CourierProfile p = new CourierProfile();
        assertEq(1, p.level(), "初始 1 级");
        assertEq(60, p.xpToNext(), "1 级升级门槛应为 60");
        assertTrue("经验不足不应升级", p.gainXp(10) == 0);
        assertTrue("攒够经验应升级", p.gainXp(60) >= 1);
        assertEq(2, p.level(), "应升到 2 级");
        assertEq(1, p.skills().points(), "升级应发 1 点技能点");
        assertTrue("15 级前每级都应给点", p.gainXp(2000) >= 1);
        assertTrue("等级不应超过上限", p.level() <= CourierProfile.MAX_LEVEL);
    }

    private void profileSerializationRoundtrip() {
        CourierProfile p = new CourierProfile();
        p.gainXp(500);
        p.skills().spendPoint(SkillTree.Skill.HAGGLER);
        p.inventory().install(Item.COLD_PACK);
        p.inventory().add(Item.MEDKIT);
        p.factions().shift(FactionSystem.Faction.SCAV, 65);
        p.achievements().unlock(Achievement.FIRST_DELIVERY);
        p.stats().inc(GameStats.DELIVERIES);
        p.stats().add(GameStats.MONEY_EARNED, 880);
        p.stats().max(GameStats.BEST_HAUL, 420);
        p.weather().set(WeatherSystem.Weather.ACID_RAIN, WeatherSystem.Phase.DAWN);
        p.recordRoute("后巷穿行");

        Map<String, String> saved = p.toSaveEntries();
        CourierProfile restored = new CourierProfile();
        restored.fromSaveEntries(saved);

        assertEq(p.level(), restored.level(), "等级应往返");
        assertEq(p.xp(), restored.xp(), "经验应往返");
        assertEq(p.skills().rank(SkillTree.Skill.HAGGLER),
                restored.skills().rank(SkillTree.Skill.HAGGLER), "技能等级应往返");
        assertEq(1, restored.inventory().count(Item.MEDKIT), "消耗品应往返");
        assertTrue("义体应往返", restored.inventory().hasRig(Item.COLD_PACK));
        assertEq(65, restored.factions().rep(FactionSystem.Faction.SCAV), "派系声望应往返");
        assertTrue("成就应往返", restored.achievements().isUnlocked(Achievement.FIRST_DELIVERY));
        assertEq(880L, restored.stats().get(GameStats.MONEY_EARNED), "统计应往返");
        assertEq(420L, restored.stats().get(GameStats.BEST_HAUL), "最高单笔应往返");
        assertEq(WeatherSystem.Weather.ACID_RAIN, restored.weather().weather(), "天气应往返");
        assertTrue("路线记录应往返", restored.visitedRoutes().contains("后巷穿行"));

        // 整份档案走一遍真实的 v3 编码/解码链路
        SaveCodec.DecodeResult round = SaveCodec.decode(SaveCodec.encode(saved));
        assertTrue("档案应能编码为 v3 存档：" + round.error(), round.ok());
        CourierProfile third = new CourierProfile();
        third.fromSaveEntries(round.document().entries());
        assertEq(restored.level(), third.level(), "v3 编解码后等级一致");
        assertEq(restored.stats().get(GameStats.MONEY_EARNED),
                third.stats().get(GameStats.MONEY_EARNED), "v3 编解码后统计一致");
    }

    private void missionAdjustBounds() {
        MissionManager.Mission m = new MissionManager.Mission(
                MissionManager.MissionType.NORMAL, MissionManager.MissionVariant.NONE, 1.0, 300, 5, 0);
        m.adjustTimeNeed(-99);
        assertEq(2, m.getTimeNeed(), "赶路步数下限应为 2");
        m.adjustTimeNeed(3);
        assertEq(5, m.getTimeNeed(), "赶路步数应能增加");
        m.adjustRisk(99);
        assertEq(5.0, m.getRisk(), "风险上限应为 5.0");
        m.adjustRisk(-99);
        assertEq(0.1, m.getRisk(), "风险下限应为 0.1");
        m.setReward(777);
        assertEq(777, m.getReward(), "报酬可被路线修正");
    }

    private void profileRewardMultiplier() {
        CourierProfile p = new CourierProfile();
        p.weather().set(WeatherSystem.Weather.CLEAR, WeatherSystem.Phase.MORNING);
        double base = p.rewardMultiplier(false);
        assertTrue("无加成时倍率应接近 1：" + base, Math.abs(base - 0.95) < 1e-9);

        p.skills().addPoints(10);
        p.skills().spendPoints(SkillTree.Skill.HAGGLER, 3);
        double withSkill = p.rewardMultiplier(false);
        assertTrue("议价技能应提高报酬：" + withSkill, withSkill > base);

        p.weather().set(WeatherSystem.Weather.BLACKOUT, WeatherSystem.Phase.NIGHT);
        assertTrue("断电深夜应进一步提高报酬：" + p.rewardMultiplier(false),
                p.rewardMultiplier(false) > withSkill);

        p.inventory().install(Item.COLD_PACK);
        p.weather().set(WeatherSystem.Weather.CLEAR, WeatherSystem.Phase.MORNING);
        double normal = p.rewardMultiplier(false);
        double contraband = p.rewardMultiplier(true);
        assertTrue("冷藏箱应只加成黑市件：" + contraband + " vs " + normal, contraband > normal);
        assertTrue("减伤不应为负", p.damageReduction(100) >= 0);
        assertTrue("低血量减伤应更高", p.damageReduction(10) >= p.damageReduction(90));
    }


    private void dailyChallengeStability() {
        java.time.LocalDate d = java.time.LocalDate.of(2026, 11, 13);
        DailyChallenge.Spec a = DailyChallenge.forDate(d);
        DailyChallenge.Spec b = DailyChallenge.forDate(d);
        assertEq(a.seed(), b.seed(), "同日挑战种子应一致");
        assertEq("2026-11-13", a.dateKey(), "日期键格式应为 YYYY-MM-DD");
        assertTrue("挑战应有标题文案", !a.headline().isBlank());
        assertTrue("挑战应给出规则名", !a.ruleName().isBlank());

        DailyChallenge.Spec other = DailyChallenge.forDate(d.plusDays(1));
        assertNotEq(a.seed(), other.seed(), "不同日期种子应不同");

        assertTrue("评分应随收入上升",
                DailyChallenge.score(8000, 10, 2, 3) > DailyChallenge.score(2000, 10, 2, 3));
        assertTrue("高成功率应得分更高",
                DailyChallenge.score(5000, 9, 1, 2) > DailyChallenge.score(5000, 5, 5, 2));
        assertTrue("成就应加分",
                DailyChallenge.score(5000, 5, 5, 10) > DailyChallenge.score(5000, 5, 5, 0));
        assertTrue("高分评级应为 S：" + DailyChallenge.grade(25000),
                DailyChallenge.grade(25000).startsWith("S"));
        assertTrue("低分评级应为 D", DailyChallenge.grade(100).startsWith("D"));
        assertEq(7, DailyChallenge.upcoming().size(), "应预告未来七天");
        for (DailyChallenge.Spec s : DailyChallenge.upcoming()) {
            assertTrue("预告日期不应为空", !s.dateKey().isBlank());
        }
    }


    private void storylineProgression() {
        StorylineSystem story = new StorylineSystem();
        assertEq(0, story.totalBeats(), "初始无进度");
        assertTrue("初始不应完成", !story.allComplete());
        assertEq(12, story.totalPossible(), "三条线共 12 拍");

        StorylineSystem.Beat first = story.advance(StorylineSystem.Line.GHOST);
        assertTrue("应能推进第一拍", first != null);
        assertEq(1, first.index(), "首拍编号应为 1");
        assertEq("幽灵", first.line(), "支线名应正确");
        assertTrue("文案不应为空", !first.text().isBlank());

        for (int i = 0; i < 3; i++) {
            story.advance(StorylineSystem.Line.GHOST);
        }
        assertTrue("四拍后应完成", story.isComplete(StorylineSystem.Line.GHOST));
        assertTrue("已完成的线不能再推进", story.advance(StorylineSystem.Line.GHOST) == null);
        assertEq(4, story.progress(StorylineSystem.Line.GHOST), "进度应为 4");

        StorylineSystem.Beat last = null;
        while (!story.allComplete()) {
            last = story.advanceAny(new Rng(3L));
            assertTrue("未完成时 advanceAny 应返回一拍", last != null);
        }
        assertTrue("全部完成后应为完成态", story.allComplete());
        assertEq(12, story.totalBeats(), "总拍数应为 12");
        assertTrue("全部完成后 advanceAny 应返回 null", story.advanceAny(new Rng(1L)) == null);

        Map<String, String> saved = story.toSaveEntries();
        StorylineSystem restored = new StorylineSystem();
        restored.fromSaveEntries(saved);
        assertEq(12, restored.totalBeats(), "支线进度应往返");
        assertTrue("往返后应仍是完成态", restored.allComplete());

        Map<String, String> junk = new LinkedHashMap<>();
        junk.put("story.GHOST", "99");
        junk.put("story.ROOF", "oops");
        StorylineSystem tolerant = new StorylineSystem();
        tolerant.fromSaveEntries(junk);
        assertEq(4, tolerant.progress(StorylineSystem.Line.GHOST), "超额进度应被裁剪到上限");
        assertEq(0, tolerant.progress(StorylineSystem.Line.ROOF), "非法值应回退为 0");
    }

    private void contentCoverage() {
        // 变体：11 种原版 + 5 种新增
        assertEq(16, MissionManager.MissionVariant.values().length, "任务变体应为 16 种");
        assertTrue("变体名不应为空", !MissionManager.MissionVariant.ORGAN.displayName.isEmpty());
        assertTrue("新变体应有中文名", MissionManager.MissionVariant.BOMB.displayName.contains("定时"));

        // 任务类型仍是 3 种
        assertEq(3, MissionManager.MissionType.values().length, "任务类型应为 3 种");

        // 事件权重：9 原版 + 3 第一轮 + 4 第二轮 = 16
        int[] weights = {
                GameConfig.POLICE_WEIGHT, GameConfig.GANG_WEIGHT, GameConfig.BREAKDOWN_WEIGHT,
                GameConfig.RIVAL_WEIGHT, GameConfig.RESCUE_WEIGHT, GameConfig.GHOST_WEIGHT,
                GameConfig.GAMBLE_WEIGHT, GameConfig.SCAN_WEIGHT, GameConfig.SCAVENGER_WEIGHT,
                GameConfig.DRONE_WEIGHT, GameConfig.RIPPERDOC_WEIGHT, GameConfig.RADIO_WEIGHT,
                GameConfig.PERFORMER_WEIGHT, GameConfig.PICKPOCKET_WEIGHT,
                GameConfig.EXLOVER_WEIGHT, GameConfig.CORPO_WEIGHT
        };
        assertEq(16, weights.length, "事件权重应为 16 类");
        for (int w : weights) {
            assertTrue("事件权重应为正数：" + w, w > 0);
        }

        // UI 字体必须能显示中文，否则整屏豆腐块（v2.0 曾踩过）
        assertTrue("等宽字体必须支持中文", Theme.mono(13).canDisplay('中'));
        assertTrue("无衬线字体必须支持中文", Theme.sans(13).canDisplay('中'));
        assertTrue("字体必须支持数字", Theme.mono(13).canDisplay('0'));

        // 物品与成就规模
        assertEq(11, Item.values().length, "物品应为 11 件（5 消耗品 + 6 义体）");
        assertEq(24, Achievement.values().length, "成就应为 24 个");
        assertEq(8, SkillTree.Skill.values().length, "技能应为 8 项");
        assertEq(6, WeatherSystem.Weather.values().length, "天气应为 6 种");
        assertEq(4, WeatherSystem.Phase.values().length, "时段应为 4 个");
    }


    // ══════════════ 世界记忆与因果链 ══════════════

    private void memoryArchetypes() {
        Memory m = new Memory();
        assertEq(Memory.Archetype.NOBODY, m.archetype(), "没有任何行为时应是无名之辈");
        assertEq(0, m.totalFacts(), "初始无记录");

        // 纯善：应成为圣人
        Memory saint = new Memory();
        for (int i = 0; i < 3; i++) {
            saint.record(Memory.Fact.SAVED_LIFE);
        }
        saint.record(Memory.Fact.REFUSED_CORPO);
        assertEq(Memory.Archetype.SAINT, saint.archetype(), "多次善行应成为圣人");
        assertTrue("善意计数应正确：" + saint.totalKind(), saint.totalKind() == 4);
        assertTrue("天平均为正：" + saint.moralScore(), saint.moralScore() > 0);

        // 纯恶：应成为屠夫
        Memory butcher = new Memory();
        for (int i = 0; i < 2; i++) {
            butcher.record(Memory.Fact.SOLD_ORGANS);
            butcher.record(Memory.Fact.IGNORED_CRY);
        }
        assertEq(Memory.Archetype.BUTCHER, butcher.archetype(), "多次冷血应成为屠夫");
        assertTrue("天平应为负：" + butcher.moralScore(), butcher.moralScore() < 0);

        // 出卖同行 + 反复贿赂城警 = 公司的人
        Memory company = new Memory();
        company.record(Memory.Fact.SOLD_OUT_COLLEAGUE);
        company.record(Memory.Fact.BRIBED_POLICE);
        company.record(Memory.Fact.BRIBED_POLICE);
        assertEq(Memory.Archetype.COMPANY_MAN, company.archetype(), "卖同行并贿赂城警应成为公司的人");

        // 屋顶：帮拾荒者两次
        Memory roof = new Memory();
        roof.record(Memory.Fact.HELPED_SCAV);
        roof.record(Memory.Fact.HELPED_SCAV);
        assertEq(Memory.Archetype.ROOF_DWELLER, roof.archetype(), "帮拾荒者两次应成为屋顶上的");

        // 幽灵：听劝 + 拆弹
        Memory ghost = new Memory();
        ghost.record(Memory.Fact.LISTENED_GHOST);
        ghost.record(Memory.Fact.DEFUSED_BOMB);
        assertEq(Memory.Archetype.GHOST, ghost.archetype(), "听劝并拆弹应成为幽灵");

        // 中间态
        Memory prag = new Memory();
        prag.record(Memory.Fact.GAMBLED);
        prag.record(Memory.Fact.BRIBED_GANG);
        assertEq(Memory.Archetype.PRAGMATIST, prag.archetype(), "只做中性行为应是现实主义者");
    }

    private void memoryPersistence() {
        Memory m = new Memory();
        m.record(Memory.Fact.SAVED_LIFE);
        m.record(Memory.Fact.SOLD_ORGANS);
        m.record(Memory.Fact.SAVED_LIFE);
        Map<String, String> saved = m.toSaveEntries();
        Memory restored = new Memory();
        restored.fromSaveEntries(saved);
        assertEq(2, restored.count(Memory.Fact.SAVED_LIFE), "计数应往返");
        assertEq(1, restored.count(Memory.Fact.SOLD_ORGANS), "恶行计数应往返");
        assertEq(m.totalFacts(), restored.totalFacts(), "时间线长度应往返");
        assertEq(m.moralScore(), restored.moralScore(), "道德分应往返");
        assertEq(m.archetype(), restored.archetype(), "画像应往返");

        // 结局回顾应按顺序给出
        List<String> recap = restored.recap(10);
        assertEq(3, recap.size(), "回顾应为三条");
        assertTrue("回顾应包含救人的描述", recap.get(0).contains("救了一个人"));
        assertTrue("回顾应包含卖器官的描述", recap.get(1).contains("器官"));

        // 超过上限时只取最近的
        assertEq(1, restored.recap(1).size(), "限制条数应生效");
    }

    private void memoryShiftsEventPool() {
        Memory butcher = new Memory();
        for (int i = 0; i < 2; i++) {
            butcher.record(Memory.Fact.SOLD_ORGANS);
            butcher.record(Memory.Fact.IGNORED_CRY);
        }
        assertTrue("屠夫更容易遇到公司招聘：" + butcher.weightShift("CORPO"),
                butcher.weightShift("CORPO") > 0);
        assertTrue("屠夫更少遇到求救：" + butcher.weightShift("RESCUE"),
                butcher.weightShift("RESCUE") < 0);

        Memory company = new Memory();
        company.record(Memory.Fact.SOLD_OUT_COLLEAGUE);
        company.record(Memory.Fact.BRIBED_POLICE);
        company.record(Memory.Fact.BRIBED_POLICE);
        assertTrue("公司的人更少被城警拦：" + company.weightShift("POLICE"),
                company.weightShift("POLICE") < 0);
        assertTrue("公司的人更少被公司招募：" + company.weightShift("CORPO"),
                company.weightShift("CORPO") < 0);

        Memory roof = new Memory();
        roof.record(Memory.Fact.HELPED_SCAV);
        roof.record(Memory.Fact.HELPED_SCAV);
        assertTrue("屋顶的人更容易遇到拾荒者：" + roof.weightShift("SCAVENGER"),
                roof.weightShift("SCAVENGER") > 0);

        Memory betrayer = new Memory();
        betrayer.record(Memory.Fact.BETRAYED_GANG);
        assertTrue("出卖帮派后更容易被帮派找上门：" + betrayer.weightShift("GANG"),
                betrayer.weightShift("GANG") >= 10);

        Memory nobody = new Memory();
        assertEq(0, nobody.weightShift("POLICE"), "无名之辈不应有偏移");
        assertEq(0, nobody.weightShift("CORPO"), "无名之辈不应有偏移");
    }

    private void eventVoiceReactsToMemory() {
        Memory empty = new Memory();
        assertEq("", EventVoice.opening("POLICE", empty), "无记忆时不应有特殊开场");
        assertEq("", EventVoice.opening("RESCUE", empty), "无记忆时不应有特殊开场");
        assertEq("跑单的", EventVoice.address(empty), "无记忆时称呼应为跑单的");

        Memory saint = new Memory();
        for (int i = 0; i < 3; i++) {
            saint.record(Memory.Fact.SAVED_LIFE);
        }
        saint.record(Memory.Fact.REFUSED_CORPO);
        assertTrue("圣人遇到城警应有特殊开场", !EventVoice.opening("POLICE", saint).isEmpty());
        assertTrue("圣人遇到求救应有特殊开场", !EventVoice.opening("RESCUE", saint).isEmpty());
        assertTrue("圣人称呼应变化：" + EventVoice.address(saint),
                EventVoice.address(saint).contains("圣人"));

        Memory betrayer = new Memory();
        betrayer.record(Memory.Fact.BETRAYED_GANG);
        String gangVoice = EventVoice.opening("GANG", betrayer);
        assertTrue("出卖帮派后帮派开场应变化", !gangVoice.isEmpty());
        assertTrue("开场应明确指向寻仇：" + gangVoice, gangVoice.contains("不是来收过路费"));

        Memory butcher = new Memory();
        for (int i = 0; i < 2; i++) {
            butcher.record(Memory.Fact.SOLD_ORGANS);
            butcher.record(Memory.Fact.IGNORED_CRY);
        }
        assertTrue("屠夫遇到求救时对方应害怕", EventVoice.opening("RESCUE", butcher).contains("低了下去"));
        assertTrue("屠夫的称呼应变化：" + EventVoice.address(butcher),
                EventVoice.address(butcher).contains("屠夫"));

        // 同一事件对不同的人，开场不应相同
        assertNotEq(EventVoice.opening("POLICE", saint), EventVoice.opening("POLICE", butcher),
                "圣人/屠夫遇到城警的开场应不同");
    }

    private void storylineDrivenByFacts() {
        assertEq(StorylineSystem.Line.ROOF, StorylineSystem.lineFor(Memory.Fact.FREED_PET),
                "放走活体货物应触发屋顶线");
        assertEq(StorylineSystem.Line.ROOF, StorylineSystem.lineFor(Memory.Fact.JOINED_WEDDING),
                "参加婚礼应触发屋顶线");
        assertEq(StorylineSystem.Line.GHOST, StorylineSystem.lineFor(Memory.Fact.LISTENED_GHOST),
                "听劝应触发幽灵线");
        assertEq(StorylineSystem.Line.GHOST, StorylineSystem.lineFor(Memory.Fact.DEFUSED_BOMB),
                "拆弹应触发幽灵线");
        assertEq(StorylineSystem.Line.OLD_FRIEND, StorylineSystem.lineFor(Memory.Fact.SAVED_LIFE),
                "救过人应触发旧友线");
        assertEq(null, StorylineSystem.lineFor(Memory.Fact.GAMBLED), "赌局不应触发支线");
        assertEq(null, StorylineSystem.lineFor(null), "null 事实不应触发支线");

        StorylineSystem story = new StorylineSystem();
        StorylineSystem.Beat beat = story.advanceBy(Memory.Fact.FREED_PET);
        assertTrue("行为应直接推进对应的支线", beat != null);
        assertEq("屋顶", beat.line(), "应推进屋顶线");
        assertEq(1, story.progress(StorylineSystem.Line.ROOF), "屋顶线应到第 1 拍");
        assertEq(0, story.progress(StorylineSystem.Line.GHOST), "幽灵线不应被推进");

        // 推进到完成后再触发不再增长
        for (int i = 0; i < 5; i++) {
            story.advanceBy(Memory.Fact.FREED_PET);
        }
        assertEq(4, story.progress(StorylineSystem.Line.ROOF), "最多推进到第 4 拍");

        // 因果闭环：一组行为应产生预期的画像与支线进度
        Memory m = new Memory();
        StorylineSystem s2 = new StorylineSystem();
        m.record(Memory.Fact.SOLD_OUT_COLLEAGUE);
        s2.advanceBy(Memory.Fact.SOLD_OUT_COLLEAGUE);
        m.record(Memory.Fact.BRIBED_POLICE);
        m.record(Memory.Fact.BRIBED_POLICE);
        assertEq(Memory.Archetype.COMPANY_MAN, m.archetype(), "因果闭环：卖同行+贿赂=公司的人");
        assertTrue("卖同行不应推进三条剧情线中的任何一条",
                s2.totalBeats() == 0);
    }


    /**
     * 端到端因果剧本：一条行为如何穿过名声、支线、事件池与结局。
     *
     * <p>这是本次改动的验收核心——它必须证明系统之间是串联的，而不是各跑各的。
     */
    private void causalChainScenario() {
        Memory m = new Memory();
        StorylineSystem story = new StorylineSystem();

        // 第 1 步：起点无名
        assertEq(Memory.Archetype.NOBODY, m.archetype(), "开局应无名");
        assertEq("", EventVoice.opening("RESCUE", m), "开局事件不应有特殊口吻");

        // 第 2 步：救了一个人 → 世界立刻记住
        m.record(Memory.Fact.SAVED_LIFE);
        StorylineSystem.Beat b1 = story.advanceBy(Memory.Fact.SAVED_LIFE);
        assertTrue("救人应立刻开启旧友线", b1 != null);
        assertEq("旧友", b1.line(), "应推进旧友线");
        assertEq("顺路", b1.title(), "旧友第一拍应是「顺路」");
        assertTrue("旧友线此刻应到 1/4", story.progress(StorylineSystem.Line.OLD_FRIEND) == 1);

        // 第 3 步：继续行善 → 画像迁移
        m.record(Memory.Fact.SAVED_LIFE);
        m.record(Memory.Fact.REFUSED_CORPO);
        assertEq(Memory.Archetype.SAINT, m.archetype(), "善行累积后应成为圣人");

        // 第 4 步：画像改变事件池——更少被抢、更多被求助
        assertTrue("圣人更少遇到扒手", m.weightShift("PICKPOCKET") < 0);
        assertTrue("圣人更多遇到求救", m.weightShift("RESCUE") > 0);
        assertTrue("圣人略微少被城警拦", m.weightShift("POLICE") < 0);

        // 第 5 步：画像改变 NPC 口吻——同一事件，开场不同
        String voice = EventVoice.opening("RESCUE", m);
        assertTrue("圣人遇到求救应有特殊开场", !voice.isEmpty());
        assertTrue("开场应体现对方信任你：" + voice, voice.contains("唯一能指望"));
        assertTrue("别人对你的称呼应变成圣人", EventVoice.address(m).contains("圣人"));

        // 第 6 步：结局能逐条回述
        List<String> recap = m.recap(10);
        assertEq(3, recap.size(), "应回述三条行为");
        assertTrue("回述应含救人", recap.get(0).contains("救了一个人"));
        assertTrue("回述应含拒绝收买", recap.get(2).contains("拒绝了公司"));

        // 反向验证：同一批行为换成冷血版本，世界反应应完全不同
        Memory dark = new Memory();
        dark.record(Memory.Fact.SOLD_OUT_COLLEAGUE);
        dark.record(Memory.Fact.BRIBED_POLICE);
        dark.record(Memory.Fact.BRIBED_POLICE);
        assertNotEq(m.archetype(), dark.archetype(), "善恶两条路的画像必须不同");
        assertNotEq(EventVoice.opening("RESCUE", m), EventVoice.opening("RESCUE", dark),
                "同一事件在两种名声下开场必须不同");
        // 注意：COMPANY_MAN 已经被招募，所以招聘事件反而减少；真正「更容易被找上」的是屠夫
        assertTrue("公司的人不再被招聘（已经收编）", dark.weightShift("CORPO") < 0);
        Memory butcher = new Memory();
        butcher.record(Memory.Fact.SOLD_ORGANS);
        butcher.record(Memory.Fact.SOLD_ORGANS);
        butcher.record(Memory.Fact.IGNORED_CRY);
        butcher.record(Memory.Fact.IGNORED_CRY);
        assertTrue("屠夫更容易被公司找上",
                butcher.weightShift("CORPO") > m.weightShift("CORPO"));
        assertTrue("冷血路线的天平均为负", dark.moralScore() < 0);
        assertTrue("行善路线的天平均为正", m.moralScore() > 0);
    }


    // ══════════════ 具名人物关系 ══════════════

    private void contactsImpactsDifferPerPerson() {
        assertTrue("卖同行应让格雷欣赏："
                        + Contacts.impact(Contacts.Who.GRAY, Memory.Fact.SOLD_OUT_COLLEAGUE),
                Contacts.impact(Contacts.Who.GRAY, Memory.Fact.SOLD_OUT_COLLEAGUE) > 0);
        assertTrue("卖同行应让老K 恨你："
                        + Contacts.impact(Contacts.Who.OLD_K, Memory.Fact.SOLD_OUT_COLLEAGUE),
                Contacts.impact(Contacts.Who.OLD_K, Memory.Fact.SOLD_OUT_COLLEAGUE) < 0);
        assertTrue("卖同行应让小满恨你",
                Contacts.impact(Contacts.Who.XIAOMAN, Memory.Fact.SOLD_OUT_COLLEAGUE) < 0);
        assertTrue("拒绝公司应让格雷记恨",
                Contacts.impact(Contacts.Who.GRAY, Memory.Fact.REFUSED_CORPO) < 0);
        assertTrue("拒绝公司应让老K 敬重",
                Contacts.impact(Contacts.Who.OLD_K, Memory.Fact.REFUSED_CORPO) > 0);
        assertTrue("救人不应让格雷喜欢你",
                Contacts.impact(Contacts.Who.GRAY, Memory.Fact.SAVED_LIFE) < 0);
        assertTrue("放走活体货物应让小满喜欢你",
                Contacts.impact(Contacts.Who.XIAOMAN, Memory.Fact.FREED_PET) > 0);

        // 赌局与谁都无关
        assertEq(0, Contacts.impact(Contacts.Who.OLD_K, Memory.Fact.GAMBLED), "赌局不改变关系");
        assertEq(0, Contacts.impact(Contacts.Who.GRAY, Memory.Fact.GAMBLED), "赌局不改变关系");
    }

    private void contactsPersistence() {
        Contacts c = new Contacts();
        c.distribute(Memory.Fact.SOLD_OUT_COLLEAGUE);
        c.distribute(Memory.Fact.SAVED_LIFE);
        c.meet(Contacts.Who.OLD_K);
        c.meet(Contacts.Who.OLD_K);

        Map<String, String> saved = c.toSaveEntries();
        Contacts restored = new Contacts();
        restored.fromSaveEntries(saved);
        assertEq(c.relation(Contacts.Who.GRAY), restored.relation(Contacts.Who.GRAY), "格雷关系应往返");
        assertEq(c.relation(Contacts.Who.OLD_K), restored.relation(Contacts.Who.OLD_K), "老K 关系应往返");
        assertTrue("应标记为已认识", restored.hasMet(Contacts.Who.OLD_K));
        assertEq(2, restored.bond(Contacts.Who.OLD_K).meetings(), "见面次数应往返");
        assertEq(1, restored.bond(Contacts.Who.OLD_K).count(Memory.Fact.SOLD_OUT_COLLEAGUE),
                "共同记忆应往返");
        assertTrue("摘要不应为空", !restored.summary().isEmpty());
        assertTrue("回顾应有条目", !restored.recap().isEmpty());
    }

    private void contactsArcScenario() {
        // 一个人的行为，在五个 NPC 心里留下完全不同的痕迹
        Contacts c = new Contacts();
        c.distribute(Memory.Fact.SOLD_OUT_COLLEAGUE);
        c.distribute(Memory.Fact.FREED_PET);

        assertTrue("格雷欣赏出卖同行的人：" + c.relation(Contacts.Who.GRAY),
                c.relation(Contacts.Who.GRAY) > 0);
        assertTrue("老K 恨出卖同行的人：" + c.relation(Contacts.Who.OLD_K),
                c.relation(Contacts.Who.OLD_K) < 0);
        // 单独看：放走活体货物让小满记你一份好
        Contacts kindOnly = new Contacts();
        kindOnly.distribute(Memory.Fact.FREED_PET);
        assertTrue("小满喜欢放走活体货物的人：" + kindOnly.relation(Contacts.Who.XIAOMAN),
                kindOnly.relation(Contacts.Who.XIAOMAN) > 0);
        // 但出卖同行是更重的一笔，两件事在小满那里互相抵消——关系是净值，不是标签
        assertTrue("两件事在小满那里应互相抵消（" + c.relation(Contacts.Who.XIAOMAN) + "）",
                c.relation(Contacts.Who.XIAOMAN) < kindOnly.relation(Contacts.Who.XIAOMAN));
        assertTrue("老K 仍然敌视你：" + c.stage(Contacts.Who.OLD_K),
                c.stage(Contacts.Who.OLD_K) == Contacts.Stage.ENEMY
                        || c.stage(Contacts.Who.OLD_K) == Contacts.Stage.COLD);

        // 关系影响遭遇权重：极端关系更容易在路上碰见
        int grayWeight = c.encounterWeight(Contacts.Who.GRAY);
        int weiWeight = c.encounterWeight(Contacts.Who.WEI);
        assertTrue("认识的人权重应大于不认识的人：" + grayWeight + " vs " + weiWeight,
                grayWeight > weiWeight);
        assertTrue("初次相遇也应有权重", weiWeight > 0);

        // 敌人与挚友
        Contacts arc = new Contacts();
        for (int i = 0; i < 4; i++) {
            arc.distribute(Memory.Fact.REFUSED_CORPO);
        }
        assertTrue("四次拒绝收买应成生死之交：" + arc.relation(Contacts.Who.OLD_K),
                arc.relation(Contacts.Who.OLD_K) >= Contacts.Stage.BONDED.min());
        assertEq(1, arc.bonded().size(), "应恰好有一位生死之交");
        assertTrue("格雷应成为敌人：" + arc.relation(Contacts.Who.GRAY),
                arc.relation(Contacts.Who.GRAY) <= Contacts.Stage.ENEMY.max());
        assertEq(1, arc.enemies().size(), "应恰好有一位敌人");
        assertEq(Contacts.Who.OLD_K, arc.closest(), "最亲近的应是老K");
        assertEq(Contacts.Who.GRAY, arc.enemy(), "敌对的应是格雷");
    }

    // ══════════════ 工具 ══════════════

    private String readMark(Path file) throws Exception {
        String raw = Files.readString(file, StandardCharsets.UTF_8);
        SaveCodec.DecodeResult r = SaveCodec.decode(raw);
        assertTrue("备份文件应可读：" + file.getFileName(), r.ok());
        return r.ok() ? r.document().get("mark", "") : "";
    }

    private String sha256(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private void deleteRecursively(Path dir) throws Exception {
        if (!Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        }
    }

    private void assertTrue(String msg, boolean cond) {
        if (cond) {
            passed++;
        } else {
            failures.add(msg);
        }
    }

    private void assertEq(Object expected, Object actual, String msg) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
        } else {
            failures.add(msg + "（期望 " + expected + "，实际 " + actual + "）");
        }
    }

    private void assertNotEq(Object forbidden, Object actual, String msg) {
        if (forbidden == null ? actual != null : !forbidden.equals(actual)) {
            passed++;
        } else {
            failures.add(msg + "（不应等于 " + forbidden + "）");
        }
    }
}
