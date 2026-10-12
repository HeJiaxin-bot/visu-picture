package com.visupicture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 发表评论请求
 */
@Data
public class PictureCommentAddRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 评论内容
     */
    private String content;

    private static final long serialVersionUID = 1L;
}