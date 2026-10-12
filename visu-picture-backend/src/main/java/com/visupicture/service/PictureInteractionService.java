package com.visupicture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.visupicture.model.entity.User;
import com.visupicture.model.vo.PictureCommentVO;
import com.visupicture.model.vo.PictureVO;

/**
 * 图片互动服务：点赞、收藏、评论、浏览历史
 *
 * <p>点赞/收藏的「记录」与「计数」分离：</p>
 * <ul>
 *   <li><b>记录</b>（谁对哪张图做了什么）落在 picture_like / picture_favorite 关联表，
 *       由唯一索引保证幂等，回答「我赞过什么」「是否已赞」；</li>
 *   <li><b>计数</b>（picture.likeCount / favoriteCount）留在 picture 主表，
 *       供列表页直接读取，避免每次 COUNT。</li>
 * </ul>
 * <p>两者在同一事务内更新，保证不漂移。</p>
 */
public interface PictureInteractionService {

    /**
     * 点赞 / 取消点赞（幂等）
     *
     * @param pictureId 图片 id
     * @param userId    用户 id
     * @param isLike    true-点赞；false-取消
     * @return 操作后的点赞数；重复点赞/重复取消不会改变计数
     */
    int like(long pictureId, long userId, boolean isLike);

    /**
     * 收藏 / 取消收藏（幂等）
     *
     * @return 操作后的收藏数
     */
    int favorite(long pictureId, long userId, boolean isFavorite);

    /**
     * 查询用户是否已点赞 / 已收藏
     *
     * @return 长度为 2 的布尔数组：[是否点赞, 是否收藏]；未登录时返回 [false, false]
     */
    boolean[] queryInteractionState(long pictureId, Long userId);

    /**
     * 用当前登录用户的点赞/收藏状态填充图片 VO（批量，避免 N+1 查询）
     *
     * @param pictureVOList 图片列表
     * @param userId        当前登录用户 id（可为 null，表示未登录）
     */
    void fillInteractionState(java.util.List<PictureVO> pictureVOList, Long userId);

    /**
     * 发表评论
     *
     * @return 新评论的 VO（含评论者信息）
     */
    PictureCommentVO addComment(long pictureId, String content, User loginUser);

    /**
     * 分页查询某张图片的评论
     *
     * @param loginUser 当前登录用户（用于计算 canDelete；可为 null）
     * @param sortField 排序方式：hot-按点赞数（默认）；new-按时间倒序
     */
    Page<PictureCommentVO> pageComments(long pictureId, long current, long pageSize, User loginUser,
                                        String sortField);

    /**
     * 评论点赞 / 取消点赞（幂等，与图片点赞同构）
     *
     * @param commentId 评论 id
     * @param userId    用户 id
     * @param isLike    true-点赞；false-取消
     * @return 操作后的点赞数
     */
    int likeComment(long commentId, long userId, boolean isLike);

    /**
     * 删除评论（仅本人或管理员）
     */
    void deleteComment(long commentId, User loginUser);

    /**
     * 记录一次浏览（幂等：同图只留一条，刷新时间并累加次数）
     */
    void recordView(long pictureId, long userId);

    /**
     * 我的最近查看（按查看时间倒序）
     */
    Page<PictureVO> pageMyViewHistory(long userId, long current, long pageSize);

    /**
     * 清空我的浏览历史
     *
     * @return 清除的条数
     */
    int clearMyViewHistory(long userId);

    /**
     * 我的收藏（按收藏时间倒序）
     */
    Page<PictureVO> pageMyFavorites(long userId, long current, long pageSize);

    /**
     * 我的点赞（按点赞时间倒序）
     */
    Page<PictureVO> pageMyLikes(long userId, long current, long pageSize);

    /**
     * 删除图片时清理其全部互动数据（点赞、收藏、评论、浏览历史、分享链接）
     *
     * @param pictureId 图片 id
     */
    void cleanUpByPictureId(long pictureId);
}