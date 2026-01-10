-- ============================================================
-- Patra Catalog 数据库 - 文献-作者关联表 DDL
-- ============================================================
-- 设计阶段: Author 领域模型重构
-- 创建日期: 2025-01-18
-- 修改日期: 2026-01-09
-- 设计范围: 文献-作者关联表（1张表，预估1.4亿条记录）
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_0900_ai_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 设计决策说明
-- ============================================================
-- **关键变更（相比旧版本）**:
-- 1. 机构归属放在此关联表，记录"发表时的归属"
--    - 作者跳槽、访问学者等场景下，机构随文献变化
--    - 原 cat_author_organization 表删除，机构信息迁移至此
-- 2. 支持逻辑删除
-- 3. 简化字段结构，移除不常用的 contribution_type
--
-- **表结构**:
-- 1. cat_publication_author (文献-作者关联，N:M) - 含机构归属
-- ============================================================


-- ============================================================
-- 表: cat_publication_author (文献-作者关联表)
-- ============================================================
-- 表说明: 管理文献与作者的多对多关系,记录作者顺序、角色和机构归属
-- 记录数预估: 初始 3000万 / 年增长 2200万 / 5年规模 1.4亿
-- 主要查询场景:
--   1. 查询某文献的所有作者(按顺序)(>5000次/天,高频)
--   2. 查询某作者的所有文献(>3000次/天,高频)
--   3. 筛选第一作者文献(>1000次/天,高频)
--   4. 筛选通讯作者文献(>800次/天,中频)
--   5. 按机构筛选作者文献(>500次/天,中频)
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
    -- 机构归属（发表时的归属）
    -- ========================================
    `affiliation_string` VARCHAR(1000) NULL DEFAULT NULL COMMENT '原始机构字符串(外部采集,未标准化)',
    `organization_id` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '机构ID(外键:cat_organization.id,延迟填充)',

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
    INDEX `idx_corresponding` (`is_corresponding_author`) COMMENT '通讯作者索引,支持筛选通讯作者文献(联系查询)',
    INDEX `idx_organization` (`organization_id`) COMMENT '机构索引,支持按机构筛选作者文献'

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
COMMENT='文献-作者关联表:管理作者顺序/角色/机构归属,支持逻辑删除(大数据量表,预估1.4亿条)';
