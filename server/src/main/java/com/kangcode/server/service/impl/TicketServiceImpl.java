package com.kangcode.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangcode.common.constant.RedisKeyConstant;

import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.pojo.dto.OrderCreateMessage;
import com.kangcode.pojo.dto.TicketBuyRequest;
import com.kangcode.pojo.vo.TicketBuyVO;
import com.kangcode.pojo.entity.TrainSeat;
import com.kangcode.server.mapper.TrainSeatMapper;
import com.kangcode.server.mq.TicketOrderProducer;
import com.kangcode.server.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
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
    /** 下单幂等键 TTL：30 秒（防手抖重复提交） */
    private static final long DUP_TTL_SECONDS = 30;
    /** 抢锁最多等待 3 秒 */
    private static final long LOCK_WAIT_SECONDS = 3;
    /** 锁租约 10 秒：即使服务宕机，锁也会自动释放，不会死锁 */
    private static final long LOCK_LEASE_SECONDS = 10;


    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final TrainSeatMapper trainSeatMapper;
    private final TicketOrderProducer ticketOrderProducer;
    private final DefaultRedisScript<Long> stockDeductScript;

    @Override
    public Integer queryRemain(Long trainId, LocalDate travelDate, String seatType) {
        String date = travelDate.toString();
        // 注意：余票必须用「库存 key」ticket:stock:*，不能用「锁 key」ticket:lock:*。
        // 锁 key 归 Redisson 的 RLock 使用（Hash 结构），把余票字符串写进去会导致购票时 WRONGTYPE。
        String key = RedisKeyConstant.ticketStockKey(trainId, date, seatType);

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
        // ① 幂等校验（order:dup:{userId}:{trainId}:{travelDate}）
        Long trainId=request.getTrainId();
        LocalDate travelDate=request.getTravelDate();
        String date=travelDate.toString();
        String seatType=request.getSeatType();
        Integer count=request.getTicketCount();
        String stockKey=RedisKeyConstant.ticketStockKey(trainId,date,seatType);

        String dupKey=RedisKeyConstant.orderDupKey(userId,trainId,date);
        Boolean first=stringRedisTemplate.opsForValue()
                .setIfAbsent(dupKey,"1",DUP_TTL_SECONDS,TimeUnit.SECONDS);

        if(!Boolean.TRUE.equals(first)) {
        throw new BusinessException(ResultCode.ORDER_REPEATED);
        }
        //      2. Redisson 分布式锁（ticket:lock:{trainId}:{date}:{seatType}）
        RLock lock=redissonClient.getLock(RedisKeyConstant.ticketLockKey(trainId,date,seatType));        boolean locked=false;
        boolean deducted=false;
        try {
            locked=lock.tryLock(LOCK_WAIT_SECONDS, LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if(!locked){
                throw  new BusinessException(ResultCode.BUSINESS_ERROR.getCode(),"当前购票人数较多，请稍后重试");
            }

            //      3. Redis Lua 原子预扣余票，不足则抛 STOCK_NOT_ENOUGH
            Long result=stringRedisTemplate.execute(
                    stockDeductScript,
                    Collections.singletonList(stockKey),
                    String.valueOf(count));

            if(result==null){
                throw new BusinessException(ResultCode.SYSTEM_ERROR);
            }
            if(result==-2L){
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH.getCode(),"该车次还没有放票，请稍后再试");
            }
            if (result == -1L) {
                throw new BusinessException(ResultCode.STOCK_NOT_ENOUGH);
            }
            deducted=true;
            log.info("预扣库存成功 key={} 剩余={} userId={}", stockKey, result, userId);

            //      4. 生成订单号（Redis INCR 每日序列）
            String orderNo=generateOrderNo(travelDate);

            //      5. 发送 RabbitMQ 下单消息（异步落库订单）
            ticketOrderProducer.sendOrderCreate(OrderCreateMessage.builder()
                    .messageId(orderNo)
                    .orderNo(orderNo)
                    .userId(userId)
                    .trainId(trainId)
                    .travelDate(travelDate)
                    .seatType(seatType)
                    .ticketCount(count)
                    .passengerName(request.getPassengerName())
                    .idCardNo(request.getIdCardNo())
                    .build()
            );

            return TicketBuyVO.builder()
                    .orderNo(orderNo)
                    .userId(userId)
                    .status(0)
                    .message("下单成功，请在30分钟内完成支付")
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SYSTEM_ERROR);
        }catch (RuntimeException e){
            //库存已经扣了，但后面（比如发MQ）失败-->把库存还回去，避免票被扣了却没有订单
            if(deducted){
                stringRedisTemplate.opsForValue().increment(stockKey,count);
                log.warn("下单失败，已回补预扣库存 key={} count={}", stockKey, count);
            }
            throw e;
        }finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

    }

    /**
     * 订单号：KR + 乘车日期 + 6 位每日序号，例如 KR20261005000001
     */
    private String generateOrderNo(LocalDate travelDate) {
        String day = travelDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long seq = stringRedisTemplate.opsForValue().increment(RedisKeyConstant.orderSeqKey(day));
        long value = seq == null ? 1L : seq;
        return "KR" + day + String.format("%06d", value);
    }
}
