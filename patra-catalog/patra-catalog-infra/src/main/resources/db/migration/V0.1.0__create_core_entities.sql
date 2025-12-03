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
-- 2. cat_venue_identifier (载体标识符表) - 依赖 cat_venue
-- 3. cat_venue_metrics (载体指标表) - 依赖 cat_venue
-- 4. cat_venue_instance (载体实例表) - 依赖 cat_venue
-- 5. cat_publication (出版物主表) - 依赖 cat_venue_instance, cat_venue
-- 6. cat_identifier (标识符表) - 依赖 cat_publication
-- 7. cat_author (作者表) - 无依赖（独立表）
-- 8. cat_abstract (摘要表) - 依赖 cat_publication
-- ============================================================

-- ============================================================
-- 执行说明
-- ============================================================
-- 1. 确保 MySQL 版本 >= 8.0（需要生成列支持）
-- 2. 按顺序执行表创建（考虑外键依赖）
-- 3. 全文索引需要在表创建后单独执行
-- 4. 建议在测试环境先验证，再在生产环境执行
-- 5. 执行前备份现有数据（如果有）
-- ============================================================


-- ============================================================
-- 表 1: cat_venue (出版载体表)
-- ============================================================
-- 表说明: 管理期刊/仓库/会议/电子书平台等出版载体,支持OpenAlex和PubMed数据
-- 记录数预估: 初始 25万 / 年增长 2万 / 5年规模 35万
-- 主要查询场景:
--   1. 按 openalex_id 精确查询(>2000次/天,高频-数据同步)
--   2. 按 issn_l 精确查询(>1500次/天,高频-文献关联)
--   3. 按载体类型筛选(500-1000次/天,中频)
--   4. 按名称模糊查询(100-500次/天,中频)
--   5. 按OA状态筛选(<500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_venue` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 基本信息
    -- ========================================
    `venue_type` VARCHAR(32) NOT NULL COMMENT '载体类型:JOURNAL/REPOSITORY/CONFERENCE/EBOOK_PLATFORM/BOOK_SERIES/METADATA/OTHER',
    `display_name` VARCHAR(500) NOT NULL COMMENT '载体显示名称(主名称)',
    `abbreviated_title` VARCHAR(200) NULL DEFAULT NULL COMMENT '缩写标题(来自ISSN中心或ISO)',
    `alternate_titles` JSON NULL DEFAULT NULL COMMENT '替代名称列表(JSON数组,包含缩写和翻译名)',
    `homepage_url` TEXT NULL DEFAULT NULL COMMENT '载体主页URL',

    -- ========================================
    -- 冗余标识符 (高频查询优化)
    -- ========================================
    `openalex_id` VARCHAR(50) NULL DEFAULT NULL COMMENT 'OpenAlex ID(冗余,格式:S1234567890)',
    `issn_l` VARCHAR(20) NULL DEFAULT NULL COMMENT 'Linking ISSN(冗余,关联纸质版和电子版)',

    -- ========================================
    -- 宿主机构信息
    -- ========================================
    `host_organization_id` VARCHAR(50) NULL DEFAULT NULL COMMENT '宿主机构OpenAlex ID',
    `host_organization_name` VARCHAR(500) NULL DEFAULT NULL COMMENT '宿主机构名称(出版商/机构)',
    `host_organization_lineage` JSON NULL DEFAULT NULL COMMENT '机构所有权链(JSON数组,从直接母公司到顶层)',

    -- ========================================
    -- 地理信息
    -- ========================================
    `country_code` VARCHAR(10) NULL DEFAULT NULL COMMENT '国家代码(ISO 3166-1 alpha-2,如US/CN)',

    -- ========================================
    -- OA 状态和质量指标
    -- ========================================
    `is_oa` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否为开放获取来源(0=否,1=是)',
    `is_in_doaj` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否收录于DOAJ(0=否,1=是)',
    `is_core` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否为核心来源-CWTS标准(0=否,1=是)',

    -- ========================================
    -- 统计指标 (当前快照)
    -- ========================================
    `works_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '托管作品数量',
    `cited_by_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '被引用次数总计',
    `h_index` INT UNSIGNED NULL DEFAULT NULL COMMENT 'H-Index指数',
    `i10_index` INT UNSIGNED NULL DEFAULT NULL COMMENT 'i10-Index指数(被引≥10次的论文数)',
    `two_year_mean_citedness` DECIMAL(10,4) NULL DEFAULT NULL COMMENT '两年平均被引率(类似影响因子)',

    -- ========================================
    -- APC 信息 (文章处理费)
    -- ========================================
    `apc_usd` INT UNSIGNED NULL DEFAULT NULL COMMENT '文章处理费(美元)',
    `apc_prices` JSON NULL DEFAULT NULL COMMENT 'APC费用列表(JSON数组,含不同货币价格)',

    -- ========================================
    -- 关联学会
    -- ========================================
    `societies` JSON NULL DEFAULT NULL COMMENT '关联学术组织(JSON数组,含url和organization)',

    -- ========================================
    -- 数据来源与同步
    -- ========================================
    `provenance_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '数据来源代码:OPENALEX/PUBMED/CROSSREF/MANUAL',
    `source_created_date` DATE NULL DEFAULT NULL COMMENT '来源系统创建日期',
    `source_updated_date` DATE NULL DEFAULT NULL COMMENT '来源系统更新日期',
    `last_synced_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后同步时间(UTC,微秒精度)',

    -- ========================================
    -- 扩展数据
    -- ========================================
    `ext_data` JSON NULL DEFAULT NULL COMMENT '扩展数据(灵活存储来源特定字段)',

    -- ========================================
    -- 审计字段
    -- ========================================
    `record_remarks` JSON NULL DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标志',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引 (冗余字段)
    UNIQUE INDEX `uk_openalex_id` (`openalex_id`) COMMENT 'OpenAlex ID唯一索引',
    UNIQUE INDEX `uk_issn_l` (`issn_l`) COMMENT 'ISSN-L唯一索引',

    -- 普通索引
    INDEX `idx_venue_type` (`venue_type`) COMMENT '载体类型索引',
    INDEX `idx_display_name` (`display_name`(100)) COMMENT '名称前缀索引',
    INDEX `idx_host_org` (`host_organization_id`) COMMENT '宿主机构索引',
    INDEX `idx_country` (`country_code`) COMMENT '国家代码索引',
    INDEX `idx_is_oa` (`is_oa`) COMMENT 'OA状态索引',
    INDEX `idx_is_in_doaj` (`is_in_doaj`) COMMENT 'DOAJ收录索引',
    INDEX `idx_provenance` (`provenance_code`) COMMENT '数据来源索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='出版载体表:管理期刊/仓库/会议/电子书平台等出版载体,支持OpenAlex和PubMed数据';

-- 全文索引
CREATE FULLTEXT INDEX `ft_display_name` ON `cat_venue` (`display_name`)
    WITH PARSER ngram
    COMMENT '名称全文索引,支持中英文检索';


-- ============================================================
-- 表 2: cat_venue_identifier (载体标识符表)
-- ============================================================
-- 表说明: 存储载体的各类标识符,支持一对多关系(如多个ISSN)
-- 记录数预估: 初始 100万 / 年增长 10万 / 5年规模 150万 (平均每个venue 4个标识符)
-- 主要查询场景:
--   1. 按 venue_id 查询所有标识符(>1000次/天,高频)
--   2. 按 identifier_type + identifier_value 反查venue(>2000次/天,高频)
--   3. 按 identifier_type 筛选(<500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_venue_identifier` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',

    -- ========================================
    -- 标识符信息
    -- ========================================
    `identifier_type` VARCHAR(32) NOT NULL COMMENT '标识符类型:OPENALEX/ISSN/ISSN_L/ISBN/NLM/MAG/FATCAT/WIKIDATA',
    `identifier_value` VARCHAR(255) NOT NULL COMMENT '标识符值',
    `is_primary` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否首选标识符(同类型中,0=否,1=是)',

    -- ========================================
    -- 审计字段
    -- ========================================
    `record_remarks` JSON NULL DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标志',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引 (防止重复)
    UNIQUE INDEX `uk_venue_type_value` (`venue_id`, `identifier_type`, `identifier_value`) COMMENT '载体+类型+值唯一索引',

    -- 复合索引 (反向查询优化)
    INDEX `idx_type_value` (`identifier_type`, `identifier_value`) COMMENT '类型+值索引,支持按标识符反查载体',

    -- 普通索引
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引,支持查询载体的所有标识符'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='载体标识符表:存储载体的各类标识符,支持一对多关系';


-- ============================================================
-- 表 3: cat_venue_metrics (载体指标表)
-- ============================================================
-- 表说明: 存储载体年度统计数据,支持时序分析
-- 记录数预估: 初始 250万 / 年增长 30万 / 5年规模 400万 (平均每个venue 10年数据)
-- 主要查询场景:
--   1. 按 venue_id 查询历年数据(>500次/天,中频)
--   2. 按年份统计所有载体数据(<200次/天,低频)
--   3. 按发文量/被引量排序(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_venue_metrics` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',
    `year` SMALLINT NOT NULL COMMENT '统计年份(1900-2100)',

    -- ========================================
    -- 统计指标
    -- ========================================
    `works_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '该年发表作品数量',
    `cited_by_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '该年被引用次数',
    `oa_works_count` INT UNSIGNED NULL DEFAULT NULL COMMENT '该年OA作品数量(可选)',

    -- ========================================
    -- 审计字段
    -- ========================================
    `record_remarks` JSON NULL DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标志',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_venue_year` (`venue_id`, `year`) COMMENT '载体+年份唯一索引,保证每年只有一条记录',

    -- 普通索引
    INDEX `idx_year` (`year`) COMMENT '年份索引,支持按年份统计',
    INDEX `idx_works_count` (`works_count`) COMMENT '发文量索引,支持排序',
    INDEX `idx_cited_by_count` (`cited_by_count`) COMMENT '被引量索引,支持排序'

    -- 年份约束由领域层 VenueMetrics 实体负责校验

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='载体指标表:存储载体年度统计数据,支持时序分析';


-- ============================================================
-- 表 4: cat_venue_instance (载体实例表)
-- ============================================================
-- 表说明: 存储载体的具体实例(期刊的卷期、书籍的版次、会议的届次)
-- 注意: 本表保持原有设计,通过 venue_id 关联 cat_venue
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
    `conference_start_date` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '会议开始时间(会议专用,UTC微秒精度)',
    `conference_end_date` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '会议结束时间(会议专用,UTC微秒精度)',
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
    INDEX `idx_venue_volume_issue` (`venue_id`, `volume`, `issue`) COMMENT '载体+卷+期复合索引,支持精确定位某期刊某卷某期'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='载体实例表:存储期刊卷期/书籍版次/会议届次等具体实例';


-- ============================================================
-- 表 5: cat_publication (出版物主表)
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
    INDEX `idx_deleted_updated` (`deleted`, `updated_at`) COMMENT '软删除和更新时间复合索引,支持查询"未删除的最新更新记录"'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='出版物主表:存储医学文献核心元数据,系统中心表';


-- ============================================================
-- 表 6: cat_identifier (标识符表)
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
-- 表 7: cat_author (作者表)
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
-- 表 8: cat_abstract (摘要表)
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
    UNIQUE INDEX `uk_publication` (`publication_id`) COMMENT '出版物ID唯一索引,保证一对一关系,支持高频查询摘要(<10ms)'

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


