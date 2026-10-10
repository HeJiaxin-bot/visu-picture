package com.visupicture.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新空间请求
 *
 * ⚠️ 安全约定：本类字段直接来自前端，禁止包含任何「用户不应自行决定」的字段。
 * 特别地 maxSize / maxCount（配额）必须由空间等级（spaceLevel）在服务端推导，
 * 否则普通用户提交天文数字即可绕过付费等级限制。
 * 历史漏洞：本类曾包含 maxSize / maxCount，且 fillSpaceBySpaceLevel 仅在 null 时才覆盖，
 * 导致用户可自行把普通版空间改成无限容量。
 */
@Data
public class SpaceUpdateRequest implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 空间名称
     */
    private String spaceName;

    /**
     * 空间级别：0-普通版 1-专业版 2-旗舰版
     * （配额 maxSize / maxCount 由该等级在服务端推导，不由前端传入）
     */
    private Integer spaceLevel;

    private static final long serialVersionUID = 1L;
}
