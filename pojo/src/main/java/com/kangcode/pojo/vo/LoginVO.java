package com.kangcode.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录/注册返回结果（出参统一用 VO）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginVO {

    private String token;

    private Long userId;

    private String username;

    private String nickname;

    /** 角色编码：ADMIN / USER */
    private String role;
}
