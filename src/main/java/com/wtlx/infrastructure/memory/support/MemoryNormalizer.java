package com.wtlx.infrastructure.memory.support;

import com.wtlx.domain.entity.memory.enums.MemoryType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * 记忆归一化工具。
 * <p>
 * 承担"把自由文本压成可比对键"的职责，是去重与冲突校验的地基：
 * <ul>
 *     <li><b>槽位键归一化</b>：让"预算 / budget / 花费"落到同一个槽位 {@code budget.level}，
 *         否则同一件事会以多条不相关记忆的形式共存，永远不会互相冲突——也就永远无法被修正；</li>
 *     <li><b>取值归一化</b>：忽略大小写、空白与标点，"喜欢徒步" 与 " 喜欢徒步！ " 视为同一取值；</li>
 *     <li><b>去重键</b>：{@code type|key|valueHash}，配合 ACTIVE 部分唯一索引在数据库层兜住并发重复写入。</li>
 * </ul>
 *
 * @author 金小梁
 */
@Component
public class MemoryNormalizer {

    /**
     * 槽位键最大长度（与 DDL 中 memory_key 的 VARCHAR(96) 对齐）
     */
    private static final int KEY_MAX_LEN = 96;

    /**
     * 去重键最大长度（与 DDL 中 dedup_key 的 VARCHAR(200) 对齐）
     */
    private static final int DEDUP_KEY_MAX_LEN = 200;

    /**
     * 取值指纹截取长度：16 位十六进制已足够区分，且让 dedup_key 保持短小
     */
    private static final int VALUE_HASH_LEN = 16;

    /**
     * 归一化槽位键。
     * <p>
     * 保留 ASCII 字母数字、下划线、点与中日韩字符；其余（斜杠、空格、短横线）统一折叠成点，
     * 连续点合并。无法产出有效槽位时回落到 {@link MemoryConstants#DEFAULT_MEMORY_KEY}。
     *
     * @param rawKey 模型输出的槽位键
     * @return 归一化后的槽位键
     */
    public String normalizeKey(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return MemoryConstants.DEFAULT_MEMORY_KEY;
        }
        StringBuilder sb = new StringBuilder(rawKey.length());
        boolean lastDot = false;
        for (int i = 0; i < rawKey.length(); i++) {
            char c = rawKey.charAt(i);
            char lower = Character.toLowerCase(c);
            if ((lower >= 'a' && lower <= 'z') || (lower >= '0' && lower <= '9') || lower == '_') {
                sb.append(lower);
                lastDot = false;
            } else if (isCjk(c)) {
                sb.append(c);
                lastDot = false;
            } else {
                // 分隔类字符统一折叠为一个点
                if (!lastDot && sb.length() > 0) {
                    sb.append('.');
                    lastDot = true;
                }
            }
        }
        String key = trimDots(sb.toString());
        if (key.isBlank()) {
            return MemoryConstants.DEFAULT_MEMORY_KEY;
        }
        return key.length() > KEY_MAX_LEN ? key.substring(0, KEY_MAX_LEN) : key;
    }

    /**
     * 计算去重键：{@code type|key|valueHash}
     *
     * @param type    记忆类型
     * @param key     已归一化的槽位键
     * @param content 记忆内容
     * @return 去重键
     */
    public String dedupKey(MemoryType type, String key, String content) {
        String raw = type.getCode() + "|" + key + "|" + valueHash(content);
        return raw.length() > DEDUP_KEY_MAX_LEN ? raw.substring(0, DEDUP_KEY_MAX_LEN) : raw;
    }

    /**
     * 内容指纹（sha256 全量），用于抽取台账幂等与精确查重
     */
    public String contentHash(String content) {
        return sha256(content == null ? "" : content.trim());
    }

    /**
     * 取值指纹：去除空白与标点、转小写后取 sha256 前 16 位。
     * 归一化后为空（例如纯标点）时退回全量内容指纹，保证不同取值不会撞成同一个键
     */
    public String valueHash(String content) {
        StringBuilder sb = new StringBuilder();
        if (content != null) {
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (isCjk(c) || Character.isLetterOrDigit(c)) {
                    sb.append(Character.toLowerCase(c));
                }
            }
        }
        String normalized = sb.toString();
        if (normalized.isEmpty()) {
            return sha256(content == null ? "" : content.trim()).substring(0, VALUE_HASH_LEN);
        }
        return sha256(normalized).substring(0, VALUE_HASH_LEN);
    }

    /**
     * 主体键清洗：只保留字母数字、下划线、冒号、短横线，防止拼接进 Redis 键或 SQL 参数时引入噪音
     */
    public String sanitizeSubjectKey(String subjectKey) {
        if (subjectKey == null || subjectKey.isBlank()) {
            return null;
        }
        StringBuilder sb = new StringBuilder(subjectKey.length());
        for (int i = 0; i < subjectKey.length(); i++) {
            char c = subjectKey.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || c == ':' || c == '-' || c == '.') {
                sb.append(c);
            }
        }
        String cleaned = sb.toString();
        if (cleaned.isBlank()) {
            return null;
        }
        return cleaned.length() > MemoryConstants.SUBJECT_KEY_MAX_LEN
                ? cleaned.substring(0, MemoryConstants.SUBJECT_KEY_MAX_LEN)
                : cleaned;
    }

    private String trimDots(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '.') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '.') {
            end--;
        }
        return value.substring(start, end);
    }

    private boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            // 环境不支持 SHA-256 属不可恢复场景；退回 hashCode 仅保证流程不中断
            return Integer.toHexString(text.hashCode());
        }
    }
}
