package com.moji.NightCityCourier.core;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 统一日志出口。
 *
 * <p>原版直接把叙事、系统提示、错误混在 {@code System.out} 里，导致三个问题：
 * 无法区分「剧情文本」与「诊断信息」、无法在无头环境下重定向、出错时只能靠肉眼翻屏。
 * 本类把输出收敛成一个可插拔的 {@link Consumer}：
 * <ul>
 *   <li>Swing 版把 sink 接到文本区，观感不变；</li>
 *   <li>CLI 版接到 stdout；</li>
 *   <li>测试接到内存缓冲，断言输出内容；</li>
 *   <li>同时可挂一个文件 sink，落盘到 {@code debug.log}。</li>
 * </ul>
 *
 * <p>阈值可调：生产默认 {@code INFO}，排查时切 {@code TRACE}，压测时切 {@code WARN}。
 */
public final class GameLog {

    public enum Level {
        TRACE(0), DEBUG(1), INFO(2), WARN(3), ERROR(4), OFF(5);

        public final int weight;

        Level(int weight) {
            this.weight = weight;
        }
    }

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private static final Object LOCK = new Object();
    private static final List<String> MEMORY_BUFFER = new ArrayList<>();
    private static volatile Level threshold = Level.INFO;
    private static volatile Consumer<String> sink = GameLog::printToStdout;
    private static volatile Path fileSink;
    private static volatile boolean memoryEnabled;
    private static volatile boolean timestamped;

    private GameLog() {
    }

    // ────────── 配置 ──────────

    public static void setThreshold(Level level) {
        threshold = level == null ? Level.INFO : level;
    }

    public static Level getThreshold() {
        return threshold;
    }

    /** 替换文本出口。传 null 表示静默（只落文件）。 */
    public static void setSink(Consumer<String> newSink) {
        sink = newSink;
    }

    /** 恢复默认的 stdout 出口。 */
    public static void resetSink() {
        sink = GameLog::printToStdout;
    }

    /** 追加写入日志文件；传 null 关闭文件落盘。 */
    public static void setFileSink(Path path) {
        fileSink = path;
    }

    /** 打开内存缓冲，便于测试断言。 */
    public static void enableMemoryBuffer(boolean enable) {
        memoryEnabled = enable;
        if (!enable) {
            synchronized (LOCK) {
                MEMORY_BUFFER.clear();
            }
        }
    }

    public static List<String> memorySnapshot() {
        synchronized (LOCK) {
            return List.copyOf(MEMORY_BUFFER);
        }
    }

    public static void clearMemory() {
        synchronized (LOCK) {
            MEMORY_BUFFER.clear();
        }
    }

    /** 是否给每行加时间戳前缀（CLI 调试用；叙事场景应关闭以保持沉浸感）。 */
    public static void setTimestamped(boolean enable) {
        timestamped = enable;
    }

    /** 恢复默认状态，供测试之间隔离。 */
    public static void reset() {
        threshold = Level.INFO;
        sink = GameLog::printToStdout;
        fileSink = null;
        memoryEnabled = false;
        timestamped = false;
        synchronized (LOCK) {
            MEMORY_BUFFER.clear();
        }
    }

    // ────────── 输出 ──────────

    public static void trace(String message) {
        log(Level.TRACE, message);
    }

    public static void debug(String message) {
        log(Level.DEBUG, message);
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void warn(String message) {
        log(Level.WARN, message);
    }

    public static void error(String message) {
        log(Level.ERROR, message);
    }

    public static void error(String message, Throwable t) {
        log(Level.ERROR, message + " :: " + describe(t));
    }

    /**
     * 叙事输出：属于「故事正文」而非诊断信息，永远穿透阈值过滤。
     * 剧情、事件描述、选项提示都应走这里，保证玩家调高日志级别时故事不会消失。
     */
    public static void story(String message) {
        emit("", message);
    }

    /** 空行，用于叙事分段。 */
    public static void blank() {
        emit("", "");
    }

    /** 章节横幅。 */
    public static void banner(String title) {
        String line = "═══════════════════════════════";
        emit("", line);
        emit("", "  " + title);
        emit("", line);
    }

    public static void log(Level level, String message) {
        if (level.weight < threshold.weight) {
            return;
        }
        String prefix = switch (level) {
            case TRACE -> "[TRACE] ";
            case DEBUG -> "[DEBUG] ";
            case WARN -> "【警告】";
            case ERROR -> "【错误】";
            default -> "";
        };
        if (level.weight >= Level.WARN.weight) {
            emit(prefix, message);
        } else {
            emit(prefix, message);
        }
    }

    private static void emit(String prefix, String message) {
        String text = message == null ? "" : message;
        String line = timestamped ? LocalTime.now().format(TS) + " " + prefix + text : prefix + text;
        synchronized (LOCK) {
            if (memoryEnabled) {
                MEMORY_BUFFER.add(line);
            }
            Consumer<String> current = sink;
            if (current != null) {
                current.accept(line);
            }
            Path target = fileSink;
            if (target != null) {
                appendFile(target, line);
            }
        }
    }

    private static void printToStdout(String line) {
        System.out.println(line);
    }

    private static void appendFile(Path target, String line) {
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(target, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            // 日志失败不能反过来打断游戏
            System.err.println("日志写入失败: " + e.getMessage());
        }
    }

    private static String describe(Throwable t) {
        if (t == null) {
            return "null";
        }
        StringWriter sw = new StringWriter();
        t.printStackTrace(new PrintWriter(sw));
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
