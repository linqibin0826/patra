-- ============================================================
-- Patra Catalog 数据库 - 资助体系 DDL
-- ============================================================
-- 版本: V1.5.0
-- 领域: Funding 领域
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: 资助体系（2张表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- 资助体系 (2张表):
--   1. cat_funding (资助信息表) - 无依赖
--   2. cat_publication_funding (文献-资助关联表) - 依赖 cat_publication, cat_funding
-- ============================================================


-- ============================================================
-- 表 1: cat_funding (资助信息表)
-- ============================================================
-- 表说明: 管理研究资金来源和项目信息,通过去重策略避免重复存储
-- 记录数预估: 初始 300万 / 年增长 80万 / 5年规模 700万
-- 主要查询场景:
--   1. 按 dedup_key 去重查询(>1000次/天,高频)
--   2. 按 funder_id 查询(500-1000次/天,中频)
--   3. 按 agency_name 查询(100-500次/天,中频)
--   4. 按 grant_id 查询(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_funding` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `agency_name` VARCHAR(200) NOT NULL COMMENT '资助机构名称',
    `agency_abbreviation` VARCHAR(100) NULL DEFAULT NULL COMMENT '机构缩写(如"NIH","NSF")',
    `country` VARCHAR(100) NULL DEFAULT NULL COMMENT '资助国家(ISO 3166-1 alpha-3,如USA/CHN)',
    `grant_id` VARCHAR(100) NOT NULL COMMENT '资助编号/项目编号',
    `grant_acronym` VARCHAR(100) NULL DEFAULT NULL COMMENT '项目缩写',
    `grant_name` VARCHAR(500) NULL DEFAULT NULL COMMENT '项目名称',
    `funding_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '资助类型(Government/Foundation/Corporate/University/Non-profit/Other)',
    `amount` DECIMAL(20,2) NULL DEFAULT NULL COMMENT '资助金额',
    `currency` VARCHAR(10) NULL DEFAULT NULL COMMENT '货币类型(如"USD","EUR","CNY")',
    `start_date` DATE NULL DEFAULT NULL COMMENT '开始日期',
    `end_date` DATE NULL DEFAULT NULL COMMENT '结束日期',
    `funder_id` VARCHAR(100) NULL DEFAULT NULL COMMENT 'Crossref Funder Registry ID',
    `ror_id` VARCHAR(100) NULL DEFAULT NULL COMMENT 'ROR(Research Organization Registry)标识符',
    `dedup_key` VARCHAR(255) NOT NULL COMMENT '去重键(MD5哈希,基于agency_name+grant_id)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '资助元数据(灵活扩展)',

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
    UNIQUE INDEX `uk_dedup_key` (`dedup_key`) COMMENT '去重键唯一索引,防止重复资助记录,支持插入前去重查询',

    -- 普通索引
    INDEX `idx_agency` (`agency_name`) COMMENT '机构名称索引,支持按机构查询资助项目',
    INDEX `idx_grant_id` (`grant_id`) COMMENT '项目编号索引,支持按项目编号查询',
    INDEX `idx_funder_id` (`funder_id`) COMMENT 'Crossref Funder ID 索引,支持标准化查询',
    INDEX `idx_ror` (`ror_id`) COMMENT 'ROR 标识符索引,支持机构标识符查询',
    INDEX `idx_funding_type` (`funding_type`) COMMENT '资助类型索引,支持按类型筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='资助信息表:管理研究资金来源和项目信息,支持去重策略';


-- ============================================================
-- 表 2: cat_publication_funding (文献-资助关联表)
-- ============================================================
-- 表说明: 管理文献与资助的多对多关系,支持主要资助标记和顺序
-- 记录数预估: 初始 750万 / 年增长 200万 / 5年规模 1750万
-- 主要查询场景:
--   1. 查询某文献的所有资助来源(>1000次/天,高频)
--   2. 查询某资助项目的所有文献产出(100-500次/天,中频)
--   3. 按主要资助筛选(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_funding` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `funding_id` BIGINT UNSIGNED NOT NULL COMMENT '资助ID(外键:cat_funding.id)',
    `acknowledgment_text` VARCHAR(500) NULL DEFAULT NULL COMMENT '致谢文本(原始致谢内容)',
    `is_primary` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否主要资助(0=否,1=是)',
    `order_num` INTEGER NOT NULL DEFAULT 1 COMMENT '顺序号(用于排序显示)',
    `recipient_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '接收人/主要研究者(PI)姓名',
    `recipient_orcid` VARCHAR(100) NULL DEFAULT NULL COMMENT '接收人 ORCID 标识符',
    `metadata` JSON NULL DEFAULT NULL COMMENT '关联元数据(灵活扩展)',

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
    UNIQUE INDEX `uk_pub_funding` (`publication_id`, `funding_id`) COMMENT '文献+资助组合唯一索引,防止重复关联',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '文献ID索引,支持查询某文献的所有资助来源(高频)',
    INDEX `idx_funding` (`funding_id`) COMMENT '资助ID索引,支持查询某资助项目的所有文献产出(中频)',
    INDEX `idx_primary` (`is_primary`) COMMENT '主要资助索引,支持筛选主要资助来源'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='文献-资助关联表:管理文献与资助的多对多关系';
