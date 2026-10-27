package com.kangcode.common.constant;

/**
 * 订单状态机常量
 *
 * <p>状态流转全部使用「带源状态条件的 UPDATE + 影响行数判断」，
 * 状态值必须与 sql/init.sql 里 movie_order.status 的注释保持一致。</p>
 */
public final class OrderStatusConstant {

    private OrderStatusConstant() {
    }

    /** 0-待支付（初始状态） */
    public static final int STATUS_WAIT_PAY = 0;

    /** 1-已支付（支付回调成功） */
    public static final int STATUS_PAID = 1;

    /** 2-已取消 / 已关闭（用户取消或超时关单） */
    public static final int STATUS_CANCELED = 2;

    /** 3-已出票（选做环节回填座位号后的终态之一） */
    public static final int STATUS_ISSUED = 3;

    /** 4-已退票（已支付/已出票 → 退票） */
    public static final int STATUS_REFUNDED = 4;

    /** 支付超时时间：30 分钟（超时后由延时队列关单） */
    public static final int PAY_TIMEOUT_MINUTES = 30;
}
