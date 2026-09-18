package com.flashsale.activity.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 当前可参与秒杀的活动视图。
 *
 * @param remainingStock Day 1 为 MySQL 库存；Day 2 后改为 Redis 可抢库存快照
 */
public record ActivityView(
        Long id,
        String title,
        String skuCode,
        BigDecimal originalPrice,
        BigDecimal flashPrice,
        Integer remainingStock,
        LocalDateTime startTime,
        LocalDateTime endTime
) {
}
