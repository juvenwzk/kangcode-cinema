package com.kangcode.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 影院信息（返回给前端）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CinemaVO {

    private Long id;

    /** 影院电报码/站码 */
    private String cinemaCode;

    private String cinemaName;

    private String city;

    /** 影院 Logo（OSS 完整 URL），未上传时为 null */
    private String logoUrl;

    /** 状态：0 停用 1 正常（管理端列表要展示，用户端恒为 1） */
    private Integer status;
}
