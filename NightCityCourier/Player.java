package com.moji.NightCityCourier;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 玩家类，管理所有玩家属性、状态标记、回响石系统和存档序列化。
 * 实现了 {@link Serializable} 以支持存档读写。
 */
public class Player implements Serializable {
    private static final long serialVersionUID = 1L;

    // ────────── 基础属性常量 ──────────
    private static final int BASE_MAX_HEALTH = 100;
    private static final int MAX_HEALTH_PER_LEVEL = 5;
    private static final int INITIAL_HEALTH = BASE_MAX_HEALTH;
    private static final int MIN_WANTED = 0;
    private static final int MAX_WANTED = 5;
    private static final int MIN_REPUTATION = 0;
    private static final int MAX_REPUTATION = 100;

    // ────────── 核心属性 ──────────
    private final String name;
    private int health = INITIAL_HEALTH;
    private int money = 0;
    private int level = 1;
    private int avoidPolice = 0;
    private int avoidGang = 0;
    private int speed = 1;
    private final int targetMoney = GameConfig.WIN_TARGET;
    private int wantedLevel = 0;
    private int reputation = MIN_REPUTATION;
    private int helpCount = 0;
    private int refuseCount = 0;

    // ────────── 剧情状态标记 ──────────

    private boolean hasRivalFriend = false;
    private boolean gambleRevenge = false;
    private boolean sparedLife = false;
    private boolean stoleFromDead = false;
    private boolean joinedGang = false;
    private boolean liedToLover = false;
    private boolean looted = false;
    private boolean celebEscort = false;
    private boolean ghostAccepted = false;
    private boolean trapSurrendered = false;
    private int scavengerIntel = 0;
    private boolean legacyReturned = false;

    // ────────── 统计计数 ──────────
    private int blackMarketCount = 0;
    private int bribePoliceCount = 0;
    private int bribeGangCount = 0;
    private int totalTasks = 0;
    private int pendingScrutiny = 0;

    // ────────── 道德值 ──────────
    private int humanity = 0;
    private int coldness = 0;

    // ────────── 回响石与物品 ──────────
    private List<EchoStone> echoPool = new ArrayList<>();
    private List<String> inventory = new ArrayList<>();

    /**
     * 反序列化钩子，确保 list 字段不为 null（兼容旧存档）。
     */
    private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (inventory == null) {
            inventory = new ArrayList<>();
        }
        if (echoPool == null) {
            echoPool = new ArrayList<>();
        }
    }

    /**
     * 回响石内部类。
     * 每次关键选择会生成一颗回响石，在结局九道门中呈现为对应关卡。
     */
    public static class EchoStone implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 回响石在九道门中的处理状态 */
        public enum EchoStatus {
            /** 未处理 */
            RAW,
            /** 正面面对 */
            FACED,
            /** 绕过 */
            BYPASSED,
            /** 击碎 */
            SHATTERED
        }

        /** 来源标识，对应 GameConfig 中的 ECHO_* 常量 */
        private String source;
        /** 当前状态 */
        private EchoStatus status;

        public EchoStone(String source) {
            this.source = source;
            this.status = EchoStatus.RAW;
        }

        public String getSource() { return source; }
        public EchoStatus getStatus() { return status; }
        public void setStatus(EchoStatus status) { this.status = status; }
    }

    public Player(String name) {
        this.name = name;
    }

    // ────────── 回响石操作 ──────────

    /** 添加一颗回响石（去重） */
    public void addEcho(String source) {
        for (EchoStone s : echoPool) {
            if (s.getSource().equals(source)) {
                return;
            }
        }
        echoPool.add(new EchoStone(source));
    }

    public List<EchoStone> getEchoPool() { return echoPool; }

    /** 统计指定状态的回响石数量 */
    public int countEchoByStatus(EchoStone.EchoStatus status) {
        int count = 0;
        for (EchoStone s : echoPool) {
            if (s.getStatus() == status) count++;
        }
        return count;
    }

    /** 获取回响石总数 */
    public int totalEchoCount() {
        return echoPool.size();
    }

    // ────────── 属性访问器 ──────────

    public String getName() { return name; }
    public int getMoney() { return money; }
    public void addMoney(int val) { money += val; }
    public void costMoney(int val) { money = Math.max(0, money - val); }
    public int getLevel() { return level; }

    /** 升级：提升等级，同时恢复升级奖励的生命上限 */
    public void levelUp() {
        level++;
        health = Math.min(getMaxHealth(), health + MAX_HEALTH_PER_LEVEL);
    }

    public int getHealth() { return health; }
    /** 最大生命随等级递增：100 + (等级-1) * 5 */
    public int getMaxHealth() { return BASE_MAX_HEALTH + (level - 1) * MAX_HEALTH_PER_LEVEL; }
    public void loseHealth(int amount) { health = Math.max(0, health - amount); }
    public void restoreHealth(int amount) { health = Math.min(getMaxHealth(), health + amount); }
    public boolean isDead() { return health <= 0; }

    public int getAvoidPolice() { return avoidPolice; }
    public void addAvoidPolice() { avoidPolice++; }
    public int getAvoidGang() { return avoidGang; }
    public void addAvoidGang() { avoidGang++; }
    public int getSpeed() { return speed; }
    public void addSpeed() { speed++; }

    public int getTargetMoney() { return targetMoney; }
    public boolean isWin() { return money >= targetMoney; }

    public int getWantedLevel() { return wantedLevel; }
    public void addWantedLevel(int amount) { wantedLevel = Math.min(MAX_WANTED, wantedLevel + amount); }
    public void reduceWantedLevel(int amount) { wantedLevel = Math.max(MIN_WANTED, wantedLevel - amount); }
    public boolean isWantedMax() { return wantedLevel >= MAX_WANTED; }

    public int getReputation() { return reputation; }
    public void addReputation(int amount) { reputation = Math.min(MAX_REPUTATION, reputation + amount); }
    public void reduceReputation(int amount) { reputation = Math.max(MIN_REPUTATION, reputation - amount); }

    public int getHelpCount() { return helpCount; }
    public void addHelpCount() { helpCount++; }
    public int getRefuseCount() { return refuseCount; }
    public void addRefuseCount() { refuseCount++; }

    // ────────── 剧情状态标记访问器 ──────────

    public boolean hasRivalFriend() { return hasRivalFriend; }
    public void setRivalFriend(boolean v) { hasRivalFriend = v; }
    public boolean hasGambleRevenge() { return gambleRevenge; }
    public void setGambleRevenge(boolean v) { gambleRevenge = v; }
    public boolean hasSparedLife() { return sparedLife; }
    public void setSparedLife(boolean v) { sparedLife = v; }
    public boolean hasStoleFromDead() { return stoleFromDead; }
    public void setStoleFromDead(boolean v) { stoleFromDead = v; }
    public boolean hasJoinedGang() { return joinedGang; }
    public void setJoinedGang(boolean v) { joinedGang = v; }
    public boolean hasLiedToLover() { return liedToLover; }
    public void setLiedToLover(boolean v) { liedToLover = v; }
    public boolean hasLooted() { return looted; }
    public void setLooted(boolean v) { looted = v; }
    public boolean hasCelebEscort() { return celebEscort; }
    public void setCelebEscort(boolean v) { celebEscort = v; }
    public boolean hasGhostAccepted() { return ghostAccepted; }
    public void setGhostAccepted(boolean v) { ghostAccepted = v; }
    /** 消耗一次幽灵帮助，使用后不可重复 */
    public void consumeGhostHelp() { ghostAccepted = false; }
    public boolean hasTrapSurrendered() { return trapSurrendered; }
    public void setTrapSurrendered(boolean v) { trapSurrendered = v; }
    public boolean hasScavengerIntel() { return scavengerIntel > 0; }
    public void addScavengerIntel() { scavengerIntel++; }
    public void consumeScavengerIntel() { if (scavengerIntel > 0) scavengerIntel--; }
    public int getScavengerIntelCount() { return scavengerIntel; }
    public boolean hasLegacyReturned() { return legacyReturned; }
    public void setLegacyReturned(boolean v) { legacyReturned = v; }

    public int getPendingScrutinyCount() { return pendingScrutiny; }

    // ────────── 统计计数访问器 ──────────

    public int getBlackMarketCount() { return blackMarketCount; }
    public void addBlackMarketCount() { blackMarketCount++; }
    public int getBribePoliceCount() { return bribePoliceCount; }
    public void addBribePoliceCount() { bribePoliceCount++; }
    public int getBribeGangCount() { return bribeGangCount; }
    public void addBribeGangCount() { bribeGangCount++; }
    public int getTotalTasks() { return totalTasks; }
    public void addTotalTasks() { totalTasks++; }
    /** 获取城警额外审查计数（黑市交易后生效） */
    public int getPendingScrutiny() { return pendingScrutiny; }
    public void setPendingScrutiny(int v) { pendingScrutiny = v; }
    public void decrementPendingScrutiny() { if (pendingScrutiny > 0) pendingScrutiny--; }

    public int getHumanity() { return humanity; }
    public void addHumanity() { humanity++; }
    public int getColdness() { return coldness; }
    public void addColdness() { coldness++; }

    public List<String> getInventory() { return inventory; }
    public void addInventoryItem(String item) { inventory.add(item); }
    public boolean hasInventoryItem(String item) { return inventory.contains(item); }
    public void consumeInventoryItem(String item) { inventory.remove(item); }

    // ────────── 存档系统 ──────────

    private static final String SAVE_DIR = System.getProperty("user.home") + File.separator + ".nightcity";
    private static final String SAVE_FILE = SAVE_DIR + File.separator + "save.dat";

    /**
     * 将玩家数据序列化存档至 ~/.nightcity/save.dat
     */
    public static void save(Player player) {
        try {
            new File(SAVE_DIR).mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(SAVE_FILE))) {
                oos.writeObject(player);
            }
            System.out.println("【确认】存档成功");
        } catch (IOException e) {
            System.out.println("【警告】存档失败: " + e.getMessage());
        }
    }

    /**
     * 从存档文件加载玩家数据，失败时自动备份并删除损坏的存档。
     *
     * @return 加载成功的 Player 对象，失败返回 null
     */
    public static Player load() {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(SAVE_FILE))) {
            return (Player) ois.readObject();
        } catch (ClassNotFoundException e) {
            System.out.println("【警告】存档数据不兼容: " + e.getMessage());
            deleteSave();
        } catch (IOException e) {
            System.out.println("【警告】读取存档失败: " + e.getMessage());
            deleteSave();
        }
        return null;
    }

    /** 删除损坏的存档文件，优先备份为 .bak */
    private static void deleteSave() {
        File f = new File(SAVE_FILE);
        if (f.exists()) {
            File bak = new File(SAVE_FILE + ".bak");
            if (f.renameTo(bak)) {
                System.out.println("【提示】损坏的存档已备份为 " + bak.getName());
            } else if (!f.delete()) {
                System.out.println("【警告】无法删除损坏的存档文件");
            }
        }
    }

    /** 检查存档文件是否存在 */
    public static boolean hasSave() {
        return new File(SAVE_FILE).exists();
    }
}