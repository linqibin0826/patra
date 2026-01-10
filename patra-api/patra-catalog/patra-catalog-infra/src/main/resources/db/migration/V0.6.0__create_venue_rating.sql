-- ============================================================
-- Patra Catalog 数据库 - VenueRating 聚合根 DDL
-- ============================================================
-- 设计阶段: 独立聚合根
-- 创建日期: 2025-12-12
-- 设计范围: 载体年度评级数据（JCR/CAS/SCOPUS）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 设计说明
-- ============================================================
-- **独立聚合根设计**:
-- - VenueRatingAggregate 作为独立聚合根，拥有独立的生命周期和一致性边界
-- - 通过 venue_id 逻辑关联 cat_venue（无物理外键）
-- - 符合 DDD 聚合设计原则：独立的业务边界和事务边界
--
-- **业务唯一键**:
-- - (venue_id, year, rating_system) - 同一期刊同一年同一评价体系只有一条记录
--
-- **评价体系**:
-- - JCR: Journal Citation Reports (Clarivate)
-- - CAS: 中科院分区
-- - SCOPUS: Scopus 数据库评级
-- ============================================================


-- ============================================================
-- 表: cat_venue_rating (载体评级表 - 独立聚合根)
-- ============================================================
-- 表说明: 存储载体（主要是期刊）的年度评级数据
-- 数据来源: JCR、中科院分区、SCOPUS 等评价体系
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_rating` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成(AUTO_INCREMENT为备用)',

    -- ========================================
    -- 核心属性（不变量）
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(逻辑外键:cat_venue.id)',
    `year` SMALLINT NOT NULL COMMENT '评级年份(2000-2100)',
    `rating_system` VARCHAR(32) NOT NULL COMMENT '评价体系:JCR/CAS/SCOPUS',

    -- ========================================
    -- 评级数据
    -- ========================================
    `quartile` VARCHAR(10) NULL DEFAULT NULL COMMENT '分区(JCR:Q1-Q4,CAS:1-4区)',
    `impact_score` DECIMAL(10,4) NULL DEFAULT NULL COMMENT '影响力分数(JCR Impact Factor/SCOPUS CiteScore)',
    `rating_data` JSON NULL DEFAULT NULL COMMENT '评级详情(JSON,包含体系特定数据)',
    `categories` JSON NULL DEFAULT NULL COMMENT '学科分类(JSON数组,如WoS学科分类)',

    -- ========================================
    -- 数据来源追踪
    -- ========================================
    `source_url` VARCHAR(500) NULL DEFAULT NULL COMMENT '数据来源URL',
    `fetched_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '数据获取时间(UTC,微秒精度)',

    -- ========================================
    -- 审计字段（ChildJpaEntity: 4个字段）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引（业务唯一键）
    UNIQUE INDEX `uk_venue_year_system` (`venue_id`, `year`, `rating_system`)
        COMMENT '载体+年份+评价体系唯一索引,保证业务唯一性',

    -- 普通索引（查询优化）
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体ID索引,支持按载体查询所有评级',
    INDEX `idx_rating_system` (`rating_system`) COMMENT '评价体系索引',
    INDEX `idx_year` (`year`) COMMENT '年份索引',
    INDEX `idx_quartile` (`quartile`) COMMENT '分区索引,支持按分区筛选',
    INDEX `idx_impact_score` (`impact_score`) COMMENT '影响力分数索引,支持排序'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='载体评级表(独立聚合根):JCR/CAS/SCOPUS年度评级数据';
