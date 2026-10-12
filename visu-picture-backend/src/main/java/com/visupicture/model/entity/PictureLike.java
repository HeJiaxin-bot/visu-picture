package com.visupicture.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片点赞记录
 * <p>
 * picture.likeCount 是展示用的计数器，本表存「谁赞过哪张图」的多对多关系，
 * 用于点赞去重（唯一索引 uk_pictureId_userId）与「我的点赞」查询。
 *
 * @TableName picture_like
 */
@TableName(value = "picture_like")
@Data
public class PictureLike implements Serializable {

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
     * 点赞用户 id
     */
    private Long userId;

    /**
     * 点赞时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}