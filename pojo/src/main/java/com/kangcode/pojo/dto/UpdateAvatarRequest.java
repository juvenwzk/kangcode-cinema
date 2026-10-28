package com.kangcode.pojo.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 修改头像请求。
 * <p>只收 URL 不收文件：文件已经通过 /api/upload/image 传到 OSS 了，
 * 这里只负责把地址落到库里。上传和落库分开的好处是表单可以先传图预览，
 * 用户最后取消保存时库里不会留下垃圾数据。</p>
 */
@Data
public class UpdateAvatarRequest {

    @NotBlank(message = "头像地址不能为空")
    @Size(max = 512, message = "头像地址过长")
    private String avatarUrl;
}
