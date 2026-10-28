package com.kangcode.framework.config;

import com.kangcode.framework.security.AdminInterceptor;
import com.kangcode.framework.security.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 JWT 登录拦截器 + 管理端权限拦截器
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AdminInterceptor adminInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                // 业务接口需要登录
                .addPathPatterns("/api/**")
                // 登录/注册放行
                .excludePathPatterns("/api/auth/login", "/api/auth/register","/api/pay/callback", "/error")
                // 浏览类只读接口放行：影院/场次/票种余票，未登录也能看；下单、订单仍要登录
                .excludePathPatterns("/api/cinema/**", "/api/screening/**")
                .order(0);

        // 管理端权限：必须排在 JwtInterceptor 之后（order 更大 → 后执行），
        // 因为要先由 JWT 拦截器把用户放进 UserContext，这里才判断得出是不是 ADMIN。
        registry.addInterceptor(adminInterceptor)
                .addPathPatterns("/api/admin/**")
                .order(1);
    }
}
