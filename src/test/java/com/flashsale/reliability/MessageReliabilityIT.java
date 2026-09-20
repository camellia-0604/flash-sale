package com.flashsale.reliability;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flashsale.activity.entity.ActivityStatus;
import com.flashsale.activity.entity.FlashSaleActivity;
import com.flashsale.activity.mapper.FlashSaleActivityMapper;
import com.flashsale.config.RabbitMqConfig;
import com.flashsale.order.entity.FlashSaleOrder;
import com.flashsale.order.mapper.FlashSaleOrderMapper;
import com.flashsale.order.messaging.OrderCreationMessage;
import com.flashsale.reliability.entity.MessageFailure;
import com.flashsale.reliability.mapper.MessageFailureMapper;
import com.flashsale.reliability.service.MessageFailureService;
import com.flashsale.reservation.FlashSaleInventoryService;
import com.flashsale.reservation.FlashSaleRedisKeys;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** 使用真实三组件验证“消费重试耗尽 → 死信 → 失败记录 → Redis 幂等补偿”。 */
@SpringBootTest
class MessageReliabilityIT {
    @Autowired
    private FlashSaleInventoryService inventoryService;
    @Autowired
    private FlashSaleActivityMapper activityMapper;
    @Autowired
    private FlashSaleOrderMapper orderMapper;
    @Autowired
    private MessageFailureMapper failureMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    private long activityId;
    private final String requestId = "day04-dead-letter-test";

    @BeforeEach
    void createIsolatedActivityAndReservation() {
        FlashSaleActivity activity = new FlashSaleActivity();
        activity.setTitle("Day 04 failure injection");
        activity.setSkuCode("IT-DLQ-" + UUID.randomUUID());
        activity.setOriginalPrice(new BigDecimal("99.00"));
        activity.setFlashPrice(new BigDecimal("39.90"));
        activity.setTotalStock(1);
        activity.setAvailableStock(1);
        activity.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        activity.setEndTime(LocalDateTime.of(2030, 1, 1, 0, 0));
        activity.setStatus(ActivityStatus.ONLINE);
        activity.setVersion(0);
        assertThat(activityMapper.insert(activity)).isEqualTo(1);
        activityId = activity.getId();
        inventoryService.warmUpActivity(activity);
        assertThat(inventoryService.reserve(activityId, 9L, requestId).accepted()).isTrue();

        // 故障注入：Redis 已预扣，但把 MySQL 最终库存改为 0，迫使消费者连续失败。
        activityMapper.update(
                Wrappers.<FlashSaleActivity>lambdaUpdate()
                        .eq(FlashSaleActivity::getId, activityId)
                        .set(FlashSaleActivity::getAvailableStock, 0)
        );
    }

    @AfterEach
    void removeOnlyTestData() {
        failureMapper.delete(Wrappers.<MessageFailure>lambdaQuery()
                .eq(MessageFailure::getRequestId, requestId));
        orderMapper.delete(Wrappers.<FlashSaleOrder>lambdaQuery()
                .eq(FlashSaleOrder::getActivityId, activityId));
        activityMapper.deleteById(activityId);
        redisTemplate.delete(List.of(
                FlashSaleRedisKeys.state(activityId),
                FlashSaleRedisKeys.buyers(activityId)
        ));
    }

    @Test
    void exhaustedRetriesShouldDeadLetterAndCompensateRedis() {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.ORDER_EXCHANGE,
                RabbitMqConfig.ORDER_ROUTING_KEY,
                new OrderCreationMessage(activityId, 9L, requestId)
        );

        Awaitility.await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
            MessageFailure failure = failureMapper.selectOne(
                    Wrappers.<MessageFailure>lambdaQuery()
                            .eq(MessageFailure::getRequestId, requestId)
            );
            assertThat(failure).isNotNull();
            assertThat(failure.getStage()).isEqualTo("CONSUME");
            assertThat(failure.getStatus()).isEqualTo(MessageFailureService.COMPENSATED);
            assertThat(orderMapper.selectCount(Wrappers.<FlashSaleOrder>lambdaQuery()
                    .eq(FlashSaleOrder::getActivityId, activityId))).isZero();
            assertThat(redisTemplate.opsForHash().get(
                    FlashSaleRedisKeys.state(activityId), "stock"
            )).isEqualTo("1");
            assertThat(redisTemplate.opsForHash().hasKey(
                    FlashSaleRedisKeys.buyers(activityId), "9"
            )).isFalse();
        });
    }
}
