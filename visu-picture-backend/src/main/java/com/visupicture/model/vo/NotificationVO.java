package com.visupicture.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 站内通知视图（脱敏）
 */
@Data
public class NotificationVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 通知类型：review/system/interact
     */
    private String type;

    /**
     * 通知标题
     */
    private String title;

    /**
     * 通知内容（如拒绝理由）
     */
    private String content;

    /**
     * 关联图片 id
     */
    private Long picId;

    /**
     * 关联图片缩略图
     */
    private String picThumbnail;

    /**
     * 是否已读：0-未读 1-已读
     */
    private Integer isRead;

    /**
     * 创建时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}
