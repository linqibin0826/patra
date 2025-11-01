-- B. Ingest Module —— ing_*
-- - Unified audit fields: record_remarks / created_at/created_by/created_by_name / updated_at/updated_by/updated_by_name / version / ip_address / deleted.
-- - No physical foreign keys (integrity guaranteed at application layer across/within modules, secondary indexes created where necessary).
-- - MySQL 8.0 · InnoDB · utf8mb4_0900_ai_ci
-- ======================================================================
-- 1) Schedule Instance: An external trigger event
-- ======================================================================
/* ====================================================================
 * Table: ing_schedule_instance —— Schedule Instance
 * Semantics: Records the "root" of an external schedule trigger event, persisting the trigger parameters at that time.
 * Key Points:
 *  - provenance_code aligns with reg_provenance.provenance_code (logical association, no FK);
 *  - Config snapshots and expression prototypes are saved at ing_plan level.
 * Indexes: idx_sched_src(scheduler_code, scheduler_job_id, scheduler_log_id).
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_schedule_instance`
(
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · Schedule Instance ID',
    `scheduler_code`    VARCHAR(32)     NOT NULL DEFAULT 'XXL' COMMENT 'DICT CODE(type=ing_scheduler): Scheduler source',
    `scheduler_job_id`  VARCHAR(64)     NULL COMMENT 'External JobID (e.g. XXL jobId)',
    `scheduler_log_id`  VARCHAR(64)     NULL COMMENT 'External run/log ID (e.g. XXL logId)',
    `trigger_type_code` VARCHAR(32)     NOT NULL DEFAULT 'SCHEDULE' COMMENT 'DICT CODE(type=ing_trigger_type): Trigger type',
    `triggered_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Trigger time (UTC)',
    `trigger_params`    JSON            NULL COMMENT 'Trigger parameters (normalized)',
    `provenance_code`   VARCHAR(64)     NOT NULL COMMENT 'Provenance code: Aligns with reg_provenance.provenance_code, e.g. pubmed/epmc/crossref',

    -- Audit fields
    `record_remarks`    JSON            NULL COMMENT 'JSON array, remarks/change log [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`           BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version number',
    `ip_address`        VARBINARY(16)   NULL COMMENT 'Requester IP (binary, supports IPv4/IPv6)',
    `created_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time (UTC)',
    `created_by`        BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`   VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Update time (UTC)',
    `updated_by`        BIGINT UNSIGNED NULL COMMENT 'Updater ID',
    `updated_by_name`   VARCHAR(100)    NULL COMMENT 'Updater name',
    `deleted`           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),
    KEY `idx_sched_src` (`scheduler_code`, `scheduler_job_id`, `scheduler_log_id`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Schedule Instance: An external trigger event (serves as root for this orchestration); no physical foreign keys';


-- ======================================================================
-- 2) Plan Blueprint: Defines overall window and slicing strategy (expression prototype, no localized conditions)
-- ======================================================================
/* ====================================================================
 * Table: ing_plan —— Plan Blueprint
 * Semantics: Blueprint for a collection batch, defining overall window and slicing strategy; not executed.
 * Key Points:
 *  - Logically associated with ing_schedule_instance (no FK);
 *  - Uses *_code (dict) to store operations/strategies/statuses;
 *  - plan_key serves as external idempotency/readable key (unique).
 * Indexes: uk_plan_key / idx_plan_sched / idx_plan_status / idx_plan_expr.
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_plan`
(
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · PlanID',
    `schedule_instance_id`       BIGINT UNSIGNED NOT NULL COMMENT 'Associated schedule instance',
    `plan_key`                   VARCHAR(128)    NOT NULL COMMENT 'Human-readable/external idempotency key (unique)',

    `provenance_code`            VARCHAR(64)     NULL COMMENT 'Redundant: Provenance code, aligns with reg_provenance.provenance_code (facilitates aggregation by source)',
    `operation_code`             VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation): Collection type HARVEST/BACKFILL/UPDATE/METRICS',

    `expr_proto_hash`            CHAR(64)        NOT NULL COMMENT 'Expression prototype hash: Fingerprint calculated from "normalized prototype AST"; used for idempotency/fast comparison; one-to-one with expr_proto_snapshot',
    `expr_proto_snapshot`        JSON            NULL COMMENT 'Expression prototype snapshot (AST, JSON): "Global expression tree" without any slice/localized conditions; used for replay and audit (derives multiple slices from this prototype)',
    `provenance_config_snapshot` JSON            NULL COMMENT 'Provenance config snapshot (neutral model, JSON): Compiles reg_prov_* auth/pagination/time window/rate limiting/retry/batch processing configs into execution-immutable snapshot',
    `provenance_config_hash`     CHAR(64)        NULL COMMENT 'Provenance config snapshot hash: Fingerprint calculated from normalized provenance_config_snapshot; used for reuse determination and change detection',
    `slice_strategy_code`        VARCHAR(32)     NOT NULL COMMENT 'Slicing strategy: TIME/DATE/ID_RANGE/CURSOR_LANDMARK/VOLUME_BUDGET/HYBRID/SINGLE etc; determines how to generate multiple slices from prototype',
    `slice_params`               JSON            NULL COMMENT 'Slicing parameters: Details matching the slicing strategy (e.g. step size, time zone, landmark, budget limit etc); only used to generate slices, not directly in execution',

    `window_spec`                JSON            NOT NULL COMMENT 'Window boundary spec (nested JSON). TIME/DATE: {"strategy":"TIME|DATE","window":{"from":"2024-01-01T00:00:00Z","to":"2024-12-31T23:59:59Z"}}; ID_RANGE: {"strategy":"ID_RANGE","window":{"from":1000000,"to":2000000}}; CURSOR_LANDMARK: {"strategy":"CURSOR_LANDMARK","window":{"from":"token1","to":"token2"}}; VOLUME_BUDGET: {"strategy":"VOLUME_BUDGET","limit":100000,"unit":"RECORDS"}; SINGLE: {"strategy":"SINGLE"}',
    -- Denormalized fields for TIME/DATE strategy query optimization (application-maintained)
    `window_from_ts`             TIMESTAMP(6)    NULL COMMENT 'Denormalized: TIME/DATE strategy window start (inclusive, UTC). Populated by application layer when slice_strategy_code=TIME or DATE for efficient time-range queries. NULL for non-time-based strategies.',
    `window_to_ts`               TIMESTAMP(6)    NULL COMMENT 'Denormalized: TIME/DATE strategy window end (exclusive, UTC). Populated by application layer when slice_strategy_code=TIME or DATE for efficient time-range queries. NULL for non-time-based strategies.',

    `status_code`                VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DICT CODE(type=ing_plan_status): DRAFT/SLICING/READY/ARCHIVED',

    -- Audit fields
    `record_remarks`             JSON            NULL COMMENT 'JSON array, remarks/change log [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`                    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version number',
    `ip_address`                 VARBINARY(16)   NULL COMMENT 'Requester IP (binary, supports IPv4/IPv6)',
    `created_at`                 TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time (UTC)',
    `created_by`                 BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`            VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`                 TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Update time (UTC)',
    `updated_by`                 BIGINT UNSIGNED NULL COMMENT 'Updater ID',
    `updated_by_name`            VARCHAR(100)    NULL COMMENT 'Updater name',
    `deleted`                    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_key` (`plan_key`),
    KEY `idx_plan_sched` (`schedule_instance_id`),
    KEY `idx_plan_prov_op` (`provenance_code`, `operation_code`),
    KEY `idx_plan_status` (`status_code`),
    KEY `idx_plan_expr` (`expr_proto_hash`),
    KEY `idx_plan_prov_config_hash` (`provenance_config_hash`),
    KEY `idx_window_time_range` (`window_from_ts`, `window_to_ts`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Plan Blueprint: Defines overall window and slicing strategy (expression prototype, no localized conditions); includes provenance config hash; no physical foreign keys';

-- ======================================================================
-- 3) Plan Slice: Generic sharding (time/ID/token/budget), parallelism and idempotency boundary
-- ======================================================================
/* ====================================================================
 * Table: ing_plan_slice —— Plan Slice
 * Semantics: Slices cut from plan's overall window and strategy; minimum unit for parallelism and idempotency; expr_* is localized expression (with boundaries).
 * Key Points: Within same plan, both (slice_no) and (slice_signature_hash) are unique; no FK.
 * Indexes: uk_slice_unique / uk_slice_sig / idx_slice_status / idx_slice_expr.
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_plan_slice`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · SliceID',
    `plan_id`              BIGINT UNSIGNED NOT NULL COMMENT 'Associated Plan',

    `provenance_code`      VARCHAR(64)     NULL COMMENT 'Redundant: Provenance code, aligns with reg_provenance.provenance_code (accelerates filtering by source)',

    `slice_no`             INT             NOT NULL COMMENT 'Slice sequence number (0..N)',
    `slice_signature_hash` CHAR(64)        NOT NULL COMMENT 'Slice signature hash: computed only after normalization of window_spec (boundary JSON); used for weighting/de-weighting (same boundary not generated repeatedly under same plan)',
    `window_spec`          JSON            NOT NULL COMMENT 'Window boundary spec for this slice (narrowed from plan.window_spec). TIME/DATE: {"strategy":"TIME|DATE","window":{"from":"...","to":"..."}}; ID_RANGE: {"strategy":"ID_RANGE","window":{"from":N,"to":M}}; others similar to plan table',
    `expr_hash`            CHAR(64)        NOT NULL COMMENT 'Localized expression hash: Fingerprint calculated from "normalized localized AST"; typically changes together with slice_signature_hash',

    `expr_snapshot`        JSON            NULL COMMENT 'Localized expression snapshot (AST, JSON): "Directly executable expression tree" after injecting this slice boundary conditions into plan prototype; slice carries replay semantics',
    `status_code`          VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT 'DICT CODE(type=ing_slice_status): PENDING/ASSIGNED/FINISHED',

    -- Audit fields
    `record_remarks`       JSON            NULL COMMENT 'JSON array, remarks/change log [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version number',
    `ip_address`           VARBINARY(16)   NULL COMMENT 'Requester IP (binary, supports IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time (UTC)',
    `created_by`           BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`      VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Update time (UTC)',
    `updated_by`           BIGINT UNSIGNED NULL COMMENT 'Updater ID',
    `updated_by_name`      VARCHAR(100)    NULL COMMENT 'Updater name',
    `deleted`              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slice_unique` (`plan_id`, `slice_no`),
    UNIQUE KEY `uk_slice_sig` (`plan_id`, `slice_signature_hash`),
    KEY `idx_slice_prov_status` (`provenance_code`, `status_code`),
    KEY `idx_slice_status` (`status_code`),
    KEY `idx_slice_expr` (`expr_hash`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Plan Slice: Generic sharding (time/ID/token/budget), is the boundary for parallelism and idempotency; no physical foreign keys';


-- ======================================================================
-- 4) Task: Each slice generates ONE task (1:1 relationship); supports strong idempotency and scheduling/execution status
-- ======================================================================
/* ====================================================================
 * Table: ing_task —— Derived Task
 * Semantics: Each slice derives EXACTLY ONE schedulable task (1:1 relationship enforced by uk_task_slice); binds source, operation, credentials and execution parameters.
 * Key Points:
 *  - Logically associated with schedule_instance/plan/slice (no FK);
 *  - Idempotent key idempotent_key is unique, ensures "same slice+operation+params+trigger context" creates only one task;
 *  - 1:1 Slice-Task relationship enforced by UNIQUE KEY uk_task_slice on slice_id.
 * Indexes: uk_task_idem / uk_task_slice / idx_task_slice_status / idx_task_src_op / idx_task_sched_at / idx_task_queue.
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_task`
(
    `id`                   BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT 'PK · TaskID',

    `schedule_instance_id` BIGINT UNSIGNED  NOT NULL COMMENT 'Redundant schedule instance, facilitates aggregation',
    `plan_id`              BIGINT UNSIGNED  NOT NULL,
    `slice_id`             BIGINT UNSIGNED  NOT NULL,

    `provenance_code`      VARCHAR(64)      NOT NULL COMMENT 'Provenance code: Aligns with reg_provenance.provenance_code',
    `operation_code`       VARCHAR(32)      NOT NULL COMMENT 'DICT CODE(type=ing_operation): Operation type HARVEST/BACKFILL/UPDATE/METRICS',

    `params`               JSON             NULL COMMENT 'Task parameters (normalized)',
    `idempotent_key`       CHAR(64)         NOT NULL COMMENT 'SHA256(slice_signature + expr_hash + operation + trigger + normalized(params))',
    `expr_hash`            CHAR(64)         NOT NULL COMMENT 'Redundant: Execution expression hash',

    `priority`             TINYINT UNSIGNED NOT NULL DEFAULT 5 COMMENT '1=high→9=low',

    -- Task lease (renewal/reclaim)
    `lease_owner`          VARCHAR(128)     NULL COMMENT 'Lease holder during execution (instance#thread)',
    `leased_until`         TIMESTAMP(6)     NULL COMMENT 'Lease expiration time (UTC), expired considered re-claimable',
    `lease_count`          INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT 'Cumulative claim/renewal count (for monitoring/circuit breaker)',
    `last_heartbeat_at`    TIMESTAMP(6)     NULL COMMENT 'Execution heartbeat time',
    `retry_count`          INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT 'Retry count',
    `last_error_code`      VARCHAR(64)      NULL COMMENT 'Latest error code',
    `last_error_msg`       VARCHAR(512)     NULL COMMENT 'Latest error message',

    `status_code`          VARCHAR(32)      NOT NULL DEFAULT 'PENDING' COMMENT 'DICT CODE(type=ing_task_status): PENDING/QUEUED/RUNNING/SUCCEEDED/FAILED',
    `scheduled_at`         TIMESTAMP(6)     NULL COMMENT 'Planned start time',
    `started_at`           TIMESTAMP(6)     NULL COMMENT 'Actual start time',
    `finished_at`          TIMESTAMP(6)     NULL COMMENT 'Finish time',

    `correlation_id`       VARCHAR(64)      NULL COMMENT 'Trace/CID',

    -- Audit fields
    `record_remarks`       JSON             NULL COMMENT 'JSON array, remarks/change log [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`              BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version number',
    `ip_address`           VARBINARY(16)    NULL COMMENT 'Requester IP (binary, supports IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time (UTC)',
    `created_by`           BIGINT UNSIGNED  NULL COMMENT 'Creator ID',
    `created_by_name`      VARCHAR(100)     NULL COMMENT 'Creator name',
    `updated_at`           TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Update time (UTC)',
    `updated_by`           BIGINT UNSIGNED  NULL COMMENT 'Updater ID',
    `updated_by_name`      VARCHAR(100)     NULL COMMENT 'Updater name',
    `deleted`              TINYINT(1)       NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),

    -- Idempotency and common retrieval indexes
    UNIQUE KEY `uk_task_idem` (`idempotent_key`),
    UNIQUE KEY `uk_task_slice` (`slice_id`),
    KEY `idx_task_slice_status` (`slice_id`, `status_code`),
    KEY `idx_task_src_op` (`provenance_code`, `operation_code`, `status_code`),
    KEY `idx_task_sched_at` (`status_code`, `scheduled_at`),

    -- Queue retrieval index: For batch pulling pending tasks
    -- Suggested query: WHERE status_code='QUEUED' AND (leased_until IS NULL OR leased_until < NOW(6))
    -- Fair dequeue: priority ASC (smaller value first) → scheduled_at ASC → id ASC
    KEY `idx_task_queue` (`status_code`, `leased_until`, `priority`, `scheduled_at`, `id`),

    -- Audit auxiliary indexes
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Task: Each slice generates EXACTLY ONE task (1:1 relationship enforced by uk_task_slice); supports strong idempotency and scheduling/execution status; no physical foreign keys';

-- ======================================================================
-- 5) Task Run (attempt): One specific attempt; failed retries/replays each add new record
-- ======================================================================
/* ====================================================================
 * Table: ing_task_run —— Task Run (attempt)
 * Semantics: One specific attempt (first/retry/replay); failure does not overwrite task, only recorded in run.
 * Key Points: Within same task, (attempt_no) is unique; no FK.
 * Indexes: uk_run_attempt / idx_run_task_status.
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_task_run`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · RunID',
    `task_id`          BIGINT UNSIGNED NOT NULL COMMENT 'Associated task',
    `attempt_no`       INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT 'Attempt sequence number (starts from 1)',

    `provenance_code`  VARCHAR(64)     NULL COMMENT 'Redundant: Provenance code, aligns with reg_provenance.provenance_code (for tracing and aggregation)',
    `operation_code`   VARCHAR(32)     NULL COMMENT 'Redundant: Operation type (already in task), facilitates direct (source,op,status) statistics',

    `status_code`      VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT 'DICT CODE(type=ing_task_run_status): PENDING/RUNNING/PARTIAL/SUCCEEDED/FAILED',
    `checkpoint`       JSON            NULL COMMENT 'Run-level checkpoint (e.g. nextHint / resumeToken etc)',
    `stats`            JSON            NULL COMMENT 'Statistics: fetched/upserted/failed/pages etc',
    `error`            TEXT            NULL COMMENT 'Failure reason',

    `started_at`       TIMESTAMP(6)    NULL,
    `finished_at`      TIMESTAMP(6)    NULL,
    `last_heartbeat`   TIMESTAMP(6)    NULL,

    `correlation_id`   VARCHAR(64)     NULL,

    -- Audit fields
    `record_remarks`   JSON            NULL COMMENT 'JSON array, remarks/change log [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version number',
    `ip_address`       VARBINARY(16)   NULL COMMENT 'Requester IP (binary, supports IPv4/IPv6)',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time (UTC)',
    `created_by`       BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`  VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Update time (UTC)',
    `updated_by`       BIGINT UNSIGNED NULL COMMENT 'Updater ID',
    `updated_by_name`  VARCHAR(100)    NULL COMMENT 'Updater name',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_run_attempt` (`task_id`, `attempt_no`),
    KEY `idx_run_prov_op_status` (`provenance_code`, `operation_code`, `status_code`),
    KEY `idx_run_task_status` (`task_id`, `status_code`, `started_at`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Task Run (attempt): One specific attempt; failed retries/replays each add new record; no physical foreign keys';

-- ======================================================================
-- 6) Run Batch: Minimum accounting for page/token stepping; carries checkpoint resume and deduplication
-- ======================================================================
/* ====================================================================
 * Table: ing_task_run_batch —— Run Batch
 * Semantics: Pagination/token stepping accounting during run execution, minimum granularity for checkpoint resume and deduplication.
 * Key Points:
 *  - Only logically associated with run/task/slice/plan (no FK);
 *  - Idempotent key idempotent_key is non-null and unique; both (run_id,batch_no) and (run_id,before_token) are unique.
 * Indexes: uk_run_batch_no / uk_run_before_tok / uk_batch_idem and status-time indexes.
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_task_run_batch`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · BatchID',
    `run_id`          BIGINT UNSIGNED NOT NULL COMMENT 'Associated Run',

    `task_id`         BIGINT UNSIGNED NULL COMMENT 'Redundant · Task',
    `slice_id`        BIGINT UNSIGNED NULL COMMENT 'Redundant · Slice',
    `plan_id`         BIGINT UNSIGNED NULL COMMENT 'Redundant · Plan',
    `expr_hash`       CHAR(64)        NULL COMMENT 'Redundant · Execution expression hash',

    `provenance_code` VARCHAR(64)     NULL COMMENT 'Redundant: Provenance code (from task/run chain)',
    `operation_code`  VARCHAR(32)     NULL COMMENT 'Redundant: Operation type (from task)',

    `batch_no`        INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT 'Batch sequence number (starts from 1, consecutive)',
    `page_no`         INT UNSIGNED    NULL COMMENT 'Page number (offset/limit; null for token pagination)',
    `page_size`       INT UNSIGNED    NULL COMMENT 'Page size',

    `before_token`    VARCHAR(512)    NULL COMMENT 'Batch start token/position (retstart/cursorMark etc)',
    `after_token`     VARCHAR(512)    NULL COMMENT 'Batch end token/next position',

    `idempotent_key`  CHAR(64)        NOT NULL COMMENT 'SHA256(run_id + before_token | page_no)',
    `record_count`    INT UNSIGNED    NOT NULL DEFAULT 0,
    `status_code`     VARCHAR(32)     NOT NULL DEFAULT 'RUNNING' COMMENT 'DICT CODE(type=ing_batch_status): RUNNING/SUCCEEDED/FAILED/SKIPPED',
    `committed_at`    TIMESTAMP(6)    NULL,
    `error`           TEXT            NULL,
    `storage_key`     VARCHAR(512)    NULL COMMENT 'Object storage reference for batch payload',
    `stats`           JSON            NULL,

    -- Audit fields
    `record_remarks`  JSON            NULL COMMENT 'JSON array, remarks/change log [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version number',
    `ip_address`      VARBINARY(16)   NULL COMMENT 'Requester IP (binary, supports IPv4/IPv6)',
    `created_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time (UTC)',
    `created_by`      BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name` VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Update time (UTC)',
    `updated_by`      BIGINT UNSIGNED NULL COMMENT 'Updater ID',
    `updated_by_name` VARCHAR(100)    NULL COMMENT 'Updater name',
    `deleted`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_run_batch_no` (`run_id`, `batch_no`),
    UNIQUE KEY `uk_run_before_tok` (`run_id`, `before_token`),
    UNIQUE KEY `uk_batch_idem` (`idempotent_key`),

    KEY `idx_batch_after_tok` (`run_id`, `after_token`),
    KEY `idx_batch_status_time` (`run_id`, `status_code`, `committed_at`),
    KEY `idx_batch_prov_op_status` (`provenance_code`, `operation_code`, `status_code`, `committed_at`),
    KEY `idx_batch_task` (`task_id`, `status_code`, `committed_at`),
    KEY `idx_batch_slice` (`slice_id`, `status_code`, `committed_at`),
    KEY `idx_batch_plan` (`plan_id`, `status_code`, `committed_at`),
    KEY `idx_batch_expr` (`expr_hash`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Run Batch: Minimum accounting for page/token stepping; carries checkpoint resume and deduplication; no physical foreign keys';

-- ======================================================================
-- 7) Generic Cursor (current value): (source_code + operation + cursor_key + namespace) unique
-- ======================================================================
/* ====================================================================
 * Table: ing_cursor —— Current Cursor
 * Semantics: Current advancement value for a source + operation + cursor key + namespace; compatible with TIME/ID/TOKEN types.
 * Key Points:
 *  - Unique: (provenance_code, operation_code, cursor_key, namespace_scope_code, namespace_key);
 *  - Normalized values: normalized_instant / normalized_numeric facilitate sorting and range queries;
 *  - Lineage: Most recent advancement's schedule/plan/slice/task/run/batch (logical association, no FK).
 * Indexes: uk_cursor_ns / idx_cursor_src_key / idx_cursor_sort_time / idx_cursor_sort_id / idx_cursor_lineage.
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_cursor`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',

    `provenance_code`      VARCHAR(64)     NOT NULL COMMENT 'Provenance code: Aligns with reg_provenance.provenance_code',
    `operation_code`       VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation): HARVEST/BACKFILL/UPDATE/METRICS',
    `cursor_key`           VARCHAR(64)     NOT NULL COMMENT 'Cursor key: updated_at/published_at/seq_id/cursor_token etc',
    `namespace_scope_code` VARCHAR(32)     NOT NULL DEFAULT 'GLOBAL' COMMENT 'DICT CODE(type=ing_namespace_scope): GLOBAL/EXPR/CUSTOM',
    `namespace_key`        CHAR(64)        NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000'
        COMMENT 'Namespace key: expr_hash or custom hash; global=all zeros',

    `cursor_type_code`     VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_cursor_type): TIME/ID/TOKEN',
    `cursor_value`         VARCHAR(1024)   NOT NULL COMMENT 'Current effective cursor value (UTC ISO-8601 / decimal string / opaque string)',
    `observed_max_value`   VARCHAR(1024)   NULL COMMENT 'Observed maximum boundary',

    `normalized_instant`   TIMESTAMP(6)    NULL COMMENT 'Populated when cursor_type=time (UTC)',
    `normalized_numeric`   DECIMAL(38, 0)  NULL COMMENT 'Populated when cursor_type=id',

    `schedule_instance_id` BIGINT UNSIGNED NULL COMMENT 'Most recent advancement schedule instance',
    `plan_id`              BIGINT UNSIGNED NULL COMMENT 'Most recent advancement associated Plan',
    `slice_id`             BIGINT UNSIGNED NULL COMMENT 'Most recent advancement associated Slice',
    `task_id`              BIGINT UNSIGNED NULL COMMENT 'Most recent advancement associated Task',
    `last_run_id`          BIGINT UNSIGNED NULL COMMENT 'Most recent advancement Run',
    `last_batch_id`        BIGINT UNSIGNED NULL COMMENT 'Most recent advancement Batch',
    `expr_hash`            CHAR(64)        NULL COMMENT 'Expression hash used in most recent advancement',

    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version number (only forward advancement allowed)',

    -- Audit fields (uniformly applied to this "current value" table as well)
    `record_remarks`       JSON            NULL COMMENT 'JSON array, remarks/change log [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `ip_address`           VARBINARY(16)   NULL COMMENT 'Requester IP (binary, supports IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time (UTC)',
    `created_by`           BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`      VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Update time (UTC)',
    `updated_by`           BIGINT UNSIGNED NULL COMMENT 'Updater ID',
    `updated_by_name`      VARCHAR(100)    NULL COMMENT 'Updater name',
    `deleted`              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_cursor_ns` (`provenance_code`, `operation_code`, `cursor_key`, `namespace_scope_code`,
                               `namespace_key`),
    KEY `idx_cursor_src_key` (`provenance_code`, `operation_code`, `cursor_key`),
    KEY `idx_cursor_sort_time` (`cursor_type_code`, `normalized_instant`),
    KEY `idx_cursor_sort_id` (`cursor_type_code`, `normalized_numeric`),
    KEY `idx_cursor_lineage` (`schedule_instance_id`, `plan_id`, `slice_id`, `task_id`, `last_run_id`, `last_batch_id`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Generic Cursor·Current Value: (source_code + operation + cursor_key + namespace) unique; compatible with time/id/token; no physical foreign keys';

-- ======================================================================
-- 8) Cursor Advancement Event (immutable): One event record per successful advancement; supports replay and full-chain tracing
-- ======================================================================
/* ====================================================================
 * Table: ing_cursor_event —— Cursor Advancement Event
 * Semantics: Append-only audit event; one record per successful advancement, supports replay and full-chain tracing.
 * Key Points:
 *  - Idempotent: idempotent_key is unique;
 *  - Lineage: schedule/plan/slice/task/run/batch logically associated (no FK).
 * Indexes: uk_cur_evt_idem / idx_cur_evt_timeline / idx_cur_evt_window / idx_cur_evt_instant / idx_cur_evt_numeric / idx_cur_evt_lineage.
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_cursor_event`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',

    `provenance_code`      VARCHAR(64)     NOT NULL,
    `operation_code`       VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation): HARVEST/BACKFILL/UPDATE/METRICS',
    `cursor_key`           VARCHAR(64)     NOT NULL,
    `namespace_scope_code` VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_namespace_scope): GLOBAL/EXPR/CUSTOM',
    `namespace_key`        CHAR(64)        NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000'
        COMMENT 'Namespace key: expr_hash or custom hash; global=all zeros',

    `cursor_type_code`     VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_cursor_type): TIME/ID/TOKEN',
    `prev_value`           VARCHAR(1024)   NULL,
    `new_value`            VARCHAR(1024)   NOT NULL,
    `observed_max_value`   VARCHAR(1024)   NULL,

    `prev_instant`         TIMESTAMP(6)    NULL,
    `new_instant`          TIMESTAMP(6)    NULL,
    `prev_numeric`         DECIMAL(38, 0)  NULL,
    `new_numeric`          DECIMAL(38, 0)  NULL,
    `window_from`          TIMESTAMP(6)    NULL COMMENT 'Covered window start (UTC, inclusive)',
    `window_to`            TIMESTAMP(6)    NULL COMMENT 'Covered window end (UTC, exclusive)',

    `direction_code`       VARCHAR(16)     NULL COMMENT 'DICT CODE(type=ing_cursor_direction): FORWARD/BACKFILL',

    `idempotent_key`       CHAR(64)        NOT NULL COMMENT 'Event idempotency key: SHA256(source,op,key,ns_scope,ns_key,prev->new,ingestWindow,run_id,...)',

    `schedule_instance_id` BIGINT UNSIGNED NULL,
    `plan_id`              BIGINT UNSIGNED NULL,
    `slice_id`             BIGINT UNSIGNED NULL,
    `task_id`              BIGINT UNSIGNED NULL,
    `run_id`               BIGINT UNSIGNED NULL,
    `batch_id`             BIGINT UNSIGNED NULL,
    `expr_hash`            CHAR(64)        NULL,

    -- Audit fields (event tables typically immutable; retain fields below if unified governance needed)
    `record_remarks`       JSON            NULL COMMENT 'JSON array, remarks/change log [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version number',
    `ip_address`           VARBINARY(16)   NULL COMMENT 'Requester IP (binary, supports IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time (UTC)',
    `created_by`           BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`      VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Update time (UTC)',
    `updated_by`           BIGINT UNSIGNED NULL COMMENT 'Updater ID',
    `updated_by_name`      VARCHAR(100)    NULL COMMENT 'Updater name',
    `deleted`              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_cur_evt_idem` (`idempotent_key`),

    KEY `idx_cur_evt_timeline` (`provenance_code`, `operation_code`, `cursor_key`, `namespace_scope_code`,
                                `namespace_key`),
    KEY `idx_cur_evt_instant` (`cursor_type_code`, `new_instant`),
    KEY `idx_cur_evt_numeric` (`cursor_type_code`, `new_numeric`),
    KEY `idx_cur_evt_lineage` (`schedule_instance_id`, `plan_id`, `slice_id`, `task_id`, `run_id`, `batch_id`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Cursor Advancement Event (immutable): One event record per successful advancement; supports replay and full-chain tracing; no physical foreign keys';

-- ======================================================================
-- Table: ing_outbox_message —— Generic Outbox (task dispatch/integration events etc)
-- Semantics: Persisted in same transaction as business write; scanned and delivered to MQ (e.g. RocketMQ) by Relay.
-- Design Points:
--  - (channel, dedup_key) unique, ensures source-side idempotency;
--  - Only scan this table for publishing, not business hot tables;
--  - partition_key recommended to use "provenance:operation" (extensible as needed, but not as independent field).
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_outbox_message`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · OutboxID',
    `aggregate_type`   VARCHAR(32)     NOT NULL COMMENT 'Aggregate type: e.g. TASK/PLAN/...; for audit and replay location',
    `aggregate_id`     BIGINT UNSIGNED NOT NULL COMMENT 'Aggregate root ID; for task scenario=ing_task.id',
    `channel`          VARCHAR(64)     NOT NULL COMMENT 'Logical channel=target Topic, e.g. ingest.task',
    `op_type`          VARCHAR(32)     NOT NULL COMMENT 'Business semantic label: e.g. TASK_READY / EVENT_PUBLISHED',
    `partition_key`    VARCHAR(128)    NOT NULL COMMENT 'Partitioning/ordering routing key; recommend "provenance:operation"; for ordered delivery or partition rate limiting e.g. PUBMED:HARVEST',
    `dedup_key`        VARCHAR(128)    NOT NULL COMMENT 'Idempotency key; for task=ing_task.idempotent_key; (channel, dedup_key) unique',
    `payload_json`     JSON            NOT NULL COMMENT 'Minimal necessary payload (JSON): taskId/sliceKey/planKey/provenance/operation/endpoint/priority/notBefore etc; large fields not enqueued',
    `headers_json`     JSON            NULL COMMENT 'Extension headers (JSON): correlationId etc',
    `not_before`       TIMESTAMP(6)    NULL COMMENT 'Earliest publishable time (UTC): NULL=publishable anytime; for scheduled/delayed publishing',
    `published_at`     TIMESTAMP(6)    NULL COMMENT 'Successful publish timestamp (UTC), set when status transitions to PUBLISHED',

    `status_code`      VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT 'Publishing status: PENDING/PUBLISHING/PUBLISHED/FAILED/DEAD',
    `retry_count`      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT 'Publishing retry count (incremented on failure)',
    `next_retry_at`    TIMESTAMP(6)    NULL COMMENT 'Next publishing attempt time (UTC), used with backoff curve',
    `error_code`       VARCHAR(64)     NULL COMMENT 'Latest publishing error code',
    `error_msg`        VARCHAR(512)    NULL COMMENT 'Latest publishing error details',

    `pub_lease_owner`  VARCHAR(128)    NULL COMMENT 'Publisher lease holder (instance ID or workerId), prevents concurrent publishing on same row',
    `pub_leased_until` TIMESTAMP(6)    NULL COMMENT 'Publisher lease expiration (UTC), expired can be taken over by other publishers',

    -- Audit fields
    `record_remarks`   JSON            NULL COMMENT 'JSON array, remarks/change log [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version number',
    `ip_address`       VARBINARY(16)   NULL COMMENT 'Requester IP (binary, supports IPv4/IPv6)',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time (UTC)',
    `created_by`       BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`  VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Update time (UTC)',
    `updated_by`       BIGINT UNSIGNED NULL COMMENT 'Updater ID',
    `updated_by_name`  VARCHAR(100)    NULL COMMENT 'Updater name',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),

    -- Source-side deduplication: dedup_key must be unique within same channel
    UNIQUE KEY `uk_outbox_channel_dedup` (`channel`, `dedup_key`),
    -- Lightweight scan index: Batch publishing by status+time cursor
    KEY `idx_outbox_status_time` (`status_code`, `not_before`, `id`),
    -- Ordered/partitioned publishing and replay (controls concurrency or ordering by channel + partition_key)
    KEY `idx_outbox_partition` (`channel`, `partition_key`, `status_code`),
    -- Publisher lease reclaim (for reclaiming expired leases when multiple Relays run in parallel)
    KEY `idx_outbox_lease` (`status_code`, `pub_leased_until`),
    -- Optimized indexes for UNION ALL query pattern (V2.0 enhancement)
    KEY `idx_pending_relay` (`channel`, `status_code`, `not_before`, `id`) COMMENT 'Optimized for PENDING message relay query (UNION ALL first subquery)',
    KEY `idx_publishing_lease` (`channel`, `status_code`, `pub_leased_until`, `id`) COMMENT 'Optimized for PUBLISHING message with expired lease query (UNION ALL second subquery)',
    -- Archive/reconciliation convenience
    KEY `idx_outbox_created` (`created_at`),
    KEY `idx_outbox_deleted_upd` (`deleted`, `updated_at`)
)
    ENGINE = InnoDB COMMENT ='Outbox: Generic outbound message table (unified management of task dispatch/integration events; same transaction as business write; scanned and delivered to MQ by Relay)';

-- ======================================================================
-- Table: ing_outbox_relay_log —— Outbox Relay Execution Logs
-- Semantics: Immutable audit trail for every relay attempt; tracks publish success/failure, timing, errors, and retry scheduling.
-- Design Points:
--  - One record per relay attempt (multiple attempts for same message if retries occur);
--  - Denormalizes channel, partition_key from ing_outbox_message for query efficiency;
--  - Supports troubleshooting ("show all attempts for this message"), batch-level statistics, and monitoring/alerting.
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_outbox_relay_log`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · Relay log ID',

    -- Core references
    `message_id`       BIGINT UNSIGNED NOT NULL COMMENT 'Reference to ing_outbox_message.id (FK-like, application-enforced)',
    `relay_batch_id`   VARCHAR(50)     NOT NULL COMMENT 'Relay batch identifier (format: yyyyMMddHHmmss-xxxxxxxx), groups logs from same job execution',

    -- Relay context (denormalized for query efficiency)
    `channel`          VARCHAR(64)     NOT NULL COMMENT 'Message channel (denormalized from ing_outbox_message for query efficiency)',
    `partition_key`    VARCHAR(128)    NULL COMMENT 'Partition key (denormalized from ing_outbox_message for analysis)',
    `lease_owner`      VARCHAR(128)    NULL COMMENT 'Lease owner identifier (host-jobId-threadId-uuid format)',
    `attempt_number`   INT UNSIGNED    NOT NULL COMMENT 'Attempt number for this message (1-based, increments on retry)',

    -- Relay result
    `relay_status`     VARCHAR(16)     NOT NULL COMMENT 'Relay execution status: PUBLISHED (success) / DEFERRED (retry) / FAILED (permanent) / LEASE_MISSED (conflict)',

    -- Error details (NULL for success)
    `error_code`       VARCHAR(50)     NULL COMMENT 'Error code if relay failed (e.g., NETWORK_TIMEOUT, BROKER_UNAVAILABLE), NULL for success',
    `error_message`    TEXT            NULL COMMENT 'Error details if relay failed (truncated to 512 chars), NULL for success',
    `error_kind`       VARCHAR(16)     NULL COMMENT 'Error classification: FATAL (non-retryable) or TRANSIENT (retryable), NULL for success',

    -- Timing fields
    `started_at`       TIMESTAMP(6)    NOT NULL COMMENT 'Relay start timestamp (UTC)',
    `completed_at`     TIMESTAMP(6)    NOT NULL COMMENT 'Relay completion timestamp (UTC)',
    `duration_ms`      INT UNSIGNED    NOT NULL COMMENT 'Relay execution duration in milliseconds (completedAt - startedAt)',

    -- Next retry scheduling (only for DEFERRED status)
    `next_retry_at`    TIMESTAMP(6)    NULL COMMENT 'Next retry timestamp (UTC), only present for DEFERRED status',

    -- Standard audit fields
    `record_remarks`   JSON            NULL COMMENT 'JSON array, remarks/change log',
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version number',
    `ip_address`       VARBINARY(16)   NULL COMMENT 'Requester IP (binary, supports IPv4/IPv6)',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time (UTC)',
    `created_by`       BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`  VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Update time (UTC)',
    `updated_by`       BIGINT UNSIGNED NULL COMMENT 'Updater ID',
    `updated_by_name`  VARCHAR(100)    NULL COMMENT 'Updater name',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),

    -- Query by message ID (for troubleshooting: "show all attempts for this message")
    INDEX `idx_message_id` (`message_id`, `started_at` DESC)
        COMMENT 'Query logs by message (newest first)',

    -- Query by batch ID (for batch-level statistics)
    INDEX `idx_batch_id` (`relay_batch_id`)
        COMMENT 'Query logs by relay batch',

    -- Query by channel and time range (for monitoring dashboards)
    INDEX `idx_channel_time` (`channel`, `started_at`)
        COMMENT 'Query logs by channel and time range',

    -- Query by status (for alerting: "show recent failures")
    INDEX `idx_status` (`relay_status`, `started_at`)
        COMMENT 'Query logs by status',

    -- Archive/cleanup convenience
    INDEX `idx_created_at` (`created_at`)
        COMMENT 'Archive old logs by creation time',

    INDEX `idx_deleted_upd` (`deleted`, `updated_at`)
        COMMENT 'Soft delete support'

    -- Note: Foreign key constraint intentionally omitted for performance
    -- Referential integrity enforced at application layer
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Outbox relay execution logs - immutable audit trail for every relay attempt';
