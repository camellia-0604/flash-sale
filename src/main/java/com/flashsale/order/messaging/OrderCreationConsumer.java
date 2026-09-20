package com.flashsale.order.messaging;

import com.flashsale.config.RabbitMqConfig;
import com.flashsale.order.service.OrderCreationService;
import com.flashsale.reliability.service.MessageFailureService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/** RabbitMQ 消费入口；事务成功返回后容器才会确认本次消息。 */
@Component
public class OrderCreationConsumer {
    private final OrderCreationService orderCreationService;
    private final MessageFailureService failureService;

    public OrderCreationConsumer(OrderCreationService orderCreationService,
                                 MessageFailureService failureService) {
        this.orderCreationService = orderCreationService;
        this.failureService = failureService;
    }

    @RabbitListener(queues = RabbitMqConfig.ORDER_QUEUE)
    public void consume(OrderCreationMessage message) {
        orderCreationService.createOrder(message);
        failureService.resolveIfRecorded(message.requestId());
    }
}
