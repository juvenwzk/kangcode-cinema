package com.kangcode.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("stock_release_log")
public class StockReleaseLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    /** CANCEL / TIMEOUT / REFUND */
    private String reason;

    private Integer ticketCount;

    /** 不用赋值：DB 里是 DEFAULT CURRENT_TIMESTAMP，MP 插入时会忽略 null 字段 */
    private LocalDateTime createTime;
}