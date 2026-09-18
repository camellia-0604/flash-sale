package com.flashsale.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/** 提供可在测试中替换的系统时钟，避免活动时间判断直接依赖静态 now。 */
@Configuration
public class TimeConfig {
    @Bean
    public Clock clock() {
        // 与数据库 DATETIME 和容器 TZ 保持同一业务时区，避免换机器后活动边界漂移。
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
