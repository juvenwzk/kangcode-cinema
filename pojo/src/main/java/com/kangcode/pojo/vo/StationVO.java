package com.kangcode.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 车站信息（返回给前端）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationVO {

    private Long id;

    /** 车站电报码/站码 */
    private String stationCode;

    private String stationName;

    private String city;
}
