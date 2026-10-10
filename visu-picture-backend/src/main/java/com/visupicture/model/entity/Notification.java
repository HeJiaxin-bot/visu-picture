package com.visupicture.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 站内通知
 * @TableName notification
 */
@TableName(value = "notification")
@Data
public class Notification implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收通知的用户 id
     */
    private Long userId;

    /**
     * 通知类型：review-审核结果/system-系统公告/interact-互动
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
     * 关联图片 id（可跳转图片详情）
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

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}
