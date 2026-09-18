package com.flashsale.activity.entity;

/** 秒杀活动状态；时间窗口之外还必须满足 ONLINE 才能公开。 */
public enum ActivityStatus {
    DRAFT,
    ONLINE,
    OFFLINE
}
