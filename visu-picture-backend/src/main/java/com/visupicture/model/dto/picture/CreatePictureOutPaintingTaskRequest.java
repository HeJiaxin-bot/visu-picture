package com.visupicture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建扩图任务请求（扩图统一使用 wanx2.1-imageedit 模型，无需选择模型）
 */
@Data
public class CreatePictureOutPaintingTaskRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 可选，扩图提示词，用于引导扩展区域的内容；不传时后端使用默认提示词
     */
    private String prompt;

    /**
     * 可选，四方向统一扩展比例，范围 [1.0, 2.0]，不传时由模型自动决定扩展幅度
     */
    private Float expandScale;

    private static final long serialVersionUID = 1L;
}