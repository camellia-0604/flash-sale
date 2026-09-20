package com.flashsale.order.vo;

import com.flashsale.order.entity.FlashSaleOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 用于轮询异步下单结果的只读视图。 */
public record OrderView(
        String orderNo,
        String requestId,
        long activityId,
        long userId,
        String skuCode,
        BigDecimal amount,
        String status,
        LocalDateTime createdAt
) {
    public static OrderView from(FlashSaleOrder order) {
        return new OrderView(
                order.getOrderNo(), order.getRequestId(), order.getActivityId(), order.getUserId(),
                order.getSkuCode(), order.getAmount(), order.getStatus(), order.getCreatedAt()
        );
    }
}
