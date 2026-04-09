-- =============================================
-- Scopus 期刊指标表：CiteScore / SJR / SNIP
-- =============================================
-- 存储从 Elsevier Scopus Serial Title API 获取的年度期刊评价指标。
-- UK: (venue_id, year) — 每个期刊每年一条记录。
-- 断点续传通过 NOT EXISTS 子查询实现，不修改 cat_venue 表。

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
