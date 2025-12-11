-- ============================================================
-- Patra Catalog 数据库 - Venue 聚合表 DDL
-- ============================================================
-- 设计阶段: CQRS 最小聚合设计
-- 创建日期: 2025-01-18
-- 修改日期: 2025-12-11
-- 设计范围: Venue 聚合根（最小化）及关联数据表
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_unicode_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与设计说明
-- ============================================================
-- **CQRS 架构**:
-- - 聚合根只负责写入，不涉及读取
-- - 只保留不变量验证所需的最小属性
-- - 其他数据按业务领域拆分到独立表
--
-- **表结构**:
-- 1. cat_venue (聚合根，最小化) - 核心身份+来源追踪
-- 2. cat_venue_identifier (标识符) - 依赖 cat_venue
-- 3. cat_venue_detail (详情，1:1) - 出版信息+索引信息+OA状态
-- 4. cat_venue_stats (统计快照，1:1) - 发文/引用统计
-- 5. cat_venue_apc (APC信息，1:1) - 文章处理费
-- 6. cat_venue_society (关联学会，1:N) - 学术组织
-- 7. cat_venue_publication_stats (年度统计，1:N) - 时序发文统计
-- 8. cat_venue_instance (载体实例，独立聚合根) - 卷期/版次/届次
-- 9. cat_venue_mesh (MeSH主题，1:N) - Serfile扩展
-- 10. cat_venue_relation (期刊关联，1:N) - Serfile扩展
-- 11. cat_venue_indexing_history (索引历史，1:N) - Serfile扩展
-- ============================================================


-- ============================================================
-- 表 1: cat_venue (出版载体表 - 最小聚合根)
-- ============================================================
-- 表说明: 聚合根，仅包含核心身份标识和来源追踪
-- 设计原则: CQRS 写侧最小化，所有非核心属性移至补充数据表
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 核心属性（不变量验证所需）
    -- ========================================
    `venue_type` VARCHAR(32) NOT NULL COMMENT '载体类型:JOURNAL/REPOSITORY/CONFERENCE/EBOOK_PLATFORM/BOOK_SERIES/METADATA/OTHER',
    `display_name` VARCHAR(500) NOT NULL COMMENT '载体显示名称(主名称)',

    -- ========================================
    -- 来源追踪（Provenance）
    -- ========================================
    `provenance_code` VARCHAR(32) NOT NULL COMMENT '首次导入来源代码:OPENALEX/PUBMED/CROSSREF/DOAJ/MANUAL',
    `source_created_date` DATE NULL DEFAULT NULL COMMENT '来源系统创建日期',
    `source_updated_date` DATE NULL DEFAULT NULL COMMENT '来源系统更新日期',
    `last_synced_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后同步时间(UTC,微秒精度)',

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

    -- 普通索引
    INDEX `idx_venue_type` (`venue_type`) COMMENT '载体类型索引',
    INDEX `idx_display_name` (`display_name`(100)) COMMENT '名称前缀索引',
    INDEX `idx_provenance` (`provenance_code`) COMMENT '数据来源索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='出版载体表(最小聚合根):仅含核心身份标识和来源追踪,遵循CQRS原则';

-- 全文索引
CREATE FULLTEXT INDEX `ft_display_name` ON `cat_venue` (`display_name`)
    WITH PARSER ngram
    COMMENT '名称全文索引,支持中英文检索';


-- ============================================================
-- 表 2: cat_venue_identifier (载体标识符表)
-- ============================================================
-- 表说明: 存储载体的各类标识符,支持一对多关系(如多个ISSN)
-- 重要: 所有标识符统一存储在此表
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
    `identifier_type` VARCHAR(32) NOT NULL COMMENT '标识符类型:OPENALEX/ISSN/ISSN_L/ISBN/NLM/CODEN/MAG/FATCAT/WIKIDATA',
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

    -- 复合索引 (反向查询优化 - 通过标识符查找载体)
    INDEX `idx_type_value` (`identifier_type`, `identifier_value`) COMMENT '类型+值索引,支持按标识符反查载体',

    -- 普通索引
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引,支持查询载体的所有标识符'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='载体标识符表:存储载体的各类标识符(OPENALEX/ISSN/ISSN_L/NLM/CODEN等)';


-- ============================================================
-- 表 3: cat_venue_detail (载体详情表 - CQRS补充数据)
-- ============================================================
-- 表说明: 合并出版信息+索引信息+OA状态+宿主机构+国家,1:1关系
-- 数据来源: OpenAlex + PubMed Serfile
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_detail` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',

    -- ========================================
    -- 出版信息 (来自 OpenAlex)
    -- ========================================
    `abbreviated_title` VARCHAR(200) NULL DEFAULT NULL COMMENT '缩写标题(来自ISSN中心或ISO)',
    `alternate_titles` JSON NULL DEFAULT NULL COMMENT '替代名称列表(JSON数组)',
    `homepage_url` TEXT NULL DEFAULT NULL COMMENT '载体主页URL',
    `frequency` VARCHAR(50) NULL DEFAULT NULL COMMENT '出版频率(Weekly/Monthly/Quarterly等)',

    -- ========================================
    -- 出版历史 (来自 OpenAlex/PubMed)
    -- ========================================
    `publication_start_year` SMALLINT NULL DEFAULT NULL COMMENT '创刊年份',
    `publication_end_year` SMALLINT NULL DEFAULT NULL COMMENT '停刊年份',
    `ceased` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已停刊(0=否,1=是)',

    -- ========================================
    -- 语言信息 (来自 Serfile)
    -- ========================================
    `primary_language` VARCHAR(10) NULL DEFAULT NULL COMMENT '主要语言代码(ISO 639-3)',
    `languages` JSON NULL DEFAULT NULL COMMENT '期刊语言(JSON对象,含primary和summary数组)',

    -- ========================================
    -- 宿主机构 (来自 OpenAlex)
    -- ========================================
    `host_organization_id` VARCHAR(100) NULL DEFAULT NULL COMMENT '宿主机构OpenAlex ID',
    `host_organization_name` VARCHAR(500) NULL DEFAULT NULL COMMENT '宿主机构名称(出版商/机构)',
    `host_organization_lineage` JSON NULL DEFAULT NULL COMMENT '机构所有权链(JSON数组)',

    -- ========================================
    -- 地理信息 (来自 OpenAlex)
    -- ========================================
    `country_code` VARCHAR(10) NULL DEFAULT NULL COMMENT '国家代码(ISO 3166-1 alpha-2)',

    -- ========================================
    -- 索引信息 (来自 PubMed Catalog)
    -- ========================================
    `indexing_status` VARCHAR(32) NULL DEFAULT NULL COMMENT 'MEDLINE收录状态:MEDLINE/PUBMED/IN_PROCESS/NOT_INDEXED',
    `medline_ta` VARCHAR(200) NULL DEFAULT NULL COMMENT 'MEDLINE缩写标题',
    `iso_abbreviation` VARCHAR(200) NULL DEFAULT NULL COMMENT 'ISO缩写标题',

    -- ========================================
    -- OA 状态 (来自 OpenAlex)
    -- ========================================
    `is_oa` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否为开放获取来源(0=否,1=是)',
    `is_in_doaj` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收录于DOAJ(0=否,1=是)',
    `oa_type` VARCHAR(20) NULL DEFAULT NULL COMMENT 'OA类型:GOLD/DIAMOND/HYBRID/BRONZE',

    -- ========================================
    -- 扩展数据
    -- ========================================
    `ext_data` JSON NULL DEFAULT NULL COMMENT '扩展数据(来源特定字段)',

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

    -- 唯一索引 (1:1 关系)
    UNIQUE INDEX `uk_venue_id` (`venue_id`) COMMENT '载体ID唯一索引,保证1:1关系',

    -- 普通索引 (查询优化)
    INDEX `idx_host_org` (`host_organization_id`) COMMENT '宿主机构索引',
    INDEX `idx_country` (`country_code`) COMMENT '国家代码索引',
    INDEX `idx_indexing_status` (`indexing_status`) COMMENT 'MEDLINE收录状态索引',
    INDEX `idx_is_oa` (`is_oa`) COMMENT 'OA状态索引',
    INDEX `idx_is_in_doaj` (`is_in_doaj`) COMMENT 'DOAJ收录索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='载体详情表:出版信息+索引信息+OA状态,CQRS补充数据(1:1)';


-- ============================================================
-- 表 4: cat_venue_stats (统计快照表 - CQRS补充数据)
-- ============================================================
-- 表说明: 存储载体的统计快照数据,1:1关系
-- 数据来源: OpenAlex
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_stats` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',

    -- ========================================
    -- 统计指标 (当前快照)
    -- ========================================
    `works_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '托管作品总数',
    `cited_by_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '被引用次数总计',
    `h_index` INT UNSIGNED NULL DEFAULT NULL COMMENT 'H-Index指数',
    `i10_index` INT UNSIGNED NULL DEFAULT NULL COMMENT 'i10-Index指数',
    `two_year_mean_citedness` DECIMAL(10,4) NULL DEFAULT NULL COMMENT '两年平均引用率',

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

    -- 唯一索引 (1:1 关系)
    UNIQUE INDEX `uk_venue_id` (`venue_id`) COMMENT '载体ID唯一索引,保证1:1关系',

    -- 普通索引
    INDEX `idx_works_count` (`works_count`) COMMENT '发文量索引',
    INDEX `idx_cited_by_count` (`cited_by_count`) COMMENT '被引量索引',
    INDEX `idx_h_index` (`h_index`) COMMENT 'H-Index索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='统计快照表:发文/引用/H-Index等统计指标,CQRS补充数据(1:1)';


-- ============================================================
-- 表 5: cat_venue_apc (APC信息表 - CQRS补充数据)
-- ============================================================
-- 表说明: 存储文章处理费(APC)信息,1:1关系
-- 数据来源: OpenAlex/DOAJ
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_apc` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',

    -- ========================================
    -- APC 信息
    -- ========================================
    `apc_usd` INT UNSIGNED NULL DEFAULT NULL COMMENT '文章处理费(美元)',
    `apc_prices` JSON NULL DEFAULT NULL COMMENT 'APC费用列表(JSON数组,含不同货币价格)',

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

    -- 唯一索引 (1:1 关系)
    UNIQUE INDEX `uk_venue_id` (`venue_id`) COMMENT '载体ID唯一索引,保证1:1关系',

    -- 普通索引
    INDEX `idx_apc_usd` (`apc_usd`) COMMENT 'APC费用索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='APC信息表:文章处理费,CQRS补充数据(1:1)';


-- ============================================================
-- 表 6: cat_venue_society (关联学会表 - CQRS补充数据)
-- ============================================================
-- 表说明: 存储载体关联的学术组织,1:N关系
-- 数据来源: OpenAlex
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_society` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',

    -- ========================================
    -- 学会信息
    -- ========================================
    `url` VARCHAR(500) NULL DEFAULT NULL COMMENT '学会/组织URL',
    `organization` VARCHAR(500) NOT NULL COMMENT '学会/组织名称',

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

    -- 普通索引
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='关联学会表:载体关联的学术组织,CQRS补充数据(1:N)';


-- ============================================================
-- 表 7: cat_venue_publication_stats (年度发文统计表)
-- ============================================================
-- 表说明: 存储载体年度发文/引用统计数据,支持时序分析
-- 数据来源: OpenAlex
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
    UNIQUE INDEX `uk_venue_year` (`venue_id`, `year`) COMMENT '载体+年份唯一索引',

    -- 普通索引
    INDEX `idx_year` (`year`) COMMENT '年份索引',
    INDEX `idx_works_count` (`works_count`) COMMENT '发文量索引',
    INDEX `idx_cited_by_count` (`cited_by_count`) COMMENT '被引量索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='年度发文统计表:载体年度发文/引用统计,来源OpenAlex';


-- ============================================================
-- 表 8: cat_venue_instance (载体实例表 - 独立聚合根)
-- ============================================================
-- 表说明: 存储载体的具体实例(期刊的卷期、书籍的版次、会议的届次)
-- 设计: 独立聚合根,通过 venue_id 关联 cat_venue
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_instance` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',

    -- ========================================
    -- 期刊字段
    -- ========================================
    `volume` VARCHAR(100) NULL DEFAULT NULL COMMENT '卷号(如"45","2023")',
    `issue` VARCHAR(100) NULL DEFAULT NULL COMMENT '期号(如"3","Suppl 1")',

    -- ========================================
    -- 书籍字段
    -- ========================================
    `edition` VARCHAR(100) NULL DEFAULT NULL COMMENT '版次(书籍专用,如"2nd Edition")',

    -- ========================================
    -- 出版日期
    -- ========================================
    `publication_year` SMALLINT NOT NULL COMMENT '出版年份(必填)',
    `publication_month` TINYINT NULL DEFAULT NULL COMMENT '出版月份(1-12)',
    `publication_day` TINYINT NULL DEFAULT NULL COMMENT '出版日期(1-31)',

    -- ========================================
    -- 会议字段
    -- ========================================
    `conference_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '会议名称(会议专用)',
    `conference_start_date` DATE NULL DEFAULT NULL COMMENT '会议开始日期(会议专用)',
    `conference_end_date` DATE NULL DEFAULT NULL COMMENT '会议结束日期(会议专用)',
    `conference_location` VARCHAR(200) NULL DEFAULT NULL COMMENT '会议地点(会议专用)',

    -- ========================================
    -- 扩展字段
    -- ========================================
    `instance_metadata` JSON NULL DEFAULT NULL COMMENT '实例元数据(灵活扩展)',

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

    -- 复合索引 (期刊实例查询)
    INDEX `idx_venue_volume_issue` (`venue_id`, `volume`, `issue`) COMMENT '载体+卷+期复合索引',

    -- 普通索引
    INDEX `idx_publication_year` (`publication_year`) COMMENT '出版年份索引',
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='载体实例表(独立聚合根):期刊卷期/书籍版次/会议届次';


-- ============================================================
-- 表 9: cat_venue_mesh (期刊MeSH主题表)
-- ============================================================
-- 表说明: 存储期刊的MeSH主题词分类,来源Serfile
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

    -- 唯一索引
    UNIQUE INDEX `uk_venue_mesh` (`venue_id`, `descriptor_name`(100))
        COMMENT '载体+描述符名称唯一索引',

    -- 普通索引
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引',
    INDEX `idx_descriptor_ui` (`descriptor_ui`) COMMENT '描述符UI索引',
    INDEX `idx_is_major` (`is_major_topic`) COMMENT '主要主题索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='期刊MeSH主题表:来源Serfile';


-- ============================================================
-- 表 10: cat_venue_relation (期刊关联表)
-- ============================================================
-- 表说明: 存储期刊之间的关联关系(前刊/后刊/合并/分拆等),来源Serfile
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

    -- 唯一索引
    UNIQUE INDEX `uk_venue_related_type` (`venue_id`, `related_nlm_id`, `relation_type`)
        COMMENT '载体+关联NLM ID+类型唯一索引',

    -- 普通索引
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引',
    INDEX `idx_relation_type` (`relation_type`) COMMENT '关联类型索引',
    INDEX `idx_related_venue` (`related_venue_id`) COMMENT '关联载体索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='期刊关联表:期刊间演变关系(前刊/后刊/合并/分拆),来源Serfile';


-- ============================================================
-- 表 11: cat_venue_indexing_history (索引历史表)
-- ============================================================
-- 表说明: 存储期刊在MEDLINE/PubMed的索引历史变迁,来源Serfile
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
    `citation_subset` VARCHAR(20) NULL DEFAULT NULL COMMENT '引用子集:IM/AIM/N/D/H/K/T/E/S/X/B/C/F/Q等',

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
COMMENT='索引历史表:MEDLINE/PubMed收录历史变迁,来源Serfile';
