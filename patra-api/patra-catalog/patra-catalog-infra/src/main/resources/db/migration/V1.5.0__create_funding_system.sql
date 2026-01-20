-- ============================================================
-- Patra Catalog 数据库 - 文献资助关联 DDL
-- ============================================================
-- 版本: V1.5.0
-- 领域: Funding 领域
-- 设计阶段: 阶段 3 - SQL DDL 生成（重构版）
-- 创建日期: 2025-01-20
-- 设计范围: 文献-资助关联表（1张表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 设计说明
-- ============================================================
-- 本次重构简化了 Funding 体系：
--
-- **原设计问题**:
--   1. cat_funding 表与 cat_organization 概念重复（资助机构信息冗余）
--   2. 缺少 provenance_code 数据来源追踪
--   3. 去重策略不可靠（基于 agency_name）
--
-- **新设计**:
--   - 移除 cat_funding 表
--   - cat_publication_funding 直接关联 cat_organization（type=FUNDER）
--   - Funder（资助机构）= cat_organization with type=FUNDER
--   - Grant（资助项目）= cat_publication_funding 中的记录
--   - 保留原始数据字段，用于后续机构匹配和数据质量分析
-- ============================================================


-- ============================================================
-- 表: cat_publication_funding (文献-资助关联表)
-- ============================================================
-- 表说明: 管理文献与资助的关联关系，存储资助项目信息
-- 记录数预估: 初始 750万 / 年增长 200万 / 5年规模 1750万
-- 主要查询场景:
--   1. 查询某文献的所有资助来源(>1000次/天,高频)
--   2. 按资助机构查询文献产出(100-500次/天,中频)
--   3. 按数据来源筛选(<100次/天,低频)
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_publication_funding` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT NOT NULL COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联关系
    -- ========================================
    `publication_id` BIGINT NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `organization_id` BIGINT NULL COMMENT '资助机构ID(外键:cat_organization.id,匹配后填充)',

    -- ========================================
    -- 资助项目信息
    -- ========================================
    `grant_id` VARCHAR(200) NULL COMMENT '资助编号/项目编号',

    -- ========================================
    -- 原始数据保留（用于机构匹配和数据质量分析）
    -- ========================================
    `funder_name_raw` VARCHAR(500) NULL COMMENT '资助机构原始名称',
    `funder_acronym_raw` VARCHAR(100) NULL COMMENT '机构缩写原始值',
    `funder_identifier_raw` VARCHAR(200) NULL COMMENT '机构标识符原始值(FundRef ID/ROR ID等)',
    `country_raw` VARCHAR(100) NULL COMMENT '国家原始值',

    -- ========================================
    -- 关联元数据
    -- ========================================
    `funding_order` INT NOT NULL DEFAULT 1 COMMENT '顺序号(用于排序显示)',

    -- ========================================
    -- 来源追踪
    -- ========================================
    `provenance_code` VARCHAR(32) NOT NULL COMMENT '数据来源(PUBMED/OPENALEX/CROSSREF等)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引：同一文献、同一资助机构、同一项目编号只能有一条记录
    -- 注意：organization_id 和 grant_id 可能为 NULL，MySQL 对 NULL 值的唯一约束处理需要注意
    UNIQUE INDEX `uk_pub_org_grant` (`publication_id`, `organization_id`, `grant_id`),

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '文献ID索引,支持查询某文献的所有资助来源(高频)',
    INDEX `idx_organization` (`organization_id`) COMMENT '机构ID索引,支持按机构查询文献产出(中频)',
    INDEX `idx_provenance` (`provenance_code`) COMMENT '数据来源索引,支持按来源筛选'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='文献-资助关联表:管理文献与资助的关联关系,资助机构通过organization_id关联cat_organization';
