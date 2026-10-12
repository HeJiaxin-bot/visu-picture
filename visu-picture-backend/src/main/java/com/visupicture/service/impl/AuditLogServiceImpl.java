package com.visupicture.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.visupicture.mapper.AuditLogMapper;
import com.visupicture.model.entity.AuditLog;
import com.visupicture.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Date;

/**
 * 操作审计日志服务实现
 */
@Slf4j
@Service
public class AuditLogServiceImpl extends ServiceImpl<AuditLogMapper, AuditLog>
        implements AuditLogService {

    /**
     * 各文本列的物理长度上限，落库前统一截断，避免超长导致整条 INSERT 失败
     */
    private static final int LEN_MODULE = 64;
    private static final int LEN_ACTION = 64;
    private static final int LEN_TARGET_TYPE = 64;
    private static final int LEN_DETAIL = 1024;
    private static final int LEN_IP = 64;
    private static final int LEN_UA = 512;
    private static final int LEN_ERROR = 512;
    private static final int LEN_ACCOUNT = 256;

    @Override
    public void recordQuietly(Long userId, String userAccount, String module, String action,
                              String targetType, Long targetId, String detail,
                              String ip, String userAgent, boolean success,
                              String errorMessage, Long costMs) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(userId);
            auditLog.setUserAccount(StrUtil.maxLength(userAccount, LEN_ACCOUNT));
            auditLog.setModule(StrUtil.maxLength(module, LEN_MODULE));
            auditLog.setAction(StrUtil.maxLength(action, LEN_ACTION));
            auditLog.setTargetType(StrUtil.maxLength(targetType, LEN_TARGET_TYPE));
            auditLog.setTargetId(targetId);
            auditLog.setDetail(StrUtil.maxLength(detail, LEN_DETAIL));
            auditLog.setIp(StrUtil.maxLength(ip, LEN_IP));
            auditLog.setUserAgent(StrUtil.maxLength(userAgent, LEN_UA));
            auditLog.setSuccess(success ? 1 : 0);
            auditLog.setErrorMessage(StrUtil.maxLength(errorMessage, LEN_ERROR));
            auditLog.setCostMs(costMs);
            this.save(auditLog);
        } catch (Exception e) {
            // 审计是旁路能力：任何异常都不得向上传播影响业务
            log.error("写入操作审计日志失败, module={}, action={}, targetId={}", module, action, targetId, e);
        }
    }

    @Override
    public Page<AuditLog> pageAuditLogs(String module, String action, Long userId,
                                        Date startTime, Date endTime, long current, long pageSize) {
        long page = Math.max(current, 1);
        // 上限 100：审计表随时间无限增长，需限制单次扫描量
        long size = Math.min(Math.max(pageSize, 1), 100);
        QueryWrapper<AuditLog> wrapper = new QueryWrapper<>();
        wrapper.eq(StrUtil.isNotBlank(module), "module", module);
        wrapper.eq(StrUtil.isNotBlank(action), "action", action);
        wrapper.eq(userId != null && userId > 0, "userId", userId);
        wrapper.ge(startTime != null, "createTime", startTime);
        wrapper.le(endTime != null, "createTime", endTime);
        wrapper.orderByDesc("createTime").orderByDesc("id");
        return this.page(new Page<>(page, size), wrapper);
    }
}