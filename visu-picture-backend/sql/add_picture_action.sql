-- ==============================
-- 合并 picture_like + picture_favorite -> picture_action
-- 执行方式：mysql -uroot -p1234 visu < add_picture_action.sql
--
-- 背景：两张表结构与查询模式完全同构（id/pictureId/userId/createTime，
--       唯一索引 uk(pictureId,userId)），代码里 6 处用法一一对应，
--       合并为一张带 actionType 的表即可，去重能力不变。
-- 幂等：可重复执行；若旧表有数据会自动迁移，迁移后可选删除旧表。
-- ==============================

use visu;

-- ---------------------------------------------------------------
-- 1. 新建合并表
--    actionType：1-点赞 2-收藏
--    唯一索引加上 actionType 后，「同一用户对同一图片的同一动作」仍只能有一条，
--    防重复计数能力与原来完全一致。
-- ---------------------------------------------------------------
create table if not exists picture_action
(
    id         bigint auto_increment comment 'id' primary key,
    pictureId  bigint                             not null comment '图片 id',
    userId     bigint                             not null comment '用户 id',
    actionType tinyint                            not null comment '动作类型：1-点赞 2-收藏',
    createTime datetime default CURRENT_TIMESTAMP not null comment '操作时间',
    UNIQUE KEY uk_pic_user_type (pictureId, userId, actionType),
    INDEX idx_user_type (userId, actionType),
    INDEX idx_pictureId (pictureId)
) comment '图片互动记录（点赞/收藏）' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 2. 迁移旧表数据（旧表存在才迁；INSERT IGNORE 保证重复执行不报错）
--    用动态 SQL：旧表已被删除时跳过，脚本仍可安全重跑。
-- ---------------------------------------------------------------
drop procedure if exists migrate_legacy_action;
delimiter $$
create procedure migrate_legacy_action()
begin
    -- 迁移点赞（actionType=1）
    if exists(select 1 from information_schema.TABLES
              where TABLE_SCHEMA = database() and TABLE_NAME = 'picture_like') then
        set @s = 'insert ignore into picture_action (pictureId, userId, actionType, createTime)
                  select pictureId, userId, 1, createTime from picture_like';
        prepare stmt from @s; execute stmt; deallocate prepare stmt;
    end if;

    -- 迁移收藏（actionType=2）
    if exists(select 1 from information_schema.TABLES
              where TABLE_SCHEMA = database() and TABLE_NAME = 'picture_favorite') then
        set @s = 'insert ignore into picture_action (pictureId, userId, actionType, createTime)
                  select pictureId, userId, 2, createTime from picture_favorite';
        prepare stmt from @s; execute stmt; deallocate prepare stmt;
    end if;
end $$
delimiter ;

call migrate_legacy_action();
drop procedure if exists migrate_legacy_action;

-- ---------------------------------------------------------------
-- 3. 自检：对比迁移前后条数，确认无遗漏
-- ---------------------------------------------------------------
select 'picture_action 总条数' as metric, count(*) as value from picture_action
union all
select '  其中点赞(actionType=1)', count(*) from picture_action where actionType = 1
union all
select '  其中收藏(actionType=2)', count(*) from picture_action where actionType = 2;

-- ---------------------------------------------------------------
-- 4. 校验唯一索引生效（下面这句预期报错 1062，说明防重有效；
--    若想验证请单独执行，验证后删除测试行）
-- ---------------------------------------------------------------
-- insert into picture_action (pictureId, userId, actionType) values (1, 1, 1);
-- insert into picture_action (pictureId, userId, actionType) values (1, 1, 1);  -- 应报 Duplicate entry

-- ---------------------------------------------------------------
-- 5. 确认迁移无误后，手动删除旧表（默认注释掉，请核对上面自检数字后再执行）
-- ---------------------------------------------------------------
-- drop table if exists picture_like;
-- drop table if exists picture_favorite;
