package com.wtlx.infrastructure.mq.consumer;

import com.wtlx.infrastructure.mq.message.RagDocIndexMessage;
import com.wtlx.infrastructure.rag.RagDocType;
import com.wtlx.infrastructure.rag.loader.RagIndexService;
import com.wtlx.infrastructure.rag.config.RagProperties;
import com.wtlx.infrastructure.mq.RabbitMQConstants;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RAG 文档索引消费者：监听内容变更事件，自动切片并写入向量库。
 * <p>
 * 幂等与可靠性：
 * <ol>
 *     <li>切片 id 是确定性 UUID，重复消费等于覆盖写入，不会产生重复数据；</li>
 *     <li>索引表记录内容指纹，指纹一致且已成功时直接跳过，省掉一次 embedding 调用；</li>
 *     <li>处理失败会记录 FAILED 状态与原因后抛出异常，交由 RabbitMQ 重试（配置 3 次）；
 *         最终仍失败的数据可通过管理接口 {@code /ai/rag/reindex} 手动补偿。</li>
 * </ol>
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class RagDocIndexConsumer {

    @Resource
    private RagIndexService ragIndexService;

    @Resource
    private RagProperties ragProperties;

    @RabbitListener(queues = RabbitMQConstants.RAG_DOC_QUEUE)
    public void handleRagDocIndex(RagDocIndexMessage message) {
        if (message == null || message.getDocType() == null || message.getSourceId() == null) {
            log.warn("RAG 索引消息不完整，已丢弃: {}", message);
            return;
        }
        if (!ragProperties.isEnabled()) {
            log.debug("RAG 未启用，跳过索引消息: {}", message);
            return;
        }
        String docType = message.getDocType();
        Long sourceId = message.getSourceId();
        if (!supported(docType)) {
            log.warn("RAG 索引消息来源类型不支持，已丢弃: {}", docType);
            return;
        }

        try {
            if ("DELETED".equalsIgnoreCase(message.getEventType())) {
                ragIndexService.remove(docType, sourceId);
                log.info("RAG 删除索引完成, docType: {}, sourceId: {}", docType, sourceId);
                return;
            }
            RagIndexService.RagIndexResult result = ragIndexService.index(docType, sourceId);
            log.info("RAG 索引消费完成, docType: {}, sourceId: {}, 结果: {}, 切片数: {}",
                    docType, sourceId, result.getStatus(), result.getChunkCount());
        } catch (Exception e) {
            log.error("RAG 索引消费失败，等待重试, docType: {}, sourceId: {}, eventType: {}",
                    docType, sourceId, message.getEventType(), e);
            // 抛出以触发 RabbitMQ 重试；最终失败后由管理接口补偿
            throw new IllegalStateException("RAG 索引消费失败: " + e.getMessage(), e);
        }
    }

    /**
     * 校验来源类型是否合法
     */
    private boolean supported(String docType) {
        return RagDocType.BLOG.equals(docType) || RagDocType.GUIDE.equals(docType);
    }
}
