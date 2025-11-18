-- ============================================================
-- Patra Catalog 数据库 - 关联信息模块 DDL
-- ============================================================
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: patra_catalog 关联信息模块表（7张表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_unicode_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 数据库配置
-- ============================================================

-- 使用数据库
USE `patra_catalog`;

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- 1. cat_funding (资助信息表) - 无依赖
-- 2. cat_publication_funding (文献-资助关联表) - 依赖 cat_publication, cat_funding
-- 3. cat_reference (参考文献表) - 依赖 cat_publication, 可选依赖自身
-- 4. cat_external_reference (外部引用表) - 依赖 cat_publication
-- 5. cat_related_item (相关项目表) - 依赖 cat_publication, 可选依赖自身
-- 6. cat_supplemental_object (补充对象表) - 依赖 cat_publication
-- 7. cat_publication_history (发布历史表) - 依赖 cat_publication
-- ============================================================

-- ============================================================
-- 执行说明
-- ============================================================
-- 1. 确保 MySQL 版本 >= 8.0（需要 CHECK 约束支持）
-- 2. 确保核心实体表(cat_publication)已创建
-- 3. 按顺序执行表创建（考虑外键依赖）
-- 4. 建议在测试环境先验证，再在生产环境执行
-- 5. 执行前备份现有数据（如果有）
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

DROP TABLE IF EXISTS `cat_funding`;

CREATE TABLE `cat_funding` (
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
    -- 审计字段（极简版）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',

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
    INDEX `idx_funding_type` (`funding_type`) COMMENT '资助类型索引,支持按类型筛选',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_funding_type` CHECK (`funding_type` IN ('Government', 'Foundation', 'Corporate', 'University', 'Non-profit', 'Other') OR `funding_type` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
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

DROP TABLE IF EXISTS `cat_publication_funding`;

CREATE TABLE `cat_publication_funding` (
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
    -- 审计字段（极简版）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',

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

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='文献-资助关联表:管理文献与资助的多对多关系';


-- ============================================================
-- 表 3: cat_reference (参考文献表)
-- ============================================================
-- 表说明: 管理文献引用关系,支持库内外引用双重关联
-- 记录数预估: 初始 2亿 / 年增长 6500万 / 5年规模 5.25亿
-- 主要查询场景:
--   1. 查询某文献的所有参考文献(>2000次/天,高频)
--   2. 按 cited_pmid 查询库外引用(>1000次/天,高频)
--   3. 按 cited_publication_id 查询被引关系(500-1000次/天,中频)
--   4. 按 cited_doi 查询引用(<500次/天,中频)
-- ============================================================

DROP TABLE IF EXISTS `cat_reference`;

CREATE TABLE `cat_reference` (
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
    -- 审计字段（极简版）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',

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
    INDEX `idx_retracted` (`is_retracted`) COMMENT '撤稿索引,支持筛选撤稿文献引用',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_reference_type` CHECK (`reference_type` IN ('Journal Article', 'Book', 'Book Chapter', 'Conference Paper', 'Thesis', 'Report', 'Preprint', 'Web Page', 'Other') OR `reference_type` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='参考文献表:管理文献引用关系,支持库内外引用';


-- ============================================================
-- 表 4: cat_external_reference (外部引用表)
-- ============================================================
-- 表说明: 管理外部数据库引用(基因库、临床试验、数据集等),与参考文献分离
-- 记录数预估: 初始 200万 / 年增长 60万 / 5年规模 500万
-- 主要查询场景:
--   1. 查询某文献的所有外部引用(>500次/天,中频)
--   2. 按 database_name 查询(100-500次/天,中频)
--   3. 按 accession_number 查询(<100次/天,低频)
-- ============================================================

DROP TABLE IF EXISTS `cat_external_reference`;

CREATE TABLE `cat_external_reference` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `database_name` VARCHAR(50) NOT NULL COMMENT '数据库名称(如"GenBank","ClinicalTrials.gov")',
    `database_category` VARCHAR(100) NULL DEFAULT NULL COMMENT '数据库类别(如"Genomic","Clinical Trial")',
    `accession_number` VARCHAR(200) NOT NULL COMMENT '登录号/访问号',
    `url` VARCHAR(500) NULL DEFAULT NULL COMMENT '链接地址(完整URL)',
    `reference_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '引用类型(描述性)',
    `description` VARCHAR(500) NULL DEFAULT NULL COMMENT '描述信息',
    `access_date` DATE NULL DEFAULT NULL COMMENT '访问日期(最后验证日期)',
    `version` VARCHAR(50) NULL DEFAULT NULL COMMENT '数据库版本号',
    `order_num` INTEGER NOT NULL DEFAULT 1 COMMENT '顺序号(用于排序显示)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '外部引用元数据(灵活扩展)',

    -- ========================================
    -- 审计字段（极简版）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_external_ref` (`publication_id`, `database_name`, `accession_number`) COMMENT '文献+数据库+登录号唯一索引,防止重复引用',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '文献ID索引,支持查询某文献的所有外部引用(中频)',
    INDEX `idx_database` (`database_name`) COMMENT '数据库名称索引,支持按数据库查询(中频)',
    INDEX `idx_accession` (`accession_number`) COMMENT '登录号索引,支持按登录号查询(低频)',
    INDEX `idx_category` (`database_category`) COMMENT '数据库类别索引,支持按类别筛选(低频)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='外部引用表:管理外部数据库引用(基因库、临床试验等)';


-- ============================================================
-- 表 5: cat_related_item (相关项目表)
-- ============================================================
-- 表说明: 管理文献的相关项(撤稿、勘误、评论等),支持 12 种关联类型
-- 记录数预估: 初始 40万 / 年增长 12万 / 5年规模 100万
-- 主要查询场景:
--   1. 查询某文献的所有相关项(>500次/天,中频)
--   2. 按 relationship_type 查询撤稿文献(100-500次/天,中频)
--   3. 按 related_pmid 查询(<100次/天,低频)
-- ============================================================

DROP TABLE IF EXISTS `cat_related_item`;

CREATE TABLE `cat_related_item` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '主文献ID(外键:cat_publication.id)',
    `related_publication_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '相关文献ID(如果在库中)(外键:cat_publication.id)',
    `related_pmid` VARCHAR(20) NULL DEFAULT NULL COMMENT '相关文献PMID(库外)',
    `related_doi` VARCHAR(200) NULL DEFAULT NULL COMMENT '相关文献DOI(库外)',
    `relationship_type` VARCHAR(50) NOT NULL COMMENT '关系类型(Retraction/Partial Retraction/Expression of Concern/Withdrawn/Erratum/Correction/Comment/Response/Update/Republication/Superseded/Duplicate)',
    `title` VARCHAR(500) NULL DEFAULT NULL COMMENT '相关项标题',
    `description` VARCHAR(500) NULL DEFAULT NULL COMMENT '关系描述',
    `relationship_date` DATE NULL DEFAULT NULL COMMENT '关系建立日期',
    `initiated_by` VARCHAR(100) NULL DEFAULT NULL COMMENT '发起方(Author/Editor/Publisher/Institution/Third Party)',
    `status` VARCHAR(50) NULL DEFAULT NULL COMMENT '状态(Active/Resolved/Under Investigation/Pending)',
    `order_num` INTEGER NOT NULL DEFAULT 1 COMMENT '顺序号(用于排序显示)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '关系元数据(灵活扩展)',

    -- ========================================
    -- 审计字段（极简版）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '主文献ID索引,支持查询某文献的所有相关项(中频)',
    INDEX `idx_related_pub` (`related_publication_id`) COMMENT '相关文献ID索引,支持反向查询相关文献(中频)',
    INDEX `idx_relationship` (`relationship_type`) COMMENT '关系类型索引,支持按类型筛选(如查询所有撤稿文献)',
    INDEX `idx_status` (`status`) COMMENT '状态索引,支持按状态筛选',
    INDEX `idx_date` (`relationship_date`) COMMENT '日期索引,支持按时间排序和统计',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_relationship_type` CHECK (`relationship_type` IN ('Retraction', 'Partial Retraction', 'Expression of Concern', 'Withdrawn', 'Erratum', 'Correction', 'Comment', 'Response', 'Update', 'Republication', 'Superseded', 'Duplicate')),
    CONSTRAINT `chk_initiated_by` CHECK (`initiated_by` IN ('Author', 'Editor', 'Publisher', 'Institution', 'Third Party') OR `initiated_by` IS NULL),
    CONSTRAINT `chk_status` CHECK (`status` IN ('Active', 'Resolved', 'Under Investigation', 'Pending') OR `status` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='相关项目表:管理文献的相关项(撤稿、勘误、评论等)';


-- ============================================================
-- 表 6: cat_supplemental_object (补充对象表)
-- ============================================================
-- 表说明: 管理补充材料(图表、数据集、代码等),支持访问控制和许可证管理
-- 记录数预估: 初始 400万 / 年增长 120万 / 5年规模 1000万
-- 主要查询场景:
--   1. 查询某文献的所有补充材料(>1000次/天,高频)
--   2. 按 object_type 查询(100-500次/天,中频)
--   3. 按 is_public 筛选公开材料(<100次/天,低频)
-- ============================================================

DROP TABLE IF EXISTS `cat_supplemental_object`;

CREATE TABLE `cat_supplemental_object` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `object_type` VARCHAR(50) NOT NULL COMMENT '对象类型(Figure/Table/Dataset/Code/Video/Audio/Document/Presentation/Other)',
    `content_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '内容类型(MIME,如"application/pdf")',
    `title` VARCHAR(500) NULL DEFAULT NULL COMMENT '标题',
    `description` VARCHAR(1000) NULL DEFAULT NULL COMMENT '描述',
    `url` VARCHAR(500) NULL DEFAULT NULL COMMENT '访问URL',
    `file_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '文件名',
    `file_size` BIGINT NULL DEFAULT NULL COMMENT '文件大小(字节)',
    `doi` VARCHAR(100) NULL DEFAULT NULL COMMENT '补充材料DOI',
    `license` VARCHAR(50) NULL DEFAULT NULL COMMENT '许可证(如"CC-BY","CC0")',
    `authors` VARCHAR(500) NULL DEFAULT NULL COMMENT '作者/贡献者',
    `order_num` INTEGER NOT NULL DEFAULT 1 COMMENT '顺序号(用于排序显示)',
    `is_public` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否公开(0=否,1=是)',
    `available_date` DATE NULL DEFAULT NULL COMMENT '可用日期(延迟发布支持)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '对象元数据(灵活扩展)',

    -- ========================================
    -- 审计字段（极简版）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '文献ID索引,支持查询某文献的所有补充材料(高频)',
    INDEX `idx_object_type` (`object_type`) COMMENT '对象类型索引,支持按类型筛选(中频)',
    INDEX `idx_public` (`is_public`) COMMENT '公开标志索引,支持筛选公开材料',
    INDEX `idx_doi` (`doi`) COMMENT 'DOI索引,支持按DOI查询补充材料',
    INDEX `idx_available_date` (`available_date`) COMMENT '可用日期索引,支持按可用日期筛选',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_object_type` CHECK (`object_type` IN ('Figure', 'Table', 'Dataset', 'Code', 'Video', 'Audio', 'Document', 'Presentation', 'Other'))

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='补充对象表:管理补充材料(图表、数据集、代码等)';


-- ============================================================
-- 表 7: cat_publication_history (发布历史表)
-- ============================================================
-- 表说明: 记录文献生命周期事件(投稿、接收、发表等),支持时序性保障
-- 记录数预估: 初始 1200万 / 年增长 400万 / 5年规模 3000万
-- 主要查询场景:
--   1. 查询某文献的完整历史时间线(>1000次/天,高频)
--   2. 按 event_type 统计审稿周期(100-500次/天,中频)
--   3. 按 event_date 时间范围查询(<100次/天,低频)
-- ============================================================

DROP TABLE IF EXISTS `cat_publication_history`;

CREATE TABLE `cat_publication_history` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型(Submitted/Received/Revised/Accepted/Rejected/Published Online/Published Print/Corrected/Retracted/Reinstated/Updated/Indexed/Archived)',
    `event_date` DATE NOT NULL COMMENT '事件日期',
    `date_precision` VARCHAR(10) NULL DEFAULT NULL COMMENT '日期精度(day/month/year)',
    `description` VARCHAR(500) NULL DEFAULT NULL COMMENT '事件描述',
    `actor` VARCHAR(100) NULL DEFAULT NULL COMMENT '执行者/机构',
    `previous_status` VARCHAR(100) NULL DEFAULT NULL COMMENT '之前状态',
    `new_status` VARCHAR(100) NULL DEFAULT NULL COMMENT '新状态',
    `order_num` INTEGER NOT NULL COMMENT '事件顺序号(同一文献内唯一)',
    `is_public` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否公开(0=否,1=是)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '事件元数据(灵活扩展)',

    -- ========================================
    -- 审计字段（极简版）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_history_order` (`publication_id`, `order_num`) COMMENT '文献+顺序号唯一索引,保证顺序号在同一文献内唯一',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '文献ID索引,支持查询某文献的所有历史事件(高频)',
    INDEX `idx_event_type` (`event_type`) COMMENT '事件类型索引,支持按类型筛选(中频)',
    INDEX `idx_event_date` (`event_date`) COMMENT '事件日期索引,支持按日期排序和统计',

    -- 复合索引
    INDEX `idx_pub_date` (`publication_id`, `event_date`, `order_num`) COMMENT '文献+日期+顺序号复合索引,优化时间线查询',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_event_type` CHECK (`event_type` IN ('Submitted', 'Received', 'Revised', 'Accepted', 'Rejected', 'Published Online', 'Published Print', 'Corrected', 'Retracted', 'Reinstated', 'Updated', 'Indexed', 'Archived')),
    CONSTRAINT `chk_date_precision` CHECK (`date_precision` IN ('day', 'month', 'year') OR `date_precision` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='发布历史表:记录文献生命周期事件,支持时序性保障';


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
      'cat_funding',
      'cat_publication_funding',
      'cat_reference',
      'cat_external_reference',
      'cat_related_item',
      'cat_supplemental_object',
      'cat_publication_history'
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
      'cat_funding',
      'cat_publication_funding',
      'cat_reference',
      'cat_external_reference',
      'cat_related_item',
      'cat_supplemental_object',
      'cat_publication_history'
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
      'cat_funding',
      'cat_publication_funding',
      'cat_reference',
      'cat_external_reference',
      'cat_related_item',
      'cat_supplemental_object',
      'cat_publication_history'
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
      'cat_funding',
      'cat_publication_funding',
      'cat_reference',
      'cat_external_reference',
      'cat_related_item',
      'cat_supplemental_object',
      'cat_publication_history'
  )
GROUP BY TABLE_NAME
ORDER BY TABLE_NAME;


-- ============================================================
-- 设计决策摘要
-- ============================================================
--
-- 决策1: 资助信息去重策略
--   - 使用 dedup_key 字段(MD5哈希,基于agency_name+grant_id)
--   - 理由: 减少60%+冗余,覆盖率90%+
--   - 优势: 便于统计资助影响力和文献产出
--
-- 决策2: 引用的双重关联设计
--   - cited_publication_id(FK) + cited_pmid/doi(VARCHAR)
--   - 理由: 库内引用(~30%)支持引用网络,库外引用(~70%)保留完整信息
--   - 优势: 自动升级,新文献入库时将库外引用升级为库内引用
--
-- 决策3: 外部引用 vs 参考文献分离
--   - 分离存储在 cat_reference 和 cat_external_reference 两张表
--   - 理由: 字段差异大,查询模式不同,语义清晰
--   - 优势: 字段精简,查询高效,扩展性强
--
-- 决策4: 相关项类型设计
--   - 采用单表 + 类型枚举方式(12种关联类型)
--   - 理由: 覆盖全面,查询简单,扩展灵活
--   - 优势: 支持撤稿监测和质量控制
--
-- 决策5: 补充材料访问控制
--   - is_public + license + available_date 组合策略
--   - 理由: 明确权限,许可证管理,延迟发布支持
--   - 优势: 审计友好,记录访问日志
--
-- 决策6: 历史事件的时序性保障
--   - event_date + order_num 双重保障策略
--   - 理由: 确保同一天事件的顺序,正确表达不完整日期
--   - 优势: 查询高效,数据可靠
--
-- ============================================================


-- ============================================================
-- 索引选择性分析
-- ============================================================
--
-- 高选择性索引(>0.8):
--   - uk_dedup_key (0.95) - 去重键高度唯一
--   - uk_pub_funding (1.00) - 组合绝对唯一
--   - uk_reference_num (1.00) - 组合绝对唯一
--   - uk_external_ref (1.00) - 三字段组合绝对唯一
--   - uk_history_order (1.00) - 组合绝对唯一
--   - idx_cited_pmid (0.90) - PMID几乎唯一
--   - idx_cited_doi (0.90) - DOI几乎唯一
--   - idx_publication (0.85-0.90) - 每篇文献平均2-5个关联
--   - idx_funding (0.85) - ROR ID几乎唯一
--   - idx_accession (0.85) - 登录号高度唯一
--   - idx_doi (0.95) - DOI几乎唯一
--   - idx_pub_date (0.95) - 三字段组合高度唯一
--
-- 中选择性索引(0.5-0.8):
--   - idx_agency (0.75) - 约2000+机构
--   - idx_grant_id (0.80) - 项目编号重复度低
--   - idx_funder_id (0.85) - Funder ID高度唯一
--   - idx_cited_pub (0.80) - 库内被引文献查询
--   - idx_year (0.50) - 跨度100年
--   - idx_database (0.70) - 约50+数据库
--   - idx_date (0.60) - 跨度10+年
--   - idx_available_date (0.60) - 跨度10+年
--   - idx_event_date (0.60) - 跨度70年
--
-- 低选择性索引(<0.5,但业务需求强烈):
--   - idx_funding_type (0.30) - 仅6个枚举值
--   - idx_primary (0.20) - 仅两值(0/1)
--   - idx_retracted (0.10) - 仅两值(0/1),但撤稿监测是法规要求
--   - idx_relationship (0.40) - 12种枚举值
--   - idx_status (0.30) - 4种枚举值
--   - idx_object_type (0.50) - 9种枚举值
--   - idx_public (0.40) - 仅两值(0/1)
--   - idx_event_type (0.40) - 13种枚举值
--
-- ============================================================


-- ============================================================
-- 存储预估(5年)
-- ============================================================
--
-- | 表名                       | 5年规模    | 单行大小 | 存储预估 | 索引预估 | 总计    |
-- |---------------------------|-----------|---------|---------|---------|---------|
-- | cat_funding               | 700万行   | 0.8 KB  | 5.6 GB  | 2.0 GB  | 7.6 GB  |
-- | cat_publication_funding   | 1750万行  | 0.3 KB  | 5.25 GB | 2.0 GB  | 7.25 GB |
-- | cat_reference             | 5.25亿行  | 0.9 KB  | 472.5 GB| 180 GB  | 652.5 GB|
-- | cat_external_reference    | 500万行   | 0.5 KB  | 2.5 GB  | 1.0 GB  | 3.5 GB  |
-- | cat_related_item          | 100万行   | 0.6 KB  | 600 MB  | 250 MB  | 850 MB  |
-- | cat_supplemental_object   | 1000万行  | 0.7 KB  | 7.0 GB  | 2.5 GB  | 9.5 GB  |
-- | cat_publication_history   | 3000万行  | 0.4 KB  | 12 GB   | 4.5 GB  | 16.5 GB |
-- | **总计**                  | 5.697亿行 | -       | 505.5 GB| 192.3 GB| 697.8 GB|
--
-- 说明:
-- - reference表占总存储的93%+,是存储和性能优化的重点
-- - 单行大小包含业务字段+审计字段
-- - 索引预估约为数据大小的30%-40%
-- - 实际存储需考虑InnoDB页填充率(通常70%-80%)
-- - 建议预留2倍空间(1.5 TB+)用于索引膨胀和临时表
-- - reference表需考虑分区策略(按publication_year分区)
--
-- ============================================================


-- ============================================================
-- 关键技术考虑
-- ============================================================
--
-- 1. reference表分区策略:
--    - 数据量5.25亿行,建议按publication_year范围分区
--    - 每个分区覆盖5年数据,预计15个分区
--    - 查询优化:大部分查询集中在最近10年数据
--
-- 2. 索引维护:
--    - reference表索引总大小180 GB+,需定期OPTIMIZE TABLE
--    - 考虑使用Percona Toolkit的pt-online-schema-change进行在线索引维护
--
-- 3. 存储优化:
--    - 考虑使用压缩表(ROW_FORMAT=COMPRESSED)
--    - reference表预计压缩比50%,可节省236 GB存储
--
-- 4. 双重关联优先级:
--    - 优先通过PMID匹配库内文献
--    - 其次通过DOI匹配
--    - 无法匹配时保留PMID/DOI
--    - 新文献入库时自动升级库外引用为库内引用
--
-- 5. 去重策略实现:
--    - dedup_key = MD5(normalize(agency_name) + "|" + normalize(grant_id))
--    - 规范化逻辑:小写、单空格、移除特殊字符
--    - 插入前去重查询,复用现有记录
--
-- ============================================================


-- ============================================================
-- 执行完成提示
-- ============================================================
--
-- ✅ DDL执行完成后,请执行上述验证脚本,确认:
--    1. 所有7张表创建成功
--    2. 所有索引正确创建(主键7+唯一4+普通32+复合1=44个)
--    3. CHECK约束生效(14个约束)
--    4. 字符集和排序规则正确(utf8mb4_unicode_ci)
--
-- ⚠️ 注意事项:
--    1. 确保核心实体表(cat_publication)已创建
--    2. 冗余字段需要应用层保证同步一致性
--    3. reference表数据量极大,考虑分区策略
--    4. 双重关联设计需要应用层实现匹配逻辑
--    5. 所有TIMESTAMP字段使用微秒精度(6位小数)
--
-- 📝 后续步骤:
--    1. 创建外键约束(如果启用外键)
--    2. 实现reference表分区(按publication_year)
--    3. 配置定期索引维护任务
--    4. 监控索引使用情况和查询性能
--    5. 实现应用层去重逻辑和双重关联匹配
--
-- ============================================================

-- ============================================================
-- 文件结束
-- ============================================================
