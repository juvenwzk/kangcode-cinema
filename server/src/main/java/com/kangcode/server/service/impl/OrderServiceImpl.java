package com.kangcode.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.pojo.entity.TrainOrder;
import com.kangcode.pojo.vo.OrderVO;
import com.kangcode.server.mapper.TrainOrderMapper;
import com.kangcode.server.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单服务实现
 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final TrainOrderMapper trainOrderMapper;

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
    public void cancel(Long userId, String orderNo) {
        // TODO(P3/P4): 校验订单归属与状态（仅待支付可取消）-> 更新状态 -> 发送释放库存消息
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
