package com.visupicture.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 评论视图对象（含评论者信息与删除权限标识）
 */
@Data
public class PictureCommentVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 评论者 id
     */
    private Long userId;

    /**
     * 评论内容
     */
    private String content;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 当前登录用户是否已赞该评论（未登录时为 false）
     */
    private Boolean isLiked;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 评论者信息
     */
    private UserVO user;

    /**
     * 当前登录用户是否有权删除该评论（本人或管理员）
     */
    private Boolean canDelete;

    private static final long serialVersionUID = 1L;
}