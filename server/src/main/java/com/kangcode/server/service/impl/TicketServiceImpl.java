package com.kangcode.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangcode.common.constant.RedisKeyConstant;
import com.kangcode.framework.mq.TicketOrderProducer;
import com.kangcode.pojo.dto.TicketBuyRequest;
import com.kangcode.pojo.vo.TicketBuyVO;
import com.kangcode.pojo.entity.TrainSeat;
import com.kangcode.server.mapper.TrainSeatMapper;
import com.kangcode.server.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 余票/购票服务实现
 */



@Slf4j
@Service
@RequiredArgsConstructor
public class  TicketServiceImpl implements TicketService {

    /** 空值标记：缓存里存这个字符串表示“DB 里也没有”，用来防穿透 */
    private static final String EMPTY_VALUE = "EMPTY";
    /** 正常余票缓存 TTL：10 分钟 */
    private static final long STOCK_TTL_SECONDS = 10 * 60;
    /** TTL 随机抖动上限：60 秒（防雪崩） */
    private static final long TTL_JITTER_SECONDS = 60;
    /** 空值缓存 TTL：60 秒 */
    private static final long EMPTY_TTL_SECONDS = 60;


    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final TrainSeatMapper trainSeatMapper;
    private final TicketOrderProducer ticketOrderProducer;

    @Override
    public Integer queryRemain(Long trainId, LocalDate travelDate, String seatType) {
        String date = travelDate.toString();
        String key = RedisKeyConstant.ticketLockKey(trainId, date, seatType);

        // 1. 缓存命中直接返回
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) {
            return EMPTY_VALUE.equals(cached) ? 0 : Integer.valueOf(cached);
        }

        // 2. 抢互斥锁
        String rebuildLockKey = key + "#rebuild";
        Boolean got = stringRedisTemplate.opsForValue()
                .setIfAbsent(rebuildLockKey, "1", 10, TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(got)) {
            // 抢到锁 → 查库+写缓存
            try {
                return loadFromDbAndCache(key, trainId, travelDate, seatType);
            } finally {
                stringRedisTemplate.delete(rebuildLockKey);
            }
        }

        // 3. 没抢到锁 → 自旋等缓存建好
        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return EMPTY_VALUE.equals(cached) ? 0 : Integer.valueOf(cached);
            }
        }

        // 4. 兜底：直查 DB
        return loadFromDbAndCache(key, trainId, travelDate, seatType);
    }

    /** 从数据库加载余票并写入缓存 */
    private Integer loadFromDbAndCache(String key, Long trainId,
                                       LocalDate travelDate, String seatType) {
        TrainSeat seat = trainSeatMapper.selectOne(new LambdaQueryWrapper<TrainSeat>()
                .eq(TrainSeat::getTrainId, trainId)
                .eq(TrainSeat::getTravelDate, travelDate)
                .eq(TrainSeat::getSeatType, seatType));

        if (seat == null) {
            stringRedisTemplate.opsForValue()
                    .set(key, EMPTY_VALUE, EMPTY_TTL_SECONDS, TimeUnit.SECONDS);
            return 0;
        }

        long ttl = STOCK_TTL_SECONDS + ThreadLocalRandom.current().nextInt((int) TTL_JITTER_SECONDS);
        stringRedisTemplate.opsForValue()
                .set(key, String.valueOf(seat.getRemain()), ttl, TimeUnit.SECONDS);
        return seat.getRemain();
    }

    @Override
    public TicketBuyVO buy(TicketBuyRequest request, Long userId) {
        // TODO: 1. 幂等校验（order:dup:{userId}:{trainId}:{travelDate}）
        //      2. Redisson 分布式锁（ticket:lock:{trainId}:{date}:{seatType}）
        //      3. Redis Lua 原子预扣余票，不足则抛 STOCK_NOT_ENOUGH
        //      4. 生成订单号（Redis INCR 每日序列）
        //      5. 发送 RabbitMQ 下单消息（异步落库订单）
        return null;
    }
}
