package com.kangcode.pojo.vo;

import lombok.Data;

import java.time.LocalTime;

@Data
public class TrainStationVO {
    private Long stationId;
    private String stationCode;
    private String stationName;
    private Integer stationNo;
    private LocalTime arriveTime;
    private LocalTime leaveTime;
    private Integer stopMinutes;
    private Integer distanceKm;
}