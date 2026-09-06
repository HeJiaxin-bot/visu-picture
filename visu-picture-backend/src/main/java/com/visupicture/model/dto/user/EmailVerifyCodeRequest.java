package com.visupicture.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送邮箱验证码请求
 */
@Data
public class EmailVerifyCodeRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 邮箱
     */
    private String email;

}