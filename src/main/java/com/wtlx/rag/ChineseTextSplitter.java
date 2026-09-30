package com.wtlx.rag;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文友好的递归文本切片器。
 * <p>
 * 与英文按 token 切分不同，中文没有天然词边界，这里按"段落 → 换行 → 句末标点 → 硬切"
 * 逐级降级，尽量让切片落在语义边界上；相邻切片保留一段重叠，避免答案正好跨切点被截断。
 * <p>
 * 不依赖任何 Spring AI 的 splitter 实现（TokenTextSplitter 按 token 估算，中文长度严重失真）。
 *
 * @author 金小梁
 */
public class ChineseTextSplitter {

    /**
     * 分隔符优先级：从粗到细
     */
    private static final String[] SEPARATORS = {"\n\n", "\n", "。", "！", "？", "；", "，", " "};

    private final int chunkSize;

    private final int chunkOverlap;

    public ChineseTextSplitter(int chunkSize, int chunkOverlap) {
        this.chunkSize = Math.max(50, chunkSize);
        this.chunkOverlap = Math.max(0, Math.min(chunkOverlap, this.chunkSize / 2));
    }

    /**
     * 切片
     *
     * @param text 原文
     * @return 切片列表；原文为空时返回空列表
     */
    public List<String> split(String text) {
        List<String> chunks = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return chunks;
        }
        List<String> pieces = recursiveSplit(text.trim(), 0);
        StringBuilder current = new StringBuilder();
        for (String piece : pieces) {
            if (current.length() == 0 && piece.length() > chunkSize) {
                // 单句本身就超长（无标点长文本）：硬切
                chunks.addAll(hardSplit(piece));
                continue;
            }
            if (current.length() + piece.length() <= chunkSize) {
                current.append(piece);
                continue;
            }
            chunks.add(current.toString());
            String tail = tailOverlap(current.toString());
            current.setLength(0);
            current.append(tail).append(piece);
        }
        if (current.length() > 0) {
            chunks.add(current.toString());
        }
        return chunks;
    }

    /**
     * 按分隔符逐级切分，直到片段足够小
     */
    private List<String> recursiveSplit(String text, int level) {
        List<String> result = new ArrayList<>();
        if (text.length() <= chunkSize || level >= SEPARATORS.length) {
            result.add(text);
            return result;
        }
        String separator = SEPARATORS[level];
        int start = 0;
        int index = text.indexOf(separator);
        while (index >= 0) {
            String piece = text.substring(start, index + separator.length());
            if (piece.length() > chunkSize) {
                result.addAll(recursiveSplit(piece, level + 1));
            } else if (!piece.isBlank()) {
                result.add(piece);
            }
            start = index + separator.length();
            index = text.indexOf(separator, start);
        }
        String rest = text.substring(start);
        if (!rest.isBlank()) {
            if (rest.length() > chunkSize) {
                result.addAll(recursiveSplit(rest, level + 1));
            } else {
                result.add(rest);
            }
        }
        return result;
    }

    /**
     * 无分隔符兜底：按固定长度硬切
     */
    private List<String> hardSplit(String text) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < text.length(); i += chunkSize) {
            out.add(text.substring(i, Math.min(i + chunkSize, text.length())));
        }
        return out;
    }

    /**
     * 取上一段末尾若干字符作为下一段开头，形成重叠
     */
    private String tailOverlap(String text) {
        if (chunkOverlap == 0 || text.length() <= chunkOverlap) {
            return "";
        }
        return text.substring(text.length() - chunkOverlap);
    }
}
