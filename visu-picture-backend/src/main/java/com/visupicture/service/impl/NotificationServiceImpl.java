package com.visupicture.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.visupicture.constant.UserConstant;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.mapper.NotificationMapper;
import com.visupicture.model.entity.Notification;
import com.visupicture.model.entity.User;
import com.visupicture.model.enums.NotificationTypeEnum;
import com.visupicture.model.vo.NotificationVO;
import com.visupicture.service.NotificationService;
import com.visupicture.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 站内通知服务实现
 */
@Slf4j
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
        implements NotificationService {

    @Resource
    private UserService userService;

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
                notification.setContent("审核已通过，您现在可以查看您的图片！");
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
    public void sendAuditTaskNotification(Long picId, String picName, String picThumbnail, String uploaderName) {
        try {
            List<Long> adminIds = listAdminUserIds();
            if (CollUtil.isEmpty(adminIds)) {
                return;
            }
            String shortName = picName != null && picName.length() > 20
                    ? picName.substring(0, 20) + "…" : picName;
            String who = StrUtil.blankToDefault(uploaderName, "用户");
            List<Notification> list = new ArrayList<>();
            for (Long adminId : adminIds) {
                Notification notification = new Notification();
                notification.setUserId(adminId);
                notification.setType(NotificationTypeEnum.AUDIT.getValue());
                notification.setTitle("有新的图片待审核");
                notification.setContent(who + " 上传了「" + shortName + "」，请及时审核");
                notification.setPicId(picId);
                notification.setPicThumbnail(picThumbnail);
                notification.setIsRead(0);
                list.add(notification);
            }
            this.saveBatch(list);
        } catch (Exception e) {
            // 通知失败不影响上传主流程
            log.error("发送待审核通知失败, picId={}", picId, e);
        }
    }

    @Override
    public void sendAuditTaskBatchNotification(int count, String uploaderName) {
        if (count <= 0) {
            return;
        }
        try {
            List<Long> adminIds = listAdminUserIds();
            if (CollUtil.isEmpty(adminIds)) {
                return;
            }
            String who = StrUtil.blankToDefault(uploaderName, "用户");
            List<Notification> list = new ArrayList<>();
            for (Long adminId : adminIds) {
                Notification notification = new Notification();
                notification.setUserId(adminId);
                notification.setType(NotificationTypeEnum.AUDIT.getValue());
                notification.setTitle("有 " + count + " 张图片待审核");
                notification.setContent(who + " 批量上传了 " + count + " 张图片，请及时审核");
                notification.setIsRead(0);
                list.add(notification);
            }
            this.saveBatch(list);
        } catch (Exception e) {
            log.error("发送批量待审核通知失败, count={}", count, e);
        }
    }

    @Override
    public int sendSystemNotification(String title, String content, Long targetUserId) {
        ThrowUtils.throwIf(StrUtil.isBlank(title), ErrorCode.PARAMS_ERROR, "标题不能为空");
        ThrowUtils.throwIf(title.length() > 128, ErrorCode.PARAMS_ERROR, "标题不能超过 128 字");
        ThrowUtils.throwIf(content != null && content.length() > 512, ErrorCode.PARAMS_ERROR, "内容不能超过 512 字");
        // 接收人：指定用户或全部用户
        List<Long> receiverIds;
        if (targetUserId != null) {
            ThrowUtils.throwIf(userService.getById(targetUserId) == null,
                    ErrorCode.NOT_FOUND_ERROR, "接收用户不存在");
            receiverIds = Collections.singletonList(targetUserId);
        } else {
            receiverIds = userService.list(new QueryWrapper<User>().select("id"))
                    .stream().map(User::getId).collect(Collectors.toList());
        }
        if (CollUtil.isEmpty(receiverIds)) {
            return 0;
        }
        List<Notification> list = new ArrayList<>();
        for (Long receiverId : receiverIds) {
            Notification notification = new Notification();
            notification.setUserId(receiverId);
            notification.setType(NotificationTypeEnum.SYSTEM.getValue());
            notification.setTitle(title);
            notification.setContent(content);
            notification.setIsRead(0);
            list.add(notification);
        }
        // 分批入库，避免一次插入过多
        final int chunk = 500;
        for (int i = 0; i < list.size(); i += chunk) {
            this.saveBatch(list.subList(i, Math.min(i + chunk, list.size())));
        }
        log.info("管理员发布系统消息「{}」，接收人数 {}", title, receiverIds.size());
        return receiverIds.size();
    }

    /**
     * 查询全部管理员用户 id
     */
    private List<Long> listAdminUserIds() {
        return userService.list(new QueryWrapper<User>()
                        .select("id")
                        .eq("userRole", UserConstant.ADMIN_ROLE))
                .stream()
                .map(User::getId)
                .collect(Collectors.toList());
    }

    @Override
    public void sendInteractionNotification(Long targetUserId, String actorName, String actionText,
                                            Long picId, String picName, String picThumbnail, String content) {
        if (targetUserId == null) {
            return;
        }
        try {
            String shortName = picName != null && picName.length() > 20
                    ? picName.substring(0, 20) + "…" : picName;
            String who = StrUtil.blankToDefault(actorName, "有人");
            String action = StrUtil.blankToDefault(actionText, "互动了");
            Notification notification = new Notification();
            notification.setUserId(targetUserId);
            notification.setType(NotificationTypeEnum.INTERACT.getValue());
            notification.setTitle(who + " " + action + "你的图片「" + shortName + "」");
            notification.setContent(StrUtil.maxLength(content, 200));
            notification.setPicId(picId);
            notification.setPicThumbnail(picThumbnail);
            notification.setIsRead(0);
            this.save(notification);
        } catch (Exception e) {
            // 通知失败不影响互动主流程
            log.error("发送互动通知失败, targetUserId={}, picId={}", targetUserId, picId, e);
        }
    }

    @Override
    public Page<NotificationVO> pageMyNotifications(long userId, long current, long pageSize, String category,
                                                    String keyword) {
        long page = Math.max(current, 1);
        long size = Math.min(Math.max(pageSize, 1), 50);
        QueryWrapper<Notification> wrapper = new QueryWrapper<>();
        wrapper.eq("userId", userId);
        // 分类筛选（category 与 NotificationTypeEnum 对齐，all/unread 为特殊值）：
        // interact = 互动消息（评论/回复）；审核 = 审核结果 + 待审核提醒；系统 = 其余类型；未读 = isRead=0
        if (NotificationTypeEnum.INTERACT.getValue().equals(category)) {
            wrapper.eq("type", category);
        } else if (NotificationTypeEnum.REVIEW.getValue().equals(category)) {
            wrapper.in("type", NotificationTypeEnum.REVIEW.getValue(), NotificationTypeEnum.AUDIT.getValue());
        } else if (NotificationTypeEnum.SYSTEM.getValue().equals(category)) {
            wrapper.notIn("type", NotificationTypeEnum.REVIEW.getValue(), NotificationTypeEnum.AUDIT.getValue());
        } else if ("unread".equals(category)) {
            wrapper.eq("isRead", 0);
        }
        // 关键词搜索：标题或内容任一命中
        if (StrUtil.isNotBlank(keyword)) {
            wrapper.and(w -> w.like("title", keyword).or().like("content", keyword));
        }
        wrapper.orderByDesc("createTime").orderByDesc("id");
        Page<Notification> entityPage = this.page(new Page<>(page, size), wrapper);
        // 转 VO 分页对象（保留 total，供前端分页器计算页数）
        Page<NotificationVO> voPage = new Page<>(entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        voPage.setRecords(entityPage.getRecords().stream().map(this::toVO).collect(Collectors.toList()));
        return voPage;
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

    /**
     * 删除我的通知：仅本人通知可删（逻辑删除，与列表查询的过滤条件一致）
     */
    @Override
    public boolean deleteMyNotification(long userId, long notificationId) {
        Notification notification = this.getById(notificationId);
        if (notification == null || !notification.getUserId().equals(userId)) {
            // 通知不存在或不属于当前用户：保持幂等，不抛错
            return false;
        }
        return this.removeById(notificationId);
    }

    /**
     * 批量标记已读：限定本人 + 指定 id 集合，单条 SQL 完成
     */
    @Override
    public int markReadBatch(long userId, List<Long> notificationIds) {
        if (CollUtil.isEmpty(notificationIds)) {
            return 0;
        }
        boolean ok = this.update(new LambdaUpdateWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .in(Notification::getId, notificationIds)
                .eq(Notification::getIsRead, 0)
                .set(Notification::getIsRead, 1));
        return ok ? notificationIds.size() : 0;
    }

    /**
     * 批量删除：限定本人 + 指定 id 集合（逻辑删除），单条 SQL 完成
     */
    @Override
    public int deleteBatch(long userId, List<Long> notificationIds) {
        if (CollUtil.isEmpty(notificationIds)) {
            return 0;
        }
        boolean ok = this.remove(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getUserId, userId)
                .in(Notification::getId, notificationIds));
        return ok ? notificationIds.size() : 0;
    }

    private NotificationVO toVO(Notification notification) {
        NotificationVO vo = new NotificationVO();
        BeanUtil.copyProperties(notification, vo);
        return vo;
    }
}
