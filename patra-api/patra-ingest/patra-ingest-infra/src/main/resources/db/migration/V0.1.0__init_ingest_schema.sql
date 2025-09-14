-- =====================================================================
-- B. 采集（ingest）—— ing_*
-- =====================================================================
-- ======================================================================
-- 1) 调度实例：一次外部触发与其“当时配置/表达式原型”快照
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_schedule_instance`
(
    `id`                         BIGINT UNSIGNED                     NOT NULL COMMENT 'PK · 调度实例ID',
    `scheduler`                  ENUM ('xxl','manual','other')       NOT NULL DEFAULT 'xxl' COMMENT '调度器来源',
    `scheduler_job_id`           VARCHAR(64)                         NULL COMMENT '外部 JobID（如 XXL 的 jobId）',
    `scheduler_log_id`           VARCHAR(64)                         NULL COMMENT '外部运行/日志ID（如 XXL 的 logId）',
    `trigger_type`               ENUM ('manual','schedule','replay') NOT NULL DEFAULT 'schedule' COMMENT '触发类型',
    `triggered_at`               TIMESTAMP(6)                        NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '触发时间(UTC)',
    `trigger_params`             JSON                                NULL COMMENT '调度入参(规范化)',
    `literature_provenance_code` VARCHAR(64)                         NOT NULL COMMENT '来源代码：pubmed/epmc/crossref 等',
    `provenance_config_snapshot` JSON                                NULL COMMENT '来源配置/窗口/限流/重试等快照（中立模型）',
    `expr_proto_hash`            CHAR(64)                            NULL COMMENT '表达式原型哈希（规范化 AST）',
    `expr_proto_snapshot`        JSON                                NULL COMMENT '表达式原型 AST 快照（不含切片条件)',

    -- 审计字段
    `record_remarks`             JSON                                NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`                    BIGINT UNSIGNED                     NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                 VARBINARY(16)                       NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`                 TIMESTAMP(6)                        NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`                 BIGINT UNSIGNED                     NULL COMMENT '创建人ID',
    `created_by_name`            VARCHAR(100)                        NULL COMMENT '创建人姓名',
    `updated_at`                 TIMESTAMP(6)                        NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`                 BIGINT UNSIGNED                     NULL COMMENT '更新人ID',
    `updated_by_name`            VARCHAR(100)                        NULL COMMENT '更新人姓名',
    `deleted`                    TINYINT(1)                          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),
    KEY `idx_sched_src` (`scheduler`, `scheduler_job_id`, `scheduler_log_id`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='调度实例：一次外部触发与其快照（作为本次编排的根）';

-- ======================================================================
-- 2) 计划蓝图：定义总窗口与切片策略（表达式原型，不含局部化条件）
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_plan`
(
    `id`                   BIGINT UNSIGNED                                                     NOT NULL COMMENT 'PK · PlanID',
    `schedule_instance_id` BIGINT UNSIGNED                                                     NOT NULL COMMENT '关联调度实例',
    `plan_key`             VARCHAR(128)                                                        NOT NULL COMMENT '人类可读/外部幂等键（唯一）',

    `expr_proto_hash`      CHAR(64)                                                            NOT NULL COMMENT '表达式原型哈希',
    `expr_proto_snapshot`  JSON                                                                NULL COMMENT '表达式原型 AST 快照',

    `window_from`          TIMESTAMP(6)                                                        NULL COMMENT '总窗起(含,UTC)',
    `window_to`            TIMESTAMP(6)                                                        NULL COMMENT '总窗止(不含,UTC)',

    `slice_strategy`       ENUM ('time','id_range','cursor_landmark','volume_budget','hybrid') NOT NULL COMMENT '切片策略',
    `slice_params`         JSON                                                                NULL COMMENT '切片参数：step/zone/landmarks/budget 等',

    `status`               ENUM ('draft','ready','active','completed','aborted')               NOT NULL DEFAULT 'ready' COMMENT '计划状态',

    -- 审计字段
    `record_remarks`       JSON                                                                NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`              BIGINT UNSIGNED                                                     NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`           VARBINARY(16)                                                       NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)                                                        NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`           BIGINT UNSIGNED                                                     NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)                                                        NULL COMMENT '创建人姓名',
    `updated_at`           TIMESTAMP(6)                                                        NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`           BIGINT UNSIGNED                                                     NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)                                                        NULL COMMENT '更新人姓名',
    `deleted`              TINYINT(1)                                                          NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_key` (`plan_key`),
    KEY `idx_plan_sched` (`schedule_instance_id`),
    KEY `idx_plan_status` (`status`),
    KEY `idx_plan_expr` (`expr_proto_hash`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='计划蓝图：定义总窗口与切片策略（表达式原型，不含局部化条件）';

-- ======================================================================
-- 3) 计划切片：通用分片（时间/ID/token/预算），并行与幂等边界
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_plan_slice`
(
    `id`                   BIGINT UNSIGNED NOT NULL COMMENT 'PK · SliceID',
    `plan_id`              BIGINT UNSIGNED NOT NULL COMMENT '关联 Plan',

    `slice_no`             INT             NOT NULL COMMENT '切片序号(0..N)',
    `slice_signature_hash` CHAR(64)        NOT NULL COMMENT '切片签名哈希(规范化的通用边界JSON)',
    `slice_spec`           JSON            NOT NULL COMMENT '通用边界说明：时间/ID区间/landmark/预算等',

    `expr_hash`            CHAR(64)        NOT NULL COMMENT '局部化表达式哈希',
    `expr_snapshot`        JSON            NULL COMMENT '局部化表达式 AST 快照（含本Slice边界）',

    `status`               ENUM ('pending','dispatched','executing','succeeded','failed','partial','cancelled')
                                           NOT NULL DEFAULT 'pending' COMMENT '切片状态',

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
    KEY `idx_slice_status` (`status`),
    KEY `idx_slice_expr` (`expr_hash`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='计划切片：通用分片（时间/ID/token/预算），是并行与幂等的边界';

-- ======================================================================
-- 4) 任务：每个切片生成一个任务；支持强幂等与调度/执行状态
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_task`
(
    `id`                         BIGINT UNSIGNED                                NOT NULL COMMENT 'PK · TaskID',

    `schedule_instance_id`       BIGINT UNSIGNED                                NOT NULL COMMENT '冗余调度实例，便于聚合',
    `plan_id`                    BIGINT UNSIGNED                                NOT NULL,
    `slice_id`                   BIGINT UNSIGNED                                NOT NULL,

    `literature_provenance_code` VARCHAR(64)                                    NOT NULL COMMENT '来源代码：pubmed/epmc/crossref 等',
    `operation`                  ENUM ('harvest','backfill','update','metrics') NOT NULL COMMENT '操作类型',
    `api_credential_id`          BIGINT UNSIGNED                                NULL COMMENT '所用凭据ID（可空=匿名/公共）',

    `params`                     JSON                                           NULL COMMENT '任务参数(规范化)',
    `idempotent_key`             CHAR(64)                                       NOT NULL COMMENT 'SHA256(slice_signature + expr_hash + operation + trigger + normalized(params))',
    `expr_hash`                  CHAR(64)                                       NOT NULL COMMENT '冗余：执行表达式哈希',

    `priority`                   TINYINT UNSIGNED                               NOT NULL DEFAULT 5 COMMENT '1高→9低',
    `status`                     ENUM ('queued','running','succeeded','failed','cancelled')
                                                                                NOT NULL DEFAULT 'queued' COMMENT '任务状态',
    `scheduled_at`               TIMESTAMP(6)                                   NULL COMMENT '计划开始',
    `started_at`                 TIMESTAMP(6)                                   NULL COMMENT '实际开始',
    `finished_at`                TIMESTAMP(6)                                   NULL COMMENT '结束',

    `scheduler_run_id`           VARCHAR(64)                                    NULL COMMENT '外部调度运行ID（若逐片触发才用）',
    `correlation_id`             VARCHAR(64)                                    NULL COMMENT 'Trace/CID',

    -- 审计字段
    `record_remarks`             JSON                                           NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`                    BIGINT UNSIGNED                                NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                 VARBINARY(16)                                  NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`                 TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`                 BIGINT UNSIGNED                                NULL COMMENT '创建人ID',
    `created_by_name`            VARCHAR(100)                                   NULL COMMENT '创建人姓名',
    `updated_at`                 TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`                 BIGINT UNSIGNED                                NULL COMMENT '更新人ID',
    `updated_by_name`            VARCHAR(100)                                   NULL COMMENT '更新人姓名',
    `deleted`                    TINYINT(1)                                     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_task_idem` (`idempotent_key`),
    KEY `idx_task_slice` (`slice_id`, `status`),
    KEY `idx_task_src_op` (`literature_provenance_code`, `operation`, `status`),
    KEY `idx_task_sched_at` (`status`, `scheduled_at`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='任务：每个切片生成一个任务；支持强幂等与调度/执行状态';

-- ======================================================================
-- 5) 任务运行（attempt）：一次具体尝试；失败重试/回放各自新增记录
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_task_run`
(
    `id`               BIGINT UNSIGNED                                             NOT NULL COMMENT 'PK · RunID',
    `task_id`          BIGINT UNSIGNED                                             NOT NULL COMMENT '关联任务',
    `attempt_no`       INT UNSIGNED                                                NOT NULL DEFAULT 1 COMMENT '尝试序号(1起)',

    `status`           ENUM ('planned','running','succeeded','failed','cancelled') NOT NULL DEFAULT 'planned',
    `checkpoint`       JSON                                                        NULL COMMENT '运行级检查点（如 nextHint / resumeToken 等）',
    `stats`            JSON                                                        NULL COMMENT '统计：fetched/upserted/failed/pages 等',
    `error`            TEXT                                                        NULL COMMENT '失败原因',

    `window_from`      TIMESTAMP(6)                                                NULL COMMENT '时间型切片时冗余窗口起(UTC)[含]',
    `window_to`        TIMESTAMP(6)                                                NULL COMMENT '时间型切片时冗余窗口止(UTC)[不含]',

    `started_at`       TIMESTAMP(6)                                                NULL,
    `finished_at`      TIMESTAMP(6)                                                NULL,
    `last_heartbeat`   TIMESTAMP(6)                                                NULL,

    `scheduler_run_id` VARCHAR(64)                                                 NULL,
    `correlation_id`   VARCHAR(64)                                                 NULL,

    -- 审计字段
    `record_remarks`   JSON                                                        NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`          BIGINT UNSIGNED                                             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`       VARBINARY(16)                                               NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`       TIMESTAMP(6)                                                NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`       BIGINT UNSIGNED                                             NULL COMMENT '创建人ID',
    `created_by_name`  VARCHAR(100)                                                NULL COMMENT '创建人姓名',
    `updated_at`       TIMESTAMP(6)                                                NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`       BIGINT UNSIGNED                                             NULL COMMENT '更新人ID',
    `updated_by_name`  VARCHAR(100)                                                NULL COMMENT '更新人姓名',
    `deleted`          TINYINT(1)                                                  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_run_attempt` (`task_id`, `attempt_no`),
    KEY `idx_run_task_status` (`task_id`, `status`, `started_at`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='任务运行（attempt）：一次具体尝试；失败重试/回放各自新增记录';

-- ======================================================================
-- 6) 运行批次：页码/令牌步进的最小账目；承载断点续跑与去重
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_task_run_batch`
(
    `id`              BIGINT UNSIGNED                                 NOT NULL COMMENT 'PK · BatchID',
    `run_id`          BIGINT UNSIGNED                                 NOT NULL COMMENT '关联 Run',

    `task_id`         BIGINT UNSIGNED                                 NULL COMMENT '冗余 · Task',
    `slice_id`        BIGINT UNSIGNED                                 NULL COMMENT '冗余 · Slice',
    `plan_id`         BIGINT UNSIGNED                                 NULL COMMENT '冗余 · Plan',
    `expr_hash`       CHAR(64)                                        NULL COMMENT '冗余 · 执行表达式哈希',

    `batch_no`        INT UNSIGNED                                    NOT NULL DEFAULT 1 COMMENT '批次序号(1起,连续)',
    `page_no`         INT UNSIGNED                                    NULL COMMENT '页码（offset/limit；token 分页为空）',
    `page_size`       INT UNSIGNED                                    NULL COMMENT '页大小',

    `before_token`    VARCHAR(512)                                    NULL COMMENT '该批开始令牌/位置（retstart/cursorMark等）',
    `after_token`     VARCHAR(512)                                    NULL COMMENT '该批结束令牌/下一位置',

    `idempotent_key`  CHAR(64)                                        NULL COMMENT 'SHA256(run_id + before_token | page_no)',
    `record_count`    INT UNSIGNED                                    NOT NULL DEFAULT 0,
    `status`          ENUM ('running','succeeded','failed','skipped') NOT NULL DEFAULT 'running',
    `committed_at`    TIMESTAMP(6)                                    NULL,
    `error`           TEXT                                            NULL,
    `stats`           JSON                                            NULL,

    -- 审计字段
    `record_remarks`  JSON                                            NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`         BIGINT UNSIGNED                                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`      VARBINARY(16)                                   NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`      TIMESTAMP(6)                                    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`      BIGINT UNSIGNED                                 NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100)                                    NULL COMMENT '创建人姓名',
    `updated_at`      TIMESTAMP(6)                                    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`      BIGINT UNSIGNED                                 NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100)                                    NULL COMMENT '更新人姓名',
    `deleted`         TINYINT(1)                                      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_run_batch_no` (`run_id`, `batch_no`),
    UNIQUE KEY `uk_run_before_tok` (`run_id`, `before_token`),
    UNIQUE KEY `uk_batch_idem` (`idempotent_key`),

    KEY `idx_batch_after_tok` (`run_id`, `after_token`),
    KEY `idx_batch_status_time` (`run_id`, `status`, `committed_at`),
    KEY `idx_batch_task` (`task_id`, `status`, `committed_at`),
    KEY `idx_batch_slice` (`slice_id`, `status`, `committed_at`),
    KEY `idx_batch_plan` (`plan_id`, `status`, `committed_at`),
    KEY `idx_batch_expr` (`expr_hash`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='运行批次：页码/令牌步进的最小账目；承载断点续跑与去重';

-- ======================================================================
-- 7) 通用水位（当前值）：(source_code + operation + cursor_key + namespace) 唯一
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_cursor`
(
    `id`                         BIGINT UNSIGNED                                NOT NULL COMMENT 'PK',

    `literature_provenance_code` VARCHAR(64)                                    NOT NULL COMMENT '来源代码：pubmed/epmc/crossref 等',
    `operation`                  ENUM ('harvest','backfill','update','metrics') NOT NULL COMMENT '操作类型',
    `cursor_key`                 VARCHAR(64)                                    NOT NULL COMMENT '游标键：updated_at/published_at/seq_id/cursor_token 等',
    `namespace_scope`            ENUM ('global','expr','custom')                NOT NULL DEFAULT 'global' COMMENT '命名空间：global=全局; expr=按表达式; custom=自定义',
    `namespace_key`              CHAR(64)                                       NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000'
        COMMENT '命名空间键：expr_hash 或自定义哈希；global=全0',

    `cursor_type`                ENUM ('time','id','token')                     NOT NULL COMMENT 'time=时间; id=递增数值; token=不透明令牌',
    `cursor_value`               VARCHAR(1024)                                  NOT NULL COMMENT '当前有效游标值（UTC ISO-8601 / 十进制字符串 / 不透明串）',
    `observed_max_value`         VARCHAR(1024)                                  NULL COMMENT '观测到的最大边界',

    `normalized_instant`         TIMESTAMP(6)                                   NULL COMMENT 'cursor_type=time 时填充（UTC）',
    `normalized_numeric`         DECIMAL(38, 0)                                 NULL COMMENT 'cursor_type=id 时填充',

    `schedule_instance_id`       BIGINT UNSIGNED                                NULL COMMENT '最近一次推进的调度实例',
    `plan_id`                    BIGINT UNSIGNED                                NULL COMMENT '最近一次推进关联 Plan',
    `slice_id`                   BIGINT UNSIGNED                                NULL COMMENT '最近一次推进关联 Slice',
    `task_id`                    BIGINT UNSIGNED                                NULL COMMENT '最近一次推进关联 Task',
    `last_run_id`                BIGINT UNSIGNED                                NULL COMMENT '最近一次推进的 Run',
    `last_batch_id`              BIGINT UNSIGNED                                NULL COMMENT '最近一次推进的 Batch',
    `expr_hash`                  CHAR(64)                                       NULL COMMENT '最近推进使用的表达式哈希',

    `version`                    BIGINT UNSIGNED                                NOT NULL DEFAULT 0 COMMENT '乐观锁版本号（只允许向前推进）',

    -- 审计字段（对该“当前值”表也统一落）
    `record_remarks`             JSON                                           NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `ip_address`                 VARBINARY(16)                                  NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`                 TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`                 BIGINT UNSIGNED                                NULL COMMENT '创建人ID',
    `created_by_name`            VARCHAR(100)                                   NULL COMMENT '创建人姓名',
    `updated_at`                 TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`                 BIGINT UNSIGNED                                NULL COMMENT '更新人ID',
    `updated_by_name`            VARCHAR(100)                                   NULL COMMENT '更新人姓名',
    `deleted`                    TINYINT(1)                                     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_cursor_ns` (`literature_provenance_code`, `operation`, `cursor_key`, `namespace_scope`,
                               `namespace_key`),

    KEY `idx_cursor_src_key` (`literature_provenance_code`, `operation`, `cursor_key`),
    KEY `idx_cursor_sort_time` (`cursor_type`, `normalized_instant`),
    KEY `idx_cursor_sort_id` (`cursor_type`, `normalized_numeric`),
    KEY `idx_cursor_lineage` (`schedule_instance_id`, `plan_id`, `slice_id`, `task_id`, `last_run_id`, `last_batch_id`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='通用水位·当前值：(source_code + operation + cursor_key + namespace) 唯一；兼容 time/id/token';

-- ======================================================================
-- 8) 水位推进事件（不可变）：每次成功推进记一条事件；支持回放与全链路回溯
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_cursor_event`
(
    `id`                         BIGINT UNSIGNED                                NOT NULL COMMENT 'PK',

    `literature_provenance_code` VARCHAR(64)                                    NOT NULL,
    `operation`                  ENUM ('harvest','backfill','update','metrics') NOT NULL,
    `cursor_key`                 VARCHAR(64)                                    NOT NULL,
    `namespace_scope`            ENUM ('global','expr','custom')                NOT NULL,
    `namespace_key`              CHAR(64)                                       NOT NULL,

    `cursor_type`                ENUM ('time','id','token')                     NOT NULL,
    `prev_value`                 VARCHAR(1024)                                  NULL,
    `new_value`                  VARCHAR(1024)                                  NOT NULL,
    `observed_max_value`         VARCHAR(1024)                                  NULL,

    `prev_instant`               TIMESTAMP(6)                                   NULL,
    `new_instant`                TIMESTAMP(6)                                   NULL,
    `prev_numeric`               DECIMAL(38, 0)                                 NULL,
    `new_numeric`                DECIMAL(38, 0)                                 NULL,

    `window_from`                TIMESTAMP(6)                                   NULL COMMENT '覆盖窗口起(UTC)[含]',
    `window_to`                  TIMESTAMP(6)                                   NULL COMMENT '覆盖窗口止(UTC)[不含]',

    `direction`                  ENUM ('forward','backfill')                    NULL COMMENT 'forward=增量; backfill=历史回灌',

    `idempotent_key`             CHAR(64)                                       NOT NULL COMMENT '事件幂等键：SHA256(source,op,key,ns_scope,ns_key,prev->new,window,run_id,...)',

    `schedule_instance_id`       BIGINT UNSIGNED                                NULL,
    `plan_id`                    BIGINT UNSIGNED                                NULL,
    `slice_id`                   BIGINT UNSIGNED                                NULL,
    `task_id`                    BIGINT UNSIGNED                                NULL,
    `run_id`                     BIGINT UNSIGNED                                NULL,
    `batch_id`                   BIGINT UNSIGNED                                NULL,
    `expr_hash`                  CHAR(64)                                       NULL,

    -- 审计字段（事件表通常不可变；如需统一治理则保留以下字段）
    `record_remarks`             JSON                                           NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `version`                    BIGINT UNSIGNED                                NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                 VARBINARY(16)                                  NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`                 TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`                 BIGINT UNSIGNED                                NULL COMMENT '创建人ID',
    `created_by_name`            VARCHAR(100)                                   NULL COMMENT '创建人姓名',
    `updated_at`                 TIMESTAMP(6)                                   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`                 BIGINT UNSIGNED                                NULL COMMENT '更新人ID',
    `updated_by_name`            VARCHAR(100)                                   NULL COMMENT '更新人姓名',
    `deleted`                    TINYINT(1)                                     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_cur_evt_idem` (`idempotent_key`),

    KEY `idx_cur_evt_timeline` (`literature_provenance_code`, `operation`, `cursor_key`, `namespace_scope`,
                                `namespace_key`),
    KEY `idx_cur_evt_window` (`window_from`, `window_to`),
    KEY `idx_cur_evt_instant` (`cursor_type`, `new_instant`),
    KEY `idx_cur_evt_numeric` (`cursor_type`, `new_numeric`),
    KEY `idx_cur_evt_lineage` (`schedule_instance_id`, `plan_id`, `slice_id`, `task_id`, `run_id`, `batch_id`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='水位推进事件（不可变）：每次成功推进一条事件；支持回放与全链路回溯';
