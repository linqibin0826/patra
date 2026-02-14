-- ============================================================
-- 为 cat_publication_alternative_abstract 添加 source_type 列
-- ============================================================
-- 版本: V1.5.2
-- 变更原因: 支持同一语言多种来源类型的摘要（如 publisher、plain-language-summary）
-- 影响: 添加 source_type 列，调整唯一索引以包含 source_type
-- ============================================================

-- 1. 添加 source_type 列
ALTER TABLE `cat_publication_alternative_abstract`
    ADD COLUMN `source_type` VARCHAR(64) NOT NULL DEFAULT 'unknown'
        COMMENT '摘要来源类型（如 publisher、plain-language-summary）'
        AFTER `language_code`;

-- 2. 删除原唯一索引（publication_id + language_code）
ALTER TABLE `cat_publication_alternative_abstract`
    DROP INDEX `uk_abstract_lang`;

-- 3. 创建新唯一索引（publication_id + language_code + source_type）
ALTER TABLE `cat_publication_alternative_abstract`
    ADD UNIQUE INDEX `uk_abstract_lang_source` (`publication_id`, `language_code`, `source_type`)
        COMMENT '出版物+语言+来源类型唯一索引,支持同一语言多种来源类型';
