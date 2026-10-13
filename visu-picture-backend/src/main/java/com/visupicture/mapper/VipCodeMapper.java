package com.visupicture.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.visupicture.model.entity.VipCode;
import org.apache.ibatis.annotations.Param;

/**
 * 会员兑换码数据库操作
 */
public interface VipCodeMapper extends BaseMapper<VipCode> {

    /**
     * 原子占用兑换码
     *
     * <p><b>为什么是一条 UPDATE 而不是"先查后改"</b>：
     * {@code WHERE usedBy IS NULL} 与 {@code SET usedBy = ...} 在同一条语句内完成，
     * InnoDB 会对命中行加排他锁，因此并发提交同一个码时<b>只有一条</b>能返回 1，
     * 其余返回 0。这样既不需要应用层锁，也不受部署实例数影响
     * （原先的 {@code ReentrantLock} 是单机锁，多实例部署下防不住重复兑换）。</p>
     *
     * <p><b>SQL 位置</b>：{@code resources/mapper/VipCodeMapper.xml}。
     * 此处刻意不使用 @Update 注解 —— 注解与 XML 同名时 MyBatis 以 XML 为准
     * 并输出 {@code is ignored, because it exists, maybe from xml file} 的 ERROR 日志，
     * 使注解沦为死代码。全项目约定：自定义 SQL 一律写在 XML 中。</p>
     *
     * @param code   兑换码
     * @param userId 使用人用户 id
     * @return 1-占用成功；0-码不存在或已被占用
     */
    int claim(@Param("code") String code, @Param("userId") long userId);
}
