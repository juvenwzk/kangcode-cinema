package com.kangcode.framework.security;

import cn.hutool.json.JSONUtil;
import com.kangcode.common.constant.RedisKeyConstant;
import com.kangcode.common.core.Result;
import com.kangcode.common.core.ResultCode;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * JWT 登录拦截器
 * <p>替代 Spring Security：解析 Authorization 头里的 JWT，校验 Redis 中的 token，
 * 把用户放进 {@link UserContext}，业务代码用 {@code UserContext.userId()} 取当前用户。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        // ===== 生成 traceId 并放进 MDC（后面每一行日志都会自动带上它）=====
        // 必须放在最开头：放在后面的话，前面几行日志就没有 traceId；OPTIONS 预检也应带上。
        // 优先用上游（网关/前端）传来的，没有就自己生成 8 位。
        String traceId = request.getHeader("X-Trace-Id");
        if (!StringUtils.hasText(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        }
        MDC.put("traceId", traceId);
        // ================================================================

        // 预检请求直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (!StringUtils.hasText(token)) {
            writeUnauthorized(response, ResultCode.UNAUTHORIZED.getMessage());
            return false;
        }

        try {
            Claims claims = jwtUtils.parseToken(token);
            Long userId = Long.valueOf(claims.getSubject());
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);

            // 与 Redis 中的 token 比对：登出/被顶号后旧 token 立即失效
            String cached = stringRedisTemplate.opsForValue().get(RedisKeyConstant.userTokenKey(userId));
            if (cached == null || !cached.equals(token)) {
                writeUnauthorized(response, "登录已失效，请重新登录");
                return false;
            }

            UserContext.set(LoginUser.builder().userId(userId).username(username).role(role).build());
            return true;
        } catch (Exception e) {
            log.debug("JWT 校验失败: {}", e.getMessage());
            writeUnauthorized(response, ResultCode.UNAUTHORIZED.getMessage());
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        MDC.clear();            // 不清的话，线程被复用时会串号
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(Result.fail(ResultCode.UNAUTHORIZED.getCode(), message)));
    }
}
