package com.kangcode.server;

import com.kangcode.pojo.dto.OrderCreateMessage;
import com.kangcode.pojo.entity.Train;
import com.kangcode.pojo.entity.TrainOrder;
import com.kangcode.pojo.entity.TrainOrderItem;
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

    private TrainOrderMapper orderMapper;
    private TrainMapper trainMapper;
    private TrainSeatMapper seatMapper;
    private TrainOrderItemMapper itemMapper;
    private TicketOrderProducer producer;
    private OrderServiceImpl service;

    /**
     * ⚠️ 必须初始化 MyBatis-Plus 元数据。
     * createOrder 里用了 {@code new LambdaQueryWrapper<TrainOrder>().eq(TrainOrder::getOrderNo, ...)}，
     * 方法引用要解析成列名，而纯单测没有 Spring，元数据是空的 →
     * {@code MybatisPlusException: can not find lambda cache for this entity}。
     * （以前单独跑这个类会挂，只是因为全量跑时有别的 Spring 测试先初始化了元数据才"看起来能过"。）
     */
    @BeforeAll
    static void initMp() {
        MpLambdaSupport.init(TrainOrder.class, TrainSeat.class, Train.class);
    }

    @BeforeEach
    void setUp() {
        orderMapper = mock(TrainOrderMapper.class);
        trainMapper = mock(TrainMapper.class);
        seatMapper = mock(TrainSeatMapper.class);
        itemMapper = mock(TrainOrderItemMapper.class);
        producer = mock(TicketOrderProducer.class);
        service = new OrderServiceImpl(orderMapper, trainMapper, seatMapper,
                itemMapper, mock(StringRedisTemplate.class), producer);
    }

    private OrderCreateMessage msg() {
        return OrderCreateMessage.builder()
                .messageId("KR20261005000001")
                .orderNo("KR20261005000001")
                .userId(7L)
                .trainId(2L)
                .travelDate(LocalDate.of(2026, 10, 5))
                .seatType("二等座")
                .ticketCount(1)
                .build();
    }

    @Test
    @DisplayName("P3-7 幂等：订单已存在 -> 直接返回，不重复落库")
    void idempotent() {
        when(orderMapper.selectOne(any())).thenReturn(new TrainOrder());

        service.createOrder(msg());

        verify(orderMapper, never()).insert(any(TrainOrder.class));
        verify(producer, never()).sendOrderTimeout(any());
    }

    @Test
    @DisplayName("P3-8 车次不存在 -> 丢弃这条消息")
    void trainMissing() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(trainMapper.selectById(anyLong())).thenReturn(null);

        service.createOrder(msg());

        verify(orderMapper, never()).insert(any(TrainOrder.class));
    }

    @Test
    @DisplayName("P3-9 正常落库：状态=0、金额=票价×张数、发超时消息、DB 库存扣减")
    void createOk() {
        when(orderMapper.selectOne(any())).thenReturn(null);

        Train train = new Train();
        train.setStartStationId(1L);
        train.setEndStationId(4L);
        when(trainMapper.selectById(anyLong())).thenReturn(train);

        TrainSeat seat = new TrainSeat();
        seat.setPrice(55300L);   // 553.00 元（单位：分）
        when(seatMapper.selectOne(any())).thenReturn(seat);

        service.createOrder(msg());

        ArgumentCaptor<TrainOrder> cap = ArgumentCaptor.forClass(TrainOrder.class);
        verify(orderMapper).insert(cap.capture());
        TrainOrder saved = cap.getValue();

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
        Train train = new Train();
        when(trainMapper.selectById(anyLong())).thenReturn(train);
        TrainSeat seat = new TrainSeat();
        seat.setPrice(10000L);
        when(seatMapper.selectOne(any())).thenReturn(seat);

        OrderCreateMessage m = msg();
        m.setTicketCount(3);
        service.createOrder(m);

        ArgumentCaptor<TrainOrder> cap = ArgumentCaptor.forClass(TrainOrder.class);
        verify(orderMapper).insert(cap.capture());
        assertEquals(30000L, cap.getValue().getTotalAmount().longValue());
    }

    @Test
    @DisplayName("P3-11 ⚠️ 票价查不到时：当前会按 0 元落库（隐患，期望是失败/告警）")
    void priceMissingCurrentBehavior() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        Train train = new Train();
        when(trainMapper.selectById(anyLong())).thenReturn(train);
        when(seatMapper.selectOne(any())).thenReturn(null);   // 查不到票价

        service.createOrder(msg());

        ArgumentCaptor<TrainOrder> cap = ArgumentCaptor.forClass(TrainOrder.class);
        verify(orderMapper).insert(cap.capture());
        assertEquals(0L, cap.getValue().getTotalAmount().longValue(),
                "现在会以 0 元落库 —— 这里本应拒绝下单并告警");
    }

    @Test
    @DisplayName("P3-12 落订单明细：ticketCount=3 → 插入 3 条 train_order_item（带乘客信息）")
    void createOrderItems() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        Train train = new Train();
        when(trainMapper.selectById(anyLong())).thenReturn(train);
        TrainSeat seat = new TrainSeat();
        seat.setPrice(10000L);
        when(seatMapper.selectOne(any())).thenReturn(seat);

        OrderCreateMessage m = msg();
        m.setTicketCount(3);
        m.setPassengerName("张三");
        m.setIdCardNo("110101199001011234");
        service.createOrder(m);

        ArgumentCaptor<TrainOrderItem> cap = ArgumentCaptor.forClass(TrainOrderItem.class);
        verify(itemMapper, times(3)).insert(cap.capture());
        TrainOrderItem item = cap.getValue();
        assertEquals("张三", item.getPassengerName());
        assertEquals("110101199001011234", item.getIdCardNo());
        assertEquals(0, item.getStatus().intValue(), "新明细应为 0（待出票）");
    }
}
