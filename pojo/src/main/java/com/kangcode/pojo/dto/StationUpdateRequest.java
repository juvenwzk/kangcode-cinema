package com.kangcode.pojo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
@Data
public class StationUpdateRequest {

    @NotBlank(message = "车站不能为空")
    @Size(max=32,message = "车站名最长32位")
    private String stationName;

    @Size(max=32,message = "城市名最长32位")
    private String city;
}
