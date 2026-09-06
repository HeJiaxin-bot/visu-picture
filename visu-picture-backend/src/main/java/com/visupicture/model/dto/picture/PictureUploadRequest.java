package com.visupicture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片上传请求
 */
@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 id（用于修改）
     */
    private Long id;

    /**
     * 文件地址
     */
    private String fileUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 空间 id
     */
    private Long spaceId;

    /**
     * 是否 AI 生成（1-是，0-否）：AI 扩图上传时由前端传入
     */
    private Integer isAiGenerated;

    private static final long serialVersionUID = 1L;
}