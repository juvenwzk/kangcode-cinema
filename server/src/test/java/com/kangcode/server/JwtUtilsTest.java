package com.kangcode.server;

import com.kangcode.framework.security.JwtUtils;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JWT 工具类单元测试示例（对应 P1 认证模块）。
 * 通过反射注入配置字段，避免依赖 Spring 容器。
 */
class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", "test-secret-0123456789");
        ReflectionTestUtils.setField(jwtUtils, "expireMinutes", 60L);
    }

    @Test
    void createToken_thenParse_returnsUserInfo() {
        String token = jwtUtils.createToken(1L, "demo");

        Claims claims = jwtUtils.parseToken(token);

        assertEquals("1", claims.getSubject());
        assertEquals("demo", claims.get("username"));
    }
}
