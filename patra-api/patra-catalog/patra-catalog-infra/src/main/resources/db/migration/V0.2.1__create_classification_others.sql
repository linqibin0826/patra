-- ============================================================
-- Patra Catalog 数据库 - 其他分类索引表 DDL
-- ============================================================
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: 关键词、出版类型、物质索引（6张表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_unicode_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- 关键词体系 (2张表):
--   1. cat_keyword (关键词表) - 无依赖
--   2. cat_publication_keyword (文献-关键词关联表) - 依赖 cat_publication, cat_keyword
--
-- 出版类型 (2张表):
--   3. cat_publication_type (出版类型表) - 无依赖(支持自引用)
--   4. cat_publication_type_mapping (文献-类型关联表) - 依赖 cat_publication, cat_publication_type
--
-- 物质索引 (2张表):
--   5. cat_substance (物质表) - 无依赖
--   6. cat_publication_substance (文献-物质关联表) - 依赖 cat_publication, cat_substance
-- ============================================================


-- ============================================================
-- 表 1: cat_keyword (关键词表)
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
    INDEX `idx_normalized` (`normalized_term`) COMMENT '规范化词形索引,支持去重查询',
    INDEX `idx_frequency` (`frequency` DESC) COMMENT '频次索引,支持热门关键词排序',

    -- 复合索引
    INDEX `idx_source_lang` (`source`, `language`) COMMENT '来源+语言复合索引,支持按来源和语言筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='关键词表:存储自由关键词,支持规范化去重和频次统计';


-- ============================================================
-- 表 2: cat_publication_keyword (文献-关键词关联表)
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

    -- 复合索引(核心查询)
    INDEX `idx_pub_keyword` (`publication_id`, `keyword_id`) COMMENT '文献+关键词复合索引,支持查询文献的关键词(<20ms)',
    INDEX `idx_keyword_pub` (`keyword_id`, `publication_id`) COMMENT '关键词+文献复合索引,支持查询关键词的文献(<50ms)',
    INDEX `idx_major` (`keyword_id`, `is_major`) COMMENT '关键词+主/副标记复合索引,筛选主要关键词文献'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='文献-关键词关联表:存储文献关键词标注,支持主/副关键词标记';


-- ============================================================
-- 表 3: cat_publication_type (出版类型表)
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
    UNIQUE INDEX `uk_type_code` (`type_code`) COMMENT '类型代码唯一索引,支持精确查询(<5ms)',

    -- 普通索引
    INDEX `idx_parent` (`parent_type`) COMMENT '父类型索引,支持查询子类型(递归查询)',
    INDEX `idx_active` (`is_active`) COMMENT '有效状态索引,筛选有效类型'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='出版类型表:存储文献类型,支持层次结构(自引用)';


-- ============================================================
-- 表 4: cat_publication_type_mapping (文献-类型关联表)
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

    -- 复合索引(核心查询)
    INDEX `idx_pub_type` (`publication_id`, `type_id`) COMMENT '文献+类型复合索引,支持查询文献的类型(<20ms)',
    INDEX `idx_type_pub` (`type_id`, `publication_id`) COMMENT '类型+文献复合索引,支持查询类型的文献(<50ms)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='文献-类型关联表:存储文献类型标注,一篇文献可有多个类型';


-- ============================================================
-- 表 5: cat_substance (物质表)
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
    UNIQUE INDEX `uk_registry` (`registry_number`) COMMENT '注册号唯一索引,支持 CAS 号精确查询(<5ms)',

    -- 普通索引
    INDEX `idx_name` (`name`) COMMENT '物质名称索引,支持按名称查询',
    INDEX `idx_class` (`substance_class`) COMMENT '物质分类索引,支持按分类筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='物质表:存储化学物质/药物/生物制品,支持 CAS 号等注册号检索';


-- ============================================================
-- 表 6: cat_publication_substance (文献-物质关联表)
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

    -- 复合索引(核心查询)
    INDEX `idx_pub_substance` (`publication_id`, `substance_id`) COMMENT '文献+物质复合索引,支持查询文献的物质(<20ms)',
    INDEX `idx_substance_pub` (`substance_id`, `publication_id`) COMMENT '物质+文献复合索引,支持查询物质的文献(<50ms)',
    INDEX `idx_major_role` (`substance_id`, `is_major`, `role`) COMMENT '物质+主/副标记+角色复合索引,支持多条件筛选'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='文献-物质关联表:存储文献物质标注,支持主/副物质标记和角色';


-- ============================================================
-- 全文索引（需要在表创建后单独执行）
-- ============================================================
-- 注意: 全文索引使用 ngram 解析器,支持中文分词(MySQL 5.7.6+)
-- ============================================================

-- cat_keyword 表的全文索引
CREATE FULLTEXT INDEX `ft_keyword_term` ON `cat_keyword` (`term`)
    WITH PARSER ngram
    COMMENT '关键词全文索引,支持中英文混合检索';
