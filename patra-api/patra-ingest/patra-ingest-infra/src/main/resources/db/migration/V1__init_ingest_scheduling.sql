-- =====================================================================
-- Patra Ingest — PostgreSQL 17 baseline schema: V1 调度根
-- 来源: V0.1.0__init_ingest_schema.sql 第 1-2 段
-- 包含: ing_schedule_instance + ing_plan（2 表）
-- 已移除: AUTO_INCREMENT / ENGINE/CHARSET/COLLATE / ON UPDATE / CREATE TABLE IF NOT EXISTS / 反引号
-- =====================================================================

-- 公共触发器函数：仅当 UPDATE 未显式修改 updated_at 时（即非 JPA 路径），自动填充 now()
-- JPA 路径由 Hibernate @LastModifiedDate 显式赋值，会先于触发器执行，触发器跳过
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ======================================================================
-- 1) 调度实例: 外部触发事件
-- ======================================================================
/* ====================================================================
 * 表: ing_schedule_instance —— 调度实例
 * 语义: 记录外部调度触发事件的"根", 持久化当时的触发参数。
 * 要点:
 *  - provenance_code 与 reg_provenance.provenance_code 对齐 (逻辑关联, 无 FK);
 *  - 配置快照和表达式原型保存在 ing_plan 层级。
 * 索引: idx_sched_src(scheduler_code, scheduler_job_id, scheduler_log_id).
 * ==================================================================== */
CREATE TABLE ing_schedule_instance
(
    id                BIGINT          NOT NULL,
    scheduler_code    VARCHAR(32)     NOT NULL DEFAULT 'XXL',
    scheduler_job_id  VARCHAR(64)     NULL,
    scheduler_log_id  VARCHAR(64)     NULL,
    trigger_type_code VARCHAR(32)     NOT NULL DEFAULT 'SCHEDULE',
    triggered_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    trigger_params    jsonb           NULL,
    provenance_code   VARCHAR(64)     NOT NULL,

    PRIMARY KEY (id)
);

CREATE INDEX idx_sched_src ON ing_schedule_instance (scheduler_code, scheduler_job_id, scheduler_log_id);

-- ======================================================================
-- 2) 计划蓝图: 定义总体窗口和分片策略 (表达式原型, 无本地化条件)
-- ======================================================================
/* ====================================================================
 * 表: ing_plan —— 计划蓝图
 * 语义: 采集批次的蓝图, 定义总体窗口和分片策略; 不执行。
 * 要点:
 *  - 与 ing_schedule_instance 逻辑关联 (无 FK);
 *  - 使用 *_code (字典) 存储操作/策略/状态;
 *  - plan_key 作为外部幂等/可读键 (唯一)。
 * 索引: uk_plan_key / idx_plan_sched / idx_plan_status / idx_plan_expr.
 * ==================================================================== */
CREATE TABLE ing_plan
(
    id                         BIGINT          NOT NULL,
    schedule_instance_id       BIGINT          NOT NULL,
    plan_key                   VARCHAR(128)    NOT NULL,

    provenance_code            VARCHAR(64)     NULL,
    operation_code             VARCHAR(32)     NOT NULL,

    expr_proto_hash            CHAR(64)        NOT NULL,
    expr_proto_snapshot        jsonb           NULL,
    provenance_config_snapshot jsonb           NULL,
    provenance_config_hash     CHAR(64)        NULL,
    slice_strategy_code        VARCHAR(32)     NOT NULL,
    slice_params               jsonb           NULL,

    window_spec                jsonb           NOT NULL,
    window_from_ts             timestamptz(6)  NULL,
    window_to_ts               timestamptz(6)  NULL,

    status_code                VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',

    -- 审计字段
    record_remarks             jsonb           NULL,
    version                    BIGINT          NOT NULL DEFAULT 0,
    ip_address                 bytea           NULL,
    created_at                 timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by                 BIGINT          NULL,
    created_by_name            VARCHAR(100)    NULL,
    updated_at                 timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by                 BIGINT          NULL,
    updated_by_name            VARCHAR(100)    NULL,
    deleted_at                 timestamptz(6)  NULL DEFAULT NULL,

    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_plan_key ON ing_plan (plan_key);
CREATE INDEX idx_plan_sched ON ing_plan (schedule_instance_id);
CREATE INDEX idx_plan_prov_op ON ing_plan (provenance_code, operation_code);
CREATE INDEX idx_plan_status ON ing_plan (status_code);
CREATE INDEX idx_plan_expr ON ing_plan (expr_proto_hash);
CREATE INDEX idx_plan_prov_config_hash ON ing_plan (provenance_config_hash);
CREATE INDEX idx_window_time_range ON ing_plan (window_from_ts, window_to_ts);
CREATE INDEX idx_plan_deleted_at ON ing_plan (deleted_at);
CREATE INDEX idx_plan_audit_created_by ON ing_plan (created_by);
CREATE INDEX idx_plan_audit_updated_by ON ing_plan (updated_by);

CREATE TRIGGER trg_ing_plan_updated_at
    BEFORE UPDATE ON ing_plan
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
