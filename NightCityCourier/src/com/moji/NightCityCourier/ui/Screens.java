package com.moji.NightCityCourier.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/**
 * 界面合成层。
 *
 * <p>这里定义的是<b>真实界面</b>的绘制方法（不是示意图）：Swing 端按同样的布局参数调用
 * {@link #dashboard}，离屏预览也调用它，所以预览图与运行时界面一致。
 *
 * <p>布局是严格栅格：顶部 48px 状态条、底部 56px 操作条、中间三栏
 * （左 300 / 中自适应 / 右 260），栏间 16px 沟槽。
 */
public final class Screens {

    public static final int HEADER_H = 48;
    public static final int FOOTER_H = 56;
    public static final int GUTTER = 16;
    public static final int LEFT_W = 300;
    public static final int RIGHT_W = 260;

    /** 一屏所需的完整数据模型。真实运行时由 GameWindow 从 Player/Profile 填充。 */
    public static final class Model {
        public String callsign = "V";
        public int level = 4;
        public int cash = 3420;
        public int target = 8000;
        public int health = 62;
        public int maxHealth = 115;
        public int wanted = 2;
        public int wantedMax = 5;
        public int streak = 3;
        public String weather = "酸雨 · 深夜";
        public String phaseNote = "事件密度 ×1.29";
        public int policeRep = -22;
        public int gangRep = 14;
        public int scavRep = 48;
        public String[] rigs = {"冷藏箱", "幽灵协处理器"};
        public int rigSlots = 3;
        public String[] log = {
                "雨点砸在头盔上，像有人在敲。",
                "第七街区的单子，收件人只留了一个坐标。",
                "冷藏箱在背包里嗡嗡作响，指示灯是那种不祥的橙。",
                "",
                ">> 前方路口出现三架无人机，扫描灯在雨里拉出白线。",
                ">> 你压低身体，贴进墙根的阴影。",
                ">> 扫描灯从你头顶滑过去，停了两秒。",
                "",
                "【系统】安全通过。通缉未上升。",
                "",
                "巷口闪过一块残破的广告牌：",
                "「你想要的生活，分期付款。」",
                "",
                ">> 你继续往前走。雨水顺着义体的接缝渗进来。"
        };
        public String missionType = "💀 黑市件 · 器官件";
        public double missionRisk = 3.4;
        public int missionReward = 920;
        public int missionSteps = 5;
        public String missionNote = "冷藏限时：血浆超过 6 步开始变质";
        public String[] routes = {
                "后巷穿行 · 稳·慢",
                "夜市外围 · 肥单",
                "公司天桥 · 赌命"
        };
        public String[] routeHints = {
                "略慢 / 很安全 / 报酬正常",
                "绕远 / 常规 / 报酬丰厚",
                "极快 / 高危 / 报酬丰厚"
        };
        public String[] buttons = {"接单", "成长", "治疗", "档案", "存档"};
        public String footerNote = "F1 帮助   F5 存档   ESC 退出";
        public int shiftDeliveries = 7;
        public int shiftSteps = 38;
        public String dailyRule = "血月当空 · 事件密度上升";
        public String epithet = "屋顶上的";
        public String streetTalk = "拾荒者给你留了一条只有你能走的路。";
        public String relations = "老K 信任 · 小满 生死之交 · 格雷 敌对";
    }

    private Screens() {
    }

    // ══════════════════════════════════════════════════════════
    //  主界面
    // ══════════════════════════════════════════════════════════

    public static void dashboard(Graphics2D g, int w, int h, Model m) {
        UiKit.prepare(g);
        UiKit.background(g, w, h);

        header(g, w, m);
        int top = HEADER_H + GUTTER;
        int bottom = h - FOOTER_H - GUTTER;
        int leftX = GUTTER;
        int centerX = leftX + LEFT_W + GUTTER;
        int rightX = w - GUTTER - RIGHT_W;
        int centerW = rightX - GUTTER - centerX;

        statusPanel(g, leftX, top, LEFT_W, bottom - top, m);
        terminalPanel(g, centerX, top, centerW, bottom - top, m);
        missionPanel(g, rightX, top, RIGHT_W, bottom - top, m);
        footer(g, w, h, m);
    }

    private static void header(Graphics2D g, int w, Model m) {
        g.setColor(Theme.PANEL);
        g.fillRect(0, 0, w, HEADER_H);
        UiKit.hline(g, 0, w, HEADER_H - 1, Theme.LINE);

        // 左：品牌 + 版本
        UiKit.text(g, "NCC", 16, 31, Theme.monoBold(17), Theme.ACCENT);
        int brandX = 16 + 40;
        UiKit.tracked(g, "NIGHT CITY COURIER", brandX, 30, Theme.mono(10), Theme.TEXT_FAINT, 3);
        int brandW = UiKit.trackedWidth(g, "NIGHT CITY COURIER", Theme.mono(10), 3);
        UiKit.text(g, "v2.0", brandX + brandW + 14, 30, Theme.mono(10), Theme.TEXT_FAINT);

        // 中：代号 + 等级
        String id = m.callsign;
        UiKit.text(g, id, w / 2 - UiKit.width(g, id, Theme.sansBold(15)) / 2, 31,
                Theme.sansBold(15), Theme.TEXT);
        String lv = "LV " + Theme.id(m.level, 2);
        UiKit.text(g, lv, w / 2 + UiKit.width(g, id, Theme.sansBold(15)) / 2 + 10, 30,
                Theme.mono(11), Theme.ACCENT);

        // 右：天气 + 时间
        String right = m.weather;
        UiKit.textRight(g, right, w - 16, 30, Theme.sans(12), Theme.TEXT_DIM);
        String note = m.phaseNote;
        UiKit.textRight(g, note, w - 16 - UiKit.width(g, right, Theme.sans(12)) - 12, 30,
                Theme.mono(10), Theme.TEXT_FAINT);
    }

    private static void statusPanel(Graphics2D g, int x, int y, int w, int h, Model m) {
        UiKit.panel(g, x, y, w, h, Theme.PANEL, Theme.LINE);
        int px = x + Theme.PAD;
        int right = x + w - Theme.PAD;

        UiKit.sectionHeader(g, "01", "状态", px, right, y + 28);

        // 代号大字
        UiKit.text(g, m.callsign, px, y + 62, Theme.sansBold(26), Theme.TEXT);
        UiKit.tag(g, "callsign", px + UiKit.width(g, m.callsign, Theme.sansBold(26)) + 10, y + 62);

        // 街面上的名声：世界记忆的画像，是这个人物在城里的身份
        UiKit.text(g, "「" + m.epithet + "」", px, y + 84, Theme.sans(12), Theme.ACCENT);
        Font talkFont = Theme.sans(10);
        UiKit.text(g, UiKit.ellipsize(g, m.streetTalk, talkFont, w - Theme.PAD * 2),
                px, y + 100, talkFont, Theme.TEXT_FAINT);
        // 具名关系：谁认得你
        UiKit.text(g, UiKit.ellipsize(g, m.relations, talkFont, w - Theme.PAD * 2),
                px, y + 116, talkFont, Theme.TEXT_DIM);

        // 生命
        int ly = y + 140;
        UiKit.tag(g, "vitals", px, ly);
        UiKit.textRight(g, m.health + " / " + m.maxHealth, right, ly, Theme.mono(11), Theme.TEXT_DIM);
        double hp = m.maxHealth == 0 ? 0 : (double) m.health / m.maxHealth;
        UiKit.meter(g, px, ly + 8, w - Theme.PAD * 2, 10, hp, hp < 0.3 ? Theme.DANGER : Theme.OK);

        // 现金 / 目标
        int cy = ly + 48;
        UiKit.tag(g, "credits", px, cy);
        UiKit.textRight(g, "€ " + Theme.num(m.cash), right, cy, Theme.monoBold(15), Theme.ACCENT);
        double prog = m.target == 0 ? 0 : (double) m.cash / m.target;
        UiKit.meter(g, px, cy + 8, w - Theme.PAD * 2, 5, prog, Theme.ACCENT_DIM);
        UiKit.textRight(g, "目标 " + Theme.num(m.target) + "€", right, cy + 30, Theme.mono(10), Theme.TEXT_FAINT);

        // 通缉
        int wy = cy + 52;
        UiKit.tag(g, "wanted", px, wy);
        UiKit.pips(g, px + 62, wy - 9, m.wanted, m.wantedMax, Theme.DANGER, Theme.LINE_STRONG, 8);
        UiKit.textRight(g, m.wanted + "/" + m.wantedMax, right, wy, Theme.mono(11),
                m.wanted >= 4 ? Theme.DANGER : Theme.TEXT_DIM);

        // 连胜
        int sy = wy + 30;
        UiKit.keyValue(g, "连胜", String.valueOf(m.streak), px, right, sy, sy - 4);

        // 派系
        int fy = sy + 26;
        UiKit.hline(g, px, right, fy - 12, Theme.LINE);
        UiKit.tag(g, "factions", px, fy + 4);
        factionRow(g, "城警", m.policeRep, px, fy + 26, w - Theme.PAD * 2);
        factionRow(g, "帮派", m.gangRep, px, fy + 50, w - Theme.PAD * 2);
        factionRow(g, "拾荒者", m.scavRep, px, fy + 74, w - Theme.PAD * 2);

        // 义体
        int ry = fy + 104;
        UiKit.hline(g, px, right, ry - 12, Theme.LINE);
        UiKit.tag(g, "loadout " + m.rigs.length + "/" + m.rigSlots, px, ry + 4);
        for (int i = 0; i < m.rigs.length; i++) {
            int iy = ry + 26 + i * 22;
            g.setColor(Theme.ACCENT);
            g.fillRect(px, iy - 9, 3, 10);
            UiKit.text(g, m.rigs[i], px + 10, iy, Theme.sans(12), Theme.TEXT_DIM);
        }
        if (m.rigs.length == 0) {
            UiKit.text(g, "（空）", px, ry + 26, Theme.sans(12), Theme.TEXT_FAINT);
        }

        // 本次班次：把左栏下半部分的留白填成有效信息
        int shiftY = ry + 26 + Math.max(1, m.rigs.length) * 22 + 18;
        UiKit.hline(g, px, right, shiftY - 10, Theme.LINE);
        UiKit.tag(g, "shift", px, shiftY + 6);
        UiKit.keyValue(g, "本班派送", m.shiftDeliveries + " 单", px, right, shiftY + 28, shiftY + 24);
        UiKit.keyValue(g, "累计里程", m.shiftSteps + " 步", px, right, shiftY + 50, shiftY + 46);
        UiKit.keyValue(g, "账户目标", Math.round(prog * 100) + "%", px, right, shiftY + 72, shiftY + 68);

        // 今日规则
        int ruleY = shiftY + 100;
        UiKit.hline(g, px, right, ruleY - 8, Theme.LINE);
        UiKit.tag(g, "daily rule", px, ruleY + 6);
        Font ruleFont = Theme.sans(11);
        String rule = UiKit.ellipsize(g, m.dailyRule, ruleFont, w - Theme.PAD * 2);
        UiKit.text(g, rule, px, ruleY + 26, ruleFont, Theme.ACCENT);

        // 底部编号，工业标牌的习惯
        UiKit.text(g, "NCC-" + Theme.id(7, 4), x + Theme.PAD, y + h - 14,
                Theme.mono(9), Theme.TEXT_FAINT);
        UiKit.textRight(g, "STATUS", right, y + h - 14, Theme.mono(9), Theme.TEXT_FAINT);
    }

    private static void factionRow(Graphics2D g, String name, int rep, int x, int y, int w) {
        UiKit.text(g, name, x, y, Theme.sans(11), Theme.TEXT_DIM);
        int barX = x + 44;
        int barW = w - 44 - 56;
        boolean neg = rep < 0;
        double ratio = Math.min(1.0, Math.abs(rep) / 100.0);
        g.setColor(Theme.INSET);
        g.fillRect(barX, y - 8, barW, 6);
        g.setColor(neg ? Theme.DANGER : Theme.OK);
        if (neg) {
            int bw = (int) Math.round(barW * ratio);
            g.fillRect(barX + barW - bw, y - 8, bw, 6);
        } else {
            g.fillRect(barX, y - 8, (int) Math.round(barW * ratio), 6);
        }
        g.setColor(Theme.LINE);
        g.fillRect(barX + barW / 2, y - 10, 1, 10);
        UiKit.textRight(g, (rep > 0 ? "+" : "") + rep, x + w, y, Theme.mono(10),
                neg ? Theme.DANGER : Theme.TEXT_DIM);
    }

    private static void terminalPanel(Graphics2D g, int x, int y, int w, int h, Model m) {
        UiKit.panel(g, x, y, w, h, Theme.PANEL, Theme.LINE);
        int px = x + Theme.PAD;
        int right = x + w - Theme.PAD;

        UiKit.sectionHeader(g, "02", "现场", px, right, y + 28);
        UiKit.textRight(g, "LIVE", right, y + 28, Theme.mono(10), Theme.OK);

        int insetX = px;
        int insetY = y + 42;
        int insetW = w - Theme.PAD * 2;
        int insetH = h - 56;
        g.setColor(Theme.INSET);
        g.fillRect(insetX, insetY, insetW, insetH);
        g.setColor(Theme.LINE);
        g.drawRect(insetX, insetY, insetW - 1, insetH - 1);

        // 终端点阵头
        g.setColor(Theme.LINE);
        for (int gy = insetY + 8; gy < insetY + insetH - 8; gy += 6) {
            for (int gx = insetX + 8; gx < insetX + insetW - 8; gx += 6) {
                g.fillRect(gx, gy, 1, 1);
            }
        }

        int ty = insetY + 26;
        Font body = Theme.sans(13);
        Font mono = Theme.mono(13);
        for (String line : m.log) {
            if (line == null || line.isEmpty()) {
                ty += 12;
                continue;
            }
            boolean system = line.startsWith("【");
            boolean prompt = line.startsWith(">>");
            Font useFont = (system || prompt) ? mono : body;
            Color c = system ? Theme.INFO : prompt ? Theme.ACCENT : Theme.TEXT;
            if (system || prompt) {
                UiKit.text(g, UiKit.ellipsize(g, line, useFont, insetW - 32), insetX + 16, ty, useFont, c);
            } else {
                UiKit.text(g, UiKit.ellipsize(g, line, useFont, insetW - 32), insetX + 16, ty, useFont, c);
            }
            ty += 22;
        }
        // 光标
        g.setColor(Theme.ACCENT);
        g.fillRect(insetX + 16, ty - 12, 7, 14);

        UiKit.text(g, "▌ 输入等待…", insetX + 16, insetY + insetH - 14, Theme.mono(10), Theme.TEXT_FAINT);
    }

    private static void missionPanel(Graphics2D g, int x, int y, int w, int h, Model m) {
        UiKit.panel(g, x, y, w, h, Theme.PANEL, Theme.LINE);
        int px = x + Theme.PAD;
        int right = x + w - Theme.PAD;

        UiKit.sectionHeader(g, "03", "订单", px, right, y + 28);

        // 订单卡
        int cardY = y + 42;
        int cardH = 132;
        UiKit.panel(g, px, cardY, w - Theme.PAD * 2, cardH, Theme.RAISED, Theme.LINE_STRONG);
        UiKit.cornerTicks(g, px, cardY, w - Theme.PAD * 2, cardH, 8, Theme.ACCENT);

        UiKit.text(g, UiKit.ellipsize(g, m.missionType, Theme.sansBold(13), w - Theme.PAD * 2 - 24),
                px + 14, cardY + 24, Theme.sansBold(13), Theme.TEXT);

        UiKit.tag(g, "risk", px + 14, cardY + 48);
        for (int i = 0; i < 5; i++) {
            boolean on = i < Math.round(m.missionRisk);
            g.setColor(on ? Theme.DANGER : Theme.LINE_STRONG);
            g.fillRect(px + 56 + i * 10, cardY + 41, 7, 7);
        }
        UiKit.textRight(g, String.format("%.1f", m.missionRisk), px + w - Theme.PAD * 2 - 12,
                cardY + 48, Theme.mono(11), Theme.TEXT_DIM);

        UiKit.tag(g, "reward", px + 14, cardY + 70);
        UiKit.textRight(g, "€ " + Theme.num(m.missionReward), px + w - Theme.PAD * 2 - 12, cardY + 71,
                Theme.monoBold(14), Theme.ACCENT);

        UiKit.tag(g, "steps", px + 14, cardY + 92);
        UiKit.textRight(g, String.valueOf(m.missionSteps), px + w - Theme.PAD * 2 - 12, cardY + 92,
                Theme.mono(12), Theme.TEXT);

        String note = UiKit.ellipsize(g, m.missionNote, Theme.sans(10), w - Theme.PAD * 2 - 24);
        UiKit.text(g, note, px + 14, cardY + 116, Theme.sans(10), Theme.TEXT_FAINT);

        // 路线
        int ry = cardY + cardH + 24;
        UiKit.tag(g, "routes", px, ry);
        for (int i = 0; i < m.routes.length; i++) {
            int rowY = ry + 12 + i * 46;
            boolean hover = i == 0;
            g.setColor(hover ? Theme.RAISED : Theme.PANEL);
            g.fillRect(px, rowY, w - Theme.PAD * 2, 40);
            g.setColor(hover ? Theme.ACCENT : Theme.LINE);
            g.drawRect(px, rowY, w - Theme.PAD * 2 - 1, 39);
            if (hover) {
                g.setColor(Theme.ACCENT);
                g.fillRect(px, rowY, 3, 40);
            }
            UiKit.text(g, (i + 1) + ". " + m.routes[i], px + 14, rowY + 17,
                    Theme.sansBold(12), hover ? Theme.TEXT : Theme.TEXT_DIM);
            UiKit.text(g, m.routeHints[i], px + 14, rowY + 32, Theme.sans(10), Theme.TEXT_FAINT);
        }

        // 底部提示
        UiKit.hline(g, px, right, y + h - 30, Theme.LINE);
        UiKit.text(g, "选择路线将改变步数 / 风险 / 报酬", px, y + h - 14, Theme.sans(10), Theme.TEXT_FAINT);
    }

    private static void footer(Graphics2D g, int w, int h, Model m) {
        int y = h - FOOTER_H;
        g.setColor(Theme.PANEL);
        g.fillRect(0, y, w, FOOTER_H);
        UiKit.hline(g, 0, w, y, Theme.LINE);

        int bw = 120;
        int bh = 34;
        int gap = 10;
        int totalW = m.buttons.length * bw + (m.buttons.length - 1) * gap;
        int sx = (w - totalW) / 2;
        int by = y + (FOOTER_H - bh) / 2;
        for (int i = 0; i < m.buttons.length; i++) {
            UiKit.button(g, m.buttons[i], sx + i * (bw + gap), by, bw, bh, i == 0, i == 0);
        }
        UiKit.text(g, m.footerNote, 16, y + FOOTER_H / 2 + 4, Theme.mono(10), Theme.TEXT_FAINT);
        UiKit.textRight(g, "RAIN / 23:41", w - 16, y + FOOTER_H / 2 + 4, Theme.mono(10), Theme.TEXT_FAINT);
    }

    // ══════════════════════════════════════════════════════════
    //  顶部状态条（运行时 HUD 直接调用，不依赖三栏布局）
    // ══════════════════════════════════════════════════════════

    /**
     * 单行紧凑状态条，供 GameWindow 的 HUDPanel 使用。
     * 自适应宽度：窄窗口下优先保留代号与生命，其次现金，最后通缉与声望。
     */
    public static void hudStrip(Graphics2D g, int w, int h,
                                String callsign, int health, int maxHealth,
                                int cash, int target, int wanted, int wantedMax,
                                int reputation, int level) {
        UiKit.prepare(g);
        g.setColor(Theme.PANEL);
        g.fillRect(0, 0, w, h);
        UiKit.hline(g, 0, w, h - 1, Theme.LINE);

        int cy = h / 2;
        int x = 14;

        // 代号 + 等级
        Font nameFont = Theme.sansBold(14);
        UiKit.text(g, callsign, x, cy + 5, nameFont, Theme.TEXT);
        x += UiKit.width(g, callsign, nameFont) + 8;
        UiKit.text(g, "LV" + Theme.id(level, 2), x, cy + 4, Theme.mono(10), Theme.ACCENT);
        x += 42;

        // 生命
        int barW = Math.max(60, Math.min(160, (w - 520) / 3));
        UiKit.tag(g, "hp", x, cy - 8);
        double hp = maxHealth == 0 ? 0 : (double) health / maxHealth;
        UiKit.meter(g, x, cy - 2, barW, 8, hp, hp < 0.3 ? Theme.DANGER : Theme.OK);
        String hpText = health + "/" + maxHealth;
        UiKit.text(g, hpText, x + barW + 6, cy + 5, Theme.mono(10), Theme.TEXT_DIM);
        x += barW + 6 + UiKit.width(g, hpText, Theme.mono(10)) + 14;

        // 现金
        UiKit.tag(g, "cr", x, cy - 8);
        UiKit.text(g, "€" + Theme.num(cash), x, cy + 5, Theme.monoBold(13), Theme.ACCENT);
        x += Math.max(64, UiKit.width(g, "€" + Theme.num(target), Theme.monoBold(13))) + 16;

        // 通缉
        if (x < w - 150) {
            UiKit.tag(g, "wanted", x, cy - 8);
            UiKit.pips(g, x, cy - 4, wanted, wantedMax, Theme.DANGER, Theme.LINE_STRONG, 6);
            x += wantedMax * 10 + 14;
        }

        // 声望
        if (x < w - 90) {
            UiKit.tag(g, "rep", x, cy - 8);
            UiKit.text(g, String.valueOf(reputation), x, cy + 5, Theme.mono(12),
                    reputation < 20 ? Theme.DANGER : Theme.TEXT_DIM);
        }

        // 右上角刻度装饰
        for (int i = 0; i < 5; i++) {
            int tx = w - 14 - i * 6;
            g.setColor(i == 0 ? Theme.ACCENT : Theme.LINE);
            g.fillRect(tx, 6, 2, h - 12);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  成长面板
    // ══════════════════════════════════════════════════════════

    public static void growth(Graphics2D g, int w, int h) {
        UiKit.prepare(g);
        UiKit.background(g, w, h);

        g.setColor(Theme.PANEL);
        g.fillRect(0, 0, w, HEADER_H);
        UiKit.hline(g, 0, w, HEADER_H - 1, Theme.LINE);
        UiKit.text(g, "成长中心", 16, 31, Theme.sansBold(15), Theme.TEXT);
        UiKit.tracked(g, "PROGRESSION", 16 + 76, 30, Theme.mono(10), Theme.TEXT_FAINT, 3);
        UiKit.textRight(g, "技能点 3   现金 € 3,420", w - 16, 30, Theme.mono(12), Theme.ACCENT);

        int px = GUTTER;
        int py = HEADER_H + GUTTER;
        int colW = (w - GUTTER * 3) / 2;
        int colH = h - py - GUTTER;

        // 左：技能
        UiKit.panel(g, px, py, colW, colH, Theme.PANEL, Theme.LINE);
        UiKit.sectionHeader(g, "01", "技能树", px + Theme.PAD, px + colW - Theme.PAD, py + 28);
        String[][] skills = {
                {"潜行步法", "3", "事件自动闪避 +6%/级"},
                {"银舌头", "2", "贿赂与治疗费用 -10%/级"},
                {"扳手直觉", "1", "义体故障自愈 +25%/级"},
                {"街头之眼", "1", "可见隐藏变体与真实风险"},
                {"铁胃", "2", "成功额外回血 +8/级"},
                {"议价高手", "3", "任务报酬 +8%/级"},
                {"最后一搏", "0", "低血时受伤 -20%/级"},
                {"网潜者", "0", "无人机与扫描免疫"}
        };
        for (int i = 0; i < skills.length; i++) {
            int ry = py + 46 + i * 42;
            boolean maxed = skills[i][1].equals(skills[i][2]);
            UiKit.text(g, skills[i][0], px + Theme.PAD, ry + 14, Theme.sansBold(13),
                    maxed ? Theme.ACCENT : Theme.TEXT);
            UiKit.text(g, skills[i][2], px + Theme.PAD, ry + 30, Theme.mono(10), Theme.TEXT_FAINT);
            int rank = Integer.parseInt(skills[i][1]);
            int max = 3;
            for (int k = 0; k < max; k++) {
                int dx = px + colW - Theme.PAD - (max - k) * 14;
                g.setColor(k < rank ? Theme.ACCENT : Theme.LINE_STRONG);
                g.fillRect(dx, ry + 6, 9, 9);
            }
            UiKit.hline(g, px + Theme.PAD, px + colW - Theme.PAD, ry + 38, Theme.LINE);
        }

        // 右：义体 + 背包
        int rx = px + colW + GUTTER;
        int rightH = (colH - GUTTER) / 2;

        UiKit.panel(g, rx, py, colW, rightH, Theme.PANEL, Theme.LINE);
        UiKit.sectionHeader(g, "02", "义体槽 2/3", rx + Theme.PAD, rx + colW - Theme.PAD, py + 28);
        String[][] rigs = {
                {"冷藏箱", "黑市件与器官件报酬 +20%"},
                {"幽灵协处理器", "事件闪避与逃脱 +10%"},
                {"空闲槽位", "点击安装义体"}
        };
        for (int i = 0; i < rigs.length; i++) {
            int cy = py + 44 + i * 46;
            boolean empty = rigs[i][0].equals("空闲槽位");
            g.setColor(empty ? Theme.PANEL : Theme.RAISED);
            g.fillRect(rx + Theme.PAD, cy, colW - Theme.PAD * 2, 38);
            g.setColor(empty ? Theme.LINE : Theme.LINE_STRONG);
            g.drawRect(rx + Theme.PAD, cy, colW - Theme.PAD * 2 - 1, 37);
            if (!empty) {
                g.setColor(Theme.ACCENT);
                g.fillRect(rx + Theme.PAD, cy, 3, 38);
            }
            UiKit.text(g, rigs[i][0], rx + Theme.PAD + 12, cy + 17,
                    Theme.sansBold(12), empty ? Theme.TEXT_FAINT : Theme.TEXT);
            UiKit.text(g, rigs[i][1], rx + Theme.PAD + 12, cy + 31, Theme.mono(9), Theme.TEXT_FAINT);
        }

        int by = py + rightH + GUTTER;
        UiKit.panel(g, rx, by, colW, rightH, Theme.PANEL, Theme.LINE);
        UiKit.sectionHeader(g, "03", "背包", rx + Theme.PAD, rx + colW - Theme.PAD, by + 28);
        String[][] items = {
                {"急救针", "×3"}, {"战斗鸡尾酒", "×1"},
                {"影子证件", "×1"}, {"信号干扰器", "×2"},
                {"烟雾弹", "×2"}, {"空槽", ""},
                {"空槽", ""}, {"空槽", ""}
        };
        int cell = (colW - Theme.PAD * 2 - 30) / 4;
        for (int i = 0; i < 8; i++) {
            int cx = rx + Theme.PAD + (i % 4) * (cell + 10);
            int cy = by + 44 + (i / 4) * 54;
            boolean empty = items[i][0].equals("空槽");
            g.setColor(Theme.INSET);
            g.fillRect(cx, cy, cell, 46);
            g.setColor(empty ? Theme.LINE : Theme.LINE_STRONG);
            g.drawRect(cx, cy, cell - 1, 45);
            UiKit.text(g, items[i][0], cx + 8, cy + 20,
                    Theme.sans(11), empty ? Theme.TEXT_FAINT : Theme.TEXT);
            UiKit.text(g, items[i][1], cx + 8, cy + 36, Theme.mono(10), Theme.ACCENT);
        }
    }

    // ══════════════════════════════════════════════════════════
    //  成就墙
    // ══════════════════════════════════════════════════════════

    public static void achievements(Graphics2D g, int w, int h) {
        UiKit.prepare(g);
        UiKit.background(g, w, h);

        g.setColor(Theme.PANEL);
        g.fillRect(0, 0, w, HEADER_H);
        UiKit.hline(g, 0, w, HEADER_H - 1, Theme.LINE);
        UiKit.text(g, "成就", 16, 31, Theme.sansBold(15), Theme.TEXT);
        UiKit.tracked(g, "ACHIEVEMENTS", 16 + 44, 30, Theme.mono(10), Theme.TEXT_FAINT, 3);
        UiKit.textRight(g, "11 / 24", w - 16, 30, Theme.monoBold(13), Theme.ACCENT);

        String[][] list = {
                {"第一单", "完成第一次派送", "1"},
                {"连成一串", "连续 5 单无失败", "1"},
                {"十连", "连续 10 单无失败", "1"},
                {"攒够了", "个人资产达到 5000€", "1"},
                {"一条大单", "单笔报酬超过 1000€", "1"},
                {"夜之城的圣人", "善意选择累计 10 次", "1"},
                {"黑市常客", "完成 5 单黑市件", "1"},
                {"润滑剂", "累计行贿 5 次", "1"},
                {"街面通", "三条路线都走一遍", "1"},
                {"三件套", "同时装备三件义体", "1"},
                {"安静地离开", "达成「平凡逃离」结局", "1"},
                {"毫发无伤", "单局 10 单且从未受伤", "0"},
                {"通缉满级", "五级通缉下完成一单", "0"},
                {"屋顶的朋友", "拾荒者声望达到同盟", "0"},
                {"网潜", "网潜者技能满级", "0"},
                {"差一点", "1 点血完成一单", "0"},
                {"传奇", "达成「传奇英雄」结局", "0"},
                {"???", "隐藏成就", "-1"},
                {"???", "隐藏成就", "-1"},
                {"???", "隐藏成就", "-1"},
                {"逃亡艺术家", "累计成功逃脱 10 次", "0"},
                {"夜行者", "深夜时段完成 20 单", "0"},
                {"风雨无阻", "恶劣天气完成 10 单", "0"},
                {"第一次死", "在夜之城死过一次", "1"}
        };

        int cols = 4;
        int px = GUTTER;
        int py = HEADER_H + GUTTER;
        int cellW = (w - GUTTER * 2 - (cols - 1) * 12) / cols;
        int rows = (list.length + cols - 1) / cols;
        int cellH = (h - py - GUTTER - (rows - 1) * 12) / rows;
        for (int i = 0; i < list.length; i++) {
            int cx = px + (i % cols) * (cellW + 12);
            int cy = py + (i / cols) * (cellH + 12);
            int state = Integer.parseInt(list[i][2]);
            boolean locked = state == 0;
            boolean hidden = state == -1;

            g.setColor(locked || hidden ? Theme.PANEL : Theme.RAISED);
            g.fillRect(cx, cy, cellW, cellH);
            g.setColor(hidden ? Theme.LINE : locked ? Theme.LINE : Theme.LINE_STRONG);
            g.drawRect(cx, cy, cellW - 1, cellH - 1);

            if (!locked && !hidden) {
                g.setColor(Theme.ACCENT);
                g.fillRect(cx, cy, cellW, 2);
                UiKit.text(g, "★", cx + 12, cy + 28, Theme.sans(16), Theme.ACCENT);
            } else if (hidden) {
                UiKit.text(g, "?", cx + 14, cy + 28, Theme.sansBold(15), Theme.TEXT_FAINT);
            } else {
                UiKit.text(g, "☆", cx + 12, cy + 28, Theme.sans(16), Theme.TEXT_FAINT);
            }

            Font titleFont = Theme.sansBold(12);
            UiKit.text(g, UiKit.ellipsize(g, list[i][0], titleFont, cellW - 44),
                    cx + 32, cy + 27, titleFont, locked || hidden ? Theme.TEXT_FAINT : Theme.TEXT);
            Font descFont = Theme.sans(10);
            UiKit.text(g, UiKit.ellipsize(g, list[i][1], descFont, cellW - 24),
                    cx + 12, cy + 48, descFont, Theme.TEXT_FAINT);
            if (!locked && !hidden) {
                UiKit.tag(g, "unlocked", cx + 12, cy + 66);
            }
        }
    }

    /** 便捷方法：把任意界面渲染到图片，供预览与截图使用。 */
    public static BufferedImage render(int w, int h, java.util.function.Consumer<Graphics2D> painter) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        try {
            painter.accept(g);
        } finally {
            g.dispose();
        }
        return img;
    }
}
