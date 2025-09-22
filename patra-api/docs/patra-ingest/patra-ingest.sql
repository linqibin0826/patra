-- =====================================================================
-- B. 采集（ingest）—— ing_*
-- - 统一审计字段：record_remarks / created_at/created_by/created_by_name / updated_at/updated_by/updated_by_name / version / ip_address / deleted。
-- - 不创建物理外键（跨/内模块均由应用层保证完整性，必要处建立二级索引）。
-- - MySQL 8.0 · InnoDB · utf8mb4_0900_ai_ci
-- =====================================================================
-- 设计更新(2025-09-21)：
--  1. 根据采集调度/可视化/统计的高频查询需求，统一在核心执行链路表冗余 provenance_code：
--     ing_plan / ing_plan_slice / ing_task 已有或新增 / ing_task_run / ing_task_run_batch。
--     目的：
--       - 减少跨表回溯 schedule_instance -> task -> slice -> plan 时的多跳 join；
--       - 支持按 (provenance_code, operation_code, status_code, 时间) 直接过滤聚合；
--       - 统一与 reg_provenance.provenance_code 的逻辑对齐（仍不建外键）。
--  2. 未引入 provenance_id；遵循“CODE 即主识别”策略，避免额外维度。
--  3. 未预留物理分区字段；后续如需冷热分离，可按 created_at RANGE 或 provenance_code HASH 重建。
--  4. 不改动原有字段与语义；仅追加新列与对应复合索引 (idx_*_prov_src / idx_*_prov_status_time 等)。
--  5. 保持 append-only 语义的事件表 ing_cursor_event 不再额外冗余（其已含 provenance_code）。
-- ======================================================================
-- 1) 调度实例：一次外部触发与其“当时配置/表达式原型”快照
-- ======================================================================
/* ====================================================================
 * 表：ing_schedule_instance —— 调度实例
 * 语义：记录一次外部调度触发事件的“根”，固化当时的调度入参、来源配置快照、表达式原型（未局部化）。
 * 关键点：
 *  - provenance_code 对齐 reg_provenance.provenance_code（逻辑关联，不建 FK）；
 *  - provenance_config_snapshot 为按 reg_prov_* 读侧装配出的中立快照；
 *  - expr_proto_* 仅保存表达式原型，后续派生 plan/slice 时再局部化。
 * 索引：idx_sched_src(scheduler_code, scheduler_job_id, scheduler_log_id)。
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_schedule_instance`
(
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · 调度实例ID',
    `scheduler_code`             VARCHAR(32)     NOT NULL DEFAULT 'XXL' COMMENT 'DICT CODE(type=ing_scheduler)：调度器来源',
    `scheduler_job_id`           VARCHAR(64)     NULL COMMENT '外部 JobID（如 XXL 的 jobId）',
    `scheduler_log_id`           VARCHAR(64)     NULL COMMENT '外部运行/日志ID（如 XXL 的 logId）',
    `trigger_type_code`          VARCHAR(32)     NOT NULL DEFAULT 'SCHEDULE' COMMENT 'DICT CODE(type=ing_trigger_type)：触发类型',
    `triggered_at`               TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '触发时间(UTC)',
    `trigger_params`             JSON            NULL COMMENT '调度入参(规范化)',
    `provenance_code`            VARCHAR(64)     NOT NULL COMMENT '来源代码：与 reg_provenance.provenance_code 一致，如 pubmed/epmc/crossref',
    `provenance_config_snapshot` JSON            NULL COMMENT '来源配置/窗口/限流/重试等快照（中立模型）',
    `expr_proto_hash`            CHAR(64)        NULL COMMENT '表达式原型哈希（规范化 AST）',
    `expr_proto_snapshot`        JSON            NULL COMMENT '表达式原型 AST 快照（不含切片条件)',

    -- 审计字段
    `record_remarks`             JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`                    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                 VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`                 TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`                 BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`            VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`                 TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`                 BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`            VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted`                    TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),
    KEY `idx_sched_src` (`scheduler_code`, `scheduler_job_id`, `scheduler_log_id`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='调度实例：一次外部触发与其快照（作为本次编排的根）；不创建物理外键';

-- ======================================================================
-- 2) 计划蓝图：定义总窗口与切片策略（表达式原型，不含局部化条件）
-- ======================================================================
/* ====================================================================
 * 表：ing_plan —— 计划蓝图
 * 语义：一次采集批次的蓝图，定义总窗口与切片策略；不执行。
 * 关键点：
 *  - 与 ing_schedule_instance 逻辑关联（不建 FK）；
 *  - 使用 *_code（dict）存储操作/策略/状态；
 *  - plan_key 作为对外幂等/可读键（唯一）。
 * 索引：uk_plan_key / idx_plan_sched / idx_plan_status / idx_plan_expr。
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_plan`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · PlanID',
    `schedule_instance_id` BIGINT UNSIGNED NOT NULL COMMENT '关联调度实例',
    `plan_key`             VARCHAR(128)    NOT NULL COMMENT '人类可读/外部幂等键（唯一）',

    `provenance_code`      VARCHAR(64)     NULL COMMENT '冗余：来源代码，与 reg_provenance.provenance_code 一致（便于按来源聚合）',
    `endpoint_name`        VARCHAR(64)     NULL COMMENT '来源端点标识（search/detail/metrics 等），辅助区分多端点策略',

    `operation_code`       VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation)：采集类型 HARVEST/BACKFILL/UPDATE/METRICS',
    `expr_proto_hash`      CHAR(64)        NOT NULL COMMENT '表达式原型哈希',
    `expr_proto_snapshot`  JSON            NULL COMMENT '表达式原型 AST 快照',

    `window_from`          TIMESTAMP(6)    NULL COMMENT '总窗起(含,UTC)',
    `window_to`            TIMESTAMP(6)    NULL COMMENT '总窗止(不含,UTC)',

    `slice_strategy_code`  VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_slice_strategy)：TIME/ID_RANGE/CURSOR_LANDMARK/VOLUME_BUDGET/HYBRID',
    `slice_params`         JSON            NULL COMMENT '切片参数：step/zone/landmarks/budget 等',

    `status_code`          VARCHAR(32)     NOT NULL DEFAULT 'READY' COMMENT 'DICT CODE(type=ing_plan_status)：DRAFT/READY/ACTIVE/COMPLETED/ABORTED',

    -- 审计字段
    `record_remarks`       JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`           VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`           BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`           BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted`              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_key` (`plan_key`),
    KEY `idx_plan_sched` (`schedule_instance_id`),
    KEY `idx_plan_prov_op` (`provenance_code`, `operation_code`),
    KEY `idx_plan_endpoint` (`endpoint_name`),
    KEY `idx_plan_status` (`status_code`),
    KEY `idx_plan_expr` (`expr_proto_hash`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='计划蓝图：定义总窗口与切片策略（表达式原型，不含局部化条件）；不创建物理外键';

-- ======================================================================
-- 3) 计划切片：通用分片（时间/ID/token/预算），并行与幂等边界
-- ======================================================================
/* ====================================================================
 * 表：ing_plan_slice —— 计划切片
 * 语义：根据 plan 的总窗与策略切出的分片；并行与幂等的最小单元；expr_* 为局部化表达式（含边界）。
 * 关键点：同一 plan 下 (slice_no) 与 (slice_signature_hash) 各自唯一；不建 FK。
 * 索引：uk_slice_unique / uk_slice_sig / idx_slice_status / idx_slice_expr。
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_plan_slice`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · SliceID',
    `plan_id`              BIGINT UNSIGNED NOT NULL COMMENT '关联 Plan',

    `provenance_code`      VARCHAR(64)     NULL COMMENT '冗余：来源代码，与 reg_provenance.provenance_code 一致（加速按来源过滤）',

    `slice_no`             INT             NOT NULL COMMENT '切片序号(0..N)',
    `slice_signature_hash` CHAR(64)        NOT NULL COMMENT '切片签名哈希(规范化的通用边界JSON)',
    `slice_spec`           JSON            NOT NULL COMMENT '通用边界说明：时间/ID区间/landmark/预算等',

    `expr_hash`            CHAR(64)        NOT NULL COMMENT '局部化表达式哈希',
    `expr_snapshot`        JSON            NULL COMMENT '局部化表达式 AST 快照（含本Slice边界）',

    `status_code`          VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT 'DICT CODE(type=ing_slice_status)：PENDING/DISPATCHED/EXECUTING/SUCCEEDED/FAILED/PARTIAL/CANCELLED',

    -- 审计字段
    `record_remarks`       JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`           VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`           BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`           BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted`              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

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
    COMMENT ='计划切片：通用分片（时间/ID/token/预算），是并行与幂等的边界；不创建物理外键';
-- ======================================================================
-- 4) 任务：每个切片生成一个任务；支持强幂等与调度/执行状态
-- ======================================================================
/* ====================================================================
 * 表：ing_task —— 派生任务
 * 语义：每个 slice 派生出一个可调度任务；绑定来源、操作、凭据与执行参数。
 * 关键点：
 *  - 逻辑关联 schedule_instance/plan/slice（不建 FK）；
 *  - credential_id 逻辑指向 reg_prov_credential.id（不建 FK，保留二级索引 idx_task_cred）；
 *  - 幂等键 idempotent_key 唯一，保证“同 slice+操作+参数+触发上下文”只建一条任务。
 * 索引：uk_task_idem / idx_task_slice / idx_task_src_op / idx_task_sched_at / idx_task_queue / idx_task_cred。
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_task`
(
    `id`                   BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT 'PK · TaskID',

    `schedule_instance_id` BIGINT UNSIGNED  NOT NULL COMMENT '冗余调度实例，便于聚合',
    `plan_id`              BIGINT UNSIGNED  NOT NULL,
    `slice_id`             BIGINT UNSIGNED  NOT NULL,

    `provenance_code`      VARCHAR(64)      NOT NULL COMMENT '来源代码：与 reg_provenance.provenance_code 一致',
    `operation_code`       VARCHAR(32)      NOT NULL COMMENT 'DICT CODE(type=ing_operation)：操作类型 HARVEST/BACKFILL/UPDATE/METRICS',
    `credential_id`        BIGINT UNSIGNED  NULL COMMENT '所用凭据ID（reg_prov_credential.id；可空=匿名/公共）',

    `params`               JSON             NULL COMMENT '任务参数(规范化)',
    `idempotent_key`       CHAR(64)         NOT NULL COMMENT 'SHA256(slice_signature + expr_hash + operation + trigger + normalized(params))',
    `expr_hash`            CHAR(64)         NOT NULL COMMENT '冗余：执行表达式哈希',

    `priority`             TINYINT UNSIGNED NOT NULL DEFAULT 5 COMMENT '1高→9低',

    -- 任务租约（并发抢占/续租/回收）
    `lease_owner`          VARCHAR(64)      NULL COMMENT '租约持有者标识（执行器实例/workerId）',
    `leased_until`         TIMESTAMP(6)     NULL COMMENT '租约到期时间(UTC)，过期视为可重新抢占',
    `lease_count`          INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '累计抢占/续租次数（监控/熔断用）',

    `status_code`          VARCHAR(32)      NOT NULL DEFAULT 'QUEUED' COMMENT 'DICT CODE(type=ing_task_status)：QUEUED/RUNNING/SUCCEEDED/FAILED/CANCELLED',
    `scheduled_at`         TIMESTAMP(6)     NULL COMMENT '计划开始',
    `started_at`           TIMESTAMP(6)     NULL COMMENT '实际开始',
    `finished_at`          TIMESTAMP(6)     NULL COMMENT '结束',

    `scheduler_run_id`     VARCHAR(64)      NULL COMMENT '外部调度运行ID（若逐片触发才用）',
    `correlation_id`       VARCHAR(64)      NULL COMMENT 'Trace/CID',

    -- 审计字段
    `record_remarks`       JSON             NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`              BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`           VARBINARY(16)    NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`           BIGINT UNSIGNED  NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)     NULL COMMENT '创建人姓名',
    `updated_at`           TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`           BIGINT UNSIGNED  NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)     NULL COMMENT '更新人姓名',
    `deleted`              TINYINT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),

    -- 幂等与常用检索索引
    UNIQUE KEY `uk_task_idem` (`idempotent_key`),
    KEY `idx_task_slice` (`slice_id`, `status_code`),
    KEY `idx_task_src_op` (`provenance_code`, `operation_code`, `status_code`),
    KEY `idx_task_sched_at` (`status_code`, `scheduled_at`),
    KEY `idx_task_cred` (`credential_id`),

    -- 队列检索索引：用于批量拉取待运行任务
    -- 建议查询：WHERE status_code='QUEUED' AND (leased_until IS NULL OR leased_until < NOW(6))
    -- 公平出队：priority ASC（数值小优先）→ scheduled_at ASC → id ASC
    KEY `idx_task_queue` (`status_code`, `leased_until`, `priority`, `scheduled_at`, `id`),

    -- 审计辅助索引
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='任务：每个切片生成一个任务；支持强幂等与调度/执行状态；不创建物理外键';

-- ======================================================================
-- 5) 任务运行（attempt）：一次具体尝试；失败重试/回放各自新增记录
-- ======================================================================
/* ====================================================================
 * 表：ing_task_run —— 任务运行（attempt）
 * 语义：一次具体尝试（首次/重试/回放）；失败不覆盖 task，只在 run 记录。
 * 关键点：同一 task 下 (attempt_no) 唯一；不建 FK。
 * 索引：uk_run_attempt / idx_run_task_status。
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_task_run`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · RunID',
    `task_id`          BIGINT UNSIGNED NOT NULL COMMENT '关联任务',
    `attempt_no`       INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '尝试序号(1起)',

    `provenance_code`  VARCHAR(64)     NULL COMMENT '冗余：来源代码，与 reg_provenance.provenance_code 一致（回溯与聚合）',
    `operation_code`   VARCHAR(32)     NULL COMMENT '冗余：操作类型（任务已含），便于直接按 (source,op,status) 统计',

    `status_code`      VARCHAR(32)     NOT NULL DEFAULT 'PLANNED' COMMENT 'DICT CODE(type=ing_task_run_status)：PLANNED/RUNNING/SUCCEEDED/FAILED/CANCELLED',
    `checkpoint`       JSON            NULL COMMENT '运行级检查点（如 nextHint / resumeToken 等）',
    `stats`            JSON            NULL COMMENT '统计：fetched/upserted/failed/pages 等',
    `error`            TEXT            NULL COMMENT '失败原因',

    `window_from`      TIMESTAMP(6)    NULL COMMENT '时间型切片时冗余窗口起(UTC)[含]',
    `window_to`        TIMESTAMP(6)    NULL COMMENT '时间型切片时冗余窗口止(UTC)[不含]',

    `started_at`       TIMESTAMP(6)    NULL,
    `finished_at`      TIMESTAMP(6)    NULL,
    `last_heartbeat`   TIMESTAMP(6)    NULL,

    `scheduler_run_id` VARCHAR(64)     NULL,
    `correlation_id`   VARCHAR(64)     NULL,

    -- 审计字段
    `record_remarks`   JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`       VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`       BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`  VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`       BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`  VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

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
    COMMENT ='任务运行（attempt）：一次具体尝试；失败重试/回放各自新增记录；不创建物理外键';

-- ======================================================================
-- 6) 运行批次：页码/令牌步进的最小账目；承载断点续跑与去重
-- ======================================================================
/* ====================================================================
 * 表：ing_task_run_batch —— 运行批次
 * 语义：run 执行过程中的分页/令牌步进账目，是断点续跑与去重的最小颗粒。
 * 关键点：
 *  - 与 run/task/slice/plan 仅逻辑关联（不建 FK）；
 *  - 幂等键 idempotent_key 非空且唯一；(run_id,batch_no) 与 (run_id,before_token) 唯一。
 * 索引：uk_run_batch_no / uk_run_before_tok / uk_batch_idem 及状态时间索引。
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_task_run_batch`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · BatchID',
    `run_id`          BIGINT UNSIGNED NOT NULL COMMENT '关联 Run',

    `task_id`         BIGINT UNSIGNED NULL COMMENT '冗余 · Task',
    `slice_id`        BIGINT UNSIGNED NULL COMMENT '冗余 · Slice',
    `plan_id`         BIGINT UNSIGNED NULL COMMENT '冗余 · Plan',
    `expr_hash`       CHAR(64)        NULL COMMENT '冗余 · 执行表达式哈希',

    `provenance_code` VARCHAR(64)     NULL COMMENT '冗余：来源代码（来源于 task/run 链路）',
    `operation_code`  VARCHAR(32)     NULL COMMENT '冗余：操作类型（来源于 task）',

    `batch_no`        INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '批次序号(1起,连续)',
    `page_no`         INT UNSIGNED    NULL COMMENT '页码（offset/limit；token 分页为空）',
    `page_size`       INT UNSIGNED    NULL COMMENT '页大小',

    `before_token`    VARCHAR(512)    NULL COMMENT '该批开始令牌/位置（retstart/cursorMark等）',
    `after_token`     VARCHAR(512)    NULL COMMENT '该批结束令牌/下一位置',

    `idempotent_key`  CHAR(64)        NOT NULL COMMENT 'SHA256(run_id + before_token | page_no)',
    `record_count`    INT UNSIGNED    NOT NULL DEFAULT 0,
    `status_code`     VARCHAR(32)     NOT NULL DEFAULT 'RUNNING' COMMENT 'DICT CODE(type=ing_batch_status)：RUNNING/SUCCEEDED/FAILED/SKIPPED',
    `committed_at`    TIMESTAMP(6)    NULL,
    `error`           TEXT            NULL,
    `stats`           JSON            NULL,

    -- 审计字段
    `record_remarks`  JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`      VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`      BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`      BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

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
    COMMENT ='运行批次：页码/令牌步进的最小账目；承载断点续跑与去重；不创建物理外键';

-- ======================================================================
-- 7) 通用水位（当前值）：(source_code + operation + cursor_key + namespace) 唯一
-- ======================================================================
/* ====================================================================
 * 表：ing_cursor —— 当前水位
 * 语义：某来源 + 操作 + 游标键 + 命名空间的当前推进值；兼容 TIME/ID/TOKEN 三类。
 * 关键点：
 *  - 唯一：(provenance_code, operation_code, cursor_key, namespace_scope_code, namespace_key)；
 *  - 归一值：normalized_instant / normalized_numeric 便于排序与范围查询；
 *  - lineage：最近一次推进的 schedule/plan/slice/task/run/batch（逻辑关联，不建 FK）。
 * 索引：uk_cursor_ns / idx_cursor_src_key / idx_cursor_sort_time / idx_cursor_sort_id / idx_cursor_lineage。
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_cursor`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',

    `provenance_code`      VARCHAR(64)     NOT NULL COMMENT '来源代码：与 reg_provenance.provenance_code 一致',
    `operation_code`       VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation)：HARVEST/BACKFILL/UPDATE/METRICS',
    `cursor_key`           VARCHAR(64)     NOT NULL COMMENT '游标键：updated_at/published_at/seq_id/cursor_token 等',
    `namespace_scope_code` VARCHAR(32)     NOT NULL DEFAULT 'GLOBAL' COMMENT 'DICT CODE(type=ing_namespace_scope)：GLOBAL/EXPR/CUSTOM',
    `namespace_key`        CHAR(64)        NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000'
        COMMENT '命名空间键：expr_hash 或自定义哈希；global=全0',

    `cursor_type_code`     VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_cursor_type)：TIME/ID/TOKEN',
    `cursor_value`         VARCHAR(1024)   NOT NULL COMMENT '当前有效游标值（UTC ISO-8601 / 十进制字符串 / 不透明串）',
    `observed_max_value`   VARCHAR(1024)   NULL COMMENT '观测到的最大边界',

    `normalized_instant`   TIMESTAMP(6)    NULL COMMENT 'cursor_type=time 时填充（UTC）',
    `normalized_numeric`   DECIMAL(38, 0)  NULL COMMENT 'cursor_type=id 时填充',

    `schedule_instance_id` BIGINT UNSIGNED NULL COMMENT '最近一次推进的调度实例',
    `plan_id`              BIGINT UNSIGNED NULL COMMENT '最近一次推进关联 Plan',
    `slice_id`             BIGINT UNSIGNED NULL COMMENT '最近一次推进关联 Slice',
    `task_id`              BIGINT UNSIGNED NULL COMMENT '最近一次推进关联 Task',
    `last_run_id`          BIGINT UNSIGNED NULL COMMENT '最近一次推进的 Run',
    `last_batch_id`        BIGINT UNSIGNED NULL COMMENT '最近一次推进的 Batch',
    `expr_hash`            CHAR(64)        NULL COMMENT '最近推进使用的表达式哈希',

    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（只允许向前推进）',

    -- 审计字段（对该“当前值”表也统一落）
    `record_remarks`       JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `ip_address`           VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`           BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`           BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted`              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

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
    COMMENT ='通用水位·当前值：(source_code + operation + cursor_key + namespace) 唯一；兼容 time/id/token；不创建物理外键';

-- ======================================================================
-- 8) 水位推进事件（不可变）：每次成功推进记一条事件；支持回放与全链路回溯
-- ======================================================================
/* ====================================================================
 * 表：ing_cursor_event —— 水位推进事件
 * 语义：Append-only 审计事件；每次成功推进记录一条，支持回放与全链路回溯。
 * 关键点：
 *  - 幂等：idempotent_key 唯一；
 *  - lineage：schedule/plan/slice/task/run/batch 逻辑关联（不建 FK）。
 * 索引：uk_cur_evt_idem / idx_cur_evt_timeline / idx_cur_evt_window / idx_cur_evt_instant / idx_cur_evt_numeric / idx_cur_evt_lineage。
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_cursor_event`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',

    `provenance_code`      VARCHAR(64)     NOT NULL,
    `operation_code`       VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation)：HARVEST/BACKFILL/UPDATE/METRICS',
    `cursor_key`           VARCHAR(64)     NOT NULL,
    `namespace_scope_code` VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_namespace_scope)：GLOBAL/EXPR/CUSTOM',
    `namespace_key`        CHAR(64)        NOT NULL,

    `cursor_type_code`     VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_cursor_type)：TIME/ID/TOKEN',
    `prev_value`           VARCHAR(1024)   NULL,
    `new_value`            VARCHAR(1024)   NOT NULL,
    `observed_max_value`   VARCHAR(1024)   NULL,

    `prev_instant`         TIMESTAMP(6)    NULL,
    `new_instant`          TIMESTAMP(6)    NULL,
    `prev_numeric`         DECIMAL(38, 0)  NULL,
    `new_numeric`          DECIMAL(38, 0)  NULL,

    `window_from`          TIMESTAMP(6)    NULL COMMENT '覆盖窗口起(UTC)[含]',
    `window_to`            TIMESTAMP(6)    NULL COMMENT '覆盖窗口止(UTC)[不含]',

    `direction_code`       VARCHAR(16)     NULL COMMENT 'DICT CODE(type=ing_cursor_direction)：FORWARD/BACKFILL',

    `idempotent_key`       CHAR(64)        NOT NULL COMMENT '事件幂等键：SHA256(source,op,key,ns_scope,ns_key,prev->new,ingestWindow,run_id,...)',

    `schedule_instance_id` BIGINT UNSIGNED NULL,
    `plan_id`              BIGINT UNSIGNED NULL,
    `slice_id`             BIGINT UNSIGNED NULL,
    `task_id`              BIGINT UNSIGNED NULL,
    `run_id`               BIGINT UNSIGNED NULL,
    `batch_id`             BIGINT UNSIGNED NULL,
    `expr_hash`            CHAR(64)        NULL,

    -- 审计字段（事件表通常不可变；如需统一治理则保留以下字段）
    `record_remarks`       JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`           VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`           BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`           BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted`              TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_cur_evt_idem` (`idempotent_key`),

    KEY `idx_cur_evt_timeline` (`provenance_code`, `operation_code`, `cursor_key`, `namespace_scope_code`,
                                `namespace_key`),
    KEY `idx_cur_evt_window` (`window_from`, `window_to`),
    KEY `idx_cur_evt_instant` (`cursor_type_code`, `new_instant`),
    KEY `idx_cur_evt_numeric` (`cursor_type_code`, `new_numeric`),
    KEY `idx_cur_evt_lineage` (`schedule_instance_id`, `plan_id`, `slice_id`, `task_id`, `run_id`, `batch_id`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='水位推进事件（不可变）：每次成功推进一条事件；支持回放与全链路回溯；不创建物理外键';
