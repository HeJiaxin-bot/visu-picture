-- ==============================================================================
-- 视界智能协同云图库 —— 全量建表脚本（唯一权威 SQL 文件）
-- ==============================================================================
-- 本文件包含系统全部 10 张表的建表语句，与线上数据库（visu）当前结构一致。
-- 历史版本的增量 ALTER 脚本已全部合并进此文件，可直接用于全新环境初始化，
-- 也可重复执行（全部使用 create table if not exists）。
--
-- 执行方式：
--   mysql -uroot -p1234 --default-character-set=utf8mb4 < create_table.sql
--
-- 表清单：
--   1. user                  用户（含会员、积分签到、邀请）
--   2. picture               图片（含空间、审核、去重摘要、互动计数、分享）
--   3. picture_action        图片互动记录（点赞 / 收藏，用 actionType 区分）
--   4. picture_comment       图片评论（含楼中楼线程字段）
--   5. comment_like          评论点赞记录
--   6. picture_view_history  图片浏览历史（最近查看）
--   7. space                 空间
--   8. space_user            空间成员关联
--   9. notification          站内通知
--  10. audit_log             操作审计日志
--  11. vip_code              会员兑换码（仅结构，数据单独导入）
--
-- 命名约定：列名统一使用驼峰，与 MyBatis-Plus 实体字段一致
-- （application.yml 中 map-underscore-to-camel-case: false）。
-- ==============================================================================

-- 创建库
create database if not exists visu default charset utf8mb4 collate utf8mb4_unicode_ci;

-- 切换库
use visu;

-- ==============================================================================
-- 1. 用户表
-- ==============================================================================
create table if not exists user
(
    id             bigint auto_increment comment 'id' primary key,
    userAccount    varchar(256)                           not null comment '账号',
    userPassword   varchar(512)                           not null comment '密码',
    userName       varchar(256)                           null comment '用户昵称',
    userAvatar     varchar(1024)                          null comment '用户头像',
    userProfile    varchar(512)                           null comment '用户简介',
    phone          varchar(128)                           null comment '电话',
    email          varchar(512)                           null comment '邮箱（注册时同时作为账号）',
    userRole       varchar(256) default 'user'            not null comment '用户角色：user/admin',
    isVip          tinyint      default 0                 not null comment '是否会员：0-否 1-是',
    editTime       datetime     default CURRENT_TIMESTAMP not null comment '编辑时间',
    createTime     datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime     datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete       tinyint      default 0                 not null comment '是否删除',
    vipExpireTime  datetime                               null comment '会员过期时间',
    vipCode        varchar(128)                           null comment '会员兑换码',
    vipNumber      bigint                                 null comment '会员编号',
    points         int          default 0                 not null comment '积分',
    inviteCode     varchar(32)                            null comment '我的专属邀请码',
    inviterId      bigint                                 null comment '邀请人用户 id',
    lastSignInTime datetime                               null comment '最近签到时间',
    UNIQUE KEY uk_userAccount (userAccount),
    UNIQUE KEY uk_email (email),          -- 邮箱唯一：注册时 email 即账号，需在库层兜底防重复
    UNIQUE KEY uk_inviteCode (inviteCode),-- 邀请码唯一；NULL 不参与去重
    INDEX idx_userName (userName),
    INDEX idx_inviterId (inviterId)
) comment '用户' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ==============================================================================
-- 2. 图片表
-- ==============================================================================
create table if not exists picture
(
    id             bigint auto_increment comment 'id' primary key,
    url            varchar(512)                       not null comment '图片 url',
    name           varchar(128)                       not null comment '图片名称',
    introduction   varchar(512)                       null comment '简介',
    category       varchar(64)                        null comment '分类',
    tags           varchar(512)                       null comment '标签（JSON 数组）',
    picSize        bigint                             null comment '图片体积',
    picWidth       int                                null comment '图片宽度',
    picHeight      int                                null comment '图片高度',
    picScale       double                             null comment '图片宽高比例',
    picFormat      varchar(32)                        null comment '图片格式',
    userId         bigint                             not null comment '创建用户 id',
    createTime     datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    editTime       datetime default CURRENT_TIMESTAMP not null comment '编辑时间',
    updateTime     datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete       tinyint  default 0                 not null comment '是否删除',
    spaceId        bigint                             null comment '空间 id（为空表示公共空间）',
    thumbnailUrl   varchar(512)                       null comment '缩略图 url',
    reviewStatus   int      default 0                 not null comment '审核状态：0-待审核; 1-通过; 2-拒绝',
    reviewMessage  varchar(512)                       null comment '审核信息',
    reviewerId     bigint                             null comment '审核人 ID',
    reviewTime     datetime                           null comment '审核时间',
    picColor       varchar(16)                        null comment '图片主色调',
    picHash        varchar(64)                        null comment '图片内容 SHA-256（字节级重复检测）',
    isAiGenerated  tinyint  default 0                 not null comment '是否 AI 生成（1-是，0-否）',
    likeCount      int      default 0                 not null comment '点赞数（计数器留在主表，列表免 COUNT）',
    favoriteCount  int      default 0                 not null comment '收藏数',
    -- 分享字段：原 picture_share 表已并入。一张图最多一条有效分享链接，本质 1:1，
    -- 故并入主表，省掉一张表与一套 Mapper/Service。
    -- 取舍：撤销即 shareCode = NULL，不再保留历史链接痕迹（系统无分享历史页面，可接受）。
    shareCode      varchar(32)                        null comment '分享码（NULL=未分享，非空即可公开访问）',
    shareExpireTime datetime                          null comment '分享过期时间（NULL=永久有效）',
    shareViewCount int      default 0                 not null comment '分享链接访问次数',
    shareCreateTime datetime                          null comment '分享创建时间',
    INDEX idx_name (name),                  -- 提升基于图片名称的查询性能
    INDEX idx_introduction (introduction),  -- 用于模糊搜索图片简介
    INDEX idx_category (category),          -- 提升基于分类的查询性能
    INDEX idx_tags (tags),                  -- 提升基于标签的查询性能
    INDEX idx_userId (userId),              -- 提升基于用户 ID 的查询性能
    INDEX idx_reviewStatus (reviewStatus),  -- 提升基于审核状态的查询性能
    INDEX idx_spaceId (spaceId),            -- 提升基于空间的查询性能
    INDEX idx_picHash (picHash),            -- 提升重复图片检测的比对性能
    UNIQUE KEY uk_shareCode (shareCode)     -- 分享码唯一（NULL 不参与去重，正好表达"未分享"）
) comment '图片' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ==============================================================================
-- 3. 图片互动记录（点赞 / 收藏合并为一张表）
-- ==============================================================================
-- 原 picture_like 与 picture_favorite 结构、索引、查询模式完全同构，
-- 合并后用 actionType 区分动作：唯一索引含 actionType，
-- 因此「同一用户对同一图片的同一动作」仍只能有一条，去重能力不变。
-- 配合 INSERT IGNORE 实现幂等，彻底杜绝重复点赞把计数刷高。
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

-- ==============================================================================
-- 4. 图片评论（含楼中楼线程字段）
-- ==============================================================================
-- 模型（B站/网易云式两级展示）：
--   顶层评论：parentId / rootId 均为 NULL，参与分页与排序
--   回复：    parentId=被回复评论 id，rootId=所属顶层评论 id，
--             全部平铺展示在顶层评论下（回复回复时不加深度）
create table if not exists picture_comment
(
    id              bigint auto_increment comment 'id' primary key,
    pictureId       bigint                             not null comment '图片 id',
    userId          bigint                             not null comment '评论用户 id',
    parentId        bigint                             null comment '父评论 id（回复时指向被回复的评论，顶层为 NULL）',
    rootId          bigint                             null comment '线程根评论 id（回复所属的顶层评论，顶层为 NULL）',
    replyToUserId   bigint                             null comment '被回复用户 id',
    replyToUserName varchar(256)                       null comment '被回复用户昵称快照（用户改名后仍可显示）',
    location        varchar(64)                        null comment '评论者 IP 属地（省份，预留字段）',
    content         varchar(500)                       not null comment '评论内容',
    likeCount       int      default 0                 not null comment '点赞数',
    createTime      datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime      datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    isDelete        tinyint  default 0                 not null comment '是否删除',
    INDEX idx_pictureId (pictureId),
    INDEX idx_userId (userId),
    INDEX idx_createTime (createTime),
    INDEX idx_rootId (rootId)              -- 按线程根取回复是最高频查询
) comment '图片评论' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ==============================================================================
-- 5. 评论点赞记录
-- ==============================================================================
-- 与图片互动同构，但目标实体是评论，故独立成表（避免多态外键丢失引用完整性）。
-- 唯一索引 uk_commentId_userId + INSERT IGNORE 保证幂等。
create table if not exists comment_like
(
    id         bigint auto_increment comment 'id' primary key,
    commentId  bigint                             not null comment '评论 id',
    userId     bigint                             not null comment '点赞用户 id',
    createTime datetime default CURRENT_TIMESTAMP not null comment '点赞时间',
    UNIQUE KEY uk_commentId_userId (commentId, userId),
    INDEX idx_commentId (commentId),
    INDEX idx_userId (userId)
) comment '评论点赞记录' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ==============================================================================
-- 6. 图片浏览历史（最近查看）
-- ==============================================================================
-- 唯一索引 (userId, pictureId)：同一张图只留一条记录，重复查看刷新时间并累加次数，
-- 因此「最近查看」天然按时间倒序且不会重复占位。
create table if not exists picture_view_history
(
    id        bigint auto_increment comment 'id' primary key,
    userId    bigint                             not null comment '用户 id',
    pictureId bigint                             not null comment '图片 id',
    viewTime  datetime default CURRENT_TIMESTAMP not null comment '最近查看时间',
    viewCount int      default 1                 not null comment '累计查看次数',
    UNIQUE KEY uk_userId_pictureId (userId, pictureId),
    INDEX idx_userId_viewTime (userId, viewTime)
) comment '图片浏览历史' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ==============================================================================
-- 7. 空间表
-- ==============================================================================
create table if not exists space
(
    id           bigint auto_increment comment 'id' primary key,
    spaceName    varchar(128)                       null comment '空间名称',
    spaceLevel   int      default 0                 null comment '空间级别：0-普通版 1-专业版 2-旗舰版',
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
    coverPicture varchar(512)                       null comment '空间封面图 url',
    INDEX idx_userId (userId),          -- 提升基于用户的查询效率
    INDEX idx_spaceName (spaceName),    -- 提升基于空间名称的查询效率
    INDEX idx_spaceLevel (spaceLevel),  -- 提升按空间级别查询的效率
    INDEX idx_spaceType (spaceType)     -- 提升按空间类型查询的效率
) comment '空间' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ==============================================================================
-- 8. 空间成员表
-- ==============================================================================
create table if not exists space_user
(
    id         bigint auto_increment comment 'id' primary key,
    spaceId    bigint                                 not null comment '空间 id',
    userId     bigint                                 not null comment '用户 id',
    spaceRole  varchar(128) default 'viewer'          null comment '空间角色：viewer/editor/admin',
    createTime datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    updateTime datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    UNIQUE KEY uk_spaceId_userId (spaceId, userId), -- 用户在一个空间中只能有一个角色
    INDEX idx_spaceId (spaceId),                    -- 提升按空间查询的性能
    INDEX idx_userId (userId)                       -- 提升按用户查询的性能
) comment '空间用户关联' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ==============================================================================
-- 9. 站内通知表
-- ==============================================================================
create table if not exists notification
(
    id           bigint auto_increment comment 'id' primary key,
    userId       bigint                             not null comment '接收通知的用户 id',
    type         varchar(32)  default 'system'      not null comment '通知类型：review-审核结果/system-系统公告/interact-互动',
    title        varchar(128)                       not null comment '通知标题',
    content      varchar(512)                       null comment '通知内容（如拒绝理由）',
    picId        bigint                             null comment '关联图片 id（可跳转图片详情）',
    picThumbnail varchar(512)                       null comment '关联图片缩略图（面板内直接展示）',
    isRead       tinyint      default 0             not null comment '是否已读：0-未读 1-已读',
    createTime   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    INDEX idx_userId (userId),
    INDEX idx_isRead (isRead),
    INDEX idx_createTime (createTime)
) comment '站内通知' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ==============================================================================
-- 10. 操作审计日志
-- ==============================================================================
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

-- ==============================================================================
-- 11. 会员兑换码
-- ==============================================================================
-- 原实现把兑换码存在 jar 内的 biz/vipCode.json，用
-- resourceLoader.getResource("classpath:...").getFile() 读写 —— 该写法在 fat jar 中
-- 必然抛 FileNotFoundException（资源在 jar 内，无法解析为文件系统路径），
-- 异常被转成"系统繁忙"，导致线上兑换全部失败。改为落库后与部署形态无关。
--
-- 未使用 = usedBy IS NULL。刻意不设 hasUsed 布尔列：两个字段表达同一状态会产生
-- 「已标记使用但没有使用人」的不一致数据，用一个可空列即可表达且不会矛盾。
-- 占用走原子 UPDATE ... WHERE usedBy IS NULL（见 VipCodeMapper.xml），
-- 并发下只有一条能成功，多实例部署同样安全。
--
-- 注意：本表只建结构，不含任何兑换码数据。
-- 兑换码属于敏感凭据，通过单独脚本导入，不入版本库。
create table if not exists vip_code
(
    id         bigint auto_increment comment 'id' primary key,
    code       varchar(64)                        not null comment '兑换码',
    usedBy     bigint                             null comment '使用人用户 id（NULL = 未使用）',
    usedTime   datetime                           null comment '使用时间（NULL = 未使用）',
    createTime datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    UNIQUE KEY uk_code (code),
    INDEX idx_usedBy (usedBy)
) comment '会员兑换码' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

-- ==============================================================================
-- 执行结果自检：应列出全部 11 张表
-- ==============================================================================
select table_name as created_table
from information_schema.TABLES
where TABLE_SCHEMA = database()
  and TABLE_NAME in ('user', 'picture', 'picture_action', 'picture_comment',
                     'comment_like', 'picture_view_history', 'space',
                     'space_user', 'notification', 'audit_log', 'vip_code')
order by table_name;
