package com.kangcode.pojo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
@Data
public class CinemaUpdateRequest {

    @NotBlank(message = "影院不能为空")
    @Size(max=32,message = "影院名最长32位")
    private String cinemaName;

    @Size(max=32,message = "城市名最长32位")
    private String city;
}
