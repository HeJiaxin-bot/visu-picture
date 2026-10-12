package com.visupicture.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 图片互动记录（点赞 / 收藏）
 * <p>
 * 原 picture_like 与 picture_favorite 两张表结构、索引、查询模式完全同构，
 * 合并为一张表用 {@link ActionType} 区分动作，去重能力不变
 *（唯一索引含 actionType，同一用户对同一图片的同一动作仍只能有一条）。
 *
 * @TableName picture_action
 */
@TableName(value = "picture_action")
@Data
public class PictureAction implements Serializable {

    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 用户 id
     */
    private Long userId;

    /**
     * 动作类型：1-点赞 2-收藏
     */
    private Integer actionType;

    /**
     * 操作时间
     */
    private Date createTime;

    private static final long serialVersionUID = 1L;

    /**
     * 动作类型常量
     */
    public static final class ActionType {
        private ActionType() {
        }

        /** 点赞 */
        public static final int LIKE = 1;
        /** 收藏 */
        public static final int FAVORITE = 2;
    }
}