package com.flashsale.activity.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flashsale.activity.entity.ActivityStatus;
import com.flashsale.activity.entity.FlashSaleActivity;
import com.flashsale.activity.mapper.FlashSaleActivityMapper;
import com.flashsale.activity.vo.ActivityView;
import com.flashsale.reservation.FlashSaleInventoryService;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 秒杀活动公开查询服务。
 *
 * <p>MySQL 保存活动配置，Redis 保存请求链路使用的实时库存；列表查询优先展示
 * Redis 快照，缓存暂不可用时退回数据库基线。</p>
 */
@Service
public class ActivityService {
    private final FlashSaleActivityMapper activityMapper;
    private final Clock clock;
    private final FlashSaleInventoryService inventoryService;

    public ActivityService(FlashSaleActivityMapper activityMapper, Clock clock,
                           FlashSaleInventoryService inventoryService) {
        this.activityMapper = activityMapper;
        this.clock = clock;
        this.inventoryService = inventoryService;
    }

    /** 查询已经开始、尚未结束且状态为 ONLINE 的活动。 */
    public List<ActivityView> listActive() {
        LocalDateTime now = LocalDateTime.now(clock);
        return activityMapper.selectList(
                        Wrappers.<FlashSaleActivity>lambdaQuery()
                                .eq(FlashSaleActivity::getStatus, ActivityStatus.ONLINE)
                                .le(FlashSaleActivity::getStartTime, now)
                                .gt(FlashSaleActivity::getEndTime, now)
                                .orderByAsc(FlashSaleActivity::getEndTime)
                                .orderByAsc(FlashSaleActivity::getId)
                ).stream()
                .map(this::toView)
                .toList();
    }

    /** 实体只裁剪公开字段，版本号和内部状态不暴露给客户端。 */
    private ActivityView toView(FlashSaleActivity activity) {
        return new ActivityView(
                activity.getId(), activity.getTitle(), activity.getSkuCode(),
                activity.getOriginalPrice(), activity.getFlashPrice(),
                inventoryService.currentStockOrDatabase(activity),
                activity.getStartTime(), activity.getEndTime()
        );
    }
}
