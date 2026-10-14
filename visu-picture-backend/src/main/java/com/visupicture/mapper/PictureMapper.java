package com.visupicture.mapper;

import com.visupicture.model.entity.Picture;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author 何佳鑫
* @description 针对表【picture(图片)】的数据库操作Mapper
*/
public interface PictureMapper extends BaseMapper<Picture> {

    /**
     * 回收站：当前用户已软删除的图片
     * <p>isDelete 带 @TableLogic，常规 Wrapper 查询会被自动追加 isDelete = 0 条件，
     * 因此回收站必须用自定义 SQL 绕过逻辑删除过滤。</p>
     */
    @Select("SELECT * FROM picture WHERE isDelete = 1 AND userId = #{userId} " +
            "ORDER BY updateTime DESC LIMIT #{offset}, #{pageSize}")
    List<Picture> listMyRecycled(@Param("userId") Long userId,
                                 @Param("offset") long offset,
                                 @Param("pageSize") long pageSize);

    @Select("SELECT COUNT(*) FROM picture WHERE isDelete = 1 AND userId = #{userId}")
    long countMyRecycled(@Param("userId") Long userId);

    /**
     * 取一条属于当前用户的已删除图片（恢复 / 彻底删除前的存在性校验）
     */
    @Select("SELECT * FROM picture WHERE id = #{id} AND userId = #{userId} AND isDelete = 1 LIMIT 1")
    Picture selectMyRecycledById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 恢复：软删除行回置为正常（仅限本人；空间额度由服务层归还）
     */
    @Select("UPDATE picture SET isDelete = 0 WHERE id = #{id} AND userId = #{userId} AND isDelete = 1")
    int restoreRecycled(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 彻底删除：物理删除行（仅限本人）；对象存储文件由服务层负责清理
     */
    @Select("DELETE FROM picture WHERE id = #{id} AND userId = #{userId} AND isDelete = 1")
    int deleteRecycledPhysically(@Param("id") Long id, @Param("userId") Long userId);
}
