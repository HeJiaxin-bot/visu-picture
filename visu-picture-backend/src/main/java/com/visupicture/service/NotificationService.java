package com.visupicture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
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
     * 通知全部管理员有新的图片待审核（用户上传后调用；失败不影响上传）
     *
     * @param picId        图片 id
     * @param picName      图片名称
     * @param picThumbnail 图片缩略图
     * @param uploaderName 上传者昵称
     */
    void sendAuditTaskNotification(Long picId, String picName, String picThumbnail, String uploaderName);

    /**
     * 批量上传场景：图片较多时只发一条汇总提醒，避免刷屏
     *
     * @param count        待审核图片数量
     * @param uploaderName 上传者昵称
     */
    void sendAuditTaskBatchNotification(int count, String uploaderName);

    /**
     * 管理员发布系统消息
     *
     * @param title        标题
     * @param content      内容
     * @param targetUserId 接收用户 id；为空表示发给全部用户
     * @return 实际接收人数
     */
    int sendSystemNotification(String title, String content, Long targetUserId);

    /**
     * 发送互动通知（如有人评论了你的图片）
     *
     * @param targetUserId 接收者（图片作者）id
     * @param actorName    触发者昵称
     * @param actionText   行为描述，如「评论了」
     * @param picId        关联图片 id
     * @param picName      图片名称
     * @param picThumbnail 图片缩略图
     * @param content      互动内容摘要（评论正文）
     */
    void sendInteractionNotification(Long targetUserId, String actorName, String actionText,
                                     Long picId, String picName, String picThumbnail, String content);

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
     * 分页查询我的通知（带总数，供消息中心分页器使用）
     *
     * @param userId   用户 id
     * @param current  页码
     * @param pageSize 每页条数
     * @param category 分类：all-全部 / review-审核通知 / system-系统通知 / unread-未读
     */
    Page<NotificationVO> pageMyNotifications(long userId, long current, long pageSize, String category);

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

    /**
     * 删除我的通知（仅本人通知，逻辑删除）
     *
     * @param userId
     * @param notificationId
     */
    boolean deleteMyNotification(long userId, long notificationId);

    /**
     * 批量标记已读（仅作用于本人的通知）
     *
     * @param userId
     * @param notificationIds
     * @return 实际标记的条数
     */
    int markReadBatch(long userId, List<Long> notificationIds);

    /**
     * 批量删除通知（仅本人的通知，逻辑删除）
     *
     * @param userId
     * @param notificationIds
     * @return 实际删除的条数
     */
    int deleteBatch(long userId, List<Long> notificationIds);
}
