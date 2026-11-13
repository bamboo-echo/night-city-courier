package com.moji.NightCityCourier.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 多槽位存档仓库：负责落盘、原子写入、备份轮转与损坏恢复。
 *
 * <p>设计要点：
 * <ul>
 *   <li><b>原子写</b>：先写 {@code .tmp}，再 {@code ATOMIC_MOVE} 覆盖目标。
 *       断电或进程被杀时，正式文件要么是旧的完整内容，要么是新的完整内容，
 *       不会出现「写了一半」的存档；</li>
 *   <li><b>备份轮转</b>：每次成功写入前，把上一份合法存档轮转为 {@code .bak1}，
 *       {@code .bak2} ……只保留最近 {@code BACKUP_KEEP} 份，避免无限膨胀；</li>
 *   <li><b>损坏不丢档</b>：读取失败时把问题文件改名为 {@code .corrupt-时间戳}，
 *       绝不删除，用户仍有机会手工抢救。</li>
 * </ul>
 */
public final class SaveStore {

    public static final int BACKUP_KEEP = 3;
    public static final String SLOT_MAIN = "main";
    private static final String EXT = ".dat";

    private final Path root;

    public SaveStore(Path root) {
        if (root == null) {
            throw new IllegalArgumentException("存档目录不可为 null");
        }
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public Path fileOf(String slot) {
        return root.resolve(sanitize(slot) + EXT);
    }

    /** 写入指定槽位。返回是否成功；失败时异常已转为日志，不会打断游戏。 */
    public boolean save(String slot, Map<String, String> entries) {
        Path target = fileOf(slot);
        try {
            Files.createDirectories(root);
            rotateBackups(slot);
            Path tmp = root.resolve(sanitize(slot) + EXT + ".tmp");
            Files.writeString(tmp, SaveCodec.encode(entries), StandardCharsets.UTF_8);
            moveAtomic(tmp, target);
            return true;
        } catch (IOException e) {
            GameLog.error("存档写入失败：" + e.getMessage());
            return false;
        }
    }

    /** 读取指定槽位；失败返回 empty，并把问题文件隔离保留。 */
    public Optional<SaveCodec.Document> load(String slot) {
        return loadFrom(fileOf(slot));
    }

    public Optional<SaveCodec.Document> loadFrom(Path file) {
        if (!Files.exists(file)) {
            return Optional.empty();
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            SaveCodec.DecodeResult result = SaveCodec.decode(raw);
            if (!result.ok()) {
                GameLog.warn("存档无法读取（" + result.error() + "），已隔离保留原文件。");
                quarantine(file);
                return Optional.empty();
            }
            return Optional.of(result.document());
        } catch (IOException e) {
            GameLog.error("读取存档失败：" + e.getMessage());
            return Optional.empty();
        }
    }

    /** 依次尝试当前槽位与各级备份，返回第一个可读文档。 */
    public Optional<SaveCodec.Document> loadWithFallback(String slot) {
        Optional<SaveCodec.Document> primary = load(slot);
        if (primary.isPresent()) {
            return primary;
        }
        for (int i = 1; i <= BACKUP_KEEP; i++) {
            Path bak = root.resolve(sanitize(slot) + EXT + ".bak" + i);
            Optional<SaveCodec.Document> recovered = loadFrom(bak);
            if (recovered.isPresent()) {
                GameLog.info("已从备份 " + bak.getFileName() + " 恢复存档。");
                return recovered;
            }
        }
        return Optional.empty();
    }

    public boolean exists(String slot) {
        return Files.exists(fileOf(slot));
    }

    public boolean delete(String slot) {
        try {
            return Files.deleteIfExists(fileOf(slot));
        } catch (IOException e) {
            GameLog.error("删除存档失败：" + e.getMessage());
            return false;
        }
    }

    /** 列出所有可用槽位名（按文件名排序，已剔除 tmp/备份文件）。 */
    public List<String> listSlots() {
        List<String> slots = new ArrayList<>();
        if (!Files.isDirectory(root)) {
            return slots;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root, "*" + EXT)) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (name.contains(".bak") || name.contains(".corrupt")) {
                    continue;
                }
                slots.add(name.substring(0, name.length() - EXT.length()));
            }
        } catch (IOException e) {
            GameLog.error("枚举存档失败：" + e.getMessage());
        }
        slots.sort(String::compareTo);
        return slots;
    }

    /** 手动备份当前槽位（给「导入/导出」功能用）。 */
    public boolean snapshot(String slot, String suffix) {
        Path src = fileOf(slot);
        if (!Files.exists(src)) {
            return false;
        }
        try {
            Files.createDirectories(root);
            Files.copy(src, root.resolve(sanitize(slot) + EXT + "." + sanitize(suffix)),
                    StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            GameLog.error("备份失败：" + e.getMessage());
            return false;
        }
    }

    // ────────── 内部 ──────────

    private void rotateBackups(String slot) throws IOException {
        Path target = fileOf(slot);
        if (!Files.exists(target)) {
            return;
        }
        // 先删最旧的，再整体后移，避免覆盖
        Files.deleteIfExists(root.resolve(sanitize(slot) + EXT + ".bak" + BACKUP_KEEP));
        for (int i = BACKUP_KEEP - 1; i >= 1; i--) {
            Path from = root.resolve(sanitize(slot) + EXT + ".bak" + i);
            Path to = root.resolve(sanitize(slot) + EXT + ".bak" + (i + 1));
            if (Files.exists(from)) {
                moveAtomic(from, to);
            }
        }
        moveAtomic(target, root.resolve(sanitize(slot) + EXT + ".bak1"));
    }

    private static void moveAtomic(Path from, Path to) throws IOException {
        try {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void quarantine(Path file) {
        try {
            String stamp = String.valueOf(System.currentTimeMillis());
            Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt-" + stamp),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            GameLog.error("隔离损坏存档失败：" + e.getMessage());
        }
    }

    /** 槽位名只允许字母数字与 - _，防止路径穿越。 */
    static String sanitize(String slot) {
        if (slot == null || slot.isBlank()) {
            throw new IllegalArgumentException("槽位名不可为空");
        }
        StringBuilder sb = new StringBuilder();
        for (char c : slot.toCharArray()) {
            boolean ok = Character.isLetterOrDigit(c) || c == '-' || c == '_';
            sb.append(ok ? c : '_');
        }
        return sb.toString();
    }
}
