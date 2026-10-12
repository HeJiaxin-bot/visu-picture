package com.visupicture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visupicture.model.entity.PictureFavorite;
import org.apache.ibatis.annotations.Param;

/**
 * 图片收藏记录数据库操作
 */
public interface PictureFavoriteMapper extends BaseMapper<PictureFavorite> {

    /**
     * 幂等插入收藏记录
     *
     * <p><b>SQL 位置</b>：语句定义在 {@code resources/mapper/PictureFavoriteMapper.xml}。
     * 此处刻意不使用 @Insert 注解 —— 注解与 XML 同名时 MyBatis 以 XML 为准并输出
     * {@code ... is ignored, because it exists, maybe from xml file} 的 ERROR 日志，
     * 使注解沦为死代码。全项目约定：自定义 SQL 一律写在 XML 中。</p>
     *
     * @param pictureId 图片 id
     * @param userId    用户 id
     * @return 实际插入的行数（1-新增成功；0-已收藏，本次为重复操作）
     */
    int insertIgnore(@Param("pictureId") long pictureId, @Param("userId") long userId);
}