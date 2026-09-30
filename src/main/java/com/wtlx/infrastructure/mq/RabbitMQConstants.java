package com.wtlx.infrastructure.mq;

public class RabbitMQConstants {

    public static final String THUMBS_UP_EXCHANGE = "thumbs.up.exchange";
    public static final String THUMBS_UP_QUEUE = "thumbs.up.queue";
    public static final String THUMBS_UP_ROUTING_KEY = "thumbs.up";

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_QUEUE = "order.queue";
    public static final String ORDER_ROUTING_KEY = "order";

    /**
     * RAG 文档索引交换机：游记发布 / 攻略变更后触发切片与向量入库
     */
    public static final String RAG_DOC_EXCHANGE = "rag.doc.exchange";

    public static final String RAG_DOC_QUEUE = "rag.doc.index.queue";

    public static final String RAG_DOC_ROUTING_KEY = "rag.doc.index";

    private RabbitMQConstants() {
    }
}
