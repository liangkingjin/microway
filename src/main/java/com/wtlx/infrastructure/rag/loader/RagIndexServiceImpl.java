package com.wtlx.infrastructure.rag.loader;

import com.wtlx.infrastructure.rag.config.RagProperties;
import com.wtlx.infrastructure.rag.splitter.ChineseTextSplitter;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wtlx.infrastructure.rag.RagDocType;
import com.wtlx.domain.entity.Blog;
import com.wtlx.domain.entity.Guides;
import com.wtlx.domain.entity.RagSourceIndex;
import com.wtlx.repository.mapper.BlogMapper;
import com.wtlx.repository.mapper.GuidesMapper;
import com.wtlx.repository.mapper.RagSourceIndexMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * RAG 索引服务实现。
 * <p>
 * 幂等策略：切片 id 是确定性 UUID，配合"先删旧片再写新片"实现重放安全；
 * 同时用索引表的内容指纹做短路，内容没变就不调用 embedding（省配额）。
 *
 * @author 金小梁
 */
@Slf4j
@Service
public class RagIndexServiceImpl implements RagIndexService {

    @Resource
    private VectorStore vectorStore;

    @Resource
    private RagProperties ragProperties;

    @Resource
    private BlogMapper blogMapper;

    @Resource
    private GuidesMapper guidesMapper;

    @Resource
    private RagSourceIndexMapper indexMapper;

    private RagDocumentFactory documentFactory;

    @PostConstruct
    public void init() {
        this.documentFactory = new RagDocumentFactory(
                new ChineseTextSplitter(ragProperties.getChunkSize(), ragProperties.getChunkOverlap()),
                ragProperties.getMaxChunks());
    }

    @Override
    public RagIndexResult index(String docType, Long sourceId) {
        if (!ragProperties.isEnabled()) {
            return RagIndexResult.of(RagIndexResult.Status.UNCHANGED);
        }
        if (!StringUtils.hasText(docType) || sourceId == null) {
            return RagIndexResult.of(RagIndexResult.Status.NOT_FOUND);
        }
        List<Document> documents;
        String hash;
        try {
            if (RagDocType.BLOG.equals(docType)) {
                Blog blog = blogMapper.selectById(sourceId);
                if (blog == null) {
                    remove(docType, sourceId);
                    return RagIndexResult.of(RagIndexResult.Status.NOT_FOUND);
                }
                documents = documentFactory.fromBlog(blog);
                hash = hashOf(documents);
            } else if (RagDocType.GUIDE.equals(docType)) {
                Guides guide = guidesMapper.selectById(sourceId.intValue());
                if (guide == null) {
                    remove(docType, sourceId);
                    return RagIndexResult.of(RagIndexResult.Status.NOT_FOUND);
                }
                documents = documentFactory.fromGuide(guide);
                hash = hashOf(documents);
            } else {
                return RagIndexResult.of(RagIndexResult.Status.NOT_FOUND);
            }
        } catch (Exception e) {
            markFailed(docType, sourceId, "读取来源数据失败: " + e.getMessage());
            throw new IllegalStateException("RAG 读取来源数据失败", e);
        }

        RagSourceIndex existed = findIndex(docType, sourceId);
        if (documents.isEmpty()) {
            deleteChunks(docType, sourceId, existed == null ? 0 : existed.getChunkCount());
            upsert(docType, sourceId, hash, 0, RagDocType.STATUS_SUCCESS, null, existed);
            return RagIndexResult.of(RagIndexResult.Status.EMPTY);
        }

        if (existed != null && RagDocType.STATUS_SUCCESS.equals(existed.getStatus())
                && hash.equals(existed.getContentHash())) {
            log.debug("RAG 来源内容未变化，跳过索引, docType: {}, sourceId: {}", docType, sourceId);
            return RagIndexResult.of(RagIndexResult.Status.UNCHANGED);
        }

        try {
            int oldCount = existed == null ? 0 : (existed.getChunkCount() == null ? 0 : existed.getChunkCount());
            deleteChunks(docType, sourceId, Math.max(oldCount, documents.size()));
            addInBatch(documents);
            upsert(docType, sourceId, hash, documents.size(), RagDocType.STATUS_SUCCESS, null, existed);
            log.info("RAG 索引完成, docType: {}, sourceId: {}, 切片数: {}", docType, sourceId, documents.size());
            return RagIndexResult.indexed(documents.size());
        } catch (Exception e) {
            markFailed(docType, sourceId, "切片入库失败: " + e.getMessage());
            throw new IllegalStateException("RAG 切片入库失败", e);
        }
    }

    @Override
    public void remove(String docType, Long sourceId) {
        RagSourceIndex existed = findIndex(docType, sourceId);
        int count = existed == null ? ragProperties.getMaxChunks()
                : (existed.getChunkCount() == null ? ragProperties.getMaxChunks() : existed.getChunkCount());
        deleteChunks(docType, sourceId, count);
        if (existed != null) {
            indexMapper.deleteById(existed.getId());
        }
    }

    @Override
    public RebuildStat indexBatch(String docType, List<Long> ids) {
        RebuildStat stat = new RebuildStat();
        stat.setTotal(ids == null ? 0 : ids.size());
        if (ids == null) {
            return stat;
        }
        for (Long id : ids) {
            try {
                RagIndexResult result = index(docType, id);
                if (result.getStatus() == RagIndexResult.Status.INDEXED) {
                    stat.incrIndexed();
                } else {
                    stat.incrUnchanged();
                }
            } catch (Exception e) {
                stat.incrFailed();
                log.error("RAG 回填失败, docType: {}, sourceId: {}", docType, id, e);
            }
        }
        return stat;
    }

    /**
     * 按切片数量删除某来源全部切片（先于写入执行，保证幂等）
     */
    private void deleteChunks(String docType, Long sourceId, int count) {
        List<String> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            ids.add(RagDocumentFactory.chunkId(docType, sourceId, i));
        }
        if (!ids.isEmpty()) {
            vectorStore.delete(ids);
        }
    }

    /**
     * 分批写入，避免超出 embedding 接口单批上限
     */
    private void addInBatch(List<Document> documents) {
        int size = Math.max(1, ragProperties.getEmbeddingBatchSize());
        for (int i = 0; i < documents.size(); i += size) {
            List<Document> batch = documents.subList(i, Math.min(i + size, documents.size()));
            vectorStore.add(batch);
        }
    }

    private String hashOf(List<Document> documents) {
        if (documents.isEmpty()) {
            return "";
        }
        Object hash = documents.get(0).getMetadata().get("contentHash");
        return hash == null ? "" : String.valueOf(hash);
    }

    private RagSourceIndex findIndex(String docType, Long sourceId) {
        return indexMapper.selectOne(new LambdaQueryWrapper<RagSourceIndex>()
                .eq(RagSourceIndex::getDocType, docType)
                .eq(RagSourceIndex::getSourceId, sourceId)
                .last("limit 1"));
    }

    private void upsert(String docType, Long sourceId, String hash, int chunkCount,
                        String status, String errorMsg, RagSourceIndex existed) {
        LocalDateTime now = LocalDateTime.now();
        if (existed == null) {
            RagSourceIndex record = new RagSourceIndex()
                    .setDocType(docType)
                    .setSourceId(sourceId)
                    .setContentHash(hash)
                    .setChunkCount(chunkCount)
                    .setStatus(status)
                    .setErrorMsg(errorMsg)
                    .setRetryCount(0)
                    .setCreateTime(now)
                    .setUpdateTime(now);
            indexMapper.insert(record);
            return;
        }
        existed.setContentHash(hash)
                .setChunkCount(chunkCount)
                .setStatus(status)
                .setErrorMsg(errorMsg)
                .setRetryCount(0)
                .setUpdateTime(now);
        indexMapper.updateById(existed);
    }

    private void markFailed(String docType, Long sourceId, String message) {
        RagSourceIndex existed = findIndex(docType, sourceId);
        LocalDateTime now = LocalDateTime.now();
        if (existed == null) {
            indexMapper.insert(new RagSourceIndex()
                    .setDocType(docType)
                    .setSourceId(sourceId)
                    .setContentHash("")
                    .setChunkCount(0)
                    .setStatus(RagDocType.STATUS_FAILED)
                    .setErrorMsg(truncate(message))
                    .setRetryCount(1)
                    .setCreateTime(now)
                    .setUpdateTime(now));
            return;
        }
        existed.setStatus(RagDocType.STATUS_FAILED)
                .setErrorMsg(truncate(message))
                .setRetryCount((existed.getRetryCount() == null ? 0 : existed.getRetryCount()) + 1)
                .setUpdateTime(now);
        indexMapper.updateById(existed);
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
