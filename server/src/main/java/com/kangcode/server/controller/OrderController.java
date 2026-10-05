package com.kangcode.server.controller;

import com.kangcode.common.core.Result;
import com.kangcode.framework.security.UserContext;
import com.kangcode.pojo.vo.OrderVO;
import com.kangcode.server.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单接口
 */
@RestController
@RequestMapping("/api/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/my")
    public Result<List<OrderVO>> myOrders() {
        return Result.ok(orderService.listMyOrders(UserContext.userId()));
    }

    @GetMapping("/{orderNo}")
    public Result<OrderVO> detail(@PathVariable String orderNo) {
        return Result.ok(orderService.getOrder(UserContext.userId(), orderNo));
    }

    @PostMapping("/{orderNo}/cancel")
    public Result<Void> cancel(@PathVariable String orderNo) {
        orderService.cancel(UserContext.userId(), orderNo);
        return Result.ok();
    }
}
