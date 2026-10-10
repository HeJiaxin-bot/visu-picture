package com.visupicture.manager.auth;

import lombok.Data;

/**
 * 空间鉴权上下文：仅承载「来自请求的不可信输入」（各种 id）。
 *
 * ⚠️ 安全约定：本类字段全部来自请求体/参数，属于攻击者可控数据。
 * 因此只允许放 id 类字段 —— 任何实体对象（Picture / Space / SpaceUser）
 * 都必须由服务端按 id 查库后临时持有，绝不可作为本类字段由请求体反序列化，
 * 否则攻击者可自行填充 spaceUser.spaceRole 等字段伪造身份绕过鉴权。
 *
 * 历史漏洞：本类曾包含 picture / space / spaceUser 三个实体字段，
 * 攻击者提交 {"spaceUser":{"spaceRole":"admin"}} 即可获得全部空间权限。
 */
@Data
public class SpaceUserAuthContext {

    /**
     * 临时参数，不同请求对应的 id 可能不同
     * （由请求路径前缀决定语义：picture -> pictureId，spaceUser -> spaceUserId，space -> spaceId）
     */
    private Long id;

    /**
     * 图片 ID
     */
    private Long pictureId;

    /**
     * 空间 ID
     */
    private Long spaceId;

    /**
     * 空间用户 ID
     */
    private Long spaceUserId;
}
