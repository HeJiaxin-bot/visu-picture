package com.visupicture.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.visupicture.annotation.AuditLog;
import com.visupicture.annotation.AuthCheck;
import com.visupicture.annotation.RateLimit;
import com.visupicture.common.BaseResponse;
import com.visupicture.common.DeleteRequest;
import com.visupicture.common.ResultUtils;
import com.visupicture.constant.UserConstant;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.model.dto.user.*;
import com.visupicture.model.entity.User;
import com.visupicture.model.vo.InviteRankVO;
import com.visupicture.model.vo.LoginUserVO;
import com.visupicture.model.vo.UserInviteInfoVO;
import com.visupicture.model.vo.UserVO;
import com.visupicture.service.EmailService;
import com.visupicture.service.UserService;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private EmailService emailService;

    /**
     * 发送邮箱注册验证码
     *
     * <p>限流说明：这是最容易被滥用的接口 —— 每次调用都会真实发一封邮件，
     * 既消耗配额也可能被用来骚扰他人邮箱。因此同时限制 IP 与邮箱维度，
     * 且阈值收紧（同一 IP 每分钟 3 次、每小时 10 次由 IP 维度覆盖）。</p>
     */
    @PostMapping("/email/code")
    @RateLimit(count = 3, period = 60, ipCount = 3, dimension = RateLimit.Dimension.IP_AND_USER,
            message = "验证码发送过于频繁，请稍后再试")
    @AuditLog(module = "user", action = "send_email_code", description = "发送邮箱验证码")
    public BaseResponse<Boolean> sendEmailVerifyCode(@RequestBody EmailVerifyCodeRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        emailService.sendVerifyCode(request.getEmail());
        return ResultUtils.success(true);
    }

    /**
     * 用户注册（邮箱 + 密码 + 验证码 + 邀请码选填）
     */
    @PostMapping("/register")
    @RateLimit(count = 5, period = 60, ipCount = 10, dimension = RateLimit.Dimension.IP_AND_USER,
            message = "注册过于频繁，请稍后再试")
    @AuditLog(module = "user", action = "register", description = "用户注册")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        ThrowUtils.throwIf(userRegisterRequest == null, ErrorCode.PARAMS_ERROR);
        String email = userRegisterRequest.getEmail();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        String captcha = userRegisterRequest.getCaptcha();
        long result = userService.userRegister(email, userPassword, checkPassword, captcha,
                userRegisterRequest.getInviteCode());
        return ResultUtils.success(result);
    }

    /**
     * 用户登录
     *
     * <p>限流说明：按 IP 限制暴力破解 —— 阈值放宽到 20 次/分钟，
     * 因为同一出口 IP 后可能有大量正常用户（校园网/NAT）。</p>
     */
    @PostMapping("/login")
    @RateLimit(count = 20, period = 60, dimension = RateLimit.Dimension.IP,
            message = "登录尝试过于频繁，请稍后再试")
    @AuditLog(module = "user", action = "login", description = "用户登录")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userLoginRequest == null, ErrorCode.PARAMS_ERROR);
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return ResultUtils.success(loginUserVO);
    }

    /**
     * 获取当前登录用户
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getLoginUserVO(loginUser));
    }

    /**
     * 用户注销
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        boolean result = userService.userLogout(request);
        return ResultUtils.success(result);
    }

    /**
     * 修改密码（需登录，校验原密码）
     */
    @PostMapping("/change_password")
    @AuditLog(module = "user", action = "change_password", description = "修改密码")
    public BaseResponse<Boolean> changePassword(@RequestBody UserChangePasswordRequest userChangePasswordRequest,
                                                HttpServletRequest request) {
        ThrowUtils.throwIf(userChangePasswordRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        userService.changePassword(userChangePasswordRequest, loginUser);
        return ResultUtils.success(true);
    }

    /**
     * 注销账号（删除账号数据并退出登录，不可恢复）
     */
    @PostMapping("/deregister")
    @AuditLog(module = "user", action = "deregister", description = "注销账号（不可恢复）")
    public BaseResponse<Boolean> deregisterAccount(HttpServletRequest request) {
        ThrowUtils.throwIf(request == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        boolean result = userService.deregisterAccount(loginUser, request);
        return ResultUtils.success(result);
    }

    /**
     * 创建用户
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        ThrowUtils.throwIf(userAddRequest == null, ErrorCode.PARAMS_ERROR);
        User user = new User();
        BeanUtil.copyProperties(userAddRequest, user);
        // 默认密码
        final String DEFAULT_PASSWORD = "12345678";
        String encryptPassword = userService.getEncryptPassword(DEFAULT_PASSWORD);
        user.setUserPassword(encryptPassword);
        // 插入数据库
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 根据 id 获取用户（仅管理员）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取用户公开信息（用户主页）
     *
     * 【安全沿革】原实现内部调用 {@code getUserById(id)}，而 Spring AOP 基于代理，
     * 同类内部自调用不经过代理，导致方法上的 @AuthCheck 完全失效，匿名即可读取任意
     * 用户信息（含邮箱、会员兑换码），因此一度收紧为「仅本人或管理员可查」。
     * 但用户主页是公开页面（从图片详情点作者进入），收紧后他人主页直接 40101。
     * 现在：匿名/任意登录用户可读取公开资料；敏感字段（会员兑换码）仅本人或管理员视角返回。
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id, HttpServletRequest request) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR);
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        // 尽力获取登录用户：匿名访客也可查看公开主页
        User loginUser = null;
        try {
            loginUser = userService.getLoginUser(request);
        } catch (BusinessException e) {
            // 未登录，按匿名视角返回脱敏资料
        }
        UserVO userVO = userService.getUserVO(user);
        // 敏感字段脱敏：会员兑换码仅本人或管理员可见
        boolean selfOrAdmin = loginUser != null
                && (loginUser.getId().equals(id) || userService.isAdmin(loginUser));
        if (!selfOrAdmin) {
            userVO.setVipCode(null);
        }
        return ResultUtils.success(userVO);
    }

    /**
     * 删除用户
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @AuditLog(module = "user", action = "delete", targetType = "user",
            targetIdParam = "deleteRequest", description = "管理员删除用户")
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @AuditLog(module = "user", action = "update", targetType = "user",
            targetIdParam = "userUpdateRequest", description = "管理员更新用户")
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 更新当前登录用户的个人资料（昵称、个性签名）
     * 仅允许修改自己，且只改白名单字段，防止任意提权
     */
    @PostMapping("/update/my")
    @AuthCheck
    public BaseResponse<Boolean> updateMyInfo(@RequestBody UserUpdateRequest userUpdateRequest,
                                              HttpServletRequest request) {
        ThrowUtils.throwIf(userUpdateRequest == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        User user = new User();
        user.setId(loginUser.getId());
        // 昵称：非空时才更新，并去除首尾空格
        if (userUpdateRequest.getUserName() != null
                && !userUpdateRequest.getUserName().trim().isEmpty()) {
            user.setUserName(userUpdateRequest.getUserName().trim());
        }
        // 个性签名：允许清空（传空字符串）,仅当字段被提供时更新
        if (userUpdateRequest.getUserProfile() != null) {
            user.setUserProfile(userUpdateRequest.getUserProfile());
        }
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 分页获取用户封装列表（仅管理员）
     *
     * @param userQueryRequest 查询请求参数
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        ThrowUtils.throwIf(userQueryRequest == null, ErrorCode.PARAMS_ERROR);
        long current = userQueryRequest.getCurrent();
        long pageSize = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, pageSize),
                userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        List<UserVO> userVOList = userService.getUserVOList(userPage.getRecords());
        userVOPage.setRecords(userVOList);
        return ResultUtils.success(userVOPage);
    }

    /**
     * 更新用户头像
     */
    @PostMapping("/avatar")
    @RateLimit(count = 5, period = 300, dimension = RateLimit.Dimension.USER,
            message = "头像上传过于频繁，请稍后再试")
    public BaseResponse<LoginUserVO> uploadAvatar(@RequestPart("file") MultipartFile file,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(file == null || file.isEmpty(), ErrorCode.PARAMS_ERROR, "头像文件不能为空");
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.updateUserAvatar(file, loginUser));
    }

    /**
     * 每日签到（每天一次，赠送积分）
     */
    @PostMapping("/sign_in")
    public BaseResponse<Integer> signIn(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        Integer points = userService.signIn(loginUser);
        return ResultUtils.success(points);
    }

    /**
     * 兑换会员
     */
    @PostMapping("/exchange/vip")
    @RateLimit(count = 10, period = 60, dimension = RateLimit.Dimension.USER,
            message = "兑换过于频繁，请稍后再试")
    @AuditLog(module = "user", action = "exchange_vip", description = "兑换会员")
    public BaseResponse<Boolean> exchangeVip(@RequestBody VipExchangeRequest vipExchangeRequest,
                                             HttpServletRequest httpServletRequest) {
        ThrowUtils.throwIf(vipExchangeRequest == null, ErrorCode.PARAMS_ERROR);
        String vipCode = vipExchangeRequest.getVipCode();
        User loginUser = userService.getLoginUser(httpServletRequest);
        // 调用 service 层的方法进行会员兑换
        boolean result = userService.exchangeVip(loginUser, vipCode);
        return ResultUtils.success(result);
    }

    /**
     * 获取我的邀请计划信息（专属邀请码、邀请列表、解锁进度）
     */
    @GetMapping("/invite/info")
    public BaseResponse<UserInviteInfoVO> getInviteInfo(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(userService.getUserInviteInfo(loginUser));
    }

    /**
     * 邀请排行榜（前 10 名）
     */
    @GetMapping("/invite/rank")
    public BaseResponse<List<InviteRankVO>> getInviteRank() {
        return ResultUtils.success(userService.getInviteRank());
    }

}
