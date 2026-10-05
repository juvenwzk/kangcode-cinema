package com.kangcode.framework.config;

import com.kangcode.common.constant.MqConstant;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置：声明业务队列
 * 超时关单的延时能力：TODO 使用 TTL + 死信交换机（或 rabbitmq-delayed-message-exchange 插件）
 */
@Configuration
public class RabbitMqConfig {

    @Bean
    public Queue orderCreateQueue() {
        return new Queue(MqConstant.ORDER_CREATE_QUEUE, true);
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return new Queue(MqConstant.ORDER_TIMEOUT_QUEUE, true);
    }

    @Bean
    public Queue stockReleaseQueue() {
        return new Queue(MqConstant.STOCK_RELEASE_QUEUE, true);
    }

}
