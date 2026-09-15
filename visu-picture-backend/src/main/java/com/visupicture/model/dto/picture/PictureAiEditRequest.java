package com.visupicture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * AI 配文请求（AI 生成简介、分类、标签）
 */
@Data
public class PictureAiEditRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    private static final long serialVersionUID = 1L;
}
