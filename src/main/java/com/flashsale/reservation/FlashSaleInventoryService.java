package com.flashsale.reservation;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.flashsale.activity.entity.ActivityStatus;
import com.flashsale.activity.entity.FlashSaleActivity;
import com.flashsale.activity.mapper.FlashSaleActivityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

/**
 * Redis 秒杀库存服务。
 *
 * <p>应用启动时将在线活动预热到 Redis；请求阶段只执行 Lua，一次完成时间窗口、
 * 一人一单和库存扣减。Day 2 的成功表示取得资格，Day 3 才会异步创建 MySQL 订单。</p>
 */
@Service
public class FlashSaleInventoryService {
    private static final Logger log = LoggerFactory.getLogger(FlashSaleInventoryService.class);
    private static final DefaultRedisScript<Long> INITIALIZE_SCRIPT =
            loadScript("scripts/initialize-activity.lua");
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT =
            loadScript("scripts/reserve-stock.lua");

    private final StringRedisTemplate redisTemplate;
    private final FlashSaleActivityMapper activityMapper;
    private final Clock clock;

    /** Spring Data Redis 3.x 通过 setter 绑定脚本资源和返回类型。 */
    private static DefaultRedisScript<Long> loadScript(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    public FlashSaleInventoryService(
            StringRedisTemplate redisTemplate,
            FlashSaleActivityMapper activityMapper,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.activityMapper = activityMapper;
        this.clock = clock;
    }

    /** 启动完成后预热全部在线活动，未来活动也提前具备开始时间判断。 */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpOnlineActivities() {
        List<FlashSaleActivity> activities = activityMapper.selectList(
                Wrappers.<FlashSaleActivity>lambdaQuery()
                        .eq(FlashSaleActivity::getStatus, ActivityStatus.ONLINE)
        );
        activities.forEach(this::warmUpActivity);
        log.info("Flash-sale inventory warm-up completed, activityCount={}", activities.size());
    }

    /** 初始化单个活动；仅在 Key 不存在时复制库存，防止应用重启导致库存回滚。 */
    public void warmUpActivity(FlashSaleActivity activity) {
        ZoneId zone = clock.getZone();
        long startAt = activity.getStartTime().atZone(zone).toInstant().toEpochMilli();
        long endAt = activity.getEndTime().atZone(zone).toInstant().toEpochMilli();
        redisTemplate.execute(
                INITIALIZE_SCRIPT,
                List.of(FlashSaleRedisKeys.state(activity.getId())),
                Long.toString(startAt), Long.toString(endAt),
                Integer.toString(activity.getAvailableStock())
        );
    }

    /** Lua 返回前库存已经预扣，后续请求不会看到同一份库存。 */
    public ReservationResult reserve(long activityId, long userId, String requestId) {
        Long rawCode = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(FlashSaleRedisKeys.state(activityId), FlashSaleRedisKeys.buyers(activityId)),
                Long.toString(userId), requestId
        );
        if (rawCode == null) {
            throw new IllegalStateException("Redis did not return a reservation result");
        }
        return ReservationResult.of(ReservationCode.from(rawCode), requestId);
    }

    /** 查询 Redis 可抢库存；缓存暂不可用时使用 MySQL 基线，查询值不参与资格判断。 */
    public int currentStockOrDatabase(FlashSaleActivity activity) {
        try {
            Object value = redisTemplate.opsForHash().get(
                    FlashSaleRedisKeys.state(activity.getId()), "stock"
            );
            return value == null ? activity.getAvailableStock() : Integer.parseInt(value.toString());
        } catch (DataAccessException | NumberFormatException ex) {
            log.warn("Flash-sale stock snapshot unavailable, activityId={}", activity.getId(), ex);
            return activity.getAvailableStock();
        }
    }
}
