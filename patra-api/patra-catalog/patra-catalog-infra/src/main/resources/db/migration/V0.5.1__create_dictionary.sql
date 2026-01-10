-- ============================================================
-- Patra Catalog 数据库 - 字典表 DDL
-- ============================================================
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: 语言映射字典表（1张表，独立管理）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表说明
-- ============================================================
-- 本文件包含系统字典表，与业务实体表分离管理
-- 字典表特点:
--   1. 数据量小但查询频率极高
--   2. 需要单独的缓存策略
--   3. 变更频率低，适合应用层缓存
-- ============================================================


-- ============================================================
-- 表: cat_language_mapping (语言映射表)
-- ============================================================
-- 表说明: 原始语言值到标准语言代码的映射表,支持动态学习和人工验证
-- 记录数预估: 初始 1千 / 年增长 500 / 5年规模 3.5千
-- 主要查询场景:
--   1. 按 raw_value 查询标准代码(>5000次/天,极高频-应用层语言标准化)
--   2. 按 standard_code 反向查询(<100次/天,低频)
--   3. 按置信度查询未验证记录(<100次/天,低频-人工审核)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_language_mapping`
(
    -- ========================================
    -- 业务字段
    -- ========================================
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `raw_value`            VARCHAR(100)    NOT NULL COMMENT '原始语言值(唯一,如"eng","Chinese")',
    `standard_code`        VARCHAR(10)     NOT NULL COMMENT '标准语言代码(ISO 639-1,如"en","zh")',
    `base_language`        VARCHAR(5)      NULL     DEFAULT NULL COMMENT '基础语种(如"en","zh","ja")',
    `language_name_en`     VARCHAR(100)    NULL     DEFAULT NULL COMMENT '英文名称(如"English","Chinese")',
    `language_name_native` VARCHAR(100)    NULL     DEFAULT NULL COMMENT '本地名称(如"English","中文","日本語")',
    `mapping_source`       VARCHAR(50)     NULL     DEFAULT NULL COMMENT '映射来源:ISO_639/NLP_Inference/Manual/Similarity_Match',
    `confidence_score`     DECIMAL(5, 2)   NOT NULL DEFAULT 0.00 COMMENT '置信度(0-100,如95.50)',
    `usage_count`          INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '使用次数(每次应用层查询自增)',
    `is_verified`          BOOLEAN         NOT NULL DEFAULT 0 COMMENT '是否已验证(0=未验证,1=已验证)',
    `last_used`            TIMESTAMP(6)    NULL     DEFAULT NULL COMMENT '最后使用时间(UTC,微秒精度)',
    `variant_forms`        JSON            NULL     DEFAULT NULL COMMENT '变体形式(JSON数组)',
    `metadata`             JSON            NULL     DEFAULT NULL COMMENT '映射元数据(灵活扩展)',

    -- ========================================
    -- 审计字段（完整版）
    -- ========================================
    `record_remarks`       JSON            NULL     DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次更新自增)',
    `ip_address`           VARBINARY(16)   NULL     DEFAULT NULL COMMENT '请求者IP(二进制,支持IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `created_by`           BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)    NULL     DEFAULT NULL COMMENT '创建人姓名(冗余-审计友好)',
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',
    `updated_by`           BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)    NULL     DEFAULT NULL COMMENT '更新人姓名(冗余-审计友好)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引(最高频查询)
    UNIQUE INDEX `uk_raw_value` (`raw_value`) COMMENT '原始值唯一索引,支持应用层语言标准化(极高频,>5000次/天)',

    -- 普通索引
    INDEX `idx_standard_code` (`standard_code`) COMMENT '标准代码索引,支持反向查询(低频)',
    INDEX `idx_base_language` (`base_language`) COMMENT '基础语种索引,支持按语种分组查询(低频)',
    INDEX `idx_confidence` (`confidence_score`) COMMENT '置信度索引,支持查询低置信度记录(低频)',
    INDEX `idx_verified` (`is_verified`) COMMENT '验证状态索引,支持查询未验证记录(低频)',
    INDEX `idx_usage` (`usage_count`) COMMENT '使用次数索引,支持查询高频映射(低频)'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='语言映射表:原始语言值到标准代码映射,支持动态学习(字典表)';
