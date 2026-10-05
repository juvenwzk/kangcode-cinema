package com.kangcode.server.service;

import com.kangcode.pojo.dto.LoginRequest;
import com.kangcode.pojo.vo.LoginVO;
import com.kangcode.pojo.dto.RegisterRequest;

/**
 * 认证服务
 */
public interface AuthService {

    LoginVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);
}
