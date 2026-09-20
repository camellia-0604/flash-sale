package com.flashsale.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashsale.order.entity.FlashSaleOrder;
import org.apache.ibatis.annotations.Mapper;

/** 秒杀订单数据入口；唯一索引是消费者幂等的最终防线。 */
@Mapper
public interface FlashSaleOrderMapper extends BaseMapper<FlashSaleOrder> {
}
