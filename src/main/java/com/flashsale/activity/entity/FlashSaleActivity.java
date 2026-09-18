package com.flashsale.activity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * flash_sale_activity 表对应的秒杀活动。
 *
 * <p>Day 1 的 MySQL 库存是活动事实与最终落库基线；Day 2 会把可抢库存预热到
 * Redis，并通过 Lua 原子完成资格和库存预扣。</p>
 */
@Getter
@Setter
@TableName("flash_sale_activity")
public class FlashSaleActivity {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 用户可见的活动标题。 */
    private String title;
    /** 被抢购商品的稳定业务编码。 */
    @TableField("sku_code")
    private String skuCode;
    /** 展示划线价，不参与秒杀金额计算。 */
    @TableField("original_price")
    private BigDecimal originalPrice;
    /** 服务端可信秒杀价。 */
    @TableField("flash_price")
    private BigDecimal flashPrice;
    /** 活动配置的初始库存。 */
    @TableField("total_stock")
    private Integer totalStock;
    /** MySQL 中最终可用库存；Redis 预扣结果后续需要与它对账。 */
    @TableField("available_stock")
    private Integer availableStock;
    @TableField("start_time")
    private LocalDateTime startTime;
    @TableField("end_time")
    private LocalDateTime endTime;
    private ActivityStatus status;
    /** 后台修改活动配置时使用的乐观锁版本。 */
    @Version
    private Integer version;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
