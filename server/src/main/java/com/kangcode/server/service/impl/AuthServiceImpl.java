package com.kangcode.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.kangcode.common.constant.RedisKeyConstant;
import com.kangcode.common.core.ResultCode;
import com.kangcode.common.exception.BusinessException;
import com.kangcode.framework.security.JwtUtils;
import com.kangcode.pojo.dto.LoginRequest;
import com.kangcode.pojo.vo.LoginVO;
import com.kangcode.pojo.dto.RegisterRequest;
import com.kangcode.pojo.entity.SysRole;
import com.kangcode.pojo.entity.SysUser;
import com.kangcode.pojo.entity.SysUserRole;
import com.kangcode.server.mapper.SysRoleMapper;
import com.kangcode.server.mapper.SysUserMapper;
import com.kangcode.server.mapper.SysUserRoleMapper;
import com.kangcode.server.service.AuthService;
import cn.hutool.crypto.digest.BCrypt;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;
/**
 * 认证服务实现
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleMapper sysRoleMapper;
    private final SysUserRoleMapper sysUserRoleMapper;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate stringRedisTemplate;


    @Value("${jwt.expire-minutes:720}")
    private long expireMinutes;

    @Override
    @Transactional
    public LoginVO register(RegisterRequest request) {
        // 用户名唯一性校验 -> BCrypt 加密密码 -> 保存用户 -> 绑定 USER 角色
        //1.判断用户名是否唯一
        Long count=sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername,request.getUsername()));
        if (count!=null&&count>0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTS);//返回1005
        }

        //2.BCrypt 加密后入库
        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));//存在是密文（Hutool BCrypt）
        user.setNickname(request.getNickname()==null?request.getUsername():request.getNickname());
        user.setStatus(1);
        sysUserMapper.insert(user);

        //3.绑定USER角色
        SysRole userRole=sysRoleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>()
                        .eq(SysRole::getRoleCode,"USER"));

        if(userRole!=null){
            SysUserRole userRoleRel=new SysUserRole();
            userRoleRel.setUserId(user.getId());
            userRoleRel.setRoleId(userRole.getId());
            sysUserRoleMapper.insert(userRoleRel);
        }

        // 4.注册即登录：签发 token 并写入 Redis
        return buildLoginVO(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        //  校验用户名密码 -> 签发 JWT -> Token 写入 Redis（user:token:{userId}，支持续期/踢人）
        //1.查用户
        SysUser user=sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername,request.getUsername())
        );

        //2.密码校验(marches:明文 vs 密文)
        if(user==null||!BCrypt.checkpw(request.getPassword(), user.getPassword()))
        {
            throw new BusinessException(ResultCode.LOGIN_ERROR);//返回1006
        }

        if(user.getStatus()!=null&&user.getStatus()==0){
            throw new BusinessException(ResultCode.LOGIN_ERROR);//被禁用
        }
        return buildLoginVO(user);
    }

    /**
     *
     * 签发token并写入Redis（登录和注册共用）
     * @param user
     * @return
     */

    private LoginVO buildLoginVO(SysUser user){
        String role = roleOf(user.getId());
        String token=jwtUtils.createToken(user.getId(),user.getUsername(), role);
        stringRedisTemplate.opsForValue().set(
                RedisKeyConstant.userTokenKey(user.getId()),
                token,
                expireMinutes,
                TimeUnit.MINUTES);

        return LoginVO.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .role(role)
                .build();
    }

    /** 取用户角色编码（取第一个，一般为 ADMIN 或 USER） */
    private String roleOf(Long userId) {
        SysUserRole rel = sysUserRoleMapper.selectOne(
                new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId).last("limit 1"));
        if (rel == null) {
            return "USER";
        }
        SysRole role = sysRoleMapper.selectById(rel.getRoleId());
        return role == null ? "USER" : role.getRoleCode();
    }

}
