package com.kangcode.server.mq;


import com.kangcode.common.constant.MqConstant;
import com.kangcode.pojo.dto.StockReleaseMessage;
import com.kangcode.server.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StockReleaseConsumer {


    private final OrderService orderService;

    @RabbitListener(queues = MqConstant.STOCK_RELEASE_QUEUE)
    public void onMessage(StockReleaseMessage message){
        log.info("收到释放库存消息: {}", message);
        orderService.releaseStock(message);
    }
}
