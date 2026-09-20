package com.flashsale.order.messaging;

/** Publisher Confirm 的稳定结果；UNKNOWN 不能直接补偿，避免消息实际已到达时多加库存。 */
public record PublishResult(Status status, String reason) {
    public enum Status {
        CONFIRMED,
        REJECTED,
        UNKNOWN
    }

    public static PublishResult confirmed() {
        return new PublishResult(Status.CONFIRMED, "broker confirmed");
    }

    public static PublishResult rejected(String reason) {
        return new PublishResult(Status.REJECTED, reason);
    }

    public static PublishResult unknown(String reason) {
        return new PublishResult(Status.UNKNOWN, reason);
    }
}
