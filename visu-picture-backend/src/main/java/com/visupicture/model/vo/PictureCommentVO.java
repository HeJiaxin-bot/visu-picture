package com.visupicture.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 评论视图对象（含评论者信息、楼中楼回复与删除权限标识）
 * <p>
 * 两级线程模型：顶层评论携带 replies；回复本身不再嵌套（回复"回复"仍平铺在同一层，
 * 通过 replyToUserName 标注回复对象），与 B 站 / 网易云的展示方式一致。
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
     * 父评论 id（顶层为 null）
     */
    private Long parentId;

    /**
     * 线程根评论 id（顶层为 null）
     */
    private Long rootId;

    /**
     * 被回复用户昵称快照（回复里有，顶层为 null）
     */
    private String replyToUserName;

    /**
     * 评论者 IP 属地（省份，预留字段）
     */
    private String location;

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

    /**
     * 该顶层评论下的回复总数（仅顶层有值）
     */
    private Long replyCount;

    /**
     * 楼中楼回复（仅顶层有值；分页首屏带前 N 条，展开时由专门接口拉全量）
     */
    private List<PictureCommentVO> replies = new ArrayList<>();

    private static final long serialVersionUID = 1L;
}