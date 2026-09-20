package com.flashsale.order.messaging;

import com.flashsale.config.RabbitMqConfig;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** 把资格消息发布到 RabbitMQ，并等待 Broker 对单条消息给出关联确认。 */
@Component
public class OrderMessagePublisher {
    private final RabbitTemplate rabbitTemplate;
    private final Duration confirmTimeout;

    public OrderMessagePublisher(
            RabbitTemplate rabbitTemplate,
            @Value("${flashsale.reliability.confirm-timeout:3s}") Duration confirmTimeout
    ) {
        this.rabbitTemplate = rabbitTemplate;
        this.confirmTimeout = confirmTimeout;
    }

    public PublishResult publish(OrderCreationMessage message) {
        CorrelationData correlation = new CorrelationData(message.requestId());
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.ORDER_EXCHANGE,
                    RabbitMqConfig.ORDER_ROUTING_KEY,
                    message,
                    correlation
            );
            CorrelationData.Confirm confirm = correlation.getFuture().get(
                    confirmTimeout.toMillis(), TimeUnit.MILLISECONDS
            );
            ReturnedMessage returned = correlation.getReturned();
            if (returned != null) {
                return PublishResult.rejected("unroutable: " + returned.getReplyText());
            }
            if (!confirm.isAck()) {
                return PublishResult.rejected("broker nack: " + confirm.getReason());
            }
            return PublishResult.confirmed();
        } catch (AmqpException ex) {
            // 连接或发送阶段同步失败，消息没有得到 Broker 接收确认，可以进入补偿。
            return PublishResult.rejected(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } catch (TimeoutException | ExecutionException ex) {
            // 超时和异步异常无法证明消息未到达；先持久化待对账，不能贸然加回库存。
            return PublishResult.unknown(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return PublishResult.unknown("publisher confirm interrupted");
        }
    }
}
