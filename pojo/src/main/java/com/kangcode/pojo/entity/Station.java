package com.kangcode.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车站
 */
@Data
@TableName("station")
public class Station {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 车站电报码/站码 */
    private String stationCode;

    private String stationName;

    private String city;

    private Integer status;

    private LocalDateTime createTime;
}
