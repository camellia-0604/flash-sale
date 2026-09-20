package com.flashsale.reliability.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flashsale.order.messaging.OrderCreationMessage;
import com.flashsale.reliability.entity.MessageFailure;
import com.flashsale.reliability.mapper.MessageFailureMapper;
import com.flashsale.reservation.FlashSaleInventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 持久化消息异常并执行幂等 Redis 补偿。
 *
 * <p>数据库记录先独立落库，再执行 Redis Lua。即使补偿暂时失败，PENDING 记录仍会
 * 被对账任务再次处理；不会因为一次 Redis 故障永久丢失失败事实。</p>
 */
@Service
public class MessageFailureService {
    private static final Logger log = LoggerFactory.getLogger(MessageFailureService.class);
    public static final String PENDING = "PENDING";
    public static final String COMPENSATED = "COMPENSATED";
    public static final String RESOLVED = "RESOLVED";

    private final MessageFailureMapper failureMapper;
    private final FlashSaleInventoryService inventoryService;

    public MessageFailureService(MessageFailureMapper failureMapper,
                                 FlashSaleInventoryService inventoryService) {
        this.failureMapper = failureMapper;
        this.inventoryService = inventoryService;
    }

    /** 保存结果未知的消息，等待消费者成功回写或定时对账。 */
    public MessageFailure recordPending(OrderCreationMessage message, String stage, String reason) {
        MessageFailure failure = findByRequestId(message.requestId());
        if (failure == null) {
            failure = new MessageFailure();
            failure.setRequestId(message.requestId());
            failure.setActivityId(message.activityId());
            failure.setUserId(message.userId());
            failure.setAttempts(1);
            failure.setStage(stage);
            failure.setStatus(PENDING);
            failure.setReason(shortReason(reason));
            try {
                failureMapper.insert(failure);
                return failure;
            } catch (DuplicateKeyException ignored) {
                failure = findByRequestId(message.requestId());
            }
        }
        failure.setStage(stage);
        failure.setStatus(PENDING);
        failure.setReason(shortReason(reason));
        failure.setAttempts(failure.getAttempts() + 1);
        failureMapper.updateById(failure);
        return failure;
    }

    /** 明确失败或死信到达时记录并立即尝试补偿；Lua 保证重复调用不多加库存。 */
    public void recordAndCompensate(OrderCreationMessage message, String stage, String reason) {
        MessageFailure failure = recordPending(message, stage, reason);
        compensate(failure);
    }

    /** 旧失败请求重新发布成功后保留 PENDING，待成功消费或对账确认。 */
    public void markRepublishedIfRecorded(OrderCreationMessage message) {
        MessageFailure failure = findByRequestId(message.requestId());
        if (failure == null) {
            return;
        }
        failure.setStatus(PENDING);
        failure.setStage("PUBLISH");
        failure.setReason("republished and broker confirmed");
        failure.setAttempts(failure.getAttempts() + 1);
        failureMapper.updateById(failure);
    }

    /** 消费事务成功后把已有异常记录闭环为 RESOLVED。 */
    public void resolveIfRecorded(String requestId) {
        MessageFailure failure = findByRequestId(requestId);
        if (failure == null || RESOLVED.equals(failure.getStatus())) {
            return;
        }
        failure.setStatus(RESOLVED);
        failure.setReason("database order exists");
        failureMapper.updateById(failure);
    }

    public MessageFailure findByRequestId(String requestId) {
        return failureMapper.selectOne(Wrappers.<MessageFailure>lambdaQuery()
                .eq(MessageFailure::getRequestId, requestId));
    }

    /** 只扫描经过宽限期的待处理记录，给结果未知但实际已投递的消息留出消费时间。 */
    public List<MessageFailure> listPendingBefore(LocalDateTime deadline) {
        return failureMapper.selectList(Wrappers.<MessageFailure>lambdaQuery()
                .eq(MessageFailure::getStatus, PENDING)
                .le(MessageFailure::getUpdatedAt, deadline)
                .orderByAsc(MessageFailure::getId)
                .last("LIMIT 100"));
    }

    public void compensate(MessageFailure failure) {
        try {
            long result = inventoryService.compensate(
                    failure.getActivityId(), failure.getUserId(), failure.getRequestId()
            );
            if (result >= 0) {
                failure.setStatus(COMPENSATED);
                failure.setReason(shortReason(failure.getReason() + "; redis compensation=" + result));
                failureMapper.updateById(failure);
            }
        } catch (RuntimeException ex) {
            log.warn("Redis compensation deferred, requestId={}", failure.getRequestId(), ex);
        }
    }

    private String shortReason(String reason) {
        String value = reason == null ? "unknown" : reason;
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
