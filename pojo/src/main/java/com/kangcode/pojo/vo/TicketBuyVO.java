package com.kangcode.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 购票返回结果（出参统一用 VO）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketBuyVO {

    private String orderNo;

    private Long userId;

    /** 0 待支付 */
    private Integer status;

    private String message;
}
