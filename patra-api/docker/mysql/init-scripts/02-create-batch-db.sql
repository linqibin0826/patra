-- ============================================================
-- Spring Batch 共享数据库初始化脚本
-- ============================================================
-- 此脚本创建独立的 Batch 元数据数据库，用于存储 Spring Batch
-- 的作业执行状态、步骤进度和断点续传信息。
--
-- 使用方式：
--   1. 复制到 ~/.patra/docker/mysql/init/ 目录（Docker 自动执行）
--   2. 或手动执行: mysql -u root -p < 02-create-batch-db.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS patra_batch
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

-- 注意：Batch 表由 BatchSchemaInitializer 自动创建，无需在此定义
