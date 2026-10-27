package com.kangcode.server.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangcode.common.constant.RedisKeyConstant;
import com.kangcode.common.constant.TicketCacheConstant;
import com.kangcode.pojo.entity.ScreeningSeat;
import com.kangcode.server.mapper.ScreeningSeatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 库存对账任务：定时用 MySQL 校准 Redis（替代 Canal 的兜底方案）。
 *
 * <p>重要设计取舍：对账只「收紧」不「放大」。
 * 因为购票是「先扣 Redis、再由 MQ 异步落库」，在消息还没消费时 DB 余票会大于 Redis 余票，
 * 如果直接用 DB 覆盖 Redis，就等于把已经卖出去的票又放回去 → 超卖。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockReconcileTask {

    /** 对账范围：今天 ~ 今天 + 14 天 */
    private static final int DAYS_AHEAD = 14;

    private final ScreeningSeatMapper screeningSeatMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 启动 1 分钟后跑第一次，之后每 5 分钟一次 */
    @Scheduled(initialDelay = 60_000, fixedDelay = 5 * 60 * 1000)
    public void reconcile() {
        LocalDate today = LocalDate.now();
        List<ScreeningSeat> seats = screeningSeatMapper.selectList(new LambdaQueryWrapper<ScreeningSeat>()
                .ge(ScreeningSeat::getShowDate, today)
                .le(ScreeningSeat::getShowDate, today.plusDays(DAYS_AHEAD))
                .eq(ScreeningSeat::getStatus, 1));

        int preheated = 0;
        int tightened = 0;
        int inFlight = 0;

        for (ScreeningSeat seat : seats) {
            String key = RedisKeyConstant.ticketStockKey(
                    seat.getScreeningId(), seat.getShowDate().toString(), seat.getTicketType());
            String cached = stringRedisTemplate.opsForValue().get(key);
            int dbRemain = seat.getRemain();

            // ① 还没预热过 → 补上（属于正常情况，不打 WARN）
            if (cached == null || TicketCacheConstant.EMPTY_VALUE.equals(cached)) {
                write(key, dbRemain);
                preheated++;
                continue;
            }

            int redisRemain;
            try {
                redisRemain = Integer.parseInt(cached);
            } catch (NumberFormatException e) {
                log.warn("库存对账发现非法值 key={} value={}，按 DB 覆盖", key, cached);
                write(key, dbRemain);
                tightened++;
                continue;
            }

            if (redisRemain > dbRemain) {
                // ② Redis 比 DB 大：缓存被放大了，收紧到 DB 值（防止超卖）
                log.warn("库存对账收紧 key={} redis={} db={}", key, redisRemain, dbRemain);
                write(key, dbRemain);
                tightened++;
            } else if (redisRemain < dbRemain) {
                // ③ Redis 比 DB 小：通常是"已扣减但订单还没落库"的在途状态，不能放大
                inFlight++;
                log.warn("库存对账发现差异（可能在途） key={} redis={} db={}", key, redisRemain, dbRemain);
            }
        }

        log.info("库存对账完成：检查 {} 条，补预热 {} 条，收紧 {} 条，在途差异 {} 条",
                seats.size(), preheated, tightened, inFlight);
    }

    private void write(String key, int remain) {
        stringRedisTemplate.opsForValue()
                .set(key, String.valueOf(remain), TicketCacheConstant.STOCK_CACHE_MINUTES, TimeUnit.MINUTES);
    }
}