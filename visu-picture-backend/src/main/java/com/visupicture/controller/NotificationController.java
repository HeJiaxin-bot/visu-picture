package com.visupicture.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.visupicture.annotation.AuditLog;
import com.visupicture.annotation.AuthCheck;
import com.visupicture.annotation.RateLimit;
import com.visupicture.common.BaseResponse;
import com.visupicture.common.ResultUtils;
import com.visupicture.constant.UserConstant;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.model.dto.notification.NotificationSendRequest;
import com.visupicture.model.entity.User;
import com.visupicture.model.enums.NotificationTypeEnum;
import com.visupicture.model.vo.NotificationVO;
import com.visupicture.service.NotificationService;
import com.visupicture.service.UserService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 站内通知接口
 */
@RestController
@RequestMapping("/notification")
public class NotificationController {

    @Resource
    private NotificationService notificationService;

    @Resource
    private UserService userService;

    /**
     * 消息中心支持的分类白名单：直接复用 NotificationTypeEnum 的值，
     * all/unread 为两个不对应存储类型的特殊值
     */
    private static final Set<String> VALID_CATEGORIES =
            Set.of("all", NotificationTypeEnum.INTERACT.getValue(),
                    NotificationTypeEnum.REVIEW.getValue(), NotificationTypeEnum.SYSTEM.getValue(),
                    "unread");

    /**
     * 分页获取我的通知（onlyUnread=true 时只看未读）
     */
    @GetMapping("/list")
    public BaseResponse<List<NotificationVO>> listMyNotifications(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(defaultValue = "false") boolean onlyUnread,
            HttpServletRequest request) {
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        List<NotificationVO> list = notificationService.listMyNotifications(
                loginUser.getId(), current, pageSize, onlyUnread);
        return ResultUtils.success(list);
    }

    /**
     * 分页获取我的通知（带总数，供消息中心分页器使用）
     *
     * 分类参数直接复用 NotificationTypeEnum 的值（all/unread 为两个特殊值）：
     * all-全部 / interact-互动(评论回复) / review-审核通知 / system-系统通知 / unread-未读
     *
     * @param keyword 搜索关键词，匹配标题或内容，可空
     */
    @GetMapping("/list/page")
    public BaseResponse<Page<NotificationVO>> pageMyNotifications(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(!VALID_CATEGORIES.contains(category),
                ErrorCode.PARAMS_ERROR, "不支持的消息分类");
        User loginUser = userService.getLoginUser(request);
        Page<NotificationVO> page = notificationService.pageMyNotifications(
                loginUser.getId(), current, pageSize, category, keyword);
        return ResultUtils.success(page);
    }

    /**
     * 我的未读通知数量（导航栏角标轮询用）
     */
    @GetMapping("/unread/count")
    public BaseResponse<Long> countMyUnread(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(notificationService.countMyUnread(loginUser.getId()));
    }

    /**
     * 标记单条通知为已读
     */
    @PostMapping("/read")
    public BaseResponse<Boolean> markRead(@RequestBody Map<String, Long> body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.get("id") == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        boolean result = notificationService.markRead(loginUser.getId(), body.get("id"));
        return ResultUtils.success(result);
    }

    /**
     * 批量标记已读（选择模式下勾选的通知）
     */
    @PostMapping("/batch/read")
    public BaseResponse<Integer> markReadBatch(@RequestBody List<Long> ids, HttpServletRequest request) {
        ThrowUtils.throwIf(ids == null || ids.isEmpty(), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(notificationService.markReadBatch(loginUser.getId(), ids));
    }

    /**
     * 批量删除通知（选择模式下勾选的通知）
     */
    @PostMapping("/batch/delete")
    public BaseResponse<Integer> deleteBatch(@RequestBody List<Long> ids, HttpServletRequest request) {
        ThrowUtils.throwIf(ids == null || ids.isEmpty(), ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(notificationService.deleteBatch(loginUser.getId(), ids));
    }

    /**
     * 删除我的通知（仅本人通知，像删除手机短信一样从列表移除）
     */
    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteNotification(@RequestBody Map<String, Long> body, HttpServletRequest request) {
        ThrowUtils.throwIf(body == null || body.get("id") == null, ErrorCode.PARAMS_ERROR);
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(notificationService.deleteMyNotification(loginUser.getId(), body.get("id")));
    }

    /**
     * 全部标记已读
     */
    @PostMapping("/read/all")
    public BaseResponse<Boolean> markAllRead(HttpServletRequest request) {
        User loginUser = userService.getLoginUser(request);
        return ResultUtils.success(notificationService.markAllRead(loginUser.getId()));
    }

    /**
     * 管理员发布系统消息（全员或指定用户）
     */
    @PostMapping("/send")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    @RateLimit(count = 10, period = 60, dimension = RateLimit.Dimension.USER,
            message = "发布消息过于频繁，请稍后再试")
    @AuditLog(module = "notification", action = "send", targetType = "user",
            targetIdParam = "sendRequest", description = "管理员发布系统消息")
    public BaseResponse<Integer> sendSystemNotification(@RequestBody NotificationSendRequest sendRequest) {
        ThrowUtils.throwIf(sendRequest == null, ErrorCode.PARAMS_ERROR);
        int receiverCount = notificationService.sendSystemNotification(
                sendRequest.getTitle(), sendRequest.getContent(), sendRequest.getTargetUserId());
        return ResultUtils.success(receiverCount);
    }
}
