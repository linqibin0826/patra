-- ============================================================
-- Patra Catalog 数据库 - 辅助管理模块表 DDL
-- ============================================================
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: patra_catalog 辅助管理表（5张表）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_unicode_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- 1. cat_language_mapping (语言映射表) - 无依赖（独立字典表）
-- 2. cat_publication_date (日期信息表) - 依赖 cat_publication
-- 3. cat_publication_metadata (元数据表) - 依赖 cat_publication
-- 4. cat_alternative_abstract (其他语言摘要表) - 依赖 cat_publication, cat_abstract
-- 5. cat_oa_location (开放获取位置表) - 依赖 cat_publication
-- ============================================================

-- ============================================================
-- 执行说明
-- ============================================================
-- 1. 确保 MySQL 版本 >= 8.0（需要部分索引支持）
-- 2. 确保已执行核心实体表 DDL（cat_publication, cat_abstract）
-- 3. 按顺序执行表创建（考虑依赖关系）
-- 4. 部分索引在 MySQL 8.0.13+ 支持，如不支持请移除 WHERE 条件
-- 5. 建议在测试环境先验证，再在生产环境执行
-- 6. 执行前备份现有数据（如果有）
-- ============================================================


-- ============================================================
-- 表 1: cat_language_mapping (语言映射表)
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
    `deleted`              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除标志(0=正常,1=已删除)',


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
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='语言映射表:原始语言值到标准代码映射,支持动态学习';


-- ============================================================
-- 表 2: cat_publication_date (日期信息表)
-- ============================================================
-- 表说明: 精确记录文献生命周期的各类日期,支持不完整日期表达
-- 记录数预估: 初始 200万 / 年增长 350万 / 5年规模 1950万
-- 主要查询场景:
--   1. 按 publication_id 查询某文献的所有日期(>1000次/天,高频)
--   2. 按日期类型查询(500-1000次/天,中频)
--   3. 按年份范围查询(100-500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_date`
(
    -- ========================================
    -- 业务字段
    -- ========================================
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id`  BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `date_type`       VARCHAR(50)     NOT NULL COMMENT '日期类型:Received/Accepted/Published/Revised/Retracted/EPub/PPub/EntrezDate/Other',
    `date_value`      DATE            NULL     DEFAULT NULL COMMENT '日期值(仅完整日期时填充)',
    `year`            SMALLINT        NOT NULL COMMENT '年份(必填,1900-2100)',
    `month`           TINYINT         NULL     DEFAULT NULL COMMENT '月份(1-12,可能为空)',
    `day`             TINYINT         NULL     DEFAULT NULL COMMENT '日期(1-31,可能为空)',
    `date_precision`  VARCHAR(10)     NOT NULL DEFAULT 'year' COMMENT '精度:year/month/day',
    `season`          VARCHAR(100)    NULL     DEFAULT NULL COMMENT '季节(如"Spring 2024","Q1 2023")',
    `date_string`     VARCHAR(200)    NULL     DEFAULT NULL COMMENT '原始日期字符串(如"June 2023")',
    `is_primary`      BOOLEAN         NOT NULL DEFAULT 0 COMMENT '是否主要日期(0=否,1=是)',
    `order_num`       INT             NULL     DEFAULT NULL COMMENT '顺序号(同类型多个日期时使用)',
    `metadata`        JSON            NULL     DEFAULT NULL COMMENT '日期元数据(灵活扩展)',

    -- ========================================
    -- ========================================
    -- 审计字段（完整版）
    -- ========================================
    `record_remarks`  JSON            NULL     DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version`         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次更新自增)',
    `ip_address`      VARBINARY(16)   NULL     DEFAULT NULL COMMENT '请求者IP(二进制,支持IPv4/IPv6)',
    `created_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `created_by`      BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100)    NULL     DEFAULT NULL COMMENT '创建人姓名(冗余-审计友好)',
    `updated_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',
    `updated_by`      BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100)    NULL     DEFAULT NULL COMMENT '更新人姓名(冗余-审计友好)',
    `deleted`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除标志(0=正常,1=已删除)',


    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,支持查询某文献的所有日期(高频)',
    INDEX `idx_date_type` (`date_type`) COMMENT '日期类型索引,支持按类型查询(中频)',
    INDEX `idx_year` (`year`) COMMENT '年份索引,支持按年份范围查询(中频)'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='日期信息表:记录文献生命周期各类日期,支持不完整日期';

-- 部分索引(仅索引完整日期) - MySQL 8.0.13+
-- 注意: 如果 MySQL 版本 < 8.0.13,请移除此索引或将 WHERE 条件移除
CREATE INDEX `idx_date_value` ON `cat_publication_date` (`date_value`) COMMENT '完整日期索引(部分索引,仅索引非空值)';

-- 部分唯一索引(保证主要日期唯一) - MySQL 8.0.13+
-- 注意: 如果 MySQL 版本 < 8.0.13,需要通过应用层或触发器保证唯一性
-- CREATE UNIQUE INDEX `uk_primary_date` ON `cat_publication_date` (`publication_id`, `date_type`) WHERE `is_primary` = 1 COMMENT '主要日期唯一约束';


-- ============================================================
-- 表 3: cat_publication_metadata (元数据表)
-- ============================================================
-- 表说明: 独立管理文献的元数据信息(索引状态、质量评分、数据溯源),与 cat_publication 一对一关系
-- 记录数预估: 初始 100万 / 年增长 200万 / 5年规模 1100万
-- 主要查询场景:
--   1. 按 publication_id 查询元数据(>1000次/天,高频)
--   2. 按索引状态查询(500-1000次/天,中频)
--   3. 按数据来源查询(100-500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_publication_metadata`
(
    -- ========================================
    -- 业务字段
    -- ========================================
    `id`                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id`     BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id,一对一关系)',
    `indexing_status`    VARCHAR(50)     NULL     DEFAULT NULL COMMENT '索引状态:Pending/Indexed/MEDLINE/PubMed-not-MEDLINE/OLDMEDLINE/In-Data-Review/In-Process/Failed',
    `indexing_method`    VARCHAR(50)     NULL     DEFAULT NULL COMMENT '索引方法:Automated/Curated/In-Data-Review',
    `indexed_date`       DATE            NULL     DEFAULT NULL COMMENT '索引日期',
    `data_source`        VARCHAR(50)     NULL     DEFAULT NULL COMMENT '数据来源:PubMed/EPMC/Crossref/Manual/Other',
    `import_batch`       VARCHAR(50)     NULL     DEFAULT NULL COMMENT '导入批次标识(如"2025-01-18_PUBMED")',
    `import_date`        TIMESTAMP(6)    NULL     DEFAULT NULL COMMENT '导入时间(UTC微秒精度)',
    `quality_score`      VARCHAR(2)      NULL     DEFAULT NULL COMMENT '质量评分:A/B/C/D/F',
    `completeness_score` VARCHAR(2)      NULL     DEFAULT NULL COMMENT '完整性评分:A/B/C/D/F',
    `has_full_text`      BOOLEAN         NOT NULL DEFAULT 0 COMMENT '是否有全文(0=否,1=是)',
    `full_text_url`      VARCHAR(200)    NULL     DEFAULT NULL COMMENT '全文链接',
    `review_status`      VARCHAR(50)     NULL     DEFAULT NULL COMMENT '审核状态:Pending/Reviewed/Rejected/Approved',
    `review_date`        DATE            NULL     DEFAULT NULL COMMENT '审核日期',
    `reviewer`           VARCHAR(100)    NULL     DEFAULT NULL COMMENT '审核人姓名',
    `validation_errors`  JSON            NULL     DEFAULT NULL COMMENT '验证错误(JSON数组)',
    `processing_notes`   JSON            NULL     DEFAULT NULL COMMENT '处理注释(JSON数组)',
    `ext_metadata`       JSON            NULL     DEFAULT NULL COMMENT '扩展元数据(灵活扩展)',

    -- ========================================
    -- 审计字段（完整版）
    -- ========================================
    -- 审计字段（完整版）
    -- ========================================
    `record_remarks`     JSON            NULL     DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version`            BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次更新自增)',
    `ip_address`         VARBINARY(16)   NULL     DEFAULT NULL COMMENT '请求者IP(二进制,支持IPv4/IPv6)',
    `created_at`         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `created_by`         BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '创建人ID',
    `created_by_name`    VARCHAR(100)    NULL     DEFAULT NULL COMMENT '创建人姓名(冗余-审计友好)',
    `updated_at`         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',
    `updated_by`         BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name`    VARCHAR(100)    NULL     DEFAULT NULL COMMENT '更新人姓名(冗余-审计友好)',
    `deleted`            TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除标志(0=正常,1=已删除)',


    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_pub_metadata` (`publication_id`) COMMENT '出版物ID唯一索引,保证一对一关系,支持高频查询元数据(<10ms)',

    -- 普通索引
    INDEX `idx_indexing_status` (`indexing_status`) COMMENT '索引状态索引,支持按状态查询(中频)',
    INDEX `idx_data_source` (`data_source`) COMMENT '数据来源索引,支持按来源查询(中频)',
    INDEX `idx_import_batch` (`import_batch`) COMMENT '导入批次索引,支持批次查询和回滚(低频)',
    INDEX `idx_review_status` (`review_status`) COMMENT '审核状态索引,支持审核工作流查询(低频)',

    -- 复合索引(软删除 + 更新时间)
    INDEX `idx_deleted_updated` (`deleted`, `updated_at`) COMMENT '软删除和更新时间复合索引,支持查询"未删除的最新更新记录"'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='元数据表:管理文献索引状态、质量评分、数据溯源,与主表一对一';

-- 部分索引(仅索引有全文的记录) - MySQL 8.0.13+
-- 注意: 如果 MySQL 版本 < 8.0.13,请移除此索引或将 WHERE 条件移除
-- CREATE INDEX `idx_has_full_text` ON `cat_publication_metadata` (`has_full_text`) WHERE `has_full_text` = 1 COMMENT '全文筛选索引(部分索引)';


-- ============================================================
-- 表 4: cat_alternative_abstract (其他语言摘要表)
-- ============================================================
-- 表说明: 管理文献摘要的多语言版本(官方翻译、专业翻译、机器翻译)
-- 记录数预估: 初始 20万 / 年增长 15万 / 5年规模 95万
-- 主要查询场景:
--   1. 按 publication_id 查询某文献的所有翻译摘要(>500次/天,中频)
--   2. 按语言代码查询(100-500次/天,中频)
--   3. 按官方翻译标记筛选(<100次/天,低频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_alternative_abstract`
(
    -- ========================================
    -- 业务字段
    -- ========================================
    `id`                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id`      BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `abstract_id`         BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '主摘要ID(外键:cat_abstract.id,关联原摘要)',
    `language_code`       VARCHAR(10)     NOT NULL COMMENT '语言代码(ISO 639-1,如"zh-CN","ja")',
    `language_name`       VARCHAR(50)     NULL     DEFAULT NULL COMMENT '语言名称(如"Chinese","Japanese")',
    `plain_text`          TEXT            NULL     DEFAULT NULL COMMENT '纯文本摘要(最大65535字符)',
    `structured_sections` JSON            NULL     DEFAULT NULL COMMENT '结构化摘要段落(JSON对象)',
    `translation_type`    VARCHAR(50)     NULL     DEFAULT NULL COMMENT '翻译类型:Official/Professional/Machine/Community',
    `translator`          VARCHAR(100)    NULL     DEFAULT NULL COMMENT '译者姓名或机构',
    `translation_date`    DATE            NULL     DEFAULT NULL COMMENT '翻译日期',
    `quality_level`       VARCHAR(50)     NULL     DEFAULT NULL COMMENT '质量级别:Excellent/Good/Fair/Poor',
    `is_official`         BOOLEAN         NOT NULL DEFAULT 0 COMMENT '是否官方翻译(0=否,1=是)',
    `order_num`           INT             NULL     DEFAULT NULL COMMENT '顺序号(同一语言多个翻译时排序)',
    `metadata`            JSON            NULL     DEFAULT NULL COMMENT '翻译元数据(灵活扩展)',

    -- ========================================
    -- ========================================
    -- 审计字段（完整版）
    -- ========================================
    `record_remarks`      JSON            NULL     DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version`             BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次更新自增)',
    `ip_address`          VARBINARY(16)   NULL     DEFAULT NULL COMMENT '请求者IP(二进制,支持IPv4/IPv6)',
    `created_at`          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `created_by`          BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '创建人ID',
    `created_by_name`     VARCHAR(100)    NULL     DEFAULT NULL COMMENT '创建人姓名(冗余-审计友好)',
    `updated_at`          TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',
    `updated_by`          BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name`     VARCHAR(100)    NULL     DEFAULT NULL COMMENT '更新人姓名(冗余-审计友好)',
    `deleted`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除标志(0=正常,1=已删除)',


    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 复合唯一索引
    UNIQUE INDEX `uk_abstract_lang` (`publication_id`, `language_code`) COMMENT '出版物+语言唯一索引,保证每种语言只有一个翻译',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,支持查询某文献的所有翻译(中频)',
    INDEX `idx_abstract` (`abstract_id`) COMMENT '主摘要索引,支持查询某摘要的所有翻译(低频)',
    INDEX `idx_language` (`language_code`) COMMENT '语言代码索引,支持按语言查询(中频)'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='其他语言摘要表:管理摘要的多语言版本,支持官方/机器翻译';

-- 部分索引(仅索引官方翻译) - MySQL 8.0.13+
-- 注意: 如果 MySQL 版本 < 8.0.13,请移除此索引或将 WHERE 条件移除
-- CREATE INDEX `idx_official` ON `cat_alternative_abstract` (`is_official`) WHERE `is_official` = 1 COMMENT '官方翻译索引(部分索引)';


-- ============================================================
-- 表 5: cat_oa_location (开放获取位置表)
-- ============================================================
-- 表说明: 详细记录文献的开放获取位置,支持多位置管理和最佳位置选择
-- 记录数预估: 初始 500万 / 年增长 600万 / 5年规模 3500万
-- 主要查询场景:
--   1. 按 publication_id 查询某文献的所有 OA 位置(>1000次/天,高频)
--   2. 按 OA 状态查询(500-1000次/天,中频)
--   3. 按位置类型查询(100-500次/天,中频)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_oa_location`
(
    -- ========================================
    -- 业务字段
    -- ========================================
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',
    `publication_id`   BIGINT UNSIGNED NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `oa_status`        VARCHAR(20)     NOT NULL COMMENT 'OA状态:gold/green/hybrid/bronze/closed',
    `location_type`    VARCHAR(50)     NULL     DEFAULT NULL COMMENT '位置类型:publisher/repository/pubmed_central/preprint/academic_social/other',
    `url`              VARCHAR(500)    NULL     DEFAULT NULL COMMENT '访问URL',
    `host_domain`      VARCHAR(200)    NULL     DEFAULT NULL COMMENT '托管域名(如"pubmed.ncbi.nlm.nih.gov")',
    `repository_name`  VARCHAR(100)    NULL     DEFAULT NULL COMMENT '仓库名称(如"PubMed Central","arXiv")',
    `repository_id`    VARCHAR(100)    NULL     DEFAULT NULL COMMENT '仓库标识符(如"PMC1234567")',
    `version_type`     VARCHAR(50)     NULL     DEFAULT NULL COMMENT '版本类型:publishedVersion/acceptedVersion/submittedVersion',
    `license`          VARCHAR(100)    NULL     DEFAULT NULL COMMENT '许可证(如"CC-BY-4.0","CC-BY-NC")',
    `available_date`   DATE            NULL     DEFAULT NULL COMMENT '可用日期',
    `embargo_end_date` DATE            NULL     DEFAULT NULL COMMENT '禁发期结束日期',
    `is_best`          BOOLEAN         NOT NULL DEFAULT 0 COMMENT '是否最佳位置(0=否,1=是)',
    `priority`         INT             NULL     DEFAULT NULL COMMENT '优先级(1=最高,数值越小优先级越高)',
    `evidence_source`  VARCHAR(50)     NULL     DEFAULT NULL COMMENT '证据来源(如"Unpaywall","OpenAlex")',
    `checked_date`     TIMESTAMP(6)    NULL     DEFAULT NULL COMMENT '链接检查时间(UTC微秒精度)',
    `is_active`        BOOLEAN         NOT NULL DEFAULT 1 COMMENT '是否有效(0=失效,1=有效)',
    `pmcid`            VARCHAR(200)    NULL     DEFAULT NULL COMMENT 'PMC ID(如"PMC1234567",PMC专用)',
    `access_metrics`   JSON            NULL     DEFAULT NULL COMMENT '访问指标(下载次数等)',
    `metadata`         JSON            NULL     DEFAULT NULL COMMENT '位置元数据(灵活扩展)',

    -- ========================================
    -- ========================================
    -- 审计字段（完整版）
    -- ========================================
    `record_remarks`   JSON            NULL     DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号(每次更新自增)',
    `ip_address`       VARBINARY(16)   NULL     DEFAULT NULL COMMENT '请求者IP(二进制,支持IPv4/IPv6)',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC,微秒精度)',
    `created_by`       BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '创建人ID',
    `created_by_name`  VARCHAR(100)    NULL     DEFAULT NULL COMMENT '创建人姓名(冗余-审计友好)',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC,微秒精度)',
    `updated_by`       BIGINT UNSIGNED NULL     DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name`  VARCHAR(100)    NULL     DEFAULT NULL COMMENT '更新人姓名(冗余-审计友好)',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除标志(0=正常,1=已删除)',


    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,支持查询某文献的所有OA位置(高频)',
    INDEX `idx_oa_status` (`oa_status`) COMMENT 'OA状态索引,支持按状态查询(中频)',
    INDEX `idx_location_type` (`location_type`) COMMENT '位置类型索引,支持按类型查询(中频)',

    -- 复合唯一索引(同一文献的同一URL不重复)
    UNIQUE INDEX `uk_oa_url` (`publication_id`, `url`(255)) COMMENT '出版物+URL唯一索引,防止重复记录'
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='开放获取位置表:记录文献OA位置,支持多位置管理和最佳位置选择';
