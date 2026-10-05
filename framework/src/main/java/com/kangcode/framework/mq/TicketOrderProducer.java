package com.kangcode.framework.mq;

import com.kangcode.common.constant.MqConstant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 票务消息生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TicketOrderProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送下单消息（异步落库）
     * TODO: 完善消息体、MessageId（幂等）、发送确认与失败重试
     */
    public void sendOrderCreate(Object payload) {
        rabbitTemplate.convertAndSend(MqConstant.ORDER_CREATE_QUEUE, payload);
        log.info("已发送下单消息: {}", payload);
    }

    /**
     * 发送订单支付超时延时消息
     * TODO: 通过 TTL + 死信队列（或 rabbitmq-delayed-message-exchange 插件）实现延时
     */
    public void sendOrderTimeout(Object payload) {
        rabbitTemplate.convertAndSend(MqConstant.ORDER_TIMEOUT_QUEUE, payload);
    }

    /**
     * 发送释放库存消息
     */
    public void sendStockRelease(Object payload) {
        rabbitTemplate.convertAndSend(MqConstant.STOCK_RELEASE_QUEUE, payload);
    }
}
