package com.visupicture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visupicture.model.entity.PictureViewHistory;
import org.apache.ibatis.annotations.Param;

/**
 * 图片浏览历史数据库操作
 */
public interface PictureViewHistoryMapper extends BaseMapper<PictureViewHistory> {

    /**
     * 记录一次浏览：同一用户同一图片只保留一条，重复查看刷新时间并累加次数
     * <p>
     * 依赖唯一索引 uk_userId_pictureId + ON DUPLICATE KEY UPDATE，
     * 单条 SQL 完成「有则更新、无则插入」，无需先查询再判断。
     *
     * <p><b>SQL 位置</b>：语句定义在 {@code resources/mapper/PictureViewHistoryMapper.xml}。
     * 此处刻意不使用 @Insert 注解 —— 注解与 XML 同名时 MyBatis 以 XML 为准并输出
     * {@code ... is ignored, because it exists, maybe from xml file} 的 ERROR 日志，
     * 使注解沦为死代码。全项目约定：自定义 SQL 一律写在 XML 中。</p>
     *
     * @param userId    用户 id
     * @param pictureId 图片 id
     * @return 受影响行数（插入为 1，更新为 2，值未变化为 0）
     */
    int recordView(@Param("userId") long userId, @Param("pictureId") long pictureId);
}