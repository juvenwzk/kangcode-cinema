package com.kangcode.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图片上传结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadVO {

    /** 可直接放进 img 标签的完整 URL */
    private String url;

    /** 原始文件名，前端回显用 */
    private String name;

    /** 字节数 */
    private Long size;
}
