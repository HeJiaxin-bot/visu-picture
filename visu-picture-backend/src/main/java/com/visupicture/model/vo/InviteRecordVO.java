package com.visupicture.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 邀请记录（我成功邀请的用户）
 */
@Data
public class InviteRecordVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 被邀请用户 id
     */
    private Long userId;

    /**
     * 被邀请用户昵称
     */
    private String userName;

    /**
     * 被邀请用户头像
     */
    private String userAvatar;

    /**
     * 注册（加入）时间
     */
    private Date createTime;
}
