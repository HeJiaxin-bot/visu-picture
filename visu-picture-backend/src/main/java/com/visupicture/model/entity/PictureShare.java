package com.visupicture.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片分享链接（公开访问）
 *
 * @TableName picture_share
 */
@TableName(value = "picture_share")
@Data
public class PictureShare implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 分享码（对外唯一标识，随机不可枚举）
     */
    private String shareCode;

    /**
     * 被分享的图片 id
     */
    private Long pictureId;

    /**
     * 创建分享的用户 id
     */
    private Long userId;

    /**
     * 过期时间（为空表示永久有效）
     */
    private Date expireTime;

    /**
     * 公开访问次数
     */
    private Integer viewCount;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除（撤销分享）
     */
    @TableLogic
    private Integer isDelete;

    private static final long serialVersionUID = 1L;
}