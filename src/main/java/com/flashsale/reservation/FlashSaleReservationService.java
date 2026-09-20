package com.flashsale.reservation;

import com.flashsale.order.messaging.OrderCreationMessage;
import com.flashsale.order.messaging.OrderMessagePublisher;
import com.flashsale.order.messaging.PublishResult;
import com.flashsale.reliability.service.MessageFailureService;
import org.springframework.stereotype.Service;

/**
 * 串联“Redis 快速裁决”和“RabbitMQ 异步成单”。
 *
 * <p>请求线程不等待 MySQL 事务。只有 Lua 接受资格时才发送消息；活动未开始、售罄和
 * 重复请求都不会制造无效队列流量。Day 4 会处理预扣成功但发送失败的补偿窗口。</p>
 */
@Service
public class FlashSaleReservationService {
    private final FlashSaleInventoryService inventoryService;
    private final OrderMessagePublisher messagePublisher;
    private final MessageFailureService failureService;

    public FlashSaleReservationService(FlashSaleInventoryService inventoryService,
                                       OrderMessagePublisher messagePublisher,
                                       MessageFailureService failureService) {
        this.inventoryService = inventoryService;
        this.messagePublisher = messagePublisher;
        this.failureService = failureService;
    }

    public ReservationResult reserve(long activityId, long userId, String requestId) {
        ReservationResult result = inventoryService.reserve(activityId, userId, requestId);
        if (result.accepted()) {
            OrderCreationMessage message = new OrderCreationMessage(activityId, userId, requestId);
            PublishResult publishResult = messagePublisher.publish(message);
            if (publishResult.status() == PublishResult.Status.REJECTED) {
                failureService.recordAndCompensate(
                        message, "PUBLISH", publishResult.reason()
                );
                return ReservationResult.of(ReservationCode.QUEUE_UNAVAILABLE, requestId);
            }
            if (publishResult.status() == PublishResult.Status.UNKNOWN) {
                failureService.recordPending(message, "PUBLISH", publishResult.reason());
                return ReservationResult.of(ReservationCode.PUBLISH_PENDING, requestId);
            }
            failureService.markRepublishedIfRecorded(message);
        }
        return result;
    }
}
