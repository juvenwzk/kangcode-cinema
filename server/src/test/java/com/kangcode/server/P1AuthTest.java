package com.kangcode.server;

import com.kangcode.framework.security.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * P1 认证与权限 —— JWT 签发/解析的边界测试。
 * 通过反射注入配置字段，不依赖 Spring 容器。
 */
class P1AuthTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", "test-secret-0123456789");
        ReflectionTestUtils.setField(jwtUtils, "expireMinutes", 60L);
    }

    @Test
    @DisplayName("P1-1 签发后能解析出 userId / username / role")
    void createAndParse() {
        String token = jwtUtils.createToken(7L, "kang", "ADMIN");

        Claims claims = jwtUtils.parseToken(token);

        assertEquals("7", claims.getSubject());
        assertEquals("kang", claims.get("username"));
        assertEquals("ADMIN", claims.get("role"));
    }

    @Test
    @DisplayName("P1-2 不传角色的重载方法：role 为 null")
    void createWithoutRole() {
        String token = jwtUtils.createToken(1L, "demo");

        Claims claims = jwtUtils.parseToken(token);

        assertEquals("1", claims.getSubject());
        assertEquals(null, claims.get("role"));
    }

    @Test
    @DisplayName("P1-3 过期 token -> 解析抛 ExpiredJwtException")
    void expiredToken() {
        ReflectionTestUtils.setField(jwtUtils, "expireMinutes", -1L);   // 立刻过期
        String token = jwtUtils.createToken(1L, "u");

        assertThrows(ExpiredJwtException.class, () -> jwtUtils.parseToken(token));
    }

    @Test
    @DisplayName("P1-4 换一个 secret 解析 -> 签名校验失败")
    void tamperedToken() {
        String token = jwtUtils.createToken(1L, "u");

        JwtUtils other = new JwtUtils();
        ReflectionTestUtils.setField(other, "secret", "another-secret-xxx");
        ReflectionTestUtils.setField(other, "expireMinutes", 60L);

        assertThrows(SignatureException.class, () -> other.parseToken(token));
    }
}
