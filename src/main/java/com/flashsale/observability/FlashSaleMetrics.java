package com.flashsale.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/** 汇总秒杀入口的业务结果和端到端耗时，供 Actuator 与 Prometheus 读取。 */
@Component
public class FlashSaleMetrics {
    private static final String RESULT_METRIC = "flashsale.reservation.results";
    private static final String DURATION_METRIC = "flashsale.reservation.duration";

    private final MeterRegistry meterRegistry;

    public FlashSaleMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startReservation() {
        return Timer.start(meterRegistry);
    }

    /** 结果码数量是固定集合，可以安全地作为低基数标签长期统计。 */
    public void finishReservation(Timer.Sample sample, String resultCode) {
        meterRegistry.counter(RESULT_METRIC, "code", resultCode).increment();
        sample.stop(meterRegistry.timer(DURATION_METRIC));
    }
}