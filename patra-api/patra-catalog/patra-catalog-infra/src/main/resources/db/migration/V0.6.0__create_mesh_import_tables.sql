-- ============================================================
-- Patra Catalog - MeSH 数据导入相关表 DDL
-- ============================================================
-- 版本: V0.6.0
-- 创建日期: 2025-11-20
-- 设计范围: MeSH 数据首次导入功能的3张管理表
-- 作者: Patra Lin
-- MySQL 版本: 8.0+
-- 字符集: utf8mb4 (支持完整Unicode)
-- 排序规则: utf8mb4_unicode_ci (支持多语言准确排序)
-- ============================================================

-- ============================================================
-- 表清单与依赖关系
-- ============================================================
-- 1. cat_mesh_import_task (MeSH 导入任务表) - 无依赖
-- 2. cat_mesh_table_progress (MeSH 表进度记录表) - 依赖 cat_mesh_import_task
-- 3. cat_mesh_batch_detail (MeSH 批次详情表) - 依赖 cat_mesh_import_task
-- ============================================================

-- ============================================================
-- 表 1: cat_mesh_import_task (MeSH 导入任务表)
-- ============================================================
-- 表说明: 管理 MeSH 数据导入任务的完整生命周期
-- 功能:
--   1. 跟踪导入任务的状态转换 (PENDING → PROCESSING → SUCCESS/FAILED)
--   2. 记录 XML 文件元数据 (MD5哈希、文件大小) 用于数据完整性验证
--   3. 统计总体进度 (总记录数、已处理数、失败批次数)
--   4. 支持断点续传 (通过表进度记录实现)
-- 记录数预估: 初始 <100 / 年增长 12 (每月一次更新)
-- 主要查询场景:
--   1. 查询正在运行的任务 (status = 'PROCESSING')
--   2. 按创建时间倒序查询任务历史
--   3. 查询失败任务用于重试
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_import_task` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL COMMENT '主键,雪花算法生成',
    `task_name` VARCHAR(100) NOT NULL COMMENT '任务名称 (如 "2025年MeSH数据首次导入")',
    `status` VARCHAR(20) NOT NULL COMMENT '任务状态: PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED',
    `source_url` VARCHAR(500) NOT NULL COMMENT 'NLM 数据源 URL',
    `xml_file_hash` VARCHAR(32) NULL DEFAULT NULL COMMENT 'XML 文件 MD5 哈希 (验证数据完整性)',
    `xml_file_size` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT 'XML 文件大小 (字节)',
    `total_records` INT UNSIGNED NULL DEFAULT NULL COMMENT '总记录数 (约 350,000)',
    `processed_records` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已处理记录数 (用于进度计算)',
    `failed_batch_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '失败批次数',
    `last_error_message` TEXT NULL DEFAULT NULL COMMENT '最后错误信息',
    `start_time` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '开始时间 (UTC, 微秒精度)',
    `end_time` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '结束时间 (UTC, 微秒精度)',

    -- ========================================
    -- 审计字段 (BaseDO 标准字段)
    -- ========================================
    `record_remarks` JSON NULL DEFAULT NULL COMMENT 'JSON数组, 备注/变更日志',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC, 微秒精度)',
    `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名 (冗余-审计友好)',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC, 微秒精度)',
    `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名 (冗余-审计友好)',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号 (每次更新自增)',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP (二进制, 支持IPv4/IPv6)',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标志 (0=正常, 1=已删除)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',
    INDEX `idx_import_task_status` (`status`) COMMENT '任务状态索引 (用于筛选正在运行的任务)',
    INDEX `idx_import_task_created_at` (`created_at`) COMMENT '创建时间索引 (用于按时间查询任务列表)',
    INDEX `idx_import_task_deleted` (`deleted`) COMMENT '软删除标志索引 (MyBatis-Plus 自动使用)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='MeSH 导入任务表 - 管理导入任务生命周期和进度跟踪';

-- ============================================================
-- 表 2: cat_mesh_table_progress (MeSH 表进度记录表)
-- ============================================================
-- 表说明: 跟踪每张表的导入进度, 支持断点续传
-- 功能:
--   1. 记录每张表的导入进度 (总数、已处理数、失败数)
--   2. 记录最后处理批次号 (断点续传的关键字段)
--   3. 支持按表维度统计导入状态
-- 记录数预估: 每个任务 6 条记录 (6张表) × 任务数
-- 主要查询场景:
--   1. 按任务 ID 查询所有表进度 (import_id)
--   2. 按表名查询特定表进度 (table_name)
--   3. 断点续传时读取最后批次号 (import_id + table_name)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_table_progress` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL COMMENT '主键,雪花算法生成',
    `import_id` BIGINT UNSIGNED NOT NULL COMMENT '关联任务 ID (外键: cat_mesh_import_task.id)',
    `table_name` VARCHAR(50) NOT NULL COMMENT '表名 (如 "cat_mesh_descriptor")',
    `total_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '总记录数',
    `processed_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '已处理数',
    `failed_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '失败数',
    `status` VARCHAR(20) NOT NULL COMMENT '表状态: NOT_STARTED/IN_PROGRESS/COMPLETED/FAILED',
    `last_batch_num` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后处理批次号 (用于断点续传)',

    -- ========================================
    -- 审计字段 (BaseDO 标准字段)
    -- ========================================
    `record_remarks` JSON NULL DEFAULT NULL COMMENT 'JSON数组, 备注/变更日志',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC, 微秒精度)',
    `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名 (冗余-审计友好)',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC, 微秒精度)',
    `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名 (冗余-审计友好)',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号 (每次更新自增)',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP (二进制, 支持IPv4/IPv6)',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标志 (0=正常, 1=已删除)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',
    INDEX `idx_table_progress_import_id` (`import_id`) COMMENT '任务 ID 索引 (用于查询某任务的所有表进度)',
    INDEX `idx_table_progress_table_name` (`table_name`) COMMENT '表名索引 (用于快速定位特定表的进度)',
    UNIQUE INDEX `uk_table_progress_import_table` (`import_id`, `table_name`) COMMENT '任务ID+表名唯一索引 (断点续传查询)',
    INDEX `idx_table_progress_deleted` (`deleted`) COMMENT '软删除标志索引 (MyBatis-Plus 自动使用)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='MeSH 表进度记录表 - 跟踪每张表的导入进度, 支持断点续传';

-- ============================================================
-- 表 3: cat_mesh_batch_detail (MeSH 批次详情表)
-- ============================================================
-- 表说明: 记录每个批次的处理详情, 用于错误追踪和批次级别重试
-- 功能:
--   1. 记录每个批次的处理状态 (PENDING/PROCESSING/SUCCESS/FAILED)
--   2. 记录批次失败的错误信息和重试次数
--   3. 支持批次级别的细粒度重试 (仅重新处理失败批次)
-- 记录数预估: 每张表约 35 个批次 (35000条/1000条) × 6张表 × 任务数
-- 主要查询场景:
--   1. 按任务 ID 查询所有批次 (import_id)
--   2. 查询失败批次用于重试 (import_id + table_name + status = 'FAILED')
--   3. 唯一定位批次 (import_id + table_name + batch_num)
-- ============================================================


CREATE TABLE IF NOT EXISTS `cat_mesh_batch_detail` (
    -- ========================================
    -- 业务字段
    -- ========================================
    `id` BIGINT UNSIGNED NOT NULL COMMENT '主键,雪花算法生成',
    `import_id` BIGINT UNSIGNED NOT NULL COMMENT '关联任务 ID (外键: cat_mesh_import_task.id)',
    `table_name` VARCHAR(50) NOT NULL COMMENT '表名 (如 "cat_mesh_descriptor")',
    `batch_num` INT UNSIGNED NOT NULL COMMENT '批次序号 (从 1 开始)',
    `batch_size` INT UNSIGNED NOT NULL COMMENT '批次大小 (本批次实际处理的记录数)',
    `status` VARCHAR(20) NOT NULL COMMENT '批次状态: PENDING/PROCESSING/SUCCESS/FAILED',
    `retry_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '重试次数 (最多 3 次)',
    `error_message` TEXT NULL DEFAULT NULL COMMENT '错误信息',
    `start_time` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '开始时间 (UTC, 微秒精度)',
    `end_time` TIMESTAMP(6) NULL DEFAULT NULL COMMENT '结束时间 (UTC, 微秒精度)',

    -- ========================================
    -- 审计字段 (BaseDO 标准字段)
    -- ========================================
    `record_remarks` JSON NULL DEFAULT NULL COMMENT 'JSON数组, 备注/变更日志',
    `created_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC, 微秒精度)',
    `created_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '创建人姓名 (冗余-审计友好)',
    `updated_at` TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC, 微秒精度)',
    `updated_by` BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100) NULL DEFAULT NULL COMMENT '更新人姓名 (冗余-审计友好)',
    `version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号 (每次更新自增)',
    `ip_address` VARBINARY(16) NULL DEFAULT NULL COMMENT '请求者IP (二进制, 支持IPv4/IPv6)',
    `deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '软删除标志 (0=正常, 1=已删除)',

    -- ========================================
    -- 主键和索引
    -- ========================================
    PRIMARY KEY (`id`) COMMENT '主键聚簇索引',
    INDEX `idx_batch_detail_import_id` (`import_id`) COMMENT '任务 ID 索引 (用于查询某任务的所有批次)',
    UNIQUE INDEX `uk_batch_detail_import_table_batch` (`import_id`, `table_name`, `batch_num`) COMMENT '任务ID+表名+批次号唯一索引 (唯一标识批次)',
    INDEX `idx_batch_detail_status` (`status`) COMMENT '批次状态索引 (用于筛选失败批次)',
    INDEX `idx_batch_detail_deleted` (`deleted`) COMMENT '软删除标志索引 (MyBatis-Plus 自动使用)'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
COMMENT='MeSH 批次详情表 - 记录每个批次的处理详情, 用于错误追踪和批次级别重试';

-- ============================================================
-- DDL 执行完成
-- ============================================================
-- 说明:
-- 1. 所有表使用 InnoDB 引擎,支持事务和外键
-- 2. 字符集 utf8mb4 + utf8mb4_unicode_ci 支持多语言和表情符号
-- 3. 主键使用雪花算法生成的 BIGINT UNSIGNED, MyBatis-Plus 自动填充
-- 4. 审计字段完整包含 BaseDO 的 10 个标准字段
-- 5. 索引设计考虑高频查询场景 (status, created_at, 唯一约束)
-- 6. 支持软删除 (deleted 字段 + 索引)
-- 7. 支持乐观锁 (version 字段)
-- ============================================================
