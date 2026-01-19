-- ============================================================
-- Patra Catalog 数据库 - 参考文献表 DDL（超大数据量）
-- ============================================================
-- 版本: V1.1.2
-- 领域: Publication 领域
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: 参考文献表（1张表，预估5.25亿条记录）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表说明
-- ============================================================
-- 注意: 本表数据量极大（5年预估5.25亿条），单独管理便于后续分表/分库决策
-- 可能的分片策略:
--   1. 按 publication_id 哈希分片（推荐）
--   2. 按 year 范围分片（历史数据归档）
--   3. 按 publication_id 区间分片（顺序写入友好）
-- ============================================================


-- ============================================================
-- 表: cat_publication_reference (参考文献表)
-- ============================================================
-- 表说明: 管理文献引用关系,支持库内外引用双重关联
-- 记录数预估: 初始 2亿 / 年增长 6500万 / 5年规模 5.25亿
-- 主要查询场景:
--   1. 查询某文献的所有参考文献(>2000次/天,高频)
--   2. 按 cited_pmid 查询库外引用(>1000次/天,高频)
--   3. 按 cited_publication_id 查询被引关系(500-1000次/天,中频)
--   4. 按 cited_doi 查询引用(<500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_reference` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '引用文献ID(本文)(外键:cat_publication.id)',
    `cited_publication_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '被引文献ID(如果在库中)(外键:cat_publication.id)',
    `cited_pmid` VARCHAR(20) NULL DEFAULT NULL COMMENT '被引文献PMID(库外引用)',
    `cited_doi` VARCHAR(200) NULL DEFAULT NULL COMMENT '被引文献DOI(库外引用)',
    `citation_text` VARCHAR(2000) NULL DEFAULT NULL COMMENT '引用文本(原始引用格式)',
    `article_title` VARCHAR(500) NULL DEFAULT NULL COMMENT '文章标题',
    `source` VARCHAR(500) NULL DEFAULT NULL COMMENT '来源期刊/书籍名称',
    `volume` VARCHAR(100) NULL DEFAULT NULL COMMENT '卷号',
    `issue` VARCHAR(100) NULL DEFAULT NULL COMMENT '期号',
    `pages` VARCHAR(50) NULL DEFAULT NULL COMMENT '页码(如"123-145")',
    `year` SMALLINT NULL DEFAULT NULL COMMENT '出版年份',
    `authors` VARCHAR(500) NULL DEFAULT NULL COMMENT '作者列表(简化格式)',
    `reference_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '引用类型(Journal Article/Book/Book Chapter/Conference Paper/Thesis/Report/Preprint/Web Page/Other)',
    `reference_number` INTEGER NOT NULL COMMENT '引用编号(本文中的序号)',
    `is_retracted` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否已撤稿(0=否,1=是)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '引用元数据(灵活扩展)',

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
    UNIQUE INDEX `uk_reference_num` (`publication_id`, `reference_number`) COMMENT '文献+引用编号唯一索引,保证引用编号在同一文献内唯一',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '文献ID索引,支持查询某文献的所有参考文献(高频)',
    INDEX `idx_cited_pub` (`cited_publication_id`) COMMENT '被引文献ID索引,支持查询被引关系(中频)',
    INDEX `idx_cited_pmid` (`cited_pmid`) COMMENT '被引PMID索引,支持按PMID查询引用(高频)',
    INDEX `idx_cited_doi` (`cited_doi`) COMMENT '被引DOI索引,支持按DOI查询引用(中频)',
    INDEX `idx_year` (`year`) COMMENT '年份索引,支持按年份统计引用趋势',
    INDEX `idx_retracted` (`is_retracted`) COMMENT '撤稿索引,支持筛选撤稿文献引用'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='参考文献表:管理文献引用关系,支持库内外引用(超大数据量表,预估5.25亿条)';
