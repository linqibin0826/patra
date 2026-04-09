-- 添加 cited_by_count 快速访问字段（来自 CitationMetrics JSON 列）
-- 用于批量富化 Job 按引用量过滤期刊
ALTER TABLE cat_venue ADD COLUMN cited_by_count INT UNSIGNED NULL DEFAULT NULL;

-- 从 JSON 列回填已有数据
UPDATE cat_venue
SET cited_by_count = JSON_UNQUOTE(JSON_EXTRACT(citation_metrics, '$.citedByCount'))
WHERE citation_metrics IS NOT NULL
  AND JSON_EXTRACT(citation_metrics, '$.citedByCount') IS NOT NULL;

-- 添加索引（支持范围查询过滤）
CREATE INDEX idx_cited_by_count ON cat_venue (cited_by_count);
