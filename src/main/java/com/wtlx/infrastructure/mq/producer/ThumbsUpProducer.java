package com.wtlx.infrastructure.mq.producer;

import com.wtlx.infrastructure.mq.RabbitMQConstants;
import com.wtlx.infrastructure.mq.message.ThumbsUpMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 点赞消息生产者。
 * <p>
 * 点赞/取消点赞只写 Redis，业务表计数由消费端异步落库，故此处只负责投递。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class ThumbsUpProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 投递点赞变更消息
     *
     * @param type   业务类型
     * @param bizId  实体 ID
     * @param isLike true=点赞，false=取消点赞
     */
    public void sendThumbChange(String type, Long bizId, boolean isLike) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.THUMBS_UP_EXCHANGE,
                RabbitMQConstants.THUMBS_UP_ROUTING_KEY,
                new ThumbsUpMessage(type, bizId, isLike));
    }
}
