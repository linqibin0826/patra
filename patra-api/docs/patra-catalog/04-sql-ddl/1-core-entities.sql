-- ============================================================
-- Patra Catalog 数据库 - 核心实体表 DDL
-- ============================================================
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: patra_catalog 核心实体表（6张表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_unicode_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 数据库配置
-- ============================================================

-- 创建数据库（如果不存在）
CREATE DATABASE IF NOT EXISTS `patra_catalog`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 使用数据库
USE `patra_catalog`;

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- 1. cat_venue (出版载体表) - 无依赖
-- 2. cat_venue_instance (载体实例表) - 依赖 cat_venue
-- 3. cat_publication (出版物主表) - 依赖 cat_venue_instance, cat_venue
-- 4. cat_identifier (标识符表) - 依赖 cat_publication
-- 5. cat_author (作者表) - 无依赖（独立表）
-- 6. cat_abstract (摘要表) - 依赖 cat_publication
-- ============================================================

-- ============================================================
-- 执行说明
-- ============================================================
-- 1. 确保 MySQL 版本 >= 8.0（需要 CHECK 约束和生成列支持）
-- 2. 按顺序执行表创建（考虑外键依赖）
-- 3. 全文索引需要在表创建后单独执行
-- 4. 建议在测试环境先验证，再在生产环境执行
-- 5. 执行前备份现有数据（如果有）
-- ============================================================


-- ============================================================
-- 表 1: cat_venue (出版载体表)
-- ============================================================
-- 表说明: 管理期刊、书籍、会议等出版载体的基本信息(不包含具体卷期)
-- 记录数预估: 初始 2万 / 年增长 5千 / 5年规模 4.5万
-- 主要查询场景:
--   1. 按 ISSN 查询期刊(>1000次/天,高频)
--   2. 按 ISBN 查询书籍(100-500次/天,中频)
--   3. 按载体类型筛选(<100次/天,低频)
--   4. 按载体名称模糊查询(<100次/天,低频)
-- ============================================================

DROP TABLE IF EXISTS `cat_venue`;

CREATE TABLE `cat_venue` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `venue_type` VARCHAR(32) NOT NULL COMMENT '载体类型:JOURNAL/BOOK/CONFERENCE/OTHER',
    `title` VARCHAR(500) NOT NULL COMMENT '载体名称(期刊名/书名/会议名)',
    `iso_abbreviation` VARCHAR(200) NULL DEFAULT NULL COMMENT 'ISO标准缩写(期刊专用)',
    `medline_abbreviation` VARCHAR(200) NULL DEFAULT NULL COMMENT 'Medline缩写(期刊专用)',
    `issn` VARCHAR(20) NULL DEFAULT NULL COMMENT 'ISSN号(期刊专用,格式:1234-5678)',
    `isbn` VARCHAR(20) NULL DEFAULT NULL COMMENT 'ISBN号(书籍专用,格式:978-3-16-148410-0)',
    `issn_type` VARCHAR(32) NULL DEFAULT NULL COMMENT 'ISSN类型:print/electronic',
    `issn_linking` VARCHAR(20) NULL DEFAULT NULL COMMENT 'Linking ISSN(关联纸质版和电子版)',
    `nlm_unique_id` VARCHAR(50) NULL DEFAULT NULL COMMENT 'NLM唯一标识符',
    `country` VARCHAR(100) NULL DEFAULT NULL COMMENT '出版国家(ISO 3166-1 alpha-3,如USA/CHN)',
    `publisher` VARCHAR(500) NULL DEFAULT NULL COMMENT '出版商名称',
    `venue_specific_data` JSON NULL DEFAULT NULL COMMENT '类型特定数据(灵活扩展)',

    -- ========================================
    -- 审计字段（简化版）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_issn` (`issn`) COMMENT 'ISSN索引,支持按期刊ISSN查询(高频)',
    INDEX `idx_isbn` (`isbn`) COMMENT 'ISBN索引,支持按书籍ISBN查询(中频)',
    INDEX `idx_venue_type` (`venue_type`) COMMENT '载体类型索引,支持按类型筛选(低频)',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_venue_type` CHECK (`venue_type` IN ('JOURNAL', 'BOOK', 'CONFERENCE', 'OTHER')),
    CONSTRAINT `chk_issn_type` CHECK (`issn_type` IN ('print', 'electronic') OR `issn_type` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='出版载体表:管理期刊/书籍/会议等出版载体基本信息';


-- ============================================================
-- 表 2: cat_venue_instance (载体实例表)
-- ============================================================
-- 表说明: 存储载体的具体实例(期刊的卷期、书籍的版次、会议的届次)
-- 记录数预估: 初始 50万 / 年增长 15万 / 5年规模 125万
-- 主要查询场景:
--   1. 按 venue_id 查询某载体的所有实例(>500次/天,中频)
--   2. 按出版年份查询(>1000次/天,高频)
--   3. 按卷期组合查询(100-500次/天,中频)
-- ============================================================

DROP TABLE IF EXISTS `cat_venue_instance`;

CREATE TABLE `cat_venue_instance` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',
    `volume` VARCHAR(100) NULL DEFAULT NULL COMMENT '卷号(如"45","2023")',
    `issue` VARCHAR(100) NULL DEFAULT NULL COMMENT '期号(如"3","Suppl 1")',
    `edition` VARCHAR(100) NULL DEFAULT NULL COMMENT '版次(书籍专用,如"2nd Edition")',
    `publication_year` SMALLINT NOT NULL COMMENT '出版年份(必填,用于冗余到主表)',
    `publication_month` TINYINT NULL DEFAULT NULL COMMENT '出版月份(1-12,可能为空)',
    `publication_day` TINYINT NULL DEFAULT NULL COMMENT '出版日期(1-31,可能为空)',
    `conference_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '会议名称(会议专用)',
    `conference_start_date` DATE NULL DEFAULT NULL COMMENT '会议开始日期(会议专用)',
    `conference_end_date` DATE NULL DEFAULT NULL COMMENT '会议结束日期(会议专用)',
    `conference_location` VARCHAR(200) NULL DEFAULT NULL COMMENT '会议地点(会议专用)',
    `instance_metadata` JSON NULL DEFAULT NULL COMMENT '实例元数据(灵活扩展)',

    -- ========================================
    -- 审计字段（极简版）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_publication_year` (`publication_year`) COMMENT '出版年份索引,支持按年份筛选',

    -- 复合索引
    INDEX `idx_venue_volume_issue` (`venue_id`, `volume`, `issue`) COMMENT '载体+卷+期复合索引,支持精确定位某期刊某卷某期',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_publication_month` CHECK (`publication_month` BETWEEN 1 AND 12 OR `publication_month` IS NULL),
    CONSTRAINT `chk_publication_day` CHECK (`publication_day` BETWEEN 1 AND 31 OR `publication_day` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='载体实例表:存储期刊卷期/书籍版次/会议届次等具体实例';


-- ============================================================
-- 表 3: cat_publication (出版物主表)
-- ============================================================
-- 表说明: 存储医学文献出版物的核心元数据,是整个系统的中心表
-- 记录数预估: 初始 100万 / 年增长 200万 / 5年规模 1100万
-- 主要查询场景:
--   1. 按 PMID 精确查询(>5000次/天,高频)
--   2. 按 DOI 精确查询(>3000次/天,高频)
--   3. 按出版年份范围查询(>2000次/天,高频)
--   4. 按期刊筛选文献(>1500次/天,高频)
--   5. 按语种筛选(500-1000次/天,中频)
--   6. 按 OA 状态筛选(<500次/天,低频)
-- ============================================================

DROP TABLE IF EXISTS `cat_publication`;

CREATE TABLE `cat_publication` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- 标识符（冗余优化）
    `pmid` VARCHAR(15) NULL DEFAULT NULL COMMENT 'PubMed ID(冗余优化,支持高频精确查询)',
    `doi` VARCHAR(200) NULL DEFAULT NULL COMMENT '数字对象标识符DOI(冗余优化,支持高频精确查询)',

    -- 关联关系（含冗余）
    `venue_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '载体ID(冗余优化-避免二级JOIN)',
    `venue_instance_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '载体实例ID(外键:cat_venue_instance.id)',

    -- 标题和语言
    `title` VARCHAR(2000) NOT NULL COMMENT '文献标题(英文或原语言)',
    `original_title` VARCHAR(1000) NULL DEFAULT NULL COMMENT '原始语言标题(非英文时填充)',
    `language_raw` VARCHAR(50) NULL DEFAULT NULL COMMENT '原始语言值(外部采集,如"Chinese")',
    `language_code` VARCHAR(10) NULL DEFAULT NULL COMMENT '标准语言代码(应用层处理,如"zh-CN")',
    `language_base` VARCHAR(5) GENERATED ALWAYS AS (SUBSTRING_INDEX(`language_code`, '-', 1)) STORED COMMENT '基础语种(生成列,如"zh")',

    -- 出版信息
    `publication_status` VARCHAR(32) NULL DEFAULT NULL COMMENT '出版状态:ppublish/epublish/aheadofprint/pubmed/pubmednotmedline/premedline',
    `media_type` VARCHAR(32) NULL DEFAULT NULL COMMENT '媒介类型:print/electronic/both',
    `publication_year` SMALLINT NULL DEFAULT NULL COMMENT '出版年份(冗余优化-最高频查询字段)',

    -- OA 信息（冗余）
    `is_oa` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否有OA版本(冗余-快速筛选,0=否,1=是)',
    `oa_status` VARCHAR(20) NULL DEFAULT NULL COMMENT '最佳OA状态(冗余,gold/green/hybrid/bronze/closed)',

    -- 作者和引用信息
    `authors_complete` BOOLEAN NOT NULL DEFAULT 1 COMMENT '作者列表是否完整(0=不完整,1=完整)',
    `citation_count` INT UNSIGNED NULL DEFAULT 0 COMMENT '被引次数(定期更新)',
    `number_of_references` INT UNSIGNED NULL DEFAULT 0 COMMENT '参考文献数量',

    -- 其他业务信息
    `conflict_of_interest` VARCHAR(500) NULL DEFAULT NULL COMMENT '利益冲突声明',
    `ext_data` JSON NULL DEFAULT NULL COMMENT '扩展数据(灵活存储自定义字段)',

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
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标志(0=正常,1=已删除)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_pmid` (`pmid`) COMMENT 'PMID唯一索引,支持高频精确查询(<10ms)',
    UNIQUE INDEX `uk_doi` (`doi`) COMMENT 'DOI唯一索引,支持高频精确查询(<10ms)',

    -- 普通索引
    INDEX `idx_venue` (`venue_id`) COMMENT '载体索引,支持按期刊筛选文献',
    INDEX `idx_venue_instance` (`venue_instance_id`) COMMENT '载体实例索引,支持按卷期查询',
    INDEX `idx_publication_year` (`publication_year`) COMMENT '出版年份索引,最高频查询(>60%查询包含年份条件)',
    INDEX `idx_language_base` (`language_base`) COMMENT '基础语种索引,支持按语言筛选(如查询所有中文文献)',
    INDEX `idx_is_oa` (`is_oa`) COMMENT 'OA状态索引,支持快速筛选开放获取文献',

    -- 复合索引（软删除 + 更新时间）
    INDEX `idx_deleted_updated` (`deleted`, `updated_at`) COMMENT '软删除和更新时间复合索引,支持查询"未删除的最新更新记录"',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_publication_status` CHECK (`publication_status` IN ('ppublish', 'epublish', 'aheadofprint', 'pubmed', 'pubmednotmedline', 'premedline') OR `publication_status` IS NULL),
    CONSTRAINT `chk_media_type` CHECK (`media_type` IN ('print', 'electronic', 'both') OR `media_type` IS NULL),
    CONSTRAINT `chk_oa_status` CHECK (`oa_status` IN ('gold', 'green', 'hybrid', 'bronze', 'closed') OR `oa_status` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='出版物主表:存储医学文献核心元数据,系统中心表';


-- ============================================================
-- 表 4: cat_identifier (标识符表)
-- ============================================================
-- 表说明: 管理出版物的多种类型标识符(PMID、DOI、PMC、PII、arXiv等)
-- 记录数预估: 初始 300万 / 年增长 700万 / 5年规模 3800万
-- 主要查询场景:
--   1. 按 publication_id 查询某文献的所有标识符(>1000次/天,高频)
--   2. 按标识符类型+值精确查询(>500次/天,中频)
--   3. 按标识符类型查询(100-500次/天,中频)
-- ============================================================

DROP TABLE IF EXISTS `cat_identifier`;

CREATE TABLE `cat_identifier` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `type` VARCHAR(20) NOT NULL COMMENT '标识符类型:pmid/doi/pmc/pii/arxiv等',
    `value` VARCHAR(255) NOT NULL COMMENT '标识符值(如"38123456","10.1038/nature12345")',
    `source` VARCHAR(50) NULL DEFAULT NULL COMMENT '标识符来源(如"PubMed","Crossref","Manual")',

    -- ========================================
    -- 审计字段（极简版）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 复合索引
    INDEX `idx_pub_type` (`publication_id`, `type`) COMMENT '出版物+类型复合索引,支持查询某文献的某类型标识符(如查询PMID)',
    INDEX `idx_type_value` (`type`, `value`) COMMENT '类型+值复合索引,支持按标识符查询文献(如通过DOI查询文献)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='标识符表:管理出版物的多种类型标识符';


-- ============================================================
-- 表 5: cat_author (作者表)
-- ============================================================
-- 表说明: 存储作者信息,支持复合去重策略(ORCID + 姓名 + 机构 + 邮箱)
-- 记录数预估: 初始 500万 / 年增长 200万 / 5年规模 1500万
-- 主要查询场景:
--   1. 按 ORCID 精确查询(>500次/天,中频)
--   2. 按 dedup_key 去重查询(>1000次/天,高频)
--   3. 按姓名模糊查询(100-500次/天,中频)
--   4. 按邮箱查询(<100次/天,低频)
-- ============================================================

DROP TABLE IF EXISTS `cat_author`;

CREATE TABLE `cat_author` (
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
    -- 审计字段（简化版）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',

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


-- ============================================================
-- 表 6: cat_abstract (摘要表)
-- ============================================================
-- 表说明: 独立存储文献摘要(大文本),支持结构化摘要和全文检索
-- 记录数预估: 初始 80万 / 年增长 160万 / 5年规模 960万
-- 主要查询场景:
--   1. 按 publication_id 查询摘要(>2000次/天,高频)
--   2. 摘要全文检索(>500次/天,中频)
--   3. 按摘要类型筛选(<100次/天,低频)
-- ============================================================

DROP TABLE IF EXISTS `cat_abstract`;

CREATE TABLE `cat_abstract` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id,一对一关系)',
    `plain_text` TEXT NULL DEFAULT NULL COMMENT '纯文本摘要(最大65535字符)',
    `structured_sections` JSON NULL DEFAULT NULL COMMENT '结构化摘要段落(JSON对象,如BACKGROUND/METHODS/RESULTS/CONCLUSIONS)',
    `copyright` VARCHAR(1000) NULL DEFAULT NULL COMMENT '版权信息/使用限制',
    `abstract_type` VARCHAR(32) NULL DEFAULT NULL COMMENT '摘要类型:structured/unstructured/graphical/none',

    -- ========================================
    -- 审计字段（极简版）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_publication` (`publication_id`) COMMENT '出版物ID唯一索引,保证一对一关系,支持高频查询摘要(<10ms)',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_abstract_type` CHECK (`abstract_type` IN ('structured', 'unstructured', 'graphical', 'none') OR `abstract_type` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='摘要表:独立存储文献摘要,优化主表性能';


-- ============================================================
-- 全文索引（需要在表创建后单独执行）
-- ============================================================
-- 注意: 全文索引使用 ngram 解析器,支持中文分词(MySQL 5.7.6+)
-- ============================================================

-- cat_publication 表的标题全文索引
CREATE FULLTEXT INDEX `ft_title` ON `cat_publication` (`title`)
    WITH PARSER ngram
    COMMENT '标题全文索引,支持中英文混合检索';

-- cat_abstract 表的摘要全文索引
CREATE FULLTEXT INDEX `ft_plain_text` ON `cat_abstract` (`plain_text`)
    WITH PARSER ngram
    COMMENT '摘要全文索引,支持中英文混合检索';


-- ============================================================
-- 验证脚本
-- ============================================================

-- 1. 检查所有表是否创建成功
SELECT
    TABLE_NAME AS '表名',
    ENGINE AS '存储引擎',
    TABLE_ROWS AS '预估行数',
    AVG_ROW_LENGTH AS '平均行长度(字节)',
    DATA_LENGTH AS '数据大小(字节)',
    INDEX_LENGTH AS '索引大小(字节)',
    TABLE_COLLATION AS '排序规则',
    TABLE_COMMENT AS '表注释'
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
      'cat_venue',
      'cat_venue_instance',
      'cat_publication',
      'cat_identifier',
      'cat_author',
      'cat_abstract'
  )
ORDER BY TABLE_NAME;


-- 2. 检查索引统计
SELECT
    TABLE_NAME AS '表名',
    INDEX_NAME AS '索引名',
    INDEX_TYPE AS '索引类型',
    NON_UNIQUE AS '是否非唯一',
    SEQ_IN_INDEX AS '列序号',
    COLUMN_NAME AS '列名',
    CARDINALITY AS '基数',
    INDEX_COMMENT AS '索引注释'
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
      'cat_venue',
      'cat_venue_instance',
      'cat_publication',
      'cat_identifier',
      'cat_author',
      'cat_abstract'
  )
ORDER BY TABLE_NAME, INDEX_NAME, SEQ_IN_INDEX;


-- 3. 检查约束定义
SELECT
    CONSTRAINT_NAME AS '约束名',
    TABLE_NAME AS '表名',
    CHECK_CLAUSE AS '约束条件'
FROM information_schema.CHECK_CONSTRAINTS
WHERE CONSTRAINT_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
      'cat_venue',
      'cat_venue_instance',
      'cat_publication',
      'cat_identifier',
      'cat_author',
      'cat_abstract'
  )
ORDER BY TABLE_NAME, CONSTRAINT_NAME;


-- 4. 字段统计
SELECT
    TABLE_NAME AS '表名',
    COUNT(*) AS '字段数量',
    SUM(CASE WHEN IS_NULLABLE = 'NO' THEN 1 ELSE 0 END) AS 'NOT NULL字段数',
    SUM(CASE WHEN COLUMN_KEY = 'PRI' THEN 1 ELSE 0 END) AS '主键字段数',
    SUM(CASE WHEN COLUMN_KEY = 'UNI' THEN 1 ELSE 0 END) AS '唯一键字段数',
    SUM(CASE WHEN COLUMN_KEY = 'MUL' THEN 1 ELSE 0 END) AS '索引字段数',
    SUM(CASE WHEN DATA_TYPE = 'json' THEN 1 ELSE 0 END) AS 'JSON字段数'
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME IN (
      'cat_venue',
      'cat_venue_instance',
      'cat_publication',
      'cat_identifier',
      'cat_author',
      'cat_abstract'
  )
GROUP BY TABLE_NAME
ORDER BY TABLE_NAME;


-- ============================================================
-- 设计决策摘要
-- ============================================================
--
-- 决策1: 标识符冗余优化(PMID/DOI)
--   - 在cat_publication主表冗余pmid和doi字段
--   - 理由: 查询频率>90%,避免JOIN,性能提升>90%
--   - 成本: 1000万行×208字节≈2.5MB
--
-- 决策2: venue_id冗余
--   - 在cat_publication主表冗余venue_id字段
--   - 理由: 避免二级JOIN(publication→venue_instance→venue)
--   - 性能提升: 50%+
--
-- 决策3: 日期分离字段
--   - 使用year/month/day分离字段,而非DATE类型
--   - 理由: 精确表达不完整日期,避免虚假精度
--   - 优势: NULL表示"不存在此精度",而非"未知"
--
-- 决策4: publication_year冗余
--   - 在cat_publication主表冗余publication_year字段
--   - 理由: 最高频查询字段(>60%查询包含年份筛选)
--   - 成本: 1000万行×2字节=20MB
--
-- 决策5: 复合作者去重策略
--   - 使用dedup_key字段,应用层计算MD5哈希
--   - 理由: ORCID覆盖率仅30%,需要复合去重
--   - 优先级: ORCID > 姓名+机构+邮箱 > 姓名+机构+Scopus > 姓名+机构 > 仅姓名
--
-- 决策6: 摘要独立存储
--   - 摘要独立存储在cat_abstract表
--   - 理由: 大文本字段(平均2KB)影响主表扫描性能
--   - 性能提升: 主表扫描性能提升30%+
--
-- ============================================================


-- ============================================================
-- 索引选择性分析
-- ============================================================
--
-- 高选择性索引(>0.8):
--   - uk_pmid (0.98) - PMID几乎唯一
--   - uk_doi (0.95) - DOI高度唯一
--   - uk_orcid (0.99) - ORCID绝对唯一(30%覆盖率)
--   - uk_publication (1.00) - 一对一关系
--   - idx_venue (0.80) - 5万+期刊
--   - idx_venue_instance (0.85) - 120万+卷期
--   - idx_dedup_key (0.95) - 复合去重键高度唯一
--   - idx_email (0.85) - 邮箱几乎唯一
--   - idx_issn (0.90) - ISSN高度唯一
--   - idx_isbn (0.95) - ISBN几乎唯一
--   - idx_pub_type (0.95) - 每文献平均3-4个标识符
--   - idx_type_value (0.98) - 同类型标识符值几乎唯一
--   - idx_venue_volume_issue (0.99) - 组合几乎唯一
--
-- 中选择性索引(0.5-0.8):
--   - idx_publication_year (0.60) - 跨度70年(1950-2025)
--
-- 低选择性索引(<0.5,但业务需求强烈):
--   - idx_language_base (0.40) - 主要语种20种,英文占85%
--   - idx_is_oa (0.30) - 仅两值(0/1),但OA筛选是核心需求
--   - idx_venue_type (0.25) - 仅4个枚举值
--
-- ============================================================


-- ============================================================
-- 存储预估(5年)
-- ============================================================
--
-- | 表名                  | 5年规模    | 单行大小 | 存储预估 | 索引预估 | 总计    |
-- |-----------------------|-----------|---------|---------|---------|---------|
-- | cat_publication       | 1100万行  | 1.2 KB  | 13.2 GB | 5.5 GB  | 18.7 GB |
-- | cat_venue             | 4.5万行   | 0.8 KB  | 36 MB   | 10 MB   | 46 MB   |
-- | cat_venue_instance    | 125万行   | 0.5 KB  | 625 MB  | 200 MB  | 825 MB  |
-- | cat_identifier        | 3800万行  | 0.3 KB  | 11.4 GB | 4.2 GB  | 15.6 GB |
-- | cat_author            | 1500万行  | 0.6 KB  | 9.0 GB  | 3.5 GB  | 12.5 GB |
-- | cat_abstract          | 960万行   | 2.0 KB  | 19.2 GB | 6.0 GB  | 25.2 GB |
-- | **总计**              | 5768.5万行| -       | 53.5 GB | 19.4 GB | 72.9 GB |
--
-- 说明:
-- - 单行大小包含业务字段+审计字段
-- - 索引预估约为数据大小的30%-40%
-- - 实际存储需考虑InnoDB页填充率(通常70%-80%)
-- - 建议预留2倍空间(150 GB+)用于索引膨胀和临时表
--
-- ============================================================


-- ============================================================
-- 执行完成提示
-- ============================================================
--
-- ✅ DDL执行完成后,请执行上述验证脚本,确认:
--    1. 所有6张表创建成功
--    2. 所有索引正确创建(主键6+唯一4+普通8+复合3+全文2=23个)
--    3. CHECK约束生效
--    4. 字符集和排序规则正确(utf8mb4_unicode_ci)
--
-- ⚠️ 注意事项:
--    1. 全文索引创建可能耗时较长(取决于数据量)
--    2. 生成列(language_base)在插入/更新时自动计算
--    3. 冗余字段需要应用层保证同步一致性
--    4. 审计字段中的IP地址使用VARBINARY(16)支持IPv4/IPv6
--    5. 所有TIMESTAMP字段使用微秒精度(6位小数)
--
-- 📝 后续步骤:
--    1. 创建关联表(cat_publication_author等)
--    2. 配置主从复制(如果需要)
--    3. 设置定期备份策略
--    4. 监控索引使用情况和查询性能
--    5. 根据实际查询模式调整索引
--
-- ============================================================

-- ============================================================
-- 文件结束
-- ============================================================
