package com.flashsale.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashsale.activity.entity.FlashSaleActivity;
import org.apache.ibatis.annotations.Mapper;

/** 秒杀活动数据访问入口；复杂库存命令将在后续迭代使用显式 SQL。 */
@Mapper
public interface FlashSaleActivityMapper extends BaseMapper<FlashSaleActivity> {
}
