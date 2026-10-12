package com.visupicture.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.visupicture.exception.BusinessException;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.mapper.CommentLikeMapper;
import com.visupicture.mapper.PictureCommentMapper;
import com.visupicture.mapper.PictureFavoriteMapper;
import com.visupicture.mapper.PictureLikeMapper;
import com.visupicture.mapper.PictureMapper;
import com.visupicture.mapper.PictureShareMapper;
import com.visupicture.mapper.PictureViewHistoryMapper;
import com.visupicture.model.entity.CommentLike;
import com.visupicture.model.entity.Picture;
import com.visupicture.model.entity.PictureComment;
import com.visupicture.model.entity.PictureFavorite;
import com.visupicture.model.entity.PictureLike;
import com.visupicture.model.entity.PictureShare;
import com.visupicture.model.entity.PictureViewHistory;
import com.visupicture.model.entity.User;
import com.visupicture.model.enums.PictureReviewStatusEnum;
import com.visupicture.model.vo.PictureCommentVO;
import com.visupicture.model.vo.PictureVO;
import com.visupicture.model.vo.UserVO;
import com.visupicture.service.NotificationService;
import com.visupicture.service.PictureInteractionService;
import com.visupicture.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图片互动服务实现（点赞、收藏、评论、浏览历史）
 */
@Slf4j
@Service
public class PictureInteractionServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureInteractionService {

    /**
     * 单条评论最大长度
     */
    private static final int COMMENT_MAX_LENGTH = 500;

    /**
     * 浏览历史最多保留条数（超出时清理最旧的，避免表无限增长）
     */
    private static final int VIEW_HISTORY_KEEP = 200;

    @Resource
    private PictureMapper pictureMapper;

    @Resource
    private PictureLikeMapper pictureLikeMapper;

    @Resource
    private PictureFavoriteMapper pictureFavoriteMapper;

    @Resource
    private PictureCommentMapper pictureCommentMapper;

    @Resource
    private CommentLikeMapper commentLikeMapper;

    @Resource
    private PictureViewHistoryMapper pictureViewHistoryMapper;

    @Resource
    private PictureShareMapper pictureShareMapper;

    @Resource
    private UserService userService;

    @Resource
    private NotificationService notificationService;

    @Resource
    private TransactionTemplate transactionTemplate;

    /**
     * 校验图片存在且对当前用户可见
     *
     * @param pictureId 图片 id
     * @return 图片实体（不存在则抛异常）
     */
    private Picture requirePicture(long pictureId) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR, "图片 id 不合法");
        Picture picture = pictureMapper.selectById(pictureId);
        ThrowUtils.throwIf(picture == null, ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        return picture;
    }

    // ============================ 点赞 ============================

    /**
     * 点赞 / 取消点赞
     *
     * <p><b>幂等实现要点</b>：计数是否变更完全由「记录表的写入结果」决定，而不是由
     * 前端传入的 isLike 决定。这样即使用户连点、或两个请求并发到达，
     * 也只有真正改变了记录状态的那一次会去改计数。</p>
     *
     * <ul>
     *   <li>点赞：{@code INSERT IGNORE} 命中唯一索引返回 0（已赞过）→ 不动计数；
     *       返回 1（首次点赞）→ likeCount + 1。</li>
     *   <li>取消：{@code DELETE} 返回 0（本来就没赞）→ 不动计数；
     *       返回 1（确实取消）→ likeCount 用 GREATEST(x-1, 0) 递减，保证不为负。</li>
     * </ul>
     * 记录与计数在同一事务内提交，不会出现「记录成功但计数没加」的漂移。
     */
    @Override
    public int like(long pictureId, long userId, boolean isLike) {
        requirePicture(pictureId);
        Integer result = transactionTemplate.execute(status -> {
            if (isLike) {
                // 唯一索引 (pictureId, userId) + INSERT IGNORE：重复点赞被静默忽略
                int inserted = pictureLikeMapper.insertIgnore(pictureId, userId);
                if (inserted == 1) {
                    pictureMapper.update(null, new UpdateWrapper<Picture>()
                            .eq("id", pictureId)
                            .setSql("likeCount = likeCount + 1"));
                }
            } else {
                int deleted = pictureLikeMapper.delete(new QueryWrapper<PictureLike>()
                        .eq("pictureId", pictureId)
                        .eq("userId", userId));
                if (deleted > 0) {
                    // GREATEST 兜底：即使历史数据导致计数与记录不一致，也不会出现负数
                    pictureMapper.update(null, new UpdateWrapper<Picture>()
                            .eq("id", pictureId)
                            .setSql("likeCount = GREATEST(likeCount - 1, 0)"));
                }
            }
            return currentCount(pictureId, "likeCount");
        });
        return result == null ? 0 : result;
    }

    /**
     * 读取主表当前计数（在事务内读，拿到的是本次变更后的值）
     */
    private int currentCount(long pictureId, String column) {
        QueryWrapper<Picture> wrapper = new QueryWrapper<>();
        wrapper.select(column).eq("id", pictureId);
        Picture picture = pictureMapper.selectOne(wrapper);
        if (picture == null) {
            return 0;
        }
        Integer value = "likeCount".equals(column) ? picture.getLikeCount() : picture.getFavoriteCount();
        return value == null ? 0 : value;
    }

    // ============================ 收藏 ============================

    @Override
    public int favorite(long pictureId, long userId, boolean isFavorite) {
        requirePicture(pictureId);
        Integer result = transactionTemplate.execute(status -> {
            if (isFavorite) {
                int inserted = pictureFavoriteMapper.insertIgnore(pictureId, userId);
                if (inserted == 1) {
                    pictureMapper.update(null, new UpdateWrapper<Picture>()
                            .eq("id", pictureId)
                            .setSql("favoriteCount = favoriteCount + 1"));
                }
            } else {
                int deleted = pictureFavoriteMapper.delete(new QueryWrapper<PictureFavorite>()
                        .eq("pictureId", pictureId)
                        .eq("userId", userId));
                if (deleted > 0) {
                    pictureMapper.update(null, new UpdateWrapper<Picture>()
                            .eq("id", pictureId)
                            .setSql("favoriteCount = GREATEST(favoriteCount - 1, 0)"));
                }
            }
            return currentCount(pictureId, "favoriteCount");
        });
        return result == null ? 0 : result;
    }

    // ====================== 状态查询与填充 ======================

    @Override
    public boolean[] queryInteractionState(long pictureId, Long userId) {
        if (userId == null) {
            return new boolean[]{false, false};
        }
        long liked = pictureLikeMapper.selectCount(new QueryWrapper<PictureLike>()
                .eq("pictureId", pictureId).eq("userId", userId));
        long favorited = pictureFavoriteMapper.selectCount(new QueryWrapper<PictureFavorite>()
                .eq("pictureId", pictureId).eq("userId", userId));
        return new boolean[]{liked > 0, favorited > 0};
    }

    /**
     * 批量填充点赞/收藏状态
     *
     * <p>用两个 IN 查询一次性取回当前用户对整页图片的点赞与收藏集合，
     * 再在内存里标记，避免逐条查询造成 N+1（一页 20 张图会变成 40 次查询）。</p>
     */
    @Override
    public void fillInteractionState(List<PictureVO> pictureVOList, Long userId) {
        if (CollUtil.isEmpty(pictureVOList)) {
            return;
        }
        if (userId == null) {
            pictureVOList.forEach(vo -> {
                vo.setIsLiked(false);
                vo.setIsFavorited(false);
            });
            return;
        }
        Set<Long> pictureIds = pictureVOList.stream()
                .map(PictureVO::getId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (pictureIds.isEmpty()) {
            return;
        }
        Set<Long> likedIds = pictureLikeMapper.selectList(new QueryWrapper<PictureLike>()
                        .select("pictureId")
                        .eq("userId", userId)
                        .in("pictureId", pictureIds))
                .stream().map(PictureLike::getPictureId).collect(Collectors.toSet());
        Set<Long> favoritedIds = pictureFavoriteMapper.selectList(new QueryWrapper<PictureFavorite>()
                        .select("pictureId")
                        .eq("userId", userId)
                        .in("pictureId", pictureIds))
                .stream().map(PictureFavorite::getPictureId).collect(Collectors.toSet());
        pictureVOList.forEach(vo -> {
            vo.setIsLiked(vo.getId() != null && likedIds.contains(vo.getId()));
            vo.setIsFavorited(vo.getId() != null && favoritedIds.contains(vo.getId()));
        });
    }

    // ============================ 评论 ============================

    @Override
    public PictureCommentVO addComment(long pictureId, String content, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        Picture picture = requirePicture(pictureId);
        String trimmed = StrUtil.trim(content);
        ThrowUtils.throwIf(StrUtil.isBlank(trimmed), ErrorCode.PARAMS_ERROR, "评论内容不能为空");
        ThrowUtils.throwIf(trimmed.length() > COMMENT_MAX_LENGTH,
                ErrorCode.PARAMS_ERROR, "评论内容不能超过 " + COMMENT_MAX_LENGTH + " 字");

        PictureComment comment = new PictureComment();
        comment.setPictureId(pictureId);
        comment.setUserId(loginUser.getId());
        comment.setContent(trimmed);
        comment.setCreateTime(new Date());
        comment.setUpdateTime(new Date());
        boolean saved = pictureCommentMapper.insert(comment) > 0;
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "评论失败");

        // 通知图片作者（自己评论自己的图片不通知；失败不影响评论）
        Long authorId = picture.getUserId();
        if (authorId != null && !authorId.equals(loginUser.getId())) {
            notificationService.sendInteractionNotification(
                    authorId,
                    loginUser.getUserName(),
                    "评论了",
                    picture.getId(),
                    picture.getName(),
                    picture.getThumbnailUrl(),
                    trimmed);
        }
        return toCommentVO(comment, loginUser);
    }

    @Override
    public Page<PictureCommentVO> pageComments(long pictureId, long current, long pageSize, User loginUser,
                                               String sortField) {
        long page = Math.max(current, 1);
        long size = Math.min(Math.max(pageSize, 1), 50);
        QueryWrapper<PictureComment> wrapper = new QueryWrapper<>();
        wrapper.eq("pictureId", pictureId);
        // 排序方式：hot-按点赞数（相同赞数按时间倒序，保证分页稳定）；new-按时间倒序。
        // sortField 是白名单内的枚举值而非自由列名，不存在注入风险。
        if ("new".equals(sortField)) {
            wrapper.orderByDesc("createTime").orderByDesc("id");
        } else {
            // 默认 hot：热门优先
            wrapper.orderByDesc("likeCount").orderByDesc("createTime").orderByDesc("id");
        }
        Page<PictureComment> entityPage = pictureCommentMapper.selectPage(new Page<>(page, size), wrapper);

        Page<PictureCommentVO> voPage = new Page<>(
                entityPage.getCurrent(), entityPage.getSize(), entityPage.getTotal());
        if (CollUtil.isEmpty(entityPage.getRecords())) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }
        // 批量取评论者信息，避免 N+1
        Set<Long> userIds = entityPage.getRecords().stream()
                .map(PictureComment::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Long loginUserId = loginUser == null ? null : loginUser.getId();
        boolean isAdmin = loginUser != null && userService.isAdmin(loginUser);

        // 批量取当前用户对本页评论的点赞集合（两个 IN 查询完成整页，避免 N+1）
        Set<Long> likedCommentIds = Collections.emptySet();
        if (loginUserId != null) {
            List<Long> commentIds = entityPage.getRecords().stream()
                    .map(PictureComment::getId).collect(Collectors.toList());
            likedCommentIds = commentLikeMapper.selectList(new QueryWrapper<CommentLike>()
                            .select("commentId")
                            .eq("userId", loginUserId)
                            .in("commentId", commentIds))
                    .stream().map(CommentLike::getCommentId).collect(Collectors.toSet());
        }

        List<PictureCommentVO> voList = new ArrayList<>();
        for (PictureComment comment : entityPage.getRecords()) {
            PictureCommentVO vo = new PictureCommentVO();
            vo.setId(comment.getId());
            vo.setPictureId(comment.getPictureId());
            vo.setUserId(comment.getUserId());
            vo.setContent(comment.getContent());
            vo.setLikeCount(comment.getLikeCount() == null ? 0 : comment.getLikeCount());
            vo.setIsLiked(loginUserId != null && likedCommentIds.contains(comment.getId()));
            vo.setCreateTime(comment.getCreateTime());
            vo.setUser(userService.getUserVO(userMap.get(comment.getUserId())));
            vo.setCanDelete(isAdmin || (loginUserId != null && loginUserId.equals(comment.getUserId())));
            voList.add(vo);
        }
        voPage.setRecords(voList);
        return voPage;
    }

    /**
     * 评论点赞 / 取消点赞
     *
     * <p>与图片点赞完全同构：计数是否变更由 comment_like 记录表的写入结果决定，
     * 唯一索引 uk_commentId_userId + INSERT IGNORE 保证幂等，
     * 记录与计数在同一事务内提交。</p>
     */
    @Override
    public int likeComment(long commentId, long userId, boolean isLike) {
        PictureComment comment = pictureCommentMapper.selectById(commentId);
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR, "评论不存在");
        Integer result = transactionTemplate.execute(status -> {
            if (isLike) {
                int inserted = commentLikeMapper.insertIgnore(commentId, userId);
                if (inserted == 1) {
                    pictureCommentMapper.update(null, new UpdateWrapper<PictureComment>()
                            .eq("id", commentId)
                            .setSql("likeCount = likeCount + 1"));
                }
            } else {
                int deleted = commentLikeMapper.delete(new QueryWrapper<CommentLike>()
                        .eq("commentId", commentId)
                        .eq("userId", userId));
                if (deleted > 0) {
                    // GREATEST 兜底：即使历史数据不一致也不会出现负数
                    pictureCommentMapper.update(null, new UpdateWrapper<PictureComment>()
                            .eq("id", commentId)
                            .setSql("likeCount = GREATEST(likeCount - 1, 0)"));
                }
            }
            PictureComment after = pictureCommentMapper.selectById(commentId);
            return after == null || after.getLikeCount() == null ? 0 : after.getLikeCount();
        });
        return result == null ? 0 : result;
    }

    @Override
    public void deleteComment(long commentId, User loginUser) {
        ThrowUtils.throwIf(loginUser == null || loginUser.getId() == null, ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(commentId <= 0, ErrorCode.PARAMS_ERROR);
        PictureComment comment = pictureCommentMapper.selectById(commentId);
        ThrowUtils.throwIf(comment == null, ErrorCode.NOT_FOUND_ERROR, "评论不存在");
        // 仅评论本人或管理员可删除
        boolean isOwner = comment.getUserId() != null && comment.getUserId().equals(loginUser.getId());
        ThrowUtils.throwIf(!isOwner && !userService.isAdmin(loginUser),
                ErrorCode.NO_AUTH_ERROR, "无权删除该评论");
        // @TableLogic：逻辑删除
        int deleted = pictureCommentMapper.deleteById(commentId);
        ThrowUtils.throwIf(deleted <= 0, ErrorCode.OPERATION_ERROR, "删除失败");
    }

    private PictureCommentVO toCommentVO(PictureComment comment, User loginUser) {
        PictureCommentVO vo = new PictureCommentVO();
        vo.setId(comment.getId());
        vo.setPictureId(comment.getPictureId());
        vo.setUserId(comment.getUserId());
        vo.setContent(comment.getContent());
        vo.setCreateTime(comment.getCreateTime());
        vo.setUser(userService.getUserVO(userService.getById(comment.getUserId())));
        boolean isAdmin = loginUser != null && userService.isAdmin(loginUser);
        vo.setCanDelete(isAdmin || (loginUser != null && loginUser.getId().equals(comment.getUserId())));
        return vo;
    }

    // ========================= 浏览历史 =========================

    @Override
    public void recordView(long pictureId, long userId) {
        requirePicture(pictureId);
        try {
            // 唯一索引 (userId, pictureId) + ON DUPLICATE KEY UPDATE：
            // 一条 SQL 完成「首次插入 / 重复刷新时间并累加次数」，无竞态、无重复占位
            pictureViewHistoryMapper.recordView(userId, pictureId);
            trimViewHistory(userId);
        } catch (Exception e) {
            // 浏览历史是旁路数据，记录失败不影响查看图片
            log.error("记录浏览历史失败, userId={}, pictureId={}", userId, pictureId, e);
        }
    }

    /**
     * 控制单个用户的浏览历史规模：超过上限时删除最旧的记录
     */
    private void trimViewHistory(long userId) {
        Long total = pictureViewHistoryMapper.selectCount(
                new QueryWrapper<PictureViewHistory>().eq("userId", userId));
        if (total == null || total <= VIEW_HISTORY_KEEP) {
            return;
        }
        // 找出第 KEEP 条之后的所有记录 id（按时间倒序，跳过前 KEEP 条）
        List<PictureViewHistory> overflow = pictureViewHistoryMapper.selectList(
                new QueryWrapper<PictureViewHistory>()
                        .select("id")
                        .eq("userId", userId)
                        .orderByDesc("viewTime").orderByDesc("id")
                        .last("limit " + VIEW_HISTORY_KEEP + ", 1000"));
        if (CollUtil.isEmpty(overflow)) {
            return;
        }
        List<Long> ids = overflow.stream().map(PictureViewHistory::getId).collect(Collectors.toList());
        pictureViewHistoryMapper.deleteBatchIds(ids);
    }

    @Override
    public Page<PictureVO> pageMyViewHistory(long userId, long current, long pageSize) {
        long page = Math.max(current, 1);
        long size = Math.min(Math.max(pageSize, 1), 50);
        Page<PictureViewHistory> historyPage = pictureViewHistoryMapper.selectPage(
                new Page<>(page, size),
                new QueryWrapper<PictureViewHistory>()
                        .eq("userId", userId)
                        .orderByDesc("viewTime").orderByDesc("id"));
        return toPictureVOPage(historyPage, userId);
    }

    @Override
    public int clearMyViewHistory(long userId) {
        return pictureViewHistoryMapper.delete(
                new QueryWrapper<PictureViewHistory>().eq("userId", userId));
    }

    // ==================== 我的收藏 / 我的点赞 ====================

    @Override
    public Page<PictureVO> pageMyFavorites(long userId, long current, long pageSize) {
        long page = Math.max(current, 1);
        long size = Math.min(Math.max(pageSize, 1), 50);
        Page<PictureFavorite> favoritePage = pictureFavoriteMapper.selectPage(
                new Page<>(page, size),
                new QueryWrapper<PictureFavorite>()
                        .eq("userId", userId)
                        .orderByDesc("createTime").orderByDesc("id"));
        return toPictureVOPage(favoritePage, userId);
    }

    @Override
    public Page<PictureVO> pageMyLikes(long userId, long current, long pageSize) {
        long page = Math.max(current, 1);
        long size = Math.min(Math.max(pageSize, 1), 50);
        Page<PictureLike> likePage = pictureLikeMapper.selectPage(
                new Page<>(page, size),
                new QueryWrapper<PictureLike>()
                        .eq("userId", userId)
                        .orderByDesc("createTime").orderByDesc("id"));
        return toPictureVOPage(likePage, userId);
    }

    /**
     * 关联记录分页 → 图片 VO 分页
     *
     * <p>三条硬性约束：</p>
     * <ol>
     *   <li><b>过滤不可见图片</b>：被逻辑删除的、或未通过审核且非本人上传的图片不能出现在
     *       「我的收藏/点赞/历史」里 —— 否则等于绕过审核机制泄露未过审内容。</li>
     *   <li><b>保持关联表的顺序</b>：图片按关联记录的先后（收藏/点赞/查看时间倒序）排列，
     *       不能因为改成批量查询就丢失顺序。</li>
     *   <li><b>total 用关联表的原始总数</b>：被过滤掉的图片仍计入总数，
     *       这样前端分页器的页数不会因过滤而跳动。</li>
     * </ol>
     *
     * @param relatedPage 关联记录分页（图片集合只用其顺序与 total）
     * @param userId      当前用户 id（用于判定"本人上传"从而放行未过审的自己图片）
     */
    private Page<PictureVO> toPictureVOPage(Page<?> relatedPage, long userId) {
        List<Long> orderedIds = new ArrayList<>();
        for (Object record : relatedPage.getRecords()) {
            Long pictureId = extractPictureId(record);
            if (pictureId != null) {
                orderedIds.add(pictureId);
            }
        }
        Page<PictureVO> voPage = new Page<>(
                relatedPage.getCurrent(), relatedPage.getSize(), relatedPage.getTotal());
        if (orderedIds.isEmpty()) {
            voPage.setRecords(Collections.emptyList());
            return voPage;
        }
        // 批量查图片（selectBatchIds 自动带上 @TableLogic 条件，已删除的不会返回），
        // 并按可见性过滤：审核通过 或 本人上传
        List<Picture> pictureList = pictureMapper.selectBatchIds(orderedIds);
        Map<Long, Picture> pictureMap = new LinkedHashMap<>();
        for (Picture picture : pictureList) {
            boolean pass = picture.getReviewStatus() != null
                    && picture.getReviewStatus() == PictureReviewStatusEnum.PASS.getValue();
            boolean mine = picture.getUserId() != null && picture.getUserId() == userId;
            if (pass || mine) {
                pictureMap.put(picture.getId(), picture);
            }
        }

        List<PictureVO> voList = new ArrayList<>();
        for (Long id : orderedIds) {
            Picture picture = pictureMap.get(id);
            if (picture == null) {
                // 图片已删除 / 未过审且非本人：跳过（不占位）
                continue;
            }
            voList.add(PictureVO.objToVo(picture));
        }
        // 填充作者信息
        if (!voList.isEmpty()) {
            Set<Long> authorIds = voList.stream().map(PictureVO::getUserId)
                    .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
            if (!authorIds.isEmpty()) {
                Map<Long, User> authorMap = userService.listByIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
                voList.forEach(vo -> vo.setUser(userService.getUserVO(authorMap.get(vo.getUserId()))));
            }
            // 填充当前用户的点赞/收藏状态：否则「我的收藏」页面上收藏图标不会高亮，
            // 用户会以为自己没收藏（前端依据 isFavorited 渲染选中态）
            fillInteractionState(voList, userId);
        }
        voPage.setRecords(voList);
        return voPage;
    }

    private Long extractPictureId(Object record) {
        if (record instanceof PictureFavorite) {
            return ((PictureFavorite) record).getPictureId();
        }
        if (record instanceof PictureLike) {
            return ((PictureLike) record).getPictureId();
        }
        if (record instanceof PictureViewHistory) {
            return ((PictureViewHistory) record).getPictureId();
        }
        return null;
    }

    // ============================ 清理 ============================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cleanUpByPictureId(long pictureId) {
        // 图片被删除时一并清理其互动数据，避免关联表残留指向不存在图片的孤儿记录
        pictureLikeMapper.delete(new QueryWrapper<PictureLike>().eq("pictureId", pictureId));
        pictureFavoriteMapper.delete(new QueryWrapper<PictureFavorite>().eq("pictureId", pictureId));
        // 评论是逻辑删除表，用 delete 走 @TableLogic 置位
        pictureCommentMapper.delete(new QueryWrapper<PictureComment>().eq("pictureId", pictureId));
        pictureViewHistoryMapper.delete(new QueryWrapper<PictureViewHistory>().eq("pictureId", pictureId));
        // 分享链接失效，避免已删除图片仍可通过外链访问
        pictureShareMapper.delete(new QueryWrapper<PictureShare>().eq("pictureId", pictureId));
    }
}