-- ==============================
-- 将 picture_share 并入 picture
-- 执行方式：mysql -uroot -p1234 visu < merge_picture_share.sql
--
-- 背景：一张图片当前最多只有 1 条有效分享链接（createShare 会复用已有链接，
--       实测库中从无一张图存在多条有效链接），因此分享数据本质是 1:1，
--       可并入主表，省掉一张表与一套 Mapper/Service。
--
-- 取舍说明（并入后失去的能力）：
--   原表用「逻辑删除」保留历史链接痕迹（撤销后 isDelete=1 仍可追溯）。
--   并入主表后撤销即 shareCode = NULL，历史分享记录不再留存。
--   经确认：系统没有"分享历史"页面（listMyShares 接口无任何 UI 调用），
--   故该损失可接受。
--
-- 幂等：可重复执行；旧表存在时自动迁移未过期的有效链接。
-- ==============================

use visu;

-- ---------------------------------------------------------------
-- 1. picture 表增分享字段
--    分享码仍需唯一索引：它是对外暴露的随机标识，必须保证不可枚举、不重复
-- ---------------------------------------------------------------
drop procedure if exists add_column_if_absent;
delimiter $$
create procedure add_column_if_absent(in tbl varchar(64), in col varchar(64), in ddl varchar(512))
begin
    if not exists (select 1 from information_schema.COLUMNS
                   where TABLE_SCHEMA = database() and TABLE_NAME = tbl and COLUMN_NAME = col) then
        set @s = concat('alter table `', tbl, '` add column ', ddl);
        prepare stmt from @s;
        execute stmt;
        deallocate prepare stmt;
    end if;
end $$
delimiter ;

call add_column_if_absent('picture', 'shareCode',
  '`shareCode` varchar(32) null comment ''分享码（NULL=未分享，非空即可公开访问）'' after `favoriteCount`');
call add_column_if_absent('picture', 'shareExpireTime',
  '`shareExpireTime` datetime null comment ''分享过期时间（NULL=永久有效）'' after `shareCode`');
call add_column_if_absent('picture', 'shareViewCount',
  '`shareViewCount` int default 0 not null comment ''分享链接访问次数'' after `shareExpireTime`');
call add_column_if_absent('picture', 'shareCreateTime',
  '`shareCreateTime` datetime null comment ''分享创建时间'' after `shareViewCount`');

drop procedure if exists add_column_if_absent;

-- 分享码唯一索引（允许多个 NULL：MySQL 唯一索引对 NULL 不去重，正好表达"未分享"）
drop procedure if exists add_index_if_absent;
delimiter $$
create procedure add_index_if_absent(in tbl varchar(64), in idx varchar(64), in cols varchar(255), in uniq varchar(10))
begin
    if not exists (select 1 from information_schema.STATISTICS
                   where TABLE_SCHEMA = database() and TABLE_NAME = tbl and INDEX_NAME = idx) then
        set @s = concat('alter table `', tbl, '` add ', uniq, ' index `', idx, '` (', cols, ')');
        prepare stmt from @s;
        execute stmt;
        deallocate prepare stmt;
    end if;
end $$
delimiter ;

call add_index_if_absent('picture', 'uk_shareCode', '`shareCode`', 'unique');

drop procedure if exists add_index_if_absent;

-- ---------------------------------------------------------------
-- 2. 迁移旧表数据：只迁未撤销且未过期的有效链接
--    按 pictureId 对齐；一张图理论上只会有 1 条有效链接
-- ---------------------------------------------------------------
drop procedure if exists migrate_share_into_picture;
delimiter $$
create procedure migrate_share_into_picture()
begin
    if exists(select 1 from information_schema.TABLES
              where TABLE_SCHEMA = database() and TABLE_NAME = 'picture_share') then
        -- 用子查询取每张图最新的一条有效链接
        set @s = '
            update picture p
            join (
                select s.pictureId,
                       substring(max(concat(lpad(s.id, 20, ''0''), s.shareCode)), 21) as shareCode,
                       max(s.expireTime) as expireTime,
                       max(s.viewCount) as viewCount,
                       max(s.createTime) as createTime
                from picture_share s
                where s.isDelete = 0
                  and (s.expireTime is null or s.expireTime > now())
                group by s.pictureId
            ) t on t.pictureId = p.id
            set p.shareCode = t.shareCode,
                p.shareExpireTime = t.expireTime,
                p.shareViewCount = coalesce(t.viewCount, 0),
                p.shareCreateTime = t.createTime
            where p.shareCode is null';
        prepare stmt from @s; execute stmt; deallocate prepare stmt;
    end if;
end $$
delimiter ;

call migrate_share_into_picture();
drop procedure if exists migrate_share_into_picture;

-- ---------------------------------------------------------------
-- 3. 自检
-- ---------------------------------------------------------------
select 'picture 中有分享码的图片数' as metric, count(*) as value from picture where shareCode is not null
union all
select '  其中永久有效(expireTime IS NULL)', count(*) from picture where shareCode is not null and shareExpireTime is null
union all
select '  其中已过期', count(*) from picture where shareCode is not null and shareExpireTime is not null and shareExpireTime <= now();

-- ---------------------------------------------------------------
-- 4. 确认迁移无误后手动删除旧表（默认注释，核对上面数字后再执行）
-- ---------------------------------------------------------------
-- drop table if exists picture_share;
