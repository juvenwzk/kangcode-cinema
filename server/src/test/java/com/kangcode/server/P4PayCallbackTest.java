package com.kangcode.server;

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
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4 任务 4-2 —— 支付回调 OrderServiceImpl.paySuccess 单元测试。
 *
 * <p>支付平台会**重复推送**回调（没收到 200 就一直重试），
 * 所以这个方法的全部价值就一句话：
 * <b>把 status = 0 写进 WHERE，靠影响行数判断"是不是第一次"</b>。
 */
class P4PayCallbackTest {

    private MovieOrderMapper orderMapper;
    private TicketOrderProducer producer;
    private OrderServiceImpl service;

    /** Lambda Wrapper 要把方法引用解析成列名，得先有实体的元数据（纯单测里没有 Spring） */
    @BeforeAll
    static void initMp() {
        MpLambdaSupport.init(MovieOrder.class, ScreeningSeat.class);
    }

    @BeforeEach
    void setUp() {
        orderMapper = mock(MovieOrderMapper.class);
        producer = mock(TicketOrderProducer.class);
        service = new OrderServiceImpl(orderMapper, mock(ScreeningMapper.class),
                mock(ScreeningSeatMapper.class), mock(MovieOrderItemMapper.class),
                mock(StringRedisTemplate.class), producer, mock(StockReleaseLogMapper.class));
    }

    @Test
    @DisplayName("P4-5 首次回调：影响 1 行，正常置为已支付")
    void payFirstTime() {
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        assertDoesNotThrow(() -> service.paySuccess("MT20261120000002"));

        // 只更新一次，且用的是 wrapper 方式（第一个参数 null）
        verify(orderMapper, times(1)).update(isNull(), any());
    }

    @Test
    @DisplayName("P4-6 重复回调：影响 0 行 → 幂等返回，不报错")
    void payRepeatedIsIdempotent() {
        // 第二次回调时 status 已经是 1，WHERE status=0 不命中 → 影响 0 行
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        // 关键：不能抛异常。支付平台收到非 200 会一直重试，越重试越乱
        assertDoesNotThrow(() -> service.paySuccess("MT20261120000002"),
                "重复回调必须静默返回，不能抛异常");

        verify(orderMapper, times(1)).update(isNull(), any());
    }

    @Test
    @DisplayName("P4-6b 已取消的订单收到迟到的回调 → 同样静默忽略")
    void payAfterCanceledIsIgnored() {
        // 订单已取消：WHERE status=0 依然不命中 → 0 行
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        assertDoesNotThrow(() -> service.paySuccess("MT20261120000003"));
    }

    @Test
    @DisplayName("P4-6c 支付成功不该触发任何库存回补（票已经卖出去了）")
    void payNeverReleasesStock() {
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        service.paySuccess("MT20261120000002");

        verify(producer, never()).sendStockRelease(any());
        verify(producer, never()).sendOrderTimeout(any());
    }
}
