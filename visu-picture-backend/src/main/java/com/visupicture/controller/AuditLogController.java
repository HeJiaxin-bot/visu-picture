package com.visupicture.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.visupicture.annotation.AuthCheck;
import com.visupicture.common.BaseResponse;
import com.visupicture.common.ResultUtils;
import com.visupicture.constant.UserConstant;
import com.visupicture.exception.ErrorCode;
import com.visupicture.exception.ThrowUtils;
import com.visupicture.model.entity.AuditLog;
import com.visupicture.service.AuditLogService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Date;

/**
 * 操作审计日志查询接口（仅管理员）
 */
@RestController
@RequestMapping("/audit")
public class AuditLogController {

    @Resource
    private AuditLogService auditLogService;

    /**
     * 分页查询操作审计日志
     *
     * <p>审计日志本身是高度敏感数据（包含全站操作轨迹），因此：</p>
     * <ul>
     *   <li>仅管理员可访问（{@code @AuthCheck}）；</li>
     *   <li>该查询<b>不</b>再打审计日志 —— 否则管理员查看审计会产生新的审计记录，
     *       形成自引用噪音。</li>
     * </ul>
     *
     * @param module    模块筛选（可空）
     * @param action    操作筛选（可空）
     * @param userId    操作人筛选（可空）
     * @param startTime 起始时间（可空，格式 yyyy-MM-dd HH:mm:ss）
     * @param endTime   结束时间（可空）
     */
    @GetMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<AuditLog>> pageAuditLogs(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long pageSize) {
        ThrowUtils.throwIf(current <= 0 || pageSize <= 0, ErrorCode.PARAMS_ERROR);
        return ResultUtils.success(auditLogService.pageAuditLogs(
                module, action, userId, startTime, endTime, current, pageSize));
    }
}