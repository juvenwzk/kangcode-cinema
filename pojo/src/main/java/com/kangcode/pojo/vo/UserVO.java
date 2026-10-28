package com.kangcode.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前登录用户信息（返回给前端，不暴露密码等字段）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    private Long userId;

    private String username;

    private String nickname;

    /** 头像（OSS 完整 URL），未上传时为 null */
    private String avatarUrl;

    /** 角色编码列表，如 ["USER"]、["ADMIN"] */
    private List<String> roles;
}
