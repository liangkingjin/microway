package com.wtlx.infrastructure.mq.producer;

import com.wtlx.infrastructure.mq.RabbitMQConstants;
import com.wtlx.infrastructure.mq.message.OrderMessage;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 订单消息生产者：秒杀下单走消息异步落库，削峰保护数据库。
 *
 * @author 金小梁
 */
@Slf4j
@Component
public class OrderProducer {

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 投递订单创建消息
     *
     * @param message 订单消息
     */
    public void sendOrderCreated(OrderMessage message) {
        rabbitTemplate.convertAndSend(RabbitMQConstants.ORDER_EXCHANGE,
                RabbitMQConstants.ORDER_ROUTING_KEY, message);
    }

}
