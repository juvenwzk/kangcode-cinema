package com.kangcode.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 场次
 */
@Data
@TableName("screening")
public class Screening {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 场次号，如 G1001 */
    private String screeningNo;

    private String movieName;

    /** 电影海报的 OSS 完整 URL，未上传时为 null（前端回落到占位图） */
    private String posterUrl;

    private Long cinemaId;

    private Long hallId;

    private LocalTime startTime;

    private LocalTime endTime;

    /** 状态：0 停运 1 运行 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
