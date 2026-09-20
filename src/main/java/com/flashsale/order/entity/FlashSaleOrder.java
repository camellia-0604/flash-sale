package com.flashsale.order.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** MySQL 中最终成立的秒杀订单；Redis 资格不能替代这张事实表。 */
@Getter
@Setter
@TableName("flash_sale_order")
public class FlashSaleOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("order_no")
    private String orderNo;
    @TableField("request_id")
    private String requestId;
    @TableField("activity_id")
    private Long activityId;
    @TableField("user_id")
    private Long userId;
    @TableField("sku_code")
    private String skuCode;
    private Integer quantity;
    private BigDecimal amount;
    private String status;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
