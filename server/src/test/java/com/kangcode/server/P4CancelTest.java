package com.kangcode.server;

import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.pojo.dto.StockReleaseMessage;
import com.kangcode.pojo.entity.TrainOrder;
import com.kangcode.pojo.entity.TrainSeat;
import com.kangcode.server.mapper.TrainMapper;
import com.kangcode.server.mapper.TrainOrderItemMapper;
import com.kangcode.server.mapper.TrainOrderMapper;
import com.kangcode.server.mapper.TrainSeatMapper;
import com.kangcode.server.mq.TicketOrderProducer;
import com.kangcode.server.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4 任务 4-1 —— 用户取消订单 OrderServiceImpl.cancel 单元测试。
 * 全程 Mockito，不依赖 MySQL / Redis / RabbitMQ。
 *
 * <p>核心考点只有两个：
 * <ol>
 *   <li><b>归属校验</b>：查询带 userId，别人的订单查不到 → 404</li>
 *   <li><b>状态机（幂等）</b>：只有"待支付"能取消；重复取消不会重复发库存回补消息</li>
 * </ol>
 */
class P4CancelTest {

    private static final int WAIT_PAY = 0;
    private static final int PAID = 1;
    private static final int CANCELED = 2;

    private TrainOrderMapper orderMapper;
    private TicketOrderProducer producer;
    private OrderServiceImpl service;

    /** Lambda Wrapper 要把方法引用解析成列名，得先有实体的元数据（纯单测里没有 Spring） */
    @BeforeAll
    static void initMp() {
        MpLambdaSupport.init(TrainOrder.class, TrainSeat.class);
    }

    @BeforeEach
    void setUp() {
        orderMapper = mock(TrainOrderMapper.class);
        producer = mock(TicketOrderProducer.class);
        service = new OrderServiceImpl(orderMapper, mock(TrainMapper.class),
                mock(TrainSeatMapper.class), mock(TrainOrderItemMapper.class),
                mock(StringRedisTemplate.class), producer);
    }

    /** 造一张属于 userId=7 的订单 */
    private TrainOrder order(int status) {
        TrainOrder o = new TrainOrder();
        o.setId(1L);
        o.setOrderNo("KR20261120000001");
        o.setUserId(7L);
        o.setTrainId(2L);
        o.setTravelDate(LocalDate.of(2026, 11, 20));
        o.setSeatType("二等座");
        o.setTicketCount(1);
        o.setStatus(status);
        return o;
    }

    @Test
    @DisplayName("P4-1 正常取消：状态 0 → 2，并发一条释放库存消息（reason=CANCEL）")
    void cancelOk() {
        when(orderMapper.selectOne(any())).thenReturn(order(WAIT_PAY));
        when(orderMapper.update(isNull(), any())).thenReturn(1);   // 影响 1 行 = 取消成功

        service.cancel(7L, "KR20261120000001");

        ArgumentCaptor<StockReleaseMessage> cap = ArgumentCaptor.forClass(StockReleaseMessage.class);
        verify(producer).sendStockRelease(cap.capture());

        StockReleaseMessage msg = cap.getValue();
        assertEquals("KR20261120000001", msg.getOrderNo());
        assertEquals(2L, msg.getTrainId().longValue(), "带上了车次，消费者才能定位库存 key");
        assertEquals(LocalDate.of(2026, 11, 20), msg.getTravelDate());
        assertEquals("二等座", msg.getSeatType());
        assertEquals(1, msg.getTicketCount().intValue());
        assertEquals("CANCEL", msg.getReason(), "取消的 reason 必须是 CANCEL（超时是 TIMEOUT）");
    }

    @Test
    @DisplayName("P4-2 只能取消自己的订单：查不到（归属校验带 userId）→ 404")
    void cancelOthersOrder() {
        when(orderMapper.selectOne(any())).thenReturn(null);   // 别人的订单，查出来是 null

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(999L, "KR20261120000001"));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        verify(producer, never()).sendStockRelease(any());     // 更不该放库存
    }

    @Test
    @DisplayName("P4-3 已支付的订单不能取消 → 1003（ORDER_PAID）")
    void cannotCancelPaid() {
        when(orderMapper.selectOne(any())).thenReturn(order(PAID));
        when(orderMapper.update(isNull(), any())).thenReturn(0);   // 条件 status=0 没命中

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(7L, "KR20261120000001"));

        assertEquals(ResultCode.ORDER_PAID.getCode(), ex.getCode());
        verify(producer, never()).sendStockRelease(any());
    }

    @Test
    @DisplayName("P4-4 重复取消 → 1004（ORDER_CANCELED），且不会二次释放库存")
    void cancelTwice() {
        when(orderMapper.selectOne(any())).thenReturn(order(CANCELED));
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.cancel(7L, "KR20261120000001"));

        assertEquals(ResultCode.ORDER_CANCELED.getCode(), ex.getCode());
        // 这条断言是重点：重复取消若再发一次消息，库存会被多还一次
        verify(producer, never()).sendStockRelease(any());
    }
}
