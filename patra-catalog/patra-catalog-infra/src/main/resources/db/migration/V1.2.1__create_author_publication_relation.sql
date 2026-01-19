-- ============================================================
-- Patra Catalog 数据库 - 文献-作者关联表 DDL
-- ============================================================
-- 版本: V1.2.1
-- 领域: Author 领域
-- 设计阶段: 阶段 3 - SQL DDL 生成
-- 创建日期: 2025-01-18
-- 设计范围: 文献-作者关联表（2张表，预估2.1亿条记录）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 设计决策说明
-- ============================================================
-- **关键设计**:
-- 1. 职责分离：cat_publication_author 负责作者角色，
--    cat_publication_author_affiliation 负责机构归属
-- 2. 多机构支持：一个作者在一篇文献中可有多个机构归属
--    （联合培养、双聘、访问学者、多中心临床研究）
-- 3. 延迟消歧：先存储原始机构信息和标识符，后续批量关联到 OrganizationAggregate
-- 4. 冗余优化：affiliation 表冗余 publication_id/author_id，避免 JOIN
--
-- **表结构**:
-- 1. cat_publication_author (文献-作者关联，N:M) - 作者角色
-- 2. cat_publication_author_affiliation (作者-机构归属，1:N) - 多机构
-- ============================================================


-- ============================================================
-- 表: cat_publication_author (文献-作者关联表)
-- ============================================================
-- 表说明: 管理文献与作者的多对多关系，记录作者顺序和角色
-- 记录数预估: 初始 3000万 / 年增长 2200万 / 5年规模 1.4亿
-- 主要查询场景:
--   1. 查询某文献的所有作者(按顺序)(>5000次/天,高频)
--   2. 查询某作者的所有文献(>3000次/天,高频)
--   3. 筛选第一作者文献(>1000次/天,高频)
--   4. 筛选通讯作者文献(>800次/天,中频)
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_publication_author` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT NOT NULL COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息
    -- ========================================
    `publication_id` BIGINT NOT NULL COMMENT '出版物ID(外键:cat_publication.id)',
    `author_id` BIGINT NOT NULL COMMENT '作者ID(外键:cat_author.id)',

    -- ========================================
    -- 作者角色信息
    -- ========================================
    `author_order` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '作者顺序(1=第一作者,2=第二作者...)',
    `is_first_author` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否第一作者(0=否,1=是)',
    `is_corresponding_author` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否通讯作者(0=否,1=是)',
    `is_equal_contribution` BOOLEAN NOT NULL DEFAULT 0 COMMENT '是否同等贡献作者(0=否,1=是)',

    -- ========================================
    -- 联系方式
    -- ========================================
    `email` VARCHAR(255) NULL DEFAULT NULL COMMENT '作者邮箱(通讯作者时常填写)',

    -- ========================================
    -- 扩展字段
    -- ========================================
    `author_metadata` JSON NULL DEFAULT NULL COMMENT '作者元数据(灵活扩展)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_pub_author` (`publication_id`, `author_id`) COMMENT '防止同一作者在同一文献重复关联',
    UNIQUE INDEX `uk_author_order` (`publication_id`, `author_order`) COMMENT '防止同一文献的作者顺序重复,保证学术顺序正确性',

    -- 普通索引
    INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,支持查询某文献的所有作者(高频)',
    INDEX `idx_author` (`author_id`) COMMENT '作者索引,支持查询某作者的所有文献(高频)',
    INDEX `idx_first_author` (`is_first_author`) COMMENT '第一作者索引,支持筛选第一作者文献(学术评价重要指标)',
    INDEX `idx_corresponding` (`is_corresponding_author`) COMMENT '通讯作者索引,支持筛选通讯作者文献(联系查询)'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='文献-作者关联表:管理作者顺序和角色(大数据量表,预估1.4亿条)';


-- ============================================================
-- 表: cat_publication_author_affiliation (作者-机构归属表)
-- ============================================================
-- 表说明: 存储作者在特定文献发表时的所有机构归属，支持多机构
-- 记录数预估: 初始 4500万 / 年增长 3300万 / 5年规模 2.1亿
--   （平均每条 pub_author 有 1.5 个机构归属）
-- 主要查询场景:
--   1. 查询某文献所有作者的机构(>3000次/天,高频)
--   2. 查询某作者在所有文献的机构(>1000次/天,中频)
--   3. 按机构筛选作者文献(>500次/天,中频)
--   4. 批量消歧待处理记录(定时任务,低频)
-- 设计原则:
--   1. 一个作者在一篇文献中可有多个机构归属
--   2. 保留原始机构信息，支持延迟消歧到 OrganizationAggregate
--   3. 存储机构标识符（ROR/Ringgold/GRID），加速后续消歧
--   4. 冗余 publication_id/author_id 避免 JOIN cat_publication_author
-- ============================================================

CREATE TABLE IF NOT EXISTS `cat_publication_author_affiliation` (
    -- ========================================
    -- 主键
    -- ========================================
    `id` BIGINT NOT NULL COMMENT '主键,雪花算法生成',

    -- ========================================
    -- 关联信息（冗余 publication_id/author_id 避免 JOIN）
    -- ========================================
    `pub_author_id` BIGINT NOT NULL COMMENT '文献-作者关联ID(外键:cat_publication_author.id)',
    `publication_id` BIGINT NOT NULL COMMENT '出版物ID(冗余,避免JOIN)',
    `author_id` BIGINT NOT NULL COMMENT '作者ID(冗余,避免JOIN)',

    -- ========================================
    -- 原始机构信息
    -- ========================================
    `affiliation_order` INT UNSIGNED NOT NULL DEFAULT 1 COMMENT '机构顺序(1=第一机构,2=第二机构...)',
    `affiliation_string` VARCHAR(2000) NOT NULL COMMENT '原始机构字符串(外部采集,未标准化)',

    -- ========================================
    -- 机构标识符（用于消歧，PubMed 可能提供）
    -- ========================================
    `ror_id` VARCHAR(50) NULL DEFAULT NULL COMMENT 'ROR ID(如"03vek6s52")',
    `ringgold_id` VARCHAR(20) NULL DEFAULT NULL COMMENT 'Ringgold ID',
    `grid_id` VARCHAR(50) NULL DEFAULT NULL COMMENT 'GRID ID(历史数据,已废弃)',

    -- ========================================
    -- 消歧结果（延迟填充）
    -- ========================================
    `organization_id` BIGINT NULL DEFAULT NULL COMMENT '关联机构ID(外键:cat_organization.id,延迟填充)',
    `disambiguation_status` VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '消歧状态:PENDING/MATCHED/UNMATCHED/AMBIGUOUS',
    `disambiguation_method` VARCHAR(50) NULL DEFAULT NULL COMMENT '消歧方法:ROR_ID/RINGGOLD/GRID/NAME_MATCH/MANUAL',
    `disambiguation_score` DECIMAL(5,4) NULL DEFAULT NULL COMMENT '消歧置信度(0.0000-1.0000)',
    `disambiguated_at` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '消歧时间(UTC)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',

    -- 唯一索引
    UNIQUE INDEX `uk_pub_author_order` (`pub_author_id`, `affiliation_order`) COMMENT '同一作者-文献关联的机构顺序唯一',

    -- 普通索引
    INDEX `idx_pub_author` (`pub_author_id`) COMMENT '作者-文献关联索引,查询某关联的所有机构',
    INDEX `idx_publication` (`publication_id`) COMMENT '出版物索引,查询某文献所有作者的机构',
    INDEX `idx_author` (`author_id`) COMMENT '作者索引,查询某作者在所有文献的机构',
    INDEX `idx_organization` (`organization_id`) COMMENT '机构索引,按机构筛选作者文献',
    INDEX `idx_ror_id` (`ror_id`) COMMENT 'ROR ID索引,加速基于ROR的消歧',
    INDEX `idx_ringgold_id` (`ringgold_id`) COMMENT 'Ringgold ID索引,加速基于Ringgold的消歧',
    INDEX `idx_disambiguation_status` (`disambiguation_status`) COMMENT '消歧状态索引,批量处理待消歧记录'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='作者-机构归属表:支持一个作者在一篇文献中的多机构归属(预估2.1亿条)';
