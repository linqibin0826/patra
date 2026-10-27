-- ============================================================
-- Patra Identity — V2：@ 联想的用户名与昵称前缀索引（PAP-118）
-- 设计: docs/patra/specs/2026-10-27-identity-mention-support-design.md 第 4.5 节
-- ============================================================

-- 库按 postgres:17 镜像默认建库，排序规则是 en_US.utf8：连接符、下划线在第一层被忽略，前缀范围在这个
-- 顺序下不连续，按它排序的索引用不上。两个索引都按 "C"（UTF-8 字节序，即码点序）排序。
-- 查询必须写成 列 COLLATE "C" >= 前缀 AND 列 COLLATE "C" < 前缀末码点加一（UserDao），不能用 LIKE：
-- _ 是 LIKE 的通配符，参数化的 LIKE 在通用计划下也推不出索引范围。

-- ============================================================
-- 用户名：只收 ACTIVE。联想只要正常账号；查询里 status 写字面量，通用计划下也能匹配这个部分索引。
-- ============================================================
CREATE INDEX idx_idn_user_username_prefix
    ON idn_user (username COLLATE "C")
    WHERE status = 'ACTIVE';

-- ============================================================
-- 昵称：按小写昵称，比较不分大小写；lower() 按列的排序规则做大小写映射。只收已设昵称的行：没设昵称的人
-- 昵称跟随用户名，已由上一个索引覆盖。做不到只收 ACTIVE：状态在 idn_user，部分索引不能引用别的表；
-- ACTIVE 由查询联用户表、按主键回表时过滤。查询里 display_name IS NOT NULL 写字面量，匹配部分索引。
-- ============================================================
CREATE INDEX idx_idn_user_profile_display_name_prefix
    ON idn_user_profile ((lower(display_name)) COLLATE "C")
    WHERE display_name IS NOT NULL;
