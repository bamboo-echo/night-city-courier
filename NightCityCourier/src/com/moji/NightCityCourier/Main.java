package com.moji.NightCityCourier;
import javax.swing.*;
import java.awt.*;

/**
 * 游戏入口类，负责初始化界面、显示加载动画、启动游戏主线程。
 */
public class Main {

    /**
     * 程序入口。
     * 依次执行：显示加载画面 → 创建主窗口 → 显示标题和教程 → 询问代号 → 加载存档 → 启动游戏控制器。
     */
    public static void main(String[] args) {
        showLoadingScreen();
        GameWindow gw = new GameWindow();
        try {
            SwingUtilities.invokeAndWait(() -> gw.setVisible(true));
        } catch (Exception e) {
            gw.setVisible(true);
        }
        gw.showWelcomeDialog();
        gw.showTutorialDialog();
        new Thread(() -> {
            gw.showTitle();
            String name = gw.showInputDialog("代号", "请输入你的代号：");
            if (name == null || name.trim().isEmpty()) {
                name = "V";
                System.out.println("【系统】输入为空，已使用默认代号: V");
            } else {
                name = name.trim();
            }
            Player player = new Player(name);
            if (Player.hasSave()) {
                int loadChoice = gw.showChoiceDialog("存档", "检测到存档，是否加载？", new String[]{"加载", "新游戏"});
                if (loadChoice == 0) {
                    Player loaded = Player.load();
                    if (loaded != null) {
                        player = loaded;
                        System.out.println("【确认】存档已加载，欢迎回来，" + player.getName() + "。");
                    } else {
                        System.out.println("【警告】存档加载失败，将开始新游戏。");
                    }
                }
            }
            System.out.println("\n欢迎来到夜之城，" + player.getName() + "。");
            System.out.println("活下来，攒够钱，离开这里。\n");
            MissionManager mm = new MissionManager();
            UpgradeSystem us = new UpgradeSystem();
            EventSystem es = new EventSystem(gw);
            GameController controller = new GameController(player, mm, us, es, gw);
            controller.run();
        }).start();
    }

    /** 加载进度 (0.0 ~ 1.0)，在加载动画中使用 */
    private static volatile double progress = 0;
    /** 加载状态文字，在加载动画中使用 */
    private static volatile String statusText = "正在启动...";

    /**
     * 显示模态加载窗口，模拟夜之城系统启动过程。
     * 包含标题、进度条、加载步骤文字和自定义绘制背景。
     */
    private static void showLoadingScreen() {
        JDialog loadingDialog = new JDialog((Frame) null, "夜之城快递员", true);
        loadingDialog.setUndecorated(true);
        loadingDialog.setSize(600, 350);
        loadingDialog.setLocationRelativeTo(null);
        loadingDialog.setBackground(new Color(0, 0, 0, 0));

        JPanel mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(new Color(0x0A, 0x0A, 0x14));
                g2.fillRect(0, 0, getWidth(), getHeight());

                g2.setColor(new Color(0x00, 0xCC, 0xCC));
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

                g2.setColor(new Color(0x00, 0xCC, 0xCC));
                Font titleFont = new Font("Microsoft YaHei", Font.BOLD, 28);
                g2.setFont(titleFont);
                FontMetrics fm = g2.getFontMetrics();
                String title = "夜之城快递员";
                g2.drawString(title, (getWidth() - fm.stringWidth(title)) / 2, 80);

                g2.setColor(new Color(0xC0, 0xC0, 0xC0));
                Font subFont = new Font("Microsoft YaHei", Font.PLAIN, 14);
                g2.setFont(subFont);
                FontMetrics sfm = g2.getFontMetrics();
                g2.drawString("NIGHT CITY COURIER SYSTEM v1.3.7",
                        (getWidth() - sfm.stringWidth("NIGHT CITY COURIER SYSTEM v1.3.7")) / 2, 110);

                int barX = 80;
                int barY = 180;
                int barW = getWidth() - 160;
                int barH = 24;

                g2.setColor(new Color(0x1A, 0x1A, 0x2A));
                g2.fillRoundRect(barX, barY, barW, barH, 6, 6);

                Color barColor = new Color(0x00, 0xCC, 0xCC);
                g2.setColor(barColor);
                int fillW = (int) (barW * progress);
                if (fillW > 0) {
                    g2.fillRoundRect(barX, barY, fillW, barH, 6, 6);
                }

                g2.setColor(new Color(0x00, 0xFF, 0xFF, 60));
                g2.setStroke(new BasicStroke(1));
                g2.drawRoundRect(barX, barY, barW, barH, 6, 6);

                g2.setColor(new Color(0xC0, 0xC0, 0xC0));
                Font statusFont = new Font("Microsoft YaHei", Font.PLAIN, 13);
                g2.setFont(statusFont);
                FontMetrics stfm = g2.getFontMetrics();
                int percent = (int) (progress * 100);
                String pctText = percent + "%";
                g2.drawString(pctText, (getWidth() - stfm.stringWidth(pctText)) / 2, barY + barH + 30);

                g2.setColor(new Color(0x66, 0x66, 0x88));
                Font hintFont = new Font("Microsoft YaHei", Font.PLAIN, 11);
                g2.setFont(hintFont);
                FontMetrics hfm = g2.getFontMetrics();
                g2.drawString(statusText, (getWidth() - hfm.stringWidth(statusText)) / 2, barY + barH + 55);
            }
        };
        mainPanel.setLayout(null);
        mainPanel.setBackground(new Color(0x0A, 0x0A, 0x14));

        loadingDialog.setContentPane(mainPanel);

        String[] loadingSteps = {
                "初始化神经接口...",
                "连接城市网格...",
                "加载义体驱动...",
                "绕过身份验证...",
                "同步记忆碎片...",
                "校准神经映射...",
                "验证系统完整性...",
                "建立安全通道...",
                "启动快递员协议...",
                "准备就绪"
        };

        // 后台线程逐步更新进度条和文字
        new Thread(() -> {
            for (int i = 0; i < loadingSteps.length; i++) {
                statusText = loadingSteps[i];
                progress = (i + 1.0) / loadingSteps.length;
                mainPanel.repaint();
                try {
                    Thread.sleep(300 + (int) (Math.random() * 400));
                } catch (InterruptedException e) {
                    break;
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
            SwingUtilities.invokeLater(() -> loadingDialog.dispose());
        }).start();

        loadingDialog.setVisible(true);
    }

    /** 系统关机序列（无界面版本） */
    public static void shutdownSequence() {
        shutdownSequence(null);
    }

    /**
     * 系统关机序列，逐行输出关机提示文字。
     *
     * @param gameWindow 游戏窗口引用，用于刷新打字机并强制显示
     */
    public static void shutdownSequence(GameWindow gameWindow) {
        String[] lines = {
                "> 会话即将结束...",
                "> 正在保存日志...",
                "> 感谢你为夜之城所做的一切。",
                "> 正在断开神经接口...",
                "> ",
                "你摘下头盔。夜之城的雨还在下。"
        };
        for (String line : lines) {
            System.out.println(line);
            if (gameWindow != null) {
                gameWindow.flushTypewriter();
            }
            try {
                Thread.sleep(400 + (int) (Math.random() * 100));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}