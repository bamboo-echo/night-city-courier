package com.moji.NightCityCourier;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 游戏主窗口，负责所有UI渲染、打字机效果、按钮交互、HUD显示。
 * 包含内部类：HUDPanel（状态栏）、ParticlePanel（粒子效果）、
 * ScanlineOverlay（扫描线）、TerminalStateManager（终端状态动画）、RoundedBorder（圆角边框）。
 * <p>
 * 使用 BlockingQueue 实现游戏线程与 UI 线程之间的同步通信。
 */
public class GameWindow extends JFrame {

    private static final Color BG_DARK = new Color(0x0A, 0x0A, 0x14);
    private static final Color BG_TEXT_AREA = new Color(0x0F, 0x0F, 0x1A);
    private static final Color FG_MAIN = new Color(0xC0, 0xC0, 0xC0);
    private static final Color FG_PURPLE = new Color(0xB3, 0x66, 0xFF);
    private static final Color FG_CYAN = new Color(0x00, 0xCC, 0xCC);
    private static final Color FG_DANGER = new Color(0xFF, 0x6B, 0x6B);
    private static final Color FG_SUCCESS = new Color(0x00, 0xCC, 0x66);
    private static final Color FG_WARNING = new Color(0xFF, 0xB3, 0x00);
    private static final Color FG_MAGENTA = new Color(0xFF, 0x00, 0x7F);
    private static final Color FG_WHITE_BOLD = new Color(0xFF, 0xFF, 0xFF);
    private static final Color BORDER_COLOR = new Color(0x1A, 0x3A, 0x3A);
    private static final Color BUTTON_BG = new Color(0x0A, 0x0A, 0x14);
    private static final Color BUTTON_FG = FG_CYAN;
    private static final Color BUTTON_BORDER = new Color(0x00, 0xCC, 0xCC);
    private static final Color BUTTON_HOVER_BG = new Color(0x00, 0xCC, 0xCC, 0x1A);
    private static final Color BUTTON_HOVER_BORDER = new Color(0x00, 0xFF, 0xFF);
    private static final Color BUTTON_HOVER_GLOW = new Color(0x00, 0xFF, 0xFF, 0x40);
    private static final Color DIMMED_TEXT = new Color(0x55, 0x55, 0x66);

    private static final Font TERMINAL_FONT = new Font("Microsoft YaHei", Font.PLAIN, 16);
    private static final Font BUTTON_FONT = new Font("Microsoft YaHei", Font.BOLD, 15);
    private static final Font TITLE_FONT = new Font("Microsoft YaHei", Font.PLAIN, 11);

    private JTextPane outputPane;
    private StyledDocument doc;
    private final BlockingQueue<Integer> menuQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<Integer> choiceQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<String> lineQueue = new LinkedBlockingQueue<>();
    private JPanel buttonPanel;
    private Timer typewriterTimer;
    private String currentLine = null;
    private int currentPos = 0;

    private final AtomicBoolean skipCurrentLine = new AtomicBoolean(false);
    private final AtomicBoolean skipAllLines = new AtomicBoolean(false);

    private ParticlePanel particlePanel;
    private ScanlineOverlay scanlineOverlay;
    private HUDPanel hudPanel;
    private JPanel titleBar;
    private JLabel titleLabel;
    private JLabel statusLabel;
    private Timer statusTimer;
    private Timer glitchTimer;

    private int typewriterDelay = 40;
    private double glitchChance = 0;
    private int glitchSkipChance = 0;
    private int baseGlitchInterval = 45000;

    private int lineCount = 0;
    private int lastDimmedLine = 0;
    private Player player;
    private Style cachedDimStyle;
    private Style cachedMainStyle;
    private Style cachedEmojiStyle;
    private static final int MAX_DOC_CHARS = 30000;
    private int dimCounter = 0;
    private static final int DIM_INTERVAL = 20;

    public void setPlayer(Player player) {
        this.player = player;
    }

    public GameWindow() {
        initUI();
        redirectSystemOut();
        startTypewriter();
        enableSkipOnKeyPress();
        startCursorBlink();
    }

    private void initUI() {
        setTitle("夜之城快递员 OS v1.3.7");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 750);
        setLocationRelativeTo(null);
        setBackground(BG_DARK);

        // ---------- 标题栏 ----------
        titleBar = createTitleBar();
        add(titleBar, BorderLayout.NORTH);

        // ---------- 主体分层容器 ----------
        JLayeredPane layeredPane = new JLayeredPane();
        layeredPane.setBackground(BG_DARK);
        layeredPane.setOpaque(true);

        // 叙事文本区（底层）
        outputPane = new JTextPane();
        outputPane.setEditable(false);
        outputPane.setBackground(BG_TEXT_AREA);
        outputPane.setForeground(FG_MAIN);
        outputPane.setCaretColor(FG_CYAN);
        outputPane.setFont(TERMINAL_FONT);
        outputPane.setBorder(new EmptyBorder(10, 15, 10, 15));
        MutableAttributeSet globalAttrs = new SimpleAttributeSet();
        StyleConstants.setLineSpacing(globalAttrs, 0.35f);
        StyleConstants.setFontFamily(globalAttrs, "Microsoft YaHei");
        StyleConstants.setFontSize(globalAttrs, 16);
        outputPane.setParagraphAttributes(globalAttrs, true);
        doc = outputPane.getStyledDocument();

        JScrollPane scrollPane = new JScrollPane(outputPane);
        scrollPane.setBackground(BG_DARK);
        scrollPane.getViewport().setBackground(BG_TEXT_AREA);
        scrollPane.setBorder(new LineBorder(BORDER_COLOR, 1));
        scrollPane.setViewportBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel narrativePanel = new JPanel(new BorderLayout());
        narrativePanel.setBackground(BG_TEXT_AREA);
        narrativePanel.add(scrollPane, BorderLayout.CENTER);

        // 粒子面板（中层，透明）
        particlePanel = new ParticlePanel();
        particlePanel.initParticles(800, 700);
        particlePanel.setOpaque(false);

        // 扫描线覆盖（顶层，透明）
        scanlineOverlay = new ScanlineOverlay();
        scanlineOverlay.setOpaque(false);

        // HUD面板（右侧）
        hudPanel = new HUDPanel();
        hudPanel.setPreferredSize(new Dimension(320, 0));
        hudPanel.setMinimumSize(new Dimension(280, 0));

        // 使用 JSplitPane 容纳叙事区 + HUD
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, narrativePanel, hudPanel);
        mainSplit.setDividerSize(1);
        mainSplit.setResizeWeight(0.7);
        mainSplit.setOpaque(false);
        mainSplit.setBorder(null);

        // 分层添加
        mainSplit.setBounds(0, 0, 1200, 700);     // 底层内容
        particlePanel.setBounds(0, 0, 1200, 700); // 中层粒子
        scanlineOverlay.setBounds(0, 0, 1200, 700); // 顶层扫描线

        layeredPane.add(mainSplit, Integer.valueOf(0));
        layeredPane.add(particlePanel, Integer.valueOf(1));
        layeredPane.add(scanlineOverlay, Integer.valueOf(2));

        // 按钮面板
        buttonPanel = createButtonPanel();

        // 放入主界面
        add(layeredPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // 组件尺寸自适应
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                Dimension size = layeredPane.getSize();
                mainSplit.setBounds(0, 0, size.width, size.height);
                particlePanel.setBounds(0, 0, size.width, size.height);
                scanlineOverlay.setBounds(0, 0, size.width, size.height);
            }
        });

        enableWindowDrag();
        startGlitchEffect();
        startStatusUpdate();
    }

    private JPanel createTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(0x08, 0x08, 0x10));
        bar.setPreferredSize(new Dimension(getWidth(), 30));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0x1A, 0x3A, 0x3A, 0x40)));

        titleLabel = new JLabel("夜之城快递员 OS v1.3.7");
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(FG_CYAN);
        titleLabel.setBorder(new EmptyBorder(0, 12, 0, 0));

        statusLabel = new JLabel();
        statusLabel.setFont(TITLE_FONT);
        statusLabel.setForeground(FG_WARNING);
        statusLabel.setBorder(new EmptyBorder(0, 0, 0, 12));
        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        bar.add(titleLabel, BorderLayout.WEST);
        bar.add(statusLabel, BorderLayout.EAST);
        return bar;
    }

    private void startStatusUpdate() {
        if (statusTimer != null) statusTimer.stop();
        statusTimer = new Timer(1000, e -> updateStatusBar());
        statusTimer.start();
    }

    private void updateStatusBar() {
        if (statusLabel == null) return;
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        String wanted = "";
        if (hudPanel != null) {
            int wl = hudPanel.getWantedLevel();
            wanted = "通缉: ";
            for (int i = 0; i < 5; i++) {
                wanted += i < wl ? "★" : "☆";
            }
        } else {
            wanted = "通缉: ★☆☆☆☆";
        }
        statusLabel.setText(time + "  " + wanted);
    }

    private JPanel createButtonPanel() {
        JPanel buttonPanel = new JPanel(new GridLayout(2, 4, 10, 10));
        buttonPanel.setBackground(BG_DARK);
        buttonPanel.setBorder(new EmptyBorder(8, 12, 12, 12));

        buttonPanel.add(createMenuButton("接单送货", 1));
        buttonPanel.add(createMenuButton("升级装备", 2));
        buttonPanel.add(createMenuButton("查看目标", 3));
        buttonPanel.add(createMenuButton("治疗", 4));
        buttonPanel.add(createMenuButton("统计", 5));
        buttonPanel.add(createMenuButton("最后一单", 6));
        buttonPanel.add(createMenuButton("存档", 7));
        buttonPanel.add(createMenuButton("退出游戏", 8));
        return buttonPanel;
    }

    private JButton createMenuButton(String text, int command) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(BUTTON_HOVER_BG);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.setColor(BUTTON_HOVER_GLOW);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                }
                g2.setColor(BUTTON_BORDER);
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(BUTTON_FONT);
        btn.setBackground(BUTTON_BG);
        btn.setForeground(BUTTON_FG);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(4, 8, 4, 8));
        btn.setPreferredSize(new Dimension(110, 40));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            menuQueue.offer(command);
            SwingUtilities.invokeLater(() -> {
                buttonPanel.removeAll();
                buttonPanel.revalidate();
                buttonPanel.repaint();
            });
        });

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.repaint();
            }
            @Override
            public void mouseExited(MouseEvent e) {
                btn.repaint();
            }
        });

        btn.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(String.valueOf(command)), "cmd" + command);
        btn.getActionMap().put("cmd" + command, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                menuQueue.offer(command);
            }
        });

        return btn;
    }

    private void enableWindowDrag() {
        if (titleBar == null) return;
        final Point clickPoint = new Point();
        MouseAdapter dragListener = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                clickPoint.setLocation(e.getPoint());
            }
            @Override
            public void mouseDragged(MouseEvent e) {
                Point current = e.getLocationOnScreen();
                setLocation(current.x - clickPoint.x, current.y - clickPoint.y);
            }
        };
        titleBar.addMouseListener(dragListener);
        titleBar.addMouseMotionListener(dragListener);
    }

    private void startGlitchEffect() {
        if (glitchTimer != null) glitchTimer.stop();
        scheduleNextGlitch();
    }

    private void scheduleNextGlitch() {
        int delay = baseGlitchInterval + (int) (Math.random() * 30000);
        if (hudPanel != null) {
            int wl = hudPanel.getWantedLevel();
            if (wl >= 3) {
                delay = 15000 + (int) (Math.random() * 15000);
            }
            if (wl >= 4) {
                delay = 8000 + (int) (Math.random() * 12000);
            }
            if (wl >= 5) {
                delay = 5000 + (int) (Math.random() * 10000);
            }
        }
        glitchTimer = new Timer(delay, e -> triggerGlitch());
        glitchTimer.setRepeats(false);
        glitchTimer.start();
    }

    private void triggerGlitch() {
        int dx = (int) (Math.random() * 5) - 2;
        int dy = (int) (Math.random() * 3) - 1;
        setLocation(getLocation().x + dx, getLocation().y + dy);
        Timer restore = new Timer(50, e -> {
            setLocation(getLocation().x - dx, getLocation().y - dy);
            ((Timer) e.getSource()).stop();
            scheduleNextGlitch();
        });
        restore.setRepeats(false);
        restore.start();
    }

    private void startCursorBlink() {
        outputPane.setCaretColor(FG_CYAN);
    }

    public int getMenuChoice() {
        restoreMenuButtons();
        try {
            return menuQueue.take();
        } catch (InterruptedException e) {
            return 8;
        }
    }

    private void startTypewriter() {
        typewriterTimer = new Timer(typewriterDelay, e -> {
            if (skipAllLines.get()) {
                StringBuilder allText = new StringBuilder();
                if (currentLine != null && currentPos < currentLine.length()) {
                    allText.append(currentLine.substring(currentPos)).append("\n");
                    currentLine = null;
                    currentPos = 0;
                    lineCount++;
                }
                String line;
                while ((line = lineQueue.poll()) != null) {
                    allText.append(processGlitchText(line)).append("\n");
                    lineCount++;
                }
                if (allText.length() > 0) {
                    try {
                        ensureMainStyle();
                        doc.insertString(doc.getLength(), allText.toString(), cachedMainStyle);
                        outputPane.setCaretPosition(doc.getLength());
                    } catch (BadLocationException ex) {
                        ex.printStackTrace();
                    }
                }
                dimOldLines();
                trimDocument();
                skipAllLines.set(false);
                skipCurrentLine.set(false);
                setTypewriterIdle(true);
                return;
            }
            if (skipCurrentLine.get() && currentLine != null) {
                flushCurrentLine();
                skipCurrentLine.set(false);
                return;
            }
            if (currentLine == null) {
                String next = lineQueue.poll();
                if (next != null) {
                    setTypewriterIdle(false);
                    String processed = processGlitchText(next);
                    currentLine = processed;
                    currentPos = 0;
                    currentLineColor = FG_MAIN;
                    currentLineBold = false;
                    maybeInsertGlitchLine();
                } else {
                    setTypewriterIdle(true);
                    return;
                }
            }
            if (currentLine.isEmpty()) {
                try {
                    doc.insertString(doc.getLength(), "\n", null);
                    outputPane.setCaretPosition(doc.getLength());
                    lineCount++;
                    dimCounter++;
                    if (dimCounter >= DIM_INTERVAL) {
                        dimOldLines();
                        dimCounter = 0;
                    }
                    trimDocument();
                } catch (BadLocationException ex) {
                    ex.printStackTrace();
                }
                currentLine = null;
                currentPos = 0;
                return;
            }
            if (currentPos < currentLine.length()) {
                outputNextChar();
            } else {
                finishCurrentLine();
            }
        });
        typewriterTimer.start();
    }

    private void finishCurrentLine() {
        try {
            doc.insertString(doc.getLength(), "\n", null);
            outputPane.setCaretPosition(doc.getLength());
            lineCount++;
            dimCounter++;
            if (dimCounter >= DIM_INTERVAL) {
                dimOldLines();
                dimCounter = 0;
            }
            trimDocument();
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        currentLine = null;
        currentPos = 0;
        if (lineQueue.isEmpty()) {
            setTypewriterIdle(true);
        }
    }

    private String processGlitchText(String text) {
        if (glitchSkipChance <= 0 || Math.random() * 100 >= glitchSkipChance) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        String[] glitchChars = new String[]{"█", "▓", "▒", "░", "", "■", "□"};
        int i = 0;
        while (i < text.length()) {
            char ch = text.charAt(i);
            if (Character.isHighSurrogate(ch) && i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                if (Math.random() * 100 < glitchSkipChance) {
                    sb.append(glitchChars[(int) (Math.random() * glitchChars.length)]);
                } else {
                    sb.append(ch).append(text.charAt(i + 1));
                }
                i += 2;
                continue;
            }
            if (Math.random() * 100 < glitchSkipChance) {
                sb.append(glitchChars[(int) (Math.random() * glitchChars.length)]);
            } else {
                sb.append(ch);
            }
            i++;
        }
        return sb.toString();
    }

    private void maybeInsertGlitchLine() {
    }

    private Color currentLineColor = FG_MAIN;
    private boolean currentLineBold = false;
    private Style cachedColorStyle = null;
    private Color cachedColorStyleColor = null;
    private boolean cachedColorStyleBold = false;

    private void outputNextChar() {
        if (currentPos >= currentLine.length()) return;
        char ch = currentLine.charAt(currentPos);

        if (ch == '[' && currentPos + 3 < currentLine.length()
                && currentLine.charAt(currentPos + 2) == ':'
                && currentLine.charAt(currentPos + 3) == ']') {
            char colorCode = currentLine.charAt(currentPos + 1);
            currentLineColor = switch (colorCode) {
                case 'R' -> FG_DANGER;
                case 'G' -> FG_SUCCESS;
                case 'C' -> FG_CYAN;
                case 'P' -> FG_PURPLE;
                case 'Y' -> FG_WARNING;
                default -> FG_MAIN;
            };
            currentLineBold = (colorCode == 'R' || colorCode == 'P');
            currentPos += 4;
            return;
        }

        if (Character.isHighSurrogate(ch) && currentPos + 1 < currentLine.length()
                && Character.isLowSurrogate(currentLine.charAt(currentPos + 1))) {
            try {
                ensureEmojiStyle();
                int end = currentPos + 2;
                if (end < currentLine.length() && currentLine.charAt(end) == '\uFE0F') end++;
                doc.insertString(doc.getLength(), currentLine.substring(currentPos, end), cachedEmojiStyle);
                outputPane.setCaretPosition(doc.getLength());
                currentPos = end;
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
            return;
        }

        if (isBmpEmoji(ch)) {
            try {
                ensureEmojiStyle();
                int end = currentPos + 1;
                if (end < currentLine.length() && currentLine.charAt(end) == '\uFE0F') end++;
                doc.insertString(doc.getLength(), currentLine.substring(currentPos, end), cachedEmojiStyle);
                outputPane.setCaretPosition(doc.getLength());
                currentPos = end;
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
            return;
        }

        try {
            Style charStyle;
            if (currentLineColor != FG_MAIN || currentLineBold) {
                if (cachedColorStyle == null || cachedColorStyleColor != currentLineColor || cachedColorStyleBold != currentLineBold) {
                    cachedColorStyle = outputPane.addStyle("lineColor_" + currentLineColor.hashCode() + "_" + currentLineBold, null);
                    StyleConstants.setForeground(cachedColorStyle, currentLineColor);
                    StyleConstants.setFontFamily(cachedColorStyle, "Microsoft YaHei");
                    StyleConstants.setFontSize(cachedColorStyle, 16);
                    if (currentLineBold) StyleConstants.setBold(cachedColorStyle, true);
                    cachedColorStyleColor = currentLineColor;
                    cachedColorStyleBold = currentLineBold;
                }
                charStyle = cachedColorStyle;
            } else {
                ensureMainStyle();
                charStyle = cachedMainStyle;
            }
            doc.insertString(doc.getLength(), String.valueOf(ch), charStyle);
            outputPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
        currentPos++;
    }

    private void ensureMainStyle() {
        if (cachedMainStyle == null) {
            cachedMainStyle = outputPane.addStyle("mainChar", null);
            StyleConstants.setForeground(cachedMainStyle, FG_MAIN);
            StyleConstants.setFontFamily(cachedMainStyle, "Microsoft YaHei");
            StyleConstants.setFontSize(cachedMainStyle, 16);
        }
    }

    private void ensureEmojiStyle() {
        if (cachedEmojiStyle == null) {
            cachedEmojiStyle = outputPane.addStyle("emojiChar", null);
            StyleConstants.setForeground(cachedEmojiStyle, FG_MAIN);
            StyleConstants.setFontFamily(cachedEmojiStyle, "Segoe UI Emoji");
            StyleConstants.setFontSize(cachedEmojiStyle, 16);
        }
    }

    private boolean isBmpEmoji(char ch) {
        return ch == 0x2705 || ch == 0x274C || ch == 0x274E || ch == 0x2753
            || ch == 0x2757 || ch == 0x26A0 || ch == 0x261D || ch == 0x26F9
            || ch == 0x270A || ch == 0x270B || ch == 0x270C
            || (ch >= 0x2600 && ch <= 0x27BF)
            || (ch >= 0x2300 && ch <= 0x23FF)
            || ch == 0x2B50 || ch == 0x2B55
            || ch == 0x2934 || ch == 0x2935
            || ch == 0x25AA || ch == 0x25AB || ch == 0x25B6 || ch == 0x25C0
            || ch == 0x25FB || ch == 0x25FC || ch == 0x25FD || ch == 0x25FE
            || ch == 0x3030 || ch == 0x303D || ch == 0x3297 || ch == 0x3299;
    }

    private void flushCurrentLine() {
        if (currentLine == null) return;
        if (currentPos < currentLine.length()) {
            String remaining = currentLine.substring(currentPos);
            insertMixedText(remaining);
            outputPane.setCaretPosition(doc.getLength());
        }
        currentPos = currentLine.length();
        finishCurrentLine();
    }

    private void insertMixedText(String text) {
        try {
            int i = 0;
            StringBuilder normalBuf = new StringBuilder();
            while (i < text.length()) {
                char ch = text.charAt(i);
                if (Character.isHighSurrogate(ch) && i + 1 < text.length()
                        && Character.isLowSurrogate(text.charAt(i + 1))) {
                    if (normalBuf.length() > 0) {
                        ensureMainStyle();
                        doc.insertString(doc.getLength(), normalBuf.toString(), cachedMainStyle);
                        normalBuf.setLength(0);
                    }
                    ensureEmojiStyle();
                    int end = i + 2;
                    if (end < text.length() && text.charAt(end) == '\uFE0F') end++;
                    doc.insertString(doc.getLength(), text.substring(i, end), cachedEmojiStyle);
                    i = end;
                    continue;
                }
                if (isBmpEmoji(ch)) {
                    if (normalBuf.length() > 0) {
                        ensureMainStyle();
                        doc.insertString(doc.getLength(), normalBuf.toString(), cachedMainStyle);
                        normalBuf.setLength(0);
                    }
                    ensureEmojiStyle();
                    int end = i + 1;
                    if (end < text.length() && text.charAt(end) == '\uFE0F') end++;
                    doc.insertString(doc.getLength(), text.substring(i, end), cachedEmojiStyle);
                    i = end;
                    continue;
                }
                normalBuf.append(ch);
                i++;
            }
            if (normalBuf.length() > 0) {
                ensureMainStyle();
                doc.insertString(doc.getLength(), normalBuf.toString(), cachedMainStyle);
            }
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private void dimOldLines() {
        if (lineCount <= 5) return;
        try {
            if (cachedDimStyle == null) {
                cachedDimStyle = outputPane.addStyle("dimmed_cache", null);
                StyleConstants.setForeground(cachedDimStyle, DIMMED_TEXT);
            }
            Element root = doc.getDefaultRootElement();
            int paragraphCount = root.getElementCount();
            int startFrom = Math.max(0, paragraphCount - (lineCount - 5));
            for (int i = Math.max(lastDimmedLine, startFrom); i < paragraphCount - 1; i++) {
                Element elem = root.getElement(i);
                doc.setCharacterAttributes(elem.getStartOffset(), elem.getEndOffset() - elem.getStartOffset(), cachedDimStyle, false);
            }
            lastDimmedLine = paragraphCount - 1;
        } catch (Exception e) {
        }
    }

    private void trimDocument() {
        if (doc.getLength() <= MAX_DOC_CHARS) return;
        try {
            Element root = doc.getDefaultRootElement();
            int removeEnd = 0;
            int targetRemove = doc.getLength() - MAX_DOC_CHARS + 5000;
            for (int i = 0; i < root.getElementCount(); i++) {
                Element elem = root.getElement(i);
                removeEnd = elem.getEndOffset();
                if (removeEnd >= targetRemove) break;
            }
            if (removeEnd > 0 && removeEnd < doc.getLength()) {
                doc.remove(0, removeEnd);
                lineCount = Math.max(0, lineCount - 10);
                lastDimmedLine = 0;
                cachedDimStyle = null;
                cachedMainStyle = null;
                cachedEmojiStyle = null;
            }
        } catch (BadLocationException e) {
        }
    }

    private void redirectSystemOut() {
        OutputStream out = new OutputStream() {
            private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            @Override
            public synchronized void write(int b) {
                if (b == '\n' || b == '\r') {
                    flushBuffer();
                } else {
                    buffer.write(b);
                }
            }
            private synchronized void flushBuffer() {
                String line = buffer.toString(StandardCharsets.UTF_8);
                buffer.reset();
                lineQueue.offer(line);
            }
            @Override
            public synchronized void flush() {
                flushBuffer();
            }
        };
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(out, true, StandardCharsets.UTF_8));
    }

    private void enableSkipOnKeyPress() {
        outputPane.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_SPACE) {
                    skipAllLines.set(true);
                } else {
                    skipCurrentLine.set(true);
                }
            }
        });
        outputPane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    skipAllLines.set(true);
                } else {
                    skipCurrentLine.set(true);
                }
            }
        });
        outputPane.setFocusable(true);
        outputPane.requestFocusInWindow();
        addWindowFocusListener(new java.awt.event.WindowAdapter() {
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                outputPane.requestFocusInWindow();
            }
        });
    }

    public void setTypewriterDelay(int delay) {
        this.typewriterDelay = delay;
        if (typewriterTimer != null) {
            typewriterTimer.setDelay(delay);
        }
    }

    public void setGlitchChance(double chance) {
        this.glitchChance = chance;
    }

    public void setGlitchSkipChance(int chance) {
        this.glitchSkipChance = chance;
    }

    public void updateGlitchInterval(int interval) {
        this.baseGlitchInterval = interval;
        if (glitchTimer != null) {
            glitchTimer.stop();
            scheduleNextGlitch();
        }
    }

    public ParticlePanel getParticlePanel() {
        return particlePanel;
    }

    public ScanlineOverlay getScanlineOverlay() {
        return scanlineOverlay;
    }

    public HUDPanel getHudPanel() {
        return hudPanel;
    }

    private Style cachedImpactStyle;

    public void showImpactText(String text) {
        try {
            if (cachedImpactStyle == null) {
                cachedImpactStyle = outputPane.addStyle("impact", null);
                StyleConstants.setForeground(cachedImpactStyle, FG_MAGENTA);
                StyleConstants.setBold(cachedImpactStyle, true);
                StyleConstants.setFontFamily(cachedImpactStyle, "Microsoft YaHei");
                StyleConstants.setFontSize(cachedImpactStyle, 22);
            }
            doc.insertString(doc.getLength(), "\n" + text + "\n", cachedImpactStyle);
            outputPane.setCaretPosition(doc.getLength());
        } catch (BadLocationException e) {
            e.printStackTrace();
        }
    }

    private static class RoundedBorder extends javax.swing.border.AbstractBorder {
        private final int radius;
        private final Color color;
        RoundedBorder(int radius, Color color) {
            this.radius = radius;
            this.color = color;
        }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.setStroke(new BasicStroke(2));
            g2.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            g2.dispose();
        }
        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(2, 2, 2, 2);
        }
    }

    static class HUDPanel extends JPanel {

        private static final Color BG_HUD = new Color(0x0A, 0x0A, 0x14);
        private static final Color BORDER_HUD = new Color(0x1A, 0x3A, 0x3A);
        private static final Color TEXT_CYAN = new Color(0x00, 0xCC, 0xCC);
        private static final Color TEXT_MAGENTA = new Color(0xFF, 0x00, 0x7F);
        private static final Color TEXT_AMBER = new Color(0xFF, 0xB3, 0x00);
        private static final Color TEXT_GREEN = new Color(0x00, 0xCC, 0x66);
        private static final Color TEXT_WHITE = new Color(0xCC, 0xCC, 0xCC);
        private static final Color BAR_BG = new Color(0x1A, 0x1A, 0x2A);
        private static final Color BAR_HEALTH = new Color(0x00, 0xCC, 0x66);
        private static final Color BAR_HEALTH_LOW = new Color(0xFF, 0x33, 0x00);
        private static final Color BAR_WANTED = new Color(0xFF, 0x00, 0x7F);
        private static final Font FONT_SMALL = new Font("Microsoft YaHei", Font.PLAIN, 12);
        private static final Font FONT_NORMAL = new Font("Microsoft YaHei", Font.BOLD, 13);
        private static final Font FONT_TITLE = new Font("Microsoft YaHei", Font.BOLD, 14);

        private Player player;
        private Timer refreshTimer;

        private int healthPercent = 100;
        private int wantedLevel = 1;
        private int money = 0;
        private int reputation = 0;
        private int humanity = 0;
        private int coldness = 0;
        private int scavengerIntelCount = 0;
        private boolean ghostHelp = false;
        private int pendingScrutiny = 0;

        public HUDPanel() {
            setBackground(BG_HUD);
            setBorder(BorderFactory.createLineBorder(BORDER_HUD, 1));
            setLayout(new BorderLayout());
        }

        public void setPlayer(Player player) {
            this.player = player;
            startRefresh();
        }

        private void startRefresh() {
            if (refreshTimer != null) {
                refreshTimer.stop();
            }
            refreshTimer = new Timer(2000, e -> refresh());
            refreshTimer.start();
        }

        private void refresh() {
            if (player == null) return;
            healthPercent = (int) ((double) player.getHealth() / player.getMaxHealth() * 100);
            wantedLevel = player.getWantedLevel();
            money = player.getMoney();
            reputation = player.getReputation();
            this.humanity = player.getHumanity();
            this.coldness = player.getColdness();
            this.scavengerIntelCount = player.getScavengerIntelCount();
            this.ghostHelp = player.hasGhostAccepted();
            this.pendingScrutiny = player.getPendingScrutinyCount();
            repaint();
        }

        public int getWantedLevel() {
            return wantedLevel;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int x = 15;
            int y = 20;
            int w = getWidth() - 30;

            g2.setFont(FONT_TITLE);
            g2.setColor(TEXT_CYAN);
            g2.drawString("状态面板", x, y);
            y += 25;

            g2.setColor(new Color(0x2A, 0x2A, 0x3A));
            g2.drawLine(x, y, x + w, y);
            y += 15;

            g2.setFont(FONT_NORMAL);
            g2.setColor(TEXT_CYAN);
            g2.drawString("生命体征", x, y);
            y += 18;
            drawBar(g2, x, y, w, healthPercent, healthPercent < 30 ? BAR_HEALTH_LOW : BAR_HEALTH);
            y += 20;
            g2.setFont(FONT_SMALL);
            g2.setColor(TEXT_WHITE);
            g2.drawString(player != null ? player.getHealth() + "/" + player.getMaxHealth() + " (" + healthPercent + "%)" : "--", x, y);
            y += 25;

            g2.setFont(FONT_NORMAL);
            g2.setColor(TEXT_MAGENTA);
            g2.drawString("城警关注度", x, y);
            y += 18;
            drawWantedStars(g2, x, y, w, wantedLevel);
            y += 25;

            g2.setFont(FONT_NORMAL);
            g2.setColor(TEXT_GREEN);
            g2.drawString("金钱", x, y);
            y += 18;
            g2.setFont(FONT_SMALL);
            g2.setColor(TEXT_WHITE);
            g2.drawString(String.valueOf(money) + "€ / 目标: " + GameConfig.WIN_TARGET + "€", x, y);
            y += 25;

            g2.setFont(FONT_NORMAL);
            g2.setColor(TEXT_AMBER);
            g2.drawString("声望", x, y);
            y += 18;
            g2.setFont(FONT_SMALL);
            g2.setColor(TEXT_WHITE);
            g2.drawString(String.valueOf(reputation), x, y);
            y += 25;

            // 属性区域
            g2.setColor(new Color(0x2A, 0x2A, 0x3A));
            g2.drawLine(x, y, x + w, y);
            y += 12;

            g2.setFont(FONT_NORMAL);
            g2.setColor(TEXT_CYAN);
            g2.drawString("属性", x, y);
            y += 18;

            g2.setFont(FONT_SMALL);
            g2.setColor(TEXT_WHITE);
            int speed = player != null ? player.getSpeed() : 0;
            int avoidPolice = player != null ? player.getAvoidPolice() : 0;
            int avoidGang = player != null ? player.getAvoidGang() : 0;
            int level = player != null ? player.getLevel() : 1;
            int totalTasks = player != null ? player.getTotalTasks() : 0;
            g2.drawString("速度: " + speed + " | 避警: " + avoidPolice + " | 避帮: " + avoidGang, x, y);
            y += 16;
            g2.drawString("等级: " + level + " | 任务数: " + totalTasks, x, y);
            y += 25;

            boolean hasSpecial = scavengerIntelCount > 0 || ghostHelp || pendingScrutiny > 0;
            if (hasSpecial) {
                g2.setColor(new Color(0x2A, 0x2A, 0x3A));
                g2.drawLine(x, y, x + w, y);
                y += 12;

                g2.setFont(FONT_NORMAL);
                g2.setColor(TEXT_AMBER);
                g2.drawString("特殊状态", x, y);
                y += 18;

                g2.setFont(FONT_SMALL);
                if (scavengerIntelCount > 0) {
                    g2.setColor(TEXT_GREEN);
                    g2.drawString("拾荒者情报 \u00d7 " + scavengerIntelCount, x, y);
                    y += 16;
                }
                if (ghostHelp) {
                    g2.setColor(TEXT_MAGENTA);
                    g2.drawString("幽灵协议：活跃", x, y);
                    y += 16;
                }
                if (pendingScrutiny > 0) {
                    g2.setColor(new Color(0xFF, 0x33, 0x00));
                    g2.drawString("城警审查：" + pendingScrutiny + "次", x, y);
                    y += 16;
                }
                y += 10;
            }

            g2.setFont(FONT_NORMAL);
            String alignmentLabel;
            Color alignmentColor;
            if (humanity > coldness + 3) {
                alignmentLabel = "偏向人性";
                alignmentColor = new Color(0x00, 0xDD, 0xDD);
            } else if (coldness > humanity + 3) {
                alignmentLabel = "偏向冷酷";
                alignmentColor = new Color(0xFF, 0x33, 0x99);
            } else {
                alignmentLabel = "混沌中立";
                alignmentColor = TEXT_AMBER;
            }
            g2.setColor(alignmentColor);
            g2.drawString("核心协议", x, y);
            y += 18;
            g2.setFont(FONT_SMALL);
            g2.setColor(TEXT_WHITE);
            g2.drawString(alignmentLabel, x, y);
            y += 30;

            g2.setColor(new Color(0x2A, 0x2A, 0x3A));
            g2.drawLine(x, y, x + w, y);
            y += 15;

            g2.setFont(FONT_SMALL);
            g2.setColor(new Color(0x66, 0x66, 0x77));
            g2.drawString("夜之城快递员 OS", x, y);
            y += 14;
            g2.drawString("v1.3.7 - " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), x, y);

            g2.dispose();
        }

        private void drawBar(Graphics2D g2, int x, int y, int width, int percent, Color barColor) {
            int barH = 10;
            g2.setColor(BAR_BG);
            g2.fillRect(x, y, width, barH);
            int filled = (int) (width * Math.min(100, Math.max(0, percent)) / 100);
            g2.setColor(barColor);
            g2.fillRect(x, y, filled, barH);
            g2.setColor(BORDER_HUD);
            g2.drawRect(x, y, width, barH);
        }

        private void drawWantedStars(Graphics2D g2, int x, int y, int width, int level) {
            int starSize = 14;
            int gap = 4;
            int totalWidth = 5 * starSize + 4 * gap;
            int startX = Math.max(0, x + (width - totalWidth) / 2);
            for (int i = 0; i < 5; i++) {
                if (i < level) {
                    g2.setColor(BAR_WANTED);
                    fillStar(g2, startX + i * (starSize + gap), y, starSize);
                } else {
                    g2.setColor(new Color(0x33, 0x33, 0x44));
                    fillStar(g2, startX + i * (starSize + gap), y, starSize);
                }
            }
        }

        private void fillStar(Graphics2D g2, int x, int y, int size) {
            int cx = x + size / 2;
            int cy = y + size / 2;
            int r = size / 2;
            int[] xs = new int[5];
            int[] ys = new int[5];
            for (int i = 0; i < 5; i++) {
                double angle = Math.toRadians(-90 + i * 72);
                xs[i] = (int) (cx + r * Math.cos(angle));
                ys[i] = (int) (cy + r * Math.sin(angle));
            }
            g2.fillPolygon(xs, ys, 5);
        }
    }

    static class ParticlePanel extends JPanel {

        private static final Color CYAN_PARTICLE = new Color(0x00, 0xCC, 0xCC);
        private static final Color MAGENTA_PARTICLE = new Color(0xFF, 0x00, 0x7F);
        private static final Color WHITE_PARTICLE = Color.WHITE;

        private final List<Particle> particles = new ArrayList<>();
        private String state = "normal";
        private Timer animTimer;

        private static class Particle {
            float x, y;
            Color color;
            float speed;
            float alpha;

            Particle(int width, int height) {
                x = (float) Math.random() * width;
                y = (float) Math.random() * height;
                double roll = Math.random();
                if (roll < 0.60) {
                    color = CYAN_PARTICLE;
                } else if (roll < 0.85) {
                    color = MAGENTA_PARTICLE;
                } else {
                    color = WHITE_PARTICLE;
                }
                speed = 0.3f + (float) (Math.random() * 0.5);
                alpha = 0.3f + (float) (Math.random() * 0.5);
            }
        }

        public ParticlePanel() {
            setOpaque(false);
            setLayout(null);
        }

        public void initParticles(int width, int height) {
            particles.clear();
            int count = 60 + (int) (Math.random() * 21);
            for (int i = 0; i < count; i++) {
                particles.add(new Particle(width, height));
            }
            startAnimation();
        }

        private void startAnimation() {
            if (animTimer != null) {
                animTimer.stop();
            }
            animTimer = new Timer(33, e -> {
                animateParticles();
                repaint();
            });
            animTimer.start();
        }

        private void animateParticles() {
            int h = getHeight();
            float speedMult = 1.0f;
            if (state.equals("tense")) {
                speedMult = 1.5f;
            } else if (state.equals("critical")) {
                speedMult = 2.0f;
            }
            for (Particle p : particles) {
                p.y -= p.speed * speedMult;
                if (p.y < -5) {
                    p.y = h + 5;
                    p.x = (float) (Math.random() * getWidth());
                }
            }
        }

        public void setState(String state) {
            this.state = state;
        }

        public String getState() {
            return state;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (Particle p : particles) {
                g2.setColor(p.color);
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, p.alpha));
                g2.fillOval((int) p.x, (int) p.y, 2, 2);
            }
            g2.dispose();
        }
    }

    static class ScanlineOverlay extends JPanel {

        private static final Color MAGENTA_EDGE = new Color(0xFF, 0x00, 0x7F, 30);
        private static final Color CYAN_EDGE = new Color(0x00, 0xFF, 0xFF, 30);
        private static final Color SCANLINE = new Color(0, 0, 0, 25);
        private static final Color RED_FLASH = new Color(0xFF, 0x00, 0x00, 60);

        private int density = 4;
        private boolean edgeEnabled = true;
        private boolean redFlash = false;
        private Timer flashTimer;

        public ScanlineOverlay() {
            setOpaque(false);
            setLayout(null);
        }

        public void setDensity(int linesPerPixel) {
            this.density = linesPerPixel;
            repaint();
        }

        public void setEdgeEnabled(boolean enabled) {
            this.edgeEnabled = enabled;
            repaint();
        }

        public void triggerRedFlash() {
            redFlash = true;
            repaint();
            if (flashTimer != null) {
                flashTimer.stop();
            }
            flashTimer = new Timer(200, e -> {
                redFlash = false;
                repaint();
                flashTimer.stop();
            });
            flashTimer.setRepeats(false);
            flashTimer.start();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();

            int h = getHeight();
            int w = getWidth();

            for (int y = 0; y < h; y += density) {
                g2.setColor(SCANLINE);
                g2.fillRect(0, y, w, 1);
            }

            if (edgeEnabled) {
                g2.setColor(MAGENTA_EDGE);
                g2.fillRect(0, 0, 3, h);
                g2.setColor(CYAN_EDGE);
                g2.fillRect(w - 3, 0, 3, h);
            }

            if (redFlash) {
                g2.setColor(RED_FLASH);
                g2.fillRect(0, 0, 3, h);
                g2.fillRect(w - 3, 0, 3, h);
            }

            g2.dispose();
        }
    }

    class TerminalStateManager {

        private static final Random RANDOM = new Random();

        private final GameWindow gameWindow;
        private final ParticlePanel particlePanel;
        private final ScanlineOverlay scanlineOverlay;
        private Player player;
        private Timer checkTimer;

        private int typewriterSpeed = 40;
        private boolean glitchTextEnabled = false;
        private double glitchChance = 0;
        private int glitchSkipChance = 0;

        private int baseGlitchInterval = 45000;
        private int currentGlitchInterval = 45000;

        public TerminalStateManager(GameWindow gameWindow, ParticlePanel particlePanel, ScanlineOverlay scanlineOverlay) {
            this.gameWindow = gameWindow;
            this.particlePanel = particlePanel;
            this.scanlineOverlay = scanlineOverlay;
        }

        public void setPlayer(Player player) {
            this.player = player;
            startMonitoring();
        }

        private void startMonitoring() {
            if (checkTimer != null) {
                checkTimer.stop();
            }
            checkTimer = new Timer(5000, e -> checkAndUpdate());
            checkTimer.start();
            checkAndUpdate();
        }

        public void triggerUpdate() {
            SwingUtilities.invokeLater(this::checkAndUpdate);
        }

        private void checkAndUpdate() {
            if (player == null) return;
            updateHealthEffect();
            String particleState = determineParticleState();
            updateWantedEffect();
            updateGlitchTimer();
            particlePanel.setState(particleState);
            gameWindow.setTypewriterDelay(typewriterSpeed);
            gameWindow.setGlitchChance(glitchChance);
            gameWindow.setGlitchSkipChance(glitchSkipChance);
        }

        private String determineParticleState() {
            String healthState;
            int healthPercent = (int) ((double) player.getHealth() / player.getMaxHealth() * 100);
            if (healthPercent < 30) {
                healthState = "critical";
            } else if (healthPercent < 60) {
                healthState = "tense";
            } else {
                healthState = "normal";
            }

            String humanityState;
            int humanity = player.getHumanity();
            int coldness = player.getColdness();
            if (coldness > humanity + 3) {
                humanityState = "tense";
            } else {
                humanityState = "normal";
            }

            if (healthState.equals("critical") || humanityState.equals("critical")) {
                return "critical";
            }
            if (healthState.equals("tense") || humanityState.equals("tense")) {
                return "tense";
            }
            return "normal";
        }

        private void updateHealthEffect() {
            int healthPercent = (int) ((double) player.getHealth() / player.getMaxHealth() * 100);
            if (healthPercent < 30) {
                typewriterSpeed = 80;
                glitchSkipChance = 2;
            } else if (healthPercent < 60) {
                typewriterSpeed = 52;
                glitchSkipChance = 0;
            } else {
                typewriterSpeed = 40;
                glitchSkipChance = 0;
            }
        }

        private void updateWantedEffect() {
            int wanted = player.getWantedLevel();
            if (wanted >= 5) {
                scanlineOverlay.triggerRedFlash();
                scanlineOverlay.setDensity(2);
                currentGlitchInterval = 15000;
                glitchChance = 5.0;
            } else if (wanted == 4) {
                scanlineOverlay.setDensity(3);
                currentGlitchInterval = 20000;
                glitchChance = 3.0;
            } else if (wanted == 3) {
                scanlineOverlay.setDensity(2);
                currentGlitchInterval = 30000;
                glitchChance = 0;
            } else {
                scanlineOverlay.setDensity(4);
                currentGlitchInterval = baseGlitchInterval;
                glitchChance = 0;
            }
        }

        private void updateGlitchTimer() {
            gameWindow.updateGlitchInterval(currentGlitchInterval);
        }

        public void dispose() {
            if (checkTimer != null) {
                checkTimer.stop();
            }
        }
    }

    private final Object typewriterLock = new Object();
    private volatile boolean typewriterIdle = true;

    /**
     * 显示选择对话框，带选项按钮。
     * 在EDT外调用的线程会阻塞等待用户选择，通过 BlockingQueue 实现同步。
     *
     * @param title   对话框标题
     * @param prompt  提示文本
     * @param options 按钮文本数组
     * @return 用户选择的按钮索引 (0-based)，关闭对话框返回 -1
     */
    public int showChoiceDialog(String title, String prompt, String[] options) {
        choiceQueue.clear();
        waitForTypewriter();
        if (prompt != null && !prompt.isEmpty()) {
            System.out.println("[" + title + "] " + prompt);
        }
        waitForTypewriter();
        SwingUtilities.invokeLater(() -> {
            buttonPanel.removeAll();
            buttonPanel.setLayout(new GridLayout(1, options.length, 10, 10));
            for (int i = 0; i < options.length; i++) {
                JButton btn = createChoiceButton(options[i], i);
                buttonPanel.add(btn);
            }
            buttonPanel.revalidate();
            buttonPanel.repaint();
            // 二次强制刷新，防止按钮延迟显示
            SwingUtilities.invokeLater(() -> buttonPanel.repaint());
        });
        try {
            return choiceQueue.take();
        } catch (InterruptedException e) {
            return -1;
        }
    }

    private void waitForTypewriter() {
        long startTime = System.currentTimeMillis();
        synchronized (typewriterLock) {
            while ((!typewriterIdle || !lineQueue.isEmpty()) && System.currentTimeMillis() - startTime < 30000) {
                try {
                    typewriterLock.wait(10);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private void setTypewriterIdle(boolean idle) {
        synchronized (typewriterLock) {
            typewriterIdle = idle;
            typewriterLock.notifyAll();
        }
    }

    private JButton createChoiceButton(String text, int index) {
        JButton btn = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isRollover()) {
                    g2.setColor(BUTTON_HOVER_BG);
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.setColor(BUTTON_HOVER_GLOW);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                }
                g2.setColor(BUTTON_BORDER);
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(BUTTON_FONT);
        btn.setBackground(BUTTON_BG);
        btn.setForeground(FG_MAGENTA);
        btn.setOpaque(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(4, 12, 4, 12));
        btn.setPreferredSize(new Dimension(140, 40));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addActionListener(e -> {
            choiceQueue.offer(index);
            SwingUtilities.invokeLater(() -> {
                buttonPanel.removeAll();
                buttonPanel.revalidate();
                buttonPanel.repaint();
            });
        });
        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { btn.repaint(); }
            @Override
            public void mouseExited(MouseEvent e) { btn.repaint(); }
        });
        return btn;
    }

    private void restoreMenuButtons() {
        SwingUtilities.invokeLater(() -> {
            buttonPanel.removeAll();
            buttonPanel.setLayout(new GridLayout(2, 4, 10, 10));
            buttonPanel.add(createMenuButton("接单送货", 1));
            buttonPanel.add(createMenuButton("升级装备", 2));
            buttonPanel.add(createMenuButton("查看目标", 3));
            buttonPanel.add(createMenuButton("治疗", 4));
            buttonPanel.add(createMenuButton("统计", 5));
            buttonPanel.add(createMenuButton("最后一单", 6));
            buttonPanel.add(createMenuButton("存档", 7));
            buttonPanel.add(createMenuButton("退出游戏", 8));
            buttonPanel.revalidate();
            buttonPanel.repaint();
        });
    }

    public void flushTypewriter() {
        skipAllLines.set(true);
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            if (currentLine == null && lineQueue.isEmpty()) {
                break;
            }
            try { Thread.sleep(30); } catch (InterruptedException e) { break; }
        }
        skipAllLines.set(false);
    }

    public String showInputDialog(String title, String message) {
        choiceQueue.clear();
        waitForTypewriter();
        if (message != null && !message.isEmpty()) {
            System.out.println(message);
        }
        waitForTypewriter();
        final String[] result = {null};
        final AtomicBoolean confirmed = new AtomicBoolean(false);
        final JTextField inputField = new JTextField();
        inputField.setFont(TERMINAL_FONT);
        inputField.setBackground(BG_TEXT_AREA);
        inputField.setForeground(FG_CYAN);
        inputField.setCaretColor(FG_CYAN);
        inputField.setBorder(BorderFactory.createLineBorder(BUTTON_BORDER, 1));
        SwingUtilities.invokeLater(() -> {
            buttonPanel.removeAll();
            buttonPanel.setLayout(new BorderLayout(10, 10));
            JPanel inner = new JPanel(new BorderLayout(5, 5));
            inner.setBackground(BG_DARK);
            inner.add(inputField, BorderLayout.CENTER);
            JButton confirmBtn = createChoiceButton("确认", 0);
            confirmBtn.addActionListener(e2 -> {
                if (confirmed.compareAndSet(false, true)) {
                    result[0] = inputField.getText();
                    choiceQueue.offer(0);
                    SwingUtilities.invokeLater(() -> {
                        buttonPanel.removeAll();
                        buttonPanel.revalidate();
                        buttonPanel.repaint();
                    });
                }
            });
            inputField.addActionListener(e2 -> {
                if (confirmed.compareAndSet(false, true)) {
                    result[0] = inputField.getText();
                    choiceQueue.offer(0);
                    SwingUtilities.invokeLater(() -> {
                        buttonPanel.removeAll();
                        buttonPanel.revalidate();
                        buttonPanel.repaint();
                    });
                }
            });
            inner.add(confirmBtn, BorderLayout.EAST);
            inner.setBorder(new EmptyBorder(5, 5, 5, 5));
            buttonPanel.add(inner, BorderLayout.CENTER);
            buttonPanel.revalidate();
            buttonPanel.repaint();
            inputField.requestFocusInWindow();
        });
        try {
            choiceQueue.take();
        } catch (InterruptedException e) {
            return "";
        }
        return result[0] == null ? "" : result[0];
    }

    public void showTutorial() {
        System.out.println("══════════════════════════════════");
        System.out.println("  📋 新手指引");
        System.out.println("══════════════════════════════════");
        System.out.println("  速度     → 减少赶路步数，降低逃跑难度");
        System.out.println("  避警     → 更高概率自动避开警方");
        System.out.println("  避帮     → 更高概率自动避开帮派");
        System.out.println("  声望     → 影响结局，帮助他人可提升");
        System.out.println("  通缉     → 满5星被捕结局，贿赂可降低");
        System.out.println("  治疗     → 花费金钱恢复30血量");
        System.out.println("══════════════════════════════════");
        System.out.println("  提示：按任意键跳过当前段，按空格跳过全部");
        System.out.println("══════════════════════════════════");
    }

    public void showStatus(Player p) {
        // 状态信息已移至右侧面板显示，不再在文本区打印
    }

    /**
     * 显示玩家详细统计信息
     * @param p 玩家对象
     */
    public void showStats(Player p) {
        System.out.println("══════════════════════════════════");
        System.out.println("  📊 详细统计");
        System.out.println("  当前等级：" + p.getLevel());
        System.out.println("  完成任务数：" + p.getTotalTasks());
        System.out.println("  声望：" + p.getReputation());
        System.out.println("  人性：" + p.getHumanity() + " 次");
        System.out.println("  冷酷：" + p.getColdness() + " 次");
        System.out.println("  救人次数：" + p.getHelpCount());
        System.out.println("  拒检次数：" + p.getRefuseCount());
        System.out.println("  贿赂警察：" + p.getBribePoliceCount() + " 次");
        System.out.println("  贿赂帮派：" + p.getBribeGangCount() + " 次");
        System.out.println("  黑市件数：" + p.getBlackMarketCount());
        System.out.println("  拾荒者情报：" + (p.hasScavengerIntel() ? "持有" : "无"));
        System.out.println("  回响石子：" + p.totalEchoCount() + " 颗");
        System.out.println("══════════════════════════════════\n");
    }

    public void showTitle() {
        System.out.println("======================================");
        System.out.println("    夜之城快递员 赛博朋克快递员");
        System.out.println("======================================");
        System.out.println("目标：攒够 8000 欧元逃离夜之城");
        System.out.println("接单、送货、活着、走人。\n");
    }

    public boolean showWelcomeDialog() {
        Font titleFont = new Font("Microsoft YaHei", Font.BOLD, 32);
        Font textFont = new Font("Microsoft YaHei", Font.PLAIN, 15);
        Font btnFont = new Font("Microsoft YaHei", Font.BOLD, 16);
        Color bg = new Color(0x0A, 0x0A, 0x14);
        Color borderColor = new Color(0x00, 0xCC, 0xCC);
        Color textColor = new Color(0xCC, 0xDD, 0xEE);
        Color highlight = new Color(0xFF, 0xAA, 0x00);

        JDialog dialog = new JDialog((Frame) null, true);
        dialog.setUndecorated(true);
        dialog.setSize(520, 400);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(4, 4, getWidth() - 9, getHeight() - 9, 10, 10);

                g2.setColor(new Color(0, 204, 204, 15));
                for (int i = 0; i < 20; i++) {
                    int px = (int) (Math.random() * getWidth());
                    int py = (int) (Math.random() * getHeight());
                    g2.fillOval(px, py, 2, 2);
                }

                g2.setFont(titleFont);
                g2.setColor(highlight);
                FontMetrics fm = g2.getFontMetrics();
                String title = "夜之城快递员";
                g2.drawString(title, (getWidth() - fm.stringWidth(title)) / 2, 60);

                g2.setColor(new Color(0x33, 0x66, 0x88));
                g2.setStroke(new BasicStroke(1));
                int lineY = 80;
                g2.drawLine(80, lineY, getWidth() - 80, lineY);

                g2.setFont(textFont);
                g2.setColor(textColor);
                fm = g2.getFontMetrics();
                String[] lines = {
                    "公元2077年，夜之城。",
                    "企业掌控一切，帮派割据街区，城警腐败横行。",
                    "你是一个底层快递员，",
                    "每天穿梭于霓虹与暗巷之间。",
                    "",
                    "你的目标只有一个：",
                    "攒够8000欧元，逃离这座吞噬灵魂的城市。",
                    "",
                    "接单、送货、活着、走人。"
                };
                int ly = 120;
                for (String line : lines) {
                    if (line.isEmpty()) { ly += 10; continue; }
                    g2.drawString(line, (getWidth() - fm.stringWidth(line)) / 2, ly);
                    ly += 24;
                }
            }
        };
        panel.setLayout(null);

        JButton enterBtn = new JButton("进入夜之城");
        enterBtn.setFont(btnFont);
        enterBtn.setForeground(bg);
        enterBtn.setBackground(borderColor);
        enterBtn.setBorderPainted(false);
        enterBtn.setFocusPainted(false);
        enterBtn.setBounds(160, 340, 200, 40);
        enterBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        enterBtn.addActionListener(e -> dialog.dispose());

        panel.add(enterBtn);
        dialog.setContentPane(panel);
        dialog.setVisible(true);
        return true;
    }

    public boolean showTutorialDialog() {
        Font titleFont = new Font("Microsoft YaHei", Font.BOLD, 22);
        Font sectionFont = new Font("Microsoft YaHei", Font.BOLD, 13);
        Font textFont = new Font("Microsoft YaHei", Font.PLAIN, 12);
        Font btnFont = new Font("Microsoft YaHei", Font.BOLD, 15);
        Color bg = new Color(0x0A, 0x0A, 0x14);
        Color borderColor = new Color(0x00, 0xCC, 0xCC);
        Color textColor = new Color(0xCC, 0xDD, 0xEE);
        Color highlight = new Color(0xFF, 0xAA, 0x00);
        Color accentGreen = new Color(0x00, 0xDD, 0x66);
        Color accentCyan = new Color(0x00, 0xCC, 0xCC);

        JDialog dialog = new JDialog((Frame) null, true);
        dialog.setUndecorated(true);
        dialog.setSize(620, 860);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(4, 4, getWidth() - 9, getHeight() - 9, 10, 10);

                g2.setFont(titleFont);
                g2.setColor(highlight);
                g2.drawString("新手指引", 30, 40);

                g2.setColor(new Color(0x33, 0x66, 0x88));
                g2.setStroke(new BasicStroke(1));
                g2.drawLine(30, 52, getWidth() - 30, 52);

                int sy = 68;
                int leftMargin = 35;
                int textMargin = 45;
                int contentWidth = getWidth() - 70;

                g2.setFont(sectionFont);
                g2.setColor(accentCyan);
                g2.drawString("── 核心属性 ──", leftMargin, sy + 14);
                sy += 24;

                String[][] coreSections = {
                    {"💨 速度（可升级，150€/级）",
                        "减少每次送货的赶路步数，提高逃跑成功率。",
                        "每升一级缩短一步路程，让你更快完成送货。"},
                    {"🛡️ 避警（可升级，150€/级）",
                        "遭遇城警时有概率自动避开，避免战斗或贿赂。",
                        "等级越高，自动避开概率越大。"},
                    {"⚔️ 避帮（可升级，150€/级）",
                        "遭遇帮派时有概率自动避开，和避警类似。",
                        "升级后能安全绕过更多帮派冲突。"},
                    {"⭐ 声望（自然获取）",
                        "救人、帮助他人可提升声望。高声望影响结局走向，",
                        "路人可能认出你并伸出援手。低声望则走向黑暗结局。"},
                    {"🔴 通缉（动态变化，满5星=被捕结局）",
                        "违法、逃跑、拒绝检查都会涨通缉星数。",
                        "贿赂警察或配合检查可降低通缉。满5星直接被捕！"},
                    {"❤️ 治疗（花费金钱，恢复30HP）",
                        "费用随等级递增。血量归零将导致「过劳暴毙」。",
                        "送货成功会恢复15点血量，注意保持健康。"},
                };

                for (String[] sec : coreSections) {
                    g2.setFont(sectionFont);
                    g2.setColor(accentGreen);
                    g2.drawString(sec[0], leftMargin, sy + 14);
                    sy += 18;
                    g2.setFont(textFont);
                    g2.setColor(textColor);
                    g2.drawString(sec[1], textMargin, sy + 13);
                    sy += 15;
                    g2.drawString(sec[2], textMargin, sy + 13);
                    sy += 20;
                }

                sy += 4;
                g2.setFont(sectionFont);
                g2.setColor(accentCyan);
                g2.drawString("── 赶路与事件 ──", leftMargin, sy + 14);
                sy += 24;

                String[][] eventSections = {
                    {"🚶 正常走 vs 🏃 抄近道",
                        "正常走安全但慢；抄近道省1~2步但可能扣血。",
                        "两种走法都可能触发随机事件。"},
                    {"📦 任务变体",
                        "送货完成后可能触发特殊变体：陷阱件、活体件、名人件、",
                        "遗物件、救命件等。每种变体都有独特选择和后果。"},
                    {"🔮 回响石子",
                        "你的每个重要选择都会产生「回响石子」，",
                        "影响最终九道门的走向和结局类型。"},
                    {"🚪 九道门（最终挑战）",
                        "攒够8000€后触发。面对九扇门，每扇门后是一段记忆。",
                        "你可以面对、绕过或击碎——你的选择决定结局。"},
                };

                for (String[] sec : eventSections) {
                    g2.setFont(sectionFont);
                    g2.setColor(accentGreen);
                    g2.drawString(sec[0], leftMargin, sy + 14);
                    sy += 18;
                    g2.setFont(textFont);
                    g2.setColor(textColor);
                    g2.drawString(sec[1], textMargin, sy + 13);
                    sy += 15;
                    g2.drawString(sec[2], textMargin, sy + 13);
                    sy += 20;
                }

                sy += 4;
                g2.setFont(sectionFont);
                g2.setColor(accentCyan);
                g2.drawString("── 特殊状态 ──", leftMargin, sy + 14);
                sy += 24;

                String[][] specialSections = {
                    {"🔍 拾荒者情报",
                        "从拾荒者事件中获得，可消耗以降低任务风险。"},
                    {"👻 幽灵帮助",
                        "从神秘通讯中获得，可消耗以免除任务失败惩罚。"},
                };

                for (String[] sec : specialSections) {
                    g2.setFont(sectionFont);
                    g2.setColor(accentGreen);
                    g2.drawString(sec[0], leftMargin, sy + 14);
                    sy += 18;
                    g2.setFont(textFont);
                    g2.setColor(textColor);
                    g2.drawString(sec[1], textMargin, sy + 13);
                    sy += 20;
                }

                g2.setFont(textFont);
                g2.setColor(highlight);
                String tip = "每次选择都会影响结局。你的每一个决定，都会被夜之城记住。";
                g2.drawString(tip, leftMargin, sy + 8);
            }
        };
        panel.setLayout(null);

        JButton readyBtn = new JButton("我准备好了");
        readyBtn.setFont(btnFont);
        readyBtn.setForeground(bg);
        readyBtn.setBackground(borderColor);
        readyBtn.setBorderPainted(false);
        readyBtn.setFocusPainted(false);
        readyBtn.setBounds(210, 800, 200, 38);
        readyBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        readyBtn.addActionListener(e -> dialog.dispose());

        panel.add(readyBtn);
        dialog.setContentPane(panel);
        dialog.setVisible(true);
        return true;
    }

    public boolean showEndingDialog(String endingTitle, String endingSubtitle, Color titleColor) {
        Font endingFont = new Font("Microsoft YaHei", Font.BOLD, 28);
        Font subtitleFont = new Font("Microsoft YaHei", Font.PLAIN, 14);
        Font btnFont = new Font("Microsoft YaHei", Font.BOLD, 15);
        Color bg = new Color(0x0A, 0x0A, 0x14);
        Color borderColor = new Color(0x00, 0xCC, 0xCC);
        Color textColor = new Color(0xCC, 0xDD, 0xEE);

        JDialog dialog = new JDialog((Frame) null, true);
        dialog.setUndecorated(true);
        dialog.setSize(480, 360);
        dialog.setLocationRelativeTo(this);

        JPanel panel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(borderColor);
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(4, 4, getWidth() - 9, getHeight() - 9, 10, 10);

                g2.setFont(endingFont);
                g2.setColor(titleColor);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(endingTitle, (getWidth() - fm.stringWidth(endingTitle)) / 2, 80);

                g2.setColor(new Color(0x33, 0x66, 0x88));
                g2.setStroke(new BasicStroke(1));
                g2.drawLine(80, 100, getWidth() - 80, 100);

                g2.setFont(subtitleFont);
                g2.setColor(textColor);
                fm = g2.getFontMetrics();
                g2.drawString(endingSubtitle, (getWidth() - fm.stringWidth(endingSubtitle)) / 2, 130);

                g2.setFont(btnFont);
                g2.setColor(textColor);
                String question = "夜之城的故事告一段落。";
                g2.drawString(question, (getWidth() - fm.stringWidth(question)) / 2, 180);

                String question2 = "你接下来要怎么做？";
                fm = g2.getFontMetrics();
                g2.drawString(question2, (getWidth() - fm.stringWidth(question2)) / 2, 200);
            }
        };
        panel.setLayout(null);

        JButton restartBtn = new JButton("再次出发");
        restartBtn.setFont(btnFont);
        restartBtn.setForeground(bg);
        restartBtn.setBackground(new Color(0x00, 0xCC, 0x88));
        restartBtn.setBorderPainted(false);
        restartBtn.setFocusPainted(false);
        restartBtn.setBounds(60, 260, 160, 40);
        restartBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        restartBtn.addActionListener(e -> {
            dialog.dispose();
        });

        JButton exitBtn = new JButton("告别夜之城");
        exitBtn.setFont(btnFont);
        exitBtn.setForeground(new Color(0xCC, 0xDD, 0xEE));
        exitBtn.setBackground(new Color(0x33, 0x33, 0x44));
        exitBtn.setBorderPainted(false);
        exitBtn.setFocusPainted(false);
        exitBtn.setBounds(260, 260, 160, 40);
        exitBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        exitBtn.addActionListener(e -> {
            panel.putClientProperty("exitChosen", true);
            dialog.dispose();
        });

        panel.add(restartBtn);
        panel.add(exitBtn);
        dialog.setContentPane(panel);
        dialog.setVisible(true);
        return panel.getClientProperty("exitChosen") == Boolean.TRUE;
    }
}
