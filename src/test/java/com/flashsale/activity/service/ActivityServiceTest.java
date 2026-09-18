package com.flashsale.activity.service;

import com.flashsale.activity.entity.FlashSaleActivity;
import com.flashsale.activity.mapper.FlashSaleActivityMapper;
import com.flashsale.reservation.FlashSaleInventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** 秒杀活动时间查询和公开字段映射测试。 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {
    @Mock
    private FlashSaleActivityMapper activityMapper;
    @Mock
    private FlashSaleInventoryService inventoryService;

    /** Mapper 返回的在线活动应被稳定映射成公开视图。 */
    @Test
    void shouldReturnActiveActivityView() {
        FlashSaleActivity activity = activity();
        when(activityMapper.selectList(any())).thenReturn(List.of(activity));
        Clock clock = Clock.fixed(
                Instant.parse("2026-09-16T12:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );

        when(inventoryService.currentStockOrDatabase(activity)).thenReturn(100);
        var result = new ActivityService(activityMapper, clock, inventoryService).listActive();

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.id()).isEqualTo(1L);
            assertThat(view.flashPrice()).isEqualByComparingTo("59.90");
            assertThat(view.remainingStock()).isEqualTo(100);
        });
    }

    private FlashSaleActivity activity() {
        FlashSaleActivity activity = new FlashSaleActivity();
        activity.setId(1L);
        activity.setTitle("Day 1 演示活动");
        activity.setSkuCode("DEMO-SKU-001");
        activity.setOriginalPrice(new BigDecimal("199.00"));
        activity.setFlashPrice(new BigDecimal("59.90"));
        activity.setAvailableStock(100);
        activity.setStartTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        activity.setEndTime(LocalDateTime.of(2030, 1, 1, 0, 0));
        return activity;
    }
}
