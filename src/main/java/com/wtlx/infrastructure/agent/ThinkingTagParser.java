package com.wtlx.infrastructure.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * 思考标记解析器：把模型增量文本按 {@code <thinking>} / {@code </thinking>} 拆成
 * "思考片段"与"正文片段"两类。
 * <p>
 * 设计要点：
 * <ol>
 *     <li>模型增量是按 token 到达的，标记极可能被切在两个 chunk 中间
 *         （如 "...<thi" + "nking>..."），因此必须做<b>尾部残留保护</b>：
 *         若缓冲区末尾是某个标签的前缀，则这部分留在缓冲区等待后续数据；</li>
 *     <li>初始状态为 {@code CONTENT}（正文）：模型若不使用标记，全部内容仍归入正文，
 *         保证最坏情况下也不比改造前差；</li>
 *     <li>结束时需调用 {@link #flush()} 吐出缓冲区残余内容，避免最后一段丢失。</li>
 * </ol>
 *
 * @author 金小梁
 */
public class ThinkingTagParser {

    private static final String OPEN_TAG = "<thinking>";

    private static final String CLOSE_TAG = "</thinking>";

    /**
     * 片段归属
     */
    public enum Kind {
        /** 结论正文 */
        CONTENT,
        /** 思考过程 */
        THINKING
    }

    /**
     * 解析出的一个片段
     */
    public static class Segment {
        private final Kind kind;
        private final String text;

        Segment(Kind kind, String text) {
            this.kind = kind;
            this.text = text;
        }

        public Kind getKind() {
            return kind;
        }

        public String getText() {
            return text;
        }

        static Segment content(String text) {
            return new Segment(Kind.CONTENT, text);
        }

        static Segment thinking(String text) {
            return new Segment(Kind.THINKING, text);
        }
    }

    private Kind state = Kind.CONTENT;

    private final StringBuilder pending = new StringBuilder();

    /**
     * 追加一段模型增量，返回可立即输出的片段（可能为空）
     *
     * @param text 本次到达的增量文本
     * @return 解析出的片段列表
     */
    public List<Segment> feed(String text) {
        List<Segment> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        pending.append(text);

        String source = pending.toString();
        int cursor = 0;

        while (true) {
            String tag = state == Kind.CONTENT ? OPEN_TAG : CLOSE_TAG;
            int index = source.indexOf(tag, cursor);
            if (index < 0) {
                break;
            }
            if (index > cursor) {
                out.add(emit(source.substring(cursor, index)));
            }
            cursor = index + tag.length();
            state = state == Kind.CONTENT ? Kind.THINKING : Kind.CONTENT;
        }

        String rest = source.substring(cursor);
        int keep = tailLooksLikePartialTag(rest);
        pending.setLength(0);
        if (keep > 0) {
            if (rest.length() > keep) {
                out.add(emit(rest.substring(0, rest.length() - keep)));
            }
            pending.append(rest, rest.length() - keep, rest.length());
        } else if (!rest.isEmpty()) {
            out.add(emit(rest));
        }
        return out;
    }

    /**
     * 流结束时吐出缓冲区残余内容
     *
     * @return 剩余片段列表（0 或 1 个）
     */
    public List<Segment> flush() {
        List<Segment> out = new ArrayList<>();
        if (pending.length() > 0) {
            out.add(emit(pending.toString()));
            pending.setLength(0);
        }
        return out;
    }

    /**
     * 当前是否处于思考块内部（用于判断是否需要兜底）
     *
     * @return true 表示已进入 {@code <thinking>} 但尚未闭合
     */
    public boolean insideThinking() {
        return state == Kind.THINKING;
    }

    private Segment emit(String text) {
        return state == Kind.CONTENT ? Segment.content(text) : Segment.thinking(text);
    }

    /**
     * 判断字符串尾部是否可能是某个标签的未完成前缀。
     * <p>
     * 例：{@code "我在思"} 尾部 {@code "<thi"} 是 {@code <thinking>} 的前缀 → 返回 4。
     *
     * @param text 待判断字符串
     * @return 需要保留的尾部长度；0 表示无需保留
     */
    private int tailLooksLikePartialTag(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int max = Math.min(text.length(), Math.max(OPEN_TAG.length(), CLOSE_TAG.length()) - 1);
        for (int len = max; len >= 1; len--) {
            String tail = text.substring(text.length() - len);
            if (OPEN_TAG.startsWith(tail) || CLOSE_TAG.startsWith(tail)) {
                // 仅当尾部存在 '<' 才可能是标签前缀，避免普通文本被误判
                if (tail.indexOf('<') >= 0) {
                    return len;
                }
            }
        }
        return 0;
    }
}
