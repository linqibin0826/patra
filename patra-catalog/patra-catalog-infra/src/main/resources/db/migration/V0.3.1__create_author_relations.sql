-- ============================================================
-- Patra Catalog 数据库 - 作者关联表 DDL（大数据量）
-- ============================================================
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: 作者与文献、机构的关联表（2张表，总计约3亿条记录）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_unicode_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- 注意: 本文件包含的表数据量巨大（合计约3亿条），单独管理便于后续分表/分库决策
--
-- 1. cat_publication_author (文献-作者关联表) - 依赖 cat_publication, cat_author
--    预估数据量: 1.4亿条
-- 2. cat_author_affiliation (作者-机构关联表) - 依赖 cat_author, cat_affiliation
--    预估数据量: 1.75亿条
-- ============================================================


-- ============================================================
-- 表 1: cat_publication_author (文献-作者关联表)
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
    `deleted_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

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
    INDEX `idx_corresponding` (`is_corresponding_author`) COMMENT '通讯作者索引,支持筛选通讯作者文献(联系查询)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='文献-作者关联表:管理作者顺序和角色(大数据量表,预估1.4亿条)';


-- ============================================================
-- 表 2: cat_author_affiliation (作者-机构关联表)
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
    `deleted_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_author` (`author_id`) COMMENT '作者索引,支持查询某作者的所有机构(高频)',
    INDEX `idx_affiliation` (`affiliation_id`) COMMENT '机构索引,支持查询某机构的所有作者(中频)',
    INDEX `idx_publication` (`publication_id`) COMMENT '文献索引,支持查询某文献的作者机构(高频)',

    -- 复合索引
    INDEX `idx_author_primary` (`author_id`, `is_primary`) COMMENT '作者+主要机构复合索引,支持快速查询作者的主要机构'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='作者-机构关联表:支持时间维度追踪和文献上下文(大数据量表,预估1.75亿条)';
