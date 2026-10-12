package com.visupicture.model.dto.file;

import lombok.Data;

/**
 * 上传图片的结果
 */
@Data
public class UploadPictureResult {

    /**
     * 图片地址
     */
    private String url;

    /**
     * 缩略图 url
     */
    private String thumbnailUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 文件体积
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private int picWidth;

    /**
     * 图片高度
     */
    private int picHeight;

    /**
     * 图片宽高比
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    /**
     * 图片主色调
     */
    private String picColor;

    /**
     * 图片内容 SHA-256（用于重复图片检测）
     * <p>注意：这是<b>原始输入文件</b>的摘要，不是压缩后文件的摘要 ——
     * 同一源文件无论被压缩成什么样，摘要都相同，重复检测才成立。</p>
     */
    private String picHash;
}