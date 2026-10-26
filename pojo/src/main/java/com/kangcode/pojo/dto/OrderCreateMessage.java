package com.kangcode.pojo.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreateMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息幂等用，这里直接用订单号 */
    private String messageId;

    private String orderNo;

    private Long userId;

    private Long trainId;

    private LocalDate travelDate;

    private String seatType;

    private Integer ticketCount;

    /** 乘客姓名（单乘客演示：一张订单的所有明细共用；为空时落库用占位值） */
    private String passengerName;

    /** 乘客证件号（同上；为空时落库留空字符串） */
    private String idCardNo;
}
