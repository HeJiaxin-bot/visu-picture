package com.visupicture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 发表评论 / 回复请求
 */
@Data
public class PictureCommentAddRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 被回复的评论 id（回复时传，顶层评论为空）
     */
    private Long parentId;

    /**
     * 评论内容
     */
    private String content;

    private static final long serialVersionUID = 1L;
}