package com.visupicture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片点赞请求
 */
@Data
public class PictureLikeRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 是否点赞（true-点赞；false-取消点赞）
     */
    private Boolean isLike;

    private static final long serialVersionUID = 1L;
}
