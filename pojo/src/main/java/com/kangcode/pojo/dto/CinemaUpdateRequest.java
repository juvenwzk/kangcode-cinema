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

    /**
     * 影院 Logo 的 OSS URL。
     * 传空字符串表示「清空 Logo」，传 null 表示「不修改」——两者语义不同，
     * Service 里必须区分，否则用户想删掉 Logo 却怎么都删不掉。
     */
    @Size(max=512, message = "Logo 地址过长")
    private String logoUrl;
}
