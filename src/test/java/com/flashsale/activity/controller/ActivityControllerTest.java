package com.flashsale.activity.controller;

import com.flashsale.activity.service.ActivityService;
import com.flashsale.activity.vo.ActivityView;
import com.flashsale.reservation.FlashSaleReservationService;
import com.flashsale.reservation.ReservationCode;
import com.flashsale.reservation.ReservationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 秒杀活动公开接口切片测试，不连接 MySQL 或 Redis。 */
@WebMvcTest(ActivityController.class)
class ActivityControllerTest {
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private ActivityService activityService;
    @MockitoBean
    private FlashSaleReservationService reservationService;

    /** API 应使用统一响应并保留金额与库存字段。 */
    @Test
    void shouldReturnActiveActivities() throws Exception {
        when(activityService.listActive()).thenReturn(List.of(new ActivityView(
                1L, "Day 1 演示活动", "DEMO-SKU-001",
                new BigDecimal("199.00"), new BigDecimal("59.90"), 100,
                LocalDateTime.of(2026, 1, 1, 0, 0),
                LocalDateTime.of(2030, 1, 1, 0, 0)
        )));

        mvc.perform(get("/api/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].skuCode").value("DEMO-SKU-001"))
                .andExpect(jsonPath("$.data[0].remainingStock").value(100));
    }

    /** 合法用户和请求标识会原样进入 Lua 服务，并返回稳定业务编码。 */
    @Test
    void shouldReserveFlashSaleQualification() throws Exception {
        when(reservationService.reserve(1L, 9L, "request-0009"))
                .thenReturn(ReservationResult.of(ReservationCode.ACCEPTED, "request-0009"));

        mvc.perform(post("/api/activities/1/reservations")
                        .header("X-User-Id", "9")
                        .header("X-Request-Id", "request-0009"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("ACCEPTED"))
                .andExpect(jsonPath("$.data.accepted").value(true))
                .andExpect(jsonPath("$.data.requestId").value("request-0009"));
    }

    /** 请求标识过短时在进入库存服务前返回 400。 */
    @Test
    void shouldRejectUnsafeRequestId() throws Exception {
        mvc.perform(post("/api/activities/1/reservations")
                        .header("X-User-Id", "9")
                        .header("X-Request-Id", "short"))
                .andExpect(status().isBadRequest());
    }
}
