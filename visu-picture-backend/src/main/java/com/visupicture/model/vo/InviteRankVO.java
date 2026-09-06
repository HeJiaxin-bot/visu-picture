package com.visupicture.model.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 邀请排行榜条目
 */
@Data
public class InviteRankVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 用户昵称
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 成功邀请人数
     */
    private Long count;
}
