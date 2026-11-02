-- 同主题邻居（analytics PAP-170）：按主题词 / 期刊取最新收录的几篇，靠索引倒序直接取前几行，不排序整组。
-- 主题词索引不做部分索引：major 可能标在副主题词上，按索引倒序扫、逐行判断。
CREATE INDEX idx_mesh_heading_descriptor_pub ON cat_publication_mesh_heading (descriptor_ui, publication_id DESC);
CREATE INDEX idx_pub_venue_id_live ON cat_publication (venue_id, id DESC) WHERE deleted_at IS NULL;
