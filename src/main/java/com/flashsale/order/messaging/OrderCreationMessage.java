package com.flashsale.order.messaging;

/**
 * 请求线程与订单消费者之间的最小消息契约。
 *
 * <p>金额和 SKU 不由客户端或消息携带，消费者必须重新读取 MySQL 中的活动事实，
 * 防止消息字段被误当成可信成交数据。</p>
 */
public record OrderCreationMessage(long activityId, long userId, String requestId) {
}
