package com.visupicture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片收藏 / 取消收藏请求
 */
@Data
public class PictureFavoriteRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 是否收藏（true-收藏；false-取消收藏）
     */
    private Boolean isFavorite;

    private static final long serialVersionUID = 1L;
}