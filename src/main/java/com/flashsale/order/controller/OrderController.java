package com.flashsale.order.controller;

import com.flashsale.common.ApiResponse;
import com.flashsale.order.entity.FlashSaleOrder;
import com.flashsale.order.service.OrderCreationService;
import com.flashsale.order.vo.OrderView;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 查询 RabbitMQ 异步成单结果。 */
@RestController
@RequestMapping("/api/orders")
@Validated
public class OrderController {
    private final OrderCreationService orderCreationService;

    public OrderController(OrderCreationService orderCreationService) {
        this.orderCreationService = orderCreationService;
    }

    /** 未消费完成时 data 为 null；客户端可使用同一 requestId 短暂轮询。 */
    @GetMapping("/by-request/{requestId}")
    public ApiResponse<OrderView> findByRequest(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9._-]{8,64}") String requestId,
            @RequestHeader("X-User-Id") @Positive long userId
    ) {
        FlashSaleOrder order = orderCreationService.findByRequestId(requestId);
        if (order == null || order.getUserId() != userId) {
            return ApiResponse.success(null);
        }
        return ApiResponse.success(OrderView.from(order));
    }
}
