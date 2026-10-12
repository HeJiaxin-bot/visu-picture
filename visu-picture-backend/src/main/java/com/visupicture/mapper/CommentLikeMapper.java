package com.visupicture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visupicture.model.entity.CommentLike;
import org.apache.ibatis.annotations.Param;

/**
 * 评论点赞记录数据库操作
 */
public interface CommentLikeMapper extends BaseMapper<CommentLike> {

    /**
     * 幂等插入评论点赞记录
     *
     * <p><b>SQL 位置</b>：{@code resources/mapper/CommentLikeMapper.xml}（全项目约定：
     * 自定义 SQL 一律写在 XML，注解与 XML 同名时注解会被静默忽略）。</p>
     *
     * @param commentId 评论 id
     * @param userId    用户 id
     * @return 实际插入的行数（1-首次点赞；0-已赞过，本次为重复操作）
     */
    int insertIgnore(@Param("commentId") long commentId, @Param("userId") long userId);
}