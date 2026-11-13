package com.moji.NightCityCourier.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 跑单档案：v2.0 新增成长层的聚合根。
 *
 * <p>职责边界（重要）：
 * <ul>
 *   <li>{@code Player} 继续管<b>核心数值</b>（钱、血、等级、回响石）与旧存档格式，
 *       保持对既有存档与测试的完全兼容；</li>
 *   <li>{@code CourierProfile} 管<b>成长与收藏</b>（背包、义体、技能、派系、成就、统计），
 *       使用 v3 独立存档；</li>
 *   <li>两者通过本类的 {@code 派生倍率} 单向沟通，不互相持有引用，避免循环依赖。</li>
 * </ul>
 *
 * <p>这样拆的直接好处：新增成长系统不需要动旧的序列化代码，
 * 而旧存档读进来只是「成长层为空」，不会崩。
 */
public final class CourierProfile {

    /** 升到下一级所需经验的基数与斜率。 */
    public static final int XP_BASE = 60;
    public static final int XP_PER_LEVEL = 40;
    public static final int MAX_LEVEL = 30;

    private final Inventory inventory = new Inventory();
    private final SkillTree skills = new SkillTree();
    private final FactionSystem factions = new FactionSystem();
    private final AchievementSystem achievements = new AchievementSystem();
    private final GameStats stats = new GameStats();
    private final WeatherSystem weather = new WeatherSystem();
    private final StorylineSystem storylines = new StorylineSystem();
    private final Memory memory = new Memory();
    private final Contacts contacts = new Contacts();
    private final Set<String> visitedRoutes = new LinkedHashSet<>();

    private int level = 1;
    private int xp = 0;
    private int streak = 0;
    private int bestStreak = 0;
    private int totalSpent = 0;
    private long createdAt = System.currentTimeMillis();

    // ────────── 组件访问 ──────────

    public Inventory inventory() {
        return inventory;
    }

    public SkillTree skills() {
        return skills;
    }

    public FactionSystem factions() {
        return factions;
    }

    public AchievementSystem achievements() {
        return achievements;
    }

    public GameStats stats() {
        return stats;
    }

    public WeatherSystem weather() {
        return weather;
    }

    public StorylineSystem storylines() {
        return storylines;
    }

    /** 世界记忆：所有道德/立场选择的台账，驱动事件改写、支线触发与结局回顾。 */
    public Memory memory() {
        return memory;
    }

    /** 具名人物关系网：老K、小满、格雷、薇、Doc 各自的态度账本。 */
    public Contacts contacts() {
        return contacts;
    }

    public int level() {
        return level;
    }

    public int xp() {
        return xp;
    }

    public int streak() {
        return streak;
    }

    public int bestStreak() {
        return bestStreak;
    }

    public int totalSpent() {
        return totalSpent;
    }

    public long createdAt() {
        return createdAt;
    }

    public Set<String> visitedRoutes() {
        return Set.copyOf(visitedRoutes);
    }

    // ────────── 成长 ──────────

    /** 升到下一级所需经验。等级越高越慢，30 级封顶。 */
    public int xpToNext() {
        return XP_BASE + XP_PER_LEVEL * (level - 1);
    }

    /**
     * 获得经验，可能连升多级。每级发放 1 点技能点。
     *
     * @return 本次提升的等级数
     */
    public int gainXp(int amount) {
        if (amount <= 0 || level >= MAX_LEVEL) {
            return 0;
        }
        xp += amount;
        int gained = 0;
        while (level < MAX_LEVEL && xp >= xpToNext()) {
            xp -= xpToNext();
            level++;
            skills.addPoints(1);
            gained++;
        }
        if (level >= MAX_LEVEL) {
            xp = 0;
        }
        return gained;
    }

    /** 记录一次成功派送：连胜、统计、经验。 */
    public void recordDelivery(int reward, boolean damaged, boolean contraband) {
        streak++;
        bestStreak = Math.max(bestStreak, streak);
        stats.inc(GameStats.DELIVERIES);
        stats.add(GameStats.MONEY_EARNED, reward);
        stats.max(GameStats.BEST_HAUL, reward);
        if (contraband) {
            stats.inc(GameStats.BLACK_MARKET);
        }
        if (!damaged) {
            stats.inc("stat.cleanRuns");
        } else {
            stats.add("stat.dirtyRuns", 1);
        }
        gainXp(20 + reward / 40);
        checkStreakAchievements();
        if (stats.get(GameStats.DELIVERIES) == 1) {
            achievements.unlock(Achievement.FIRST_DELIVERY);
        }
        if (reward > 1000) {
            achievements.unlock(Achievement.WHALE);
        }
    }

    /** 记录一次失败：断连胜。 */
    public void recordFailure(int moneyLost, int damage, boolean contraband) {
        streak = 0;
        stats.inc(GameStats.FAILURES);
        stats.add(GameStats.MONEY_LOST, Math.max(0, moneyLost));
        stats.add(GameStats.DAMAGE_TAKEN, Math.max(0, damage));
        if (contraband) {
            stats.inc(GameStats.BLACK_MARKET);
        }
    }

    public void recordDamage(int amount) {
        if (amount > 0) {
            stats.add(GameStats.DAMAGE_TAKEN, amount);
        }
    }

    public void recordStep() {
        stats.inc(GameStats.STEPS_TRAVELED);
    }

    public void recordEvent() {
        stats.inc(GameStats.EVENTS_SEEN);
    }

    public void recordKindAct() {
        stats.inc(GameStats.KIND_ACTS);
        if (stats.get(GameStats.KIND_ACTS) >= 10) {
            achievements.unlock(Achievement.SAINT);
        }
    }

    public void recordCruelAct() {
        stats.inc(GameStats.CRUEL_ACTS);
        if (stats.get(GameStats.CRUEL_ACTS) >= 10) {
            achievements.unlock(Achievement.BUTCHER);
        }
    }

    public void recordBribe() {
        stats.inc(GameStats.BRIBERY);
        if (stats.get(GameStats.BRIBERY) >= 5) {
            achievements.unlock(Achievement.BRIBER);
        }
    }

    public void recordGamble() {
        stats.inc(GameStats.GAMBLES);
        if (stats.get(GameStats.GAMBLES) >= 5) {
            achievements.unlock(Achievement.GAMBLER);
        }
    }

    public void recordEscape() {
        stats.inc("stat.escapes");
        if (stats.get("stat.escapes") >= 10) {
            achievements.unlock(Achievement.ESCAPE_ARTIST);
        }
    }

    public void recordRoute(String routeName) {
        visitedRoutes.add(routeName);
        stats.inc(GameStats.ROUTES_TAKEN);
        if (visitedRoutes.size() >= 3) {
            achievements.unlock(Achievement.STREET_KNOWLEDGE);
        }
    }

    public void recordSpend(int amount) {
        if (amount > 0) {
            totalSpent += amount;
        }
    }

    public void recordCloseCall() {
        stats.inc(GameStats.CLOSE_CALLS);
        achievements.unlock(Achievement.ALMOST);
    }

    private void checkStreakAchievements() {
        if (streak >= 5) {
            achievements.unlock(Achievement.STREAK_5);
        }
        if (streak >= 10) {
            achievements.unlock(Achievement.STREAK_10);
        }
    }

    /** 每次关键操作后调用，集中检查「状态型」成就。 */
    public void evaluateAchievements(int money, int health, int wantedLevel, int totalTasks) {
        if (money >= 5000) {
            achievements.unlock(Achievement.RICH);
        }
        if (inventory.rigs().size() >= Inventory.MAX_RIG_SLOTS) {
            achievements.unlock(Achievement.FULL_RIG);
        }
        if (wantedLevel >= 5 && totalTasks > 0) {
            achievements.unlock(Achievement.MAX_WANTED_SURVIVE);
        }
        if (skills.rank(SkillTree.Skill.NETRUNNER) >= SkillTree.Skill.NETRUNNER.maxRank()) {
            achievements.unlock(Achievement.NETRUNNER);
        }
        if (factions.tier(FactionSystem.Faction.SCAV) == FactionSystem.Tier.ALLY) {
            achievements.unlock(Achievement.SCAV_TRUST);
        }
        if (health == 1) {
            achievements.unlock(Achievement.ALMOST);
        }
        long nightDeliveries = stats.get("stat.nightDeliveries");
        if (nightDeliveries >= 20) {
            achievements.unlock(Achievement.NIGHT_OWL);
        }
        long stormDeliveries = stats.get("stat.stormDeliveries");
        if (stormDeliveries >= 10) {
            achievements.unlock(Achievement.STORM_RIDER);
        }
        if (stats.get(GameStats.DELIVERIES) >= 10 && stats.get(GameStats.DAMAGE_TAKEN) == 0) {
            achievements.unlock(Achievement.FLAWLESS);
        }
    }

    // ────────── 派生倍率（供主循环查询） ──────────

    /** 义体 + 技能 + 天气共同决定的报酬倍率。 */
    public double rewardMultiplier(boolean contraband) {
        double mul = 1.0 + skills.rewardBonus();
        if (contraband) {
            mul *= 1.0 + inventory.rigBonus(Item.Effect.REWARD_BONUS) / 100.0;
        }
        return mul * weather.rewardMultiplier();
    }

    /** 事件密度倍率：天气时段决定的基准值。 */
    public double encounterMultiplier() {
        return weather.encounterMultiplier();
    }

    /** 事件密度倍率（显式带干扰器状态，避免隐藏副作用）。 */
    public double encounterMultiplier(boolean jammerActive) {
        double mul = weather.encounterMultiplier();
        if (jammerActive) {
            mul *= 0.5;
        }
        return Math.max(0.2, mul);
    }

    /** 闪避加成：技能 + 幽灵芯片。 */
    public double evasionBonus() {
        return skills.evasionBonus() + inventory.rigBonus(Item.Effect.EVASION) / 100.0;
    }

    /** 减伤比例：义体 + 技能（低血量额外）。 */
    public double damageReduction(int currentHealth) {
        double reduction = inventory.rigBonus(Item.Effect.DAMAGE_REDUCE) / 100.0;
        if (currentHealth <= 20) {
            reduction += skills.lastStandReduction();
        }
        return Math.min(0.75, reduction);
    }

    /** 每单可省的赶路步数。 */
    public int stepSave() {
        return inventory.rigBonus(Item.Effect.STEP_SAVE);
    }

    /** 完成任务额外经验。 */
    public int xpBonus() {
        return inventory.rigBonus(Item.Effect.XP_BONUS);
    }

    /** 费用折扣（技能）。 */
    public double costDiscount() {
        return skills.costDiscount();
    }

    public boolean hasBlackIce() {
        return inventory.hasRig(Item.BLACK_ICE);
    }

    // ────────── 存档 ──────────

    public Map<String, String> toSaveEntries() {
        Map<String, String> out = new LinkedHashMap<>();
        out.put("profile.version", "1");
        out.put("profile.created", String.valueOf(createdAt));
        out.put("profile.level", String.valueOf(level));
        out.put("profile.xp", String.valueOf(xp));
        out.put("profile.streak", String.valueOf(streak));
        out.put("profile.bestStreak", String.valueOf(bestStreak));
        out.put("profile.totalSpent", String.valueOf(totalSpent));
        StringBuilder routes = new StringBuilder();
        for (String r : visitedRoutes) {
            if (routes.length() > 0) {
                routes.append('|');
            }
            routes.append(r);
        }
        out.put("profile.routes", routes.toString());
        out.putAll(inventory.toSaveEntries());
        out.putAll(skills.toSaveEntries());
        out.putAll(factions.toSaveEntries());
        out.putAll(achievements.toSaveEntries());
        out.putAll(stats.toSaveEntries());
        out.putAll(weather.toSaveEntries());
        out.putAll(storylines.toSaveEntries());
        out.putAll(memory.toSaveEntries());
        out.putAll(contacts.toSaveEntries());
        return out;
    }

    public void fromSaveEntries(Map<String, String> entries) {
        if (entries == null) {
            return;
        }
        createdAt = parseLong(entries.get("profile.created"), System.currentTimeMillis());
        level = clamp(parseInt(entries.get("profile.level"), 1), 1, MAX_LEVEL);
        xp = Math.max(0, parseInt(entries.get("profile.xp"), 0));
        streak = Math.max(0, parseInt(entries.get("profile.streak"), 0));
        bestStreak = Math.max(0, parseInt(entries.get("profile.bestStreak"), 0));
        totalSpent = Math.max(0, parseInt(entries.get("profile.totalSpent"), 0));
        visitedRoutes.clear();
        String routes = entries.getOrDefault("profile.routes", "");
        for (String r : routes.split("\\|")) {
            if (!r.isBlank()) {
                visitedRoutes.add(r);
            }
        }
        inventory.fromSaveEntries(entries);
        skills.fromSaveEntries(entries);
        factions.fromSaveEntries(entries);
        achievements.fromSaveEntries(entries);
        stats.fromSaveEntries(entries);
        weather.fromSaveEntries(entries);
        storylines.fromSaveEntries(entries);
        memory.fromSaveEntries(entries);
        contacts.fromSaveEntries(entries);
    }

    public void reset() {
        inventory.clear();
        skills.fromSaveEntries(Map.of());
        factions.fromSaveEntries(Map.of());
        achievements.fromSaveEntries(Map.of());
        stats.reset();
        weather.set(WeatherSystem.Weather.CLEAR, WeatherSystem.Phase.NIGHT);
        storylines.fromSaveEntries(Map.of());
        memory.fromSaveEntries(Map.of());
        contacts.fromSaveEntries(Map.of());
        visitedRoutes.clear();
        level = 1;
        xp = 0;
        streak = 0;
        bestStreak = 0;
        totalSpent = 0;
        createdAt = System.currentTimeMillis();
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text == null ? "" : text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long parseLong(String text, long fallback) {
        try {
            return Long.parseLong(text == null ? "" : text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public String toString() {
        return "CourierProfile{lv=" + level + " xp=" + xp + " streak=" + streak
                + " rigs=" + inventory.rigs().size() + " ach=" + achievements.unlockedCount() + "}";
    }
}
