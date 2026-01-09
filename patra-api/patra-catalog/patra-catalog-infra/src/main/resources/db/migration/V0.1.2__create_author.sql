-- ============================================================
-- Patra Catalog 数据库 - Author 聚合表 DDL
-- ============================================================
-- 设计阶段: Author 领域模型重构
-- 创建日期: 2025-01-18
-- 修改日期: 2026-01-09
-- 设计范围: Author 聚合根 + 名字变体 + ORCID（3张表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_unicode_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与设计说明
-- ============================================================
-- **设计决策**:
-- - Author 是独立聚合根，与 Publication 通过关联表建立 N:M 关系
-- - 业务键 normalized_key 与 PubMed Computed Authors 对齐
-- - 名字变体独立存表，支持多种名字形式搜索
-- - ORCID 独立存表，支持一对多（少数作者有多个 ORCID）
--
-- **表结构**:
-- 1. cat_author (聚合根) - 核心身份 + 来源追踪 + 逻辑删除
-- 2. cat_author_name_variant (名字变体，1:N) - 解析自 PubMed names 数组
-- 3. cat_author_orcid (ORCID标识符，1:N) - 全局唯一约束
-- ============================================================


-- ============================================================
-- 表 1: cat_author (作者表 - 聚合根)
-- ============================================================
-- 表说明: Author 聚合根，适配 PubMed Computed Authors 数据源
-- 记录数预估: 初始 2100万 / 年增长 100万 / 5年规模 2600万
-- 主要查询场景:
--   1. 按 normalized_key 精确查询(>2000次/天,高频)
--   2. 按 ORCID 查询(通过子表,>500次/天,中频)
--   3. 按展示名称模糊查询(100-500次/天,中频)
--   4. 按状态筛选(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_author` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL  COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 核心属性
    -- ========================================
    `normalized_key` VARCHAR(100) NOT NULL COMMENT '规范化标识(如"Lu+Z"),与PubMed Computed Authors对齐',
    `display_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '展示名称(从首个名字变体派生)',
    `status` VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态:ACTIVE/MERGED/INACTIVE',

    -- ========================================
    -- 来源追踪（Provenance）
    -- ========================================
    `provenance_code` VARCHAR(32) NOT NULL COMMENT '数据来源代码:PUBMED/ORCID/OPENALEX/MANUAL',
    `last_synced_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后同步时间(UTC,微秒精度)',

    -- ========================================
    -- 扩展字段
    -- ========================================
    `ext_data` JSON NULL DEFAULT NULL COMMENT '扩展数据(预留ORCID API补充信息)',

    -- ========================================
    -- 审计字段（完整版）
    -- ========================================
    `record_remarks` JSON NULL DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次更新自增)',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP(二进制,支持IPv4/IPv6)',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名(冗余-审计友好)',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',
    `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名(冗余-审计友好)',
    `deleted_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '逻辑删除时间戳:NULL=活动,有值=删除时间(UTC)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    UNIQUE INDEX `uk_normalized_key` (`normalized_key`) COMMENT '规范化标识唯一索引',
    INDEX `idx_status` (`status`) COMMENT '状态索引',
    INDEX `idx_provenance` (`provenance_code`) COMMENT '数据来源索引',
    INDEX `idx_display_name` (`display_name`(100)) COMMENT '展示名称前缀索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='作者表(聚合根):适配PubMed Computed Authors,支持逻辑删除';


-- ============================================================
-- 表 2: cat_author_name_variant (作者名字变体表)
-- ============================================================
-- 表说明: 存储作者的多种名字形式,解析自 PubMed 的 names 数组
-- 记录数预估: 初始 4200万 / 年增长 200万 / 5年规模 5200万
-- (平均每个作者约 2 个名字变体)
-- 主要查询场景:
--   1. 按 author_id 查询所有变体(>1000次/天,高频)
--   2. 按 last_name 模糊查询(100-500次/天,中频)
--   3. 按 full_string 精确匹配(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_author_name_variant` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL  COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `author_id` BIGINT UNSIGNED NOT NULL COMMENT '作者ID(外键:cat_author.id)',

    -- ========================================
    -- 名字组成部分
    -- ========================================
    `last_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '姓(Last Name/Family Name)',
    `fore_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '名(First Name/Given Name,可选)',
    `initials` VARCHAR(50) NULL DEFAULT NULL COMMENT '姓名缩写(如"Z","JK")',
    `full_string` VARCHAR(300) NOT NULL COMMENT '原始字符串(如"Lu,Zhiyong,Z")',

    -- ========================================
    -- 审计字段（精简版-子表）
    -- ========================================
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    UNIQUE INDEX `uk_author_full` (`author_id`, `full_string`(200)) COMMENT '作者+原始字符串唯一索引',
    INDEX `idx_author_id` (`author_id`) COMMENT '作者索引',
    INDEX `idx_last_name` (`last_name`(100)) COMMENT '姓氏前缀索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='作者名字变体表:存储作者的多种名字形式,来源PubMed Computed Authors';


-- ============================================================
-- 表 3: cat_author_orcid (作者ORCID表)
-- ============================================================
-- 表说明: 存储作者的 ORCID 标识符,支持一对多(少数作者有多个ORCID)
-- 记录数预估: 初始 500万 / 年增长 50万 / 5年规模 750万
-- (约 25% 的作者有 ORCID)
-- 主要查询场景:
--   1. 按 ORCID 精确查询(>500次/天,中频)
--   2. 按 author_id 查询所有 ORCID(100-500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_author_orcid` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL  COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `author_id` BIGINT UNSIGNED NOT NULL COMMENT '作者ID(外键:cat_author.id)',

    -- ========================================
    -- ORCID 信息
    -- ========================================
    `orcid` VARCHAR(19) NOT NULL COMMENT 'ORCID标识符(格式:0000-0001-2345-6789)',
    `is_primary` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否主要ORCID(0=否,1=是)',

    -- ========================================
    -- 审计字段（精简版-子表）
    -- ========================================
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    UNIQUE INDEX `uk_orcid` (`orcid`) COMMENT 'ORCID全局唯一索引',
    INDEX `idx_author_id` (`author_id`) COMMENT '作者索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='作者ORCID表:存储作者的ORCID标识符,支持一对多';
