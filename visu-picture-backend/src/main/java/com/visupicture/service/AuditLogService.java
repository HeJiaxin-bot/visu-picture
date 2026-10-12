package com.visupicture.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.visupicture.model.entity.AuditLog;

import java.util.Date;

/**
 * 操作审计日志服务
 */
public interface AuditLogService extends IService<AuditLog> {

    /**
     * 静默记录一条审计日志
     *
     * <p>「静默」意味着：内部吞掉所有异常，只打日志，绝不向调用方抛出。
     * 审计是旁路能力，任何审计故障都不应导致业务失败。</p>
     *
     * @param userId       操作人 id（未登录为 null）
     * @param userAccount  操作人账号快照
     * @param module       业务模块
     * @param action       操作类型
     * @param targetType   目标类型（可为 null）
     * @param targetId     目标 id（可为 null）
     * @param detail       操作详情
     * @param ip           客户端 IP
     * @param userAgent    User-Agent
     * @param success      是否成功
     * @param errorMessage 失败原因（成功时为 null）
     * @param costMs       耗时毫秒
     */
    void recordQuietly(Long userId, String userAccount, String module, String action,
                       String targetType, Long targetId, String detail,
                       String ip, String userAgent, boolean success,
                       String errorMessage, Long costMs);

    /**
     * 分页查询审计日志（仅管理员）
     *
     * @param module    模块筛选（可空）
     * @param action    操作筛选（可空）
     * @param userId    操作人筛选（可空）
     * @param startTime 起始时间（可空）
     * @param endTime   结束时间（可空）
     * @param current   页码
     * @param pageSize  每页条数
     */
    Page<AuditLog> pageAuditLogs(String module, String action, Long userId,
                                 Date startTime, Date endTime, long current, long pageSize);
}