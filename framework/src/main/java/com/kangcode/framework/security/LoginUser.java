package com.kangcode.framework.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 当前登录用户（由 JwtInterceptor 解析 token 后放入 UserContext）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginUser {

    private Long userId;

    private String username;

    /** 角色编码：ADMIN / USER */
    private String role;
}
