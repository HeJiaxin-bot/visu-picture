package com.visupicture.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 评论点赞记录
 * <p>
 * picture_comment.likeCount 是计数器，本表存「谁赞过哪条评论」的多对多关系，
 * 唯一索引 uk_commentId_userId 保证幂等。
 *
 * @TableName comment_like
 */
@TableName(value = "comment_like")
@Data
public class CommentLike implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 评论 id
     */
    private Long commentId;

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