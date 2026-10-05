package com.kangcode.server.controller;

import com.kangcode.common.core.Result;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.framework.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端接口
 *
 * <p>准备工程只提供 ping，用于验证「普通用户访问管理接口返回 403」这条拦截链路。
 * 后续放票、车次/车站维护等管理接口由你自己在这里新增。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/ping")
    public Result<String> ping() {
        // 拦截器已经把登录用户放进 UserContext，这里校验角色
        if (!UserContext.isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        return Result.ok("admin ok");
    }
}
