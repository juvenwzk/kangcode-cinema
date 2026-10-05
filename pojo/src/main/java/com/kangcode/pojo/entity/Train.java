package com.kangcode.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 车次
 */
@Data
@TableName("train")
public class Train {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 车次号，如 G1001 */
    private String trainNo;

    private String trainName;

    private Long startStationId;

    private Long endStationId;

    private LocalTime startTime;

    private LocalTime endTime;

    /** 状态：0 停运 1 运行 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
