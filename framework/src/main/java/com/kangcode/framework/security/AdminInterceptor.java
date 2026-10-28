package com.kangcode.framework.security;

import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理端权限拦截器。
 *
 * <p>为什么单独抽一个拦截器，而不是在每个 admin 接口里写 {@code if (!UserContext.isAdmin())}：</p>
 * <ul>
 *   <li>原来每个方法都复制一遍判断，加一个接口就要记得复制一次，忘一次就是越权漏洞；</li>
 *   <li>权限是「一组路径」的公共约束，属于横切关注点，交给拦截器才是它该待的位置；</li>
 *   <li>它必须排在 {@link JwtInterceptor} 之后（order 更大），因为要靠前者先把 UserContext 填好。</li>
 * </ul>
 */
@Component
public class AdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // 走到这里说明 JwtInterceptor 已经校验过登录，UserContext 里有值
        if (!UserContext.isAdmin()) {
            // 抛业务异常，由 GlobalExceptionHandler 统一转成 {code:403,...}
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return true;
    }
}
