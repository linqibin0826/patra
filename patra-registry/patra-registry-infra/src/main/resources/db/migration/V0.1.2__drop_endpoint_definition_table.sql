-- 移除遗留的端点定义表及相关引用列，保持 Registry 模式与领域实现一致

ALTER TABLE `reg_prov_batching_cfg`
    DROP FOREIGN KEY `fk_reg_prov_batching_cfg__endpoint`,
    DROP INDEX `idx_reg_prov_batching_cfg__by_ep_cred`,
    DROP COLUMN `endpoint_id`;

ALTER TABLE `reg_prov_rate_limit_cfg`
    DROP FOREIGN KEY `fk_reg_prov_rate_limit_cfg__endpoint`,
    DROP INDEX `idx_reg_prov_rate_limit_cfg__by_ep_cred`,
    DROP COLUMN `endpoint_id`;

ALTER TABLE `reg_prov_credential`
    DROP FOREIGN KEY `fk_reg_prov_credential__endpoint`,
    DROP INDEX `idx_reg_prov_credential__dim`,
    DROP INDEX `uk_reg_prov_credential__preferred_one`,
    DROP COLUMN `endpoint_id`,
    DROP COLUMN `endpoint_id_key`;

ALTER TABLE `reg_prov_credential`
    ADD INDEX `idx_reg_prov_credential__dim`(`provenance_id`, `scope_code`, `task_type_key`, `credential_name`),
    ADD UNIQUE KEY `uk_reg_prov_credential__preferred_one`(`provenance_id`, `scope_code`, `task_type_key`, `preferred_1`);

DROP TABLE IF EXISTS `reg_prov_endpoint_def`;
