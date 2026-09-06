package com.visupicture.constant;

/**
 * 用户常量
 */
public interface UserConstant {

    /**
     * 用户登录态键
     */
    String USER_LOGIN_STATE = "user_login";

    //  region 权限

    /**
     * 默认角色
     */
    String DEFAULT_ROLE = "user";

    /**
     * 管理员角色
     */
    String VIP_ROLE = "vip";

    /**
     * 管理员角色
     */
    String ADMIN_ROLE = "admin";

    // endregion

    // region 积分

    /**
     * 新用户注册赠送积分
     */
    int REGISTER_POINTS = 50;

    /**
     * 每日签到赠送积分
     */
    int SIGN_IN_POINTS = 5;

    /**
     * AI 扩图一次消耗积分
     */
    int OUT_PAINTING_POINTS = 20;

    // endregion

    // region 邀请计划

    /**
     * 每成功邀请 1 人获得的会员月数
     */
    int INVITE_MEMBER_MONTHS = 12;

    /**
     * 累计成功邀请 N 人升级永久会员
     */
    int INVITE_PERMANENT_COUNT = 3;

    // endregion
}
