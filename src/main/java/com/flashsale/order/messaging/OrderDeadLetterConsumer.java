package com.flashsale.order.messaging;

import com.flashsale.config.RabbitMqConfig;
import com.flashsale.reliability.service.MessageFailureService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** 消费重试耗尽后的最终入口：持久化失败事实并幂等归还 Redis 资格。 */
@Component
public class OrderDeadLetterConsumer {
    private final MessageFailureService failureService;

    public OrderDeadLetterConsumer(MessageFailureService failureService) {
        this.failureService = failureService;
    }

    @RabbitListener(
            queues = RabbitMqConfig.DEAD_LETTER_QUEUE,
            containerFactory = "deadLetterListenerContainerFactory"
    )
    public void consume(OrderCreationMessage message) {
        failureService.recordAndCompensate(
                message, "CONSUME", "consumer retries exhausted and message entered dead-letter queue"
        );
    }
}
