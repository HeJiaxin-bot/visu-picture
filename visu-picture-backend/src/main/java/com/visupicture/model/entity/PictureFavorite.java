package com.visupicture.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片收藏记录
 *
 * @TableName picture_favorite
 */
@TableName(value = "picture_favorite")
@Data
public class PictureFavorite implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 收藏用户 id
     */
    private Long userId;

    /**
     * 收藏时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}