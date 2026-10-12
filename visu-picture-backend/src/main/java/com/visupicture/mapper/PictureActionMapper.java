package com.visupicture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visupicture.model.entity.PictureAction;
import org.apache.ibatis.annotations.Param;

/**
 * 图片互动记录（点赞/收藏）数据库操作
 */
public interface PictureActionMapper extends BaseMapper<PictureAction> {

    /**
     * 幂等插入互动记录
     *
     * <p>依赖唯一索引 uk_pic_user_type + INSERT IGNORE：
     * 已存在时插入被忽略并返回 0，首次插入返回 1。
     * 调用方据此决定是否对 picture.likeCount / favoriteCount 计数，
     * 从根本上杜绝重复点赞/重复收藏把计数刷高。</p>
     *
     * <p><b>SQL 位置</b>：{@code resources/mapper/PictureActionMapper.xml}。
     * 此处刻意不使用 @Insert 注解 —— 注解与 XML 同名时 MyBatis 以 XML 为准
     * 并输出 {@code is ignored, because it exists, maybe from xml file} 的 ERROR 日志，
     * 使注解沦为死代码。全项目约定：自定义 SQL 一律写在 XML 中。</p>
     *
     * @param pictureId  图片 id
     * @param userId     用户 id
     * @param actionType 动作类型：1-点赞 2-收藏
     * @return 实际插入的行数（1-新增成功；0-已存在，本次为重复操作）
     */
    int insertIgnore(@Param("pictureId") long pictureId,
                     @Param("userId") long userId,
                     @Param("actionType") int actionType);
}