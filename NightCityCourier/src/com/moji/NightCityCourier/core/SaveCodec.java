package com.moji.NightCityCourier.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;

/**
 * 存档编解码器（格式 v3）。
 *
 * <p>相比 v2 的「魔数 + 版本 + SHA-256 + Properties」，v3 补上了三处工程缺口：
 * <ol>
 *   <li><b>双校验</b>：CRC32 先做廉价的整体完整性判断，SHA-256 再做内容摘要。
 *       单靠 SHA-256 无法区分「截断」和「篡改」，加长度字段后可以精确定位损坏方式；</li>
 *   <li><b>显式长度</b>：载荷字节数写入头部，读取时先比对长度，避免把半截文件
 *       当成合法存档解析后再报一堆莫名其妙的错误；</li>
 *   <li><b>键值转义</b>：v2 直接拼接 {@code key=value}，值里出现换行或等号就错位。
 *       v3 定义 {@code \\} {@code \n} {@code \r} {@code =} 四个转义，保证任意文本可往返。</li>
 * </ol>
 *
 * <p>文件布局（UTF-8，行尾统一 {@code \n}）：
 * <pre>
 * NCCv3
 * version=3
 * created=1712345678901
 * payload-len=1234
 * crc32=1a2b3c4d
 * sha256=...(64 hex)
 * --
 * key=value
 * ...
 * </pre>
 */
public final class SaveCodec {

    public static final String MAGIC = "NCCv3";
    public static final String LEGACY_MAGIC = "NCCv";
    public static final int VERSION = 3;
    private static final String SEPARATOR = "--";

    private SaveCodec() {
    }

    /** 解码后的文档。entries 保持插入顺序，未知键原样保留以支持前向兼容。 */
    public record Document(int version, long createdAt, Map<String, String> entries) {

        public Document {
            entries = Map.copyOf(entries);
        }

        public String get(String key, String fallback) {
            return entries.getOrDefault(key, fallback);
        }

        public int getInt(String key, int fallback) {
            try {
                return Integer.parseInt(entries.getOrDefault(key, "").trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        public long getLong(String key, long fallback) {
            try {
                return Long.parseLong(entries.getOrDefault(key, "").trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }

        public boolean getBool(String key, boolean fallback) {
            String v = entries.get(key);
            if (v == null) {
                return fallback;
            }
            return "true".equalsIgnoreCase(v.trim()) || "1".equals(v.trim());
        }

        public double getDouble(String key, double fallback) {
            try {
                return Double.parseDouble(entries.getOrDefault(key, "").trim());
            } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }

    /** 解码结果。ok=false 时 error 说明具体原因，detectedVersion 为识别到的版本（无法识别时为 -1）。 */
    public record DecodeResult(boolean ok, Document document, String error, int detectedVersion) {

        static DecodeResult fail(String error, int version) {
            return new DecodeResult(false, null, error, version);
        }

        static DecodeResult success(Document doc) {
            return new DecodeResult(true, doc, null, doc.version());
        }
    }

    // ────────── 编码 ──────────

    public static String encode(Map<String, String> entries) {
        return encode(entries, Instant.now().toEpochMilli());
    }

    /** 指定时间戳编码，便于测试产出稳定文本。 */
    public static String encode(Map<String, String> entries, long createdAt) {
        if (entries == null) {
            throw new IllegalArgumentException("entries 不可为 null");
        }
        Map<String, String> sorted = new TreeMap<>(entries);
        StringBuilder payload = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            requireSafeKey(e.getKey());
            payload.append(e.getKey()).append('=').append(escape(e.getValue())).append('\n');
        }
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);

        CRC32 crc = new CRC32();
        crc.update(bytes);

        StringBuilder head = new StringBuilder();
        head.append(MAGIC).append('\n');
        head.append("version=").append(VERSION).append('\n');
        head.append("created=").append(createdAt).append('\n');
        head.append("payload-len=").append(bytes.length).append('\n');
        head.append("crc32=").append(HexFormat.of().formatHex(intToBytes((int) crc.getValue()))).append('\n');
        head.append("sha256=").append(sha256Hex(bytes)).append('\n');
        head.append(SEPARATOR).append('\n');
        return head.append(payload).toString();
    }

    // ────────── 解码 ──────────

    /**
     * 解码任意版本的存档文本。
     * v2 会被识别并迁移为 v3 文档（version 字段仍记录为 2，便于调用方决定是否改写落盘）。
     */
    public static DecodeResult decode(String raw) {
        if (raw == null || raw.isBlank()) {
            return DecodeResult.fail("存档内容为空", -1);
        }
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);
        String first = lines[0].trim();

        if (LEGACY_MAGIC.equals(first)) {
            return decodeLegacy(lines);
        }
        if (!MAGIC.equals(first)) {
            return DecodeResult.fail("不是本程序生成的存档（魔数不匹配：" + first + "）", -1);
        }

        Map<String, String> head = new LinkedHashMap<>();
        int separatorIndex = -1;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (SEPARATOR.equals(line.trim())) {
                separatorIndex = i;
                break;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                return DecodeResult.fail("头部第 " + (i + 1) + " 行格式非法", VERSION);
            }
            head.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
        }
        if (separatorIndex < 0) {
            return DecodeResult.fail("头部缺少分隔线，文件可能被截断", VERSION);
        }

        int version = parseInt(head.get("version"), -1);
        if (version != VERSION) {
            return DecodeResult.fail("存档版本不受支持：v" + version + "（当前 v" + VERSION + "）", version);
        }

        StringBuilder payload = new StringBuilder();
        for (int i = separatorIndex + 1; i < lines.length; i++) {
            payload.append(lines[i]);
            if (i < lines.length - 1) {
                payload.append('\n');
            }
        }
        // 注意：不要在这里剥掉末尾换行。编码时每行（含最后一行）都以 \n 结尾，
        // 重建载荷时必须原样保留，否则字节长度与头部声明不符，会被判为「截断」。
        String payloadText = payload.toString();
        byte[] bytes = payloadText.getBytes(StandardCharsets.UTF_8);

        int declaredLen = parseInt(head.get("payload-len"), -1);
        if (declaredLen >= 0 && declaredLen != bytes.length) {
            return DecodeResult.fail("载荷长度不符（声明 " + declaredLen + "，实际 " + bytes.length + "），文件可能被截断",
                    VERSION);
        }

        CRC32 crc = new CRC32();
        crc.update(bytes);
        String actualCrc = HexFormat.of().formatHex(intToBytes((int) crc.getValue()));
        String declaredCrc = head.getOrDefault("crc32", "");
        if (!declaredCrc.isEmpty() && !declaredCrc.equalsIgnoreCase(actualCrc)) {
            return DecodeResult.fail("CRC32 校验失败，存档已损坏", VERSION);
        }

        String declaredSha = head.getOrDefault("sha256", "");
        if (declaredSha.isEmpty() || !declaredSha.equalsIgnoreCase(sha256Hex(bytes))) {
            return DecodeResult.fail("SHA-256 校验失败，存档内容被修改或损坏", VERSION);
        }

        Map<String, String> entries = parsePayload(payloadText);
        if (entries == null) {
            return DecodeResult.fail("载荷包含非法行", VERSION);
        }
        long created = parseLong(head.get("created"), 0L);
        return DecodeResult.success(new Document(version, created, entries));
    }

    /** v2：{@code NCCv\n2\n<sha256>\n<props>}。校验通过则迁移为文档。 */
    private static DecodeResult decodeLegacy(String[] lines) {
        if (lines.length < 4) {
            return DecodeResult.fail("旧版存档字段不足，文件可能被截断", 2);
        }
        int version = parseInt(lines[1].trim(), -1);
        if (version != 2) {
            return DecodeResult.fail("旧版存档版本不支持：v" + version, version);
        }
        StringBuilder payload = new StringBuilder();
        for (int i = 3; i < lines.length; i++) {
            payload.append(lines[i]);
            if (i < lines.length - 1) {
                payload.append('\n');
            }
        }
        byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
        String expected = lines[2].trim();
        if (!expected.equalsIgnoreCase(sha256Hex(bytes))) {
            return DecodeResult.fail("旧版存档 SHA-256 校验失败", 2);
        }
        Map<String, String> entries = parsePayload(payload.toString());
        if (entries == null) {
            return DecodeResult.fail("旧版载荷包含非法行", 2);
        }
        return DecodeResult.success(new Document(2, 0L, entries));
    }

    /** 把旧的 v2 存档文本升级为 v3；无法识别时返回 null。 */
    public static String upgradeLegacy(String raw) {
        DecodeResult result = decode(raw);
        if (!result.ok()) {
            return null;
        }
        return encode(result.document().entries());
    }

    // ────────── 内部工具 ──────────

    private static Map<String, String> parsePayload(String payload) {
        Map<String, String> entries = new LinkedHashMap<>();
        if (payload.isEmpty()) {
            return entries;
        }
        for (String line : payload.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                return null;
            }
            String key = line.substring(0, eq);
            if (!isSafeKey(key)) {
                return null;
            }
            entries.put(key, unescape(line.substring(eq + 1)));
        }
        return entries;
    }

    /** 转义顺序很重要：先转义反斜杠，否则会把后续转义引入的反斜杠二次转义。 */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 8);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '=' -> sb.append("\\e");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unescape(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\\' || i == value.length() - 1) {
                sb.append(c);
                continue;
            }
            char next = value.charAt(++i);
            switch (next) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 'e' -> sb.append('=');
                case '\\' -> sb.append('\\');
                default -> sb.append('\\').append(next);
            }
        }
        return sb.toString();
    }

    private static void requireSafeKey(String key) {
        if (!isSafeKey(key)) {
            throw new IllegalArgumentException("非法存档键：" + key);
        }
    }

    private static boolean isSafeKey(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }
        for (int i = 0; i < key.length(); i++) {
            char c = key.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                    || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '-';
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    private static byte[] intToBytes(int value) {
        return new byte[]{(byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value};
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }

    private static int parseInt(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static long parseLong(String text, long fallback) {
        try {
            return Long.parseLong(text.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    /** 生成一份用于调试差异的排序键列表。 */
    public static List<String> keys(Document doc) {
        return new ArrayList<>(doc.entries().keySet());
    }
}
