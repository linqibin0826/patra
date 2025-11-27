-- ============================================================
-- Patra Catalog 数据库 - 分类与索引表 DDL
-- ============================================================
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: patra_catalog 分类与索引表(12张表)
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_unicode_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- MeSH 体系 (6张表):
--   1. cat_mesh_descriptor (MeSH 主题词表) - 无依赖
--   2. cat_mesh_qualifier (MeSH 限定词表) - 无依赖
--   3. cat_mesh_tree_number (树形编号表) - 依赖 cat_mesh_descriptor
--   4. cat_mesh_entry_term (入口术语表) - 依赖 cat_mesh_descriptor
--   5. cat_mesh_concept (MeSH 概念表) - 依赖 cat_mesh_descriptor
--   6. cat_publication_mesh (文献-MeSH关联表) - 依赖 cat_publication, cat_mesh_descriptor, cat_mesh_qualifier
--
-- 关键词体系 (2张表):
--   7. cat_keyword (关键词表) - 无依赖
--   8. cat_publication_keyword (文献-关键词关联表) - 依赖 cat_publication, cat_keyword
--
-- 出版类型 (2张表):
--   9. cat_publication_type (出版类型表) - 无依赖(支持自引用)
--  10. cat_publication_type_mapping (文献-类型关联表) - 依赖 cat_publication, cat_publication_type
--
-- 物质索引 (2张表):
--  11. cat_substance (物质表) - 无依赖
--  12. cat_publication_substance (文献-物质关联表) - 依赖 cat_publication, cat_substance
-- ============================================================

-- ============================================================
-- 执行说明
-- ============================================================
-- 1. 确保 MySQL 版本 >= 8.0 (需要 CHECK 约束支持)
-- 2. 按顺序执行表创建 (考虑依赖关系)
-- 3. 全文索引需要在表创建后单独执行
-- 4. 建议在测试环境先验证,再在生产环境执行
-- 5. 执行前备份现有数据 (如果有)
-- ============================================================


-- ============================================================
-- 表 1: cat_mesh_descriptor (MeSH 主题词表)
-- ============================================================
-- 表说明: 存储 NLM MeSH(医学主题词表)主题词的核心信息,是医学文献标引的权威词表
-- 记录数预估: 初始 3.5万 / 年增长 500 / 5年规模 3.75万
-- 主要查询场景:
--   1. 按 MeSH UI 精确查询(>2000次/天,高频)
--   2. 按主题词名称查询(>1000次/天,高频)
--   3. 全文检索 scope_note(100-500次/天,中频)
--   4. 按版本筛选有效主题词(>500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_descriptor` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `ui` VARCHAR(10) NOT NULL COMMENT 'MeSH 唯一标识符(格式:D000001-D999999)',
    `name` VARCHAR(255) NOT NULL COMMENT '主题词名称(首选术语,英文)',
    `descriptor_class` VARCHAR(50) NULL DEFAULT NULL COMMENT '主题词类型(枚举:1-Topical/2-PublicationType/3-Geographicals/4-CheckTag)',
    `scope_note` TEXT NULL DEFAULT NULL COMMENT '范围说明(定义和使用指南)',
    `annotation` TEXT NULL DEFAULT NULL COMMENT '注释(索引员使用的说明)',
    `previous_indexing` TEXT NULL DEFAULT NULL COMMENT '之前的索引方式(历史参考)',
    `public_mesh_note` TEXT NULL DEFAULT NULL COMMENT '公共 MeSH 注释(面向用户)',
    `consider_also` TEXT NULL DEFAULT NULL COMMENT '另请参考(相关主题词建议)',
    `date_created` VARCHAR(10) NULL DEFAULT NULL COMMENT '创建日期(格式:YYYYMMDD,如 20230115)',
    `date_revised` VARCHAR(10) NULL DEFAULT NULL COMMENT '修订日期(格式:YYYYMMDD)',
    `date_established` VARCHAR(10) NULL DEFAULT NULL COMMENT '确立日期(格式:YYYYMMDD)',
    `active_status` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否有效(0=已废弃,1=有效)',
    `mesh_version` VARCHAR(10) NULL DEFAULT NULL COMMENT 'MeSH 版本年份(如"2025")',
    `metadata` JSON NULL DEFAULT NULL COMMENT '其他元数据(扩展字段)',

    -- ========================================
    -- 审计字段(简化版)
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
    UNIQUE INDEX `uk_mesh_ui` (`ui`) COMMENT 'MeSH UI 唯一索引,支持高频精确查询(<5ms)',

    -- 普通索引
    INDEX `idx_name` (`name`) COMMENT '主题词名称索引,支持按名称查询',

    -- 复合索引
    INDEX `idx_active_version` (`active_status`, `mesh_version`) COMMENT '有效状态+版本复合索引,筛选某版本的有效主题词',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_descriptor_class` CHECK (`descriptor_class` IN ('1', '2', '3', '4') OR `descriptor_class` IS NULL),
    CONSTRAINT `chk_mesh_version` CHECK (`mesh_version` REGEXP '^[0-9]{4}$' OR `mesh_version` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='MeSH 主题词表:存储 NLM MeSH 主题词核心信息,医学文献标引权威词表';


-- ============================================================
-- 表 2: cat_mesh_qualifier (MeSH 限定词表)
-- ============================================================
-- 表说明: 存储 MeSH 限定词,用于修饰主题词(如"immunology"限定"Antibodies")
-- 记录数预估: 初始 100 / 年增长 5 / 5年规模 125
-- 主要查询场景:
--   1. 按限定词 UI 精确查询(>500次/天,中频)
--   2. 按限定词名称查询(100-500次/天,中频)
--   3. 按缩写查询(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_qualifier` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `ui` VARCHAR(10) NOT NULL COMMENT '限定词唯一标识符(格式:Q000001-Q999999)',
    `name` VARCHAR(100) NOT NULL COMMENT '限定词名称(英文)',
    `abbreviation` VARCHAR(10) NULL DEFAULT NULL COMMENT '限定词缩写(如 DI, GE, IM)',
    `annotation` TEXT NULL DEFAULT NULL COMMENT '注释说明',
    `date_created` VARCHAR(10) NULL DEFAULT NULL COMMENT '创建日期(格式:YYYYMMDD)',
    `date_revised` VARCHAR(10) NULL DEFAULT NULL COMMENT '修订日期(格式:YYYYMMDD)',
    `date_established` VARCHAR(10) NULL DEFAULT NULL COMMENT '确立日期(格式:YYYYMMDD)',
    `active_status` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否有效(0=已废弃,1=有效)',
    `mesh_version` VARCHAR(10) NULL DEFAULT NULL COMMENT 'MeSH 版本年份(如"2025")',
    `history_note` TEXT NULL DEFAULT NULL COMMENT '历史说明(记录限定词的历史使用规则)',
    `online_note` TEXT NULL DEFAULT NULL COMMENT '在线检索说明(检索策略指南)',
    `tree_numbers` JSON NULL DEFAULT NULL COMMENT '树形编号列表(JSON数组,限定词在MeSH层级树中的位置)',

    -- ========================================
    -- 审计字段(简化版)
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
    UNIQUE INDEX `uk_qualifier_ui` (`ui`) COMMENT '限定词 UI 唯一索引,支持精确查询(<5ms)',

    -- 普通索引
    INDEX `idx_name` (`name`) COMMENT '限定词名称索引,支持按名称查询',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_qualifier_mesh_version` CHECK (`mesh_version` REGEXP '^[0-9]{4}$' OR `mesh_version` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='MeSH 限定词表:存储 MeSH 限定词,用于修饰主题词';


-- ============================================================
-- 表 3: cat_mesh_tree_number (MeSH 树形编号表)
-- ============================================================
-- 表说明: 存储 MeSH 主题词的树形编号,支持多位置和层次查询(一个主题词平均 2.3 个位置)
-- 记录数预估: 初始 8万 / 年增长 1000 / 5年规模 8.5万
-- 主要查询场景:
--   1. 按 descriptor_id 查询某主题词的所有位置(>1000次/天,高频)
--   2. 按树形编号前缀查询某分支下的所有主题词(>500次/天,中频)
--   3. 按层级深度查询(100-500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_tree_number` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `descriptor_id` BIGINT UNSIGNED NOT NULL COMMENT '主题词ID(外键:cat_mesh_descriptor.id)',
    `tree_number` VARCHAR(50) NOT NULL COMMENT '树形编号(如 C04.557.337.428)',
    `tree_level` TINYINT NOT NULL COMMENT '层级深度(1-10,自动计算)',
    `is_primary` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否主要位置(0=次要,1=主要)',

    -- ========================================
    -- 审计字段(简化版)
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
    UNIQUE INDEX `uk_tree_number` (`tree_number`) COMMENT '树形编号唯一索引,保证编号唯一性',

    -- 普通索引
    INDEX `idx_descriptor` (`descriptor_id`) COMMENT '主题词索引,支持查询某主题词的所有位置',

    -- 前缀索引(层次查询优化)
    INDEX `idx_tree_prefix` (`tree_number`(20)) COMMENT '树形编号前缀索引,支持层次查询(LIKE "D12.%")',

    -- 复合索引
    INDEX `idx_tree_level` (`tree_level`, `descriptor_id`) COMMENT '层级+主题词复合索引,支持按层级筛选',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_tree_level` CHECK (`tree_level` BETWEEN 1 AND 10)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='MeSH 树形编号表:存储主题词树形编号,支持多位置和层次查询';


-- ============================================================
-- 表 4: cat_mesh_entry_term (MeSH 入口术语表)
-- ============================================================
-- 表说明: 存储 MeSH 主题词的同义词和入口术语,支持模糊检索(如 "A-23187" → "Calcimycin")
-- 记录数预估: 初始 25万 / 年增长 1万 / 5年规模 30万
-- 主要查询场景:
--   1. 按 descriptor_id 查询某主题词的所有同义词(>500次/天,中频)
--   2. 全文检索入口术语(>1000次/天,高频)
--   3. 按词法标记筛选(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_entry_term` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `descriptor_id` BIGINT UNSIGNED NOT NULL COMMENT '主题词ID(外键:cat_mesh_descriptor.id)',
    `term` VARCHAR(255) NOT NULL COMMENT '入口术语/同义词',
    `lexical_tag` VARCHAR(10) NULL DEFAULT NULL COMMENT '词法标记(枚举:NON/PEF/LAB/ABB/ACR/NAM)',
    `is_print_flag` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否打印(0=否,1=是)',
    `record_preferred` VARCHAR(10) NULL DEFAULT NULL COMMENT '记录首选(枚举:Y/N)',
    `is_permuted_term` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否排列术语(0=否,1=是)',

    -- ========================================
    -- 审计字段(简化版)
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
    INDEX `idx_descriptor` (`descriptor_id`) COMMENT '主题词索引,支持查询某主题词的所有入口术语',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_lexical_tag` CHECK (`lexical_tag` IN ('NON', 'PEF', 'LAB', 'ABB', 'ACR', 'NAM') OR `lexical_tag` IS NULL),
    CONSTRAINT `chk_record_preferred` CHECK (`record_preferred` IN ('Y', 'N') OR `record_preferred` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='MeSH 入口术语表:存储主题词同义词和入口术语,支持模糊检索';


-- ============================================================
-- 表 5: cat_mesh_concept (MeSH 概念表)
-- ============================================================
-- 表说明: 存储 MeSH 主题词下的概念,支持概念级别的关联和检索
-- 记录数预估: 初始 18万 / 年增长 5000 / 5年规模 20.5万
-- 主要查询场景:
--   1. 按 descriptor_id 查询某主题词的所有概念(>300次/天,中频)
--   2. 按 concept_ui 精确查询(<500次/天,中频)
--   3. 按 registry_number 查询(化学物质,<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_concept` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `descriptor_id` BIGINT UNSIGNED NOT NULL COMMENT '主题词ID(外键:cat_mesh_descriptor.id)',
    `concept_ui` VARCHAR(10) NOT NULL COMMENT '概念唯一标识符(格式:M000001-M999999)',
    `concept_name` VARCHAR(255) NOT NULL COMMENT '概念名称',
    `is_preferred` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否首选概念(0=否,1=是)',
    `casn1_name` VARCHAR(255) NULL DEFAULT NULL COMMENT 'CAS 类型 1 名称(化学物质专用)',
    `registry_number` VARCHAR(50) NULL DEFAULT NULL COMMENT '注册号(如 CAS 号,EC 号)',
    `scope_note` TEXT NULL DEFAULT NULL COMMENT '范围说明',
    `concept_status` VARCHAR(10) NULL DEFAULT NULL COMMENT '概念状态(枚举值)',

    -- ========================================
    -- 审计字段(简化版)
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
    UNIQUE INDEX `uk_concept_ui` (`concept_ui`) COMMENT '概念 UI 唯一索引,支持精确查询',

    -- 普通索引
    INDEX `idx_descriptor` (`descriptor_id`) COMMENT '主题词索引,支持查询某主题词的所有概念',
    INDEX `idx_registry_number` (`registry_number`) COMMENT '注册号索引,支持化学物质查询'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='MeSH 概念表:存储主题词下的概念,支持概念级别关联和检索';


-- ============================================================
-- 表 6: cat_publication_mesh (文献-MeSH 关联表)
-- ============================================================
-- 表说明: 存储文献的 MeSH 标引,关联文献、主题词、限定词,支持主/副主题标记
-- 记录数预估: 初始 2000万 / 年增长 1600万 / 5年规模 2.8亿
-- 主要查询场景:
--   1. 按 publication_id 查询某文献的所有 MeSH(>3000次/天,高频)
--   2. 按 descriptor_id 查询某主题词的所有文献(>2000次/天,高频)
--   3. 筛选主要主题(is_major_topic=1)(>1000次/天,高频)
--   4. 按限定词筛选(>500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_mesh` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `descriptor_id` BIGINT UNSIGNED NOT NULL COMMENT '主题词ID(外键:cat_mesh_descriptor.id)',
    `qualifier_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '限定词ID(外键:cat_mesh_qualifier.id,可选)',
    `is_major_topic` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否主要主题(0=副主题,1=主要主题,对应 MeSH 星号*)',
    `order_num` INT UNSIGNED NULL DEFAULT NULL COMMENT '顺序号(在同一文献内的排序)',
    `indexing_method` VARCHAR(50) NULL DEFAULT NULL COMMENT '标引方法(如 Manual/Automatic)',

    -- ========================================
    -- 审计字段(极简版)
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

    -- 复合索引(核心查询)
    INDEX `idx_pub_desc` (`publication_id`, `descriptor_id`) COMMENT '文献+主题词复合索引,支持查询文献的MeSH(<20ms)',
    INDEX `idx_desc_pub` (`descriptor_id`, `publication_id`) COMMENT '主题词+文献复合索引,支持查询MeSH的文献(<50ms)',
    INDEX `idx_major_topic` (`descriptor_id`, `is_major_topic`) COMMENT '主题词+主/副主题复合索引,筛选主要主题文献',

    -- 普通索引
    INDEX `idx_qualifier` (`qualifier_id`) COMMENT '限定词索引,支持按限定词筛选文献',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_indexing_method` CHECK (`indexing_method` IN ('Manual', 'Automatic', 'Hybrid') OR `indexing_method` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='文献-MeSH 关联表:存储文献 MeSH 标引,支持主/副主题标记';


-- ============================================================
-- 表 7: cat_keyword (关键词表)
-- ============================================================
-- 表说明: 存储作者/编辑提供的自由关键词,支持规范化去重和频次统计
-- 记录数预估: 初始 300万 / 年增长 100万 / 5年规模 800万
-- 主要查询场景:
--   1. 按规范化关键词查询(去重,>1000次/天,高频)
--   2. 全文检索关键词(>500次/天,中频)
--   3. 按频次排序(热门关键词,100-500次/天,中频)
--   4. 按来源和语言筛选(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_keyword` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `term` VARCHAR(500) NOT NULL COMMENT '关键词原始形式',
    `source` VARCHAR(50) NULL DEFAULT NULL COMMENT '来源(枚举:author/editor/indexer/pubmed)',
    `language` VARCHAR(10) NULL DEFAULT NULL COMMENT '语言代码(ISO 639-1,如 en/zh)',
    `normalized_term` VARCHAR(255) NULL DEFAULT NULL COMMENT '规范化词形(小写+去标点+去空格,用于去重)',
    `frequency` INT UNSIGNED NULL DEFAULT 0 COMMENT '出现频次(被多少篇文献使用)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '元数据(扩展字段)',

    -- ========================================
    -- 审计字段(简化版)
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
    INDEX `idx_normalized` (`normalized_term`) COMMENT '规范化词形索引,支持去重查询',
    INDEX `idx_frequency` (`frequency` DESC) COMMENT '频次索引,支持热门关键词排序',

    -- 复合索引
    INDEX `idx_source_lang` (`source`, `language`) COMMENT '来源+语言复合索引,支持按来源和语言筛选',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_source` CHECK (`source` IN ('author', 'editor', 'indexer', 'pubmed') OR `source` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='关键词表:存储自由关键词,支持规范化去重和频次统计';


-- ============================================================
-- 表 8: cat_publication_keyword (文献-关键词关联表)
-- ============================================================
-- 表说明: 存储文献的关键词标注,关联文献和关键词,支持主/副关键词标记
-- 记录数预估: 初始 1500万 / 年增长 1400万 / 5年规模 8500万
-- 主要查询场景:
--   1. 按 publication_id 查询某文献的所有关键词(>2000次/天,高频)
--   2. 按 keyword_id 查询某关键词的所有文献(>1000次/天,高频)
--   3. 筛选主要关键词(is_major=1)(>500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_keyword` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `keyword_id` BIGINT UNSIGNED NOT NULL COMMENT '关键词ID(外键:cat_keyword.id)',
    `is_major` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否主要关键词(0=副关键词,1=主要关键词)',
    `order_num` INT UNSIGNED NULL DEFAULT NULL COMMENT '顺序号(在同一文献内的排序)',
    `keyword_set` VARCHAR(50) NULL DEFAULT NULL COMMENT '关键词集(如 Author/Editor,区分不同来源)',

    -- ========================================
    -- 审计字段(极简版)
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

    -- 复合索引(核心查询)
    INDEX `idx_pub_keyword` (`publication_id`, `keyword_id`) COMMENT '文献+关键词复合索引,支持查询文献的关键词(<20ms)',
    INDEX `idx_keyword_pub` (`keyword_id`, `publication_id`) COMMENT '关键词+文献复合索引,支持查询关键词的文献(<50ms)',
    INDEX `idx_major` (`keyword_id`, `is_major`) COMMENT '关键词+主/副标记复合索引,筛选主要关键词文献',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_keyword_set` CHECK (`keyword_set` IN ('author', 'editor', 'pubmed') OR `keyword_set` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='文献-关键词关联表:存储文献关键词标注,支持主/副关键词标记';


-- ============================================================
-- 表 9: cat_publication_type (出版类型表)
-- ============================================================
-- 表说明: 存储文献出版类型(如期刊文章、综述、临床试验),支持层次结构
-- 记录数预估: 初始 120 / 年增长 5 / 5年规模 145
-- 主要查询场景:
--   1. 按 type_code 精确查询(>500次/天,中频)
--   2. 按 parent_type 查询子类型(递归查询,100-500次/天,中频)
--   3. 按类型名称查询(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_type` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `type_code` VARCHAR(100) NOT NULL COMMENT '类型代码(英文,唯一标识)',
    `type_name` VARCHAR(200) NOT NULL COMMENT '类型名称(英文)',
    `description` VARCHAR(500) NULL DEFAULT NULL COMMENT '描述说明',
    `vocabulary_source` VARCHAR(50) NULL DEFAULT NULL COMMENT '词表来源(如 MEDLINE/EMBASE/CUSTOM)',
    `parent_type` VARCHAR(100) NULL DEFAULT NULL COMMENT '父类型代码(自引用,支持层次)',
    `is_active` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否有效(0=已废弃,1=有效)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '元数据(扩展字段)',

    -- ========================================
    -- 审计字段(简化版)
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
    UNIQUE INDEX `uk_type_code` (`type_code`) COMMENT '类型代码唯一索引,支持精确查询(<5ms)',

    -- 普通索引
    INDEX `idx_parent` (`parent_type`) COMMENT '父类型索引,支持查询子类型(递归查询)',
    INDEX `idx_active` (`is_active`) COMMENT '有效状态索引,筛选有效类型',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_vocabulary_source` CHECK (`vocabulary_source` IN ('MEDLINE', 'EMBASE', 'CUSTOM') OR `vocabulary_source` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='出版类型表:存储文献类型,支持层次结构(自引用)';


-- ============================================================
-- 表 10: cat_publication_type_mapping (文献-类型关联表)
-- ============================================================
-- 表说明: 存储文献的出版类型标注,关联文献和类型(一篇文献可有多个类型)
-- 记录数预估: 初始 300万 / 年增长 300万 / 5年规模 1800万
-- 主要查询场景:
--   1. 按 publication_id 查询某文献的所有类型(>1500次/天,高频)
--   2. 按 type_id 查询某类型的所有文献(>1000次/天,高频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_type_mapping` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `type_id` BIGINT UNSIGNED NOT NULL COMMENT '类型ID(外键:cat_publication_type.id)',
    `order_num` INT UNSIGNED NULL DEFAULT NULL COMMENT '顺序号(在同一文献内的排序)',

    -- ========================================
    -- 审计字段(极简版)
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

    -- 复合索引(核心查询)
    INDEX `idx_pub_type` (`publication_id`, `type_id`) COMMENT '文献+类型复合索引,支持查询文献的类型(<20ms)',
    INDEX `idx_type_pub` (`type_id`, `publication_id`) COMMENT '类型+文献复合索引,支持查询类型的文献(<50ms)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='文献-类型关联表:存储文献类型标注,一篇文献可有多个类型';


-- ============================================================
-- 表 11: cat_substance (物质表)
-- ============================================================
-- 表说明: 存储化学物质、药物、生物制品等信息,支持 CAS 号等注册号检索
-- 记录数预估: 初始 7万 / 年增长 2000 / 5年规模 7.7万
-- 主要查询场景:
--   1. 按 registry_number 精确查询(>500次/天,中频)
--   2. 按物质名称查询(>300次/天,中频)
--   3. 按物质分类筛选(100-500次/天,中频)
--   4. 全文检索同义词(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_substance` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `registry_number` VARCHAR(100) NOT NULL COMMENT '注册号(如 CAS 号,格式:50-78-2)',
    `name` VARCHAR(500) NOT NULL COMMENT '物质名称(英文)',
    `vocabulary_id` VARCHAR(100) NULL DEFAULT NULL COMMENT '词表ID(外部词表标识)',
    `vocabulary_source` VARCHAR(50) NULL DEFAULT NULL COMMENT '词表来源(如 CAS/EC/UNII/ChEBI/PubChem)',
    `substance_class` VARCHAR(50) NULL DEFAULT NULL COMMENT '物质分类(枚举:chemical/drug/biological/enzyme/antibody/protein)',
    `molecular_formula` VARCHAR(200) NULL DEFAULT NULL COMMENT '分子式(如 C9H8O4)',
    `synonyms` JSON NULL DEFAULT NULL COMMENT '同义词列表(JSON 数组,多语言)',
    `metadata` JSON NULL DEFAULT NULL COMMENT '元数据(扩展字段)',

    -- ========================================
    -- 审计字段(简化版)
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
    UNIQUE INDEX `uk_registry` (`registry_number`) COMMENT '注册号唯一索引,支持 CAS 号精确查询(<5ms)',

    -- 普通索引
    INDEX `idx_name` (`name`) COMMENT '物质名称索引,支持按名称查询',
    INDEX `idx_class` (`substance_class`) COMMENT '物质分类索引,支持按分类筛选',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_vocabulary_source_substance` CHECK (`vocabulary_source` IN ('CAS', 'EC', 'UNII', 'ChEBI', 'PubChem') OR `vocabulary_source` IS NULL),
    CONSTRAINT `chk_substance_class` CHECK (`substance_class` IN ('chemical', 'drug', 'biological', 'enzyme', 'antibody', 'protein') OR `substance_class` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='物质表:存储化学物质/药物/生物制品,支持 CAS 号等注册号检索';


-- ============================================================
-- 表 12: cat_publication_substance (文献-物质关联表)
-- ============================================================
-- 表说明: 存储文献涉及的化学物质标注,关联文献和物质,支持主/副物质标记和角色
-- 记录数预估: 初始 300万 / 年增长 300万 / 5年规模 1800万
-- 主要查询场景:
--   1. 按 publication_id 查询某文献的所有物质(>1000次/天,高频)
--   2. 按 substance_id 查询某物质的所有文献(>500次/天,中频)
--   3. 筛选主要物质(is_major=1)(>300次/天,中频)
--   4. 按物质角色筛选(100-500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_substance` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id` BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `substance_id` BIGINT UNSIGNED NOT NULL COMMENT '物质ID(外键:cat_substance.id)',
    `is_major` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否主要物质(0=副物质,1=主要物质)',
    `role` VARCHAR(100) NULL DEFAULT NULL COMMENT '物质角色(枚举:therapeutic/diagnostic/research_tool/adverse_effect/target/metabolite)',

    -- ========================================
    -- 审计字段(极简版)
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

    -- 复合索引(核心查询)
    INDEX `idx_pub_substance` (`publication_id`, `substance_id`) COMMENT '文献+物质复合索引,支持查询文献的物质(<20ms)',
    INDEX `idx_substance_pub` (`substance_id`, `publication_id`) COMMENT '物质+文献复合索引,支持查询物质的文献(<50ms)',
    INDEX `idx_major_role` (`substance_id`, `is_major`, `role`) COMMENT '物质+主/副标记+角色复合索引,支持多条件筛选',

    -- ========================================
    -- 约束
    -- ========================================
    CONSTRAINT `chk_role` CHECK (`role` IN ('therapeutic', 'diagnostic', 'research_tool', 'adverse_effect', 'target', 'metabolite') OR `role` IS NULL)

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='文献-物质关联表:存储文献物质标注,支持主/副物质标记和角色';


-- ============================================================
-- 全文索引(需要在表创建后单独执行)
-- ============================================================
-- 注意: 全文索引使用 ngram 解析器,支持中文分词(MySQL 5.7.6+)
-- ============================================================

-- cat_mesh_descriptor 表的全文索引
CREATE FULLTEXT INDEX `ft_name_note` ON `cat_mesh_descriptor` (`name`, `scope_note`)
    WITH PARSER ngram
    COMMENT '名称和范围说明全文索引,支持中英文混合检索';

-- cat_mesh_entry_term 表的全文索引
CREATE FULLTEXT INDEX `ft_term` ON `cat_mesh_entry_term` (`term`)
    WITH PARSER ngram
    COMMENT '入口术语全文索引,支持同义词模糊检索';

-- cat_keyword 表的全文索引
CREATE FULLTEXT INDEX `ft_keyword_term` ON `cat_keyword` (`term`)
    WITH PARSER ngram
    COMMENT '关键词全文索引,支持中英文混合检索';

-- cat_substance 表的全文索引(JSON 字段需要 MySQL 5.7.8+)
-- 注意: 由于 synonyms 是 JSON 字段,全文索引可能需要特殊处理
-- 建议在应用层进行同义词检索,或使用专门的搜索引擎(如 Elasticsearch)

