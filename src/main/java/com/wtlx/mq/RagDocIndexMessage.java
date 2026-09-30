package com.wtlx.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * RAG 文档索引消息：来源内容发生变化时投递，驱动切片与向量入库。
 *
 * @author 金小梁
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RagDocIndexMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 事件类型：PUBLISHED=发布/更新，DELETED=删除
     */
    private String eventType;

    /**
     * 来源类型：BLOG / GUIDE
     */
    private String docType;

    /**
     * 来源业务主键
     */
    private Long sourceId;

    /**
     * 内容指纹快照（可为空，消费者侧会重新计算并比对）
     */
    private String contentHash;

    /**
     * 事件发生时间（毫秒时间戳）
     */
    private Long occurredAt;

    public RagDocIndexMessage(String eventType, String docType, Long sourceId) {
        this(eventType, docType, sourceId, null, System.currentTimeMillis());
    }
}
