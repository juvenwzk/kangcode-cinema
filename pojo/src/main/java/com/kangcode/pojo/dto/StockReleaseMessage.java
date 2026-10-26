package com.kangcode.pojo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 释放库存消息：取消订单 / 支付超时关单后，把预扣的库存还回去。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockReleaseMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 订单号（与 OrderCreateMessage / OrderTimeoutMessage 保持一致，统一叫 orderNo） */
    private String orderNo;

    private Long screeningId;

    private LocalDate showDate;

    private String ticketType;

    private Integer ticketCount;

    /** CANCEL=用户取消，TIMEOUT=支付超时 */
    private String reason;
}
