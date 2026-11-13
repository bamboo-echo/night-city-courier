package com.moji.NightCityCourier.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;

/**
 * 自绘原语集合。
 *
 * <p>全部是无状态的静态方法，只依赖 {@link Graphics2D} 与 {@link Theme}。
 * 这样同一套绘制代码既能给 Swing 组件用，也能在无显示环境下直接画进
 * {@code BufferedImage}（见 {@link Screens}），保证「看到的」就是「跑起来的」。
 *
 * <p>所有方法都不创建图片资源、不依赖字体文件，纯几何 + 文字。
 */
public final class UiKit {

    private UiKit() {
    }

    // ────────── 画布 ──────────

    public static Graphics2D prepare(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        return g;
    }

    /** 底色 + 极弱点阵。点距 24px，亮度只够让平坦区域不显廉价。 */
    public static void background(Graphics2D g, int w, int h) {
        g.setColor(Theme.BG);
        g.fillRect(0, 0, w, h);
        g.setColor(Theme.GRID);
        for (int y = 12; y < h; y += 24) {
            for (int x = 12; x < w; x += 24) {
                g.fillRect(x, y, 1, 1);
            }
        }
    }

    /** 一像素细线。故意不抗锯齿偏移，保持锐利。 */
    public static void hline(Graphics2D g, int x1, int x2, int y, Color color) {
        g.setColor(color);
        g.fillRect(x1, y, x2 - x1, 1);
    }

    public static void vline(Graphics2D g, int y1, int y2, int x, Color color) {
        g.setColor(color);
        g.fillRect(x, y1, 1, y2 - y1);
    }

    /** 面板：实底 + 细描边，可选四角刻线。 */
    public static void panel(Graphics2D g, int x, int y, int w, int h, Color fill, Color stroke) {
        g.setColor(fill);
        g.fillRect(x, y, w, h);
        g.setColor(stroke);
        g.drawRect(x, y, w - 1, h - 1);
    }

    /** 工业感四角刻线：在矩形的四个角各画一段短促的亮线。 */
    public static void cornerTicks(Graphics2D g, int x, int y, int w, int h, int len, Color color) {
        g.setColor(color);
        g.fillRect(x, y, len, 1);
        g.fillRect(x, y, 1, len);
        g.fillRect(x + w - len, y, len, 1);
        g.fillRect(x + w - 1, y, 1, len);
        g.fillRect(x, y + h - 1, len, 1);
        g.fillRect(x, y + h - len, 1, len);
        g.fillRect(x + w - len, y + h - 1, len, 1);
        g.fillRect(x + w - 1, y + h - len, 1, len);
    }

    // ────────── 文字 ──────────

    public static void text(Graphics2D g, String s, int x, int baseline, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        g.drawString(s, x, baseline);
    }

    public static void textCenter(Graphics2D g, String s, int cx, int baseline, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, cx - fm.stringWidth(s) / 2, baseline);
    }

    public static void textRight(Graphics2D g, String s, int right, int baseline, Font font, Color color) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(s, right - fm.stringWidth(s), baseline);
    }

    /**
     * 带字距的文字。逐字符绘制，仅用于全大写小标签——
     * 这是工业标牌的关键细节，普通 drawString 做不出来。
     */
    public static void tracked(Graphics2D g, String s, int x, int baseline, Font font, Color color, int spacing) {
        g.setFont(font);
        g.setColor(color);
        FontMetrics fm = g.getFontMetrics();
        int cursor = x;
        for (int i = 0; i < s.length(); i++) {
            String ch = String.valueOf(s.charAt(i));
            g.drawString(ch, cursor, baseline);
            cursor += fm.stringWidth(ch) + spacing;
        }
    }

    public static int trackedWidth(Graphics2D g, String s, Font font, int spacing) {
        FontMetrics fm = g.getFontMetrics(font);
        int total = 0;
        for (int i = 0; i < s.length(); i++) {
            total += fm.stringWidth(String.valueOf(s.charAt(i))) + spacing;
        }
        return Math.max(0, total - spacing);
    }

    /** 小标签：全大写 + 字距 2px + 次文字色。 */
    public static void tag(Graphics2D g, String s, int x, int baseline) {
        tracked(g, s.toUpperCase(), x, baseline, Theme.mono(10), Theme.TEXT_FAINT, 2);
    }

    /** 键值对：暗色键 + 亮色等宽值，中间用点线填充，是仪表盘的经典排法。 */
    public static void keyValue(Graphics2D g, String key, String value, int x, int right, int baseline, int dotY) {
        text(g, key, x, baseline, Theme.sans(12), Theme.TEXT_DIM);
        textRight(g, value, right, baseline, Theme.mono(13), Theme.TEXT);
        FontMetrics fm = g.getFontMetrics(Theme.sans(12));
        int startX = x + fm.stringWidth(key) + 8;
        int endX = right - g.getFontMetrics(Theme.mono(13)).stringWidth(value) - 8;
        if (endX > startX) {
            g.setColor(Theme.LINE);
            for (int dx = startX; dx < endX; dx += 4) {
                g.fillRect(dx, dotY, 1, 1);
            }
        }
    }

    // ────────── 图形组件 ──────────

    /**
     * 细条计量器。无圆角、无渐变，靠「已填充段 + 刻度」表达数值，
     * 危险时整条换成砖红。
     */
    public static void meter(Graphics2D g, int x, int y, int w, int h, double ratio, Color color) {
        double r = Math.max(0, Math.min(1, ratio));
        g.setColor(Theme.INSET);
        g.fillRect(x, y, w, h);
        g.setColor(color);
        g.fillRect(x, y, (int) Math.round(w * r), h);
        // 每 10% 一道刻度缝隙，便于读数而不必看数字
        g.setColor(Theme.BG);
        for (int i = 1; i < 10; i++) {
            int gx = x + w * i / 10;
            g.fillRect(gx, y, 1, h);
        }
        g.setColor(Theme.LINE_STRONG);
        g.drawRect(x, y, w - 1, h - 1);
    }

    /** 描边徽章：小写字+细框，用于状态标记。 */
    public static void badge(Graphics2D g, String label, int x, int y, Color color) {
        Font f = Theme.mono(10);
        FontMetrics fm = g.getFontMetrics(f);
        int w = fm.stringWidth(label) + 12;
        int h = 18;
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 38));
        g.fillRect(x, y, w, h);
        g.setColor(color);
        g.drawRect(x, y, w - 1, h - 1);
        text(g, label, x + 6, y + 13, f, color);
    }

    public static int badgeWidth(Graphics2D g, String label) {
        return g.getFontMetrics(Theme.mono(10)).stringWidth(label) + 12;
    }

    /** 分组标题：编号 + 标题 + 一条延伸到右边的细线。 */
    public static void sectionHeader(Graphics2D g, String index, String title, int x, int right, int baseline) {
        text(g, index, x, baseline, Theme.mono(11), Theme.ACCENT);
        Font f = Theme.sansBold(13);
        int tx = x + g.getFontMetrics(Theme.mono(11)).stringWidth(index) + 8;
        text(g, title, tx, baseline, f, Theme.TEXT);
        int lineX = tx + g.getFontMetrics(f).stringWidth(title) + 12;
        if (right > lineX) {
            hline(g, lineX, right, baseline - 4, Theme.LINE);
        }
    }

    /** 星标：通缉等级用实心/空心方点表示，比星星更冷硬。 */
    public static void pips(Graphics2D g, int x, int y, int count, int max, Color on, Color off, int size) {
        for (int i = 0; i < max; i++) {
            int px = x + i * (size + 4);
            g.setColor(i < count ? on : off);
            g.fillRect(px, y, size, size);
        }
    }

    /** 细十字准星装饰，用于空白处收边。 */
    public static void crosshair(Graphics2D g, int cx, int cy, int r, Color color) {
        g.setColor(color);
        g.fillRect(cx - r, cy, r * 2 + 1, 1);
        g.fillRect(cx, cy - r, 1, r * 2 + 1);
    }

    /** 斜线填充条（危险标识），用 1px 斜线簇。 */
    public static void hazardStripe(Graphics2D g, int x, int y, int w, int h, Color color) {
        g.setColor(color);
        for (int i = -h; i < w; i += 6) {
            g.drawLine(x + i, y + h, x + i + h, y);
        }
    }

    /** 描边按钮的绘制（hover 时反色：底变亮、字变暗）。 */
    public static void button(Graphics2D g, String label, int x, int y, int w, int h,
                              boolean hover, boolean primary) {
        // 主按钮也不做反白：暗底 + 琥珀描边 + 琥珀字，保持整屏只有一种亮度焦点
        Color fill = primary ? Theme.RAISED : Theme.PANEL;
        Color stroke = primary ? Theme.ACCENT : Theme.LINE_STRONG;
        Color fg = primary ? Theme.ACCENT : Theme.TEXT_DIM;
        if (hover) {
            fill = primary ? Theme.ACCENT : Theme.RAISED;
            stroke = Theme.ACCENT;
            fg = primary ? Theme.BG : Theme.ACCENT;
        }
        g.setColor(fill);
        g.fillRect(x, y, w, h);
        g.setColor(stroke);
        g.drawRect(x, y, w - 1, h - 1);
        textCenter(g, label, x + w / 2, y + h / 2 + 5, Theme.sansBold(13), fg);
    }

    /** 测量文本宽度。 */
    public static int width(Graphics2D g, String s, Font f) {
        return g.getFontMetrics(f).stringWidth(s);
    }

    /** 截断过长文本并加省略号。 */
    public static String ellipsize(Graphics2D g, String s, Font f, int maxWidth) {
        if (s == null) {
            return "";
        }
        if (width(g, s, f) <= maxWidth) {
            return s;
        }
        String ell = "…";
        int ellW = width(g, ell, f);
        int limit = maxWidth - ellW;
        int lo = 0;
        int hi = s.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (width(g, s.substring(0, mid), f) <= limit) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return s.substring(0, Math.max(0, lo)) + ell;
    }

    /** 圆角仅用于弹窗按钮这类需要柔化的少量元素，半径 2px。 */
    public static void softRect(Graphics2D g, int x, int y, int w, int h, Color fill, Color stroke, float strokeWidth) {
        g.setColor(fill);
        g.fill(new Rectangle2D.Float(x, y, w, h));
        if (stroke != null) {
            g.setColor(stroke);
            g.setStroke(new BasicStroke(strokeWidth));
            g.draw(new Rectangle2D.Float(x + 0.5f, y + 0.5f, w - 1, h - 1));
            g.setStroke(new BasicStroke(1f));
        }
    }
}
