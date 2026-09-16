package com.visupicture.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 批量导入图片请求
 */
@Data
public class PictureUploadByBatchRequest implements Serializable {

    /**
     * 搜索词
     */
    private String searchText;

    /**
     * 抓取数量
     */
    private Integer count = 10;

    /**
     * 图片名称前缀
     */
    private String namePrefix;

    /**
     * 是否开启 AI 配文（自动生成名称、简介、分类、标签），默认开启
     */
    private Boolean aiEdit;

    /**
     * 抓取源：bing（默认，必应图片搜索）/ pexels（Pexels 高清图库）
     */
    private String searchSource;

    private static final long serialVersionUID = 1L;
}