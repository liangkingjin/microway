package com.wtlx.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wtlx.entity.dto.Result;
import com.wtlx.entity.Orders;
import com.wtlx.entity.ScenicSpots;
import com.wtlx.entity.ScenicSeckill;
import com.wtlx.mapper.OrdersMapper;
import com.wtlx.mapper.ScenicSeckillMapper;
import com.wtlx.mq.OrderMessage;
import com.wtlx.service.IOrdersService;
import com.wtlx.service.IScenicSpotsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wtlx.utils.RateLimiter;
import com.wtlx.utils.RabbitMQConstants;
import com.wtlx.utils.RedisConstants;
import com.wtlx.utils.RedisIdWorker;
import com.wtlx.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

/**
 * 订单表服务实现类
 * 包含秒杀门票的核心逻辑：
 * 1. 库存预热至Redis
 * 2. Lua脚本保证原子性的库存扣减和一人一单校验
 * 3. 令牌桶算法实现流量控制
 * 4. 异步线程池处理订单创建
 *
 * @author 金小梁
 * @since 2025-04-03
 */
@Slf4j
@Service
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersService {

    /**
     * 默认的令牌桶容量（同时允许通过的并发请求数）
     */
    private static final double DEFAULT_RATE_LIMIT = 1000;

    /**
     * 注入StringRedisTemplate，用于操作Redis
     */
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 注入RedisIdWorker，用于生成唯一订单号
     */
    @Autowired
    private RedisIdWorker redisIdWorker;

    /**
     * 注入景点服务，用于查询景点信息
     */
    @Resource
    private IScenicSpotsService scenicSpotsService;

    @Resource
    private ScenicSeckillMapper scenicSeckillMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    /**
     * Lua脚本对象，用于执行原子性的秒杀逻辑
     * 脚本位置：resources/lua/seckill.lua
     */
    private DefaultRedisScript<Long> seckillScript;



    /**
     * 初始化方法，在bean创建后执行
     * 1. 加载Lua脚本
     * 2. 预热门票库存到Redis
     */
    @PostConstruct
     public void init() {

         // 初始化Lua脚本
         seckillScript = new DefaultRedisScript<>();
         seckillScript.setLocation(new ClassPathResource("lua/seckill.lua"));
         seckillScript.setResultType(Long.class);

         // 预热所有景点的库存到Redis
         warmUpAllStocks();
     }

     /**
      * 预热所有景点的门票库存到Redis
      * 启动时自动将数据库中的门票秒杀库存同步到Redis
      */
     private void warmUpAllStocks() {
        log.info("开始预热门票库存到Redis...");
        // 查询所有门票秒杀库存
        List<ScenicSeckill> seckillList = scenicSeckillMapper.selectList(null);
        if (CollUtil.isEmpty(seckillList)) {
            log.warn("未找到门票秒杀库存信息");
            return;
        }
        // 遍历秒杀库存，将库存和限流器写入Redis
        for (ScenicSeckill seckill : seckillList) {
            // 设置库存key
            String stockKey = RedisConstants.STOCK_SCENIC_KEY + seckill.getScenicId() + ":default";
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(seckill.getStock()));

            log.info("景点[{}]库存预热完成，库存: {}", seckill.getScenicId(), seckill.getStock());
        }
    }

    /**
     * 秒杀门票核心方法
     * 流程：限流检查 -> Lua脚本校验库存和一人一单 -> 异步创建订单
     *
     * @param scenicId   景点ID
     * @param visitDate  游玩日期
     * @param quantity   购买数量
     * @return Result 秒杀结果
     */
    @Override
    public Result seckill(Long scenicId, String visitDate, Integer quantity) {
        // 1. 获取当前登录用户ID
        Long userId = UserHolder.getUser().getId();

        // 2. 令牌桶限流检查
        // 使用景点ID作为key获取对应的限流器
        RateLimiter rateLimiter = RateLimiter.getInstance(String.valueOf(scenicId), DEFAULT_RATE_LIMIT);
        if (!rateLimiter.tryAcquire()) {
            return Result.fail("系统繁忙，请稍后重试");
        }

        // 3. 查询景点信息
        ScenicSpots scenic = scenicSpotsService.getById(scenicId);
        if (scenic == null) {
            return Result.fail("景点不存在");
        }

        // 4. 构造Redis key
        String stockKey = RedisConstants.STOCK_SCENIC_KEY + scenicId + ":default";
        String orderKey = RedisConstants.ORDER_SCENIC_KEY + scenicId + ":default";

        // 5. 执行Lua脚本进行原子性校验和库存扣减
        // 返回值：1=成功，-1=库存不足，-2=已购买
        Long result = stringRedisTemplate.execute(
                seckillScript,
                Arrays.asList(stockKey, orderKey),
                String.valueOf(quantity),
                String.valueOf(userId)
        );

        // 6. 处理Lua脚本返回结果
        if (result == null || result == -1) {
            return Result.fail("库存不足");
        }
        if (result == -2) {
            return Result.fail("您已购买过该景点门票");
        }

        // 7. 构建订单对象
        Orders orders = new Orders();
        orders.setScenicId(scenicId);
        orders.setScenicName(scenic.getName());
        orders.setVisitDate(visitDate);
        orders.setTickets(scenic.getTicketInfo());
        orders.setTotalPrice(scenic.getPrice().multiply(new BigDecimal(quantity)));
        orders.setTotalQuantity(quantity);
        orders.setOrderStatus(1);
        orders.setUserId(userId);
        orders.setOrderTime(LocalDateTime.now());

        OrderMessage message = new OrderMessage();
        message.setScenicId(scenicId);
        message.setScenicName(scenic.getName());
        message.setVisitDate(visitDate);
        message.setTickets(scenic.getTicketInfo());
        message.setQuantity(quantity);
        message.setTotalPrice(scenic.getPrice().multiply(new BigDecimal(quantity)));
        message.setUserId(userId);

        rabbitTemplate.convertAndSend(RabbitMQConstants.ORDER_EXCHANGE, RabbitMQConstants.ORDER_ROUTING_KEY, message);

        return Result.ok("秒杀成功，订单已创建");
    }

    /**
     * 购买门票（普通模式，非秒杀）
     *
     * @param orders 订单信息
     * @return Result
     */
    @Override
    public Result insert(Orders orders) {
        // 获取当前用户ID
        Long userId = UserHolder.getUser().getId();
        // 设置订单状态为已支付
        orders.setOrderStatus(1);
        // 设置用户ID
        orders.setUserId(userId);
        // 生成订单号（基于时间戳）
        LocalDateTime now = LocalDateTime.now();
        long nowEpochSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowEpochSecond - 1640995200L;
        orders.setOrderNo(String.valueOf(timestamp));
        // 保存订单
        save(orders);
        return Result.ok();
    }

    /**
     * 分页查询用户门票订单
     *
     * @param page 页码
     * @param size 每页数量
     * @return Result
     */
    @Override
    public Result queryUserTicketOrders(Integer page, Integer size) {
        // 1.获取当前用户ID
        Long userId = UserHolder.getUser().getId();

        // 2.构建分页查询条件
        Page<Orders> p = new Page<>(page, size);
        LambdaQueryWrapper<Orders> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Orders::getUserId, userId)
                .orderByDesc(Orders::getOrderTime);

        // 3.执行查询并返回结果
        Page<Orders> result = this.page(p, wrapper);
        return Result.ok(result.getRecords());
    }

    /**
     * 取消门票订单
     *
     * @param id 订单ID
     * @return Result
     */
    @Override
    @Transactional
    public Result cancelTicketOrder(Long id) {
        // 1.查询订单是否存在
        Orders order = this.getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 2.检查订单状态，只有待支付状态(0)可以取消
        if (order.getOrderStatus() != 0) {
            return Result.fail("当前状态不可取消");
        }

        // 3.更新订单状态为已取消(2)
        order.setOrderStatus(2);
        return this.updateById(order) ? Result.ok() : Result.fail("取消失败");
    }

    /**
     * 获取订单详情
     *
     * @param id 订单ID
     * @return Result
     */
    @Override
    public Result getOrderDetail(Long id) {
        // 1.获取当前用户ID
        Long userId = UserHolder.getUser().getId();

        // 2.查询订单
        Orders order = this.getById(id);
        if (order == null) {
            return Result.fail("订单不存在");
        }

        // 3.验证订单归属
        if (!order.getUserId().equals(userId)) {
            return Result.fail("无权查看此订单");
        }

        // 4.返回订单数据
        return Result.ok(order);
    }

}
