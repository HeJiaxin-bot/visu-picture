package com.visupicture.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.visupicture.model.dto.user.UserQueryRequest;
import com.visupicture.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import com.visupicture.model.vo.InviteRankVO;
import com.visupicture.model.vo.LoginUserVO;
import com.visupicture.model.vo.UserInviteInfoVO;
import com.visupicture.model.vo.UserVO;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * @author 何佳鑫
 * @description 针对表【user(用户)】的数据库操作Service
 */
public interface UserService extends IService<User> {

    /**
     * 用户注册（邮箱 + 密码 + 验证码 + 邀请码选填）
     *
     * @param email        邮箱
     * @param userPassword 用户密码
     * @param checkPassword 校验密码
     * @param captcha      邮箱验证码
     * @param inviteCode   邀请码（选填）
     * @return 新用户 id
     */
    long userRegister(String email, String userPassword, String checkPassword, String captcha, String inviteCode);

    /**
     * 用户登录
     *
     * @param userAccount  用户账户
     * @param userPassword 用户密码
     * @param request
     * @return 脱敏后的用户信息
     */
    LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request);

    /**
     * 获取加密后的密码
     *
     * @param userPassword
     * @return
     */
    String getEncryptPassword(String userPassword);

    /**
     * 获取当前登录用户
     *
     * @param request
     * @return
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 获得脱敏后的登录用户信息
     *
     * @param user
     * @return
     */
    LoginUserVO getLoginUserVO(User user);

    /**
     * 获得脱敏后的用户信息
     *
     * @param user
     * @return
     */
    UserVO getUserVO(User user);

    /**
     * 获得脱敏后的用户信息列表
     *
     * @param userList
     * @return 脱敏后的用户列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 用户注销
     *
     * @param request
     * @return
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 获取查询条件
     * @param userQueryRequest
     * @return
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 是否为管理员
     *
     * @param user
     * @return
     */
    boolean isAdmin(User user);

    /**
     * 用户兑换会员（会员码兑换）
     */
    boolean exchangeVip(User user, String vipCode);

    /**
     * 每日签到（每天一次，赠送积分）
     *
     * @param loginUser 登录用户
     * @return 签到后的最新积分
     */
    Integer signIn(User loginUser);

    /**
     * 更新用户头像（上传到对象存储并更新数据库）
     *
     * @param file      头像图片文件
     * @param loginUser 登录用户
     * @return 更新后的脱敏用户信息
     */
    LoginUserVO updateUserAvatar(MultipartFile file, User loginUser);

    /**
     * 获取当前用户的邀请计划信息（专属邀请码、邀请列表、解锁进度）
     *
     * @param loginUser 登录用户
     * @return 邀请计划信息
     */
    UserInviteInfoVO getUserInviteInfo(User loginUser);

    /**
     * 邀请排行榜（按成功邀请人数排序，前 10 名）
     *
     * @return 排行榜列表
     */
    List<InviteRankVO> getInviteRank();
}
