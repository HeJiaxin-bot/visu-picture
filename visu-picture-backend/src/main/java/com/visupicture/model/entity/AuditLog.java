package com.visupicture.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 操作审计日志
 *
 * @TableName audit_log
 */
@TableName(value = "audit_log")
@Data
public class AuditLog implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作人 id（未登录为空）
     */
    private Long userId;

    /**
     * 操作人账号快照（用户被删除后仍可追溯）
     */
    private String userAccount;

    /**
     * 业务模块：picture/user/space/notification...
     */
    private String module;

    /**
     * 操作类型：delete/update/review...
     */
    private String action;

    /**
     * 目标类型
     */
    private String targetType;

    /**
     * 目标 id
     */
    private Long targetId;

    /**
     * 操作详情（JSON 或描述）
     */
    private String detail;

    /**
     * 客户端 IP
     */
    private String ip;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 是否成功：0-失败 1-成功
     */
    private Integer success;

    /**
     * 失败原因
     */
    private String errorMessage;

    /**
     * 耗时（毫秒）
     */
    private Long costMs;

    /**
     * 操作时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;
}