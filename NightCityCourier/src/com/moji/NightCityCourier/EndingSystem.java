package com.moji.NightCityCourier;
import java.util.*;

/**
 * 结局系统，负责结局判定、九道门机制和终局叙事。
 * 包含内部类 EchoPool 用于记录事件遭遇频次和回响内容。
 * <p>
 * 结局判定逻辑：根据回响石、状态标记、声望和选择综合判定8种结局：
 * 自由、囚徒、夜城之王、幽灵、传奇英雄、堕落枭雄、牺牲、无人知晓。
 */
public class EndingSystem {

    public static class EchoPool {
        private final Map<String, Integer> encounterCounts;

        public EchoPool() {
            this.encounterCounts = new HashMap<>();
        }

        public void recordEncounter(String eventKey) {
            encounterCounts.merge(eventKey, 1, Integer::sum);
        }

        public int getEncounterCount(String eventKey) {
            return encounterCounts.getOrDefault(eventKey, 0);
        }

        public boolean shouldTriggerEcho(String eventKey, double probability) {
            return GameConfig.RANDOM.nextDouble() < probability;
        }

        public String getEchoText(String eventKey, int count) {
            return switch (eventKey) {
                case "POLICE" -> count == 1
                        ? "你想起第一次被城警追的时候。手抖得连油门都拧不稳。"
                        : "城警的巡逻车还是老样子。只是你现在已经不会手抖了。";
                case "GANG" -> count == 1
                        ? "同样的面孔，同样的狠话。夜之城的帮派从不创新。"
                        : "帮派分子看到你眼熟。\"又是你。\"他们说。";
                case "RESCUE" -> count == 1
                        ? "巷口的呼救声。和上一次几乎一样。"
                        : "你又听到了那个声音。夜之城总是需要拯救。";
                case "RIVAL" -> count == 1
                        ? "那个快递员的脸闪过脑海。你们曾经并肩。"
                        : "同行之间的默契。一个眼神就够了。";
                case "GAMBLE" -> count == 1
                        ? "骰子落地的声音。和那天一样清脆。"
                        : "你又看到了那块屏幕。血溅在上面，新的，旧的，都一样。";
                case "GHOST" -> count == 1
                        ? "通讯器里的杂音。那个声音还在等你。"
                        : "幽灵又找到了你。它总是有办法。";
                case "SCAVENGER" -> count == 1
                        ? "废弃工厂里的那个声音。\"宝贝\"还是老一套。"
                        : "拾荒者认出了你。\"又见面了。\"";
                case "BREAKDOWN" -> count == 1
                        ? "义体过热。和上次一样的位置，一样的灼痛。"
                        : "老毛病了。你的车和你一样，都在勉强撑着。";
                case "SCAN" -> count == 1
                        ? "蓝色光栅。第一次通过时你心跳加速。"
                        : "扫描点。你已经学会了不紧张。";
                default -> "一段模糊的记忆涌上心头。";
            };
        }
    }

    private static class Gate {
        String name;
        String scene;
        String echoSource;
        String faceMessage;
        String bypassMessage;
        String shatterMessage;
        boolean isEmpty;
        Gate(String name, String scene, String echoSource, String faceMessage, String bypassMessage, String shatterMessage) {
            this.name = name;
            this.scene = scene;
            this.echoSource = echoSource;
            this.faceMessage = faceMessage;
            this.bypassMessage = bypassMessage;
            this.shatterMessage = shatterMessage;
            this.isEmpty = false;
        }
        static Gate emptyGate(String name, String scene, String faceMessage, String bypassMessage, String shatterMessage) {
            Gate g = new Gate(name, scene, null, faceMessage, bypassMessage, shatterMessage);
            g.isEmpty = true;
            return g;
        }
    }

    public enum EndingType {
        GAMBLE_REVENGE, BURNOUT, NCPD_ARREST,
        LEGEND_HERO, FALLEN_KINGPIN, BROTHERHOOD,
        GRAY_WALKER, QUIET_ESCAPE
    }

    private final Player player;
    private final List<Gate> gates;
    private String mirrorState = "完整";

    public EndingSystem(Player player) {
        this.player = player;
        this.gates = buildGates();
    }

    private List<Gate> buildGates() {
        List<Gate> result = new ArrayList<>();
        addLivingGate(result);
        addRescueGate(result);
        addBribeGate(result);
        addRivalGate(result);
        addGambleGate(result);
        addGhostGate(result);
        addScavengerGate(result);
        addLegacyGate(result);

        if (result.isEmpty()) {
            result.add(Gate.emptyGate(
                "平凡之门",
                "门内是一面普通的镜子，映出你日复一日送货的身影。没有传奇，没有波澜。",
                "你看着镜中的自己。没有声音，没有回响。只有引擎的轰鸣在很远的地方。",
                "你绕过平凡之门。日子还是一天天过，没什么改变。",
                "你打碎了这扇门。碎片落地，和每一天的碎碗声一样普通。"
            ));
        }

        while (result.size() < 9) {
            result.add(createEmptyGateForSlot(result.size()));
        }

        if (result.size() > 9) {
            result = new ArrayList<>(result.subList(0, 9));
        }
        return result;
    }

    private Gate createEmptyGateForSlot(int index) {
        String[] emptyNames = {"沉默之门", "虚无之门", "遗忘之门", "空巷之门", "雨门", "回声之门", "荒废之门"};
        String[] emptyScenes = {
            "门内什么也没有。只有夜之城的风从门缝里灌进来，带着雨水的味道。",
            "空荡荡的门后，一面墙。墙上用喷漆写着：你本来可以不这样。",
            "门内是一段被删掉的记忆。你确定它存在过，但什么都想不起来。",
            "空的。只有你自己的脚步声在隧道里回荡。一声，两声，三声。",
            "门后是雨。没有别的，就是夜之城无休止的雨。打在脸上，冰凉。",
            "门内有一面碎裂的屏幕，播放着你从未见过的画面。",
            "空的。你伸手进去，只抓到一把灰。"
        };
        String[] emptyFace = {
            "你走进去，什么都没有发生。也许这就是答案。",
            "你站在空门中央。什么也没有改变，但你知道它曾经存在过。",
            "你面对这片虚无。它回以沉默。",
            "你闭上眼睛，试图从空无中找到什么。但你只找到自己。",
            "你接受了这片空。有时候，什么都没有就是全部。",
            "你凝视着空门深处。门也凝视着你。你们互不相欠。",
            "你把双手放在空门的内壁上。金属的冰冷顺着指尖传进来。这就是全部了。"
        };
        String[] emptyBypass = {
            "你绕过空门。它空着，你也是。",
            "你没有停留。空门不值得你看第二眼。",
            "你加快脚步走过。空洞的风从身后追来。",
            "你选择忽略它。也许下次它会不一样。也许不会。",
            "你从空门旁边走过。没有回头。它也不值得回头。",
            "你绕过了它。空洞里什么也没有，连遗憾都没有。",
            "你的脚步没有停。空门在你身后关上了，没有声音。"
        };
        String[] emptyShatter = {
            "你打碎了一扇空门。碎片落了一地，里面什么也没有。",
            "空门碎的时候，你听到一声叹息。不是你的。",
            "你把虚无砸成更小的虚无。碎片在空中飘了一会儿，消失了。",
            "你一脚踹碎了空门。后面还是空的。",
            "碎片飞出去，在隧道里闪烁了一秒，然后归于寂静。",
            "你砸碎了空门。里面连回声都没有，只有更深的空。",
            "门碎了。你伸手接住一块碎片，它在你指尖化成粉末。"
        };
        int base = index % 7;
        int nameIdx = base % emptyNames.length;
        int sceneIdx = base % emptyScenes.length;
        int faceIdx = base % emptyFace.length;
        int bypassIdx = base % emptyBypass.length;
        int shatterIdx = base % emptyShatter.length;
        return Gate.emptyGate(
            emptyNames[nameIdx],
            emptyScenes[sceneIdx],
            emptyFace[faceIdx],
            emptyBypass[bypassIdx],
            emptyShatter[shatterIdx]
        );
    }

    private void addLivingGate(List<Gate> result) {
        if (hasEcho(GameConfig.ECHO_LIVING_SAVED)) {
            result.add(new Gate(
                "活体之门",
                "赛博犬站在门内，尾巴轻轻摇晃。它记得你的味道。",
                GameConfig.ECHO_LIVING_SAVED,
                "它轻轻叫了一声，像是认得你。尾巴扫过地面，发出细碎的金属摩擦声。",
                "赛博犬目送你离开。它的眼睛里没有怨恨，只有困惑。\"为什么你不带我走？\"你听不见。",
                "你一掌拍碎了门。赛博犬呜咽一声，消失在碎片中。你不愿承认，你曾经救过它。"
            ));
        } else if (hasEcho(GameConfig.ECHO_LIVING_DIED)) {
            result.add(new Gate(
                "活体之门",
                "赛博犬躺在门内，眼睛半闭。你来晚了。",
                GameConfig.ECHO_LIVING_DIED,
                "门内传来一声低低的呜咽，然后归于寂静。它的眼睛再也没有睁开。",
                "你绕过这扇门。它还躺在那里，一动不动。你告诉自己，那不是你的错。",
                "你踩着它的残骸走过去。它曾经是一条生命。你不敢看它的眼睛。"
            ));
        }
    }

    private void addRescueGate(List<Gate> result) {
        if (player.getHelpCount() > 0) {
            String scene = "你救过的人站成一排。";
            if (player.hasSparedLife()) scene += " 那个被你原价给了药的家属也在其中。";
            if (player.hasCelebEscort()) scene += " 明星对你微微点头。";
            scene += " 没有人说话。";
            result.add(new Gate(
                "救人之门",
                scene,
                GameConfig.ECHO_RESCUE_SAVED,
                "没有人说话，但所有眼神都在说：谢谢你。一个孩子的手轻轻拉住了你的衣角。",
                "你选择不看他们。有些帮助，注定没有回报。你加快脚步离开了。",
                "你把门砸烂。他们四散逃离，像你从未救过一样。夜之城的雨淋在他们身上。"
            ));
        }
    }

    private void addBribeGate(List<Gate> result) {
        int policeCount = player.getBribePoliceCount();
        int gangCount = player.getBribeGangCount();
        if (policeCount + gangCount >= 2) {
            int total = policeCount + gangCount;
            String scene = "门上刻着你累计的贿赂金额。门内是收过你钱的警察和帮派成员的背影。"
                    + " 你贿赂过 " + total + " 次。他们甚至没有回头。";
            String echoSource = policeCount > 0 ? GameConfig.ECHO_BRIBE_POLICE : GameConfig.ECHO_BRIBE_GANG;
            result.add(new Gate(
                "贿赂之门",
                scene,
                echoSource,
                "门内传来硬币落地的声音，然后是笑声。你不知道是谁在笑。",
                "你假装没看见这扇门。钱能解决的问题，都不是问题。你不想知道他们拿了钱之后做了什么。",
                "门碎成渣。你的钱也变成渣。贿赂记录还在夜之城的数据库里，从未消失。"
            ));
        }
    }

    private void addRivalGate(List<Gate> result) {
        if (player.hasRivalFriend()) {
            String scene = "同行站在门内，手里拿着头盔。";
            String faceMsg = "他举起头盔，头盔上的裂缝还在。\"你来了。\"他说。";
            String bypassMsg = "你和他擦肩而过。\"下次一起送货。\"他喊了一句。你没有回头。";
            String shatterMsg = "你把他的头盔砸碎。他没有追上来——但你知道，这份友谊也碎了。";
            result.add(new Gate("同行之门", scene, GameConfig.ECHO_RIVAL_COOPERATE, faceMsg, bypassMsg, shatterMsg));
        }
    }

    private void addGambleGate(List<Gate> result) {
        if (player.hasGambleRevenge()) {
            String scene = "门在燃烧。赌徒的脸在火焰中若隐若现。"
                    + " 你听见骰子落地的声音。";
            result.add(new Gate(
                "赌徒之门",
                scene,
                GameConfig.ECHO_GAMBLE_REPORT,
                "\"你以为夜之城会忘了？\"火焰中传来他的声音，骰子落地的声音清脆如骨裂。",
                "你绕过赌徒。他还在那里，日复一日地掷骰子。你救了他，但他不知道。",
                "火焰吞噬了一切。你报警时的正义感也一起化为灰烬。夜之城不需要英雄。"
            ));
        }
    }

    private void addGhostGate(List<Gate> result) {
        if (player.hasGhostAccepted() || hasEcho(GameConfig.ECHO_GHOST_TRACKED)) {
            String scene, faceMsg, bypassMsg, shatterMsg, echoSource;
            if (player.hasGhostAccepted()) {
                scene = "门内是镜子，镜子里是你接受那个交易时的脸。合成语音在耳边回响：\"成交。\"";
                faceMsg = "\"成交。\"合成语音再次响起，像是从你的骨头里发出来的。镜中的你点了点头。";
                bypassMsg = "你假装没听见那个声音。幽灵的交易已成过去。你不需要它的帮助。";
                shatterMsg = "镜面碎裂。\"成交。\"回音还在回响。你毁掉的不只是一面镜子。";
                echoSource = GameConfig.ECHO_GHOST_ACCEPT;
            } else {
                scene = "门内是镜子，镜子里是你反向追踪成功时的脸。你看到了背后的黑客窝点。";
                faceMsg = "屏幕闪烁了一下，黑客的代码在镜中滚动。\"你找到我们了。\"一个声音说。";
                bypassMsg = "你绕过黑客的痕迹。有些真相，知道了反而是负担。";
                shatterMsg = "你把追踪记录全部删除。那些黑客已经盯上了你——但至少，他们没有证据了。";
                echoSource = GameConfig.ECHO_GHOST_TRACKED;
            }
            result.add(new Gate("幽灵之门", scene, echoSource, faceMsg, bypassMsg, shatterMsg));
        }
    }

    private void addScavengerGate(List<Gate> result) {
        if (player.hasScavengerIntel()) {
            result.add(new Gate(
                "拾荒者之门",
                "拾荒者从门内探出头，手里是你买过的地图。\"下次再来啊。\"",
                GameConfig.ECHO_SCAVENGER_INTEL,
                "\"下次再来啊。\"他的声音从门缝里漏出来，带着机油和雨水的味道。",
                "你摆摆手走开了。那些情报只是一张纸，不值得你多看一眼。",
                "你把地图撕成碎片。拾荒者在门后叹了口气，下一个买家会是谁？"
            ));
        } else if (hasEcho(GameConfig.ECHO_SCAVENGER_DISMISS)) {
            result.add(new Gate(
                "拾荒者之门",
                "拾荒者背对你，手里攥着被你赶走时掉落的零件。",
                GameConfig.ECHO_SCAVENGER_DISMISS,
                "零件掉在地上，发出清脆的金属声。他没有回头。",
                "你绕过那个背影。也许下次他会原谅你。也许不会。",
                "你踩碎地上的零件。他蹲下来捡，一言不发。零件上的油渍映着你的脸。"
            ));
        }
    }

    private void addLegacyGate(List<Gate> result) {
        if (player.hasStoleFromDead() || player.hasLegacyReturned()) {
            String scene, faceMsg, bypassMsg, shatterMsg, echoSource;
            if (player.hasStoleFromDead()) {
                scene = "门内是包裹，上面标着你当时卖掉的价格。寄件人家属站得很远。";
                faceMsg = "\"那是他的东西。\"家属的声音很轻，像是从很远的地方传来。\"你记得他吗？\"";
                bypassMsg = "你不想看见他们的眼神。那些钱早就花了，买的是你自己的心安。";
                shatterMsg = "包裹碎裂。那个价格标签还在——你卖掉的那份信任，永远无法标价。";
                echoSource = GameConfig.ECHO_LEGACY_KEPT;
            } else {
                scene = "门内是包裹，收件人家属捧着它，对你说了一声谢谢。";
                faceMsg = "\"谢谢你。\"家属的声音很轻，但足够让你听见。\"他会在那边等你的。\"";
                bypassMsg = "你转身离开，让他们自己去处理那份遗物。你已经做得够多了。";
                shatterMsg = "门碎的时候，你看见家属的眼神里有什么东西也碎了。谢谢变得毫无意义。";
                echoSource = GameConfig.ECHO_LEGACY_RETURNED;
            }
            result.add(new Gate("遗物之门", scene, echoSource, faceMsg, bypassMsg, shatterMsg));
        }
    }

    private boolean hasEcho(String source) {
        for (Player.EchoStone s : player.getEchoPool()) {
            if (s.getSource().equals(source)) return true;
        }
        return false;
    }

    public List<String> getGateNames() {
        List<String> names = new ArrayList<>();
        for (Gate g : gates) {
            names.add(g.name);
        }
        names.add("自己之门");
        return names;
    }

    public String enterGate(int index) {
        if (index < gates.size()) {
            return gates.get(index).scene;
        } else {
            return buildMirrorScene();
        }
    }

    public String enterGateSafe(int index) {
        if (index >= 0 && index < gates.size()) {
            return gates.get(index).scene;
        } else {
            return "门内空无一物。只有灰尘在光柱中缓慢旋转。";
        }
    }

    public void faceGate(int index) {
        if (index >= 0 && index < gates.size()) {
            Gate g = gates.get(index);
            if (!g.isEmpty) {
                setEchoStatus(g.echoSource, Player.EchoStone.EchoStatus.FACED);
            }
        }
    }

    public void bypassGate(int index) {
        if (index >= 0 && index < gates.size()) {
            Gate g = gates.get(index);
            if (!g.isEmpty) {
                setEchoStatus(g.echoSource, Player.EchoStone.EchoStatus.BYPASSED);
            }
        }
    }

    public String getFaceMessage(int index) {
        if (index >= 0 && index < gates.size()) {
            return gates.get(index).faceMessage;
        }
        return "你选择正面面对。这是你的记忆，你承担它。";
    }

    public String getBypassMessage(int index) {
        if (index >= 0 && index < gates.size()) {
            return gates.get(index).bypassMessage;
        }
        return "你选择绕过。";
    }

    public String getShatterMessage(int index) {
        if (index >= 0 && index < gates.size()) {
            return gates.get(index).shatterMessage;
        }
        return "你选择击碎。";
    }

    public void shatterGate(int index) {
        if (index >= 0 && index < gates.size()) {
            Gate g = gates.get(index);
            if (!g.isEmpty) {
                setEchoStatus(g.echoSource, Player.EchoStone.EchoStatus.SHATTERED);
            }
        }
    }

    private void setEchoStatus(String source, Player.EchoStone.EchoStatus status) {
        if (source == null) return;
        for (Player.EchoStone s : player.getEchoPool()) {
            if (s.getSource().equals(source)) {
                s.setStatus(status);
            }
        }
    }

    private String buildMirrorScene() {
        int faced = player.countEchoByStatus(Player.EchoStone.EchoStatus.FACED);
        int shattered = player.countEchoByStatus(Player.EchoStone.EchoStatus.SHATTERED);
        int raw = player.totalEchoCount() - faced - shattered;
        if (shattered >= 3) {
            mirrorState = "碎片";
            return "镜子里没有人，只有碎片映着不同的夜之城角落。"
                    + "霓虹灯在碎片里闪烁，每一片都是你打碎的记忆。"
                    + "你盯着碎片，碎片里的你在不同时间线做着不同选择。"
                    + "它们同时说：\"你终于是这样了。\"";
        } else if (raw >= 3 || shattered >= 1) {
            mirrorState = "裂纹";
            return "镜中人在哭，但你听不见。"
                    + "泪水在镜面上划出细小的裂纹，透过裂纹，你看到自己曾绕过的那几扇门。"
                    + "它们还在那里，等着一句你从未说过的话。";
        } else if (player.hasRivalFriend() && faced >= 5) {
            mirrorState = "重叠";
            return "镜中是两个人的轮廓。你和同行背对背站着。"
                    + "他手里还拿着那个头盔。你伸手去接，镜面泛起涟漪。"
                    + "他说：\"下一单我等你。\"";
        } else if (faced >= 5 && shattered == 0) {
            mirrorState = "完整";
            return "镜中人清晰，是你。眼角有皱纹，手指有老茧。"
                    + "你看着自己，自己回看你。没有闪烁，没有裂纹。"
                    + "这是你面对了一切之后的样子。\"够了。\"镜中人点了点头。";
        } else if (player.totalEchoCount() < 3 && player.getTotalTasks() >= 10) {
            mirrorState = "空白";
            return "镜子是普通的镜子。你只是一个快递员，没有故事。"
                    + "夜之城不需要记住你，你也从不曾让它记住。"
                    + "你看着镜中的自己：干净、平凡、没有伤口。"
                    + "镜中人问：\"够了？\"你不知道。你从不知道。";
        } else {
            mirrorState = "完整";
            return "镜中人清晰，是你。眼角有皱纹，手指有老茧。"
                    + "你看着自己，自己回看你。没有闪烁，没有裂纹。"
                    + "\"继续。\"你说。镜中人点点头。";
        }
    }

    public int getGateCount() {
        return gates.size();
    }

    /**
     * 正面面对的门数。直接从回响石状态统计，避免维护第二份账。
     */
    public int getFacedCount() {
        return player.countEchoByStatus(Player.EchoStone.EchoStatus.FACED);
    }

    public String getMirrorState() {
        return mirrorState;
    }

    /**
     * 根据玩家状态判定结局类型。
     * 优先级：赌徒复仇 > 过劳暴毙 > 城警围捕 > 传奇英雄/堕落枭雄/兄弟/灰行者/平凡逃离
     *
     * @return 判定出的结局类型枚举值
     */
    public EndingType determineEnding() {

        if (player.hasGambleRevenge() && player.getHealth() <= GameConfig.HEALTH_CRITICAL) {
            return EndingType.GAMBLE_REVENGE;
        }

        if (player.getHealth() <= 0) {
            return EndingType.BURNOUT;
        }

        if (player.isWantedMax()) {
            return EndingType.NCPD_ARREST;
        }

        if (player.isWin()) {

            if (player.getHelpCount() >= 3
                    && player.getReputation() >= GameConfig.REPUTATION_VETERAN
                    && player.hasRivalFriend()
                    && player.hasSparedLife()
                    && !player.hasJoinedGang()
                    && !player.hasStoleFromDead()
                    && !player.hasLooted()) {
                return EndingType.LEGEND_HERO;
            }

            if (player.hasJoinedGang() && player.hasStoleFromDead() && player.hasLooted()
                    && player.getReputation() <= GameConfig.REPUTATION_BOTTOM) {
                return EndingType.FALLEN_KINGPIN;
            }

            if (player.hasRivalFriend() && !player.hasJoinedGang() && !player.hasStoleFromDead()) {
                return EndingType.BROTHERHOOD;
            }

            if (player.getHelpCount() >= 1
                    && (player.hasStoleFromDead() || player.hasLooted() || player.hasLiedToLover())
                    && !player.hasJoinedGang()) {
                return EndingType.GRAY_WALKER;
            }

            return EndingType.QUIET_ESCAPE;
        }
        return EndingType.QUIET_ESCAPE;
    }

    public String generate() {
        EndingType type = determineEnding();
        return switch (type) {
            case GAMBLE_REVENGE -> generateGambleRevenge();
            case BURNOUT -> generateBurnout();
            case NCPD_ARREST -> generateNCPDArrest();
            case LEGEND_HERO -> generateLegendHero();
            case FALLEN_KINGPIN -> generateFallenKingpin();
            case BROTHERHOOD -> generateBrotherhood();
            case GRAY_WALKER -> generateGrayWalker();
            case QUIET_ESCAPE -> generateQuietEscape();
        };
    }

    public String getEndingTypeName() {
        EndingType type = determineEnding();
        return switch (type) {
            case GAMBLE_REVENGE -> "赌徒复仇";
            case BURNOUT -> "过劳暴毙";
            case NCPD_ARREST -> "城警围捕";
            case LEGEND_HERO -> "传奇英雄";
            case FALLEN_KINGPIN -> "堕落枭雄";
            case BROTHERHOOD -> "兄弟之情";
            case GRAY_WALKER -> "灰行者";
            case QUIET_ESCAPE -> "平凡逃离";
        };
    }

    public java.awt.Color getEndingColor() {
        EndingType type = determineEnding();
        return switch (type) {
            case GAMBLE_REVENGE -> new java.awt.Color(0xCC, 0x22, 0x22);
            case BURNOUT -> new java.awt.Color(0xAA, 0x33, 0x22);
            case NCPD_ARREST -> new java.awt.Color(0xFF, 0x44, 0x00);
            case LEGEND_HERO -> new java.awt.Color(0xFF, 0xCC, 0x00);
            case FALLEN_KINGPIN -> new java.awt.Color(0x88, 0x44, 0xCC);
            case BROTHERHOOD -> new java.awt.Color(0x00, 0xCC, 0x88);
            case GRAY_WALKER -> new java.awt.Color(0x66, 0x88, 0xAA);
            case QUIET_ESCAPE -> new java.awt.Color(0x00, 0xAA, 0xCC);
        };
    }

    public String getEndingSubtitle() {
        EndingType type = determineEnding();
        return switch (type) {
            case GAMBLE_REVENGE -> "夜之城从不让背叛者安然离场。你攒了" + player.getMoney() + "€，但金钱买不到活着的权利。";
            case BURNOUT -> "夜之城吞噬了又一个无名的灵魂。你攒了" + player.getMoney() + "€，死在离自由一步之遥。";
            case NCPD_ARREST -> "你攒了" + player.getMoney() + "€，在牢里一文不值。迪达拉监狱。";
            case LEGEND_HERO -> "你的名字将被夜之城传唱。你在新的城市活了下来，带着所有人的祝福。";
            case FALLEN_KINGPIN -> "你逃离了夜之城，但你成了你最恨的那种人。新城市的人称你为\"那个枭雄\"。";
            case BROTHERHOOD -> "你逃离了夜之城，身后站着一群愿意为你赴死的人。\"走吧，兄弟。\"";
            case GRAY_WALKER -> "你逃离了夜之城，站在新城市的街头，发现无论走到哪里，你都还是你。";
            case QUIET_ESCAPE -> "你逃离了夜之城，在新城市租了一间小小的公寓。窗外没有霓虹，只有平凡的星空。";
        };
    }

    private String generateGambleRevenge() {
        StringBuilder sb = new StringBuilder();
        sb.append("暗巷里，你倒在地上。头顶是夜之城的霓虹，模糊成一片。\n\n");
        sb.append("那个被你报警的赌徒站在你身边，手里还攥着骰子。\n");
        sb.append("\"你以为夜之城会忘了？\"他把骰子丢在你胸口。\n\n");
        sb.append("你听见骰子落地。像那天下注时一样清脆。\n");
        sb.append("只不过这一次，输的是你。\n\n");
        sb.append(getMemoryAttitude()).append("\n\n");
        sb.append("========================================\n");
        sb.append("  【赌徒复仇】\n");
        sb.append("  夜之城从不让背叛者安然离场。\n");
        sb.append("  你攒了 ").append(player.getMoney()).append("€，但金钱买不到活着的权利。\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    private String generateBurnout() {
        StringBuilder sb = new StringBuilder();
        sb.append("你的腿再也迈不动了。夜之城的雨滴在你脸上，冰凉。\n\n");
        if (player.getHelpCount() > 0) {
            sb.append("你救过 ").append(player.getHelpCount()).append(" 个人。今天没人来救你。\n");
        } else {
            sb.append("你从不救人。今天也没人救你。\n");
        }
        sb.append("\n路人绕过你。有人偷了你的鞋。一个拾荒者蹲下来，翻了翻你的口袋。\n");
        sb.append("\"空的。\"他叹了口气，走了。\n\n");
        sb.append(getMemoryAttitude()).append("\n\n");
        sb.append("========================================\n");
        sb.append("  【过劳暴毙】\n");
        sb.append("  夜之城吞噬了又一个无名的灵魂。\n");
        sb.append("  你攒了 ").append(player.getMoney()).append("€，死在离自由一步之遥。\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    private String generateNCPDArrest() {
        StringBuilder sb = new StringBuilder();
        sb.append("直升机探照灯照在你身上。你无处可躲。\n\n");
        if (player.hasJoinedGang()) {
            sb.append("你身上还带着帮派的徽章。城警看了徽章一眼，冷笑：\"社团成员。重罪。\"\n\n");
        } else {
            sb.append("你是一个普通快递员，但你的通缉档案比帮派成员还厚。\n\n");
        }
        if (player.getRefuseCount() > 0) {
            sb.append("你拒检过 ").append(player.getRefuseCount()).append(" 次。每一次都以为能逃掉。\n");
        }
        sb.append("\n冰冷的手铐铐住了你的手腕，耳机里响起判决：七年。\n");
        sb.append("你回头看了一眼夜之城的霓虹。它们还在闪，但已经与你无关。\n\n");
        sb.append(getMemoryAttitude()).append("\n\n");
        sb.append("========================================\n");
        sb.append("  【城警围捕】\n");
        sb.append("  你攒了 ").append(player.getMoney()).append("€，在牢里一文不值。\n");
        sb.append("  迪达拉监狱，编号NC-").append(player.getRefuseCount() + 7).append("。\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    private String generateLegendHero() {
        StringBuilder sb = new StringBuilder();
        sb.append("你站在镜子前，九道门在你身后缓缓关闭。\n\n");
        sb.append(getMemoryAttitude()).append("\n\n");
        if (player.hasSparedLife()) {
            sb.append("救人之门里，那个被你原价给了药的小女孩走了出来。她手里捧着一束破旧的光纤花。\n");
            sb.append("\"我一直在等你。\"她说。\n\n");
        }
        if (player.hasRivalFriend()) {
            sb.append("同行之门里，那个曾经和你抢单的快递员走了出来。他把头盔递给你。\n");
            sb.append("\"下一单，我跟你一起去。\"\n\n");
        }
        sb.append("你选择了走出去。").append(GameConfig.WIN_TARGET).append("€交到边境守卫手中。\n");
        sb.append("守卫看了看你的档案：救人").append(player.getHelpCount()).append("次，声望").append(player.getReputation()).append("。\n");
        sb.append("\"走吧。\"他说。\n\n");
        sb.append("你走出城门。身后，整条街的快递员同时鸣笛。\n");
        sb.append("========================================\n");
        sb.append("  【传奇英雄】\n");
        sb.append("  夜之城不配拥有你，但它会记住你。\n");
        sb.append("  每一个你救过的人，每一个你照亮过的角落。\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    private String generateFallenKingpin() {
        StringBuilder sb = new StringBuilder();
        sb.append("你站在碎玻璃上，脚下是你打碎的记忆。\n\n");
        sb.append(getMemoryAttitude()).append("\n\n");
        if (player.hasJoinedGang()) {
            sb.append("帮派的徽章在你胸口发烫。你现在是他们的人了。\n");
        }
        if (player.hasStoleFromDead()) {
            sb.append("遗物之门已经碎了。那张死人的包裹，你连他的名字都不记得。\n");
        }
        if (player.hasLooted()) {
            sb.append("你还记得那个求救者吗？不记得了。他的钱在你口袋里。\n\n");
        }
        sb.append("你选择了烧掉它。").append(GameConfig.WIN_TARGET).append("€在火光中化为灰烬。\n");
        sb.append("守卫看着你，没有阻止。\"夜之城见过比你更疯的。\"\n\n");
        sb.append("你转身回城。口袋里空空，名声狼藉。\n");
        sb.append("但你还活着。在夜之城，这就算赢。\n\n");
        sb.append("========================================\n");
        sb.append("  【堕落枭雄】\n");
        sb.append("  恶贯满盈，满身伤痕，但你活下来了。\n");
        sb.append("  夜之城不需要圣人。它只需要活人。\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    private String generateBrotherhood() {
        StringBuilder sb = new StringBuilder();
        sb.append("城门在身后关上。你站在边境，手中攥着").append(GameConfig.WIN_TARGET).append("€。\n\n");
        sb.append(getMemoryAttitude()).append("\n\n");
        sb.append("同行站在你旁边，手里还拿着那个头盔。\n");
        sb.append("\"下一单，我跟你一起去。\"他说。\n\n");
        sb.append("你笑了笑，接过头盔。\n");
        sb.append("也许自由不在于离开，而在于有人同行。\n\n");
        sb.append("========================================\n");
        sb.append("  【兄弟结局】\n");
        sb.append("  夜之城的霓虹在身后渐渐远去。\n");
        sb.append("  但你知道，有人会记得你。\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    private String generateGrayWalker() {
        StringBuilder sb = new StringBuilder();
        sb.append("城门在身后关上。你走出来，身上带着夜之城的印记。\n\n");
        sb.append(getMemoryAttitude()).append("\n\n");
        sb.append("你不算是好人。你知道。\n");
        sb.append("但你也没完全丢掉自己。\n");
        if (player.getHelpCount() > 0) {
            sb.append("你救过 ").append(player.getHelpCount()).append(" 个人。\n");
        }
        sb.append("\n夜之城的风吹过来，带着霓虹和雨水。\n");
        sb.append("你回头看了一眼，什么也没看见。\n\n");
        sb.append("========================================\n");
        sb.append("  【灰色行者】\n");
        sb.append("  你不是英雄，也不是恶棍。\n");
        sb.append("  只是一个活着的人。\n");
        sb.append("  这在夜之城，已经算是不错的结局。\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    private String generateQuietEscape() {
        StringBuilder sb = new StringBuilder();
        sb.append("城门在身后关上。你数了数口袋里的钱：").append(GameConfig.WIN_TARGET).append("€整。\n\n");
        sb.append(getMemoryAttitude()).append("\n\n");
        sb.append("守卫挥了挥手，你走了出去。\n");
        sb.append("身后，夜之城的霓虹在闪烁，与你无关。\n\n");
        if (player.totalEchoCount() == 0) {
            sb.append("没有人记得你。你也几乎不记得这里。\n");
        } else {
            sb.append("你的口袋里还有几颗回响石子，叮当作响。\n");
        }
        sb.append("\n镜子里的你：").append(mirrorState).append("。\n");
        sb.append("\n========================================\n");
        sb.append("  【平凡逃离】\n");
        sb.append("  夜之城吞噬了无数故事。\n");
        sb.append("  你的故事，只是其中一个。\n");
        sb.append("========================================\n");
        return sb.toString();
    }

    private String getMemoryAttitude() {
        int shatteredNow = player.countEchoByStatus(Player.EchoStone.EchoStatus.SHATTERED);
        int facedNow = player.countEchoByStatus(Player.EchoStone.EchoStatus.FACED);
        if (shatteredNow >= 3) {
            return "你把大部分记忆砸得粉碎，碎片在口袋里叮当作响。";
        }
        if (facedNow >= 3 && shatteredNow == 0) {
            return "你选择面对每一个回忆，即使它们刺痛。";
        }
        return "你匆匆走过，不想多看一眼。";
    }

    public String buildEchoSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n--- 回响总结 ---\n");

        int humanity = player.getHumanity();
        int coldness = player.getColdness();

        if (humanity > 0) {
            sb.append("人性：").append(humanity).append("次。你在黑暗中伸出了手。\n");
        }
        if (coldness > 0) {
            sb.append("冷酷：").append(coldness).append("次。你选择了另一条路。\n");
        }

        int total = humanity + coldness;
        if (total == 0) {
            sb.append("你的旅途没有任何回响。夜之城记住了你的沉默。\n");
        }

        sb.append("--- 回响终了 ---\n");
        return sb.toString();
    }

    public String getTerminalAttitude() {
        EndingType type = determineEnding();
        return switch (type) {
            case LEGEND_HERO -> "【系统】快递员" + player.getName() + "档案已封存。评级：传奇。";
            case FALLEN_KINGPIN -> "【系统】快递员" + player.getName() + "档案已标记。评级：敌对。";
            case BROTHERHOOD -> "【系统】快递员" + player.getName() + "档案已归档。评级：盟友。";
            case GRAY_WALKER -> "【系统】快递员" + player.getName() + "档案已更新。评级：中立。";
            case QUIET_ESCAPE -> "【系统】快递员" + player.getName() + "档案已关闭。评级：平庸。";
            case GAMBLE_REVENGE -> "【系统】快递员" + player.getName() + "状态：已死亡。原因：街头复仇。";
            case BURNOUT -> "【系统】快递员" + player.getName() + "状态：已死亡。原因：义体过载。";
            case NCPD_ARREST -> "【系统】快递员" + player.getName() + "状态：已收押。刑期：7年。";
        };
    }
}
