-- 为期刊表新增封面图片 URL 字段（来自 Wikidata P18 / Wikimedia Commons）
ALTER TABLE cat_venue
    ADD COLUMN image_url VARCHAR(2048) COMMENT 'Wikimedia Commons 封面图片 URL（来自 Wikidata P18）';
