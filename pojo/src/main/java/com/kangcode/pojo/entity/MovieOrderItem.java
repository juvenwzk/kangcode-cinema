package com.kangcode.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 订单明细（乘客）
 */
@Data
@TableName("movie_order_item")
public class MovieOrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String passengerName;

    private String idCardNo;

    /** 出票后回填座位号 */
    private String seatNo;

    private Integer status;
}
