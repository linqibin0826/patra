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
-- ============================================================


-- ============================================================
-- 表 1: cat_venue_jcr_rating (JCR 期刊评级表)
-- ============================================================
-- 表说明: Impact Factor 年度趋势 + 最新年分区详情
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
    `five_year_if`       DECIMAL(10,4)   NULL DEFAULT NULL
                         COMMENT '五年影响因子（仅最新年）',
    `subject`            VARCHAR(100)    NULL DEFAULT NULL
                         COMMENT 'JCR 学科分类（如 MULTIDISCIPLINARY SCIENCES）',
    `collection`         VARCHAR(10)     NULL DEFAULT NULL
                         COMMENT 'JCR 收录集（SCIE/SSCI/AHCI）',
    `jif_quartile`       VARCHAR(10)     NULL DEFAULT NULL
                         COMMENT 'JIF 分区（Q1-Q4）',
    `jif_rank`           VARCHAR(20)     NULL DEFAULT NULL
                         COMMENT 'JIF 排名（如 1/100）',
    `jci_quartile`       VARCHAR(10)     NULL DEFAULT NULL
                         COMMENT 'JCI 分区',
    `jci_rank`           VARCHAR(20)     NULL DEFAULT NULL
                         COMMENT 'JCI 排名',
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
