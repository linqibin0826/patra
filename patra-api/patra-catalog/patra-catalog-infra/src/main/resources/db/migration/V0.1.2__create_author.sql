-- ============================================================
-- Patra Catalog 数据库 - Author 实体表 DDL
-- ============================================================
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: Author 独立实体（1张表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_unicode_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表说明
-- ============================================================
-- Author 是独立实体，不属于任何聚合
-- 通过 cat_publication_author 关联表与 Publication 建立多对多关系
-- ============================================================


-- ============================================================
-- 表: cat_author (作者表)
-- ============================================================
-- 表说明: 存储作者信息,支持复合去重策略(ORCID + 姓名 + 机构 + 邮箱)
-- 记录数预估: 初始 500万 / 年增长 200万 / 5年规模 1500万
-- 主要查询场景:
--   1. 按 ORCID 精确查询(>500次/天,中频)
--   2. 按 dedup_key 去重查询(>1000次/天,高频)
--   3. 按姓名模糊查询(100-500次/天,中频)
--   4. 按邮箱查询(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_author` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- 姓名信息
    `last_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '姓(Last Name/Family Name)',
    `fore_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '名(First Name/Given Name)',
    `initials` VARCHAR(50) NULL DEFAULT NULL COMMENT '姓名缩写(如"J.K.")',
    `suffix` VARCHAR(50) NULL DEFAULT NULL COMMENT '后缀(如"Jr.","III","PhD")',

    -- 机构信息
    `organization_name` VARCHAR(500) NULL DEFAULT NULL COMMENT '组织名称(机构/企业)',

    -- 标识符
    `orcid` VARCHAR(50) NULL DEFAULT NULL COMMENT 'ORCID标识符(格式:0000-0001-2345-6789)',
    `researcher_id` VARCHAR(100) NULL DEFAULT NULL COMMENT '研究者ID(ResearcherID/Publons)',
    `scopus_id` VARCHAR(100) NULL DEFAULT NULL COMMENT 'Scopus作者ID',

    -- 联系方式
    `email` VARCHAR(255) NULL DEFAULT NULL COMMENT '邮箱地址',

    -- 去重和状态
    `dedup_key` VARCHAR(255) NULL DEFAULT NULL COMMENT '复合去重键(应用层计算,MD5哈希)',
    `equal_contribution` BOOLEAN NOT NULL DEFAULT 0 COMMENT '同等贡献标志(0=否,1=是)',
    `valid` BOOLEAN NOT NULL DEFAULT 1 COMMENT '信息是否有效(0=无效,1=有效)',

    -- 扩展信息
    `author_metadata` JSON NULL DEFAULT NULL COMMENT '作者元数据(灵活扩展)',

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

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_orcid` (`orcid`) COMMENT 'ORCID唯一索引,支持按ORCID精确查询',

    -- 普通索引
    INDEX `idx_dedup_key` (`dedup_key`) COMMENT '去重键索引,支持去重查询和合并',
    INDEX `idx_email` (`email`) COMMENT '邮箱索引,支持按邮箱查询作者'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='作者表:存储作者信息并支持复合去重策略';
