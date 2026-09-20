package com.flashsale.activity.controller;

import com.flashsale.activity.service.ActivityService;
import com.flashsale.activity.vo.ActivityView;
import com.flashsale.common.ApiResponse;
import com.flashsale.reservation.FlashSaleReservationService;
import com.flashsale.reservation.ReservationResult;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 秒杀活动公开接口；Day 1 只开放安全的活动查询。 */
@RestController
@RequestMapping("/api/activities")
@Validated
public class ActivityController {
    private final ActivityService activityService;
    private final FlashSaleReservationService reservationService;

    public ActivityController(ActivityService activityService,
                              FlashSaleReservationService reservationService) {
        this.activityService = activityService;
        this.reservationService = reservationService;
    }

    /** 返回当前处于有效时间窗口的在线活动。 */
    @GetMapping
    public ApiResponse<List<ActivityView>> listActive() {
        return ApiResponse.success(activityService.listActive());
    }

    /** 抢占秒杀资格；成功后立即发送 RabbitMQ 消息，由消费者异步创建数据库订单。 */
    @PostMapping("/{activityId}/reservations")
    public ApiResponse<ReservationResult> reserve(
            @PathVariable @Positive long activityId,
            @RequestHeader("X-User-Id") @Positive long userId,
            @RequestHeader("X-Request-Id")
            @Pattern(regexp = "[A-Za-z0-9._-]{8,64}") String requestId
    ) {
        return ApiResponse.success(reservationService.reserve(activityId, userId, requestId));
    }
}
