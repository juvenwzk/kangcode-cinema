package com.kangcode.pojo.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

@Data
public class TrainUpdateRequest {
    @Size(max=32,message = "车次名称最长32位")
    private String trainName;

    @Min(value = 0,message = "状态只能是0（停用）或者1(运行)")
    @Max(value = 1,message = "状态只能是0（停用）或者1(运行)")
    private Integer status;

}
