-- ============================================================
-- Patra Catalog 数据库 - 研究者表 DDL
-- ============================================================
-- 版本: V1.3.1
-- 领域: Organization 领域
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: 研究者及关联表（3张表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- 研究者相关:
--   1. cat_investigator (研究者表) - 无依赖（独立表）
--   2. cat_publication_investigator (文献-研究者关联表) - 依赖 cat_publication, cat_investigator
--   3. cat_publication_personal_name_subject (人物主题表) - 依赖 cat_publication
-- ============================================================


-- ============================================================
-- 表 1: cat_investigator (研究者表)
-- ============================================================
-- 表说明: 存储研究者信息(非作者的研究人员,如临床试验 PI),支持去重
-- 记录数预估: 初始 20万 / 年增长 6万 / 5年规模 50万
-- 主要查询场景:
--   1. 按 ORCID 查询研究者(>200次/天,中频)
--   2. 按去重键查询研究者(>300次/天,中频,去重用)
--   3. 按邮箱查询研究者(<100次/天,低频)
--   4. 按研究者类型筛选(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_investigator` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT NOT NULL COMMENT '主键,雪花算法生成',
    `last_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '姓(Last Name/Family Name)',
    `fore_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '名(First Name/Given Name)',
    `initials` VARCHAR(50) NULL DEFAULT NULL COMMENT '姓名缩写(如"J.K.")',
    `suffix` VARCHAR(50) NULL DEFAULT NULL COMMENT '后缀(如"Jr.","III","MD","PhD")',
    `orcid` VARCHAR(50) NULL DEFAULT NULL COMMENT 'ORCID 标识符(格式:0000-0001-2345-6789)',
    `researcher_id` VARCHAR(100) NULL DEFAULT NULL COMMENT '研究者ID(ResearcherID/Publons)',
    `investigator_type` VARCHAR(100) NULL DEFAULT NULL COMMENT '研究者类型(如"PI","CoI","Collaborator")',
    `affiliation_name` VARCHAR(500) NULL DEFAULT NULL COMMENT '机构名称(文本,不关联 organization 表)',
    `email` VARCHAR(255) NULL DEFAULT NULL COMMENT '邮箱地址',
    `dedup_key` VARCHAR(255) NULL DEFAULT NULL COMMENT '复合去重键(应用层计算,MD5哈希,可选)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '研究者元数据(灵活扩展)',

    -- ========================================
    -- 审计字段
    -- ========================================
    `record_remarks` TEXT NULL DEFAULT NULL COMMENT '备注/变更日志',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `created_by` BIGINT NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名(冗余-审计友好)',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',
    `updated_by` BIGINT NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名(冗余-审计友好)',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次更新自增)',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP(二进制,支持IPv4/IPv6)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_orcid` (`orcid`) COMMENT 'ORCID 索引,支持按 ORCID 查询研究者',
    INDEX `idx_dedup_key` (`dedup_key`) COMMENT '去重键索引,支持研究者去重和合并(核心业务)',
    INDEX `idx_email` (`email`) COMMENT '邮箱索引,支持按邮箱查询研究者(联系场景)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='研究者表:存储非作者的研究人员信息(如临床试验PI)';


-- ============================================================
-- 表 2: cat_publication_investigator (文献-研究者关联表)
-- ============================================================
-- 表说明: 管理文献与研究者的多对多关系,记录研究者角色和职责
-- 记录数预估: 初始 50万 / 年增长 20万 / 5年规模 150万
-- 主要查询场景:
--   1. 查询某文献的所有研究者(>300次/天,中频)
--   2. 查询某研究者的所有文献(>200次/天,中频)
--   3. 筛选 PI(主要研究者)文献(100-200次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_investigator` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT NOT NULL COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `investigator_id` BIGINT NOT NULL COMMENT '研究者ID(外键:cat_investigator.id)',
    `role` VARCHAR(100) NULL DEFAULT NULL COMMENT '角色(如"principal","co-investigator","coordinator")',
    `is_contact` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否联系人(0=否,1=是)',
    `order_num` INT NULL DEFAULT NULL COMMENT '顺序号(多个研究者时排序)',
    `responsibility` VARCHAR(1000) NULL DEFAULT NULL COMMENT '职责描述(文本)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '关联元数据(灵活扩展)',

    -- ========================================
    -- 审计字段
    -- ========================================
    `record_remarks` TEXT NULL DEFAULT NULL COMMENT '备注/变更日志',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `created_by` BIGINT NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名(冗余-审计友好)',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',
    `updated_by` BIGINT NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名(冗余-审计友好)',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次更新自增)',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP(二进制,支持IPv4/IPv6)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_pub_investigator` (`publication_id`, `investigator_id`) COMMENT '防止同一研究者在同一文献重复关联',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,支持查询某文献的所有研究者(中频)',
    INDEX `idx_investigator` (`investigator_id`) COMMENT '研究者索引,支持查询某研究者的所有文献(中频)',
    INDEX `idx_role` (`role`) COMMENT '角色索引,支持按角色筛选(如查询 PI 的所有文献)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='文献-研究者关联表:管理研究者角色和职责';


-- ============================================================
-- 表 3: cat_publication_personal_name_subject (人物主题表)
-- ============================================================
-- 表说明: 存储文献的主题人物信息(传记类、历史类、纪念类文献)
-- 记录数预估: 初始 2万 / 年增长 0.6万 / 5年规模 5万
-- 主要查询场景:
--   1. 查询某文献的主题人物(>100次/天,中频)
--   2. 按主题类型筛选(如查询所有传记类文献)(<100次/天,低频)
--   3. 按姓氏查询历史人物(<50次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_personal_name_subject` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT NOT NULL COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `last_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '姓(Last Name/Family Name)',
    `fore_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '名(First Name/Given Name)',
    `initials` VARCHAR(50) NULL DEFAULT NULL COMMENT '姓名缩写(如"J.K.")',
    `suffix` VARCHAR(100) NULL DEFAULT NULL COMMENT '后缀/头衔(如"Jr.","King","Emperor")',
    `dates` VARCHAR(100) NULL DEFAULT NULL COMMENT '生卒年代(如"1820-1910","c. 460 BC - c. 370 BC")',
    `description` VARCHAR(500) NULL DEFAULT NULL COMMENT '人物描述(简短介绍)',
    `subject_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '主题类型(如"biography","history","memorial")',
    `identifier` VARCHAR(100) NULL DEFAULT NULL COMMENT '人物标识符(如 VIAF ID, Wikidata ID)',
    `order_num` INT NULL DEFAULT NULL COMMENT '顺序号(多个主题人物时排序)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '人物元数据(灵活扩展)',

    -- ========================================
    -- 审计字段
    -- ========================================
    `record_remarks` TEXT NULL DEFAULT NULL COMMENT '备注/变更日志',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `created_by` BIGINT NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名(冗余-审计友好)',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',
    `updated_by` BIGINT NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名(冗余-审计友好)',
    `version` BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次更新自增)',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP(二进制,支持IPv4/IPv6)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,支持查询某文献的主题人物(中频)',
    INDEX `idx_subject_type` (`subject_type`) COMMENT '主题类型索引,支持按类型筛选(如查询所有传记类文献)',
    INDEX `idx_last_name` (`last_name`) COMMENT '姓氏索引,支持按姓氏查询历史人物(低频但有需求)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='人物主题表:存储传记类/历史类/纪念类文献的主题人物';
