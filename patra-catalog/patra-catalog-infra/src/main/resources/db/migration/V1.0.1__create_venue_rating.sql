-- ============================================================
-- Patra Catalog 数据库 - Venue 评级体系 DDL
-- ============================================================
-- 版本: V1.0.1
-- 领域: Venue 领域
-- 设计范围: 期刊评级数据（JCR/CAS/Scopus 独立评级表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 设计说明
-- ============================================================
-- **强类型评级表设计**:
-- - 每个评价体系（JCR/CAS/Scopus）拥有独立的强类型表
-- - 消除 JSON 依赖，支持多学科/多版本扩展
-- - 通过 venue_id 逻辑关联 cat_venue（无物理外键）
--
-- **表结构**:
-- 1. cat_venue_jcr_rating - JCR Impact Factor 年度趋势 + 分区详情
-- 2. cat_venue_cas_rating - CAS 中科院分区（支持多版本共存）
-- 3. cat_venue_scopus_rating - Scopus CiteScore/SJR/SNIP 年度指标
-- 4. cat_venue_cas_warning - CAS 中科院期刊预警名单（按版本时间序列）
-- ============================================================


-- ============================================================
-- 表 1: cat_venue_jcr_rating (JCR 期刊评级表)
-- ============================================================
-- 表说明: Clarivate JCR 按年发布的期刊评级时间序列（每期刊每年一行）
--   除 impact_factor 外，分区 / 排名 / 学科 / 百分位 / JCI 独立字段 / 自引率 /
--   WOS 综合分区等，在 Clarivate 原生 JCR 里**都是按年发布的年度指标**。
--
-- 当前数据源局限: 仅通过 LetPub 爬取填充，而 LetPub 页面只展示最新年的详细分区
-- （历史年只给 impact_factor 趋势）。历史年行里这些字段暂为 NULL，属**数据源限制**
-- 而非 schema 错位；未来接入 Clarivate InCites API 或历史 JCR Excel 批量导入后，
-- 可回填这些年度指标。
--
-- UK: (venue_id, year) — 一个期刊每年一行
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_jcr_rating` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                         COMMENT 'Snowflake ID（auto-increment 作为后备）',
    `venue_id`           BIGINT UNSIGNED NOT NULL
                         COMMENT '关联期刊 ID（逻辑外键 → cat_venue.id）',
    `year`               SMALLINT        NOT NULL
                         COMMENT '评级年份（2000-2100）',
    `impact_factor`      DECIMAL(10,4)   NULL DEFAULT NULL
                         COMMENT 'JIF 影响因子',
    `wos_overall_quartile` VARCHAR(10)   NULL DEFAULT NULL
                         COMMENT 'WOS 综合分区等级（1区-4区，LetPub 基于 JIF + JCI 综合评定）',
    `subject`            VARCHAR(100)    NULL DEFAULT NULL
                         COMMENT 'JIF 学科分类（如 MULTIDISCIPLINARY SCIENCES）',
    `collection`         VARCHAR(10)     NULL DEFAULT NULL
                         COMMENT 'JIF 收录集（SCIE/SSCI/AHCI）',
    `jif_quartile`       VARCHAR(10)     NULL DEFAULT NULL
                         COMMENT 'JIF 分区（Q1-Q4）',
    `jif_rank`           VARCHAR(20)     NULL DEFAULT NULL
                         COMMENT 'JIF 排名（如 2/136）',
    `jif_percentile`     DECIMAL(5,2)    NULL DEFAULT NULL
                         COMMENT 'JIF 学科百分位（0.00-100.00）',
    `jci_subject`        VARCHAR(100)    NULL DEFAULT NULL
                         COMMENT 'JCI 学科分类（多数情况下同 subject）',
    `jci_collection`     VARCHAR(10)     NULL DEFAULT NULL
                         COMMENT 'JCI 收录集（多数情况下同 collection）',
    `jci_quartile`       VARCHAR(10)     NULL DEFAULT NULL
                         COMMENT 'JCI 分区（Q1-Q4）',
    `jci_rank`           VARCHAR(20)     NULL DEFAULT NULL
                         COMMENT 'JCI 排名',
    `jci_percentile`     DECIMAL(5,2)    NULL DEFAULT NULL
                         COMMENT 'JCI 学科百分位（0.00-100.00）',
    `jci_value`          DECIMAL(10,4)   NULL DEFAULT NULL
                         COMMENT 'JCI 数值（Journal Citation Indicator 本身的数值）',
    `self_citation_rate` DECIMAL(5,2)    NULL DEFAULT NULL
                         COMMENT '自引率（0.00-100.00）',
    `research_direction` VARCHAR(200)    NULL DEFAULT NULL
                         COMMENT '研究方向/学科领域',
    `source_url`         VARCHAR(500)    NULL DEFAULT NULL
                         COMMENT '数据来源 URL',
    `fetched_at`         TIMESTAMP(6)    NULL DEFAULT NULL
                         COMMENT '数据抓取时间（UTC，微秒精度）',
    `created_at`         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                         COMMENT '创建时间',
    `updated_at`         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
                         COMMENT '最后更新时间',
    `version`            BIGINT UNSIGNED NOT NULL DEFAULT 0
                         COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_venue_year` (`venue_id`, `year`),
    INDEX `idx_venue_id` (`venue_id`),
    INDEX `idx_jif_quartile` (`jif_quartile`),
    INDEX `idx_impact_factor` (`impact_factor`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='JCR 期刊评级：Impact Factor 年度趋势 + 最新年分区详情';


-- ============================================================
-- 表 2: cat_venue_cas_rating (CAS 中科院分区表)
-- ============================================================
-- 表说明: 中科院期刊分区数据
-- UK: (venue_id, year, edition) — 支持同年多版本共存（升级版/新锐版/基础版）
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_cas_rating` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                         COMMENT 'Snowflake ID（auto-increment 作为后备）',
    `venue_id`           BIGINT UNSIGNED NOT NULL
                         COMMENT '关联期刊 ID（逻辑外键 → cat_venue.id）',
    `year`               SMALLINT        NOT NULL
                         COMMENT '分区年份（2000-2100）',
    `edition`            VARCHAR(20)     NOT NULL
                         COMMENT 'CAS 版本名称（升级版/新锐版/基础版）',
    `major_category`     VARCHAR(50)     NULL DEFAULT NULL
                         COMMENT '大类学科（如 医学）',
    `major_quartile`     VARCHAR(10)     NULL DEFAULT NULL
                         COMMENT '大类分区（1区-4区）',
    `minor_subject`      VARCHAR(100)    NULL DEFAULT NULL
                         COMMENT '小类学科（如 肿瘤学）',
    `minor_quartile`     VARCHAR(10)     NULL DEFAULT NULL
                         COMMENT '小类分区（1区-4区）',
    `is_top_journal`     BOOLEAN         NULL DEFAULT NULL
                         COMMENT '是否为 Top 期刊',
    `is_review_journal`  BOOLEAN         NULL DEFAULT NULL
                         COMMENT '是否为综述期刊',
    `source_url`         VARCHAR(500)    NULL DEFAULT NULL
                         COMMENT '数据来源 URL',
    `fetched_at`         TIMESTAMP(6)    NULL DEFAULT NULL
                         COMMENT '数据抓取时间（UTC，微秒精度）',
    `created_at`         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                         COMMENT '创建时间',
    `updated_at`         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
                         COMMENT '最后更新时间',
    `version`            BIGINT UNSIGNED NOT NULL DEFAULT 0
                         COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_venue_year_edition` (`venue_id`, `year`, `edition`),
    INDEX `idx_venue_id` (`venue_id`),
    INDEX `idx_major_quartile` (`major_quartile`),
    INDEX `idx_is_top_journal` (`is_top_journal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='CAS 中科院分区：支持多版本（升级版/新锐版/基础版）';


-- ============================================================
-- 表 3: cat_venue_scopus_rating (Scopus 期刊指标表)
-- ============================================================
-- 表说明: 存储从 Elsevier Scopus Serial Title API 获取的年度期刊评价指标
-- UK: (venue_id, year) — 每个期刊每年一条记录
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_scopus_rating` (
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `venue_id`             BIGINT UNSIGNED NOT NULL                          COMMENT 'Logical FK -> cat_venue.id',
    `year`                 SMALLINT        NOT NULL                          COMMENT '指标年份（2000-2100）',
    `cite_score`           DECIMAL(10,4)   NULL     DEFAULT NULL             COMMENT 'CiteScore',
    `cite_score_tracker`   DECIMAL(10,4)   NULL     DEFAULT NULL             COMMENT 'CiteScore Tracker（当年预估值）',
    `sjr`                  DECIMAL(10,4)   NULL     DEFAULT NULL             COMMENT 'SCImago Journal Rank',
    `snip`                 DECIMAL(10,4)   NULL     DEFAULT NULL             COMMENT 'Source Normalized Impact per Paper',
    `document_count`       INT UNSIGNED    NULL     DEFAULT NULL             COMMENT '该年发文量',
    `citation_count`       INT UNSIGNED    NULL     DEFAULT NULL             COMMENT '该年被引次数',
    `percent_cited`        DECIMAL(5,2)    NULL     DEFAULT NULL             COMMENT '被引文献百分比',
    `subject_area`         VARCHAR(200)    NULL     DEFAULT NULL             COMMENT '主 ASJC 学科领域',
    `quartile`             VARCHAR(5)      NULL     DEFAULT NULL             COMMENT 'CiteScore 分区（Q1-Q4）',
    `percentile`           DECIMAL(5,2)    NULL     DEFAULT NULL             COMMENT '学科内百分位排名',
    `scopus_source_id`     VARCHAR(20)     NULL     DEFAULT NULL             COMMENT 'Scopus Source ID',
    `source_url`           VARCHAR(500)    NULL     DEFAULT NULL             COMMENT 'API 来源 URL',
    `fetched_at`           TIMESTAMP(6)    NULL     DEFAULT NULL             COMMENT '数据抓取时间（UTC）',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_venue_year` (`venue_id`, `year`),
    INDEX `idx_venue_id` (`venue_id`),
    INDEX `idx_cite_score` (`cite_score`),
    INDEX `idx_quartile` (`quartile`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Scopus 期刊指标：CiteScore/SJR/SNIP 年度数据';


-- ============================================================
-- 表 4: cat_venue_cas_warning (CAS 中科院期刊预警名单)
-- ============================================================
-- 表说明: CAS 分区表预警名单的历史时间序列
-- UK: (venue_id, published_year, edition_label) — 每版本一行
--
-- 为何独立于 cat_venue_cas_rating:
--   预警名单和分区表是两个独立的时间序列，发布节奏和版本命名不同步
--   预警名单覆盖更长历史（2020+），分区表只展示近几年版本
--   强制合并需要大量 NULL 行，违反单一职责原则
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_cas_warning` (
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT
                         COMMENT 'Snowflake ID',
    `venue_id`           BIGINT UNSIGNED NOT NULL
                         COMMENT '关联期刊 ID（逻辑外键 → cat_venue.id）',
    `published_year`     SMALLINT        NOT NULL
                         COMMENT '预警名单发布年份（2020-2100）',
    `published_month`    TINYINT UNSIGNED NULL DEFAULT NULL
                         COMMENT '预警名单发布月份 1-12（可空）',
    `edition_label`      VARCHAR(30)     NOT NULL
                         COMMENT '原始版本标签：新锐学术版/2025版/2024版/2023版...',
    `in_warning_list`    BOOLEAN         NOT NULL
                         COMMENT '是否在预警名单中：true=预警，false=正常',
    `warning_level`      VARCHAR(10)     NULL DEFAULT NULL
                         COMMENT '预警级别：高/中/低（仅当 in_warning_list=true 时可能有值）',
    `raw_text`           VARCHAR(500)    NULL DEFAULT NULL
                         COMMENT '原始描述文本（保留 LetPub 页面原句以便追溯）',
    `source_url`         VARCHAR(500)    NULL DEFAULT NULL
                         COMMENT '数据来源 URL',
    `fetched_at`         TIMESTAMP(6)    NULL DEFAULT NULL
                         COMMENT '数据抓取时间（UTC，微秒精度）',
    `created_at`         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                         COMMENT '创建时间',
    `updated_at`         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
                         COMMENT '最后更新时间',
    `version`            BIGINT UNSIGNED NOT NULL DEFAULT 0
                         COMMENT '乐观锁版本号',
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_venue_year_edition` (`venue_id`, `published_year`, `edition_label`),
    INDEX `idx_venue_id` (`venue_id`),
    INDEX `idx_in_warning` (`in_warning_list`),
    INDEX `idx_warning_level` (`warning_level`),
    INDEX `idx_published_year` (`published_year`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='CAS 中科院期刊预警名单：按版本的时间序列';
