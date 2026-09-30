package com.wtlx.infrastructure.mq.config;

import com.wtlx.infrastructure.mq.RabbitMQConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public TopicExchange thumbsUpExchange() {
        return new TopicExchange(RabbitMQConstants.THUMBS_UP_EXCHANGE);
    }

    @Bean
    public Queue thumbsUpQueue() {
        return new Queue(RabbitMQConstants.THUMBS_UP_QUEUE, true);
    }

    @Bean
    public Binding thumbsUpBinding(Queue thumbsUpQueue, TopicExchange thumbsUpExchange) {
        return BindingBuilder.bind(thumbsUpQueue).to(thumbsUpExchange).with(RabbitMQConstants.THUMBS_UP_ROUTING_KEY);
    }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(RabbitMQConstants.ORDER_EXCHANGE);
    }

    @Bean
    public Queue orderQueue() {
        return new Queue(RabbitMQConstants.ORDER_QUEUE, true);
    }

    @Bean
    public Binding orderBinding(Queue orderQueue, TopicExchange orderExchange) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with(RabbitMQConstants.ORDER_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RAG 文档索引队列与绑定：游记发布事件驱动切片入库
     */
    @Bean
    public TopicExchange ragDocExchange() {
        return new TopicExchange(RabbitMQConstants.RAG_DOC_EXCHANGE);
    }

    @Bean
    public Queue ragDocQueue() {
        return new Queue(RabbitMQConstants.RAG_DOC_QUEUE, true);
    }

    @Bean
    public Binding ragDocBinding(Queue ragDocQueue, TopicExchange ragDocExchange) {
        return BindingBuilder.bind(ragDocQueue).to(ragDocExchange).with(RabbitMQConstants.RAG_DOC_ROUTING_KEY);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        return factory;
    }
}
