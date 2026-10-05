package com.kangcode.framework.mq;

import com.kangcode.common.constant.MqConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 下单消息消费者
 * 通过 rabbitmq.consumer.enabled=true 开启（默认关闭，RabbitMQ 可用后开启）
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "rabbitmq", name = "consumer.enabled", havingValue = "true")
public class OrderCreateConsumer {

    @RabbitListener(queues = MqConstant.ORDER_CREATE_QUEUE)
    public void onMessage(String message) {
        // TODO: 幂等落库订单（待支付）-> 发送支付超时延时消息
        // 注意：消费失败抛出异常会触发重试，并通过订单号唯一索引/幂等表防重复
        log.info("收到下单消息: {}", message);
    }
}
