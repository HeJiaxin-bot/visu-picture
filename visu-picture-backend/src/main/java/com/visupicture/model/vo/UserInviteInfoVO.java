package com.visupicture.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 用户邀请计划信息
 */
@Data
public class UserInviteInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 我的专属邀请码
     */
    private String inviteCode;

    /**
     * 我成功邀请的用户列表
     */
    private List<InviteRecordVO> inviteList;

    /**
     * 累计成功邀请人数
     */
    private Integer successCount;

    /**
     * 每轮解锁所需人数
     */
    private Integer unlockTarget;

    /**
     * 距下一次解锁还差人数
     */
    private Integer remainCount;

    /**
     * 会员到期时间
     */
    private Date vipExpireTime;

    /**
     * 是否已解锁会员
     */
    private Boolean memberUnlocked;

    /**
     * 是否为永久会员（累计邀请达标）
     */
    private Boolean permanentMember;
}
