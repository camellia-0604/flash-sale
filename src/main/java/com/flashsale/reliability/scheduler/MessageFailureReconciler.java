package com.flashsale.reliability.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flashsale.order.entity.FlashSaleOrder;
import com.flashsale.order.mapper.FlashSaleOrderMapper;
import com.flashsale.reliability.entity.MessageFailure;
import com.flashsale.reliability.service.MessageFailureService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;

/** 对发布确认结果未知或补偿暂时失败的记录进行最终收敛。 */
@Component
public class MessageFailureReconciler {
    private final MessageFailureService failureService;
    private final FlashSaleOrderMapper orderMapper;
    private final Clock clock;
    private final Duration reconcileDelay;

    public MessageFailureReconciler(
            MessageFailureService failureService,
            FlashSaleOrderMapper orderMapper,
            Clock clock,
            @Value("${flashsale.reliability.reconcile-delay:30s}") Duration reconcileDelay
    ) {
        this.failureService = failureService;
        this.orderMapper = orderMapper;
        this.clock = clock;
        this.reconcileDelay = reconcileDelay;
    }

    @Scheduled(
            initialDelayString = "${flashsale.reliability.reconcile-interval:10s}",
            fixedDelayString = "${flashsale.reliability.reconcile-interval:10s}"
    )
    public void reconcile() {
        LocalDateTime deadline = LocalDateTime.now(clock).minus(reconcileDelay);
        for (MessageFailure failure : failureService.listPendingBefore(deadline)) {
            Long orderCount = orderMapper.selectCount(Wrappers.<FlashSaleOrder>lambdaQuery()
                    .eq(FlashSaleOrder::getRequestId, failure.getRequestId()));
            if (orderCount > 0) {
                failureService.resolveIfRecorded(failure.getRequestId());
            } else {
                failureService.compensate(failure);
            }
        }
    }
}
