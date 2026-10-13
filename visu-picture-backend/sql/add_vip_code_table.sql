-- ==============================================================================
-- 会员兑换码：存量库升级脚本
-- 执行方式：mysql -uroot -p1234 --default-character-set=utf8mb4 visu < add_vip_code_table.sql
--
-- 背景：原实现把兑换码存在 jar 内的 biz/vipCode.json，用
--       resourceLoader.getResource("classpath:...").getFile() 读写。
--       该写法在 fat jar 中必然抛 FileNotFoundException（资源在 jar 内，
--       无法解析为文件系统路径），异常被转成"系统繁忙"，导致线上兑换全部失败。
--       改为落库后：部署形态无关、状态持久、多实例并发安全、码不再进公开仓库。
--
-- 本脚本只做两件事：建表 + 回填已用状态。
-- 兑换码数据本身属于敏感凭据，**不在本文件内**，见文件末尾说明。
--
-- 幂等：可重复执行。
-- ==============================================================================

use visu;

-- ------------------------------------------------------------------------------
-- 1. 兑换码表（与 create_table.sql 中定义一致）
--    未使用 = usedBy IS NULL。刻意不设 hasUsed 布尔列：两个字段表达同一状态
--    会产生「已标记使用但没有使用人」的不一致数据。
-- ------------------------------------------------------------------------------
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

-- ------------------------------------------------------------------------------
-- 2. 导入兑换码
--    兑换码是敏感凭据，不写入版本库。请用随附的本地种子文件导入：
--
--      mysql -uroot -p --default-character-set=utf8mb4 visu < vip_codes_seed.local.sql
--
--    （该文件已被 .gitignore 忽略，仅存在于本地/服务器）
--    若种子文件中的码已存在，唯一索引 uk_code 会让重复导入被忽略，不会报错。
-- ------------------------------------------------------------------------------

-- ------------------------------------------------------------------------------
-- 3. 回填已用状态
--    历史用户在 user.vipCode 中记录了兑换时使用的码，据此回填使用人。
--    usedTime 用 vipExpireTime 反推：开通会员时 expireTime = 兑换时刻 + 12 个月
--   （见 UserServiceImpl#updateUserVipInfo），故减 12 个月即真实兑换时间。
--    不能直接把 vipExpireTime 当 usedTime —— 那是到期时间，会晚一年。
--    没有历史记录可依的码保持 usedBy IS NULL（即"未使用"），这是安全的一侧：
--    最坏情况是码可被再次兑换，而不会出现"码已被用却无法追溯"。
-- ------------------------------------------------------------------------------
update vip_code c
    join user u on u.vipCode = c.code
set c.usedBy   = u.id,
    c.usedTime = date_sub(u.vipExpireTime, interval 12 month)
where c.usedBy is null;

-- ------------------------------------------------------------------------------
-- 4. 自检
-- ------------------------------------------------------------------------------
select '兑换码总数' as metric, count(*) as value from vip_code
union all
select '  已使用', count(*) from vip_code where usedBy is not null
union all
select '  未使用', count(*) from vip_code where usedBy is null;
