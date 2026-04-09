-- ============================================================================
-- V1.8.0: 拆分 cat_venue_rating 为 JCR + CAS 独立评级表
--
-- 原 cat_venue_rating 用 rating_system 鉴别器 + JSON blob 混合存储 JCR/CAS 数据，
-- 拆分为强类型独立表，消除 JSON 依赖，支持多学科/多版本扩展。
-- ============================================================================

-- 1. 删除旧的多态评级表
DROP TABLE IF EXISTS `cat_venue_rating`;

-- 2. JCR 评级表：Impact Factor 年度趋势 + 最新年分区详情
--    UK: (venue_id, year) — 一个期刊每年一行
--    历史年份仅有 impact_factor，最新年份填充 quartile/rank/subject 等详情
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

-- 3. CAS 中科院分区表
--    UK: (venue_id, year, edition) — 支持同年多版本共存（升级版/新锐版/基础版）
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
