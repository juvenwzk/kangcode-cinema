package com.kangcode.common.constant;

/**
 * RabbitMQ 队列常量
 */
public final class MqConstant {

    private MqConstant() {
    }

    /** 订单创建（异步落库） */
    public static final String ORDER_CREATE_QUEUE = "movie.order.create.queue";

    /** 订单支付超时（TTL + 死信队列实现延时，触发关单） */
    public static final String ORDER_TIMEOUT_QUEUE = "movie.order.timeout.queue";

    /** 取消订单释放库存 */
    public static final String STOCK_RELEASE_QUEUE = "movie.stock.release.queue";

    public static final String ORDER_DELAY_QUEUE = "movie.order.delay.queue";
}
