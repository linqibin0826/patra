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


CREATE TABLE IF NOT EXISTS `cat_reference` (
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


CREATE TABLE IF NOT EXISTS `cat_external_reference` (
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
    `database_version` VARCHAR(50) NULL DEFAULT NULL COMMENT '数据库版本号',
    `order_num` INTEGER NOT NULL DEFAULT 1 COMMENT '顺序号(用于排序显示)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '外部引用元数据(灵活扩展)',

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


CREATE TABLE IF NOT EXISTS `cat_related_item` (
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
    `relationship_date` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '关系建立时间(UTC微秒精度)',
    `initiated_by` VARCHAR(100) NULL DEFAULT NULL COMMENT '发起方(Author/Editor/Publisher/Institution/Third Party)',
    `status` VARCHAR(50) NULL DEFAULT NULL COMMENT '状态(Active/Resolved/Under Investigation/Pending)',
    `order_num` INTEGER NOT NULL DEFAULT 1 COMMENT '顺序号(用于排序显示)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '关系元数据(灵活扩展)',

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


CREATE TABLE IF NOT EXISTS `cat_supplemental_object` (
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


CREATE TABLE IF NOT EXISTS `cat_publication_history` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `event_type` VARCHAR(50) NOT NULL COMMENT '事件类型(Submitted/Received/Revised/Accepted/Rejected/Published Online/Published Print/Corrected/Retracted/Reinstated/Updated/Indexed/Archived)',
    `event_date` TIMESTAMP(6) NOT NULL COMMENT '事件时间(UTC微秒精度)',
    `date_precision` VARCHAR(10) NULL DEFAULT NULL COMMENT '日期精度(day/month/year)',
    `description` VARCHAR(500) NULL DEFAULT NULL COMMENT '事件描述',
    `actor` VARCHAR(100) NULL DEFAULT NULL COMMENT '执行者/机构',
    `previous_status` VARCHAR(100) NULL DEFAULT NULL COMMENT '之前状态',
    `new_status` VARCHAR(100) NULL DEFAULT NULL COMMENT '新状态',
    `order_num` INTEGER NOT NULL COMMENT '事件顺序号(同一文献内唯一)',
    `is_public` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否公开(0=否,1=是)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '事件元数据(灵活扩展)',

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
    CONSTRAINT `chk_history_date_precision` CHECK (`date_precision` IN ('day', 'month', 'year') OR `date_precision` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='发布历史表:记录文献生命周期事件,支持时序性保障';
