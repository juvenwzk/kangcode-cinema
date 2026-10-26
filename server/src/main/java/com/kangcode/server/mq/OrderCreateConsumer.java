package com.kangcode.server.mq;


import com.kangcode.common.constant.MqConstant;
import com.kangcode.pojo.dto.OrderCreateMessage;
import com.kangcode.server.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreateConsumer {

    private final OrderService orderService;

    @RabbitListener(queues = MqConstant.ORDER_CREATE_QUEUE)
    public void onMessage(OrderCreateMessage message){
        log.info("收到下单消息:{}",message);
        orderService.createOrder(message);
    }
}
