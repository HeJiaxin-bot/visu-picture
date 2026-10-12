package com.visupicture.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.mapper.PictureMapper;
import com.visupicture.model.entity.Picture;
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
 *
 * <p><b>存储说明</b>：分享数据直接落在 {@code picture} 表的
 * {@code shareCode / shareExpireTime / shareViewCount / shareCreateTime} 四个字段上，
 * 原 {@code picture_share} 表已并入主表。</p>
 *
 * <p>为什么可以并入：一张图片最多只有一条有效分享链接 ——
 * {@link #createShare} 会复用已有链接，实测库中从未出现一张图有多条有效链接，
 * 因此这是 1:1 关系，独立成表只是历史设计。</p>
 *
 * <p>并入后失去的能力：原表用逻辑删除保留撤销痕迹（可追溯历史链接），
 * 现在撤销即 {@code shareCode = NULL}。因系统没有分享历史页面，该损失可接受。</p>
 */
@Slf4j
@Service
public class PictureShareServiceImpl implements PictureShareService {

    /**
     * 分享码长度。16 位 Base62 字符空间约 4.8×10^28，
     * 在分享链接总量有限的场景下无法被暴力枚举。
     */
    private static final int SHARE_CODE_LENGTH = 16;

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
    private UserService userService;

    /**
     * 校验当前用户对图片的分享管理权限：所有者或管理员，并返回图片
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

    /**
     * 判断图片当前是否持有有效分享链接（未撤销且未过期）
     */
    private boolean hasValidShare(Picture picture) {
        if (StrUtil.isBlank(picture.getShareCode())) {
            return false;
        }
        Date expire = picture.getShareExpireTime();
        return expire == null || expire.after(new Date());
    }

    @Override
    public PictureShareVO createShare(long pictureId, Integer expireDays, User loginUser) {
        Picture picture = requireOwnership(pictureId, loginUser);
        Date newExpire = expireDays == null || expireDays <= 0 ? null : calcExpireTime(expireDays);

        // 已有有效链接：复用，并在需要时续期（不缩短，避免误伤已发出的链接）
        if (hasValidShare(picture)) {
            Date current = picture.getShareExpireTime();
            boolean needExtend = newExpire != null
                    && (current == null || current.before(newExpire));
            if (needExtend) {
                pictureMapper.update(null, new UpdateWrapper<Picture>()
                        .eq("id", pictureId)
                        .set("shareExpireTime", newExpire));
                picture.setShareExpireTime(newExpire);
            }
            return toVO(picture);
        }

        // 无有效链接（未分享 / 已撤销 / 已过期）：生成新分享码覆盖写入。
        // 覆盖写入同时起到「过期链接失效」的作用，无需额外的清理任务。
        pictureMapper.update(null, new UpdateWrapper<Picture>()
                .eq("id", pictureId)
                .set("shareCode", generateUniqueCode())
                .set("shareExpireTime", newExpire)
                .set("shareViewCount", 0)
                .set("shareCreateTime", new Date()));
        // 回读以拿到写入后的真实值（分享码由数据库层生成，需确认落库）
        Picture updated = pictureMapper.selectById(pictureId);
        ThrowUtils.throwIf(updated == null || StrUtil.isBlank(updated.getShareCode()),
                ErrorCode.OPERATION_ERROR, "创建分享链接失败");
        return toVO(updated);
    }

    @Override
    public int revokeShare(long pictureId, User loginUser) {
        Picture picture = requireOwnership(pictureId, loginUser);
        if (StrUtil.isBlank(picture.getShareCode())) {
            return 0;
        }
        // 撤销即置空分享码：链接立即失效（无逻辑删除字段，历史痕迹不再保留）
        int updated = pictureMapper.update(null, new UpdateWrapper<Picture>()
                .eq("id", pictureId)
                .isNotNull("shareCode")
                .set("shareCode", null)
                .set("shareExpireTime", null));
        return updated;
    }

    @Override
    public PictureShareVO getShareByPictureId(long pictureId, User loginUser) {
        Picture picture = requireOwnership(pictureId, loginUser);
        return hasValidShare(picture) ? toVO(picture) : null;
    }

    @Override
    public List<PictureShareVO> listMyShares(long userId) {
        // 分享码非空 = 已分享；再过滤掉已过期的
        List<Picture> list = pictureMapper.selectList(new QueryWrapper<Picture>()
                .select("id", "shareCode", "shareExpireTime", "shareViewCount", "shareCreateTime")
                .isNotNull("shareCode")
                .and(w -> w.isNull("shareExpireTime").or().gt("shareExpireTime", new Date()))
                .orderByDesc("shareCreateTime").orderByDesc("id"));
        List<PictureShareVO> result = new ArrayList<>();
        for (Picture picture : list) {
            result.add(toVO(picture));
        }
        return result;
    }

    @Override
    public PictureVO getSharedPicture(String shareCode) {
        ThrowUtils.throwIf(StrUtil.isBlank(shareCode), ErrorCode.PARAMS_ERROR, "分享码不能为空");
        // 分享码有唯一索引，按它查即命中单条图片记录（撤销后 shareCode 为空，自然查不到）
        Picture picture = pictureMapper.selectOne(new QueryWrapper<Picture>()
                .eq("shareCode", shareCode.trim()));
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "分享链接不存在或已被撤销");
        // 过期校验（在应用层判断，便于给出明确的"已过期"提示而非笼统的"不存在"）
        if (picture.getShareExpireTime() != null && picture.getShareExpireTime().before(new Date())) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "分享链接已过期");
        }
        // 公开访问的前提是图片已通过审核：未过审内容不得通过外链绕过审核机制
        boolean pass = picture.getReviewStatus() != null
                && picture.getReviewStatus() == PictureReviewStatusEnum.PASS.getValue();
        ThrowUtils.throwIf(!pass, ErrorCode.NO_AUTH_ERROR, "该图片尚未通过审核，暂不支持公开访问");

        // 累加访问次数（旁路统计，失败不回滚访问）
        try {
            pictureMapper.update(null, new UpdateWrapper<Picture>()
                    .eq("id", picture.getId())
                    .setSql("shareViewCount = shareViewCount + 1"));
        } catch (Exception e) {
            log.warn("累加分享访问次数失败, pictureId={}", picture.getId(), e);
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
        // 图片被删除时清理逻辑会直接删行，此处仅作为显式失效入口保留
        return pictureMapper.update(null, new UpdateWrapper<Picture>()
                .eq("id", pictureId)
                .isNotNull("shareCode")
                .set("shareCode", null)
                .set("shareExpireTime", null));
    }

    /**
     * 生成唯一分享码：随机 + 唯一性校验（碰撞则重试）
     */
    private String generateUniqueCode() {
        for (int i = 0; i < 5; i++) {
            String code = RandomUtil.randomString(SHARE_CODE_LENGTH);
            Long exists = pictureMapper.selectCount(
                    new QueryWrapper<Picture>().eq("shareCode", code));
            if (exists == null || exists == 0) {
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

    /**
     * 图片实体 → 分享 VO
     */
    private PictureShareVO toVO(Picture picture) {
        PictureShareVO vo = new PictureShareVO();
        vo.setId(picture.getId());
        vo.setShareCode(picture.getShareCode());
        vo.setPictureId(picture.getId());
        vo.setExpireTime(picture.getShareExpireTime());
        vo.setViewCount(picture.getShareViewCount() == null ? 0 : picture.getShareViewCount());
        vo.setCreateTime(picture.getShareCreateTime());
        // 站点地址已配置则给绝对地址，否则给相对路径，由前端拼 origin
        String path = "/share/" + picture.getShareCode();
        vo.setShareUrl(StrUtil.isBlank(siteUrl) ? path : StrUtil.removeSuffix(siteUrl, "/") + path);
        return vo;
    }
}
