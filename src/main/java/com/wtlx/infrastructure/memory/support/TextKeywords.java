package com.wtlx.infrastructure.memory.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 中文关键词抽取（轻量 n-gram）。
 * <p>
 * 多路检索里的"关键词路"需要一个不依赖分词器、不依赖 embedding 的兜底召回路：
 * 向量路在语义漂移或 embedding 服务抖动时会整体失效，槽位路只覆盖预置槽位，
 * 关键词路保证"用户原话里出现过的词"一定能在记忆库里被找回来。
 * <p>
 * 实现取 2-gram / 3-gram（中文）与英文单词，配合停用词过滤。刻意不引入 jieba 之类的
 * 分词依赖：召回兜底要的是"高召回、可解释"，不是分词精度；关键词路在 RRF 里权重也最低。
 *
 * @author 金小梁
 */
public final class TextKeywords {

    /**
     * 中文停用词（含常见口语填充词）。命中即丢弃该 n-gram，避免"帮我""想去"之类
     * 泛词把整库记忆都召回来。
     * <p>
     * 用 {@link java.util.Set#of} 构建：元素必须互不重复，重复会让类初始化直接抛
     * {@code IllegalArgumentException}（表现为运行期 {@code NoClassDefFoundError}），
     * 因此词表增删后务必确认无重复项。
     */
    private static final Set<String> STOP_WORDS = Set.of(
            "帮我", "我想", "我要", "我想去", "想去", "想要", "可以", "能不能", "有没有",
            "什么", "怎么", "怎样", "如何", "哪里", "哪个", "为什么", "多少",
            "一下", "一个", "一些", "这个", "那个", "这些", "那些", "我的", "你的",
            "就是", "还是", "或者", "但是", "因为", "所以", "如果",
            "谢谢", "你好", "请问", "麻烦", "现在", "最近", "以后", "然后"
    );

    /**
     * 单个关键词最小长度
     */
    private static final int MIN_KEYWORD_LEN = 2;

    private TextKeywords() {
    }

    /**
     * 从查询文本抽取关键词
     *
     * @param text 查询文本
     * @param max  最大返回条数
     * @return 关键词列表（已去重、按长度优先）；无可用关键词时返回空列表
     */
    public static List<String> extract(String text, int max) {
        if (text == null || text.isBlank() || max <= 0) {
            return List.of();
        }
        String normalized = text.toLowerCase();
        Set<String> candidates = new LinkedHashSet<>();

        // 1. 英文 / 数字词：连续字母数字视为一个词
        StringBuilder ascii = new StringBuilder();
        List<String> cjkRuns = new ArrayList<>();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (isCjk(c)) {
                flushAscii(ascii, candidates);
                cjk.append(c);
            } else if (Character.isLetterOrDigit(c)) {
                flushCjk(cjk, cjkRuns);
                ascii.append(c);
            } else {
                flushAscii(ascii, candidates);
                flushCjk(cjk, cjkRuns);
            }
        }
        flushAscii(ascii, candidates);
        flushCjk(cjk, cjkRuns);

        // 2. 中文串切 n-gram：先 3-gram 再 2-gram，长词更有区分度，优先进结果
        List<String> ngrams = new ArrayList<>();
        for (String run : cjkRuns) {
            if (run.length() < MIN_KEYWORD_LEN) {
                continue;
            }
            for (int n = 3; n >= 2; n--) {
                for (int i = 0; i + n <= run.length(); i++) {
                    String gram = run.substring(i, i + n);
                    if (!STOP_WORDS.contains(gram)) {
                        ngrams.add(gram);
                    }
                }
            }
        }
        for (String gram : ngrams) {
            if (candidates.size() >= max) {
                break;
            }
            candidates.add(gram);
        }
        return new ArrayList<>(candidates);
    }

    /**
     * 词条拼成逗号分隔串，供 SQL 侧 {@code string_to_array(?)} 拆开用。
     * 关键词本身不含逗号（抽取阶段已按字母数字/CJK 切分），故使用逗号安全。
     */
    public static String join(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        return String.join(",", keywords);
    }

    private static void flushAscii(StringBuilder ascii, Set<String> candidates) {
        if (ascii.length() >= MIN_KEYWORD_LEN) {
            candidates.add(ascii.toString());
        }
        ascii.setLength(0);
    }

    private static void flushCjk(StringBuilder cjk, List<String> runs) {
        if (cjk.length() > 0) {
            runs.add(cjk.toString());
        }
        cjk.setLength(0);
    }

    /**
     * 判断是否 CJK 统一表意文字
     */
    private static boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }
}
