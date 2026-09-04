# 夜之城快递员 (NightCity Courier)

一款基于 Java Swing 的赛博朋克风文字冒险 / 任务驱动小游戏。玩家扮演一名夜之城底层快递员，在霓虹灯与废墟之间完成各种派送任务，体验随机事件、等级成长与多结局剧情。

## 玩法简介

- 接取派送任务 → 选择路线 → 应对随机事件 → 完成任务获取报酬
- 升级角色能力 / 装备，处理贪吃蛇式事件、铁路线式事件等突发事件
- 多个结局（包含"传奇英雄"等），最终命运由玩家的关键抉择决定

## 运行

需要 **JDK 17 或更高版本**。

```bash
cd NightCityCourier
javac -encoding UTF-8 -d out src/com/moji/NightCityCourier/*.java
java -cp out com.moji.NightCityCourier.Main
```

入口类：`com.moji.NightCityCourier.Main`

跨平台构建脚本（内部已处理 `src/` 路径与 `-encoding UTF-8`）：

```bash
# Windows (PowerShell)
.\build.ps1

# macOS / Linux
./build.sh
```

## 存档

存档位置：`~/.nightcity/save.dat`（`~` 为用户主目录，即 `System.getProperty("user.home")`）。

文件格式为纯文本 4 行结构，便于手工检查与跨版本兼容：

```
NCCv                 <- 魔数（本程序生成的存档标识）
2                    <- 格式版本
<SHA-256 十六进制>    <- 下面载荷的校验和
key=value            <- 玩家属性键值对（可多行）
...
```

校验失败、版本不匹配或文件损坏时，旧存档会被备份为 `save.dat.bak`，然后开始新游戏，不会覆盖你的数据。

## 目录结构

```
.
├── NightCityCourier/                    # 游戏工程目录
│   ├── src/com/moji/NightCityCourier/   # 源代码（package: com.moji.NightCityCourier）
│   │   ├── Main.java                    # 程序入口
│   │   ├── GameWindow.java              # Swing 主窗口与 HUD
│   │   ├── GameController.java          # 主循环 / 事件分发
│   │   ├── GameConfig.java              # 游戏常量（含 WIN_TARGET 等）
│   │   ├── EventSystem.java             # 随机事件系统
│   │   ├── MissionManager.java          # 任务系统
│   │   ├── Player.java                  # 玩家数据与存档序列化
│   │   ├── EndingSystem.java            # 多结局判定
│   │   └── UpgradeSystem.java           # 升级与成长
│   ├── test/com/moji/NightCityCourier/  # 单元测试
│   │   └── CoreSystemsTest.java         # 核心系统自研测试集（无第三方依赖）
│   ├── build.ps1                        # Windows 构建脚本
│   └── build.sh                         # macOS/Linux 构建脚本
└── README.md
```

`out/` 与 `test-out/` 为编译产物，已在 `.gitignore` 中排除。

## 测试

测试集不依赖 JUnit 等任何第三方库，可直接运行：

```bash
cd NightCityCourier
javac -encoding UTF-8 -d test-out \
  src/com/moji/NightCityCourier/*.java \
  test/com/moji/NightCityCourier/*.java
java -cp test-out com.moji.NightCityCourier.CoreSystemsTest
```

覆盖结局判定、事件系统、任务生成、存档序列化往返与篡改防护、跨平台字体解析、结局文案金额与 `WIN_TARGET` 的一致性。当前全部通过（1108 项）。

## 演示配置

`GameConfig.WIN_TARGET` 用于控制游戏的胜利金额阈值（逃离夜之城所需金额）。演示场景可调小该值以压缩流程。

游戏内的所有金额文案都从 `WIN_TARGET` 读取，改动后不需要同步修改字符串。判定阈值同理集中在 `GameConfig`：`HEALTH_CRITICAL`（致命伤）、`REPUTATION_VETERAN`（老手）、`REPUTATION_BOTTOM`（底层）。

## 跨平台

游戏可在 Windows / macOS / Linux 上运行。中文字体族与 emoji 字体族按平台优先顺序自动探测（雅黑 → 苹方 → 思源 → 文泉驿等 / Segoe UI Emoji → Apple Color Emoji → Noto Color Emoji），未命中时退回 Swing 逻辑字体 `SansSerif`，不会因缺少字体而崩溃或乱码。

解析逻辑在 `GameConfig.resolveFontFamily()` / `resolveEmojiFontFamily()`，字体族常量 `FONT_FAMILY` / `EMOJI_FONT_FAMILY` 供各界面类共用。

## License

仅用于学习交流。
