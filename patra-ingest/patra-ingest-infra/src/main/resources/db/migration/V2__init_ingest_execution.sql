-- =====================================================================
-- Patra Ingest — PostgreSQL 17 baseline schema: V2 执行链
-- 来源: V0.1.0__init_ingest_schema.sql 第 3-6 段
-- 包含: ing_plan_slice + ing_task + ing_task_run + ing_task_run_batch（4 表）
-- 特殊: ing_task.idx_task_queue 改写为 partial index（仅索引 PENDING/QUEUED 行）
-- 已移除: AUTO_INCREMENT / ENGINE/CHARSET/COLLATE / ON UPDATE / CREATE TABLE IF NOT EXISTS / 反引号
-- =====================================================================

-- ======================================================================
-- 3) 计划分片: 通用分片 (时间/ID/令牌/预算), 并行和幂等边界
-- ======================================================================
/* ====================================================================
 * 表: ing_plan_slice —— 计划分片
 * 语义: 从计划的总体窗口和策略切割的分片; 并行和幂等的最小单元; expr_* 是本地化表达式 (带边界)。
 * 要点: 在同一计划内, (slice_no) 和 (slice_signature_hash) 均唯一; 无 FK。
 * 索引: uk_slice_unique / uk_slice_sig / idx_slice_status / idx_slice_expr.
 * ==================================================================== */
CREATE TABLE ing_plan_slice
(
    id                   BIGINT          NOT NULL,
    plan_id              BIGINT          NOT NULL,

    provenance_code      VARCHAR(64)     NULL,

    slice_no             INTEGER         NOT NULL,
    slice_signature_hash CHAR(64)        NOT NULL,
    window_spec          jsonb           NOT NULL,
    expr_hash            CHAR(64)        NOT NULL,

    expr_snapshot        jsonb           NULL,
    status_code          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',

    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_slice_unique ON ing_plan_slice (plan_id, slice_no);
CREATE UNIQUE INDEX uk_slice_sig ON ing_plan_slice (plan_id, slice_signature_hash);
CREATE INDEX idx_slice_prov_status ON ing_plan_slice (provenance_code, status_code);
CREATE INDEX idx_slice_status ON ing_plan_slice (status_code);
CREATE INDEX idx_slice_expr ON ing_plan_slice (expr_hash);

-- ======================================================================
-- 4) 任务: 每个分片生成一个任务 (1:1 关系); 支持强幂等性和调度/执行状态
-- ======================================================================
/* ====================================================================
 * 表: ing_task —— 派生任务
 * 语义: 每个分片派生精确一个可调度任务 (1:1 关系通过 uk_task_slice 强制); 绑定来源、操作、凭证和执行参数。
 * 要点:
 *  - 与 schedule_instance/plan/slice 逻辑关联 (无 FK);
 *  - 幂等键 idempotent_key 唯一, 确保"相同分片+操作+参数+触发上下文"仅创建一个任务;
 *  - 1:1 分片-任务关系通过 slice_id 上的唯一键 uk_task_slice 强制。
 * 索引: uk_task_idem / uk_task_slice / idx_task_slice_status / idx_task_src_op / idx_task_sched_at
 *       + partial index idx_task_queue (仅索引 PENDING/QUEUED 行).
 * ==================================================================== */
CREATE TABLE ing_task
(
    id                   BIGINT          NOT NULL,

    schedule_instance_id BIGINT          NOT NULL,
    plan_id              BIGINT          NOT NULL,
    slice_id             BIGINT          NOT NULL,

    provenance_code      VARCHAR(64)     NOT NULL,
    operation_code       VARCHAR(32)     NOT NULL,

    params               jsonb           NULL,
    idempotent_key       CHAR(64)        NOT NULL,
    expr_hash            CHAR(64)        NOT NULL,

    -- TINYINT UNSIGNED → SMALLINT + CHECK >= 0（1=高→9=低）
    priority             SMALLINT        NOT NULL DEFAULT 5 CHECK (priority >= 0),

    -- 任务租约 (续租/回收)
    lease_owner          VARCHAR(128)    NULL,
    leased_until         timestamptz(6)  NULL,
    lease_count          INTEGER         NOT NULL DEFAULT 0 CHECK (lease_count >= 0),
    last_heartbeat_at    timestamptz(6)  NULL,
    retry_count          INTEGER         NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    last_error_code      VARCHAR(64)     NULL,
    last_error_msg       VARCHAR(512)    NULL,

    status_code          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    scheduled_at         timestamptz(6)  NULL,
    started_at           timestamptz(6)  NULL,
    finished_at          timestamptz(6)  NULL,

    correlation_id       VARCHAR(64)     NULL,

    -- 审计字段
    record_remarks       jsonb           NULL,
    version              BIGINT          NOT NULL DEFAULT 0,
    ip_address           bytea           NULL,
    created_at           timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by           BIGINT          NULL,
    created_by_name      VARCHAR(100)    NULL,
    updated_at           timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by           BIGINT          NULL,
    updated_by_name      VARCHAR(100)    NULL,
    deleted_at           timestamptz(6)  NULL DEFAULT NULL,

    PRIMARY KEY (id)
);

-- 幂等性和常用检索索引
CREATE UNIQUE INDEX uk_task_idem ON ing_task (idempotent_key);
CREATE UNIQUE INDEX uk_task_slice ON ing_task (slice_id);
CREATE INDEX idx_task_slice_status ON ing_task (slice_id, status_code);
CREATE INDEX idx_task_src_op ON ing_task (provenance_code, operation_code, status_code);
CREATE INDEX idx_task_sched_at ON ing_task (status_code, scheduled_at);
-- 审计辅助索引
CREATE INDEX idx_task_deleted_at ON ing_task (deleted_at);
CREATE INDEX idx_task_audit_created_by ON ing_task (created_by);
CREATE INDEX idx_task_audit_updated_by ON ing_task (updated_by);

-- 队列检索 partial index: 仅索引待处理/排队中的任务行，体积更小、扫描更快
-- 典型查询: WHERE status_code IN ('PENDING','QUEUED') AND (leased_until IS NULL OR leased_until < NOW())
-- 公平出队: leased_until / priority ASC → scheduled_at ASC → id ASC
CREATE INDEX idx_task_queue ON ing_task (leased_until, priority, scheduled_at, id)
  WHERE status_code IN ('PENDING', 'QUEUED');

CREATE TRIGGER trg_ing_task_updated_at
    BEFORE UPDATE ON ing_task
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ======================================================================
-- 5) 任务运行 (尝试): 一次具体的尝试; 失败的重试/重放各添加新记录
-- ======================================================================
/* ====================================================================
 * 表: ing_task_run —— 任务运行 (尝试)
 * 语义: 一次具体的尝试 (首次/重试/重放); 失败不覆盖任务, 仅记录在运行中。
 * 要点: 在同一任务内, (attempt_no) 唯一; 无 FK。
 * 索引: uk_run_attempt / idx_run_task_status.
 * ==================================================================== */
CREATE TABLE ing_task_run
(
    id               BIGINT          NOT NULL,
    task_id          BIGINT          NOT NULL,
    attempt_no       INTEGER         NOT NULL DEFAULT 1 CHECK (attempt_no >= 0),

    provenance_code  VARCHAR(64)     NULL,
    operation_code   VARCHAR(32)     NULL,

    status_code      VARCHAR(32)     NOT NULL DEFAULT 'PENDING',
    checkpoint       jsonb           NULL,
    stats            jsonb           NULL,
    error            TEXT            NULL,

    started_at       timestamptz(6)  NULL,
    finished_at      timestamptz(6)  NULL,
    last_heartbeat   timestamptz(6)  NULL,

    correlation_id   VARCHAR(64)     NULL,

    -- 审计字段（ChildJpaEntity: 4 个字段）
    created_at       timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version          BIGINT          NOT NULL DEFAULT 0,

    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_run_attempt ON ing_task_run (task_id, attempt_no);
CREATE INDEX idx_run_prov_op_status ON ing_task_run (provenance_code, operation_code, status_code);
CREATE INDEX idx_run_task_status ON ing_task_run (task_id, status_code, started_at);

CREATE TRIGGER trg_ing_task_run_updated_at
    BEFORE UPDATE ON ing_task_run
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ======================================================================
-- 6) 运行批次: 页/令牌步进的最小核算; 携带检查点恢复和去重
-- ======================================================================
/* ====================================================================
 * 表: ing_task_run_batch —— 运行批次
 * 语义: 运行执行期间的分页/令牌步进核算, 检查点恢复和去重的最小粒度。
 * 要点:
 *  - 仅与 run/task/slice/plan 逻辑关联 (无 FK);
 *  - 幂等键 idempotent_key 非空且唯一; (run_id,batch_no) 和 (run_id,before_token) 均唯一。
 * 索引: uk_run_batch_no / uk_run_before_tok / uk_batch_idem 以及状态-时间索引。
 * ==================================================================== */
CREATE TABLE ing_task_run_batch
(
    id              BIGINT          NOT NULL,
    run_id          BIGINT          NOT NULL,

    task_id         BIGINT          NULL,
    slice_id        BIGINT          NULL,
    plan_id         BIGINT          NULL,
    expr_hash       CHAR(64)        NULL,

    provenance_code VARCHAR(64)     NULL,
    operation_code  VARCHAR(32)     NULL,

    batch_no        INTEGER         NOT NULL DEFAULT 1 CHECK (batch_no >= 0),
    page_no         INTEGER         NULL CHECK (page_no >= 0),
    page_size       INTEGER         NULL CHECK (page_size >= 0),

    before_token    VARCHAR(512)    NULL,
    after_token     VARCHAR(512)    NULL,

    idempotent_key  CHAR(64)        NOT NULL,
    record_count    INTEGER         NOT NULL DEFAULT 0 CHECK (record_count >= 0),
    status_code     VARCHAR(32)     NOT NULL DEFAULT 'RUNNING',
    committed_at    timestamptz(6)  NULL,
    error           TEXT            NULL,
    storage_key     VARCHAR(512)    NULL,
    stats           jsonb           NULL,

    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uk_run_batch_no ON ing_task_run_batch (run_id, batch_no);
CREATE UNIQUE INDEX uk_run_before_tok ON ing_task_run_batch (run_id, before_token);
CREATE UNIQUE INDEX uk_batch_idem ON ing_task_run_batch (idempotent_key);

CREATE INDEX idx_batch_after_tok ON ing_task_run_batch (run_id, after_token);
CREATE INDEX idx_batch_status_time ON ing_task_run_batch (run_id, status_code, committed_at);
CREATE INDEX idx_batch_prov_op_status ON ing_task_run_batch (provenance_code, operation_code, status_code, committed_at);
CREATE INDEX idx_batch_task ON ing_task_run_batch (task_id, status_code, committed_at);
CREATE INDEX idx_batch_slice ON ing_task_run_batch (slice_id, status_code, committed_at);
CREATE INDEX idx_batch_plan ON ing_task_run_batch (plan_id, status_code, committed_at);
CREATE INDEX idx_batch_expr ON ing_task_run_batch (expr_hash);
