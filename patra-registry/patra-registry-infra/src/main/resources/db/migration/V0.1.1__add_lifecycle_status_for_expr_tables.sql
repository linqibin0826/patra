/*
 * 为 reg_prov_expr_capability / reg_prov_expr_render_rule / reg_prov_api_param_map 表补齐生命周期字段。
 * 用于与其他 Registry 配置表保持一致的生命周期过滤语义，避免读取到已弃用配置。
 */

ALTER TABLE `reg_prov_api_param_map`
    ADD COLUMN `lifecycle_status_code` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项'
        AFTER `task_type_key`;

ALTER TABLE `reg_prov_expr_capability`
    ADD COLUMN `lifecycle_status_code` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项'
        AFTER `task_type_key`;

ALTER TABLE `reg_prov_expr_render_rule`
    ADD COLUMN `lifecycle_status_code` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'
        COMMENT 'DICT CODE(type=lifecycle_status)：生命周期：读侧仅取 ACTIVE/有效项'
        AFTER `task_type_key`;
