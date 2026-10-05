package com.kangcode.pojo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 修改车站请求（管理端）
 */
@Data
public class StationUpdateRequest {

    @NotBlank(message = "车站名不能为空")
    @Size(max = 32, message = "车站名最长 32 位")
    private String stationName;

    /** 所属城市，可不传 */
    @Size(max = 32, message = "城市名最长 32 位")
    private String city;
}