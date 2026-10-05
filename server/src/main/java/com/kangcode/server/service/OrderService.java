package com.kangcode.server.service;

import com.kangcode.pojo.vo.OrderVO;

import java.util.List;

/**
 * 订单服务
 */
public interface OrderService {

    List<OrderVO> listMyOrders(Long userId);

    OrderVO getOrder(Long userId, String orderNo);

    /**
     * 取消订单（幂等），成功后释放库存
     */
    void cancel(Long userId, String orderNo);
}
