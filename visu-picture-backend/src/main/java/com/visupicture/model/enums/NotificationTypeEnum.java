package com.visupicture.model.enums;

import cn.hutool.core.util.ObjUtil;
import lombok.Getter;

/**
 * 通知类型枚举
 */
@Getter
public enum NotificationTypeEnum {

    REVIEW("审核结果", "review"),
    SYSTEM("系统公告", "system"),
    INTERACT("互动消息", "interact");

    private final String text;

    private final String value;

    NotificationTypeEnum(String text, String value) {
        this.text = text;
        this.value = value;
    }

    /**
     * 根据 value 获取枚举
     */
    public static NotificationTypeEnum getEnumByValue(String value) {
        if (ObjUtil.isEmpty(value)) {
            return null;
        }
        for (NotificationTypeEnum typeEnum : NotificationTypeEnum.values()) {
            if (typeEnum.value.equals(value)) {
                return typeEnum;
            }
        }
        return null;
    }
}
