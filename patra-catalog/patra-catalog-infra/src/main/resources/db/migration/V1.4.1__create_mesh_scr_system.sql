-- ============================================================
-- Patra Catalog 数据库 - MeSH SCR (Supplementary Concept Record) 体系表 DDL
-- ============================================================
-- 版本: V1.4.1
-- 领域: 分类体系
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-12-31
-- 设计范围: MeSH SCR (补充概念记录) 体系（5张新表）
-- 依赖: V1.4.0__create_mesh_system.sql
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- MeSH SCR 体系:
--
-- 新建表（5张）:
--   1. cat_mesh_scr (SCR 主表) - 依赖 cat_mesh_descriptor(导入顺序)
--   2. cat_mesh_scr_heading_mapped_to (映射关系表) - 依赖 cat_mesh_scr, cat_mesh_descriptor, cat_mesh_qualifier
--   3. cat_mesh_scr_source (SCR 来源表) - 依赖 cat_mesh_scr
--   4. cat_mesh_scr_indexing_info (SCR 索引信息表) - 依赖 cat_mesh_scr, cat_mesh_descriptor, cat_mesh_qualifier
--   5. cat_mesh_scr_pharmacological_action (SCR 药理作用表) - 依赖 cat_mesh_scr, cat_mesh_descriptor
--
-- 导入顺序要求:
--   QUALIFIER → DESCRIPTOR → SCR(主表) → SCR 从表
-- ============================================================


-- ============================================================
-- 表 1: cat_mesh_scr (MeSH 补充概念记录主表)
-- ============================================================
-- 表说明: 存储 NLM MeSH 补充概念记录(SCR),主要用于化学物质、药物协议、疾病等补充术语
-- 记录数预估: 初始 31.8万 / 年增长 2万 / 5年规模 42万
-- 更新频率: 每日增量更新(NLM 每工作日发布)
-- 主要查询场景:
--   1. 按 SCR UI 精确查询(>1000次/天,高频)
--   2. 按名称查询(>500次/天,中频)
--   3. 按 SCR 类别筛选(>300次/天,中频)
--   4. 增量同步:按 date_revised 筛选(每日1次,批处理)
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_mesh_scr` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `ui` VARCHAR(10) NOT NULL COMMENT 'SCR 唯一标识符(格式:C000001-C999999)',
    `name` VARCHAR(500) NOT NULL COMMENT '补充概念名称(化学物质名称可能很长)',
    `scr_class` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT 'SCR 类别(1=化学物质,2=协议,3=疾病,4=生物,5=人口群体,6=其他)',
    `note` TEXT NULL DEFAULT NULL COMMENT '说明(用法说明和注释)',
    `frequency` VARCHAR(50) NULL DEFAULT NULL COMMENT '频率(文献中出现的频率描述)',
    `previous_indexing` TEXT NULL DEFAULT NULL COMMENT '之前的索引方式(历史参考)',
    `date_created` DATE NULL DEFAULT NULL COMMENT '创建日期',
    `date_revised` DATE NULL DEFAULT NULL COMMENT '修订日期(用于增量更新)',
    `active_status` BOOLEAN NOT NULL DEFAULT 1 COMMENT '是否有效(0=已废弃,1=有效)',
    `mesh_version` VARCHAR(10) NULL DEFAULT NULL COMMENT 'MeSH 版本年份(如"2025")',
    `metadata` JSON NULL DEFAULT NULL COMMENT '其他元数据(扩展字段)',

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

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_scr_ui` (`ui`) COMMENT 'SCR UI 唯一索引,支持高频精确查询(<5ms)',

    -- 普通索引
    INDEX `idx_name` (`name`(100)) COMMENT '名称前缀索引(前100字符),支持按名称查询',
    INDEX `idx_scr_class` (`scr_class`) COMMENT 'SCR类别索引,支持按类别筛选',

    -- 复合索引
    INDEX `idx_active_version` (`active_status`, `mesh_version`) COMMENT '有效状态+版本复合索引,筛选某版本有效SCR',
    INDEX `idx_revised_version` (`date_revised`, `mesh_version`) COMMENT '修订日期+版本复合索引,支持增量更新查询'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='MeSH 补充概念记录表:存储 NLM MeSH SCR,主要用于化学物质、药物协议、疾病等补充术语';


-- ============================================================
-- 表 2: cat_mesh_scr_heading_mapped_to (SCR 映射关系表)
-- ============================================================
-- 表说明: 存储 SCR 到 Descriptor 的映射关系,这是 SCR 最核心的关系
-- 记录数预估: 初始 50万 / 年增长 3万 / 5年规模 65万 (每个SCR平均1.5个映射)
-- 主要查询场景:
--   1. 按 scr_ui 查询某 SCR 映射到哪些主题词(>1000次/天,高频)
--   2. 按 descriptor_ui 反查哪些 SCR 映射到该主题词(>500次/天,中频)
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_mesh_scr_heading_mapped_to` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `scr_ui` VARCHAR(10) NOT NULL COMMENT 'SCR UI(关联:cat_mesh_scr.ui,格式:C000001)',
    `descriptor_ui` VARCHAR(10) NOT NULL COMMENT '映射到的主题词UI(关联:cat_mesh_descriptor.ui,格式:D000001)',
    `qualifier_ui` VARCHAR(10) NULL DEFAULT NULL COMMENT '限定词UI(关联:cat_mesh_qualifier.ui,格式:Q000001,可选)',
    `major_topic` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否为主要主题词(Major Topic,NLM用*标记)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 复合唯一索引(防重)
    UNIQUE INDEX `uk_scr_desc_qual` (`scr_ui`, `descriptor_ui`, `qualifier_ui`) COMMENT 'SCR+主题词+限定词唯一索引,防止重复映射',

    -- 普通索引
    INDEX `idx_scr_ui` (`scr_ui`) COMMENT 'SCR UI索引,支持查询某SCR的所有映射',
    INDEX `idx_descriptor_ui` (`descriptor_ui`) COMMENT '主题词UI索引,支持反向查询'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='SCR 映射关系表:存储 SCR 到 Descriptor 的映射关系,这是 SCR 最核心的关系';


-- ============================================================
-- 表 3: cat_mesh_scr_source (SCR 来源表)
-- ============================================================
-- 表说明: 存储 SCR 的数据来源(如 NCI/FDA/OMIM/DrugBank 等),SCR 特有
-- 记录数预估: 初始 50万 / 年增长 3万 / 5年规模 65万 (每个SCR平均1.5个来源)
-- 主要查询场景:
--   1. 按 scr_ui 查询某 SCR 的所有来源(>100次/天,低频)
--   2. 按来源类型统计(<50次/天,低频)
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_mesh_scr_source` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `scr_ui` VARCHAR(10) NOT NULL COMMENT 'SCR UI(关联:cat_mesh_scr.ui,格式:C000001)',
    `source` VARCHAR(500) NOT NULL COMMENT '来源(如 NCI2004_11_17/FDA SRS (2023)/OMIM (2013) 等)',
    `order_num` INT UNSIGNED NULL DEFAULT NULL COMMENT '排序号(在同一SCR内的顺序)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_scr_ui` (`scr_ui`) COMMENT 'SCR UI索引,支持查询某SCR的所有来源'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='SCR 来源表:存储 SCR 的数据来源(如 NCI/FDA/OMIM 等),SCR 特有';


-- ============================================================
-- 表 4: cat_mesh_scr_indexing_info (SCR 索引信息表)
-- ============================================================
-- 表说明: 存储 SCR 的索引信息(IndexingInformationList),包含引用的主题词、限定词、其他SCR
-- 记录数预估: 初始 20万 / 年增长 1万 / 5年规模 25万 (约60%的SCR有索引信息)
-- 主要查询场景:
--   1. 按 scr_ui 查询某 SCR 的索引信息(<100次/天,低频)
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_mesh_scr_indexing_info` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `scr_ui` VARCHAR(10) NOT NULL COMMENT 'SCR UI(关联:cat_mesh_scr.ui,格式:C000001)',
    `descriptor_ui` VARCHAR(10) NULL DEFAULT NULL COMMENT '引用的主题词UI(可选,关联:cat_mesh_descriptor.ui)',
    `qualifier_ui` VARCHAR(10) NULL DEFAULT NULL COMMENT '引用的限定词UI(可选,关联:cat_mesh_qualifier.ui)',
    `chemical_ui` VARCHAR(10) NULL DEFAULT NULL COMMENT '引用的化学物质UI(可选,指向其他SCR的UI)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_scr_ui` (`scr_ui`) COMMENT 'SCR UI索引,支持查询某SCR的索引信息'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='SCR 索引信息表:存储 SCR 的索引信息,包含引用的主题词、限定词、其他SCR';


-- ============================================================
-- 表 5: cat_mesh_scr_pharmacological_action (SCR 药理作用表)
-- ============================================================
-- 表说明: 存储 SCR 的药理作用(PharmacologicalActionList),指向 Descriptor
-- 记录数预估: 初始 15万 / 年增长 1万 / 5年规模 20万 (约50%的化学物质SCR有药理作用)
-- 主要查询场景:
--   1. 按 scr_ui 查询某 SCR 的药理作用(<100次/天,低频)
--   2. 按 descriptor_ui 反查具有某药理作用的SCR(<50次/天,低频)
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_mesh_scr_pharmacological_action` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `scr_ui` VARCHAR(10) NOT NULL COMMENT 'SCR UI(关联:cat_mesh_scr.ui,格式:C000001)',
    `descriptor_ui` VARCHAR(10) NOT NULL COMMENT '药理作用主题词UI(关联:cat_mesh_descriptor.ui)',
    `descriptor_name` VARCHAR(255) NOT NULL COMMENT '主题词名称(冗余存储,避免查询时JOIN)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 复合唯一索引(防重)
    UNIQUE INDEX `uk_scr_descriptor` (`scr_ui`, `descriptor_ui`) COMMENT 'SCR+主题词唯一索引,防止重复',

    -- 普通索引
    INDEX `idx_scr_ui` (`scr_ui`) COMMENT 'SCR UI索引,支持查询某SCR的药理作用',
    INDEX `idx_descriptor_ui` (`descriptor_ui`) COMMENT '主题词UI索引,支持反向查询'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='SCR 药理作用表:存储 SCR 的药理作用,指向 Descriptor';


-- ============================================================
-- 全文索引（需要在表创建后单独执行）
-- ============================================================
-- 注意: 全文索引使用 ngram 解析器,支持中文分词(MySQL 5.7.6+)
-- ============================================================

-- cat_mesh_scr 表的全文索引
CREATE FULLTEXT INDEX `ft_name_note` ON `cat_mesh_scr` (`name`, `note`)
    WITH PARSER ngram
    COMMENT '名称和说明全文索引,支持模糊检索';
