package com.visupicture.model.dto.notification;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员发布系统消息请求
 */
@Data
public class NotificationSendRequest implements Serializable {

    /**
     * 消息标题
     */
    private String title;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 接收用户 id；为空表示发送给全部用户
     */
    private Long targetUserId;

    private static final long serialVersionUID = 1L;
}
