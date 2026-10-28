package com.kangcode.server.controller.admin;

import com.kangcode.common.core.Result;
import com.kangcode.framework.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端 · 系统类接口
 *
 * <p>这里的角色校验由 {@code AdminInterceptor} 统一做（拦截 /api/admin/**），
 * 所以方法体里不再重复写 isAdmin 判断。</p>
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminSystemController {

    /** 联通性探测：前端「管理后台」入口用它确认当前账号真的有管理权限 */
    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.ok("admin ok: " + UserContext.username());
    }
}
