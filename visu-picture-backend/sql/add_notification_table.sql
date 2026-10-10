-- ==============================
-- 站内通知表：图片审核结果通知等
-- 执行方式：mysql -uroot -p visu < add_notification_table.sql
-- ==============================

use visu;

create table if not exists notification
(
    id          bigint auto_increment comment 'id' primary key,
    userId      bigint                             not null comment '接收通知的用户 id',
    type        varchar(32)  default 'system'      not null comment '通知类型：review-审核结果/system-系统公告/interact-互动',
    title       varchar(128)                       not null comment '通知标题',
    content     varchar(512)                       null comment '通知内容（如拒绝理由）',
    picId       bigint                             null comment '关联图片 id（可跳转图片详情）',
    picThumbnail varchar(512)                      null comment '关联图片缩略图（面板内直接展示）',
    isRead      tinyint      default 0             not null comment '是否已读：0-未读 1-已读',
    createTime  datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    INDEX idx_userId (userId),
    INDEX idx_isRead (isRead),
    INDEX idx_createTime (createTime)
) comment '站内通知' engine = InnoDB default charset = utf8mb4 collate = utf8mb4_unicode_ci;

SELECT COUNT(*) AS notification_exists FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification';
