package com.flashsale.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 秒杀异步下单与死信拓扑。
 *
 * <p>主队列连续消费失败后拒绝消息，RabbitMQ 根据队列参数把它转入死信队列。
 * 队列名使用 v2，避免给已存在的 Day 3 队列动态增加参数时触发声明冲突。</p>
 */
@Configuration
public class RabbitMqConfig {
    public static final String ORDER_EXCHANGE = "flashsale.order.exchange";
    public static final String ORDER_QUEUE = "flashsale.order.create.v2.queue";
    public static final String ORDER_ROUTING_KEY = "flashsale.order.create";
    public static final String DEAD_LETTER_EXCHANGE = "flashsale.order.dlx";
    public static final String DEAD_LETTER_QUEUE = "flashsale.order.dead.queue";
    public static final String DEAD_LETTER_ROUTING_KEY = "flashsale.order.dead";

    @Bean
    public DirectExchange flashSaleOrderExchange() {
        return new DirectExchange(ORDER_EXCHANGE, true, false);
    }

    @Bean
    public Queue flashSaleOrderQueue() {
        return QueueBuilder.durable(ORDER_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(DEAD_LETTER_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding flashSaleOrderBinding(Queue flashSaleOrderQueue,
                                         DirectExchange flashSaleOrderExchange) {
        return BindingBuilder.bind(flashSaleOrderQueue)
                .to(flashSaleOrderExchange)
                .with(ORDER_ROUTING_KEY);
    }

    @Bean
    public DirectExchange flashSaleDeadLetterExchange() {
        return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    public Queue flashSaleDeadLetterQueue() {
        return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
    }

    @Bean
    public Binding flashSaleDeadLetterBinding(Queue flashSaleDeadLetterQueue,
                                               DirectExchange flashSaleDeadLetterExchange) {
        return BindingBuilder.bind(flashSaleDeadLetterQueue)
                .to(flashSaleDeadLetterExchange)
                .with(DEAD_LETTER_ROUTING_KEY);
    }

    /**
     * 死信处理使用独立容器：失败记录数据库不可用时重新入队，不能套用主队列的
     * “三次后丢弃”策略。Redis 补偿失败由已落库的 PENDING 记录交给对账任务处理。
     */
    @Bean
    public SimpleRabbitListenerContainerFactory deadLetterListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter rabbitMessageConverter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(rabbitMessageConverter);
        factory.setConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(true);
        return factory;
    }

    /** JSON 保持消息可读，避免 Java 原生序列化把类实现细节写进队列。 */
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
