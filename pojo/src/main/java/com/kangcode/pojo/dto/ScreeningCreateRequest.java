package com.kangcode.pojo.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.time.LocalTime;

/**
 * 新增场次请求（管理端）
 */
@Data
public class ScreeningCreateRequest {

    @NotBlank(message = "场次号不能为空")
    @Size(max = 32, message = "场次号最长 32 位")
    private String screeningNo;

    @NotBlank(message = "影片名称不能为空")
    @Size(max = 64, message = "影片名称最长 64 位")
    private String movieName;

    /** 电影海报的 OSS URL（先调上传接口拿到，再随表单提交）；不传则无海报 */
    @Size(max = 512, message = "海报地址过长")
    private String posterUrl;

    @NotNull(message = "影院不能为空")
    private Long cinemaId;

    @NotNull(message = "影厅不能为空")
    private Long hallId;

    @NotNull(message = "开场时间不能为空")
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime startTime;

    @NotNull(message = "散场时间不能为空")
    @JsonFormat(pattern = "HH:mm:ss")
    private LocalTime endTime;
}
