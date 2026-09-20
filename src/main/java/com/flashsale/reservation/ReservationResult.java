package com.flashsale.reservation;

/** 秒杀资格与消息投递结果；requestId 用于异步订单查询和可靠性对账。 */
public record ReservationResult(
        String code,
        boolean accepted,
        String message,
        String requestId
) {
    public static ReservationResult of(ReservationCode code, String requestId) {
        return new ReservationResult(code.name(), code.accepted(), code.message(), requestId);
    }
}
