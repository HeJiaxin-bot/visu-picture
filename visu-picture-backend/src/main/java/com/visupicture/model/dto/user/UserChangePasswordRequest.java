package com.visupicture.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户修改密码请求
 */
@Data
public class UserChangePasswordRequest implements Serializable {

    /**
     * 原密码
     */
    private String userPassword;

    /**
     * 新密码
     */
    private String newPassword;

    /**
     * 确认新密码
     */
    private String checkPassword;

    private static final long serialVersionUID = 1L;
}
