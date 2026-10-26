package com.kangcode.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

/**
 * 场次影厅
 */
@Data
@TableName("hall")
public class Hall {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long screeningId;

    private Long cinemaId;

    /** 到站顺序，1 为始发 */
    private Integer hallNo;

    private LocalTime openTime;

    private LocalTime closeTime;

    private Integer cleanupMinutes;

    private Integer rowCount;
}
