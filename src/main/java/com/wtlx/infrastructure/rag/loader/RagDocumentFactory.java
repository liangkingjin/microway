package com.wtlx.infrastructure.rag.loader;

import com.wtlx.infrastructure.rag.splitter.ChineseTextSplitter;
import com.wtlx.infrastructure.rag.RagDocType;
import com.wtlx.domain.entity.Blog;
import com.wtlx.domain.entity.Guides;
import org.springframework.ai.document.Document;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 业务实体 → 向量库 {@link Document} 的转换工厂。
 * <p>
 * 两个关键约定：
 * <ol>
 *     <li><b>切片 id 确定性</b>：id 由 {@code docType:sourceId:chunkIndex} 经
 *         {@link UUID#nameUUIDFromBytes} 生成，既满足 PGVector 主键必须是 UUID 的约束，
 *         又保证同一片重复写入时 id 不变，配合"先删后写"即可幂等；</li>
 *     <li><b>切片文本带标题前缀</b>：每片开头拼上标题，让"云南旅游"这类主题词参与语义匹配，
 *         避免游记正文里不出现目的地却检索不到。</li>
 * </ol>
 *
 * @author 金小梁
 */
public class RagDocumentFactory {

    /**
     * 切片 id 前缀命名空间，隔离不同来源类型
     */
    private static final String ID_NAMESPACE = "wtlx-rag:";

    private final ChineseTextSplitter splitter;

    private final int maxChunks;

    public RagDocumentFactory(ChineseTextSplitter splitter, int maxChunks) {
        this.splitter = splitter;
        this.maxChunks = maxChunks;
    }

    /**
     * 计算来源内容指纹，用于判断内容是否变化
     *
     * @param parts 参与计算的内容片段
     * @return md5 十六进制字符串
     */
    public static String contentHash(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            sb.append(part == null ? "" : part).append('\u0000');
        }
        return DigestUtils.md5DigestAsHex(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 游记 → 切片文档列表
     *
     * @param blog 游记；为 null 时返回空列表
     * @return 切片文档
     */
    public List<Document> fromBlog(Blog blog) {
        if (blog == null || blog.getId() == null) {
            return List.of();
        }
        String title = safe(blog.getTitle());
        String body = safe(blog.getContent());
        String text = "游记标题：" + title + "\n" + body;
        Map<String, Object> base = new HashMap<>();
        base.put("docType", RagDocType.BLOG);
        base.put("sourceId", String.valueOf(blog.getId()));
        base.put("title", title);
        base.put("authorId", blog.getUserId() == null ? null : String.valueOf(blog.getUserId()));
        base.put("cover", firstImage(blog.getImages()));
        base.put("liked", blog.getLiked() == null ? 0 : blog.getLiked());
        base.put("contentHash", contentHash(title, body, safe(blog.getImages())));
        base.put("sourceUpdatedAt", String.valueOf(blog.getUpdateTime() == null ? blog.getCreateTime() : blog.getUpdateTime()));
        return build(RagDocType.BLOG, blog.getId(), text, base);
    }

    /**
     * 官方攻略 → 切片文档列表
     *
     * @param guide 攻略；为 null 时返回空列表
     * @return 切片文档
     */
    public List<Document> fromGuide(Guides guide) {
        if (guide == null || guide.getId() == null) {
            return List.of();
        }
        String title = safe(guide.getTitle());
        String subtitle = safe(guide.getSubtitle());
        String body = safe(guide.getContent());
        String text = "攻略标题：" + title + "\n" + (subtitle.isEmpty() ? "" : "副标题：" + subtitle + "\n") + body;
        Map<String, Object> base = new HashMap<>();
        base.put("docType", RagDocType.GUIDE);
        base.put("sourceId", String.valueOf(guide.getId()));
        base.put("title", title);
        base.put("cover", StringUtils.hasText(guide.getImage()) ? guide.getImage() : firstImage(guide.getImages()));
        base.put("provinceId", guide.getProvinceId() == null ? null : String.valueOf(guide.getProvinceId()));
        base.put("liked", 0);
        base.put("contentHash", contentHash(title, subtitle, body, safe(guide.getImages())));
        base.put("sourceUpdatedAt", String.valueOf(guide.getUpdatedAt() == null ? guide.getCreatedAt() : guide.getUpdatedAt()));
        return build(RagDocType.GUIDE, guide.getId().longValue(), text, base);
    }

    /**
     * 生成某来源第 index 片的确定性 id
     *
     * @param docType   来源类型
     * @param sourceId  来源主键
     * @param chunkIndex 片序
     * @return UUID 字符串
     */
    public static String chunkId(String docType, Long sourceId, int chunkIndex) {
        String seed = ID_NAMESPACE + docType + ":" + sourceId + ":" + chunkIndex;
        return UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private List<Document> build(String docType, Long sourceId, String text, Map<String, Object> base) {
        List<String> chunks = splitter.split(text);
        if (chunks.isEmpty()) {
            return List.of();
        }
        if (chunks.size() > maxChunks) {
            chunks = new ArrayList<>(chunks.subList(0, maxChunks));
        }
        List<Document> documents = new ArrayList<>(chunks.size());
        int total = chunks.size();
        for (int i = 0; i < total; i++) {
            Map<String, Object> metadata = new HashMap<>(base);
            metadata.put("chunkIndex", i);
            metadata.put("chunkTotal", total);
            documents.add(Document.builder()
                    .id(chunkId(docType, sourceId, i))
                    .text(chunks.get(i))
                    .metadata(metadata)
                    .build());
        }
        return documents;
    }

    private String firstImage(String images) {
        if (!StringUtils.hasText(images)) {
            return "";
        }
        int comma = images.indexOf(',');
        return comma > 0 ? images.substring(0, comma) : images;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
