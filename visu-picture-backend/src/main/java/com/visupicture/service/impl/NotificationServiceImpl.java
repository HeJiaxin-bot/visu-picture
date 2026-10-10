package com.visupicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import com.visupicture.mapper.NotificationMapper;
import com.visupicture.model.entity.Notification;
import com.visupicture.model.enums.NotificationTypeEnum;
import com.visupicture.model.vo.NotificationVO;
import com.visupicture.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内通知服务实现
 */
@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    @Override
    public void sendReviewResultNotification(Long userId, boolean pass, Long picId,
                                             String picName, String picThumbnail, String reviewMessage) {
        if (userId == null) {
            return;
        }
        try {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setType(NotificationTypeEnum.REVIEW.getValue());
            String shortName = picName != null && picName.length() > 20
                    ? picName.substring(0, 20) + "…" : picName;
            if (pass) {
                notification.setTitle("你的图片「" + shortName + "」已通过审核");
                notification.setContent("恭喜！图片已发布到公共图库，其他用户现在可以看到它了。");
            } else {
                notification.setTitle("你的图片「" + shortName + "」未通过审核");
                notification.setContent("审核意见：" + (reviewMessage == null || reviewMessage.isBlank()
                        ? "内容不符合社区规范，请修改后重新提交" : reviewMessage));
            }
            notification.setPicId(picId);
            notification.setPicThumbnail(picThumbnail);
            notification.setIsRead(0);
            this.save(notification);
        } catch (Exception e) {
            // 通知失败不影响审核主流程
            log.error("发送审核通知失败, userId={}, picId={}", userId, picId, e);
        }
    }

    @Override
    public List<NotificationVO> listMyNotifications(long userId, long current, long pageSize, boolean onlyUnread) {
        long page = Math.max(current, 1);
        long size = Math.min(Math.max(pageSize, 1), 50);
        QueryWrapper<Notification> wrapper = new QueryWrapper<>();
        wrapper.eq("userId", userId);
        if (onlyUnread) {
            wrapper.eq("isRead", 0);
        }
        wrapper.orderByDesc("createTime").orderByDesc("id");
        return this.list(wrapper).stream()
                .skip((page - 1) * size)
                .limit(size)
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    public long countMyUnread(long userId) {
        return this.count(new QueryWrapper<Notification>()
                .eq("userId", userId)
                .eq("isRead", 0));
    }

    @Override
    public boolean markRead(long userId, long notificationId) {
        Notification notification = this.getById(notificationId);
        if (notification == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "通知不存在");
        }
        // 只能操作自己的通知
        if (!notification.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作该通知");
        }
        if (notification.getIsRead() != null && notification.getIsRead() == 1) {
            return true;
        }
        return this.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getId, notificationId)
                .set(Notification::getIsRead, 1));
    }

    @Override
    public boolean markAllRead(long userId) {
        return this.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
    }

    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        BeanUtil.copyProperties(notification, vo);
        return vo;
    }
}
