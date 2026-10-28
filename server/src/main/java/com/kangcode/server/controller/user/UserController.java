package com.kangcode.server.controller.user;

import com.kangcode.common.constant.RedisKeyConstant;
import com.kangcode.common.core.Result;
import com.kangcode.framework.oss.OssService;
import com.kangcode.framework.security.UserContext;
import com.kangcode.pojo.dto.UpdateAvatarRequest;
import com.kangcode.pojo.vo.UserVO;
import com.kangcode.server.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

@RequestMapping("/api/user")
@RestController
@RequiredArgsConstructor
public class UserController {

    private final StringRedisTemplate stringRedisTemplate;
    private final UserService userService;
    private final OssService ossService;

    /**
     * 当前登录用户：昵称、头像、角色都从这里取。
     * <p>原来昵称是写死的 null、头像没有，因为那时只从 UserContext 拿 JWT 里的字段；
     * 现在头像要能改，必须回库查一次。</p>
     */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(userService.currentUser());
    }

    /**
     * 修改头像。
     * <p>前端流程：先调 /api/upload/image 传图拿 URL → 再调这里落库 → 最后才删旧图。
     * 顺序不能反：先删旧图后落库，一旦落库失败，用户头像就变成裂图且找不回来。</p>
     */
    @PutMapping("/avatar")
    public Result<String> updateAvatar(@Valid @RequestBody UpdateAvatarRequest request) {
        String oldAvatar = userService.updateAvatar(request.getAvatarUrl());
        if (oldAvatar != null && !oldAvatar.equals(request.getAvatarUrl())) {
            ossService.deleteByUrl(oldAvatar);
        }
        return Result.ok(request.getAvatarUrl());
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        Long userId = UserContext.userId();
        if (userId != null) {
            stringRedisTemplate.delete(RedisKeyConstant.userTokenKey(userId));
        }
        return Result.ok();
    }
}
