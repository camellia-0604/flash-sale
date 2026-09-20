package com.flashsale.reliability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 发布结果未知或消费进入死信后的持久化记录。 */
@Getter
@Setter
@TableName("flash_sale_message_failure")
public class MessageFailure {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("request_id")
    private String requestId;
    @TableField("activity_id")
    private Long activityId;
    @TableField("user_id")
    private Long userId;
    private String stage;
    private String status;
    private String reason;
    private Integer attempts;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
