-- ============================================================
-- MeSH 树形编号表：支持共享树形位置
-- ============================================================
-- 版本: V1.4.3
-- 变更原因: MeSH 2026 引入共享树形位置特性
--           同一个 TreeNumber 可以属于多个 Descriptor
-- 示例: B03.300.390.400.001 同时属于两个不同的 Descriptor
-- 作者: Patra Lin
-- 日期: 2026-01-21
-- ============================================================

-- 删除原有的单字段唯一约束
ALTER TABLE `cat_mesh_tree_number`
    DROP INDEX `uk_tree_number`;

-- 创建新的复合唯一约束（tree_number + descriptor_ui）
-- 这允许同一个 tree_number 属于多个 descriptor，但同一 descriptor 下的 tree_number 不能重复
ALTER TABLE `cat_mesh_tree_number`
    ADD UNIQUE INDEX `uk_tree_number_descriptor` (`tree_number`, `descriptor_ui`)
    COMMENT '树形编号+主题词UI复合唯一索引,支持MeSH 2026共享树形位置';
