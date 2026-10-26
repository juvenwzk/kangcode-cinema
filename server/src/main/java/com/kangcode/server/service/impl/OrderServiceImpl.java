package com.kangcode.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kangcode.common.constant.RedisKeyConstant;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.pojo.dto.OrderCreateMessage;
import com.kangcode.pojo.dto.OrderTimeoutMessage;
import com.kangcode.pojo.dto.StockReleaseMessage;
import com.kangcode.pojo.entity.*;
import com.kangcode.pojo.vo.OrderVO;
import com.kangcode.server.mapper.*;
import com.kangcode.server.mq.TicketOrderProducer;
import com.kangcode.server.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {



    /** 订单状态：0-待支付 */
    private static final int STATUS_WAIT_PAY = 0;      // ← P3 已经加过了，确认一下在不在
    /** 订单状态：1-已支付（P4 新增） */
    private static final int STATUS_PAID = 1;
    /** 订单状态：2-已取消 / 已关闭（P4 新增） */
    private static final int STATUS_CANCELED = 2;
    /** 订单状态：3-已出票（P4 新增） */
    private static final int STATUS_ISSUED = 3;
    /** 订单状态：4-已退票（P4 新增） */
    private static final int STATUS_REFUNDED = 4;


    /** 支付超时时间（分钟） */
    private static final int PAY_TIMEOUT_MINUTES = 30;  // ← P3 已经加过了

    private final TrainOrderMapper trainOrderMapper;
    private final TrainMapper trainMapper;
    private final TrainSeatMapper trainSeatMapper;
    private final TrainOrderItemMapper trainOrderItemMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final TicketOrderProducer ticketOrderProducer;
    private final StockReleaseLogMapper stockReleaseLogMapper;

    @Override
    public List<OrderVO> listMyOrders(Long userId) {
        List<TrainOrder> orders = trainOrderMapper.selectList(new LambdaQueryWrapper<TrainOrder>()
                .eq(TrainOrder::getUserId, userId)
                .orderByDesc(TrainOrder::getCreateTime));
        // entity -> VO：不把数据库实体直接暴露给前端
        return orders.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public OrderVO getOrder(Long userId, String orderNo) {
        TrainOrder order = trainOrderMapper.selectOne(new LambdaQueryWrapper<TrainOrder>()
                .eq(TrainOrder::getOrderNo, orderNo)
                .eq(TrainOrder::getUserId, userId));
        if (order == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return toVO(order);
    }


    @Override
    @Transactional(rollbackFor = Exception.class)
    public void createOrder(OrderCreateMessage message) {
        //1.幂等：同一个订单号只落库一次（order_no上还有唯一索引发，双保险）
        TrainOrder exist=trainOrderMapper.selectOne(new LambdaQueryWrapper<TrainOrder>()
                .eq(TrainOrder::getOrderNo,message.getOrderNo()));

        if(exist!=null){
            log.info("订单已存在，忽略重复的下单消息 orderNo={}", message.getOrderNo());
            return;
        }

        //2.车次信息落库时再查：消息里不用塞那么多字段
        Train train=trainMapper.selectById(message.getTrainId());
        if(train==null){
            log.warn("下单消息里的车次不存在，丢弃这条消息trainId={}",message.getTrainId());
            return;
        }

        //3.算总金额：票价（分）*张数
        TrainSeat seat=trainSeatMapper.selectOne(new LambdaQueryWrapper<TrainSeat>()
                .eq(TrainSeat::getTrainId, message.getTrainId())
                .eq(TrainSeat::getTravelDate, message.getTravelDate())
                .eq(TrainSeat::getSeatType, message.getSeatType()));

        long price = (seat == null || seat.getPrice() == null) ? 0L : seat.getPrice();
        long totalAmount = price * message.getTicketCount();
        // ④ 落库订单（状态：待支付）
        LocalDateTime now = LocalDateTime.now();
        TrainOrder order = new TrainOrder();
        order.setOrderNo(message.getOrderNo());
        order.setUserId(message.getUserId());
        order.setTrainId(message.getTrainId());
        order.setTravelDate(message.getTravelDate());
        order.setStartStationId(train.getStartStationId());
        order.setEndStationId(train.getEndStationId());
        order.setSeatType(message.getSeatType());
        order.setTicketCount(message.getTicketCount());
        order.setTotalAmount(totalAmount);
        order.setStatus(STATUS_WAIT_PAY);
        order.setPayExpireTime(now.plusMinutes(PAY_TIMEOUT_MINUTES));
        order.setCreateTime(now);
        order.setUpdateTime(now);
        trainOrderMapper.insert(order);

        // ④' 落订单明细：一张订单对应 ticketCount 个乘客（演示：乘客信息由下单请求透传，缺省用占位）
        int itemCount = message.getTicketCount() == null ? 0 : message.getTicketCount();
        String passengerName = StringUtils.hasText(message.getPassengerName())
                ? message.getPassengerName() : "乘客";
        String idCardNo = message.getIdCardNo() == null ? "" : message.getIdCardNo();
        for (int i = 0; i < itemCount; i++) {
            TrainOrderItem item = new TrainOrderItem();
            item.setOrderId(order.getId());     // 自增主键已回填
            item.setPassengerName(passengerName);
            item.setIdCardNo(idCardNo);
            item.setStatus(0);                  // 0 待出票（出票在选做的"已出票"环节回填 seatNo）
            trainOrderItemMapper.insert(item);
        }

        // ⑤ DB 库存也要减！
        // 为什么：Redis 余票有 TTL（30 分钟），过期后会"从 DB 重建"。
        // 如果 DB 的 remain 一直不减，缓存一过期库存就"复活"，等于超卖。
        // 条件 remain >= 购买数量，保证不会把余票减成负数。
        int stockRows = trainSeatMapper.update(null, new LambdaUpdateWrapper<TrainSeat>()
                .eq(TrainSeat::getTrainId, message.getTrainId())
                .eq(TrainSeat::getTravelDate, message.getTravelDate())
                .eq(TrainSeat::getSeatType, message.getSeatType())
                .ge(TrainSeat::getRemain, message.getTicketCount())
                .setSql("remain = remain - " + message.getTicketCount()));
        if (stockRows == 0) {
            log.warn("DB 库存扣减未成功（可能已经扣完） orderNo={}", message.getOrderNo());
        }

        // ⑥ 发支付超时延时消息：30 分钟后如果还没支付，自动关单
        ticketOrderProducer.sendOrderTimeout(OrderTimeoutMessage.builder()
                .orderNo(message.getOrderNo())
                .build());

        log.info("订单落库成功 orderNo={} userId={} 总金额={}分",
                order.getOrderNo(), order.getUserId(), totalAmount);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void paySuccess(String orderNo) {
        // SQL 里带上 status = 0 条件：影响行数 = 0 说明已经支付过或已取消，直接幂等返回
        int rows = trainOrderMapper.update(null, new LambdaUpdateWrapper<TrainOrder>()
                .eq(TrainOrder::getOrderNo, orderNo)
                .eq(TrainOrder::getStatus, STATUS_WAIT_PAY)
                .set(TrainOrder::getStatus, STATUS_PAID)
                .set(TrainOrder::getPayTime, LocalDateTime.now())
                .set(TrainOrder::getUpdateTime, LocalDateTime.now()));
        if (rows == 0) {
            log.info("支付回调：订单不是待支付状态，幂等忽略 orderNo={}", orderNo);
            return;
        }
        log.info("支付成功 orderNo={}", orderNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeTimeoutOrder(String orderNo) {
        int rows = trainOrderMapper.update(null, new LambdaUpdateWrapper<TrainOrder>()
                .eq(TrainOrder::getOrderNo, orderNo)
                .eq(TrainOrder::getStatus, STATUS_WAIT_PAY)
                .set(TrainOrder::getStatus, STATUS_CANCELED)
                .set(TrainOrder::getUpdateTime, LocalDateTime.now()));

        if (rows == 0) {
            log.info("支付超时消息：订单已支付或已取消，忽略 orderNo={}", orderNo);
            return;
        }

        TrainOrder order=trainOrderMapper.selectOne(new LambdaQueryWrapper<TrainOrder>()
                .eq(TrainOrder::getOrderNo,orderNo));

        ticketOrderProducer.sendStockRelease(StockReleaseMessage.builder()
                .orderNo(orderNo)
                .trainId(order.getTrainId())
                .travelDate(order.getTravelDate())
                .seatType(order.getSeatType())
                .ticketCount(order.getTicketCount())
                .reason("TIMEOUT")
                .build());
        log.info("订单支付超时，已关单 orderNo={}", orderNo);

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseStock(StockReleaseMessage message) {
        //1.幂等闸门：插一条日志，靠 uk_order_no 拦住重复
        try{
            StockReleaseLog record=new StockReleaseLog();
            record.setOrderNo(message.getOrderNo());
            record.setReason(message.getReason());
            record.setTicketCount(message.getTicketCount());
            stockReleaseLogMapper.insert(record);
        }
        catch (DuplicateKeyException e){
            log.info("库存释放消息重复消费，已忽略 orderNo={} reason={}",
                    message.getOrderNo(),message.getReason());
            return;
        }

        //2.先改DB
        int rows=trainSeatMapper.update(null, new LambdaUpdateWrapper<TrainSeat>()
                .eq(TrainSeat::getTrainId, message.getTrainId())
                .eq(TrainSeat::getTravelDate, message.getTravelDate())
                .eq(TrainSeat::getSeatType, message.getSeatType())
                .setSql("remain = remain + " + message.getTicketCount()));
        if(rows==0){
            log.warn("库存回补没找到对应库存行 orderNo={}", message.getOrderNo());
        }

        //3.最后回补Redis
        String key = RedisKeyConstant.ticketStockKey(
                message.getTrainId(), message.getTravelDate().toString(), message.getSeatType());
        stringRedisTemplate.opsForValue().increment(key, message.getTicketCount());

        log.info("库存已回补 order={} count={} DB影响行数={} reason={}",
                message.getOrderNo(),message.getTicketCount(),rows,message
                );

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void refund(Long userId, String orderNo) {
        //1.校验归属:只能退自己的订单
        TrainOrder order=trainOrderMapper.selectOne(
                new LambdaQueryWrapper<TrainOrder>()
                        .eq(TrainOrder::getOrderNo,orderNo)
                        .eq(TrainOrder::getUserId,userId));

         if(order==null){
             throw new BusinessException(ResultCode.NOT_FOUND);
         }

        //2.只有“已支付/已出票”能退票：SQL带status条件,影响行数为0说明状态已经变了
        //  用 in(...) 一次表达“状态是 1 或 3”，避免 .eq().or().eq() 因 AND/OR 优先级越权改单
         int rows=trainOrderMapper.update(null,
                 new LambdaUpdateWrapper<TrainOrder>()
                         .eq(TrainOrder::getOrderNo,orderNo)
                         .eq(TrainOrder::getUserId,userId)//越权双保险
                         .in(TrainOrder::getStatus,STATUS_PAID,STATUS_ISSUED)//已支付 或 已出票
                         .set(TrainOrder::getStatus,STATUS_REFUNDED)// 1/3 → 4 已退票
                         .set(TrainOrder::getUpdateTime,LocalDateTime.now())

                 );

        if (rows == 0) {
            // 待支付 → 未支付不能退；其余 → 已取消/已退票
            if (order.getStatus() == STATUS_WAIT_PAY) {
                throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "订单未支付，不能退票");
            }
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "该订单不能退票");
        }

        ticketOrderProducer.sendStockRelease(StockReleaseMessage.builder()
                .orderNo(orderNo)
                .trainId(order.getTrainId())
                .travelDate(order.getTravelDate())
                .seatType(order.getSeatType())
                .ticketCount(order.getTicketCount())
                .reason("REFUND")
                .build()
        );
        log.info("订单已退票 orderNo={}", orderNo);

    }
    /**
     * 取消订单（P4 任务 4-1，已实现）。
     * <p>只有"待支付"能取消：状态判断写进 UPDATE 的 WHERE，靠影响行数判断是否成功；
     * 更新成功后发消息释放库存（reason=CANCEL）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long userId, String orderNo) {
        //1.校验归属：只能取消自己的订单
        TrainOrder order=trainOrderMapper.selectOne(new LambdaQueryWrapper<TrainOrder>()
                .eq(TrainOrder::getOrderNo,orderNo)
                .eq(TrainOrder::getUserId,userId));

        if(order==null){
            throw new BusinessException(ResultCode.NOT_FOUND);
        }

        //2.只有“待支付”能取消：SQL带status条件,影响行数为0说明状态已经变了
        int rows=trainOrderMapper.update(null,new LambdaUpdateWrapper<TrainOrder>()
                .eq(TrainOrder::getOrderNo,orderNo)
                .eq(TrainOrder::getStatus,STATUS_WAIT_PAY)
                .set(TrainOrder::getStatus,STATUS_CANCELED)
                .set(TrainOrder::getUpdateTime,LocalDateTime.now())
        );
        if(rows==0){
            if(order.getStatus()==STATUS_PAID){
                throw new BusinessException(ResultCode.ORDER_PAID);
            }
            throw new BusinessException(ResultCode.ORDER_CANCELED);
        }

        //3.发消息释放库存（取消=把票还回去）
        ticketOrderProducer.sendStockRelease(StockReleaseMessage.builder()
                .orderNo(orderNo)
                .trainId(order.getTrainId())
                .travelDate(order.getTravelDate())
                .seatType(order.getSeatType())
                .ticketCount(order.getTicketCount())
                .reason("CANCEL")
                .build()
        );
        log.info("订单已取消 orderNo={}", orderNo);
    }

    /** 实体转 VO */
    private OrderVO toVO(TrainOrder order) {
        return OrderVO.builder()
                .id(order.getId())
                .orderNo(order.getOrderNo())
                .trainId(order.getTrainId())
                .travelDate(order.getTravelDate())
                .startStationId(order.getStartStationId())
                .endStationId(order.getEndStationId())
                .seatType(order.getSeatType())
                .ticketCount(order.getTicketCount())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .payExpireTime(order.getPayExpireTime())
                .payTime(order.getPayTime())
                .createTime(order.getCreateTime())
                .build();
    }
}
