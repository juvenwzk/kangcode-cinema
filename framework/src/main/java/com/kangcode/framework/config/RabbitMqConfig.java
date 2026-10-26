package com.kangcode.framework.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kangcode.common.constant.MqConstant;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * RabbitMQ 配置：声明业务队列 + 消息用 JSON 传输
 *
 * <p>超时关单靠"延时队列"实现：消息先进 delay 队列躺够 TTL，
 * 到期后由死信机制自动转发到 timeout 队列。
 */
@Configuration
public class  RabbitMqConfig {


    @Value("${order.pay-timeout-millis:1800000}")
    private int payTimeoutMillis;

    @Bean
    public Queue orderCreateQueue() {
        return new Queue(MqConstant.ORDER_CREATE_QUEUE, true);
    }
    /**
     * 延时队列：没有消费者，消息在这里躺够 payTimeoutMillis 后变成"死信"，
     * 被转发到 x-dead-letter-routing-key 指定的队列（也就是下面的 timeout 队列）。
     */
    @Bean
    public Queue orderDelayQueue(){
        Map<String, Object> args = new HashMap<>();
        args.put("x-message-ttl", payTimeoutMillis);
        args.put("x-dead-letter-exchange", "");                                 // 默认交换机
        args.put("x-dead-letter-routing-key", MqConstant.ORDER_TIMEOUT_QUEUE);  // 转发到哪个队列
        return new Queue(MqConstant.ORDER_DELAY_QUEUE, true, false, false, args);
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return new Queue(MqConstant.ORDER_TIMEOUT_QUEUE, true);
    }

    @Bean
    public Queue stockReleaseQueue() {
        return new Queue(MqConstant.STOCK_RELEASE_QUEUE, true);
    }

    /**
     * 消息用 JSON 序列化。
     *
     * <p>默认是 Java 原生序列化：管理台里看不到内容、跨语言不通，而且消费者侧会因为
     * 反序列化白名单直接拒收（SecurityException: Attempt to deserialize unauthorized class）。
     * 用 Spring Boot 给的那个 ObjectMapper，LocalDate 这类时间类型才能正常转换。
     */
    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }
}
