package com.visupicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.visupicture.constant.UserConstant;
import com.visupicture.config.CosClientConfig;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.manager.CosManager;
import com.visupicture.manager.auth.StpKit;
import com.visupicture.manager.upload.FilePictureUpload;
import com.visupicture.model.dto.file.UploadPictureResult;
import com.visupicture.model.dto.user.UserQueryRequest;
import com.visupicture.model.dto.user.VipCode;
import com.visupicture.model.entity.User;
import com.visupicture.model.enums.UserRoleEnum;
import com.visupicture.model.vo.InviteRankVO;
import com.visupicture.model.vo.InviteRecordVO;
import com.visupicture.model.vo.LoginUserVO;
import com.visupicture.model.vo.UserInviteInfoVO;
import com.visupicture.model.vo.UserVO;
import com.visupicture.service.EmailService;
import com.visupicture.service.UserService;
import com.visupicture.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.multipart.MultipartFile;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * @author 何佳鑫
 * @description 针对表【user(用户)】的数据库操作Service实现
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Resource
    private FilePictureUpload filePictureUpload;

    @Resource
    private CosManager cosManager;

    @Resource
    private CosClientConfig cosClientConfig;

    @Resource
    private EmailService emailService;

    /**
     * 用户注册（邮箱 + 密码 + 验证码）
     *
     * @param email         邮箱
     * @param userPassword  用户密码
     * @param checkPassword 校验密码
     * @param captcha       邮箱验证码
     * @return 新用户 id
     */
    @Override
    public long userRegister(String email, String userPassword, String checkPassword, String captcha, String inviteCode) {
        // 1. 校验参数
        if (StrUtil.hasBlank(email, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        // 邮箱格式
        emailService.validateEmail(email);
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        // 2. 校验邮箱验证码（一次性，校验通过后自动失效）
        emailService.verifyCode(email, captcha);
        // 3. 检查邮箱是否已被注册（以邮箱作为账号）
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("email", email);
        long count = this.baseMapper.selectCount(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "该邮箱已被注册");
        }
        // 4. 校验邀请码（选填，填写了必须有效，避免用户拼错白注册）
        User inviter = null;
        if (StrUtil.isNotBlank(inviteCode)) {
            inviter = this.getOne(new QueryWrapper<User>().eq("inviteCode", inviteCode.trim()));
            if (inviter == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "邀请码无效，请核对后重试");
            }
        }
        // 5. 密码加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 6. 插入数据到数据库中（userAccount 存邮箱，保留登录/鉴权逻辑不变）
        User user = new User();
        user.setUserAccount(email);
        user.setEmail(email);
        user.setUserPassword(encryptPassword);
        user.setUserName("视界用户");
        user.setUserRole(UserRoleEnum.USER.getValue());
        // 新用户注册赠送积分
        user.setPoints(UserConstant.REGISTER_POINTS);
        // 生成专属邀请码
        user.setInviteCode(generateUniqueInviteCode());
        boolean saveResult = this.save(user);
        if (!saveResult) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        // 7. 绑定邀请关系并结算邀请奖励
        if (inviter != null) {
            User bindUpdate = new User();
            bindUpdate.setId(user.getId());
            bindUpdate.setInviterId(inviter.getId());
            this.updateById(bindUpdate);
            settleInviteReward(inviter.getId());
        }
        return user.getId();
    }

    /**
     * 生成全局唯一的 8 位邀请码（大写字母 + 数字，去除易混淆字符）
     */
    private String generateUniqueInviteCode() {
        final String BASE = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        for (int i = 0; i < 10; i++) {
            StringBuilder sb = new StringBuilder(8);
            for (int j = 0; j < 8; j++) {
                sb.append(BASE.charAt(RandomUtil.randomInt(BASE.length())));
            }
            String code = sb.toString();
            long exists = this.baseMapper.selectCount(new QueryWrapper<User>().eq("inviteCode", code));
            if (exists == 0) {
                return code;
            }
        }
        // 极端情况下用长码兜底
        return RandomUtil.randomStringUpper(12);
    }

    /**
     * 邀请奖励结算：每成功邀请 1 人为邀请人延长一次会员，累计满 N 人升级永久会员
     *
     * @param inviterId 邀请人 id
     */
    private void settleInviteReward(Long inviterId) {
        long inviteCount = this.baseMapper.selectCount(new QueryWrapper<User>().eq("inviterId", inviterId));
        if (inviteCount <= 0) {
            return;
        }
        User inviter = this.getById(inviterId);
        if (inviter == null) {
            return;
        }
        User update = new User();
        update.setId(inviterId);
        update.setUserRole(VIP_ROLE);
        // 累计满 N 人：升级永久会员（重复设置幂等，无副作用）
        if (inviteCount >= UserConstant.INVITE_PERMANENT_COUNT) {
            update.setVipExpireTime(PERMANENT_VIP_TIME);
            this.updateById(update);
            log.info("用户 {} 累计邀请 {} 人，已升级永久会员", inviterId, inviteCount);
            return;
        }
        // 每邀 1 人延长会员：未过期则从当前到期时间续期，已过期/从未开通则从现在起算
        Date base = (inviter.getVipExpireTime() != null && inviter.getVipExpireTime().after(new Date()))
                ? inviter.getVipExpireTime() : new Date();
        update.setVipExpireTime(DateUtil.offsetMonth(base, UserConstant.INVITE_MEMBER_MONTHS));
        this.updateById(update);
        log.info("用户 {} 成功邀请 1 人，会员延长 {} 个月，当前累计 {} 人", inviterId, UserConstant.INVITE_MEMBER_MONTHS, inviteCount);
    }

    @Override
    public LoginUserVO userLogin(String userAccount, String userPassword, HttpServletRequest request) {
        // 1. 校验
        if (StrUtil.hasBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码错误");
        }
        // 2. 对用户传递的密码进行加密
        String encryptPassword = getEncryptPassword(userPassword);
        // 3. 查询数据库中的用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 不存在，抛异常
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或者密码错误");
        }
        // 4. 保存用户的登录态
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, user);
        // 记录用户登录态到 Sa-token，便于空间鉴权时使用，注意保证该用户信息与 SpringSession 中的信息过期时间一致
        StpKit.SPACE.login(user.getId());
        StpKit.SPACE.getSession().set(UserConstant.USER_LOGIN_STATE, user);
        return this.getLoginUserVO(user);
    }

    /**
     * 获取加密后的密码
     *
     * @param userPassword 用户密码
     * @return 加密后的密码
     */
    @Override
    public String getEncryptPassword(String userPassword) {
        // 加盐，混淆密码
        final String SALT = "hejx";
        return DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 判断是否已经登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        User currentUser = (User) userObj;
        if (currentUser == null || currentUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 从数据库中查询（追求性能的话可以注释，直接返回上述结果）
        Long userId = currentUser.getId();
        currentUser = this.getById(userId);
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return currentUser;
    }

    /**
     * 获取脱敏类的用户信息
     *
     * @param user 用户
     * @return 脱敏后的用户信息
     */
    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtil.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    /**
     * 获得脱敏后的用户信息
     *
     * @param user
     * @return
     */
    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtil.copyProperties(user, userVO);
        return userVO;
    }

    /**
     * 获取脱敏后的用户列表
     *
     * @param userList
     * @return
     */
    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream()
                .map(this::getUserVO)
                .collect(Collectors.toList());
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        // 判断是否已经登录
        Object userObj = request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        // 移除登录态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        return true;
    }

    /**
     * 注销账号：逻辑删除账号数据并移除登录态，注销后无法再用该账号登录
     */
    @Override
    public boolean deregisterAccount(User loginUser, HttpServletRequest request) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        Long userId = loginUser.getId();
        // 逻辑删除账号（@TableLogic 生效，isDelete 置 1）
        boolean removed = this.removeById(userId);
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR, "账号注销失败，请稍后重试");
        // 移除登录态
        request.getSession().removeAttribute(UserConstant.USER_LOGIN_STATE);
        log.info("用户 {} 已注销账号", userId);
        return true;
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userAccount = userQueryRequest.getUserAccount();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(StrUtil.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotEmpty(sortField), sortOrder.equals("ascend"), sortField);
        return queryWrapper;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue().equals(user.getUserRole());
    }

    // region ------- 以下代码为用户兑换会员功能 --------

    // 新增依赖注入
    @Resource
    private ResourceLoader resourceLoader;

    // 文件读写锁（确保并发安全）
    private final ReentrantLock fileLock = new ReentrantLock();

    // VIP 角色常量（根据你的需求自定义）
    private static final String VIP_ROLE = "vip";

    /**
     * 永久会员的到期时间表示（远期日期）
     */
    private static final Date PERMANENT_VIP_TIME = DateUtil.parse("2099-12-31 23:59:59");

    /**
     * 兑换会员
     *
     * @param user
     * @param vipCode
     * @return
     */
    @Override
    public boolean exchangeVip(User user, String vipCode) {
        // 1. 参数校验
        if (user == null || StrUtil.isBlank(vipCode)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        // 2. 读取并校验兑换码
        VipCode targetCode = validateAndMarkVipCode(vipCode);
        // 3. 更新用户信息
        updateUserVipInfo(user, targetCode.getCode());
        return true;
    }

    /**
     * 校验兑换码并标记为已使用
     */
    private VipCode validateAndMarkVipCode(String vipCode) {
        fileLock.lock(); // 加锁保证文件操作原子性
        try {
            // 读取 JSON 文件
            JSONArray jsonArray = readVipCodeFile();

            // 查找匹配的未使用兑换码
            List<VipCode> codes = JSONUtil.toList(jsonArray, VipCode.class);
            VipCode target = codes.stream()
                    .filter(code -> code.getCode().equals(vipCode) && !code.isHasUsed())
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.PARAMS_ERROR, "无效的兑换码"));

            // 标记为已使用
            target.setHasUsed(true);

            // 写回文件
            writeVipCodeFile(JSONUtil.parseArray(codes));
            return target;
        } finally {
            fileLock.unlock();
        }
    }

    /**
     * 读取兑换码文件
     */
    private JSONArray readVipCodeFile() {
        try {
            org.springframework.core.io.Resource resource =
                    resourceLoader.getResource("classpath:biz/vipCode.json");
            String content = FileUtil.readString(resource.getFile(), StandardCharsets.UTF_8);
            return JSONUtil.parseArray(content);
        } catch (IOException e) {
            log.error("读取兑换码文件失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙");
        }
    }

    /**
     * 写入兑换码文件
     */
    private void writeVipCodeFile(JSONArray jsonArray) {
        try {
            org.springframework.core.io.Resource resource =
                    resourceLoader.getResource("classpath:biz/vipCode.json");
            FileUtil.writeString(jsonArray.toStringPretty(), resource.getFile(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("更新兑换码文件失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "系统繁忙");
        }
    }

    /**
     * 更新用户会员信息
     */
    private void updateUserVipInfo(User user, String usedVipCode) {
        // 计算过期时间（当前时间 + 1 年）
        Date expireTime = DateUtil.offsetMonth(new Date(), 12); // 计算当前时间加 1 年后的时间

        // 构建更新对象
        User updateUser = new User();
        updateUser.setId(user.getId());
        updateUser.setVipExpireTime(expireTime); // 设置过期时间
        updateUser.setVipCode(usedVipCode);     // 记录使用的兑换码
        updateUser.setUserRole(VIP_ROLE);       // 修改用户角色

        // 执行更新
        boolean updated = this.updateById(updateUser);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "开通会员失败，操作数据库失败");
        }
    }

    // endregion ------- 以下代码为用户兑换会员功能 --------

    // region ------- 以下代码为每日签到 / 积分功能 --------

    /**
     * 每日签到，赠送积分
     *
     * @param loginUser 登录用户
     * @return 签到后的最新积分
     */
    @Override
    public Integer signIn(User loginUser) {
        if (loginUser == null || loginUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 今日零点，签到时间早于它说明今天还没签到
        Date todayStart = DateUtil.beginOfDay(new Date());
        // 条件更新保证并发下不会重复签到：未签到过或签到时间不在今天时才加分
        boolean updated = this.update(new LambdaUpdateWrapper<User>()
                .eq(User::getId, loginUser.getId())
                .and(w -> w.isNull(User::getLastSignInTime).or().lt(User::getLastSignInTime, todayStart))
                .setSql("points = points + " + UserConstant.SIGN_IN_POINTS)
                .set(User::getLastSignInTime, new Date()));
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "今日已签到，明天再来吧～");
        }
        // 返回最新积分
        User user = this.getById(loginUser.getId());
        return user.getPoints();
    }

    // endregion ------- 以下代码为每日签到 / 积分功能 --------

    // region ------- 以下代码为用户头像上传 --------

    /**
     * 更新用户头像（复用图片上传模板，头像路径按用户隔离）
     *
     * @param file      头像图片文件
     * @param loginUser 登录用户
     * @return 更新后的脱敏用户信息
     */
    @Override
    public LoginUserVO updateUserAvatar(MultipartFile file, User loginUser) {
        if (loginUser == null || loginUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        // 复用文件上传模板上传到 COS，路径前缀 avatar/<userId>
        String uploadPathPrefix = String.format("avatar/%s", loginUser.getId());
        UploadPictureResult uploadPictureResult = filePictureUpload.uploadPicture(file, uploadPathPrefix);
        String newAvatarUrl = uploadPictureResult.getUrl();
        // 更新数据库中的头像地址
        User updateUser = new User();
        updateUser.setId(loginUser.getId());
        updateUser.setUserAvatar(newAvatarUrl);
        boolean updated = this.updateById(updateUser);
        if (!updated) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "头像更新失败");
        }
        // 清理旧头像的 COS 文件（原图、压缩图、缩略图），失败不影响主流程
        this.deleteOldAvatar(loginUser.getUserAvatar());
        // 返回最新脱敏用户信息
        User user = this.getById(loginUser.getId());
        return this.getLoginUserVO(user);
    }

    /**
     * 清理旧头像文件，避免 COS 孤儿文件
     */
    private void deleteOldAvatar(String oldAvatarUrl) {
        if (StrUtil.isBlank(oldAvatarUrl)) {
            return;
        }
        try {
            String host = cosClientConfig.getHost();
            if (!oldAvatarUrl.startsWith(host)) {
                return;
            }
            String key = StrUtil.removePrefix(oldAvatarUrl, host + "/");
            // 仅清理 avatar 目录下的对象，且按主文件名前缀匹配（原图、webp、缩略图），防止误删
            if (!key.startsWith("avatar/") || StrUtil.isBlank(FileUtil.mainName(key))) {
                return;
            }
            cosManager.deleteObjectsByPrefix(FileUtil.mainName(key));
        } catch (Exception e) {
            log.warn("旧头像清理失败, url = {}", oldAvatarUrl, e);
        }
    }

    // endregion ------- 以下代码为用户头像上传 --------

    // region ------- 以下代码为邀请计划 --------

    /**
     * 获取当前用户的邀请计划信息
     *
     * @param loginUser 登录用户
     * @return 邀请码、邀请列表、解锁进度等
     */
    @Override
    public UserInviteInfoVO getUserInviteInfo(User loginUser) {
        if (loginUser == null || loginUser.getId() == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User user = this.getById(loginUser.getId());
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }
        UserInviteInfoVO vo = new UserInviteInfoVO();
        // 老用户邀请码为空时懒生成，保证所有人都有专属邀请码
        if (StrUtil.isBlank(user.getInviteCode())) {
            String code = generateUniqueInviteCode();
            User update = new User();
            update.setId(user.getId());
            update.setInviteCode(code);
            this.updateById(update);
            user.setInviteCode(code);
        }
        vo.setInviteCode(user.getInviteCode());
        // 我邀请的用户列表
        List<User> invitees = this.list(new QueryWrapper<User>()
                .eq("inviterId", user.getId())
                .orderByDesc("createTime"));
        List<InviteRecordVO> records = invitees.stream().map(invitee -> {
            InviteRecordVO record = new InviteRecordVO();
            record.setUserId(invitee.getId());
            record.setUserName(invitee.getUserName());
            record.setUserAvatar(invitee.getUserAvatar());
            record.setCreateTime(invitee.getCreateTime());
            return record;
        }).collect(Collectors.toList());
        vo.setInviteList(records);
        vo.setSuccessCount(records.size());
        vo.setUnlockTarget(UserConstant.INVITE_PERMANENT_COUNT);
        vo.setRemainCount(Math.max(UserConstant.INVITE_PERMANENT_COUNT - records.size(), 0));
        Date expireTime = user.getVipExpireTime();
        vo.setVipExpireTime(expireTime);
        boolean unlocked = expireTime != null && expireTime.after(new Date());
        vo.setMemberUnlocked(unlocked);
        vo.setPermanentMember(unlocked && expireTime.compareTo(PERMANENT_VIP_TIME) >= 0);
        return vo;
    }

    /**
     * 邀请排行榜：按成功邀请人数降序，取前 10 名
     */
    @Override
    public List<InviteRankVO> getInviteRank() {
        // 按 inviterId 分组统计邀请人数
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.select("inviterId", "COUNT(*) AS cnt")
                .isNotNull("inviterId")
                .groupBy("inviterId");
        List<Map<String, Object>> rows = this.listMaps(queryWrapper);
        if (CollUtil.isEmpty(rows)) {
            return new ArrayList<>();
        }
        // 按人数排序取前 10
        List<Map<String, Object>> top = rows.stream()
                .sorted((a, b) -> Long.compare(
                        Long.parseLong(String.valueOf(b.get("cnt"))),
                        Long.parseLong(String.valueOf(a.get("cnt")))))
                .limit(10)
                .collect(Collectors.toList());
        // 批量查询邀请人信息
        List<Long> inviterIds = top.stream()
                .map(row -> Long.parseLong(String.valueOf(row.get("inviterId"))))
                .collect(Collectors.toList());
        Map<Long, User> inviterMap = this.listByIds(inviterIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        return top.stream().map(row -> {
            Long inviterId = Long.parseLong(String.valueOf(row.get("inviterId")));
            long cnt = Long.parseLong(String.valueOf(row.get("cnt")));
            InviteRankVO rankVO = new InviteRankVO();
            rankVO.setUserId(inviterId);
            rankVO.setCount(cnt);
            User inviter = inviterMap.get(inviterId);
            if (inviter != null) {
                rankVO.setUserName(inviter.getUserName());
                rankVO.setUserAvatar(inviter.getUserAvatar());
            }
            return rankVO;
        }).collect(Collectors.toList());
    }

    // endregion ------- 以下代码为邀请计划 --------
}




