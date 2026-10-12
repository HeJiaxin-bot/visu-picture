package com.visupicture.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片分享链接视图对象
 */
@Data
public class PictureShareVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 分享码
     */
    private String shareCode;

    /**
     * 完整分享地址（前端可直接复制）
     */
    private String shareUrl;

    /**
     * 图片 id
     */
    private Long pictureId;

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

    private static final long serialVersionUID = 1L;
}