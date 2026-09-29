package com.wtlx.utils;

public class RabbitMQConstants {

    public static final String THUMBS_UP_EXCHANGE = "thumbs.up.exchange";
    public static final String THUMBS_UP_QUEUE = "thumbs.up.queue";
    public static final String THUMBS_UP_ROUTING_KEY = "thumbs.up";

    public static final String ORDER_EXCHANGE = "order.exchange";
    public static final String ORDER_QUEUE = "order.queue";
    public static final String ORDER_ROUTING_KEY = "order";

    private RabbitMQConstants() {
    }
}
