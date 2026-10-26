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
}
