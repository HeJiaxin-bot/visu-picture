package com.visupicture.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.visupicture.annotation.AuditLog;
import com.visupicture.annotation.RateLimit;
import com.visupicture.common.BaseResponse;
import com.visupicture.common.ResultUtils;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.model.dto.picture.PictureCommentAddRequest;
import com.visupicture.model.dto.picture.PictureFavoriteRequest;
import com.visupicture.model.dto.picture.PictureLikeRequest;
import com.visupicture.model.entity.User;
import com.visupicture.model.vo.PictureCommentVO;
import com.visupicture.model.vo.PictureVO;
import com.visupicture.service.PictureInteractionService;
import com.visupicture.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 图片互动接口：点赞、收藏、评论、浏览历史
 */
@RestController
@RequestMapping("/picture")
public class PictureInteractionController {

    @Resource
    private PictureInteractionService pictureInteractionService;

    @Resource
    private UserService userService;

    // ============================ 点赞 ============================

    /**
     * 图片点赞 / 取消点赞（需登录，按用户去重）
     */
    @PostMapping("/like")
    @RateLimit(count = 30, period = 60, dimension = RateLimit.Dimension.USER,
            message = "点赞过于频繁，请稍后再试")
    public BaseResponse<Integer> likePicture(@RequestBody PictureLikeRequest pictureLikeRequest,
                                             HttpServletRequest request) {
        ThrowUtils.throwIf(pictureLikeRequest == null || pictureLikeRequest.getPictureId() == null
                || pictureLikeRequest.getPictureId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        boolean isLike = Boolean.TRUE.equals(pictureLikeRequest.getIsLike());
        // 传入 userId：去重必须绑定到具体用户，否则无法判断"是否已赞过"
        int likeCount = pictureInteractionService.like(
                pictureLikeRequest.getPictureId(), loginUser.getId(), isLike);
        return ResultUtils.success(likeCount);
    }

    // ============================ 收藏 ============================

    /**
     * 收藏 / 取消收藏（需登录，按用户去重）
     */
    @PostMapping("/favorite")
    @RateLimit(count = 30, period = 60, dimension = RateLimit.Dimension.USER,
            message = "操作过于频繁，请稍后再试")
    public BaseResponse<Integer> favoritePicture(@RequestBody PictureFavoriteRequest favoriteRequest,
                                                 HttpServletRequest request) {
        ThrowUtils.throwIf(favoriteRequest == null || favoriteRequest.getPictureId() == null
                || favoriteRequest.getPictureId() <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        boolean isFavorite = Boolean.TRUE.equals(favoriteRequest.getIsFavorite());
        int favoriteCount = pictureInteractionService.favorite(
                favoriteRequest.getPictureId(), loginUser.getId(), isFavorite);
        return ResultUtils.success(favoriteCount);
    }

    /**
     * 查询某张图片对当前用户的互动状态（未登录返回 false/false）
     */
    @GetMapping("/interaction/state")
    public BaseResponse<Map<String, Boolean>> queryInteractionState(@RequestParam long pictureId,
                                                                    HttpServletRequest request) {
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        Long userId = tryGetUserId(request);
        boolean[] state = pictureInteractionService.queryInteractionState(pictureId, userId);
        return ResultUtils.success(Map.of("isLiked", state[0], "isFavorited", state[1]));
    }

    /**
     * 我的收藏（按收藏时间倒序）
     */
    @GetMapping("/my/favorite")
    public BaseResponse<Page<PictureVO>> pageMyFavorites(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long pageSize,
            HttpServletRequest request) {
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureInteractionService.pageMyFavorites(
                loginUser.getId(), current, pageSize));
    }

    /**
     * 我的点赞（按点赞时间倒序）
     */
    @GetMapping("/my/like")
    public BaseResponse<Page<PictureVO>> pageMyLikes(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long pageSize,
            HttpServletRequest request) {
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureInteractionService.pageMyLikes(
                loginUser.getId(), current, pageSize));
    }

    // ============================ 评论 ============================

    /**
     * 发表评论（需登录）
     */
    @PostMapping("/comment/add")
    @RateLimit(count = 5, period = 60, ipCount = 60, dimension = RateLimit.Dimension.IP_AND_USER,
            message = "评论过于频繁，请稍后再试")
    @AuditLog(module = "picture", action = "comment_add", targetType = "picture",
            targetIdParam = "pictureCommentAddRequest", description = "发表图片评论")
    public BaseResponse<PictureCommentVO> addComment(@RequestBody PictureCommentAddRequest commentAddRequest,
                                                     HttpServletRequest request) {
        ThrowUtils.throwIf(commentAddRequest == null || commentAddRequest.getPictureId() == null,
                ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureInteractionService.addComment(
                commentAddRequest.getPictureId(), commentAddRequest.getContent(), loginUser));
    }

    /**
     * 分页查询某张图片的评论（无需登录，允许匿名浏览）
     */
    @GetMapping("/comment/list")
    public BaseResponse<Page<PictureCommentVO>> pageComments(
            @RequestParam long pictureId,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long pageSize,
            HttpServletRequest request) {
        ThrowUtils.throwIf(pictureId <= 0 || current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        // 匿名可看评论，但不能删除，故 loginUser 允许为 null
        return ResultUtils.success(pictureInteractionService.pageComments(
                pictureId, current, pageSize, tryGetLoginUser(request)));
    }

    /**
     * 删除评论（仅本人或管理员）
     */
    @PostMapping("/comment/delete")
    @AuditLog(module = "picture", action = "comment_delete", targetType = "comment",
            targetIdParam = "commentId", description = "删除图片评论")
    public BaseResponse<Boolean> deleteComment(@RequestBody Map<String, Long> body,
                                               HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.get("id") == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureInteractionService.deleteComment(body.get("id"), loginUser);
        return ResultUtils.success(true);
    }

    // ========================= 浏览历史 =========================

    /**
     * 记录一次浏览（需登录；同一张图只保留一条记录）
     */
    @PostMapping("/view/record")
    @RateLimit(count = 120, period = 60, dimension = RateLimit.Dimension.USER,
            message = "操作过于频繁")
    public BaseResponse<Boolean> recordView(@RequestBody Map<String, Long> body,
                                            HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.get("pictureId") == null, ErrorCode.PARAMS_ERROR);
        Long pictureId = body.get("pictureId");
        ThrowUtils.throwIf(pictureId <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        pictureInteractionService.recordView(pictureId, loginUser.getId());
        return ResultUtils.success(true);
    }

    /**
     * 我的最近查看（按查看时间倒序）
     */
    @GetMapping("/view/history")
    public BaseResponse<Page<PictureVO>> pageMyViewHistory(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long pageSize,
            HttpServletRequest request) {
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureInteractionService.pageMyViewHistory(
                loginUser.getId(), current, pageSize));
    }

    /**
     * 清空我的浏览历史
     */
    @PostMapping("/view/clear")
    @AuditLog(module = "picture", action = "view_history_clear", description = "清空浏览历史")
    public BaseResponse<Integer> clearMyViewHistory(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(pictureInteractionService.clearMyViewHistory(loginUser.getId()));
    }

    // ============================ 工具 ============================

    /**
     * 尽力获取当前登录用户，未登录返回 null（不抛异常）
     */
    private Long tryGetUserId(HttpServletRequest request) {
        User user = tryGetLoginUser(request);
        return user == null ? null : user.getId();
    }

    private User tryGetLoginUser(HttpServletRequest request) {
        try {
            return userService.getLoginUser(request);
        } catch (Exception e) {
            return null;
        }
    }
}