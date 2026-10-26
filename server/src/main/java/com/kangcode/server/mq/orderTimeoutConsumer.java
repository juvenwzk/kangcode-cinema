package com.kangcode.server.mq;

import com.kangcode.common.constant.MqConstant;
import com.kangcode.pojo.dto.OrderTimeoutMessage;
import com.kangcode.server.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class orderTimeoutConsumer {


    private final OrderService orderService;

    @RabbitListener(queues = MqConstant.ORDER_TIMEOUT_QUEUE)
    public void onMessage(OrderTimeoutMessage message){
        log.info("收到支付超时消息：{}",message);
        orderService.closeTimeoutOrder(message.getOrderNo());
    }
}
