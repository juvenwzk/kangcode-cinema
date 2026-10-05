package com.kangcode.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

/**
 * 车次信息（返回给前端的对象，不含任何表结构/敏感字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainVO {

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

    /** 经停站：列表接口返回空集合，详情接口返回完整列表 */
    private List<TrainStationVO> stations;
}
