package com.kangcode.server.service;

import com.kangcode.pojo.vo.UserVO;

/**
 * 当前登录用户相关操作
 */
public interface UserService {

    /**
     * 查当前登录用户的信息（含头像、昵称）。
     * <p>为什么不直接用 UserContext 里的数据：那里只有 JWT 解出来的 userId / username / role，
     * 昵称和头像存在库里，每次改头像后必须回库才拿得到最新值。</p>
     */
    UserVO currentUser();

    /**
     * 修改头像。只认 OSS 返回的 URL，不接收文件。
     *
     * @return 旧头像 URL（可能为 null），调用方可用于清理旧对象
     */
    String updateAvatar(String avatarUrl);
}
