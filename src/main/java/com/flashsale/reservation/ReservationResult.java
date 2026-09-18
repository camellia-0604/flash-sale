package com.flashsale.reservation;

/** 秒杀资格判断结果；requestId 将在 Day 3 随异步消息进入订单链路。 */
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
