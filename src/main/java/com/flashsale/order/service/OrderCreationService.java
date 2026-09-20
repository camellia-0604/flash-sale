package com.flashsale.order.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flashsale.activity.entity.FlashSaleActivity;
import com.flashsale.activity.mapper.FlashSaleActivityMapper;
import com.flashsale.order.entity.FlashSaleOrder;
import com.flashsale.order.mapper.FlashSaleOrderMapper;
import com.flashsale.order.messaging.OrderCreationMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 消费消息后创建 MySQL 订单的事务边界。
 *
 * <p>先查幂等结果，再条件扣减库存并插入订单。统一先锁活动行，避免多个消费者以
 * 不同订单索引锁与活动行锁形成反向等待。订单唯一键处理重复消息，库存 SQL
 * 处理 Redis 与 MySQL 极端不一致时的最终拒绝；任一步失败都会回滚整个事务。</p>
 */
@Service
public class OrderCreationService {
    private final FlashSaleOrderMapper orderMapper;
    private final FlashSaleActivityMapper activityMapper;

    public OrderCreationService(FlashSaleOrderMapper orderMapper,
                                FlashSaleActivityMapper activityMapper) {
        this.orderMapper = orderMapper;
        this.activityMapper = activityMapper;
    }

    @Transactional
    public void createOrder(OrderCreationMessage message) {
        if (findByRequestId(message.requestId()) != null
                || findByUserAndActivity(message.userId(), message.activityId()) != null) {
            return;
        }

        FlashSaleActivity activity = activityMapper.selectById(message.activityId());
        if (activity == null) {
            throw new IllegalStateException("Flash-sale activity does not exist: " + message.activityId());
        }

        FlashSaleOrder order = new FlashSaleOrder();
        order.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        order.setRequestId(message.requestId());
        order.setActivityId(message.activityId());
        order.setUserId(message.userId());
        order.setSkuCode(activity.getSkuCode());
        order.setQuantity(1);
        order.setAmount(activity.getFlashPrice());
        order.setStatus("CREATED");
        if (activityMapper.deductAvailableStock(message.activityId()) != 1) {
            throw new IllegalStateException("MySQL flash-sale stock is unavailable");
        }
        // 唯一键冲突会抛异常并回滚前面的库存扣减，重复消息不能消耗第二件库存。
        if (orderMapper.insert(order) != 1) {
            throw new IllegalStateException("Flash-sale order insert affected an unexpected row count");
        }
    }

    public FlashSaleOrder findByRequestId(String requestId) {
        return orderMapper.selectOne(Wrappers.<FlashSaleOrder>lambdaQuery()
                .eq(FlashSaleOrder::getRequestId, requestId));
    }

    private FlashSaleOrder findByUserAndActivity(long userId, long activityId) {
        return orderMapper.selectOne(Wrappers.<FlashSaleOrder>lambdaQuery()
                .eq(FlashSaleOrder::getUserId, userId)
                .eq(FlashSaleOrder::getActivityId, activityId));
    }
}
