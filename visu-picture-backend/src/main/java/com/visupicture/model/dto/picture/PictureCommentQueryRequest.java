package com.visupicture.model.dto.picture;

import com.visupicture.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * 评论查询请求
 */
@EqualsAndHashCode(callSuper = true)
@Data
public class PictureCommentQueryRequest extends PageRequest implements Serializable {

    /**
     * 图片 id
     */
    private Long pictureId;

    /**
     * 用户 id（查某人发的评论）
     */
    private Long userId;

    private static final long serialVersionUID = 1L;
}