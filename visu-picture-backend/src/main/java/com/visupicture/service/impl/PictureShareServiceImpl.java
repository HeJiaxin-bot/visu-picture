package com.visupicture.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.mapper.PictureMapper;
import com.visupicture.mapper.PictureShareMapper;
import com.visupicture.model.entity.Picture;
import com.visupicture.model.entity.PictureShare;
import com.visupicture.model.entity.User;
import com.visupicture.model.enums.PictureReviewStatusEnum;
import com.visupicture.model.vo.PictureShareVO;
import com.visupicture.model.vo.PictureVO;
import com.visupicture.service.PictureShareService;
import com.visupicture.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * 图片分享链接服务实现
 */
@Slf4j
@Service
public class PictureShareServiceImpl extends ServiceImpl<PictureShareMapper, PictureShare>
        implements PictureShareService {

    /**
     * 分享码长度。16 位 Base62 字符空间约 4.8×10^28，
     * 在分享链接总量有限的场景下无法被暴力枚举。
     */
    private static final int SHARE_CODE_LENGTH = 16;

    /**
     * 同一图片最多同时保留的有效链接数（复用逻辑之外的兜底上限）
     */
    private static final int MAX_SHARE_PER_PICTURE = 20;

    /**
     * 前端站点地址，用于拼接可直接复制的完整分享链接。
     * 各环境不同，在 profile 中配置；
     * 未配置时返回相对路径（前端会自行补全 origin）。
     */
    @Value("${app.site-url:}")
    private String siteUrl;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private PictureShareMapper pictureShareMapper;

    @Resource
    private UserService userService;

    /**
     * 校验当前用户对图片的分享管理权限：所有者或管理员
     */
    private Picture requireOwnership(long pictureId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片 id 不合法");
        Picture picture = pictureMapper.selectById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        boolean isOwner = picture.getUserId() != null && picture.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isOwner && !userService.isAdmin(loginUser),
                ErrorCode.NO_AUTH_ERROR, "无权分享该图片");
        return picture;
    }

    @Override
    public PictureShareVO createShare(long pictureId, Integer expireDays, User loginUser) {
        Picture picture = requireOwnership(pictureId, loginUser);
        // 复用已有的有效链接：同一张图反复点"分享"不应产生一堆等价链接
        PictureShare existing = findValidShare(pictureId);
        if (existing != null) {
            // 若本次指定了更晚的过期时间则续期（不缩短，避免误伤已发出的链接）
            if (expireDays != null && expireDays > 0) {
                Date newExpire = calcExpireTime(expireDays);
                if (existing.getExpireTime() == null || existing.getExpireTime().before(newExpire)) {
                    existing.setExpireTime(newExpire);
                    pictureShareMapper.updateById(existing);
                }
            }
            return toVO(existing);
        }
        // 上限保护：避免单张图产生过多链接（正常流程下复用逻辑已使其难以触发）
        long existCount = pictureShareMapper.selectCount(
                new QueryWrapper<PictureShare>().eq("pictureId", pictureId));
        ThrowUtils.throwIf(existCount >= MAX_SHARE_PER_PICTURE,
                ErrorCode.OPERATION_ERROR, "该图片的分享链接数量已达上限，请先撤销旧链接");

        PictureShare share = new PictureShare();
        share.setShareCode(generateUniqueCode());
        share.setPictureId(picture.getId());
        share.setUserId(loginUser.getId());
        share.setExpireTime(expireDays == null || expireDays <= 0 ? null : calcExpireTime(expireDays));
        share.setViewCount(0);
        share.setCreateTime(new Date());
        share.setUpdateTime(new Date());
        boolean saved = pictureShareMapper.insert(share) > 0;
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "创建分享链接失败");
        return toVO(share);
    }

    @Override
    public int revokeShare(long pictureId, User loginUser) {
        requireOwnership(pictureId, loginUser);
        // 逻辑删除（@TableLogic），链接立即失效但保留审计痕迹
        return pictureShareMapper.delete(new QueryWrapper<PictureShare>().eq("pictureId", pictureId));
    }

    @Override
    public PictureShareVO getShareByPictureId(long pictureId, User loginUser) {
        requireOwnership(pictureId, loginUser);
        PictureShare share = findValidShare(pictureId);
        return share == null ? null : toVO(share);
    }

    @Override
    public List<PictureShareVO> listMyShares(long userId) {
        List<PictureShare> list = pictureShareMapper.selectList(new QueryWrapper<PictureShare>()
                .eq("userId", userId)
                // 只列未过期的（过期时间空=永久）
                .and(w -> w.isNull("expireTime").or().gt("expireTime", new Date()))
                .orderByDesc("createTime").orderByDesc("id"));
        List<PictureShareVO> result = new ArrayList<>();
        for (PictureShare share : CollUtil.emptyIfNull(list)) {
            result.add(toVO(share));
        }
        return result;
    }

    /**
     * 查找图片当前有效的分享链接（未撤销、未过期）
     */
    private PictureShare findValidShare(long pictureId) {
        return pictureShareMapper.selectOne(new QueryWrapper<PictureShare>()
                .eq("pictureId", pictureId)
                .and(w -> w.isNull("expireTime").or().gt("expireTime", new Date()))
                .orderByDesc("id")
                .last("limit 1"));
    }

    @Override
    public PictureVO getSharedPicture(String shareCode) {
        ThrowUtils.throwIf(StrUtil.isBlank(shareCode), ErrorCode.PARAMS_ERROR, "分享码不能为空");
        PictureShare share = pictureShareMapper.selectOne(
                new QueryWrapper<PictureShare>().eq("shareCode", shareCode.trim()));
        ThrowUtils.throwIf(share == null, ErrorCode.NOT_FOUND_ERROR, "分享链接不存在或已被撤销");
        // 过期校验（在应用层判断，便于给出明确的"已过期"提示而非笼统的"不存在"）
        if (share.getExpireTime() != null && share.getExpireTime().before(new Date())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分享链接已过期");
        }
        Picture picture = pictureMapper.selectById(share.getPictureId());
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "分享的图片已被删除");
        // 公开访问的前提是图片已通过审核：未过审内容不得通过外链绕过审核机制
        boolean pass = picture.getReviewStatus() != null
                && picture.getReviewStatus() == PictureReviewStatusEnum.PASS.getValue();
        ThrowUtils.throwIf(!pass, ErrorCode.NO_AUTH_ERROR, "该图片尚未通过审核，暂不支持公开访问");

        // 累加访问次数（旁路统计，失败不回滚访问）
        try {
            pictureShareMapper.update(null, new UpdateWrapper<PictureShare>()
                    .eq("id", share.getId())
                    .setSql("viewCount = viewCount + 1"));
        } catch (Exception e) {
            log.warn("累加分享访问次数失败, shareId={}", share.getId(), e);
        }

        // 只暴露公开字段：作者信息 + 图片本身，不含 spaceId / 权限列表 / 审核信息
        PictureVO vo = PictureVO.objToVo(picture);
        vo.setSpaceId(null);
        vo.setReviewStatus(null);
        vo.setPermissionList(new ArrayList<>());
        if (picture.getUserId() != null) {
            User author = userService.getById(picture.getUserId());
            vo.setUser(userService.getUserVO(author));
        }
        return vo;
    }

    @Override
    public int invalidateByPictureId(long pictureId) {
        return pictureShareMapper.delete(new QueryWrapper<PictureShare>().eq("pictureId", pictureId));
    }

    /**
     * 生成唯一分享码：随机 + 唯一性校验（碰撞则重试）
     */
    private String generateUniqueCode() {
        for (int i = 0; i < 5; i++) {
            String code = RandomUtil.randomString(SHARE_CODE_LENGTH);
            long exists = pictureShareMapper.selectCount(
                    new QueryWrapper<PictureShare>().eq("shareCode", code));
            if (exists == 0) {
                return code;
            }
        }
        // 5 次均碰撞的概率极低；此处抛异常而非静默重试，便于发现异常情况
        throw new BusinessException(ErrorCode.OPERATION_ERROR, "生成分享码失败，请重试");
    }

    private Date calcExpireTime(int expireDays) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_MONTH, expireDays);
        return calendar.getTime();
    }

    private PictureShareVO toVO(PictureShare share) {
        PictureShareVO vo = new PictureShareVO();
        vo.setId(share.getId());
        vo.setShareCode(share.getShareCode());
        vo.setPictureId(share.getPictureId());
        vo.setExpireTime(share.getExpireTime());
        vo.setViewCount(share.getViewCount());
        vo.setCreateTime(share.getCreateTime());
        // 站点地址已配置则给绝对地址，否则给相对路径，由前端拼 origin
        String path = "/share/" + share.getShareCode();
        vo.setShareUrl(StrUtil.isBlank(siteUrl) ? path : StrUtil.removeSuffix(siteUrl, "/") + path);
        return vo;
    }
}