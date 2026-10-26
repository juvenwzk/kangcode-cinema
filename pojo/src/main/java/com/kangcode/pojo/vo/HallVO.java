package com.kangcode.pojo.vo;

import lombok.Data;

import java.time.LocalTime;

@Data
public class HallVO {
    private Long cinemaId;
    private String cinemaCode;
    private String cinemaName;
    private Integer hallNo;
    private LocalTime openTime;
    private LocalTime closeTime;
    private Integer cleanupMinutes;
    private Integer rowCount;
}