package com.kangcode.pojo.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

/**
 * 修改车次请求（管理端）
 */
@Data
public class TrainUpdateRequest {

    /** 车次名称，可不传 */
    @Size(max = 32, message = "车次名称最长 32 位")
    private String trainName;

    /** 状态：0 停运 1 运行，可不传 */
    @Min(value = 0, message = "状态只能是 0（停运）或 1（运行）")
    @Max(value = 1, message = "状态只能是 0（停运）或 1（运行）")
    private Integer status;
}