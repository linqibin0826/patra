-- ============================================================
-- Patra Catalog 数据库 - Organization 聚合表 DDL
-- ============================================================
-- 版本: V1.3.0
-- 领域: Organization 领域
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: Organization 聚合（基于 ROR Schema v2.0）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- Organization 聚合（基于 ROR Schema v2.0）:
--   1. cat_organization (机构主表) - 无依赖（聚合根）
--   2. cat_organization_name (机构名称表) - 依赖 cat_organization
--   3. cat_organization_external_id (外部标识符表) - 依赖 cat_organization
--   4. cat_organization_relation (机构关系表) - 依赖 cat_organization
--   5. cat_organization_location (地理位置表) - 依赖 cat_organization
-- ============================================================


-- ============================================================
-- 表 1: cat_organization (机构主表)
-- ============================================================
-- 表说明: 基于 ROR Schema v2.0 设计的机构聚合根
-- 记录数预估: 初始 12万（全球研究机构）/ 年增长 5千 / 5年规模 15万
-- 主要查询场景:
--   1. 按 ROR ID 查询机构(>500次/天,高频)
--   2. 按显示名称搜索机构(>300次/天,中频)
--   3. 按状态筛选机构(100-500次/天,中频)
--   4. 按去重键查询机构(>1000次/天,高频,去重用)
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_organization` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT NOT NULL COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 核心标识
    -- ========================================
    `ror_id` VARCHAR(50) NOT NULL COMMENT 'ROR ID(唯一标识符,如"03vek6s52",不含URL前缀)',

    -- ========================================
    -- 基本信息
    -- ========================================
    `display_name` VARCHAR(500) NOT NULL COMMENT '显示名称(主名称)',
    `status` VARCHAR(20) NOT NULL COMMENT '机构状态:ACTIVE/INACTIVE/WITHDRAWN',
    `established` INT NULL DEFAULT NULL COMMENT '成立年份',

    -- ========================================
    -- JSON 存储字段
    -- ========================================
    `types` JSON NULL DEFAULT NULL COMMENT '机构类型集合(JSON数组,如["EDUCATION","HEALTHCARE"])',
    `domains` JSON NULL DEFAULT NULL COMMENT '域名列表(JSON数组,如["harvard.edu","hms.harvard.edu"])',
    `links` JSON NULL DEFAULT NULL COMMENT '网站链接(JSON数组,包含website和wikipedia)',
    `admin_info` JSON NULL DEFAULT NULL COMMENT 'ROR管理元数据(JSON对象,包含创建/修改日期及版本)',

    -- ========================================
    -- 扩展字段
    -- ========================================
    `dedup_key` VARCHAR(32) NULL DEFAULT NULL COMMENT '去重键(MD5哈希,用于跨数据源去重)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '扩展元数据(JSON,存储来源特定的额外信息)',

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
    `deleted_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '软删除时间(NULL表示未删除)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_ror_id` (`ror_id`) COMMENT 'ROR ID唯一索引',

    -- 普通索引
    INDEX `idx_org_display_name` (`display_name`(255)) COMMENT '显示名称索引(前缀索引)',
    INDEX `idx_org_status` (`status`) COMMENT '状态索引,支持按状态筛选',
    INDEX `idx_org_dedup_key` (`dedup_key`) COMMENT '去重键索引,支持机构去重'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='机构主表:基于ROR Schema v2.0的机构聚合根';


-- ============================================================
-- 表 2: cat_organization_name (机构名称表)
-- ============================================================
-- 表说明: 存储机构的多语言名称,包括ROR显示名、标签、别名、缩写等
-- 记录数预估: 平均每机构4个名称,初始48万 / 5年规模60万
-- 主要查询场景:
--   1. 按机构ID查询所有名称(>500次/天,高频)
--   2. 按名称值搜索机构(>300次/天,中频)
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_organization_name` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT NOT NULL COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 业务字段
    -- ========================================
    `org_id` BIGINT NOT NULL COMMENT '所属机构ID(逻辑外键)',
    `value` VARCHAR(500) NOT NULL COMMENT '名称文本',
    `types` JSON NULL DEFAULT NULL COMMENT '名称类型集合(JSON数组,如["LABEL","ALIAS"])',
    `lang` VARCHAR(10) NULL DEFAULT NULL COMMENT '语言代码(ISO 639-1/639-3,可能为null)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_org_name` (`org_id`, `value`(255), `lang`) COMMENT '同一机构相同名称值和语言组合唯一',

    -- 普通索引
    INDEX `idx_org_name_org_id` (`org_id`) COMMENT '机构ID索引',
    INDEX `idx_org_name_value` (`value`(255)) COMMENT '名称值索引(前缀索引)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='机构名称表:存储机构的多语言名称';


-- ============================================================
-- 表 3: cat_organization_external_id (机构外部标识符表)
-- ============================================================
-- 表说明: 存储机构在其他系统中的标识符,如GRID、ISNI、Wikidata等
-- 记录数预估: 平均每机构3个外部ID,初始36万 / 5年规模45万
-- 主要查询场景:
--   1. 按机构ID查询所有外部标识符(>300次/天,中频)
--   2. 按首选标识符值查询(>500次/天,高频)
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_organization_external_id` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT NOT NULL COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 业务字段
    -- ========================================
    `org_id` BIGINT NOT NULL COMMENT '所属机构ID(逻辑外键)',
    `id_type` VARCHAR(20) NOT NULL COMMENT '标识符类型代码:GRID/ISNI/WIKIDATA/FUNDREF/RINGGOLD',
    `all_values` JSON NOT NULL COMMENT '所有标识符值(JSON数组,某些类型可能有多个值)',
    `preferred_value` VARCHAR(100) NOT NULL COMMENT '首选标识符值(用于快速查询和显示)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_org_ext_id_type` (`org_id`, `id_type`) COMMENT '每机构每类型只能有一个外部标识符',

    -- 普通索引
    INDEX `idx_org_ext_id_org_id` (`org_id`) COMMENT '机构ID索引',
    INDEX `idx_org_ext_id_preferred` (`preferred_value`) COMMENT '首选值索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='机构外部标识符表:存储GRID/ISNI/Wikidata等外部标识';


-- ============================================================
-- 表 4: cat_organization_relation (机构关系表)
-- ============================================================
-- 表说明: 存储机构之间的关系,如父子机构、继任/前任等
-- 记录数预估: 平均每机构1.5个关系,初始18万 / 5年规模25万
-- 主要查询场景:
--   1. 按机构ID查询所有关系(>200次/天,中频)
--   2. 按关联ROR ID查询(>300次/天,中频,用于关系建立)
--   3. 按关联机构ID查询(>200次/天,中频,内部查询)
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_organization_relation` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT NOT NULL COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 业务字段
    -- ========================================
    `org_id` BIGINT NOT NULL COMMENT '源机构ID(逻辑外键)',
    `relation_type` VARCHAR(20) NOT NULL COMMENT '关系类型代码:PARENT/CHILD/RELATED/SUCCESSOR/PREDECESSOR',
    `related_ror_id` VARCHAR(50) NOT NULL COMMENT '关联机构的ROR ID(用于导入时关系建立)',
    `related_label` VARCHAR(500) NULL DEFAULT NULL COMMENT '关联机构的显示名称(冗余,避免JOIN)',
    `related_org_id` BIGINT NULL DEFAULT NULL COMMENT '关联机构的内部ID(延迟填充,可能为null)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_org_relation` (`org_id`, `relation_type`, `related_ror_id`) COMMENT '同一机构相同类型相同关联ROR唯一',

    -- 普通索引
    INDEX `idx_org_rel_org_id` (`org_id`) COMMENT '源机构ID索引',
    INDEX `idx_org_rel_related_ror_id` (`related_ror_id`) COMMENT '关联ROR ID索引',
    INDEX `idx_org_rel_related_org_id` (`related_org_id`) COMMENT '关联机构内部ID索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='机构关系表:存储父子机构/继任前任等关系';


-- ============================================================
-- 表 5: cat_organization_location (机构地理位置表)
-- ============================================================
-- 表说明: 存储机构的地理位置信息,基于GeoNames数据库
-- 记录数预估: 平均每机构1个位置,初始12万 / 5年规模15万
-- 主要查询场景:
--   1. 按机构ID查询位置(>300次/天,中频)
--   2. 按国家统计机构(100-500次/天,中频)
--   3. 按GeoNames ID查询(>100次/天,低频)
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_organization_location` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT NOT NULL COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 业务字段
    -- ========================================
    `org_id` BIGINT NOT NULL COMMENT '所属机构ID(逻辑外键)',
    `geonames_id` INT NULL DEFAULT NULL COMMENT 'GeoNames地理位置ID',

    -- 洲级别
    `continent_code` VARCHAR(2) NULL DEFAULT NULL COMMENT '洲代码(如NA=北美洲,AS=亚洲)',
    `continent_name` VARCHAR(50) NULL DEFAULT NULL COMMENT '洲名称',

    -- 国家级别
    `country_code` VARCHAR(2) NULL DEFAULT NULL COMMENT '国家代码(ISO 3166-1 alpha-2,如US,CN)',
    `country_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '国家名称',

    -- 省/州级别
    `subdivision_code` VARCHAR(10) NULL DEFAULT NULL COMMENT '省/州代码(ISO 3166-2,如US-CA,CN-BJ)',
    `subdivision_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '省/州名称',

    -- 城市级别
    `city_name` VARCHAR(200) NULL DEFAULT NULL COMMENT '城市名称',

    -- 坐标
    `latitude` DECIMAL(10,7) NULL DEFAULT NULL COMMENT '纬度(-90到+90,精度约1厘米)',
    `longitude` DECIMAL(10,7) NULL DEFAULT NULL COMMENT '经度(-180到+180,精度约1厘米)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_org_location` (`org_id`, `geonames_id`) COMMENT '同一机构相同GeoNames ID唯一',

    -- 普通索引
    INDEX `idx_org_loc_org_id` (`org_id`) COMMENT '机构ID索引',
    INDEX `idx_org_loc_country` (`country_code`) COMMENT '国家代码索引,支持按国家统计',
    INDEX `idx_org_loc_geonames` (`geonames_id`) COMMENT 'GeoNames ID索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='机构地理位置表:基于GeoNames的层级地理信息';
