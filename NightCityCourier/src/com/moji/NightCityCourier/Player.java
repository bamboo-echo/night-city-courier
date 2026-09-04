package com.moji.NightCityCourier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    private static final String DEFAULT_SAVE_FILE = System.getProperty("user.home")
            + File.separator + ".nightcity" + File.separator + "save.dat";

    /**
     * 实际使用的存档文件路径。默认为 ~/.nightcity/save.dat。
     * 仅在测试中通过 {@link #setSaveFileForTest(String)} 覆盖，生产代码不应改动。
     */
    private static volatile String SAVE_FILE = DEFAULT_SAVE_FILE;

    /** 仅测试使用：指向临时存档路径，避免污染真实用户存档 */
    static void setSaveFileForTest(String path) {
        SAVE_FILE = path;
    }

    /** 存档格式版本。Player 结构发生不兼容变更时递增，旧版本存档不会被静默丢弃。 */
    static final int SAVE_VERSION = 2;

    /**
     * 将玩家数据存档至 ~/.nightcity/save.dat。
     * <p>采用「魔数 + 版本 + SHA-256 校验和 + Properties 明文」格式，相比旧的裸 Java 序列化：
     * <ul>
     *   <li>跨版本兼容：版本不符时拒绝加载并给出明确提示，而不是静默删档；</li>
     *   <li>抗损坏：读取前校验摘要，损坏的存档会报错而不是抛栈跟踪；</li>
     *   <li>可读可迁移：纯文本，便于备份、调试与跨平台迁移；</li>
     *   <li>不依赖外部库：纯 JDK 实现，保持项目零依赖。</li>
     * </ul>
     */
    public static void save(Player player) {
        if (player == null) {
            System.out.println("【警告】存档失败：玩家对象为空。");
            return;
        }
        try {
            new File(SAVE_FILE).getParentFile().mkdirs();
            Files.writeString(Path.of(SAVE_FILE), serializeFull(player), StandardCharsets.UTF_8);
            System.out.println("【确认】存档成功");
        } catch (IOException e) {
            System.out.println("【警告】存档失败: " + e.getMessage());
        }
    }

    /**
     * 从存档文件加载玩家数据。
     *
     * @return 加载成功的 Player 对象；文件不存在、格式不兼容、校验失败或解析错误均返回 null
     */
    public static Player load() {
        File f = new File(SAVE_FILE);
        if (!f.exists()) {
            return null;
        }
        try {
            String raw = Files.readString(Path.of(SAVE_FILE), StandardCharsets.UTF_8);
            // 注意：split 的 limit=1 表示「不拆分」，会把整个文件当成第 0 段，
            // 导致魔数检查永远失败。必须用 -1 才能真正按换行切分。
            if (!"NCCv".equals(raw.split("\n", -1)[0].trim())) {
                System.out.println("【警告】存档格式不受支持（非本程序生成的存档）。");
                backupSave();
                return null;
            }
            int version = -1;
            String[] parts = raw.split("\n", 4);
            if (parts.length >= 2) {
                try {
                    version = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException ignored) {
                }
            }
            if (version != SAVE_VERSION) {
                System.out.println("【警告】存档版本不兼容（存档 v" + version + " / 当前 v" + SAVE_VERSION
                        + "），已备份，本次将开始新游戏。");
                backupSave();
                return null;
            }
            Player loaded = deserializeFull(raw);
            if (loaded == null) {
                System.out.println("【警告】存档校验失败或解析错误，文件可能已损坏，已备份。");
                backupSave();
                return null;
            }
            return loaded;
        } catch (IOException e) {
            System.out.println("【警告】读取存档失败: " + e.getMessage());
            backupSave();
        } catch (Exception e) {
            System.out.println("【警告】存档数据解析异常: " + e.getMessage());
            backupSave();
        }
        return null;
    }

    /** 将当前存档备份为 .bak（不会覆盖已有备份） */
    private static void backupSave() {
        File f = new File(SAVE_FILE);
        if (!f.exists()) {
            return;
        }
        File bak = new File(SAVE_FILE + ".bak");
        if (bak.exists()) {
            return;
        }
        if (f.renameTo(bak)) {
            System.out.println("【提示】问题存档已备份为 " + bak.getName() + "");
        } else {
            System.out.println("【提示】无法备份存档文件，请手动处理 " + f.getAbsolutePath());
        }
    }

    /** 检查存档文件是否存在 */
    public static boolean hasSave() {
        return new File(SAVE_FILE).exists();
    }

    /**
     * 写入带魔数/版本/校验和的完整存档文本。提取为独立方法以便单元测试直接验证校验逻辑。
     */
    static String serializeFull(Player player) {
        String props = player.toPlainText();
        try {
            byte[] payload = props.getBytes(StandardCharsets.UTF_8);
            String checksum = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(payload));
            return "NCCv\n" + SAVE_VERSION + "\n" + checksum + "\n" + props;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    /**
     * 按完整存档文本格式解析（含魔数、版本、校验和）。提取为独立方法便于单元测试。
     *
     * @return 加载成功的 Player；格式不兼容、校验失败或解析失败均返回 null
     */
    static Player deserializeFull(String content) {
        String[] parts = content.split("\n", 4);
        if (parts.length < 4) {
            return null;
        }
        if (!"NCCv".equals(parts[0].trim())) {
            return null;
        }
        int version;
        try {
            version = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (version != SAVE_VERSION) {
            return null;
        }
        try {
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(parts[3].getBytes(StandardCharsets.UTF_8)));
            if (!parts[2].trim().equals(actual)) {
                return null;
            }
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
        return fromPlainText(parts[3]);
    }

    // ────────── 明文序列化（内部使用） ──────────

    /**
     * 将所有玩家状态写为 key=value 形式的纯文本。
     * <p>不用 {@link java.util.Properties}：Properties 自带转义规则
     * （{@code \;} 会被还原成 {@code ;}），会提前解除我们的转义，
     * 导致值里的分号被误当成分隔符。这里自己按行解析，完全可控。
     */
    private String toPlainText() {
        StringBuilder sb = new StringBuilder(2048);
        sb.append("name=").append(escape(name)).append("\n");
        sb.append("level=").append(level).append("\n");
        sb.append("health=").append(health).append("\n");
        sb.append("money=").append(money).append("\n");
        sb.append("reputation=").append(reputation).append("\n");
        sb.append("wantedLevel=").append(wantedLevel).append("\n");
        sb.append("avoidPolice=").append(avoidPolice).append("\n");
        sb.append("avoidGang=").append(avoidGang).append("\n");
        sb.append("speed=").append(speed).append("\n");
        sb.append("helpCount=").append(helpCount).append("\n");
        sb.append("refuseCount=").append(refuseCount).append("\n");
        sb.append("hasRivalFriend=").append(hasRivalFriend).append("\n");
        sb.append("gambleRevenge=").append(gambleRevenge).append("\n");
        sb.append("sparedLife=").append(sparedLife).append("\n");
        sb.append("stoleFromDead=").append(stoleFromDead).append("\n");
        sb.append("joinedGang=").append(joinedGang).append("\n");
        sb.append("liedToLover=").append(liedToLover).append("\n");
        sb.append("looted=").append(looted).append("\n");
        sb.append("celebEscort=").append(celebEscort).append("\n");
        sb.append("ghostAccepted=").append(ghostAccepted).append("\n");
        sb.append("trapSurrendered=").append(trapSurrendered).append("\n");
        sb.append("scavengerIntel=").append(scavengerIntel).append("\n");
        sb.append("legacyReturned=").append(legacyReturned).append("\n");
        sb.append("blackMarketCount=").append(blackMarketCount).append("\n");
        sb.append("bribePoliceCount=").append(bribePoliceCount).append("\n");
        sb.append("bribeGangCount=").append(bribeGangCount).append("\n");
        sb.append("totalTasks=").append(totalTasks).append("\n");
        sb.append("pendingScrutiny=").append(pendingScrutiny).append("\n");
        sb.append("humanity=").append(humanity).append("\n");
        sb.append("coldness=").append(coldness).append("\n");
        sb.append("inventory=").append(joinEscaped(inventory)).append("\n");
        StringBuilder eb = new StringBuilder();
        for (EchoStone stone : echoPool) {
            if (eb.length() > 0) eb.append(";");
            eb.append(escape(stone.getSource())).append("=").append(stone.getStatus().name());
        }
        sb.append("echoes=").append(eb).append("\n");
        return sb.toString();
    }

    /** 从纯文本恢复玩家对象，任何解析异常均返回 null */
    private static Player fromPlainText(String text) {
        Map<String, String> kv = parsePlainText(text);
        Player player = new Player(unescape(kv.getOrDefault("name", "快递员")));
        player.level = readInt(kv, "level", 1);
        player.health = readInt(kv, "health", player.getMaxHealth());
        player.money = readInt(kv, "money", 0);
        player.reputation = readInt(kv, "reputation", MIN_REPUTATION);
        player.wantedLevel = readInt(kv, "wantedLevel", MIN_WANTED);
        player.avoidPolice = readInt(kv, "avoidPolice", 0);
        player.avoidGang = readInt(kv, "avoidGang", 0);
        player.speed = readInt(kv, "speed", 1);
        player.helpCount = readInt(kv, "helpCount", 0);
        player.refuseCount = readInt(kv, "refuseCount", 0);
        player.hasRivalFriend = readBool(kv, "hasRivalFriend");
        player.gambleRevenge = readBool(kv, "gambleRevenge");
        player.sparedLife = readBool(kv, "sparedLife");
        player.stoleFromDead = readBool(kv, "stoleFromDead");
        player.joinedGang = readBool(kv, "joinedGang");
        player.liedToLover = readBool(kv, "liedToLover");
        player.looted = readBool(kv, "looted");
        player.celebEscort = readBool(kv, "celebEscort");
        player.ghostAccepted = readBool(kv, "ghostAccepted");
        player.trapSurrendered = readBool(kv, "trapSurrendered");
        player.scavengerIntel = readInt(kv, "scavengerIntel", 0);
        player.legacyReturned = readBool(kv, "legacyReturned");
        player.blackMarketCount = readInt(kv, "blackMarketCount", 0);
        player.bribePoliceCount = readInt(kv, "bribePoliceCount", 0);
        player.bribeGangCount = readInt(kv, "bribeGangCount", 0);
        player.totalTasks = readInt(kv, "totalTasks", 0);
        player.pendingScrutiny = readInt(kv, "pendingScrutiny", 0);
        player.humanity = readInt(kv, "humanity", 0);
        player.coldness = readInt(kv, "coldness", 0);

        for (String item : splitEscaped(kv.getOrDefault("inventory", ""))) {
            String value = unescape(item);
            if (!value.isEmpty()) {
                player.inventory.add(value);
            }
        }
        for (String pair : splitEscaped(kv.getOrDefault("echoes", ""))) {
            int sep = findUnescaped('=', pair);
            if (sep < 0) {
                continue;
            }
            String source = unescape(pair.substring(0, sep));
            if (source.isEmpty()) {
                continue;
            }
            EchoStone stone = new EchoStone(source);
            try {
                stone.setStatus(EchoStone.EchoStatus.valueOf(pair.substring(sep + 1).trim()));
            } catch (IllegalArgumentException ignored) {
                // 未知状态值时保持默认 RAW，不中断加载
            }
            player.echoPool.add(stone);
        }
        player.clampToValidRange();
        return player;
    }

    /**
     * 逐行解析 key=value 文本。只按<strong>未转义</strong>的首个 '=' 切分，
     * 因此值里出现 '=' 也没关系。重复 key 以最后一次出现为准。
     */
    private static Map<String, String> parsePlainText(String text) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : text.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            int sep = findUnescaped('=', line);
            if (sep <= 0) {
                continue;
            }
            String key = line.substring(0, sep).trim();
            if (!key.isEmpty()) {
                map.put(key, line.substring(sep + 1));
            }
        }
        return map;
    }

    /** 按未转义的 ';' 切分，保留转义序列原样（交给调用方 unescape） */
    private static List<String> splitEscaped(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        StringBuilder cur = new StringBuilder();
        boolean esc = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (esc) {
                cur.append('\\').append(c);
                esc = false;
            } else if (c == '\\') {
                esc = true;
            } else if (c == ';') {
                result.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        if (esc) {
            cur.append('\\');
        }
        result.add(cur.toString());
        return result;
    }

    /** 用 ';' 连接一组字符串，逐元素转义 */
    private static String joinEscaped(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String item : items) {
            if (sb.length() > 0) sb.append(";");
            sb.append(escape(item));
        }
        return sb.toString();
    }

    /** 返回首个<strong>未转义</strong> sep 的下标，没有则返回 -1 */
    private static int findUnescaped(char sep, String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\') {
                i++;
                continue;
            }
            if (c == sep) {
                return i;
            }
        }
        return -1;
    }

    /** 将所有数值限制在合法范围内，防止异常存档导致运行时异常 */
    private void clampToValidRange() {
        level = Math.max(1, level);
        health = Math.max(0, Math.min(health, getMaxHealth()));
        money = Math.max(0, money);
        reputation = Math.max(MIN_REPUTATION, Math.min(reputation, MAX_REPUTATION));
        wantedLevel = Math.max(MIN_WANTED, Math.min(wantedLevel, MAX_WANTED));
        speed = Math.max(1, speed);
        avoidPolice = Math.max(0, avoidPolice);
        avoidGang = Math.max(0, avoidGang);
        scavengerIntel = Math.max(0, scavengerIntel);
        pendingScrutiny = Math.max(0, pendingScrutiny);
        humanity = Math.max(0, humanity);
        coldness = Math.max(0, coldness);
        helpCount = Math.max(0, helpCount);
        refuseCount = Math.max(0, refuseCount);
        totalTasks = Math.max(0, totalTasks);
        blackMarketCount = Math.max(0, blackMarketCount);
        bribePoliceCount = Math.max(0, bribePoliceCount);
        bribeGangCount = Math.max(0, bribeGangCount);
        if (inventory == null) {
            inventory = new ArrayList<>();
        }
        if (echoPool == null) {
            echoPool = new ArrayList<>();
        }
    }

    private static int readInt(Map<String, String> kv, String key, int fallback) {
        String v = kv.get(key);
        if (v == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static boolean readBool(Map<String, String> kv, String key) {
        String v = kv.get(key);
        return v != null && "true".equalsIgnoreCase(v.trim());
    }

    /**
    /**
     * 逐字符转义反斜杠、'='、';' 与换行。
     * <p>不使用 {@link String#replace} 链：那种写法下 {@code \n -> 换行} 会先于
     * {@code \\ -> \} 生效，把字面量 {@code "a\n"}（反斜杠加字母 n）误还原成真换行。
     * 只有分隔符和换行需要转义，'{'|'}' 之类不是分隔符，转义它们只会徒增出错面。
     */
    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\':  sb.append("\\\\"); break;
                case '=':   sb.append("\\=");  break;
                case ';':   sb.append("\\;");  break;
                case '\n':  sb.append("\\n");  break;
                case '\r':  sb.append("\\r");  break;
                default:    sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 逐字符反转义。遇到不在白名单里的转义序列（如 {@code \z}）原样保留，
     * 避免损坏数据；转义序列之外的单一反斜杠也原样保留（容错旧/异常存档）。
     */
    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char next = s.charAt(++i);
            switch (next) {
                case '\\':  sb.append('\\');  break;
                case '=':   sb.append('=');   break;
                case ';':   sb.append(';');   break;
                case 'n':   sb.append('\n');  break;
                case 'r':   sb.append('\r');  break;
                default:    sb.append('\\').append(next);
            }
        }
        return sb.toString();
    }
}
