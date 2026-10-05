package com.kangcode.common.core;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    SYSTEM_ERROR(500, "系统繁忙，请稍后重试"),

    BUSINESS_ERROR(1000, "业务处理失败"),
    STOCK_NOT_ENOUGH(1001, "余票不足"),
    ORDER_REPEATED(1002, "请勿重复下单"),
    ORDER_PAID(1003, "订单已支付"),
    ORDER_CANCELED(1004, "订单已取消"),
    USERNAME_EXISTS(1005, "用户名已存在"),
    LOGIN_ERROR(1006, "用户名或密码错误");

    private final int code;
    private final String message;
}
