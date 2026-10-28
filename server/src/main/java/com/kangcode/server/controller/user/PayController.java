package com.kangcode.server.controller.user;

import com.kangcode.common.core.Result;
import com.kangcode.pojo.dto.PayCallbackRequest;
import com.kangcode.server.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@Slf4j
@RestController
@RequestMapping("/api/pay")
public class PayController {


    private final OrderService orderService;

    public PayController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("callback")
    public Result<Void> callback(@Valid @RequestBody PayCallbackRequest request){
        log.info("收到支付回调 orderNo={} payNo={}",request.getOrderNo(),request.getPayNo());
        orderService.paySuccess(request.getOrderNo());
        return Result.ok();
    }

}
