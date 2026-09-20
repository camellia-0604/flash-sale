package com.flashsale.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.flashsale.activity.entity.FlashSaleActivity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/** 秒杀活动数据访问入口；最终库存通过数据库条件更新裁决。 */
@Mapper
public interface FlashSaleActivityMapper extends BaseMapper<FlashSaleActivity> {
    /** 只有在线且仍有库存时才扣减，影响 0 行代表数据库拒绝成单。 */
    @Update("""
            UPDATE flash_sale_activity
            SET available_stock = available_stock - 1,
                version = version + 1
            WHERE id = #{activityId}
              AND status = 'ONLINE'
              AND available_stock > 0
            """)
    int deductAvailableStock(@Param("activityId") long activityId);
}
