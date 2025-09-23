-- B. 采集（ingest）—— ing_*
-- - 统一审计字段：record_remarks / created_at/created_by/created_by_name / updated_at/updated_by/updated_by_name / version / ip_address / deleted。
-- - 不创建物理外键（跨/内模块均由应用层保证完整性，必要处建立二级索引）。
-- - MySQL 8.0 · InnoDB · utf8mb4_0900_ai_ci
-- ======================================================================
-- 1) 调度实例：一次外部触发事件
-- ======================================================================
/* ====================================================================
 * 表：ing_schedule_instance —— 调度实例
 * 语义：记录一次外部调度触发事件的"根"，固化当时的调度入参。
 * 关键点：
 *  - provenance_code 对齐 reg_provenance.provenance_code（逻辑关联，不建 FK）；
 *  - 配置快照和表达式原型在 ing_plan 级别保存。
 * 索引：idx_sched_src(scheduler_code, scheduler_job_id, scheduler_log_id)。
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_schedule_instance`
(
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · 调度实例ID',
    `scheduler_code`    VARCHAR(32)     NOT NULL DEFAULT 'XXL' COMMENT 'DICT CODE(type=ing_scheduler)：调度器来源',
    `scheduler_job_id`  VARCHAR(64)     NULL COMMENT '外部 JobID（如 XXL 的 jobId）',
    `scheduler_log_id`  VARCHAR(64)     NULL COMMENT '外部运行/日志ID（如 XXL 的 logId）',
    `trigger_type_code` VARCHAR(32)     NOT NULL DEFAULT 'SCHEDULE' COMMENT 'DICT CODE(type=ing_trigger_type)：触发类型',
    `triggered_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '触发时间(UTC)',
    `trigger_params`    JSON            NULL COMMENT '调度入参(规范化)',
    `provenance_code`   VARCHAR(64)     NOT NULL COMMENT '来源代码：与 reg_provenance.provenance_code 一致，如 pubmed/epmc/crossref',

    -- 审计字段
    `record_remarks`    JSON            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`           BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`        VARBINARY(16)   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`        BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`   VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`        BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`   VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted`           TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),
    KEY `idx_sched_src` (`scheduler_code`, `scheduler_job_id`, `scheduler_log_id`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='调度实例：一次外部触发事件（作为本次编排的根）；不创建物理外键';


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
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · PlanID',
    `schedule_instance_id`       BIGINT UNSIGNED NOT NULL COMMENT '关联调度实例',
    `plan_key`                   VARCHAR(128)    NOT NULL COMMENT '人类可读/外部幂等键（唯一）',

    `provenance_code`            VARCHAR(64)     NULL COMMENT '冗余：来源代码，与 reg_provenance.provenance_code 一致（便于按来源聚合）',
    `endpoint_name`              VARCHAR(64)     NULL COMMENT '来源端点标识（search/detail/metrics 等），辅助区分多端点策略',

    `operation_code`             VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation)：采集类型 HARVEST/BACKFILL/UPDATE/METRICS',
    `expr_proto_hash`            CHAR(64)        NOT NULL COMMENT '表达式原型哈希：对“规范化后的原型AST”计算出的指纹；用于幂等/快速比较；与 expr_proto_snapshot 一一对应',

    `expr_proto_snapshot`        JSON            NULL COMMENT '表达式原型快照（AST，JSON）：不含任何切片/局部化条件的“全局表达式树”；用于回放与审计（从该原型派生多个 slice）',

    `provenance_config_snapshot` JSON            NULL COMMENT '来源配置快照（中立模型，JSON）：将 reg_prov_* 的鉴权/分页/时间窗/限流/重试/批处理等配置编译为执行期不变的快照',

    `provenance_config_hash`     CHAR(64)        NULL COMMENT '来源配置快照哈希：对规范化后的 provenance_config_snapshot 计算出的指纹；用于复用判定与变更检测',

    `slice_strategy_code`        VARCHAR(32)     NOT NULL COMMENT '切片策略：TIME/ID_RANGE/CURSOR_LANDMARK/VOLUME_BUDGET/HYBRID 等；决定如何从原型生成多个 slice',

    `slice_params`               JSON            NULL COMMENT '切片参数：与切片策略配套的细节（如步长、时间区、landmark、预算上限等）；仅用于生成 slice，不直接参与执行',

    `window_from`                TIMESTAMP(6)    NULL COMMENT '总窗起(含,UTC)',
    `window_to`                  TIMESTAMP(6)    NULL COMMENT '总窗止(不含,UTC)',


    `status_code`                VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DICT CODE(type=ing_plan_status)：DRAFT/SLICING/READY/PARTIAL/FAILED/COMPLETED',

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
    UNIQUE KEY `uk_plan_key` (`plan_key`),
    KEY `idx_plan_sched` (`schedule_instance_id`),
    KEY `idx_plan_prov_op` (`provenance_code`, `operation_code`),
    KEY `idx_plan_endpoint` (`endpoint_name`),
    KEY `idx_plan_status` (`status_code`),
    KEY `idx_plan_expr` (`expr_proto_hash`),
    KEY `idx_plan_prov_config_hash` (`provenance_config_hash`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='计划蓝图：定义总窗口与切片策略（表达式原型，不含局部化条件）；含来源配置哈希；不创建物理外键';

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
    `slice_signature_hash` CHAR(64)        NOT NULL COMMENT '切片签名哈希：仅对 slice_spec（边界JSON）做规范化后计算；用于判重/去重（同一 plan 下相同边界不重复生成）',

    `slice_spec`           JSON            NOT NULL COMMENT '切片边界说明（JSON）：声明本 slice 的执行范围与约束（时间窗口/ID 区间/游标landmark/预算等），不含业务表达式逻辑',

    `expr_hash`            CHAR(64)        NOT NULL COMMENT '局部化表达式哈希：对“规范化后的局部化AST”计算出的指纹；通常与 slice_signature_hash 一起变化',

    `expr_snapshot`        JSON            NULL COMMENT '局部化表达式快照（AST，JSON）：在 plan 的原型上注入本 slice 的边界条件后的“可直接执行表达式树”；slice 自带可重放语义',
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
