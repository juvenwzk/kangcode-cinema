package com.kangcode.pojo.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    /** 头像的 OSS 完整 URL，未上传时为 null（前端回落到默认头像） */
    private String avatarUrl;

    private String phone;

    /** 状态：0 禁用 1 正常 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
