-- ============================================================
-- Patra Catalog 数据库 - Venue 聚合表 DDL
-- ============================================================
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: Venue 聚合根及其关联实体（6张表）
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
-- 1. cat_venue (出版载体表) - 聚合根，无依赖
-- 2. cat_venue_identifier (载体标识符表) - 依赖 cat_venue
-- 3. cat_venue_publication_stats (发文统计表) - 依赖 cat_venue
-- 4. cat_venue_rating (载体评级表) - 依赖 cat_venue
-- 5. cat_venue_source_data (数据源表) - 依赖 cat_venue
-- 6. cat_venue_instance (载体实例表) - 依赖 cat_venue
-- 7. cat_venue_mesh (期刊MeSH主题表) - 依赖 cat_venue [Serfile扩展]
-- 8. cat_venue_relation (期刊关联表) - 依赖 cat_venue [Serfile扩展]
-- 9. cat_venue_indexing_history (索引历史表) - 依赖 cat_venue [Serfile扩展]
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
    `nlm_id` VARCHAR(20) NULL DEFAULT NULL COMMENT 'NLM唯一标识符(冗余,PubMed Catalog)',
    `doi_prefix` VARCHAR(50) NULL DEFAULT NULL COMMENT 'DOI前缀(来自Crossref)',
    `coden` VARCHAR(10) NULL DEFAULT NULL COMMENT 'CODEN编码(6字符标识符,来自Serfile)',

    -- ========================================
    -- 出版属性 (来自 Serfile)
    -- ========================================
    `frequency` VARCHAR(50) NULL DEFAULT NULL COMMENT '出版频率(Weekly/Monthly/Quarterly等,来自Serfile)',
    `primary_language` VARCHAR(10) NULL DEFAULT NULL COMMENT '主要语言代码(ISO 639-3,冗余字段)',
    `languages` JSON NULL DEFAULT NULL COMMENT '期刊语言(JSON对象,含primary和summary数组)',

    -- ========================================
    -- 出版商信息
    -- ========================================
    `publisher` VARCHAR(500) NULL DEFAULT NULL COMMENT '出版商名称(来自Crossref/DOAJ)',
    `host_organization_id` VARCHAR(50) NULL DEFAULT NULL COMMENT '宿主机构OpenAlex ID',
    `host_organization_name` VARCHAR(500) NULL DEFAULT NULL COMMENT '宿主机构名称(出版商/机构)',
    `host_organization_lineage` JSON NULL DEFAULT NULL COMMENT '机构所有权链(JSON数组,从直接母公司到顶层)',

    -- ========================================
    -- 地理信息
    -- ========================================
    `country_code` VARCHAR(10) NULL DEFAULT NULL COMMENT '国家代码(ISO 3166-1 alpha-2,如US/CN)',

    -- ========================================
    -- 出版历史 (来自 PubMed Catalog)
    -- ========================================
    `publication_start_year` SMALLINT NULL DEFAULT NULL COMMENT '创刊年份',
    `publication_end_year` SMALLINT NULL DEFAULT NULL COMMENT '停刊年份',
    `ceased` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已停刊(0=否,1=是)',

    -- ========================================
    -- 索引收录信息 (来自 PubMed Catalog)
    -- ========================================
    `indexing_status` VARCHAR(32) NULL DEFAULT NULL COMMENT 'MEDLINE收录状态:MEDLINE/PUBMED/IN_PROCESS/NOT_INDEXED',
    `medline_ta` VARCHAR(200) NULL DEFAULT NULL COMMENT 'MEDLINE缩写标题',
    `iso_abbreviation` VARCHAR(200) NULL DEFAULT NULL COMMENT 'ISO缩写标题',

    -- ========================================
    -- OA 状态
    -- ========================================
    `is_oa` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否为开放获取来源(0=否,1=是)',
    `is_in_doaj` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否收录于DOAJ(0=否,1=是)',
    `oa_type` VARCHAR(20) NULL DEFAULT NULL COMMENT 'OA类型:GOLD/DIAMOND/HYBRID/BRONZE(来自DOAJ)',

    -- ========================================
    -- 统计指标 (当前快照)
    -- ========================================
    `works_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '托管作品数量',
    `cited_by_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '被引用次数总计',

    -- ========================================
    -- 最新评级快照 (冗余,高频查询优化)
    -- ========================================
    `latest_impact_score` DECIMAL(10,4) NULL DEFAULT NULL COMMENT '最新影响力分数(JIF/CiteScore等)',
    `latest_quartile` VARCHAR(10) NULL DEFAULT NULL COMMENT '最新分区(Q1-Q4或1-4区)',
    `latest_rating_system` VARCHAR(32) NULL DEFAULT NULL COMMENT '最新评级来源:JCR/CAS/SCOPUS',
    `latest_rating_year` SMALLINT NULL DEFAULT NULL COMMENT '最新评级年份',

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
    `provenance_code` VARCHAR(32) NULL DEFAULT NULL COMMENT '首次导入来源代码:OPENALEX/PUBMED/CROSSREF/DOAJ/MANUAL',
    `source_created_date` DATE NULL DEFAULT NULL COMMENT '来源系统创建日期',
    `source_updated_date` DATE NULL DEFAULT NULL COMMENT '来源系统更新日期',
    `last_synced_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后同步时间(UTC,微秒精度)',

    -- ========================================
    -- 扩展数据 (存储来源特定字段)
    -- ========================================
    `ext_data` JSON NULL DEFAULT NULL COMMENT '扩展数据(h_index/i10_index/is_core等来源特定字段)',

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

    -- 唯一索引 (冗余标识符)
    UNIQUE INDEX `uk_openalex_id` (`openalex_id`) COMMENT 'OpenAlex ID唯一索引',
    UNIQUE INDEX `uk_issn_l` (`issn_l`) COMMENT 'ISSN-L唯一索引',
    UNIQUE INDEX `uk_nlm_id` (`nlm_id`) COMMENT 'NLM ID唯一索引',
    UNIQUE INDEX `uk_coden` (`coden`) COMMENT 'CODEN唯一索引',

    -- 普通索引
    INDEX `idx_venue_type` (`venue_type`) COMMENT '载体类型索引',
    INDEX `idx_display_name` (`display_name`(100)) COMMENT '名称前缀索引',
    INDEX `idx_publisher` (`publisher`(100)) COMMENT '出版商索引',
    INDEX `idx_host_org` (`host_organization_id`) COMMENT '宿主机构索引',
    INDEX `idx_country` (`country_code`) COMMENT '国家代码索引',
    INDEX `idx_indexing_status` (`indexing_status`) COMMENT 'MEDLINE收录状态索引',
    INDEX `idx_is_oa` (`is_oa`) COMMENT 'OA状态索引',
    INDEX `idx_is_in_doaj` (`is_in_doaj`) COMMENT 'DOAJ收录索引',
    INDEX `idx_oa_type` (`oa_type`) COMMENT 'OA类型索引',
    INDEX `idx_latest_quartile` (`latest_quartile`) COMMENT '最新分区索引',
    INDEX `idx_latest_impact_score` (`latest_impact_score`) COMMENT '最新影响力分数索引',
    INDEX `idx_provenance` (`provenance_code`) COMMENT '数据来源索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='出版载体表:管理期刊/仓库/会议/电子书平台,支持多数据源(OpenAlex/PubMed/DOAJ/Crossref/JCR)';

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
-- 表 3: cat_venue_publication_stats (发文统计表)
-- ============================================================
-- 表说明: 存储载体年度发文/引用统计数据,支持时序分析
-- 记录数预估: 初始 250万 / 年增长 30万 / 5年规模 400万 (平均每个venue 10年数据)
-- 主要查询场景:
--   1. 按 venue_id 查询历年数据(>500次/天,中频)
--   2. 按年份统计所有载体数据(<200次/天,低频)
--   3. 按发文量/被引量排序(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_venue_publication_stats` (
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

    -- 年份约束由领域层 VenuePublicationStats 实体负责校验

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='发文统计表:存储载体年度发文/引用统计,来源OpenAlex';


-- ============================================================
-- 表 4: cat_venue_rating (载体评级表)
-- ============================================================
-- 表说明: 存储载体的年度评级数据(JCR影响因子/中科院分区/Scopus CiteScore等)
-- 记录数预估: 初始 100万 / 年增长 50万 / 5年规模 350万 (每个venue每年多个评级体系)
-- 主要查询场景:
--   1. 按 venue_id + rating_system 查询评级历史(>1000次/天,高频)
--   2. 按分区筛选期刊(<500次/天,中频)
--   3. 按影响力分数排序(<200次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_venue_rating` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',
    `year` SMALLINT NOT NULL COMMENT '评级年份',
    `rating_system` VARCHAR(32) NOT NULL COMMENT '评级体系:JCR/CAS/SCOPUS',

    -- ========================================
    -- 通用评级字段 (冗余,高频查询优化)
    -- ========================================
    `quartile` VARCHAR(10) NULL DEFAULT NULL COMMENT '分区:Q1-Q4(JCR/Scopus)或1-4区(中科院)',
    `impact_score` DECIMAL(10,4) NULL DEFAULT NULL COMMENT '影响力分数(JIF/CiteScore/复合IF)',

    -- ========================================
    -- 评级详情 (各体系特有数据)
    -- ========================================
    `rating_data` JSON NULL DEFAULT NULL COMMENT '评级详情JSON(JCR:jif/jci/eigenfactor; CAS:is_top/trend; Scopus:snip/sjr)',
    `categories` JSON NULL DEFAULT NULL COMMENT '学科分类及分区(JSON数组)',

    -- ========================================
    -- 数据来源
    -- ========================================
    `source_url` VARCHAR(500) NULL DEFAULT NULL COMMENT '数据来源URL',
    `fetched_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '数据获取时间',

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
    UNIQUE INDEX `uk_venue_year_system` (`venue_id`, `year`, `rating_system`) COMMENT '载体+年份+评级体系唯一索引',

    -- 普通索引
    INDEX `idx_rating_system` (`rating_system`) COMMENT '评级体系索引',
    INDEX `idx_year` (`year`) COMMENT '年份索引',
    INDEX `idx_quartile` (`quartile`) COMMENT '分区索引',
    INDEX `idx_impact_score` (`impact_score`) COMMENT '影响力分数索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='载体评级表:存储JCR影响因子/中科院分区/Scopus CiteScore等年度评级';


-- ============================================================
-- 表 5: cat_venue_source_data (数据源表)
-- ============================================================
-- 表说明: 存储各数据源的原始JSON和提取字段,支持数据溯源和审计
-- 记录数预估: 初始 100万 / 年增长 50万 / 5年规模 350万 (每个venue多个数据源)
-- 主要查询场景:
--   1. 按 venue_id + source_code 查询来源数据(>500次/天,中频)
--   2. 按 source_code 统计数据量(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_venue_source_data` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',
    `source_code` VARCHAR(32) NOT NULL COMMENT '来源代码:OPENALEX/PUBMED/DOAJ/CROSSREF/JCR',
    `source_id` VARCHAR(100) NULL DEFAULT NULL COMMENT '来源系统中的ID',

    -- ========================================
    -- 数据内容
    -- ========================================
    `raw_data` JSON NULL DEFAULT NULL COMMENT '原始JSON数据(完整保存)',
    `extracted_data` JSON NULL DEFAULT NULL COMMENT '提取的关键字段(便于查询)',

    -- ========================================
    -- 来源时间
    -- ========================================
    `source_created_at` DATE NULL DEFAULT NULL COMMENT '来源系统创建时间',
    `source_updated_at` DATE NULL DEFAULT NULL COMMENT '来源系统更新时间',
    `fetched_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '数据获取时间',

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
    UNIQUE INDEX `uk_venue_source` (`venue_id`, `source_code`) COMMENT '载体+来源唯一索引',

    -- 普通索引
    INDEX `idx_source_code` (`source_code`) COMMENT '来源代码索引',
    INDEX `idx_fetched_at` (`fetched_at`) COMMENT '获取时间索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='数据源表:存储各数据源的原始JSON和提取字段,支持数据溯源';


-- ============================================================
-- 表 6: cat_venue_instance (载体实例表)
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
-- 表 7: cat_venue_mesh (期刊MeSH主题表)
-- ============================================================
-- 表说明: 存储期刊的MeSH主题词分类,来源Serfile
-- 命名说明: 与 cat_publication_mesh 保持一致的命名风格
-- 记录数预估: 初始 100万 / 年增长 10万 / 5年规模 150万 (平均每venue 4个主题)
-- 主要查询场景:
--   1. 按 venue_id 查询所有主题(>1000次/天,高频)
--   2. 按主题筛选期刊(500-1000次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_venue_mesh` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',

    -- ========================================
    -- MeSH 描述符信息
    -- ========================================
    `descriptor_name` VARCHAR(255) NOT NULL COMMENT 'MeSH描述符名称',
    `descriptor_ui` VARCHAR(20) NULL DEFAULT NULL COMMENT 'MeSH描述符唯一标识符(格式:D000001)',
    `is_major_topic` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否主要主题(0=否,1=是)',

    -- ========================================
    -- MeSH 限定符信息
    -- ========================================
    `qualifier_name` VARCHAR(100) NULL DEFAULT NULL COMMENT 'MeSH限定符名称(可选)',
    `qualifier_ui` VARCHAR(20) NULL DEFAULT NULL COMMENT 'MeSH限定符唯一标识符(格式:Q000001)',

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

    -- 唯一索引 (使用 descriptor_name 而非 descriptor_ui,因为 descriptor_ui 可为 NULL)
    UNIQUE INDEX `uk_venue_mesh` (`venue_id`, `descriptor_name`(100))
        COMMENT '载体+描述符名称唯一索引',

    -- 普通索引
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引',
    INDEX `idx_descriptor_ui` (`descriptor_ui`) COMMENT '描述符UI索引(用于MeSH关联查询)',
    INDEX `idx_qualifier_ui` (`qualifier_ui`) COMMENT '限定符UI索引(用于MeSH关联查询)',
    INDEX `idx_is_major` (`is_major_topic`) COMMENT '主要主题索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='期刊MeSH主题表:存储期刊的MeSH主题词分类,来源Serfile,与cat_publication_mesh命名风格一致';


-- ============================================================
-- 表 8: cat_venue_relation (期刊关联表)
-- ============================================================
-- 表说明: 存储期刊之间的关联关系(前刊/后刊/合并/分拆等),来源Serfile
-- 记录数预估: 初始 5万 / 年增长 1万 / 5年规模 10万 (约20%期刊有关联)
-- 主要查询场景:
--   1. 按 venue_id 查询所有关联(>500次/天,中频)
--   2. 追踪期刊演变历史(<200次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_venue_relation` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '当前载体ID(外键:cat_venue.id)',
    `related_venue_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联载体ID(系统内关联,可为空)',
    `related_nlm_id` VARCHAR(20) NULL DEFAULT NULL COMMENT '关联期刊NLM ID(系统外关联)',
    `related_title` VARCHAR(500) NOT NULL COMMENT '关联期刊标题(冗余,便于展示)',

    -- ========================================
    -- 关联属性
    -- ========================================
    `relation_type` VARCHAR(32) NOT NULL COMMENT '关联类型:PRECEDING/SUCCEEDING/ABSORBED/ABSORBED_BY/MERGED/SPLIT_FROM/CONTINUED_BY/CONTINUES',
    `effective_date` DATE NULL DEFAULT NULL COMMENT '关联生效日期',
    `notes` VARCHAR(500) NULL DEFAULT NULL COMMENT '关联说明备注',

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

    -- 唯一索引 (防止重复关联)
    UNIQUE INDEX `uk_venue_related_type` (`venue_id`, `related_nlm_id`, `relation_type`)
        COMMENT '载体+关联NLM ID+类型唯一索引',

    -- 普通索引
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引',
    INDEX `idx_relation_type` (`relation_type`) COMMENT '关联类型索引',
    INDEX `idx_related_venue` (`related_venue_id`) COMMENT '关联载体索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='期刊关联表:存储期刊间的演变关系(前刊/后刊/合并/分拆),来源Serfile';


-- ============================================================
-- 表 9: cat_venue_indexing_history (索引历史表)
-- ============================================================
-- 表说明: 存储期刊在MEDLINE/PubMed的索引历史变迁,来源Serfile
-- 记录数预估: 初始 30万 / 年增长 3万 / 5年规模 45万 (平均每venue 1.2条记录)
-- 主要查询场景:
--   1. 按 venue_id 查询索引历史(>500次/天,中频)
--   2. 统计各时期收录期刊数(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_venue_indexing_history` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',

    -- ========================================
    -- 索引来源与状态
    -- ========================================
    `indexing_source` VARCHAR(32) NOT NULL COMMENT '索引来源:MEDLINE/PMC等',
    `currently_indexed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '当前是否被索引(0=否,1=是)',
    `indexing_treatment` VARCHAR(20) NULL DEFAULT NULL COMMENT '索引处理方式:FULL/SELECTIVE',
    `citation_subset` VARCHAR(20) NULL DEFAULT NULL COMMENT '引用子集:IM(Index Medicus)/AIM(Abridged IM)/N/D/H/K/T/E/S/X/B/C/F/Q等',

    -- ========================================
    -- 索引范围(年/卷/期)
    -- ========================================
    `start_year` SMALLINT NULL DEFAULT NULL COMMENT '索引开始年份',
    `start_volume` VARCHAR(20) NULL DEFAULT NULL COMMENT '索引开始卷号',
    `start_issue` VARCHAR(20) NULL DEFAULT NULL COMMENT '索引开始期号',
    `end_year` SMALLINT NULL DEFAULT NULL COMMENT '索引结束年份(NULL=持续收录中)',
    `end_volume` VARCHAR(20) NULL DEFAULT NULL COMMENT '索引结束卷号',
    `end_issue` VARCHAR(20) NULL DEFAULT NULL COMMENT '索引结束期号',

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
    UNIQUE INDEX `uk_venue_source_start` (`venue_id`, `indexing_source`, `start_year`)
        COMMENT '载体+来源+开始年份唯一索引',

    -- 普通索引
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引',
    INDEX `idx_source_indexed` (`indexing_source`, `currently_indexed`) COMMENT '来源+索引状态复合索引',
    INDEX `idx_citation_subset` (`citation_subset`) COMMENT '引用子集索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='索引历史表:存储期刊在MEDLINE/PubMed的收录历史变迁,来源Serfile';
