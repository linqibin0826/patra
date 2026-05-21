-- Patra 业务库 + Spring Batch 元数据库初始化
-- 由 postgres:17 镜像 entrypoint 在容器首次创建时执行一次（initdb 完成后）
-- 不需要 CHARACTER SET 子句：PG 默认数据库使用 UTF-8 编码（initdb 时设定）
CREATE DATABASE patra_registry;
CREATE DATABASE patra_ingest;
CREATE DATABASE patra_catalog;
CREATE DATABASE patra_storage;
CREATE DATABASE patra_batch;
