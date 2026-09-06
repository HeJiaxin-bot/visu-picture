package com.visupicture.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户注册请求
 */
@Data
public class UserRegisterRequest implements Serializable {

    private static final long serialVersionUID = 8735650154179439661L;

    /**
     * 邮箱（注册必填）
     */
    private String email;

    /**
     * 邮箱验证码
     */
    private String captcha;

    /**
     * 账号（现注册时以邮箱作为账号，保留字段兼容）
     */
    private String userAccount;

    /**
     * 密码
     */
    private String userPassword;

    /**
     * 确认密码
     */
    private String checkPassword;

    /**
     * 邀请码（选填，好友分享链接携带）
     */
    private String inviteCode;

}
