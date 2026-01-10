-- ============================================================
-- Patra Catalog 数据库 - 关键词索引表 DDL
-- ============================================================
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: 关键词索引（2张表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- 关键词体系 (2张表):
--   1. cat_keyword (关键词表) - 无依赖
--   2. cat_publication_keyword (文献-关键词关联表) - 依赖 cat_publication, cat_keyword
--
-- 注: 出版类型（Publication Type）已合并到 MeSH 系统中（V0.2.0），
--     使用 cat_mesh_term 表的 descriptor_class='2' 字段标识
-- 注: 化学物质（Substance）已合并到 MeSH SCR 系统中（V0.2.2），
--     使用 cat_mesh_scr 表存储化学物质/药物信息
-- ============================================================


-- ============================================================
-- 表 1: cat_keyword (关键词表)
-- ============================================================
-- 表说明: 存储作者/编辑提供的自由关键词,支持规范化去重和频次统计
-- 记录数预估: 初始 300万 / 年增长 100万 / 5年规模 800万
-- 主要查询场景:
--   1. 按规范化关键词查询(去重,>1000次/天,高频)
--   2. 全文检索关键词(>500次/天,中频)
--   3. 按频次排序(热门关键词,100-500次/天,中频)
--   4. 按来源和语言筛选(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_keyword` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `term` VARCHAR(500) NOT NULL COMMENT '关键词原始形式',
    `source` VARCHAR(50) NULL DEFAULT NULL COMMENT '来源(枚举:author/editor/indexer/pubmed)',
    `language` VARCHAR(10) NULL DEFAULT NULL COMMENT '语言代码(ISO 639-1,如 en/zh)',
    `normalized_term` VARCHAR(255) NULL DEFAULT NULL COMMENT '规范化词形(小写+去标点+去空格,用于去重)',
    `frequency` INT UNSIGNED NULL DEFAULT 0 COMMENT '出现频次(被多少篇文献使用)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '元数据(扩展字段)',

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

    -- 普通索引
    INDEX `idx_normalized` (`normalized_term`) COMMENT '规范化词形索引,支持去重查询',
    INDEX `idx_frequency` (`frequency` DESC) COMMENT '频次索引,支持热门关键词排序',

    -- 复合索引
    INDEX `idx_source_lang` (`source`, `language`) COMMENT '来源+语言复合索引,支持按来源和语言筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='关键词表:存储自由关键词,支持规范化去重和频次统计';


-- ============================================================
-- 表 2: cat_publication_keyword (文献-关键词关联表)
-- ============================================================
-- 表说明: 存储文献的关键词标注,关联文献和关键词,支持主/副关键词标记
-- 记录数预估: 初始 1500万 / 年增长 1400万 / 5年规模 8500万
-- 主要查询场景:
--   1. 按 publication_id 查询某文献的所有关键词(>2000次/天,高频)
--   2. 按 keyword_id 查询某关键词的所有文献(>1000次/天,高频)
--   3. 筛选主要关键词(is_major=1)(>500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_keyword` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `keyword_id` BIGINT UNSIGNED NOT NULL COMMENT '关键词ID(外键:cat_keyword.id)',
    `is_major` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否主要关键词(0=副关键词,1=主要关键词)',
    `order_num` INT UNSIGNED NULL DEFAULT NULL COMMENT '顺序号(在同一文献内的排序)',
    `keyword_set` VARCHAR(50) NULL DEFAULT NULL COMMENT '关键词集(如 Author/Editor,区分不同来源)',

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

    -- 复合索引(核心查询)
    INDEX `idx_pub_keyword` (`publication_id`, `keyword_id`) COMMENT '文献+关键词复合索引,支持查询文献的关键词(<20ms)',
    INDEX `idx_keyword_pub` (`keyword_id`, `publication_id`) COMMENT '关键词+文献复合索引,支持查询关键词的文献(<50ms)',
    INDEX `idx_major` (`keyword_id`, `is_major`) COMMENT '关键词+主/副标记复合索引,筛选主要关键词文献'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='文献-关键词关联表:存储文献关键词标注,支持主/副关键词标记';


-- ============================================================
-- 全文索引（需要在表创建后单独执行）
-- ============================================================
-- 注意: 全文索引使用 ngram 解析器,支持中文分词(MySQL 5.7.6+)
-- ============================================================

-- cat_keyword 表的全文索引
CREATE FULLTEXT INDEX `ft_keyword_term` ON `cat_keyword` (`term`)
    WITH PARSER ngram
    COMMENT '关键词全文索引,支持中英文混合检索';
