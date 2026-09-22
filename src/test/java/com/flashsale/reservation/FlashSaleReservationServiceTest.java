package com.flashsale.reservation;

import com.flashsale.order.messaging.OrderCreationMessage;
import com.flashsale.order.messaging.OrderMessagePublisher;
import com.flashsale.order.messaging.PublishResult;
import com.flashsale.observability.FlashSaleMetrics;
import com.flashsale.reliability.service.MessageFailureService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证请求线程只为成功资格产生异步下单消息。 */
@ExtendWith(MockitoExtension.class)
class FlashSaleReservationServiceTest {
    @Mock
    private FlashSaleInventoryService inventoryService;
    @Mock
    private OrderMessagePublisher messagePublisher;
    @Mock
    private MessageFailureService failureService;
    @Mock
    private FlashSaleMetrics metrics;
    @InjectMocks
    private FlashSaleReservationService reservationService;

    @Test
    void acceptedReservationShouldPublishOrderMessage() {
        when(inventoryService.reserve(1L, 9L, "request-0009"))
                .thenReturn(ReservationResult.of(ReservationCode.ACCEPTED, "request-0009"));
        when(messagePublisher.publish(org.mockito.ArgumentMatchers.any()))
                .thenReturn(PublishResult.confirmed());

        ReservationResult result = reservationService.reserve(1L, 9L, "request-0009");

        ArgumentCaptor<OrderCreationMessage> captor = ArgumentCaptor.forClass(OrderCreationMessage.class);
        verify(messagePublisher).publish(captor.capture());
        assertThat(result.accepted()).isTrue();
        assertThat(captor.getValue()).isEqualTo(new OrderCreationMessage(1L, 9L, "request-0009"));
    }

    @Test
    void rejectedPublishShouldCompensateAndReturnRetryableResult() {
        when(inventoryService.reserve(1L, 9L, "request-0009"))
                .thenReturn(ReservationResult.of(ReservationCode.ACCEPTED, "request-0009"));
        when(messagePublisher.publish(org.mockito.ArgumentMatchers.any()))
                .thenReturn(PublishResult.rejected("broker nack"));

        ReservationResult result = reservationService.reserve(1L, 9L, "request-0009");

        verify(failureService).recordAndCompensate(
                new OrderCreationMessage(1L, 9L, "request-0009"), "PUBLISH", "broker nack"
        );
        assertThat(result.code()).isEqualTo("QUEUE_UNAVAILABLE");
        assertThat(result.accepted()).isFalse();
    }

    @Test
    void unknownPublishShouldWaitForReconciliationWithoutImmediateCompensation() {
        when(inventoryService.reserve(1L, 9L, "request-0009"))
                .thenReturn(ReservationResult.of(ReservationCode.ACCEPTED, "request-0009"));
        when(messagePublisher.publish(org.mockito.ArgumentMatchers.any()))
                .thenReturn(PublishResult.unknown("confirm timeout"));

        ReservationResult result = reservationService.reserve(1L, 9L, "request-0009");

        verify(failureService).recordPending(
                new OrderCreationMessage(1L, 9L, "request-0009"), "PUBLISH", "confirm timeout"
        );
        verify(failureService, never()).recordAndCompensate(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString()
        );
        assertThat(result.code()).isEqualTo("PUBLISH_PENDING");
        assertThat(result.accepted()).isTrue();
    }

    @Test
    void rejectedReservationShouldNotPublishOrderMessage() {
        when(inventoryService.reserve(1L, 9L, "request-0009"))
                .thenReturn(ReservationResult.of(ReservationCode.SOLD_OUT, "request-0009"));

        reservationService.reserve(1L, 9L, "request-0009");

        verify(messagePublisher, never()).publish(org.mockito.ArgumentMatchers.any());
    }
}
