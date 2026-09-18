package com.flashsale.reservation;

/** 集中维护 Redis Key，花括号内的活动 ID 是 Redis Cluster 哈希标签。 */
public final class FlashSaleRedisKeys {
    private FlashSaleRedisKeys() {
    }

    public static String state(long activityId) {
        return "flashsale:activity:{" + activityId + "}:state";
    }

    public static String buyers(long activityId) {
        return "flashsale:activity:{" + activityId + "}:buyers";
    }
}
