package com.kangcode.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;

/**
 * 场次信息（返回给前端的对象，不含任何表结构/敏感字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningVO {

    private Long id;

    /** 场次号，如 G1001 */
    private String screeningNo;

    private String movieName;

    /** 电影海报（OSS 完整 URL），未上传时为 null */
    private String posterUrl;

    private Long cinemaId;

    private Long hallId;

    private LocalTime startTime;

    private LocalTime endTime;

    /** 状态：0 停运 1 运行 */
    private Integer status;

    /** 影厅：列表接口返回空集合，详情接口返回完整列表 */
    private List<HallVO> halls;
}
