package com.kangcode.server;

import com.kangcode.common.constant.RedisKeyConstant;
import com.kangcode.pojo.entity.ScreeningSeat;
import com.kangcode.server.mapper.ScreeningSeatMapper;
import com.kangcode.server.mq.TicketOrderProducer;
import com.kangcode.server.service.impl.TicketServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2 缓存与锁 —— 余票查询的缓存逻辑（防穿透 + 空值标记）。
 * 全程 Mockito，不依赖真实 Redis。
 */
class P2CacheTest {

    private static final Long SCREENING = 2L;
    private static final LocalDate DATE = LocalDate.of(2026, 10, 5);
    private static final String SEAT = "普通票";
    private static final String KEY = RedisKeyConstant.ticketStockKey(SCREENING, DATE.toString(), SEAT);

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private ScreeningSeatMapper seatMapper;
    private TicketServiceImpl service;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        seatMapper = mock(ScreeningSeatMapper.class);

        // 布隆过滤器 mock：contains 一律返回 true（"已登记"），否则 queryRemain 第一道闸门直接拦截
        @SuppressWarnings("unchecked")
        RBloomFilter<String> bloom = mock(RBloomFilter.class);
        when(bloom.contains(anyString())).thenReturn(true);

        service = new TicketServiceImpl(bloom, redis, mock(RedissonClient.class), seatMapper,
                mock(TicketOrderProducer.class), new DefaultRedisScript<>());
    }

    @Test
    @DisplayName("P2-1 缓存命中：直接返回，不查库")
    void cacheHit() {
        when(ops.get(KEY)).thenReturn("42");

        assertEquals(42, service.queryRemain(SCREENING, DATE, SEAT).intValue());
        verify(seatMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("P2-2 缓存未命中：查库并写回缓存")
    void cacheMissLoadFromDb() {
        when(ops.get(KEY)).thenReturn(null);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);

        ScreeningSeat seat = new ScreeningSeat();
        seat.setRemain(30);
        when(seatMapper.selectOne(any())).thenReturn(seat);

        assertEquals(30, service.queryRemain(SCREENING, DATE, SEAT).intValue());
        verify(ops).set(eq(KEY), eq("30"), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("P2-3 库里也没有：写空值标记 EMPTY，返回 0（防穿透）")
    void cacheMissEmpty() {
        when(ops.get(KEY)).thenReturn(null);
        when(ops.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(seatMapper.selectOne(any())).thenReturn(null);

        assertEquals(0, service.queryRemain(SCREENING, DATE, SEAT).intValue());
        verify(ops).set(eq(KEY), eq("EMPTY"), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("P2-4 命中空值标记：返回 0，不查库")
    void emptyFlag() {
        when(ops.get(KEY)).thenReturn("EMPTY");

        assertEquals(0, service.queryRemain(SCREENING, DATE, SEAT).intValue());
        verify(seatMapper, never()).selectOne(any());
    }

    @Test
    @DisplayName("P2-5 key 规则：库存 key 与锁 key 前缀必须不同（P2 那个 WRONGTYPE 坑）")
    void keyRules() {
        String stock = RedisKeyConstant.ticketStockKey(2L, "2026-10-05", "二等座");
        String lock = RedisKeyConstant.ticketLockKey(2L, "2026-10-05", "二等座");

        assertEquals("screening:stock:2:2026-10-05:二等座", stock);
        assertEquals("screening:lock:2:2026-10-05:二等座", lock);
        assertNotEquals(stock, lock);
    }

    @Test
    @DisplayName("P2-6 幂等键 / 订单序列 key 规则")
    void otherKeys() {
        assertEquals("order:dup:7:2:2026-10-05",
                RedisKeyConstant.orderDupKey(7L, 2L, "2026-10-05"));
        assertEquals("order:seq:20261005",
                RedisKeyConstant.orderSeqKey("20261005"));
    }
}
