package com.flashsale.reservation;

import com.flashsale.activity.entity.FlashSaleActivity;
import com.flashsale.activity.mapper.FlashSaleActivityMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 使用真实 Redis 验证 Lua 原子性。默认 mvn test 不执行 IT，交付验证显式运行本类。
 */
@SpringBootTest
class FlashSaleReservationIT {
    private static final long ACTIVITY_ID = 1L;

    @Autowired
    private FlashSaleInventoryService inventoryService;
    @Autowired
    private FlashSaleActivityMapper activityMapper;
    @Autowired
    private StringRedisTemplate redisTemplate;

    /** 每个场景从 MySQL 的 100 件基线重新预热，避免依赖上一次运行结果。 */
    @BeforeEach
    void prepareInventory() {
        clearRedisKeys();
        FlashSaleActivity activity = activityMapper.selectById(ACTIVITY_ID);
        inventoryService.warmUpActivity(activity);
    }

    @AfterEach
    void clearRedisKeys() {
        redisTemplate.delete(List.of(
                FlashSaleRedisKeys.state(ACTIVITY_ID),
                FlashSaleRedisKeys.buyers(ACTIVITY_ID)
        ));
    }

    /** 160 个不同用户争抢 100 件库存，只能成功 100 次且库存不能为负。 */
    @Test
    void concurrentReservationsShouldNeverOversell() throws Exception {
        int requestCount = 160;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(32);
        List<Future<ReservationResult>> futures = new ArrayList<>();
        try {
            for (long userId = 1; userId <= requestCount; userId++) {
                long currentUser = userId;
                futures.add(executor.submit(() -> {
                    start.await();
                    return inventoryService.reserve(
                            ACTIVITY_ID, currentUser, "concurrent-" + currentUser
                    );
                }));
            }
            start.countDown();

            List<ReservationResult> results = new ArrayList<>();
            for (Future<ReservationResult> future : futures) {
                results.add(future.get());
            }
            List<Long> acceptedUsers = new ArrayList<>();
            for (int index = 0; index < results.size(); index++) {
                if (results.get(index).accepted()) {
                    acceptedUsers.add((long) index + 1);
                }
            }

            assertThat(acceptedUsers).hasSize(100);
            assertThat(results).filteredOn(result -> result.code().equals("SOLD_OUT"))
                    .hasSize(60);
            assertThat(redisTemplate.opsForHash().get(
                    FlashSaleRedisKeys.state(ACTIVITY_ID), "stock"
            )).isEqualTo("0");
            assertThat(redisTemplate.opsForHash().size(
                    FlashSaleRedisKeys.buyers(ACTIVITY_ID)
            )).isEqualTo(100);

            long acceptedUser = acceptedUsers.get(0);
            ReservationResult duplicate = inventoryService.reserve(
                    ACTIVITY_ID, acceptedUser, "retry-request-0001"
            );
            assertThat(duplicate.code()).isEqualTo("DUPLICATE");
        } finally {
            executor.shutdownNow();
        }
    }
}
