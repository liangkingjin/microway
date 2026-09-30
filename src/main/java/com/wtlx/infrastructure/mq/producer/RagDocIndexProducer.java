package com.wtlx.infrastructure.mq.producer;

import com.wtlx.infrastructure.mq.RabbitMQConstants;
import com.wtlx.infrastructure.mq.message.RagDocIndexMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * RAG 文档索引消息生产者。
 * <p>
 * 投递时机是这里唯一的业务要点：必须等本地事务提交后再发，
 * 否则会出现"消息已发出但事务回滚"，向量库里留下并不存在的来源。
 * 无事务时直接发送。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class RagDocIndexProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 事务提交后投递索引消息；当前无事务则立即投递
     *
     * @param message 索引消息
     */
    public void sendAfterCommit(RagDocIndexMessage message) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    doSend(message);
                }
            });
            return;
        }
        doSend(message);
    }

    private void doSend(RagDocIndexMessage message) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.RAG_DOC_EXCHANGE,
                RabbitMQConstants.RAG_DOC_ROUTING_KEY, message);
    }
}
