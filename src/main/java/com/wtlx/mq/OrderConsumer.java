package com.wtlx.mq;

import com.wtlx.entity.Orders;
import com.wtlx.mapper.OrdersMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Component
public class OrderConsumer {

    @Resource
    private OrdersMapper ordersMapper;

    @RabbitListener(queues = "order.queue")
    public void handleOrderMessage(OrderMessage message) {
        try {
            Orders orders = new Orders();
            orders.setOrderNo(String.valueOf(System.currentTimeMillis()));
            orders.setScenicId(message.getScenicId());
            orders.setScenicName(message.getScenicName());
            orders.setVisitDate(message.getVisitDate());
            orders.setTickets(message.getTickets());
            orders.setTotalQuantity(message.getQuantity());
            orders.setTotalPrice(message.getTotalPrice());
            orders.setUserId(message.getUserId());
            orders.setOrderStatus(1);
            orders.setOrderTime(LocalDateTime.now());

            ordersMapper.insert(orders);
            log.info("MQ订单创建成功: {}", orders.getOrderNo());
        } catch (Exception e) {
            log.error("MQ订单创建失败: {}", e.getMessage());
        }
    }
}
