package com.visupicture.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 排序字段白名单
 *
 * <p>⚠️ 安全说明：MyBatis-Plus 的 {@code QueryWrapper.orderBy(boolean, boolean, String)}
 * 第三参是「列名」，会被字面量拼接进 SQL，不做参数化、不做转义。
 * 因此该参数绝不可直接来自前端 —— 否则形成 SQL 注入（可盲注外带全库数据）。
 * 所有排序字段必须先经此处白名单校验。</p>
 */
public final class SortFieldConstant {

    private SortFieldConstant() {
    }

    /**
     * 图片表可排序字段
     */
    public static final Set<String> PICTURE_SORT_FIELDS = unmodifiableSet(
            "id", "name", "introduction", "category", "picSize", "picWidth", "picHeight",
            "picScale", "picFormat", "userId", "spaceId", "reviewStatus", "reviewTime",
            "createTime", "editTime", "updateTime", "likeCount"
    );

    /**
     * 空间表可排序字段
     */
    public static final Set<String> SPACE_SORT_FIELDS = unmodifiableSet(
            "id", "spaceName", "spaceLevel", "spaceType", "userId",
            "maxSize", "maxCount", "totalSize", "totalCount", "createTime", "editTime", "updateTime"
    );

    /**
     * 用户表可排序字段
     */
    public static final Set<String> USER_SORT_FIELDS = unmodifiableSet(
            "id", "userAccount", "userName", "userRole", "isVip", "points",
            "vipExpireTime", "createTime", "editTime", "updateTime"
    );

    /**
     * 校验排序字段是否合法
     *
     * @param sortField 前端传入的排序字段
     * @param allowed   允许的字段集合
     * @return 是否合法（空值视为合法，表示不排序）
     */
    public static boolean isValid(String sortField, Set<String> allowed) {
        if (sortField == null || sortField.isEmpty()) {
            return true;
        }
        return allowed.contains(sortField);
    }

    /**
     * 构造不可变集合（Java 8 兼容写法，避免 Set.of 的元素数量限制与 IDE 误报）
     */
    private static Set<String> unmodifiableSet(String... fields) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(fields)));
    }
}
