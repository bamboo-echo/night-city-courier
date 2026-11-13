package com.moji.NightCityCourier.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.HashSet;
import java.util.Set;

/**
 * 设计令牌（Design Tokens）。
 *
 * <p>整体方向是「雨夜工业 HUD」而非千禧年霓虹：近黑冷灰底、单一琥珀强调色、
 * 一像素细描边、锐角或极小二像素圆角、等宽字体承担数字与标签。
 * 刻意不使用渐变、外发光、玻璃模糊、荧光粉紫与大圆角——那些会让界面显得廉价。
 *
 * <p>层级规则只有一条：<b>颜色越亮，信息越重要</b>。
 * 底 < 面板 < 描边 < 次文字 < 主文字 < 强调。任何装饰元素都不允许亮过它所要表达的内容。
 */
public final class Theme {

    // ────────── 背景层级 ──────────

    /** 最底层：近黑偏冷，模拟雨夜玻璃后的城市。 */
    public static final Color BG = new Color(0x0B0D10);
    /** 面板底：比背景亮一档，靠描边而非阴影区分层级。 */
    public static final Color PANEL = new Color(0x12151A);
    /** 凹陷区（文本终端内部）。 */
    public static final Color INSET = new Color(0x0E1014);
    /** 悬浮/选中态底。 */
    public static final Color RAISED = new Color(0x191D24);

    // ────────── 线条 ──────────

    /** 常规描边：暗到接近不可见，只勾出结构。 */
    public static final Color LINE = new Color(0x1E242C);
    /** 强调描边：用于分组标题下方。 */
    public static final Color LINE_STRONG = new Color(0x333D49);
    /** 极弱网格点，制造工业底纹。 */
    public static final Color GRID = new Color(0x161A20);

    // ────────── 文字 ──────────

    public static final Color TEXT = new Color(0xE2E8F0);
    public static final Color TEXT_DIM = new Color(0x9AA5B3);
    public static final Color TEXT_FAINT = new Color(0x66707C);
    /** 反色文字，用于强调块内。 */
    public static final Color TEXT_INVERSE = new Color(0x0B0D10);

    // ────────── 语义色（唯一强调 + 三个状态） ──────────

    /** 唯一强调色：琥珀。像钠灯，也像旧仪表盘的警示灯。 */
    public static final Color ACCENT = new Color(0xE8A33D);
    public static final Color ACCENT_DIM = new Color(0x8A6224);
    /** 危险：低饱和砖红，不用纯红。 */
    public static final Color DANGER = new Color(0xC4473B);
    public static final Color DANGER_DIM = new Color(0x5E2622);
    /** 安全/正向：冷绿，避免荧光青。 */
    public static final Color OK = new Color(0x5C9C7A);
    /** 中性数据色：雾蓝。 */
    public static final Color INFO = new Color(0x6E8FA8);

    /** 兼容旧代码的青色别名，统一指向雾蓝，避免残留霓虹感。 */
    public static final Color CYAN = INFO;

    // ────────── 尺寸 ──────────

    public static final int RADIUS = 2;
    public static final int PAD = 16;
    public static final int PAD_S = 8;
    public static final int PAD_L = 24;
    public static final int HAIRLINE = 1;

    // ────────── 字体 ──────────

    private static final String[] MONO_CANDIDATES = {
            "JetBrains Mono", "Cascadia Mono", "Consolas", "SF Mono", "Menlo", "Monaco",
            "Noto Sans Mono CJK SC", "Sarasa Mono SC", "Source Han Mono SC",
            "WenQuanYi Micro Hei Mono", "DejaVu Sans Mono", "Monospaced"
    };

    private static final String[] SANS_CANDIDATES = {
            "Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC", "Hiragino Sans GB",
            "Noto Sans CJK SC", "Source Han Sans SC", "WenQuanYi Micro Hei", "Noto Sans SC",
            "SansSerif"
    };

    /**
     * 等宽族必须能显示中文。曾经的顺序把 DejaVu Sans Mono 排在 CJK 等宽之前，
     * 结果所有中文（叙事正文、系统提示、路线描述）在等宽字体下渲染成豆腐块。
     * 现在逐个校验 canDisplay('中')，不合格直接跳过。
     */
    private static final String MONO_STACK = firstAvailableCjk(MONO_CANDIDATES);

    private static final String SANS_STACK = firstAvailableCjk(SANS_CANDIDATES);

    private Theme() {
    }

    /** 等宽：用于所有数字、代码式标签、编号、进度值。 */
    public static Font mono(int size) {
        return new Font(MONO_STACK, Font.PLAIN, size);
    }

    public static Font monoBold(int size) {
        return new Font(MONO_STACK, Font.BOLD, size);
    }

    /** 无衬线黑体：用于中文正文与标题。 */
    public static Font sans(int size) {
        return new Font(SANS_STACK, Font.PLAIN, size);
    }

    public static Font sansBold(int size) {
        return new Font(SANS_STACK, Font.BOLD, size);
    }

    public static String monoFamily() {
        return MONO_STACK;
    }

    public static String sansFamily() {
        return SANS_STACK;
    }

    /** 按优先级取第一个「已安装且能显示中文」的字体族。 */
    private static String firstAvailableCjk(String... candidates) {
        Set<String> available = new HashSet<>();
        try {
            String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
            for (String n : names) {
                available.add(n);
            }
        } catch (Throwable ignored) {
            return "SansSerif";
        }
        for (String c : candidates) {
            if (!available.contains(c)) {
                continue;
            }
            Font probe = new Font(c, Font.PLAIN, 12);
            if (probe.canDisplay('中') && probe.canDisplay('A')) {
                return c;
            }
        }
        // 没有任何候选能显示中文，退回逻辑字体（Swing 会按 fontconfig 做 per-glyph 回落）
        return "SansSerif";
    }

    /** 按优先级取第一个真实安装的字体族；全不命中退回逻辑字体。 */
    private static String firstAvailable(String... candidates) {
        Set<String> available = new HashSet<>();
        try {
            String[] names = GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getAvailableFontFamilyNames();
            for (String n : names) {
                available.add(n);
            }
        } catch (Throwable ignored) {
            return "SansSerif";
        }
        for (String c : candidates) {
            if (available.contains(c)) {
                return c;
            }
        }
        return "SansSerif";
    }

    /** 数值按千分位格式化，与等宽字体配合可对齐小数点。 */
    public static String num(long value) {
        return String.format("%,d", value);
    }

    /** 补齐到指定宽度的编号，如 003。 */
    public static String id(int value, int width) {
        return String.format("%0" + width + "d", value);
    }

    /** 血量/进度条的分段字符画，用等宽字体渲染。 */
    public static String bar(double ratio, int cells) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, ratio)) * cells);
        StringBuilder sb = new StringBuilder(cells);
        for (int i = 0; i < cells; i++) {
            sb.append(i < filled ? '█' : '░');
        }
        return sb.toString();
    }
}
