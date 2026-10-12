-- ==============================
-- 视界智能协同云图库 建表脚本
-- 与线上数据库（visu）当前结构保持一致，
-- 历史版本的 ALTER 增量语句已合并进各表的建表语句，可直接用于全新环境初始化。
-- ==============================

-- 创建库
create database if not exists visu default charset utf8mb4 collate utf8mb4_unicode_ci;

-- 切换库
use visu;

-- 用户表（含会员、积分签到字段）
create table if not exists user
(
    id            bigint auto_increment comment 'id' primary key,
    userAccount   varchar(256)                           not null comment '账号',
    userPassword  varchar(512)                           not null comment '密码',
    userName      varchar(256)                           null comment '用户昵称',
    userAvatar    varchar(1024)                          null comment '用户头像',
    userProfile   varchar(512)                           null comment '用户简介',
    phone         varchar(128)                           null comment '电话',
    email         varchar(512)                           null comment '邮箱',
    userRole      varchar(256) default 'user'            not null comment '用户角色：user/admin',
    isVip         tinyint      default 0                 not null comment '是否会员：0-否 1-是',
    editTime      datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime    datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime    datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete      tinyint      default 0                 not null comment '是否删除',
    vipExpireTime datetime                               null comment '会员过期时间',
    vipCode       varchar(128)                           null comment '会员兑换码',
    vipNumber     bigint                                 null comment '会员编号',
    points        int          default 0                 not null comment '积分',
    lastSignInTime datetime                              null comment '最近签到时间',
    inviteCode    varchar(32)                            null comment '我的专属邀请码',
    inviterId     bigint                                 null comment '邀请人用户 id',
    UNIQUE KEY uk_userAccount (userAccount),
    UNIQUE KEY uk_inviteCode (inviteCode),
    INDEX idx_userName (userName),
    INDEX idx_inviterId (inviterId)
) comment '用户' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- 图片表（含空间、审核、缩略图、主色调、去重摘要、互动计数）
create table if not exists picture
(
    id           bigint auto_increment comment 'id' primary key,
    url          varchar(512)                       not null comment '图片 url',
    name         varchar(128)                       not null comment '图片名称',
    introduction varchar(512)                       null comment '简介',
    category     varchar(64)                        null comment '分类',
    tags         varchar(512)                       null comment '标签（JSON 数组）',
    picSize      bigint                             null comment '图片体积',
    picWidth     int                                null comment '图片宽度',
    picHeight    int                                null comment '图片高度',
    picScale     double                             null comment '图片宽高比例',
    picFormat    varchar(32)                        null comment '图片格式',
    userId       bigint                             not null comment '创建用户 id',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    spaceId      bigint                             null comment '空间 id（为空表示公共空间）',
    thumbnailUrl varchar(512)                       null comment '缩略图 url',
    reviewStatus int      default 0                 not null comment '审核状态：0-待审核; 1-通过; 2-拒绝',
    reviewMessage varchar(512)                      null comment '审核信息',
    reviewerId   bigint                             null comment '审核人 ID',
    reviewTime   datetime                           null comment '审核时间',
    picColor     varchar(16)                        null comment '图片主色调',
    picHash      varchar(64)                        null comment '图片内容 SHA-256（字节级重复检测）',
    isAiGenerated tinyint default 0                not null comment '是否 AI 生成（1-是，0-否）',
    likeCount    int default 0                     not null comment '点赞数',
    favoriteCount int default 0                    not null comment '收藏数',
    INDEX idx_name (name),                 -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction), -- 用于模糊搜索图片简介
    INDEX idx_category (category),         -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                 -- 提升基于标签的查询性能
    INDEX idx_userId (userId),             -- 提升基于用户 ID 的查询性能
    INDEX idx_reviewStatus (reviewStatus), -- 提升基于审核状态的查询性能
    INDEX idx_spaceId (spaceId),           -- 提升基于空间的查询性能
    INDEX idx_picHash (picHash)            -- 提升重复图片检测的比对性能
) comment '图片' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- 图片点赞记录（点赞去重）
-- picture.likeCount 是计数器；本表存「谁赞过哪张图」的多对多关系。
-- 唯一索引 uk_pictureId_userId + INSERT IGNORE 保证同一用户对同一图片只有一条记录，
-- 从数据库层面杜绝重复点赞把计数刷高（连点、脚本、并发请求都失效）。
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

-- 图片收藏记录（与点赞同构，业务语义不同：点赞表达态度，收藏偏私人整理）
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

-- 图片评论
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

-- 图片浏览历史（最近查看）
-- 唯一索引 (userId, pictureId)：同一张图只留一条记录，重复查看刷新时间并累加次数，
-- 因此「最近查看」天然按时间倒序且不会重复占位。
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

-- 图片分享链接（公开访问）
-- shareCode 为对外暴露的随机码；撤销（逻辑删除）后立即失效；expireTime 为空表示永久有效。
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

-- 操作审计日志
create table if not exists audit_log
(
    id           bigint auto_increment comment 'id' primary key,
    userId       bigint                             null comment '操作人 id（未登录为空）',
    userAccount  varchar(256)                       null comment '操作人账号快照',
    module       varchar(64)                        not null comment '业务模块：picture/user/space/notification',
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

-- 站内通知表（图片审核结果、系统公告等）
create table if not exists notification
(
    id          bigint auto_increment comment 'id' primary key,
    userId      bigint                             not null comment '接收通知的用户 id',
    type        varchar(32)  default 'system'      not null comment '通知类型：review-审核结果/system-系统公告/interact-互动',
    title       varchar(128)                       not null comment '通知标题',
    content     varchar(512)                       null comment '通知内容（如拒绝理由）',
    picId       bigint                             null comment '关联图片 id（可跳转图片详情）',
    picThumbnail varchar(512)                      null comment '关联图片缩略图',
    isRead      tinyint      default 0             not null comment '是否已读：0-未读 1-已读',
    createTime  datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    INDEX idx_userId (userId),
    INDEX idx_isRead (isRead),
    INDEX idx_createTime (createTime)
) comment '站内通知' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- 空间表（含空间类型、封面字段）
create table if not exists space
(
    id           bigint auto_increment comment 'id' primary key,
    spaceName    varchar(128)                       null comment '空间名称',
    spaceLevel   int      default 0                 null comment '空间级别：0-普通版 1-专业版 2-旗舰版',
    coverPicture varchar(512)                       null comment '空间封面图 url',
    maxSize      bigint   default 0                 null comment '空间图片的最大总大小',
    maxCount     bigint   default 0                 null comment '空间图片的最大数量',
    totalSize    bigint   default 0                 null comment '当前空间下图片的总大小',
    totalCount   bigint   default 0                 null comment '当前空间下的图片数量',
    userId       bigint                             not null comment '创建用户 id',
    createTime   datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime     datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime   datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete     tinyint  default 0                 not null comment '是否删除',
    spaceType    int      default 0                 not null comment '空间类型：0-私有 1-团队',
    -- 索引设计
    index idx_userId (userId),         -- 提升基于用户的查询效率
    index idx_spaceName (spaceName),   -- 提升基于空间名称的查询效率
    index idx_spaceLevel (spaceLevel), -- 提升按空间级别查询的效率
    index idx_spaceType (spaceType)    -- 提升按空间类型查询的效率
) comment '空间' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- 空间成员表
create table if not exists space_user
(
    id         bigint auto_increment comment 'id' primary key,
    spaceId    bigint                                 not null comment '空间 id',
    userId     bigint                                 not null comment '用户 id',
    spaceRole  varchar(128) default 'viewer'          null comment '空间角色：viewer/editor/admin',
    createTime datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    -- 索引设计
    UNIQUE KEY uk_spaceId_userId (spaceId, userId), -- 唯一索引，用户在一个空间中只能有一个角色
    INDEX idx_spaceId (spaceId),                    -- 提升按空间查询的性能
    INDEX idx_userId (userId)                       -- 提升按用户查询的性能
) comment '空间用户关联' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;
