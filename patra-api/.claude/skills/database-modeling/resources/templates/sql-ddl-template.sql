-- =============================================================================
-- SQL DDL 生成模板
-- 说明：此模板用于根据表设计自动生成标准化的 CREATE TABLE 语句
-- =============================================================================

-- 表名：{{table_name}}
-- 描述：{{table_description}}
-- 创建时间：{{generate_time}}
-- =============================================================================

CREATE TABLE IF NOT EXISTS `{{table_name}}` (
    -- =========================================================================
    -- 业务字段
    -- =========================================================================
    {{#each business_fields}}
    `{{field_name}}` {{field_type}}{{#if field_length}}({{field_length}}){{/if}} {{constraints}} COMMENT '{{comment}}',
    {{/each}}

    -- =========================================================================
    -- 审计字段（标准化）
    -- =========================================================================
    `record_remarks`  JSON            NULL COMMENT 'JSON 数组，备注/变更日志',
    `version`         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`      VARBINARY(16)   NULL COMMENT '请求者 IP（二进制，支持 IPv4/IPv6）',
    `created_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间（UTC）',
    `created_by`      BIGINT UNSIGNED NULL COMMENT '创建人 ID',
    `created_by_name` VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间（UTC）',
    `updated_by`      BIGINT UNSIGNED NULL COMMENT '更新人 ID',
    `updated_by_name` VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '软删除标志（0=正常，1=已删除）',

    -- =========================================================================
    -- 主键定义
    -- =========================================================================
    PRIMARY KEY (`{{primary_key}}`){{#if has_indexes}},{{/if}}

    {{#if has_indexes}}
    -- =========================================================================
    -- 索引定义
    -- =========================================================================
    {{#each indexes}}
    {{index_type}} `{{index_name}}` ({{index_columns}}){{#unless @last}},{{/unless}} {{#if index_comment}}COMMENT '{{index_comment}}'{{/if}}
    {{/each}}
    {{/if}}

) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='{{table_comment}}';

-- =============================================================================
-- 索引分析报告
-- =============================================================================
{{#each index_analysis}}
-- {{index_name}}:
--   类型: {{index_type}}
--   字段: {{index_columns}}
--   选择性: {{selectivity}}
--   建议: {{recommendation}}
{{/each}}

-- =============================================================================
-- 数据初始化（可选）
-- =============================================================================
{{#if has_init_data}}
-- INSERT INTO `{{table_name}}` (`{{init_columns}}`) VALUES
{{#each init_data}}
-- ({{values}}){{#unless @last}},{{/unless}}
{{/each}};
{{/if}}

-- =============================================================================
-- 权限授予（可选）
-- =============================================================================
-- GRANT SELECT, INSERT, UPDATE ON `{{database_name}}`.`{{table_name}}` TO '{{app_user}}'@'%';
-- GRANT SELECT ON `{{database_name}}`.`{{table_name}}` TO '{{readonly_user}}'@'%';

-- =============================================================================
-- 表维护建议
-- =============================================================================
-- 1. 定期分析表统计信息：ANALYZE TABLE `{{table_name}}`;
-- 2. 检查表碎片：SHOW TABLE STATUS LIKE '{{table_name}}';
-- 3. 优化表（慎用）：OPTIMIZE TABLE `{{table_name}}`;
-- 4. 监控慢查询：检查 slow_query_log 中涉及此表的查询

-- =============================================================================
-- 相关字典表（如果使用了字典代码）
-- =============================================================================
{{#if has_dict_tables}}
{{#each dict_tables}}
-- 字典表：{{dict_table_name}}
-- 用途：{{dict_purpose}}
-- 关联字段：{{related_field}}
{{/each}}
{{/if}}