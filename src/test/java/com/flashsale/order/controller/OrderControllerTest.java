package com.flashsale.order.controller;

import com.flashsale.order.entity.FlashSaleOrder;
import com.flashsale.order.service.OrderCreationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 异步订单轮询接口切片测试。 */
@WebMvcTest(OrderController.class)
class OrderControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private OrderCreationService orderCreationService;

    @Test
    void ownerShouldReadCreatedOrderByRequestId() throws Exception {
        FlashSaleOrder order = new FlashSaleOrder();
        order.setOrderNo("0123456789abcdef0123456789abcdef");
        order.setRequestId("request-0009");
        order.setActivityId(1L);
        order.setUserId(9L);
        order.setSkuCode("SKU-001");
        order.setAmount(new BigDecimal("59.90"));
        order.setStatus("CREATED");
        order.setCreatedAt(LocalDateTime.of(2026, 9, 18, 12, 0));
        when(orderCreationService.findByRequestId("request-0009")).thenReturn(order);

        mvc.perform(get("/api/orders/by-request/request-0009")
                        .header("X-User-Id", "9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(order.getOrderNo()))
                .andExpect(jsonPath("$.data.amount").value(59.90));
    }

    @Test
    void anotherUserShouldNotObserveOrder() throws Exception {
        FlashSaleOrder order = new FlashSaleOrder();
        order.setUserId(9L);
        when(orderCreationService.findByRequestId("request-0009")).thenReturn(order);

        mvc.perform(get("/api/orders/by-request/request-0009")
                        .header("X-User-Id", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }
}
