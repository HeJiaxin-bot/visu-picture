-- 邀请计划增量变更：user 表新增邀请码与邀请人字段
-- 适用于已按旧版 create_table.sql 建库的环境；全新环境直接执行最新 create_table.sql 即可
alter table user
    add column inviteCode varchar(32) null comment '我的专属邀请码' after lastSignInTime,
    add column inviterId bigint null comment '邀请人用户 id' after inviteCode;

alter table user
    add unique key uk_inviteCode (inviteCode),
    add index idx_inviterId (inviterId);
