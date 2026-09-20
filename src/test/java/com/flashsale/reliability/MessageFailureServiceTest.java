package com.flashsale.reliability;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.flashsale.order.messaging.OrderCreationMessage;
import com.flashsale.reliability.entity.MessageFailure;
import com.flashsale.reliability.mapper.MessageFailureMapper;
import com.flashsale.reliability.service.MessageFailureService;
import com.flashsale.reservation.FlashSaleInventoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 失败事实必须先记录，再把 Redis 补偿结果写回状态。 */
@ExtendWith(MockitoExtension.class)
class MessageFailureServiceTest {
    @Mock
    private MessageFailureMapper failureMapper;
    @Mock
    private FlashSaleInventoryService inventoryService;
    @InjectMocks
    private MessageFailureService failureService;

    @Test
    void explicitFailureShouldBecomeCompensated() {
        when(failureMapper.selectOne(org.mockito.ArgumentMatchers.<Wrapper<MessageFailure>>any()))
                .thenReturn(null);
        when(failureMapper.insert(any(MessageFailure.class))).thenAnswer(invocation -> {
            MessageFailure failure = invocation.getArgument(0);
            failure.setId(1L);
            return 1;
        });
        when(inventoryService.compensate(1L, 9L, "request-0009")).thenReturn(1L);

        failureService.recordAndCompensate(
                new OrderCreationMessage(1L, 9L, "request-0009"),
                "PUBLISH", "broker nack"
        );

        ArgumentCaptor<MessageFailure> captor = ArgumentCaptor.forClass(MessageFailure.class);
        verify(failureMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(MessageFailureService.COMPENSATED);
        assertThat(captor.getValue().getReason()).contains("redis compensation=1");
    }
}
