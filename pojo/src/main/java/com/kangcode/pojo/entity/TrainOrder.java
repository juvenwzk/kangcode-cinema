package com.kangcode.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 订单
 */
@Data
@TableName("train_order")
public class TrainOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Long userId;

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

    private LocalDateTime updateTime;
}
