# 夜之城快递员 (NightCity Courier)

一款基于 Java Swing 的赛博朋克风文字冒险 / 任务驱动小游戏。玩家扮演一名夜之城底层快递员，在霓虹灯与废墟之间完成各种派送任务，体验随机事件、等级成长与多结局剧情。

## 玩法简介

- 接取派送任务 → 选择路线 → 应对随机事件 → 完成任务获取报酬
- 升级角色能力 / 装备，处理贪吃蛇式事件、铁路线式事件等突发事件
- 多个结局（包含"传奇英雄"等），最终命运由玩家的关键抉择决定

## 运行

需要 JDK 17 或更高版本。

```bash
cd NightCityCourier
javac -encoding UTF-8 -d out src\com\moji\NightCityCourier\*.java
java -cp out com.moji.NightCityCourier.Main
```

> 入口类：`com.moji.NightCityCourier.Main`

## 目录结构

```
.
├── NightCityCourier/        # 源代码根目录（package: com.moji.NightCityCourier）
│   ├── Main.java            # 程序入口
│   ├── GameWindow.java      # Swing 主窗口与 HUD
│   ├── GameController.java  # 主循环 / 事件分发
│   ├── GameConfig.java      # 游戏常量（含 WIN_TARGET 等演示用配置）
│   ├── EventSystem.java     # 随机事件系统
│   ├── MissionManager.java  # 任务系统
│   ├── Player.java          # 玩家数据与存档
│   ├── EndingSystem.java    # 多结局判定
│   └── UpgradeSystem.java   # 升级与成长
└── README.md
```

## 演示配置

`GameConfig.WIN_TARGET` 用于控制游戏的胜利金额阈值。演示场景可调小该值以压缩流程。

## License

仅用于学习交流。
