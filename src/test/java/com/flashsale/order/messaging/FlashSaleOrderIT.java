package com.flashsale.order.messaging;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flashsale.activity.entity.FlashSaleActivity;
import com.flashsale.activity.mapper.FlashSaleActivityMapper;
import com.flashsale.activity.entity.ActivityStatus;
import com.flashsale.config.RabbitMqConfig;
import com.flashsale.order.entity.FlashSaleOrder;
import com.flashsale.order.mapper.FlashSaleOrderMapper;
import com.flashsale.reservation.FlashSaleInventoryService;
import com.flashsale.reservation.FlashSaleRedisKeys;
import com.flashsale.reservation.FlashSaleReservationService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用真实 Redis、RabbitMQ 与 MySQL 验证异步成单闭环。
 *
 * <p>类名以 IT 结尾，默认 mvn test 不执行；交付验证显式选择本类，避免普通单测依赖容器。</p>
 */
@SpringBootTest
class FlashSaleOrderIT {
    @Autowired
    private FlashSaleReservationService reservationService;
    @Autowired
    private FlashSaleInventoryService inventoryService;
    @Autowired
    private FlashSaleActivityMapper activityMapper;
    @Autowired
    private FlashSaleOrderMapper orderMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private long activityId;

    @BeforeEach
    void createIsolatedActivity() {
        FlashSaleActivity activity = new FlashSaleActivity();
        activity.setTitle("Day 03 integration test");
        activity.setSkuCode("IT-" + UUID.randomUUID());
        activity.setOriginalPrice(new BigDecimal("99.00"));
        activity.setFlashPrice(new BigDecimal("39.90"));
        activity.setTotalStock(20);
        activity.setAvailableStock(20);
        activity.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        activity.setEndTime(LocalDateTime.of(2030, 1, 1, 0, 0));
        activity.setStatus(ActivityStatus.ONLINE);
        activity.setVersion(0);
        assertThat(activityMapper.insert(activity)).isEqualTo(1);
        activityId = activity.getId();
        inventoryService.warmUpActivity(activity);
    }

    @AfterEach
    void removeIsolatedActivity() {
        orderMapper.delete(Wrappers.<FlashSaleOrder>lambdaQuery()
                .eq(FlashSaleOrder::getActivityId, activityId));
        activityMapper.deleteById(activityId);
        redisTemplate.delete(List.of(
                FlashSaleRedisKeys.state(activityId),
                FlashSaleRedisKeys.buyers(activityId)
        ));
    }

    /** 20 次资格成功应异步形成 20 张订单，并只扣减 20 件 MySQL 库存。 */
    @Test
    void acceptedReservationsShouldBecomeIdempotentDatabaseOrders() {
        for (long userId = 1; userId <= 20; userId++) {
            assertThat(reservationService.reserve(
                    activityId, userId, "day03-request-" + userId
            ).accepted()).isTrue();
        }

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            assertThat(orderMapper.selectCount(Wrappers.<FlashSaleOrder>lambdaQuery()
                    .eq(FlashSaleOrder::getActivityId, activityId))).isEqualTo(20);
            assertThat(activityMapper.selectById(activityId).getAvailableStock()).isZero();
        });

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ORDER_EXCHANGE,
                RabbitMqConfig.ORDER_ROUTING_KEY,
                new OrderCreationMessage(activityId, 1L, "day03-request-1")
        );

        Awaitility.await().during(Duration.ofSeconds(1)).atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(orderMapper.selectCount(Wrappers.<FlashSaleOrder>lambdaQuery()
                            .eq(FlashSaleOrder::getActivityId, activityId))).isEqualTo(20);
                    assertThat(activityMapper.selectById(activityId).getAvailableStock()).isZero();
                });
    }
}
