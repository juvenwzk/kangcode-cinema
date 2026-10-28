package com.kangcode.pojo.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

@Data
public class ScreeningUpdateRequest {
    // 影片名长度与 ScreeningCreateRequest 保持一致（原来这里写的是 32，新增时却是 64，
    // 导致「新增能填 64 字的片名、编辑却存不回去」）
    @Size(max=64,message = "影片名称最长64位")
    private String movieName;

    /** 电影海报的 OSS URL：空字符串=清空，null=不修改 */
    @Size(max=512, message = "海报地址过长")
    private String posterUrl;

    @Min(value = 0,message = "状态只能是0（停用）或者1(运行)")
    @Max(value = 1,message = "状态只能是0（停用）或者1(运行)")
    private Integer status;

}
