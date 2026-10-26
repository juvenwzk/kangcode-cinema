package com.kangcode.pojo.dto;


import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class PayCallbackRequest {
    @NotBlank(message = "订单号不能为空")
    private String orderNo;

    /** 第三方支付流水号（演示用；真实项目要验签 + 核对金额） */
    private String payNo;
}
