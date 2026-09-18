package com.flashsale.reservation;

import java.util.Arrays;

/** Lua 脚本返回的稳定业务结果；数字编码一旦对外使用就不随提示文案变化。 */
public enum ReservationCode {
    ACCEPTED(0, true, "库存预扣成功，等待异步创建订单"),
    NOT_STARTED(1, false, "活动尚未开始"),
    ENDED(2, false, "活动已经结束"),
    SOLD_OUT(3, false, "活动库存已售罄"),
    DUPLICATE(4, false, "当前用户已经抢购过该活动"),
    NOT_READY(5, false, "活动库存尚未完成预热");

    private final long value;
    private final boolean accepted;
    private final String message;

    ReservationCode(long value, boolean accepted, String message) {
        this.value = value;
        this.accepted = accepted;
        this.message = message;
    }

    public long value() { return value; }
    public boolean accepted() { return accepted; }
    public String message() { return message; }

    /** 未知编码代表服务端脚本和 Java 版本不一致，应直接暴露为程序错误。 */
    public static ReservationCode from(long value) {
        return Arrays.stream(values())
                .filter(code -> code.value == value)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unknown reservation code: " + value));
    }
}
