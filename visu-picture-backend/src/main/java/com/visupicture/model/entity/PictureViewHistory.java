package com.visupicture.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片浏览历史（最近查看）
 * <p>
 * 唯一索引 (userId, pictureId)：同一张图只保留一条记录，重复查看刷新时间并累加次数，
 * 因此「最近查看」天然按时间倒序且不会重复占位。
 *
 * @TableName picture_view_history
 */
@TableName(value = "picture_view_history")
@Data
public class PictureViewHistory implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 最近查看时间
     */
    private Date viewTime;

    /**
     * 累计查看次数
     */
    private Integer viewCount;

    private static final long serialVersionUID = 1L;
}