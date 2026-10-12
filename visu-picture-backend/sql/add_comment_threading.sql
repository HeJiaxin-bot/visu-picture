-- ==============================
-- 评论楼中楼：picture_comment 增线程字段
-- 执行方式：mysql -uroot -p1234 visu < add_comment_threading.sql
-- 模型（B站/网易云式两级展示）：
--   顶层评论：parentId / rootId 均为 NULL，参与分页与排序
--   回复：    parentId=被回复评论id，rootId=所属顶层评论id，
--             全部平铺展示在顶层评论下（回复回复时不加深度）
-- ==============================

use visu;

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

call add_column_if_absent('picture_comment', 'parentId',
  '`parentId` bigint null comment ''父评论 id（回复时指向被回复的评论，顶层为 NULL）'' after `userId`');
call add_column_if_absent('picture_comment', 'rootId',
  '`rootId` bigint null comment ''线程根评论 id（回复所属的顶层评论，顶层为 NULL）'' after `parentId`');
call add_column_if_absent('picture_comment', 'replyToUserId',
  '`replyToUserId` bigint null comment ''被回复用户 id'' after `rootId`');
call add_column_if_absent('picture_comment', 'replyToUserName',
  '`replyToUserName` varchar(256) null comment ''被回复用户昵称快照（用户改名后仍可显示）'' after `replyToUserId`');
call add_column_if_absent('picture_comment', 'location',
  '`location` varchar(64) null comment ''评论者 IP 属地（省份，预留字段）'' after `replyToUserName`');

drop procedure if exists add_column_if_absent;

-- 按线程根取回复是最高频查询
drop procedure if exists add_index_if_absent;
delimiter $$
create procedure add_index_if_absent(in tbl varchar(64), in idx varchar(64), in cols varchar(255))
begin
    if not exists (select 1 from information_schema.STATISTICS
                   where TABLE_SCHEMA = database() and TABLE_NAME = tbl and INDEX_NAME = idx) then
        set @s = concat('alter table `', tbl, '` add index `', idx, '` (', cols, ')');
        prepare stmt from @s;
        execute stmt;
        deallocate prepare stmt;
    end if;
end $$
delimiter ;

call add_index_if_absent('picture_comment', 'idx_rootId', '`rootId`');

drop procedure if exists add_index_if_absent;

select column_name, column_type from information_schema.COLUMNS
where TABLE_SCHEMA = database() and TABLE_NAME = 'picture_comment'
order by ORDINAL_POSITION;
