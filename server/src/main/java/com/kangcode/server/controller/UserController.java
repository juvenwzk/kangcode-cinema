package com.kangcode.server.controller;

import com.kangcode.common.constant.RedisKeyConstant;
import com.kangcode.common.core.Result;
import com.kangcode.framework.security.LoginUser;
import com.kangcode.framework.security.UserContext;
import com.kangcode.pojo.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RequestMapping("/api/user")
@RestController
@RequiredArgsConstructor
public class UserController {


    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping("/me")
    public Result<UserVO> me() {
        LoginUser loginUser = UserContext.get();
        List<String> roles = new ArrayList<>();
        if (loginUser != null && loginUser.getRole() != null) {
            roles.add(loginUser.getRole());
        }

        UserVO vo = UserVO.builder()
                .userId(loginUser == null ? null : loginUser.getUserId())
                .username(loginUser == null ? null : loginUser.getUsername())
                .nickname(null)
                .roles(roles)
                .build();
        return Result.ok(vo);
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
