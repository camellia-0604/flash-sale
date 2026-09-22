package com.flashsale.observability;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 验证结果码计数与请求耗时使用稳定、低基数的指标名称。 */
class FlashSaleMetricsTest {
    @Test
    void shouldRecordResultAndDuration() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        FlashSaleMetrics metrics = new FlashSaleMetrics(registry);

        Timer.Sample sample = metrics.startReservation();
        metrics.finishReservation(sample, "ACCEPTED");

        assertThat(registry.counter(
                "flashsale.reservation.results", "code", "ACCEPTED"
        ).count()).isEqualTo(1);
        assertThat(registry.timer("flashsale.reservation.duration").count()).isEqualTo(1);
    }
}