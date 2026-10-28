package com.kangcode.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 影院
 */
@Data
@TableName("cinema")
public class Cinema {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 影院电报码/站码 */
    private String cinemaCode;

    private String cinemaName;

    private String city;

    /** 影院 Logo 的 OSS 完整 URL，未上传时为 null（前端回落到占位图） */
    private String logoUrl;

    private Integer status;

    private LocalDateTime createTime;
}
