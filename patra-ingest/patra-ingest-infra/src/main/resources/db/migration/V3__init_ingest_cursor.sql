-- =====================================================================
-- Patra Ingest — PostgreSQL 17 baseline schema: V3 游标
-- 来源: V0.1.0__init_ingest_schema.sql 第 7-8 段
-- 包含: ing_cursor（当前值）+ ing_cursor_event（推进事件，仅追加）（2 表）
-- 注意: normalized_numeric / prev_numeric / new_numeric 为 DECIMAL(38,0) → NUMERIC(38,0)
-- 已移除: AUTO_INCREMENT / ENGINE/CHARSET/COLLATE / ON UPDATE / CREATE TABLE IF NOT EXISTS / 反引号
-- =====================================================================

-- ======================================================================
-- 7) 通用游标 (当前值): (source_code + operation + cursor_key + namespace) 唯一
-- ======================================================================
/* ====================================================================
 * 表: ing_cursor —— 当前游标
 * 语义: 来源 + 操作 + 游标键 + 命名空间的当前推进值; 兼容 TIME/ID/TOKEN 类型。
 * 要点:
 *  - 唯一: (provenance_code, operation_code, cursor_key, namespace_scope_code, namespace_key);
 *  - 归一化值: normalized_instant / normalized_numeric 便于排序和范围查询;
 *  - 血缘: 最近推进的 schedule/plan/slice/task/run/batch (逻辑关联, 无 FK)。
 * 索引: uk_cursor_ns / idx_cursor_src_key / idx_cursor_sort_time / idx_cursor_sort_id / idx_cursor_lineage.
 * ==================================================================== */
CREATE TABLE ing_cursor
(
    id                   BIGINT          NOT NULL,

    provenance_code      VARCHAR(64)     NOT NULL,
    operation_code       VARCHAR(32)     NOT NULL,
    cursor_key           VARCHAR(64)     NOT NULL,
    namespace_scope_code VARCHAR(32)     NOT NULL DEFAULT 'GLOBAL',
    namespace_key        CHAR(64)        NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000',

    cursor_type_code     VARCHAR(32)     NOT NULL,
    cursor_value         VARCHAR(1024)   NOT NULL,
    observed_max_value   VARCHAR(1024)   NULL,

    normalized_instant   timestamptz(6)  NULL,
    normalized_numeric   NUMERIC(38, 0)  NULL,

    schedule_instance_id BIGINT          NULL,
    plan_id              BIGINT          NULL,
    slice_id             BIGINT          NULL,
    task_id              BIGINT          NULL,
    last_run_id          BIGINT          NULL,
    last_batch_id        BIGINT          NULL,
    expr_hash            CHAR(64)        NULL,

    version              BIGINT          NOT NULL DEFAULT 0,

    -- 审计字段
    record_remarks       jsonb           NULL,
    ip_address           bytea           NULL,
    created_at           timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           BIGINT          NULL,
    created_by_name      VARCHAR(100)    NULL,
    updated_at           timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           BIGINT          NULL,
    updated_by_name      VARCHAR(100)    NULL,

    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_cursor_ns ON ing_cursor (provenance_code, operation_code, cursor_key, namespace_scope_code, namespace_key);
CREATE INDEX idx_cursor_src_key ON ing_cursor (provenance_code, operation_code, cursor_key);
CREATE INDEX idx_cursor_sort_time ON ing_cursor (cursor_type_code, normalized_instant);
CREATE INDEX idx_cursor_sort_id ON ing_cursor (cursor_type_code, normalized_numeric);
CREATE INDEX idx_cursor_lineage ON ing_cursor (schedule_instance_id, plan_id, slice_id, task_id, last_run_id, last_batch_id);
CREATE INDEX idx_cursor_audit_created_by ON ing_cursor (created_by);
CREATE INDEX idx_cursor_audit_updated_by ON ing_cursor (updated_by);

CREATE TRIGGER trg_ing_cursor_updated_at
    BEFORE UPDATE ON ing_cursor
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ======================================================================
-- 8) 游标推进事件 (不可变): 每次成功推进一条事件记录; 支持重放和全链路追踪
-- ======================================================================
/* ====================================================================
 * 表: ing_cursor_event —— 游标推进事件
 * 语义: 仅追加审计事件; 每次成功推进一条记录, 支持重放和全链路追踪。
 * 要点:
 *  - 幂等: idempotent_key 唯一;
 *  - 血缘: schedule/plan/slice/task/run/batch 逻辑关联 (无 FK)。
 * 索引: uk_cur_evt_idem / idx_cur_evt_timeline / idx_cur_evt_instant / idx_cur_evt_numeric / idx_cur_evt_lineage.
 * ==================================================================== */
CREATE TABLE ing_cursor_event
(
    id                   BIGINT          NOT NULL,

    provenance_code      VARCHAR(64)     NOT NULL,
    operation_code       VARCHAR(32)     NOT NULL,
    cursor_key           VARCHAR(64)     NOT NULL,
    namespace_scope_code VARCHAR(32)     NOT NULL,
    namespace_key        CHAR(64)        NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000',

    cursor_type_code     VARCHAR(32)     NOT NULL,
    prev_value           VARCHAR(1024)   NULL,
    new_value            VARCHAR(1024)   NOT NULL,
    observed_max_value   VARCHAR(1024)   NULL,

    prev_instant         timestamptz(6)  NULL,
    new_instant          timestamptz(6)  NULL,
    prev_numeric         NUMERIC(38, 0)  NULL,
    new_numeric          NUMERIC(38, 0)  NULL,
    window_from          timestamptz(6)  NULL,
    window_to            timestamptz(6)  NULL,

    direction_code       VARCHAR(16)     NULL,

    idempotent_key       CHAR(64)        NOT NULL,

    schedule_instance_id BIGINT          NULL,
    plan_id              BIGINT          NULL,
    slice_id             BIGINT          NULL,
    task_id              BIGINT          NULL,
    run_id               BIGINT          NULL,
    batch_id             BIGINT          NULL,
    expr_hash            CHAR(64)        NULL,

    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_cur_evt_idem ON ing_cursor_event (idempotent_key);

CREATE INDEX idx_cur_evt_timeline ON ing_cursor_event (provenance_code, operation_code, cursor_key, namespace_scope_code, namespace_key);
CREATE INDEX idx_cur_evt_instant ON ing_cursor_event (cursor_type_code, new_instant);
CREATE INDEX idx_cur_evt_numeric ON ing_cursor_event (cursor_type_code, new_numeric);
CREATE INDEX idx_cur_evt_lineage ON ing_cursor_event (schedule_instance_id, plan_id, slice_id, task_id, run_id, batch_id);
