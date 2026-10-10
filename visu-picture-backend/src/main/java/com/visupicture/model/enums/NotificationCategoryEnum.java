package com.visupicture.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 消息中心分类枚举（前端标签页）
 */
@Getter
public enum NotificationCategoryEnum {

    ALL("全部", "all"),
    REVIEW("审核通知", "review"),
    SYSTEM("系统通知", "system"),
    UNREAD("未读", "unread");

    private final String text;

    private final String value;

    NotificationCategoryEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static NotificationCategoryEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (NotificationCategoryEnum categoryEnum : NotificationCategoryEnum.values()) {
            if (categoryEnum.value.equals(value)) {
                return categoryEnum;
            }
        }
        return null;
    }
}
