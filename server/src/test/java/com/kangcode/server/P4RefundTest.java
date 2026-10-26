package com.kangcode.server;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
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

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P4 任务 4-5 —— 退票 OrderServiceImpl.refund 单元测试。
 * 全程 Mockito，不依赖 MySQL / Redis / RabbitMQ。
 *
 * <p>状态机：0 待支付 / 1 已支付 / 2 已取消 / 3 已出票 / 4 已退票。
 * <p>核心考点：
 * <ol>
 *   <li>退票只针对"已支付(1) 或 已出票(3)"，改状态为 4（不是 2 已取消，也不是别的）；</li>
 *   <li>UPDATE 只能动 status / update_time，<b>绝不能误改 show_date</b>（条件用 in，不用 or）；</li>
 *   <li>退票消息 reason = REFUND，和取消(CANCEL)/超时(TIMEOUT) 区分；</li>
 *   <li>归属校验 + 状态校验失败时都不发消息（否则库存会被多还一次）。</li>
 * </ol>
 */
class P4RefundTest {

    private static final int WAIT_PAY = 0;
    private static final int PAID = 1;
    private static final int CANCELED = 2;
    private static final int ISSUED = 3;
    private static final int REFUNDED = 4;

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

    /** 造一张属于 userId=7 的订单 */
    private MovieOrder order(int status) {
        MovieOrder o = new MovieOrder();
        o.setId(1L);
        o.setOrderNo("KR20261120000003");
        o.setUserId(7L);
        o.setScreeningId(2L);
        o.setShowDate(LocalDate.of(2026, 11, 20));
        o.setTicketType("二等座");
        o.setTicketCount(1);
        o.setStatus(status);
        return o;
    }

    /** 取出 refund 里那次 update 用的 wrapper */
    @SuppressWarnings("unchecked")
    private LambdaUpdateWrapper<MovieOrder> captureUpdateWrapper() {
        ArgumentCaptor<LambdaUpdateWrapper<MovieOrder>> cap =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(orderMapper).update(isNull(), cap.capture());
        return cap.getValue();
    }

    @Test
    @DisplayName("P4-11 正常退票：已支付(1) → 发一条释放库存消息（reason=REFUND，字段齐全）")
    void refundPaidOk() {
        when(orderMapper.selectOne(any())).thenReturn(order(PAID));
        when(orderMapper.update(isNull(), any())).thenReturn(1);   // 影响 1 行 = 退票成功

        service.refund(7L, "KR20261120000003");

        ArgumentCaptor<StockReleaseMessage> cap = ArgumentCaptor.forClass(StockReleaseMessage.class);
        verify(producer).sendStockRelease(cap.capture());

        StockReleaseMessage msg = cap.getValue();
        assertEquals("KR20261120000003", msg.getOrderNo());
        assertEquals(2L, msg.getScreeningId().longValue(), "带上了场次，消费者才能定位库存 key");
        assertEquals(LocalDate.of(2026, 11, 20), msg.getShowDate());
        assertEquals("二等座", msg.getTicketType());
        assertEquals(1, msg.getTicketCount().intValue());
        assertEquals("REFUND", msg.getReason(), "退票的 reason 必须是 REFUND（取消是 CANCEL、超时是 TIMEOUT）");
    }

    @Test
    @DisplayName("P4-12 已出票(3) 的订单也能退票")
    void refundIssuedOk() {
        when(orderMapper.selectOne(any())).thenReturn(order(ISSUED));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        service.refund(7L, "KR20261120000003");

        verify(producer).sendStockRelease(any());
    }

    @Test
    @DisplayName("P4-13 ⚠️ UPDATE 只能改 status/update_time，目标状态为 4，且绝不能碰 show_date")
    void updateOnlyTouchesStatusAndUpdateTime() {
        when(orderMapper.selectOne(any())).thenReturn(order(PAID));
        when(orderMapper.update(isNull(), any())).thenReturn(1);

        service.refund(7L, "KR20261120000003");

        LambdaUpdateWrapper<MovieOrder> w = captureUpdateWrapper();
        String set = (w.getSqlSet() == null ? "" : w.getSqlSet()).toLowerCase();
        String where = (w.getSqlSegment() == null ? "" : w.getSqlSegment()).toLowerCase();

        // SET 子句：必须改 status 和 updateTime
        assertTrue(set.contains("status"), "退票要改 status，实际 SET = " + w.getSqlSet());
        assertTrue(set.contains("update_time") || set.contains("updatetime"),
                "退票要更新 updateTime，实际 SET = " + w.getSqlSet());
        // 这是最隐蔽的坑：曾把 showDate 当成 updateTime 写进去，把放映日期覆盖成"当前时间"
        assertFalse(set.contains("travel"),
                "❌ 绝不能把放映日期(showDate/show_date)写进 SET，实际 SET = " + w.getSqlSet());

        // 目标状态必须是 4（已退票），而不是 2（已取消）
        Map<String, Object> params = w.getParamNameValuePairs();
        assertTrue(params.containsValue(REFUNDED),
                "目标状态应为 4（已退票），实际参数 = " + params);
        assertFalse(params.containsValue(CANCELED),
                "退票不能把状态改成 2（已取消），实际参数 = " + params);

        // WHERE 子句：状态条件必须能覆盖"已支付或已出票"
        assertTrue(where.contains("status"), "WHERE 要有 status 条件，实际 = " + w.getSqlSegment());
        assertTrue(where.contains("user_id") || where.contains("userId"),
                "WHERE 要带 userId 防越权，实际 = " + w.getSqlSegment());
    }

    @Test
    @DisplayName("P4-14 只能退自己的订单：查不到（归属校验带 userId）→ 404，且不发消息")
    void refundOthersOrder() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.refund(999L, "KR20261120000003"));

        assertEquals(ResultCode.NOT_FOUND.getCode(), ex.getCode());
        verify(producer, never()).sendStockRelease(any());
    }

    @Test
    @DisplayName("P4-15 待支付(0) 的订单不能退票 → 提示未支付，且不发消息")
    void cannotRefundUnpaid() {
        when(orderMapper.selectOne(any())).thenReturn(order(WAIT_PAY));
        when(orderMapper.update(isNull(), any())).thenReturn(0);   // 条件 in(1,3) 没命中

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.refund(7L, "KR20261120000003"));

        assertEquals(ResultCode.BUSINESS_ERROR.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("未支付"),
                "未支付的订单应提示「未支付」，实际 = " + ex.getMessage());
        verify(producer, never()).sendStockRelease(any());
    }

    @Test
    @DisplayName("P4-16 重复退票：已退票(4) 再退 → 不通过，且不会二次释放库存")
    void refundTwice() {
        when(orderMapper.selectOne(any())).thenReturn(order(REFUNDED));
        when(orderMapper.update(isNull(), any())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.refund(7L, "KR20261120000003"));

        assertEquals(ResultCode.BUSINESS_ERROR.getCode(), ex.getCode());
        // 这条断言是重点：重复退票若再发一次消息，库存会被多还一次
        verify(producer, never()).sendStockRelease(any());
    }
}
