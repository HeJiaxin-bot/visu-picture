package com.visupicture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建图片分享链接请求
 */
@Data
public class PictureShareCreateRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 有效期（天）。为空或 <=0 表示永久有效
     */
    private Integer expireDays;

    private static final long serialVersionUID = 1L;
}