package com.kangcode.pojo.dto;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 新增影院请求（管理端）
 */
@Data
public class CinemaCreateRequest {

    /**
     * 影院编码：唯一键。
     * 限定字母数字是因为它会拼进前端 URL / 导出文件名，带特殊字符容易出问题。
     */
    @NotBlank(message = "影院编码不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{2,16}$", message = "影院编码只能是 2~16 位字母或数字")
    private String cinemaCode;

    @NotBlank(message = "影院名称不能为空")
    @Size(max = 32, message = "影院名称最长 32 位")
    private String cinemaName;

    @Size(max = 32, message = "城市名最长 32 位")
    private String city;

    /** 影院 Logo 的 OSS URL（先调上传接口拿到，再随表单提交）；不传则无 Logo */
    @Size(max = 512, message = "Logo 地址过长")
    private String logoUrl;

    /** 状态：0 停用 1 正常，不传默认 1 */
    @Min(value = 0, message = "状态只能是 0 或 1")
    @Max(value = 1, message = "状态只能是 0 或 1")
    private Integer status;
}
