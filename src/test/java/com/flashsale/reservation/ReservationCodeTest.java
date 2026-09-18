package com.flashsale.reservation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 固定 Lua 数字结果与 Java 业务编码之间的协议。 */
class ReservationCodeTest {
    @Test
    void shouldMapEveryLuaResult() {
        assertThat(ReservationCode.from(0)).isEqualTo(ReservationCode.ACCEPTED);
        assertThat(ReservationCode.from(1)).isEqualTo(ReservationCode.NOT_STARTED);
        assertThat(ReservationCode.from(2)).isEqualTo(ReservationCode.ENDED);
        assertThat(ReservationCode.from(3)).isEqualTo(ReservationCode.SOLD_OUT);
        assertThat(ReservationCode.from(4)).isEqualTo(ReservationCode.DUPLICATE);
        assertThat(ReservationCode.from(5)).isEqualTo(ReservationCode.NOT_READY);
        assertThatThrownBy(() -> ReservationCode.from(99))
                .isInstanceOf(IllegalStateException.class);
    }
}
