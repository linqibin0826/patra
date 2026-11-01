-- 镜像只在数据目录为空的首次启动时执行 /docker-entrypoint-initdb.d/ 下的脚本。
-- 只建库；表、物化视图、TTL 由 patra-analytics（PAP-168）管理。
CREATE DATABASE IF NOT EXISTS patra_analytics;
