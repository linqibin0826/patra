-- ============================================================
-- Patra Catalog 数据库 - Venue 聚合表 DDL
-- ============================================================
-- 版本: V1.0.0
-- 领域: Venue 领域
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: Venue 聚合根（最小化）及关联数据表
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与设计说明
-- ============================================================
-- **DDD 嵌入式值对象设计**:
-- - 聚合根使用 JSON 字段嵌入值对象，保持聚合完整性
-- - 补充数据（1:1 关系）直接存储在主表 JSON 字段中
-- - 1:N 关联数据保持独立表设计
--
-- **表结构**:
-- 1. cat_venue (聚合根) - 核心身份+来源追踪+嵌入式值对象(JSON)
-- 2. cat_venue_identifier (标识符，1:N) - 依赖 cat_venue
-- 3. cat_venue_publication_stats (年度统计，1:N) - 时序发文统计
-- 4. cat_venue_instance (载体实例，独立聚合根) - 卷期/版次/届次
-- 5. cat_venue_mesh (MeSH主题，1:N) - Serfile扩展
-- 6. cat_venue_relation (期刊关联，1:N) - Serfile扩展
-- 7. cat_venue_indexing_history (索引历史，1:N) - Serfile扩展
--
-- **cat_venue JSON 嵌入式值对象**:
-- - publication_profile: 出版概况（缩写标题、备选标题、主页 URL、宿主机构、国家代码）
-- - citation_metrics: 引用指标（作品数、被引数、H 指数、i10 指数）
-- - open_access: 开放获取信息（OA 状态、DOAJ 收录、APC 定价）
-- - affiliated_societies: 关联学会（学会名称、URL）
-- ============================================================


-- ============================================================
-- 表 1: cat_venue (出版载体表 - 最小聚合根)
-- ============================================================
-- 表说明: 聚合根，仅包含核心身份标识和来源追踪
-- 设计原则: CQRS 写侧最小化，所有非核心属性移至补充数据表
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 核心属性（不变量验证所需）
    -- ========================================
    `venue_type` VARCHAR(32) NOT NULL COMMENT '载体类型:JOURNAL/REPOSITORY/CONFERENCE/EBOOK_PLATFORM/BOOK_SERIES/METADATA/OTHER',
    `title` VARCHAR(500) NOT NULL COMMENT '载体标题(主名称)',

    -- ========================================
    -- 来源追踪（Provenance）
    -- ========================================
    `provenance_code` VARCHAR(32) NOT NULL COMMENT '首次导入来源代码:OPENALEX/PUBMED/CROSSREF/DOAJ/MANUAL',
    `last_synced_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '最后同步时间(UTC,微秒精度)',

    -- ========================================
    -- 快速访问字段（优化列表展示和搜索查询）
    -- ========================================
    `nlm_id` VARCHAR(20) NULL DEFAULT NULL COMMENT 'NLM唯一标识符',
    `issn_l` VARCHAR(10) NULL DEFAULT NULL COMMENT 'Linking ISSN',
    `openalex_id` VARCHAR(50) NULL DEFAULT NULL COMMENT 'OpenAlex Source ID',
    `abbreviated_title` VARCHAR(200) NULL DEFAULT NULL COMMENT '缩写标题',
    `primary_language` VARCHAR(10) NULL DEFAULT NULL COMMENT '主要语言代码(BCP 47)',
    `country_code` VARCHAR(2) NULL DEFAULT NULL COMMENT '国家代码(ISO 3166-1 alpha-2)',
    `image_object_key` VARCHAR(512) NULL DEFAULT NULL COMMENT '封面图片对象存储键（相对于 venue-cover 桶）',
    `cited_by_count` INT UNSIGNED NULL DEFAULT NULL COMMENT '被引次数(来自 CitationMetrics,用于批量富化过滤)',

    -- ========================================
    -- 嵌入式值对象（JSON 字段）
    -- ========================================
    `publication_profile` JSON NULL DEFAULT NULL COMMENT '出版概况:含缩写标题/替代名称/出版历史/语言/宿主机构/索引信息等',
    `citation_metrics` JSON NULL DEFAULT NULL COMMENT '引用指标:含works_count/cited_by_count/h_index/i10_index/two_year_mean_citedness',
    `open_access` JSON NULL DEFAULT NULL COMMENT '开放获取信息:含is_oa/is_in_doaj/oa_type/apc_usd/apc_prices',
    `affiliated_societies` JSON NULL DEFAULT NULL COMMENT '关联学会列表:JSON数组,每项含url/organization',

    -- ========================================
    -- 审计字段
    -- ========================================
    `record_remarks` JSON NULL DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名',
    `deleted_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 普通索引
    INDEX `idx_venue_type` (`venue_type`) COMMENT '载体类型索引',
    INDEX `idx_title` (`title`(100)) COMMENT '标题前缀索引',
    INDEX `idx_provenance` (`provenance_code`) COMMENT '数据来源索引',
    INDEX `idx_cited_by_count` (`cited_by_count`) COMMENT '被引次数索引,支持范围查询过滤',

    CONSTRAINT chk_venue_country_code CHECK (
        `country_code` IS NULL OR REGEXP_LIKE(`country_code`, '^[A-Z]{2}$')
    )

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='出版载体表(最小聚合根):仅含核心身份标识和来源追踪,遵循CQRS原则';


-- ============================================================
-- 表 2: cat_venue_identifier (载体标识符表)
-- ============================================================
-- 表说明: 存储载体的各类标识符,支持一对多关系(如多个ISSN)
-- 重要: 所有标识符统一存储在此表
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_identifier` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',

    -- ========================================
    -- 标识符信息
    -- ========================================
    `identifier_type` VARCHAR(32) NOT NULL COMMENT '标识符类型:OPENALEX/ISSN/ISSN_L/ISBN/NLM/CODEN/MAG/FATCAT/WIKIDATA',
    `identifier_value` VARCHAR(255) NOT NULL COMMENT '标识符值',
    `is_primary` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否首选标识符(同类型中,0=否,1=是)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引 (防止重复)
    UNIQUE INDEX `uk_venue_type_value` (`venue_id`, `identifier_type`, `identifier_value`) COMMENT '载体+类型+值唯一索引',

    -- 复合索引 (反向查询优化 - 通过标识符查找载体)
    INDEX `idx_type_value` (`identifier_type`, `identifier_value`) COMMENT '类型+值索引,支持按标识符反查载体',

    -- 普通索引
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引,支持查询载体的所有标识符'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='载体标识符表:存储载体的各类标识符(OPENALEX/ISSN/ISSN_L/NLM/CODEN等)';


-- ============================================================
-- 表 3: cat_venue_publication_stats (年度发文统计表)
-- ============================================================
-- 表说明: 存储载体年度发文/引用统计数据,支持时序分析
-- 数据来源: OpenAlex
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_publication_stats` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',
    `year` SMALLINT NOT NULL COMMENT '统计年份(1900-2100)',

    -- ========================================
    -- 统计指标
    -- ========================================
    `works_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '该年发表作品数量',
    `cited_by_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '该年被引用次数',
    `oa_works_count` INT UNSIGNED NULL DEFAULT NULL COMMENT '该年OA作品数量(可选)',

    -- ========================================
    -- 审计字段（ChildJpaEntity: id + created_at + updated_at + version）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_venue_year` (`venue_id`, `year`) COMMENT '载体+年份唯一索引',

    -- 普通索引
    INDEX `idx_year` (`year`) COMMENT '年份索引',
    INDEX `idx_works_count` (`works_count`) COMMENT '发文量索引',
    INDEX `idx_cited_by_count` (`cited_by_count`) COMMENT '被引量索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='年度发文统计表:载体年度发文/引用统计,来源OpenAlex';


-- ============================================================
-- 表 4: cat_venue_instance (载体实例表 - 独立聚合根)
-- ============================================================
-- 表说明: 存储载体的具体实例(期刊的卷期、书籍的版次、会议的届次)
-- 设计: 独立聚合根,通过 venue_id 关联 cat_venue
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_instance` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',

    -- ========================================
    -- 期刊字段
    -- ========================================
    `volume` VARCHAR(100) NULL DEFAULT NULL COMMENT '卷号(如"45","2023")',
    `issue` VARCHAR(100) NULL DEFAULT NULL COMMENT '期号(如"3","Suppl 1")',

    -- ========================================
    -- 书籍字段
    -- ========================================
    `edition` VARCHAR(100) NULL DEFAULT NULL COMMENT '版次(书籍专用,如"2nd Edition")',

    -- ========================================
    -- 出版日期
    -- ========================================
    `publication_year` SMALLINT NOT NULL COMMENT '出版年份(必填)',
    `publication_month` TINYINT NULL DEFAULT NULL COMMENT '出版月份(1-12)',
    `publication_day` TINYINT NULL DEFAULT NULL COMMENT '出版日期(1-31)',

    -- ========================================
    -- 会议字段
    -- ========================================
    `conference_name` VARCHAR(255) NULL DEFAULT NULL COMMENT '会议名称(会议专用)',
    `conference_start_date` DATE NULL DEFAULT NULL COMMENT '会议开始日期(会议专用)',
    `conference_end_date` DATE NULL DEFAULT NULL COMMENT '会议结束日期(会议专用)',
    `conference_location` VARCHAR(200) NULL DEFAULT NULL COMMENT '会议地点(会议专用)',

    -- ========================================
    -- 扩展字段
    -- ========================================
    `instance_metadata` JSON NULL DEFAULT NULL COMMENT '实例元数据(灵活扩展)',

    -- ========================================
    -- 审计字段
    -- ========================================
    `record_remarks` JSON NULL DEFAULT NULL COMMENT 'JSON数组,备注/变更日志',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 复合索引 (期刊实例查询)
    INDEX `idx_venue_volume_issue` (`venue_id`, `volume`, `issue`) COMMENT '载体+卷+期复合索引',

    -- 普通索引
    INDEX `idx_publication_year` (`publication_year`) COMMENT '出版年份索引',
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='载体实例表(独立聚合根):期刊卷期/书籍版次/会议届次';


-- ============================================================
-- 表 5: cat_venue_mesh (期刊MeSH主题表)
-- ============================================================
-- 表说明: 存储期刊的MeSH主题词分类,来源Serfile
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_mesh` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',

    -- ========================================
    -- MeSH 描述符信息
    -- ========================================
    `descriptor_name` VARCHAR(255) NOT NULL COMMENT 'MeSH描述符名称',
    `descriptor_ui` VARCHAR(20) NULL DEFAULT NULL COMMENT 'MeSH描述符唯一标识符(格式:D000001)',
    `is_major_topic` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否主要主题(0=否,1=是)',

    -- ========================================
    -- MeSH 限定符信息
    -- ========================================
    `qualifier_name` VARCHAR(100) NULL DEFAULT NULL COMMENT 'MeSH限定符名称(可选)',
    `qualifier_ui` VARCHAR(20) NULL DEFAULT NULL COMMENT 'MeSH限定符唯一标识符(格式:Q000001)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_venue_mesh` (`venue_id`, `descriptor_name`(100))
        COMMENT '载体+描述符名称唯一索引',

    -- 普通索引
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引',
    INDEX `idx_descriptor_ui` (`descriptor_ui`) COMMENT '描述符UI索引',
    INDEX `idx_is_major` (`is_major_topic`) COMMENT '主要主题索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='期刊MeSH主题表:来源Serfile';


-- ============================================================
-- 表 6: cat_venue_relation (期刊关联表)
-- ============================================================
-- 表说明: 存储期刊之间的关联关系(前刊/后刊/合并/分拆等),来源Serfile
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_relation` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '当前载体ID(外键:cat_venue.id)',
    `related_venue_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '关联载体ID(系统内关联,可为空)',
    `related_nlm_id` VARCHAR(20) NULL DEFAULT NULL COMMENT '关联期刊NLM ID(系统外关联)',
    `related_title` VARCHAR(500) NOT NULL COMMENT '关联期刊标题(冗余,便于展示)',

    -- ========================================
    -- 关联属性
    -- ========================================
    `relation_type` VARCHAR(32) NOT NULL COMMENT '关联类型:PRECEDING/SUCCEEDING/ABSORBED/ABSORBED_BY/MERGED/SPLIT_FROM/CONTINUED_BY/CONTINUES',
    `effective_date` DATE NULL DEFAULT NULL COMMENT '关联生效日期',
    `notes` VARCHAR(500) NULL DEFAULT NULL COMMENT '关联说明备注',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_venue_related_type` (`venue_id`, `related_nlm_id`, `relation_type`)
        COMMENT '载体+关联NLM ID+类型唯一索引',

    -- 普通索引
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引',
    INDEX `idx_relation_type` (`relation_type`) COMMENT '关联类型索引',
    INDEX `idx_related_venue` (`related_venue_id`) COMMENT '关联载体索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='期刊关联表:期刊间演变关系(前刊/后刊/合并/分拆),来源Serfile';


-- ============================================================
-- 表 7: cat_venue_indexing_history (索引历史表)
-- ============================================================
-- 表说明: 存储期刊在MEDLINE/PubMed的索引历史变迁,来源Serfile
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_venue_indexing_history` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `venue_id` BIGINT UNSIGNED NOT NULL COMMENT '载体ID(外键:cat_venue.id)',

    -- ========================================
    -- 索引来源与状态
    -- ========================================
    `indexing_source` VARCHAR(32) NOT NULL COMMENT '索引来源:MEDLINE/PMC等',
    `currently_indexed` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '当前是否被索引(0=否,1=是)',
    `indexing_treatment` VARCHAR(20) NULL DEFAULT NULL COMMENT '索引处理方式:FULL/SELECTIVE',
    `citation_subset` VARCHAR(20) NULL DEFAULT NULL COMMENT '引用子集:IM/AIM/N/D/H/K/T/E/S/X/B/C/F/Q等',

    -- ========================================
    -- 索引范围(年/卷/期)
    -- ========================================
    `start_year` SMALLINT NULL DEFAULT NULL COMMENT '索引开始年份',
    `start_volume` VARCHAR(20) NULL DEFAULT NULL COMMENT '索引开始卷号',
    `start_issue` VARCHAR(20) NULL DEFAULT NULL COMMENT '索引开始期号',
    `end_year` SMALLINT NULL DEFAULT NULL COMMENT '索引结束年份(NULL=持续收录中)',
    `end_volume` VARCHAR(20) NULL DEFAULT NULL COMMENT '索引结束卷号',
    `end_issue` VARCHAR(20) NULL DEFAULT NULL COMMENT '索引结束期号',

    -- ========================================
    -- 审计字段（ChildJpaEntity: id + created_at + updated_at + version）
    -- ========================================
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_venue_source_start` (`venue_id`, `indexing_source`, `start_year`)
        COMMENT '载体+来源+开始年份唯一索引',

    -- 普通索引
    INDEX `idx_venue_id` (`venue_id`) COMMENT '载体索引',
    INDEX `idx_source_indexed` (`indexing_source`, `currently_indexed`) COMMENT '来源+索引状态复合索引',
    INDEX `idx_citation_subset` (`citation_subset`) COMMENT '引用子集索引'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='索引历史表:MEDLINE/PubMed收录历史变迁,来源Serfile';
