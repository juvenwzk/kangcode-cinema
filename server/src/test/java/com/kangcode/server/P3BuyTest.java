package com.kangcode.server;

import com.kangcode.common.constant.RedisKeyConstant;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.pojo.dto.OrderCreateMessage;
import com.kangcode.pojo.dto.TicketBuyRequest;
import com.kangcode.pojo.vo.TicketBuyVO;
import com.kangcode.server.mapper.TrainSeatMapper;
import com.kangcode.server.mq.TicketOrderProducer;
import com.kangcode.server.service.impl.TicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3 核心购票链路 —— buy() 各分支的单元测试。
 * 全程 Mockito，不依赖 Redis / MySQL / RabbitMQ。
 */
class P3BuyTest {

    private static final Long USER = 7L;
    private static final Long TRAIN = 2L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 5);
    private static final String SEAT = "二等座";
    private static final String STOCK_KEY = RedisKeyConstant.ticketStockKey(TRAIN, DATE.toString(), SEAT);

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private RLock lock;
    private TicketOrderProducer producer;
    private TicketServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws InterruptedException {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        RedissonClient redisson = mock(RedissonClient.class);
        lock = mock(RLock.class);
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        producer = mock(TicketOrderProducer.class);

        service = new TicketServiceImpl(redis, redisson, mock(TrainSeatMapper.class),
                producer, new DefaultRedisScript<>());
    }

    private TicketBuyRequest req(int count) {
        TicketBuyRequest r = new TicketBuyRequest();
        r.setTrainId(TRAIN);
        r.setTravelDate(DATE);
        r.setSeatType(SEAT);
        r.setTicketCount(count);
        return r;
    }

    private void dupPass() {
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
    }

    @Test
    @DisplayName("P3-1 正常下单：返回订单号并把消息发出去")
    void buyOk() {
        dupPass();
        when(redis.execute(any(), anyList(), any())).thenReturn(9L);   // Lua 预扣后剩 9
        when(ops.increment(anyString())).thenReturn(1L);               // 订单号序列

        TicketBuyVO vo = service.buy(req(1), USER);

        assertNotNull(vo.getOrderNo());
        assertTrue(vo.getOrderNo().startsWith("KR20261005"), "订单号应以 KR+日期 开头");
        assertEquals(0, vo.getStatus().intValue());

        ArgumentCaptor<OrderCreateMessage> cap = ArgumentCaptor.forClass(OrderCreateMessage.class);
        verify(producer).sendOrderCreate(cap.capture());
        assertEquals(TRAIN, cap.getValue().getTrainId());
        assertEquals(USER, cap.getValue().getUserId());
        assertEquals(SEAT, cap.getValue().getSeatType());
        // ↓↓↓ 这一条会暴露「消息里漏传 travelDate」的问题
        assertEquals(DATE, cap.getValue().getTravelDate(), "下单消息里必须带 travelDate，否则落库查不到座位价格");

        verify(lock).unlock();
    }

    @Test
    @DisplayName("P3-2 幂等：30 秒内重复提交 -> 应为 1002「请勿重复下单」")
    void buyDuplicate() {
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.buy(req(1), USER));

        assertEquals(ResultCode.ORDER_REPEATED.getCode(), ex.getCode(),
                "重复下单应返回 1002（ORDER_REPEATED），而不是 400");
        verify(producer, never()).sendOrderCreate(any());
    }

    @Test
    @DisplayName("P3-3 抢锁失败 -> 1000「当前购票人数较多」")
    void buyLockFail() throws InterruptedException {
        dupPass();
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.buy(req(1), USER));

        assertEquals(ResultCode.BUSINESS_ERROR.getCode(), ex.getCode());
        verify(producer, never()).sendOrderCreate(any());
    }

    @Test
    @DisplayName("P3-4 余票不足（Lua 返回 -1）-> 1001")
    void buyNotEnough() {
        dupPass();
        when(redis.execute(any(), anyList(), any())).thenReturn(-1L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.buy(req(1), USER));

        assertEquals(ResultCode.STOCK_NOT_ENOUGH.getCode(), ex.getCode());
        verify(producer, never()).sendOrderCreate(any());
    }

    @Test
    @DisplayName("P3-5 缓存没放票（Lua 返回 -2）-> 1001 且提示「还没有放票」")
    void buyNotPublished() {
        dupPass();
        when(redis.execute(any(), anyList(), any())).thenReturn(-2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.buy(req(1), USER));

        assertEquals(ResultCode.STOCK_NOT_ENOUGH.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("放票"), "应提示还没放票");
    }

    @Test
    @DisplayName("P3-6 发 MQ 失败 -> 回补预扣的库存（不能票被扣了却没订单）")
    void buyMqFailRollback() {
        dupPass();
        when(redis.execute(any(), anyList(), any())).thenReturn(9L);
        when(ops.increment(anyString())).thenReturn(1L);
        doThrow(new RuntimeException("MQ 挂了")).when(producer).sendOrderCreate(any());

        assertThrows(RuntimeException.class, () -> service.buy(req(2), USER));

        verify(ops).increment(STOCK_KEY, 2);   // 把 2 张票加回去
    }
}
