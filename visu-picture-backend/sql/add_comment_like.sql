-- ==============================
-- 评论点赞：comment_like 表（评论级别的 ❤）
-- 执行方式：mysql -uroot -p1234 visu < add_comment_like.sql
-- ==============================

use visu;

-- 评论点赞记录（结构与 picture_like 同构）
-- 唯一索引 uk_commentId_userId + INSERT IGNORE 保证幂等
create table if not exists comment_like
(
    id        bigint auto_increment comment 'id' primary key,
    commentId bigint                             not null comment '评论 id',
    userId    bigint                             not null comment '点赞用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '点赞时间',
    UNIQUE KEY uk_commentId_userId (commentId, userId),
    INDEX idx_commentId (commentId),
    INDEX idx_userId (userId)
) comment '评论点赞记录' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- 评论表增字段：likeCount（计数器留在主表，列表免 COUNT）
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

call add_column_if_absent('picture_comment', 'likeCount',
  '`likeCount` int default 0 not null comment ''点赞数'' after `content`');

drop procedure if exists add_column_if_absent;

select table_name from information_schema.TABLES
where TABLE_SCHEMA = database() and TABLE_NAME = 'comment_like';

select column_name, column_type from information_schema.COLUMNS
where TABLE_SCHEMA = database() and TABLE_NAME = 'picture_comment' and COLUMN_NAME = 'likeCount';
