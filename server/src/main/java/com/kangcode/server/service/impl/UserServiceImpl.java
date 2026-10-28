package com.kangcode.server.service.impl;

import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.framework.oss.OssService;
import com.kangcode.framework.security.LoginUser;
import com.kangcode.framework.security.UserContext;
import com.kangcode.pojo.entity.SysUser;
import com.kangcode.pojo.vo.UserVO;
import com.kangcode.server.mapper.SysUserMapper;
import com.kangcode.server.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;

/**
 * 当前登录用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final SysUserMapper sysUserMapper;
    private final OssService ossService;

    @Override
    public UserVO currentUser() {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null) {
            // token 有效但用户被删了：当作未登录处理，前端会引导重新登录
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        List<String> roles = StringUtils.hasText(loginUser.getRole())
                ? Collections.singletonList(loginUser.getRole())
                : Collections.emptyList();
        return UserVO.builder()
                .userId(user.getId())
                .username(user.getUsername())
                // 没设昵称就回落到登录名，前端不用再判空
                .nickname(StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .roles(roles)
                .build();
    }

    @Override
    public String updateAvatar(String avatarUrl) {
        LoginUser loginUser = UserContext.get();
        if (loginUser == null || loginUser.getUserId() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SysUser user = sysUserMapper.selectById(loginUser.getUserId());
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        String oldAvatar = user.getAvatarUrl();
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setAvatarUrl(avatarUrl);
        sysUserMapper.updateById(update);
        log.info("头像已更新 userId={} url={}", user.getId(), avatarUrl);
        return oldAvatar;
    }
}
