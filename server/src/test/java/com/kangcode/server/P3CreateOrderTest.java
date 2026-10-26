package com.kangcode.server;

import com.kangcode.pojo.dto.OrderCreateMessage;
import com.kangcode.pojo.entity.Screening;
import com.kangcode.pojo.entity.MovieOrder;
import com.kangcode.pojo.entity.MovieOrderItem;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P3 异步落库 —— OrderServiceImpl.createOrder 单元测试。
 * 全程 Mockito，不依赖 MySQL / RabbitMQ。
 */
class P3CreateOrderTest {

    private MovieOrderMapper orderMapper;
    private ScreeningMapper screeningMapper;
    private ScreeningSeatMapper seatMapper;
    private MovieOrderItemMapper itemMapper;
    private TicketOrderProducer producer;
    private OrderServiceImpl service;

    /**
     * ⚠️ 必须初始化 MyBatis-Plus 元数据。
     * createOrder 里用了 {@code new LambdaQueryWrapper<MovieOrder>().eq(MovieOrder::getOrderNo, ...)}，
     * 方法引用要解析成列名，而纯单测没有 Spring，元数据是空的 →
     * {@code MybatisPlusException: can not find lambda cache for this entity}。
     * （以前单独跑这个类会挂，只是因为全量跑时有别的 Spring 测试先初始化了元数据才"看起来能过"。）
     */
    @BeforeAll
    static void initMp() {
        MpLambdaSupport.init(MovieOrder.class, ScreeningSeat.class, Screening.class);
    }

    @BeforeEach
    void setUp() {
        orderMapper = mock(MovieOrderMapper.class);
        screeningMapper = mock(ScreeningMapper.class);
        seatMapper = mock(ScreeningSeatMapper.class);
        itemMapper = mock(MovieOrderItemMapper.class);
        producer = mock(TicketOrderProducer.class);
        service = new OrderServiceImpl(orderMapper, screeningMapper, seatMapper,
                itemMapper, mock(StringRedisTemplate.class), producer, mock(StockReleaseLogMapper.class));
    }

    private OrderCreateMessage msg() {
        return OrderCreateMessage.builder()
                .messageId("KR20261005000001")
                .orderNo("KR20261005000001")
                .userId(7L)
                .screeningId(2L)
                .showDate(LocalDate.of(2026, 10, 5))
                .ticketType("二等座")
                .ticketCount(1)
                .build();
    }

    @Test
    @DisplayName("P3-7 幂等：订单已存在 -> 直接返回，不重复落库")
    void idempotent() {
        when(orderMapper.selectOne(any())).thenReturn(new MovieOrder());

        service.createOrder(msg());

        verify(orderMapper, never()).insert(any(MovieOrder.class));
        verify(producer, never()).sendOrderTimeout(any());
    }

    @Test
    @DisplayName("P3-8 场次不存在 -> 丢弃这条消息")
    void screeningMissing() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(screeningMapper.selectById(anyLong())).thenReturn(null);

        service.createOrder(msg());

        verify(orderMapper, never()).insert(any(MovieOrder.class));
    }

    @Test
    @DisplayName("P3-9 正常落库：状态=0、金额=票价×张数、发超时消息、DB 库存扣减")
    void createOk() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        Screening screening = new Screening();
        screening.setCinemaId(1L);
        screening.setHallId(4L);
        when(screeningMapper.selectById(anyLong())).thenReturn(screening);

        ScreeningSeat seat = new ScreeningSeat();
        seat.setPrice(55300L);   // 553.00 元（单位：分）
        when(seatMapper.selectOne(any())).thenReturn(seat);

        service.createOrder(msg());

        ArgumentCaptor<MovieOrder> cap = ArgumentCaptor.forClass(MovieOrder.class);
        verify(orderMapper).insert(cap.capture());
        MovieOrder saved = cap.getValue();

        assertEquals("KR20261005000001", saved.getOrderNo());
        assertEquals(0, saved.getStatus().intValue(), "新订单状态应为 0（待支付）");
        assertEquals(55300L, saved.getTotalAmount().longValue(), "1 张 × 55300 分");
        assertNotNull(saved.getPayExpireTime(), "应有支付过期时间");

        verify(seatMapper).update(any(), any());        // DB 库存也跟着减
        verify(producer).sendOrderTimeout(any());        // 发超时关单延时消息
    }

    @Test
    @DisplayName("P3-10 多张票：金额 = 单价 × 数量")
    void amountMultiply() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        Screening screening = new Screening();
        when(screeningMapper.selectById(anyLong())).thenReturn(screening);
        ScreeningSeat seat = new ScreeningSeat();
        seat.setPrice(10000L);
        when(seatMapper.selectOne(any())).thenReturn(seat);

        OrderCreateMessage m = msg();
        m.setTicketCount(3);
        service.createOrder(m);

        ArgumentCaptor<MovieOrder> cap = ArgumentCaptor.forClass(MovieOrder.class);
        verify(orderMapper).insert(cap.capture());
        assertEquals(30000L, cap.getValue().getTotalAmount().longValue());
    }

    @Test
    @DisplayName("P3-11 ⚠️ 票价查不到时：当前会按 0 元落库（隐患，期望是失败/告警）")
    void priceMissingCurrentBehavior() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        Screening screening = new Screening();
        when(screeningMapper.selectById(anyLong())).thenReturn(screening);
        when(seatMapper.selectOne(any())).thenReturn(null);   // 查不到票价

        service.createOrder(msg());

        ArgumentCaptor<MovieOrder> cap = ArgumentCaptor.forClass(MovieOrder.class);
        verify(orderMapper).insert(cap.capture());
        assertEquals(0L, cap.getValue().getTotalAmount().longValue(),
                "现在会以 0 元落库 —— 这里本应拒绝下单并告警");
    }

    @Test
    @DisplayName("P3-12 落订单明细：ticketCount=3 → 插入 3 条 movie_order_item（带乘客信息）")
    void createOrderItems() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        Screening screening = new Screening();
        when(screeningMapper.selectById(anyLong())).thenReturn(screening);
        ScreeningSeat seat = new ScreeningSeat();
        seat.setPrice(10000L);
        when(seatMapper.selectOne(any())).thenReturn(seat);

        OrderCreateMessage m = msg();
        m.setTicketCount(3);
        m.setPassengerName("张三");
        m.setIdCardNo("110101199001011234");
        service.createOrder(m);

        ArgumentCaptor<MovieOrderItem> cap = ArgumentCaptor.forClass(MovieOrderItem.class);
        verify(itemMapper, times(3)).insert(cap.capture());
        MovieOrderItem item = cap.getValue();
        assertEquals("张三", item.getPassengerName());
        assertEquals("110101199001011234", item.getIdCardNo());
        assertEquals(0, item.getStatus().intValue(), "新明细应为 0（待出票）");
    }
}
