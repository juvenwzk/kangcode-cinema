package com.kangcode.server.service;

import com.kangcode.pojo.dto.OrderCreateMessage;
import com.kangcode.pojo.dto.StockReleaseMessage;
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

    void createOrder(OrderCreateMessage message);
    void paySuccess(String orderNo);

    void closeTimeoutOrder(String orderNo);

    void releaseStock(StockReleaseMessage message);

    /**
     * 退票：只有"已支付/已出票"能退，成功后释放库存
     */
    void refund(Long userId, String orderNo);
}
