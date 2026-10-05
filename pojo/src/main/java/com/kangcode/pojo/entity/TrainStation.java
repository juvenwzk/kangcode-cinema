package com.kangcode.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

/**
 * 车次经停站
 */
@Data
@TableName("train_station")
public class TrainStation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long trainId;

    private Long stationId;

    /** 到站顺序，1 为始发 */
    private Integer stationNo;

    private LocalTime arriveTime;

    private LocalTime leaveTime;

    private Integer stopMinutes;

    private Integer distanceKm;
}
