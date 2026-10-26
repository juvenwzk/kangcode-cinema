package com.kangcode.server.mq;


import com.kangcode.common.constant.MqConstant;
import com.kangcode.framework.config.RabbitMqConfig;
import com.kangcode.pojo.dto.OrderCreateMessage;
import com.kangcode.pojo.dto.OrderTimeoutMessage;
import com.kangcode.pojo.dto.StockReleaseMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketOrderProducer {

    private final RabbitTemplate rabbitTemplate;


    /**
     * 下单消息:预扣库存
     *
     */
    public void sendOrderCreate(OrderCreateMessage message){
        rabbitTemplate.convertAndSend(MqConstant.ORDER_CREATE_QUEUE,message);
        log.info("已发送下单消息 orderNo={}", message.getOrderNo());
    }

    /** 支付超时消息：先丢进延时队列，躺够 TTL 才会进关单队列 */
    public void sendOrderTimeout(OrderTimeoutMessage message){
        rabbitTemplate.convertAndSend(MqConstant.ORDER_DELAY_QUEUE,message);
        log.info("已发送支付超时延时消息 orderNo={}", message.getOrderNo());
    }

    /** 释放库存消息：取消/超时关单后回补库存 */
    public void sendStockRelease(StockReleaseMessage message){
        rabbitTemplate.convertAndSend(MqConstant.STOCK_RELEASE_QUEUE,message);
        log.info("已发送释放库存消息 orderNo={} reason={}",message.getOrderNo(),message.getReason());
    }


}
