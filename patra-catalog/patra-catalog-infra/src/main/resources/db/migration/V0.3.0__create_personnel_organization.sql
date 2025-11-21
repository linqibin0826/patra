-- ============================================================
-- Patra Catalog 数据库 - 人员机构模块表 DDL
-- ============================================================
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: patra_catalog 人员机构模块（6张表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_unicode_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- 1. cat_affiliation (机构表) - 无依赖（独立表）
-- 2. cat_investigator (研究者表) - 无依赖（独立表）
-- 3. cat_publication_author (文献-作者关联表) - 依赖 cat_publication, cat_author
-- 4. cat_author_affiliation (作者-机构关联表) - 依赖 cat_author, cat_affiliation, cat_publication(可选)
-- 5. cat_publication_investigator (文献-研究者关联表) - 依赖 cat_publication, cat_investigator
-- 6. cat_personal_name_subject (人物主题表) - 依赖 cat_publication
-- ============================================================

-- ============================================================
-- 执行说明
-- ============================================================
-- 1. 确保 MySQL 版本 >= 8.0（需要 CHECK 约束和部分唯一索引支持）
-- 2. 前置依赖: cat_publication 和 cat_author 表必须已存在
-- 3. 按顺序执行表创建（考虑依赖关系）
-- 4. 建议在测试环境先验证，再在生产环境执行
-- 5. 执行前备份现有数据（如果有）
-- ============================================================


-- ============================================================
-- 表 1: cat_affiliation (机构表)
-- ============================================================
-- 表说明: 存储机构信息,支持多种国际标识符,实现机构标准化和去重
-- 记录数预估: 初始 15万 / 年增长 6万 / 5年规模 45万
-- 主要查询场景:
--   1. 按 ROR ID 查询机构(>500次/天,中频)
--   2. 按 GRID ID 查询机构(>300次/天,中频)
--   3. 按去重键查询机构(>1000次/天,高频,去重用)
--   4. 按国家统计机构(100-500次/天,中频)
--   5. 按机构名称模糊查询(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_affiliation` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `name` VARCHAR(500) NOT NULL COMMENT '机构名称(标准化后)',
    `original_name` VARCHAR(500) NULL DEFAULT NULL COMMENT '原始名称(外部采集,未标准化)',
    `department` VARCHAR(200) NULL DEFAULT NULL COMMENT '部门/科室(如"Department of Medicine")',
    `division` VARCHAR(200) NULL DEFAULT NULL COMMENT '分部/分院(如"School of Medicine")',
    `section` VARCHAR(200) NULL DEFAULT NULL COMMENT '科/组(如"Cardiology Section")',
    `city` VARCHAR(100) NULL DEFAULT NULL COMMENT '城市(如"Boston")',
    `state_province` VARCHAR(100) NULL DEFAULT NULL COMMENT '州/省(如"Massachusetts","广东")',
    `country` VARCHAR(100) NULL DEFAULT NULL COMMENT '国家(ISO 3166-1 alpha-3,如"USA","CHN")',
    `postal_code` VARCHAR(20) NULL DEFAULT NULL COMMENT '邮政编码(如"02115")',
    `ror_id` VARCHAR(50) NULL DEFAULT NULL COMMENT 'ROR 标识符(如"https://ror.org/03vek6s52")',
    `grid_id` VARCHAR(50) NULL DEFAULT NULL COMMENT 'GRID 标识符(如"grid.38142.3c")',
    `isni` VARCHAR(50) NULL DEFAULT NULL COMMENT 'ISNI 标识符(如"0000 0004 1936 8948")',
    `ringgold_id` VARCHAR(50) NULL DEFAULT NULL COMMENT 'Ringgold ID(如"1812")',
    `parent_affiliation` VARCHAR(200) NULL DEFAULT NULL COMMENT '上级机构(如"Harvard University")',
    `affiliation_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '机构类型(如"Education","Healthcare","Company")',
    `dedup_key` VARCHAR(255) NOT NULL COMMENT '复合去重键(应用层计算,MD5哈希)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '机构元数据(灵活扩展)',

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

    -- 唯一索引(部分唯一,仅对非 NULL 值)
    UNIQUE INDEX `uk_ror` (`ror_id`) COMMENT 'ROR ID 唯一索引,支持按 ROR 查询机构(高准确率)',
    UNIQUE INDEX `uk_grid` (`grid_id`) COMMENT 'GRID ID 唯一索引,支持按 GRID 查询机构(高准确率)',

    -- 普通索引
    INDEX `idx_dedup_key` (`dedup_key`) COMMENT '去重键索引,支持机构去重和合并(核心业务)',
    INDEX `idx_country` (`country`) COMMENT '国家索引,支持按国家统计机构产出',
    INDEX `idx_name` (`name`) COMMENT '机构名称索引,支持按名称查询(中频)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='机构表:存储机构信息并支持多种国际标识符';


-- ============================================================
-- 表 2: cat_investigator (研究者表)
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
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `last_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '姓(Last Name/Family Name)',
    `fore_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '名(First Name/Given Name)',
    `initials` VARCHAR(50) NULL DEFAULT NULL COMMENT '姓名缩写(如"J.K.")',
    `suffix` VARCHAR(50) NULL DEFAULT NULL COMMENT '后缀(如"Jr.","III","MD","PhD")',
    `orcid` VARCHAR(50) NULL DEFAULT NULL COMMENT 'ORCID 标识符(格式:0000-0001-2345-6789)',
    `researcher_id` VARCHAR(100) NULL DEFAULT NULL COMMENT '研究者ID(ResearcherID/Publons)',
    `investigator_type` VARCHAR(100) NULL DEFAULT NULL COMMENT '研究者类型(如"PI","CoI","Collaborator")',
    `affiliation_name` VARCHAR(500) NULL DEFAULT NULL COMMENT '机构名称(文本,不关联 affiliation 表)',
    `email` VARCHAR(255) NULL DEFAULT NULL COMMENT '邮箱地址',
    `dedup_key` VARCHAR(255) NOT NULL COMMENT '复合去重键(应用层计算,MD5哈希)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '研究者元数据(灵活扩展)',

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
    INDEX `idx_orcid` (`orcid`) COMMENT 'ORCID 索引,支持按 ORCID 查询研究者',
    INDEX `idx_dedup_key` (`dedup_key`) COMMENT '去重键索引,支持研究者去重和合并(核心业务)',
    INDEX `idx_email` (`email`) COMMENT '邮箱索引,支持按邮箱查询研究者(联系场景)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='研究者表:存储非作者的研究人员信息(如临床试验PI)';


-- ============================================================
-- 表 3: cat_publication_author (文献-作者关联表)
-- ============================================================
-- 表说明: 管理文献与作者的多对多关系,记录作者顺序、角色和贡献类型
-- 记录数预估: 初始 3000万 / 年增长 2200万 / 5年规模 1.4亿
-- 主要查询场景:
--   1. 查询某文献的所有作者(按顺序)(>5000次/天,高频)
--   2. 查询某作者的所有文献(>3000次/天,高频)
--   3. 筛选第一作者文献(>1000次/天,高频)
--   4. 筛选通讯作者文献(>800次/天,中频)
--   5. 按 CRediT 贡献类型筛选(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_author` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `author_id` BIGINT UNSIGNED NOT NULL COMMENT '作者ID(外键:cat_author.id)',
    `author_order` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '作者顺序(1=第一作者,2=第二作者...)',
    `is_first_author` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否第一作者(0=否,1=是)',
    `is_corresponding_author` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否通讯作者(0=否,1=是)',
    `is_equal_contribution` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否同等贡献作者(0=否,1=是)',
    `contribution_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '贡献类型(CRediT分类,如"Conceptualization")',
    `affiliation_string` VARCHAR(1000) NULL DEFAULT NULL COMMENT '原始机构字符串(外部采集,未标准化)',
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
    UNIQUE INDEX `uk_pub_author` (`publication_id`, `author_id`) COMMENT '防止同一作者在同一文献重复关联',
    UNIQUE INDEX `uk_author_order` (`publication_id`, `author_order`) COMMENT '防止同一文献的作者顺序重复,保证学术顺序正确性',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,支持查询某文献的所有作者(高频)',
    INDEX `idx_author` (`author_id`) COMMENT '作者索引,支持查询某作者的所有文献(高频)',
    INDEX `idx_first_author` (`is_first_author`) COMMENT '第一作者索引,支持筛选第一作者文献(学术评价重要指标)',
    INDEX `idx_corresponding` (`is_corresponding_author`) COMMENT '通讯作者索引,支持筛选通讯作者文献(联系查询)',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_author_order` CHECK (`author_order` > 0),
    CONSTRAINT `chk_first_author_consistency` CHECK (
        (`author_order` = 1 AND `is_first_author` = 1) OR
        (`author_order` > 1)
    )

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='文献-作者关联表:管理作者顺序和角色';


-- ============================================================
-- 表 4: cat_author_affiliation (作者-机构关联表)
-- ============================================================
-- 表说明: 管理作者与机构的多对多关系,支持时间维度追踪和特定文献上下文
-- 记录数预估: 初始 5000万 / 年增长 2500万 / 5年规模 1.75亿
-- 主要查询场景:
--   1. 查询某作者的所有机构(按时间排序)(>1000次/天,高频)
--   2. 查询某机构的所有作者(>800次/天,中频)
--   3. 查询某文献的作者机构(>2000次/天,高频)
--   4. 查询作者的主要机构(>500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_author_affiliation` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `author_id` BIGINT UNSIGNED NOT NULL COMMENT '作者ID(外键:cat_author.id)',
    `affiliation_id` BIGINT UNSIGNED NOT NULL COMMENT '机构ID(外键:cat_affiliation.id)',
    `publication_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '文献ID(外键:cat_publication.id,可选)',
    `start_date` DATE NULL DEFAULT NULL COMMENT '开始日期(作者加入机构日期)',
    `end_date` DATE NULL DEFAULT NULL COMMENT '结束日期(作者离开机构日期)',
    `affiliation_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '关联类型(如"current","past","visiting")',
    `is_primary` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否主要机构(0=否,1=是)',
    `order_num` INT UNSIGNED NULL DEFAULT NULL COMMENT '机构顺序(作者有多个机构时排序)',
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

    -- 普通索引
    INDEX `idx_author` (`author_id`) COMMENT '作者索引,支持查询某作者的所有机构(高频)',
    INDEX `idx_affiliation` (`affiliation_id`) COMMENT '机构索引,支持查询某机构的所有作者(中频)',
    INDEX `idx_publication` (`publication_id`) COMMENT '文献索引,支持查询某文献的作者机构(高频)',

    -- 复合索引
    INDEX `idx_author_primary` (`author_id`, `is_primary`) COMMENT '作者+主要机构复合索引,支持快速查询作者的主要机构',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_date_range` CHECK (`end_date` IS NULL OR `start_date` IS NULL OR `end_date` >= `start_date`)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='作者-机构关联表:支持时间维度追踪和文献上下文';


-- ============================================================
-- 表 5: cat_publication_investigator (文献-研究者关联表)
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
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `investigator_id` BIGINT UNSIGNED NOT NULL COMMENT '研究者ID(外键:cat_investigator.id)',
    `role` VARCHAR(100) NULL DEFAULT NULL COMMENT '角色(如"principal","co-investigator","coordinator")',
    `is_contact` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否联系人(0=否,1=是)',
    `order_num` INT UNSIGNED NULL DEFAULT NULL COMMENT '顺序号(多个研究者时排序)',
    `responsibility` VARCHAR(1000) NULL DEFAULT NULL COMMENT '职责描述(文本)',
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
    UNIQUE INDEX `uk_pub_investigator` (`publication_id`, `investigator_id`) COMMENT '防止同一研究者在同一文献重复关联',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,支持查询某文献的所有研究者(中频)',
    INDEX `idx_investigator` (`investigator_id`) COMMENT '研究者索引,支持查询某研究者的所有文献(中频)',
    INDEX `idx_role` (`role`) COMMENT '角色索引,支持按角色筛选(如查询 PI 的所有文献)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='文献-研究者关联表:管理研究者角色和职责';


-- ============================================================
-- 表 6: cat_personal_name_subject (人物主题表)
-- ============================================================
-- 表说明: 存储文献的主题人物信息(传记类、历史类、纪念类文献)
-- 记录数预估: 初始 2万 / 年增长 0.6万 / 5年规模 5万
-- 主要查询场景:
--   1. 查询某文献的主题人物(>100次/天,中频)
--   2. 按主题类型筛选(如查询所有传记类文献)(<100次/天,低频)
--   3. 按姓氏查询历史人物(<50次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_personal_name_subject` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `last_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '姓(Last Name/Family Name)',
    `fore_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '名(First Name/Given Name)',
    `initials` VARCHAR(50) NULL DEFAULT NULL COMMENT '姓名缩写(如"J.K.")',
    `suffix` VARCHAR(100) NULL DEFAULT NULL COMMENT '后缀/头衔(如"Jr.","King","Emperor")',
    `dates` VARCHAR(100) NULL DEFAULT NULL COMMENT '生卒年代(如"1820-1910","c. 460 BC - c. 370 BC")',
    `description` VARCHAR(500) NULL DEFAULT NULL COMMENT '人物描述(简短介绍)',
    `subject_type` VARCHAR(50) NULL DEFAULT NULL COMMENT '主题类型(如"biography","history","memorial")',
    `identifier` VARCHAR(100) NULL DEFAULT NULL COMMENT '人物标识符(如 VIAF ID, Wikidata ID)',
    `order_num` INT UNSIGNED NULL DEFAULT NULL COMMENT '顺序号(多个主题人物时排序)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '人物元数据(灵活扩展)',

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
    INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,支持查询某文献的主题人物(中频)',
    INDEX `idx_subject_type` (`subject_type`) COMMENT '主题类型索引,支持按类型筛选(如查询所有传记类文献)',
    INDEX `idx_last_name` (`last_name`) COMMENT '姓氏索引,支持按姓氏查询历史人物(低频但有需求)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='人物主题表:存储传记类/历史类/纪念类文献的主题人物';
