package com.kangcode.server;

import com.kangcode.pojo.dto.StockReleaseMessage;
import com.kangcode.pojo.entity.MovieOrder;
import com.kangcode.pojo.entity.ScreeningSeat;
import com.kangcode.server.mapper.ScreeningMapper;
import com.kangcode.server.mapper.MovieOrderItemMapper;
import com.kangcode.server.mapper.MovieOrderMapper;
import com.kangcode.server.mapper.ScreeningSeatMapper;
import com.kangcode.server.mapper.StockReleaseLogMapper;
import com.kangcode.server.mq.TicketOrderProducer;
import com.kangcode.server.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4 任务 4-3 / 4-4 —— 超时自动关单 与 库存回补 单元测试。
 *
 * <p>这两块是"最容易把库存搞乱"的地方：
 * <ul>
 *   <li>已支付的订单收到超时消息，<b>绝不能</b>关单、更不能回补库存；</li>
 *   <li>同一张订单只能回补一次。</li>
 * </ul>
 */
class P4TimeoutCloseTest {

    private static final int WAIT_PAY = 0;
    private static final int PAID = 1;
    private static final int CANCELED = 2;

    private MovieOrderMapper orderMapper;
    private ScreeningSeatMapper seatMapper;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private TicketOrderProducer producer;
    private OrderServiceImpl service;

    /** Lambda Wrapper 要把方法引用解析成列名，得先有实体的元数据（纯单测里没有 Spring） */
    @BeforeAll
    static void initMp() {
        MpLambdaSupport.init(MovieOrder.class, ScreeningSeat.class);
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        orderMapper = mock(MovieOrderMapper.class);
        seatMapper = mock(ScreeningSeatMapper.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        producer = mock(TicketOrderProducer.class);

        service = new OrderServiceImpl(orderMapper, mock(ScreeningMapper.class),
                seatMapper, mock(MovieOrderItemMapper.class), redis, producer, mock(StockReleaseLogMapper.class));
    }

    private MovieOrder order(int status) {
        MovieOrder o = new MovieOrder();
        o.setOrderNo("KR20261123000001");
        o.setUserId(11L);
        o.setScreeningId(1L);
        o.setShowDate(LocalDate.of(2026, 11, 23));
        o.setTicketType("二等座");
        o.setTicketCount(1);
        o.setStatus(status);
        return o;
    }

    // ---------------- 4-3 超时关单 ----------------

    @Test
    @DisplayName("P4-7 待支付超时：状态 0 → 2，并发一条释放库存消息（reason=TIMEOUT）")
    void closeTimeoutOk() {
        when(orderMapper.update(isNull(), any())).thenReturn(1);      // 条件命中，关单成功
        when(orderMapper.selectOne(any())).thenReturn(order(WAIT_PAY));

        service.closeTimeoutOrder("KR20261123000001");

        ArgumentCaptor<StockReleaseMessage> cap = ArgumentCaptor.forClass(StockReleaseMessage.class);
        verify(producer).sendStockRelease(cap.capture());

        StockReleaseMessage msg = cap.getValue();
        assertEquals("KR20261123000001", msg.getOrderNo());
        assertEquals(1L, msg.getScreeningId().longValue());
        assertEquals(LocalDate.of(2026, 11, 23), msg.getShowDate());
        assertEquals(1, msg.getTicketCount().intValue());
        assertEquals("TIMEOUT", msg.getReason(), "超时的 reason 必须是 TIMEOUT（取消是 CANCEL）");
    }

    @Test
    @DisplayName("P4-8 ⚠️ 已支付的订单收到超时消息 → 忽略，绝不能关单、更不能回补库存")
    void paidOrderNotClosed() {
        // 用户在第 29 分钟付了款，延时消息却在之后才到 → WHERE status=0 不命中
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        service.closeTimeoutOrder("KR20261123000001");

        // 这两条断言是这套设计的命门：一旦漏了，用户付了钱票还被放回去
        verify(producer, never()).sendStockRelease(any());
        verify(orderMapper, never()).selectOne(any());     // 提前 return，压根不往下走
    }

    @Test
    @DisplayName("P4-9 已取消的订单收到超时消息 → 忽略，不会二次回补")
    void canceledOrderNotClosedAgain() {
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        service.closeTimeoutOrder("KR20261123000001");

        verify(producer, never()).sendStockRelease(any());
    }

    // ---------------- 4-4 库存回补 ----------------

    @Test
    @DisplayName("P4-10 回补库存：Redis 加回去 + DB 用 setSql 做加法（各一次）")
    void releaseStockOk() {
        when(seatMapper.update(isNull(), any())).thenReturn(1);

        service.releaseStock(StockReleaseMessage.builder()
                .orderNo("KR20261123000001")
                .screeningId(1L)
                .showDate(LocalDate.of(2026, 11, 23))
                .ticketType("二等座")
                .ticketCount(2)
                .reason("TIMEOUT")
                .build());

        // Redis 侧：key 必须是余票 key（screening:stock:*），且数量正确
        verify(valueOps).increment(eq("screening:stock:1:2026-11-23:二等座"), eq(2L));

        // DB 侧：也能摸到一次 update
        verify(seatMapper).update(isNull(), any());
    }

    @Test
    @DisplayName("P4-10b 回补用的 key 绝不能是锁 key（否则购票时 WRONGTYPE）")
    void releaseStockUsesStockKey() {
        when(seatMapper.update(isNull(), any())).thenReturn(1);

        service.releaseStock(StockReleaseMessage.builder()
                .orderNo("KR20261123000001")
                .screeningId(1L)
                .showDate(LocalDate.of(2026, 11, 23))
                .ticketType("二等座")
                .ticketCount(1)
                .reason("CANCEL")
                .build());

        ArgumentCaptor<String> keyCap = ArgumentCaptor.forClass(String.class);
        verify(valueOps).increment(keyCap.capture(), anyLong());

        String key = keyCap.getValue();
        assertTrue(key.startsWith("screening:stock:"), "回补必须写余票 key，实际是：" + key);
        assertFalse(key.startsWith("screening:lock:"),
                "写进锁 key 会让余票变成 Hash 类型，购票 Lua 会报 WRONGTYPE");
    }
}
