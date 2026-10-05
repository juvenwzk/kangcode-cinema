package com.kangcode.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单信息（返回给前端，不暴露 userId 等内部字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderVO {

    private Long id;

    private String orderNo;

    private Long trainId;

    private LocalDate travelDate;

    private Long startStationId;

    private Long endStationId;

    private String seatType;

    private Integer ticketCount;

    /** 总金额（分） */
    private Long totalAmount;

    /** 状态：0 待支付 1 已支付 2 已取消 3 已出票 4 已退票 */
    private Integer status;

    private LocalDateTime payExpireTime;

    private LocalDateTime payTime;

    private LocalDateTime createTime;
}
