package com.visupicture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visupicture.model.entity.PictureLike;
import org.apache.ibatis.annotations.Param;

/**
 * 图片点赞记录数据库操作
 */
public interface PictureLikeMapper extends BaseMapper<PictureLike> {

    /**
     * 幂等插入点赞记录
     * <p>
     * 依赖唯一索引 uk_pictureId_userId + INSERT IGNORE：
     * 已点赞时插入被忽略并返回 0，未点赞时返回 1。
     * 调用方据此决定是否对 picture.likeCount 计数，从根本上杜绝重复点赞刷高计数。
     *
     * <p><b>SQL 位置</b>：语句定义在 {@code resources/mapper/PictureLikeMapper.xml}。
     * 此处刻意<b>不使用 @Insert 注解</b> —— 若注解与 XML 同名，MyBatis 会以 XML 为准
     * 并输出 {@code ... is ignored, because it exists, maybe from xml file} 的 ERROR 日志，
     * 导致注解变成永不执行的死代码（改注解不生效且无报错，极难排查）。
     * 全项目统一约定：自定义 SQL 一律写在 XML 中。</p>
     *
     * @param pictureId 图片 id
     * @param userId    用户 id
     * @return 实际插入的行数（1-新增成功；0-已存在，本次为重复点赞）
     */
    int insertIgnore(@Param("pictureId") long pictureId, @Param("userId") long userId);
}