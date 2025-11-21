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
-- 表清单与依赖关系
-- ============================================================
-- 说明：数据库 patra_catalog 应在 Flyway 执行前已创建
--      （由 DBA 手动创建或通过部署脚本自动创建）
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


CREATE TABLE IF NOT EXISTS `cat_venue` (
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


CREATE TABLE IF NOT EXISTS `cat_venue_instance` (
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


CREATE TABLE IF NOT EXISTS `cat_publication` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- 数据来源
    `provenance_code` VARCHAR(32) NOT NULL COMMENT '数据来源代码:PUBMED/PMC/EPMC/OPENALEX/CROSSREF/UNPAYWALL/SEMANTIC_SCHOLAR等',

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

    -- 同步信息
    `last_synced_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后同步时间(UTC,微秒精度,用于增量更新)',

    -- ========================================
    -- 审计字段（完整版）
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
    INDEX `idx_provenance_code` (`provenance_code`) COMMENT '数据来源索引,支持按来源筛选文献',
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
    CONSTRAINT `chk_publication_oa_status` CHECK (`oa_status` IN ('gold', 'green', 'hybrid', 'bronze', 'closed') OR `oa_status` IS NULL)

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


CREATE TABLE IF NOT EXISTS `cat_identifier` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `type` VARCHAR(20) NOT NULL COMMENT '标识符类型:pmid/doi/pmc/pii/arxiv等',
    `value` VARCHAR(255) NOT NULL COMMENT '标识符值(如"38123456","10.1038/nature12345")',
    `source` VARCHAR(50) NULL DEFAULT NULL COMMENT '标识符来源(如"PubMed","Crossref","Manual")',

    -- ========================================
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


CREATE TABLE IF NOT EXISTS `cat_abstract` (
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


