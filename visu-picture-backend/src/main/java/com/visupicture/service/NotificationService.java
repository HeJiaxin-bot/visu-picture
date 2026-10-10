package com.visupicture.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.visupicture.model.entity.Notification;
import com.visupicture.model.vo.NotificationVO;

import java.util.List;

/**
 * 站内通知服务
 */
public interface NotificationService extends IService<Notification> {

    /**
     * 发送审核结果通知（审核通过/拒绝时调用；发送失败仅记日志，不影响审核主流程）
     *
     * @param userId       图片作者用户 id
     * @param pass         是否通过
     * @param picId        图片 id
     * @param picName      图片名称
     * @param picThumbnail 图片缩略图
     * @param reviewMessage 审核意见（拒绝理由等）
     */
    void sendReviewResultNotification(Long userId, boolean pass, Long picId,
                                      String picName, String picThumbnail, String reviewMessage);

    /**
     * 分页查询我的通知（按时间倒序）
     *
     * @param userId   用户 id
     * @param current  页码
     * @param pageSize 每页条数
     * @param onlyUnread 是否只看未读
     */
    List<NotificationVO> listMyNotifications(long userId, long current, long pageSize, boolean onlyUnread);

    /**
     * 我的未读通知数量
     */
    long countMyUnread(long userId);

    /**
     * 标记单条通知为已读（仅本人通知）
     */
    boolean markRead(long userId, long notificationId);

    /**
     * 全部标记已读
     */
    boolean markAllRead(long userId);
}
