package com.flashsale.order.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.flashsale.activity.entity.FlashSaleActivity;
import com.flashsale.activity.mapper.FlashSaleActivityMapper;
import com.flashsale.order.entity.FlashSaleOrder;
import com.flashsale.order.mapper.FlashSaleOrderMapper;
import com.flashsale.order.messaging.OrderCreationMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** MySQL 最终成单事务的单元测试。 */
@ExtendWith(MockitoExtension.class)
class OrderCreationServiceTest {
    @Mock
    private FlashSaleOrderMapper orderMapper;
    @Mock
    private FlashSaleActivityMapper activityMapper;
    @InjectMocks
    private OrderCreationService orderCreationService;

    @Test
    void shouldBuildTrustedOrderAndDeductDatabaseStock() {
        when(orderMapper.selectOne(org.mockito.ArgumentMatchers.<Wrapper<FlashSaleOrder>>any()))
                .thenReturn(null);
        FlashSaleActivity activity = new FlashSaleActivity();
        activity.setId(1L);
        activity.setSkuCode("SKU-001");
        activity.setFlashPrice(new BigDecimal("59.90"));
        when(activityMapper.selectById(1L)).thenReturn(activity);
        when(orderMapper.insert(any(FlashSaleOrder.class))).thenReturn(1);
        when(activityMapper.deductAvailableStock(1L)).thenReturn(1);

        orderCreationService.createOrder(new OrderCreationMessage(1L, 9L, "request-0009"));

        ArgumentCaptor<FlashSaleOrder> captor = ArgumentCaptor.forClass(FlashSaleOrder.class);
        verify(orderMapper).insert(captor.capture());
        verify(activityMapper).deductAvailableStock(1L);
        assertThat(captor.getValue().getSkuCode()).isEqualTo("SKU-001");
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("59.90");
        assertThat(captor.getValue().getOrderNo()).hasSize(32);
    }

    @Test
    void duplicateMessageShouldReturnBeforeTouchingStock() {
        FlashSaleOrder existing = new FlashSaleOrder();
        existing.setId(7L);
        when(orderMapper.selectOne(org.mockito.ArgumentMatchers.<Wrapper<FlashSaleOrder>>any()))
                .thenReturn(existing);

        orderCreationService.createOrder(new OrderCreationMessage(1L, 9L, "request-0009"));

        verify(orderMapper, never()).insert(any(FlashSaleOrder.class));
        verify(activityMapper, never()).deductAvailableStock(1L);
    }
}
