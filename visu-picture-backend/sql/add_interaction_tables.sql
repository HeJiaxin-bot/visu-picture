-- ==============================
-- 互动与治理功能增量建表脚本
-- 内容：点赞去重、收藏夹、图片评论、浏览历史、分享公开访问、操作审计日志
-- 执行方式：mysql -uroot -p1234 visu < add_interaction_tables.sql
-- 说明：本库列名统一使用驼峰（与 MyBatis-Plus 实体字段一致，
--       因为 application.yml 中 map-underscore-to-camel-case: false）
-- ==============================

use visu;

-- ---------------------------------------------------------------
-- 1. 图片点赞记录（点赞去重）
--    picture.likeCount 是计数器；本表存「谁赞过哪张图」的多对多关系。
--    唯一索引 uk_pictureId_userId 保证同一用户对同一图片只能有一条记录，
--    配合 INSERT IGNORE 实现幂等，彻底杜绝重复点赞把计数刷高。
-- ---------------------------------------------------------------
create table if not exists picture_like
(
    id         bigint auto_increment comment 'id' primary key,
    pictureId  bigint                             not null comment '图片 id',
    userId     bigint                             not null comment '点赞用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '点赞时间',
    UNIQUE KEY uk_pictureId_userId (pictureId, userId),
    INDEX idx_userId (userId),
    INDEX idx_pictureId (pictureId)
) comment '图片点赞记录' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 2. 图片收藏记录
--    与点赞同构，业务语义不同（点赞偏表达态度，收藏偏私人整理）。
-- ---------------------------------------------------------------
create table if not exists picture_favorite
(
    id         bigint auto_increment comment 'id' primary key,
    pictureId  bigint                             not null comment '图片 id',
    userId     bigint                             not null comment '收藏用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '收藏时间',
    UNIQUE KEY uk_pictureId_userId (pictureId, userId),
    INDEX idx_userId (userId),
    INDEX idx_pictureId (pictureId)
) comment '图片收藏记录' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 3. 图片评论（一级平铺评论）
-- ---------------------------------------------------------------
create table if not exists picture_comment
(
    id          bigint auto_increment comment 'id' primary key,
    pictureId   bigint                             not null comment '图片 id',
    userId      bigint                             not null comment '评论用户 id',
    content     varchar(500)                       not null comment '评论内容',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除',
    INDEX idx_pictureId (pictureId),
    INDEX idx_userId (userId),
    INDEX idx_createTime (createTime)
) comment '图片评论' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 4. 浏览历史 / 最近查看
--    唯一索引 (userId, pictureId)：同一张图只留一条，重复查看只刷新时间，
--    因此「最近查看」天然按时间倒序且不会重复占位。
-- ---------------------------------------------------------------
create table if not exists picture_view_history
(
    id         bigint auto_increment comment 'id' primary key,
    userId     bigint                             not null comment '用户 id',
    pictureId  bigint                             not null comment '图片 id',
    viewTime   datetime default CURRENT_TIMESTAMP not null comment '最近查看时间',
    viewCount  int      default 1                 not null comment '累计查看次数',
    UNIQUE KEY uk_userId_pictureId (userId, pictureId),
    INDEX idx_userId_viewTime (userId, viewTime)
) comment '图片浏览历史' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 5. 图片分享链接（公开访问）
--    shareCode 为对外暴露的随机码，泄露后可删除（逻辑删除）使其失效；
--    expireTime 为空表示永久有效；viewCount 记录公开访问次数。
-- ---------------------------------------------------------------
create table if not exists picture_share
(
    id          bigint auto_increment comment 'id' primary key,
    shareCode   varchar(32)                        not null comment '分享码（对外唯一标识）',
    pictureId   bigint                             not null comment '被分享的图片 id',
    userId      bigint                             not null comment '创建分享的用户 id',
    expireTime  datetime                           null comment '过期时间（为空表示永久有效）',
    viewCount   int      default 0                 not null comment '公开访问次数',
    createTime  datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime  datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete    tinyint  default 0                 not null comment '是否删除（撤销分享）',
    UNIQUE KEY uk_shareCode (shareCode),
    INDEX idx_pictureId (pictureId),
    INDEX idx_userId (userId)
) comment '图片分享链接' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 6. 操作审计日志
-- ---------------------------------------------------------------
create table if not exists audit_log
(
    id           bigint auto_increment comment 'id' primary key,
    userId       bigint                             null comment '操作人 id（未登录为空）',
    userAccount  varchar(256)                       null comment '操作人账号快照',
    module       varchar(64)                        not null comment '业务模块：picture/user/space/notification/...',
    action       varchar(64)                        not null comment '操作类型：delete/update/review/...',
    targetType   varchar(64)                        null comment '目标类型',
    targetId     bigint                             null comment '目标 id',
    detail       varchar(1024)                      null comment '操作详情（JSON 或描述）',
    ip           varchar(64)                        null comment '客户端 IP',
    userAgent    varchar(512)                       null comment 'User-Agent',
    success      tinyint  default 1                 not null comment '是否成功：0-失败 1-成功',
    errorMessage varchar(512)                       null comment '失败原因',
    costMs       bigint                             null comment '耗时（毫秒）',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '操作时间',
    INDEX idx_userId (userId),
    INDEX idx_module_action (module, action),
    INDEX idx_createTime (createTime)
) comment '操作审计日志' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------
-- 7. picture 表增量字段
--    picHash   ：图片内容 SHA-256，用于重复图片检测（上传时比对）
--    favoriteCount：收藏计数（与 likeCount 同理，计数器留在主表，列表页免 COUNT）
-- ---------------------------------------------------------------
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，用存储过程做幂等处理
drop procedure if exists add_column_if_absent;
delimiter $$
create procedure add_column_if_absent(in tbl varchar(64), in col varchar(64), in ddl varchar(512))
begin
    if not exists (select 1
                   from information_schema.COLUMNS
                   where TABLE_SCHEMA = database()
                     and TABLE_NAME = tbl
                     and COLUMN_NAME = col) then
        set @s = concat('alter table `', tbl, '` add column ', ddl);
        prepare stmt from @s;
        execute stmt;
        deallocate prepare stmt;
    end if;
end $$
delimiter ;

call add_column_if_absent('picture', 'picHash', '`picHash` varchar(64) null comment ''图片内容 SHA-256（重复检测）'' after `picColor`');
call add_column_if_absent('picture', 'favoriteCount', '`favoriteCount` int default 0 not null comment ''收藏数'' after `likeCount`');

drop procedure if exists add_column_if_absent;

-- 重复检测按 (picHash, spaceId) 检索，建索引
-- （同样做幂等处理）
drop procedure if exists add_index_if_absent;
delimiter $$
create procedure add_index_if_absent(in tbl varchar(64), in idx varchar(64), in cols varchar(255))
begin
    if not exists (select 1
                   from information_schema.STATISTICS
                   where TABLE_SCHEMA = database()
                     and TABLE_NAME = tbl
                     and INDEX_NAME = idx) then
        set @s = concat('alter table `', tbl, '` add index `', idx, '` (', cols, ')');
        prepare stmt from @s;
        execute stmt;
        deallocate prepare stmt;
    end if;
end $$
delimiter ;

call add_index_if_absent('picture', 'idx_picHash', '`picHash`');

drop procedure if exists add_index_if_absent;

-- ---------------------------------------------------------------
-- 执行结果自检
-- ---------------------------------------------------------------
select table_name as created_table
from information_schema.TABLES
where TABLE_SCHEMA = database()
  and TABLE_NAME in ('picture_like', 'picture_favorite', 'picture_comment',
                     'picture_view_history', 'picture_share', 'audit_log')
order by table_name;

select column_name, column_type, column_comment
from information_schema.COLUMNS
where TABLE_SCHEMA = database()
  and TABLE_NAME = 'picture'
  and COLUMN_NAME in ('picHash', 'favoriteCount', 'likeCount');