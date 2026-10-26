package com.kangcode.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 座位库存（热点表：库存预热到 Redis，扣减走 Redisson 锁 + 定时对账）
 */
@Data
@TableName("screening_seat")
public class ScreeningSeat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long screeningId;

    private LocalDate showDate;

    /** 票种：二等座/一等座/商务座/硬卧/软卧 */
    private String ticketType;

    /** 总票数 */
    private Integer total;

    /** 余票 */
    private Integer remain;

    /** 票价（分） */
    private Long price;

    /** 乐观锁版本 */
    @Version
    private Integer version;

    private Integer status;

    private LocalDateTime updateTime;
}
