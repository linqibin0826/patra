g_*
 / ip_address / deleted。
-- - 不创建物理外键（跨/内模块均由应用层保证完整性，必要处建立二级索引）。
oDB · utf8mb4_0900_ai_ci
-- ======================================================================
-- 1) 调度实例：一次外部触发事件
-- ======================================================================

CREATE TABLE IF NOT EXISTS `ing_schedule_instance`
(
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `scheduler_code`    VARCHAR(32)     NOT NULL DEFAULT 'XXL',
    `scheduler_job_id`  VARCHAR(64)     NULL,
    `scheduler_log_id`  VARCHAR(64)     NULL,
    `trigger_type_code` VARCHAR(32)     NOT NULL DEFAULT 'SCHEDULE',
    `triggered_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `trigger_params`    JSON            NULL,
    `provenance_code`   VARCHAR(64)     NOT NULL,

    -- 审计字段
    `record_remarks`    JSON            NULL,
    `version`           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `ip_address`        VARBINARY(16)   NULL,
    `created_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `created_by`        BIGINT UNSIGNED NULL,
    `created_by_name`   VARCHAR(100)    NULL,
    `updated_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `updated_by`        BIGINT UNSIGNED NULL,
    `updated_by_name`   VARCHAR(100)    NULL,
    `deleted`           TINYINT(1)      NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),
    KEY `idx_sched_src` (`scheduler_code`, `scheduler_job_id`, `scheduler_log_id`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
   ;


-- ======================================================================
-- 2) 计划蓝图：定义总窗口与切片策略（表达式原型，不含局部化条件）
-- ======================================================================

CREATE TABLE IF NOT EXISTS `ing_plan`
(
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `schedule_instance_id`       BIGINT UNSIGNED NOT NULL,
    `plan_key`                   VARCHAR(128)    NOT NULL,

    `provenance_code`            VARCHAR(64)     NULL,
    `operation_code`             VARCHAR(32)     NOT NULL,

    `expr_proto_hash`            CHAR(64)        NOT NULL,
    `expr_proto_snapshot`        JSON            NULL,
    `provenance_config_snapshot` JSON            NULL,
    `provenance_config_hash`     CHAR(64)        NULL,
    `slice_strategy_code`        VARCHAR(32)     NOT NULL,
    `slice_params`               JSON            NULL,

    `window_spec`                JSON            NOT NULL,
    `window_from_time`           TIMESTAMP(6) GENERATED ALWAYS AS (
        CASE
            -- Extract start timestamp from nested window.from field when strategy is TIME
            -- Path: window_spec.strategy (check) -> window_spec.window.from (extract)
            WHEN JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.strategy')) = 'TIME'
            THEN STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.window.from')), '%Y-%m-%dT%H:%i:%s.%f')
            ELSE NULL
        END
    ) VIRTUAL,
    `window_to_time`             TIMESTAMP(6) GENERATED ALWAYS AS (
        CASE
            -- Extract end timestamp from nested window.to field when strategy is TIME
            -- Path: window_spec.strategy (check) -> window_spec.window.to (extract)
            WHEN JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.strategy')) = 'TIME'
            THEN STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.window.to')), '%Y-%m-%dT%H:%i:%s.%f')
            ELSE NULL
        END
    ) VIRTUAL,
    `status_code`                VARCHAR(32)     NOT NULL DEFAULT 'DRAFT',

    -- 审计字段
    `record_remarks`             JSON            NULL,
    `version`                    BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `ip_address`                 VARBINARY(16)   NULL,
    `created_at`                 TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `created_by`                 BIGINT UNSIGNED NULL,
    `created_by_name`            VARCHAR(100)    NULL,
    `updated_at`                 TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `updated_by`                 BIGINT UNSIGNED NULL,
    `updated_by_name`            VARCHAR(100)    NULL,
    `deleted`                    TINYINT(1)      NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_key` (`plan_key`),
    KEY `idx_plan_sched` (`schedule_instance_id`),
    KEY `idx_plan_prov_op` (`provenance_code`, `operation_code`),
    KEY `idx_plan_status` (`status_code`),
    KEY `idx_plan_expr` (`expr_proto_hash`),
    KEY `idx_plan_prov_config_hash` (`provenance_config_hash`),
    KEY `idx_window_time_range` (`window_from_time`, `window_to_time`),
    KEY `idx_audit_deleted_upd` (`deleted`, `updated_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
   ;

-- ======================================================================
/预算），并行与幂等边界
-- ======================================================================

CREATE TABLE IF NOT EXISTS `ing_plan_slice`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `plan_id`              BIGINT UNSIGNED NOT NULL,

    `provenance_code`      VARCHAR(64)     NULL,

    `slice_no`             INT             NOT NULL,
    `slice_signature_hash` CHAR(64)        NOT NULL,
    `window_spec`          JSON            NOT NULL,
    `expr_hash`            CHAR(64)        NOT NULL,

    `expr_snapshot`        JSON            NULL,
    `status_code`          VARCHAR(32)     NOT NULL DEFAULT 'PENDING',

    -- 审计字段
    `record_remarks`       JSON            NULL,
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `ip_address`           VARBINARY(16)   NULL,
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `created_by`           BIGINT UNSIGNED NULL,
    `created_by_name`      VARCHAR(100)    NULL,
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `updated_by`           BIGINT UNSIGNED NULL,
    `updated_by_name`      VARCHAR(100)    NULL,
    `deleted`              TINYINT(1)      NOT NULL DEFAULT 0,

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
   ;


-- ======================================================================
-- 4) 任务：每个切片生成一个任务；支持强幂等与调度/执行状态
-- ======================================================================

CREATE TABLE IF NOT EXISTS `ing_task`
(
    `id`                   BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,

    `schedule_instance_id` BIGINT UNSIGNED  NOT NULL,
    `plan_id`              BIGINT UNSIGNED  NOT NULL,
    `slice_id`             BIGINT UNSIGNED  NOT NULL,

    `provenance_code`      VARCHAR(64)      NOT NULL,
    `operation_code`       VARCHAR(32)      NOT NULL,

    `params`               JSON             NULL,
    `idempotent_key`       CHAR(64)         NOT NULL,
    `expr_hash`            CHAR(64)         NOT NULL,

    `priority`             TINYINT UNSIGNED NOT NULL DEFAULT 5,

    -- 任务租约（续租/回收）
    `lease_owner`          VARCHAR(128)     NULL,
    `leased_until`         TIMESTAMP(6)     NULL,
    `lease_count`          INT UNSIGNED     NOT NULL DEFAULT 0,
    `last_heartbeat_at`    TIMESTAMP(6)     NULL,
    `retry_count`          INT UNSIGNED     NOT NULL DEFAULT 0,
    `last_error_code`      VARCHAR(64)      NULL,
    `last_error_msg`       VARCHAR(512)     NULL,

    `status_code`          VARCHAR(32)      NOT NULL DEFAULT 'QUEUED',
    `scheduled_at`         TIMESTAMP(6)     NULL,
    `started_at`           TIMESTAMP(6)     NULL,
    `finished_at`          TIMESTAMP(6)     NULL,

    `scheduler_run_id`     VARCHAR(64)      NULL,
    `correlation_id`       VARCHAR(64)      NULL,

    -- 审计字段
    `record_remarks`       JSON             NULL,
    `version`              BIGINT UNSIGNED  NOT NULL DEFAULT 0,
    `ip_address`           VARBINARY(16)    NULL,
    `created_at`           TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `created_by`           BIGINT UNSIGNED  NULL,
    `created_by_name`      VARCHAR(100)     NULL,
    `updated_at`           TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `updated_by`           BIGINT UNSIGNED  NULL,
    `updated_by_name`      VARCHAR(100)     NULL,
    `deleted`              TINYINT(1)       NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),

    -- 幂等与常用检索索引
    UNIQUE KEY `uk_task_idem` (`idempotent_key`),
    KEY `idx_task_slice` (`slice_id`, `status_code`),
    KEY `idx_task_src_op` (`provenance_code`, `operation_code`, `status_code`),
    KEY `idx_task_sched_at` (`status_code`, `scheduled_at`),

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
   ;

-- ======================================================================
-- 5) 任务运行（attempt）：一次具体尝试；失败重试/回放各自新增记录
-- ======================================================================

CREATE TABLE IF NOT EXISTS `ing_task_run`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `task_id`          BIGINT UNSIGNED NOT NULL,
    `attempt_no`       INT UNSIGNED    NOT NULL DEFAULT 1,

    `provenance_code`  VARCHAR(64)     NULL,
    `operation_code`   VARCHAR(32)     NULL,

    `status_code`      VARCHAR(32)     NOT NULL DEFAULT 'PLANNED',
    `checkpoint`       JSON            NULL,
    `stats`            JSON            NULL,
    `error`            TEXT            NULL,

    `started_at`       TIMESTAMP(6)    NULL,
    `finished_at`      TIMESTAMP(6)    NULL,
    `last_heartbeat`   TIMESTAMP(6)    NULL,

    `scheduler_run_id` VARCHAR(64)     NULL,
    `correlation_id`   VARCHAR(64)     NULL,

    -- 审计字段
    `record_remarks`   JSON            NULL,
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `ip_address`       VARBINARY(16)   NULL,
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `created_by`       BIGINT UNSIGNED NULL,
    `created_by_name`  VARCHAR(100)    NULL,
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `updated_by`       BIGINT UNSIGNED NULL,
    `updated_by_name`  VARCHAR(100)    NULL,
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0,

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
   ;

-- ======================================================================
-- 6) 运行批次：页码/令牌步进的最小账目；承载断点续跑与去重
-- ======================================================================

CREATE TABLE IF NOT EXISTS `ing_task_run_batch`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `run_id`          BIGINT UNSIGNED NOT NULL,

    `task_id`         BIGINT UNSIGNED NULL,
    `slice_id`        BIGINT UNSIGNED NULL,
    `plan_id`         BIGINT UNSIGNED NULL,
    `expr_hash`       CHAR(64)        NULL,

    `provenance_code` VARCHAR(64)     NULL,
    `operation_code`  VARCHAR(32)     NULL,

    `batch_no`        INT UNSIGNED    NOT NULL DEFAULT 1,
    `page_no`         INT UNSIGNED    NULL,
    `page_size`       INT UNSIGNED    NULL,

    `before_token`    VARCHAR(512)    NULL,
    `after_token`     VARCHAR(512)    NULL,

    `idempotent_key`  CHAR(64)        NOT NULL,
    `record_count`    INT UNSIGNED    NOT NULL DEFAULT 0,
    `status_code`     VARCHAR(32)     NOT NULL DEFAULT 'RUNNING',
    `committed_at`    TIMESTAMP(6)    NULL,
    `error`           TEXT            NULL,
    `stats`           JSON            NULL,

    -- 审计字段
    `record_remarks`  JSON            NULL,
    `version`         BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `ip_address`      VARBINARY(16)   NULL,
    `created_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `created_by`      BIGINT UNSIGNED NULL,
    `created_by_name` VARCHAR(100)    NULL,
    `updated_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `updated_by`      BIGINT UNSIGNED NULL,
    `updated_by_name` VARCHAR(100)    NULL,
    `deleted`         TINYINT(1)      NOT NULL DEFAULT 0,

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
   ;

-- ======================================================================
amespace) 唯一
-- ======================================================================

CREATE TABLE IF NOT EXISTS `ing_cursor`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    `provenance_code`      VARCHAR(64)     NOT NULL,
    `operation_code`       VARCHAR(32)     NOT NULL,
    `cursor_key`           VARCHAR(64)     NOT NULL,
    `namespace_scope_code` VARCHAR(32)     NOT NULL DEFAULT 'GLOBAL',
    `namespace_key`        CHAR(64)        NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000'
       ,

    `cursor_type_code`     VARCHAR(32)     NOT NULL,
    `cursor_value`         VARCHAR(1024)   NOT NULL,
    `observed_max_value`   VARCHAR(1024)   NULL,

    `normalized_instant`   TIMESTAMP(6)    NULL,
    `normalized_numeric`   DECIMAL(38, 0)  NULL,

    `schedule_instance_id` BIGINT UNSIGNED NULL,
    `plan_id`              BIGINT UNSIGNED NULL,
    `slice_id`             BIGINT UNSIGNED NULL,
    `task_id`              BIGINT UNSIGNED NULL,
    `last_run_id`          BIGINT UNSIGNED NULL,
    `last_batch_id`        BIGINT UNSIGNED NULL,
    `expr_hash`            CHAR(64)        NULL,

    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0,

    -- 审计字段（对该“当前值”表也统一落）
    `record_remarks`       JSON            NULL,
    `ip_address`           VARBINARY(16)   NULL,
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `created_by`           BIGINT UNSIGNED NULL,
    `created_by_name`      VARCHAR(100)    NULL,
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `updated_by`           BIGINT UNSIGNED NULL,
    `updated_by_name`      VARCHAR(100)    NULL,
    `deleted`              TINYINT(1)      NOT NULL DEFAULT 0,

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
   ;

-- ======================================================================
-- 8) 水位推进事件（不可变）：每次成功推进记一条事件；支持回放与全链路回溯
-- ======================================================================

CREATE TABLE IF NOT EXISTS `ing_cursor_event`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,

    `provenance_code`      VARCHAR(64)     NOT NULL,
    `operation_code`       VARCHAR(32)     NOT NULL,
    `cursor_key`           VARCHAR(64)     NOT NULL,
    `namespace_scope_code` VARCHAR(32)     NOT NULL,
    `namespace_key`        CHAR(64)        NOT NULL,

    `cursor_type_code`     VARCHAR(32)     NOT NULL,
    `prev_value`           VARCHAR(1024)   NULL,
    `new_value`            VARCHAR(1024)   NOT NULL,
    `observed_max_value`   VARCHAR(1024)   NULL,

    `prev_instant`         TIMESTAMP(6)    NULL,
    `new_instant`          TIMESTAMP(6)    NULL,
    `prev_numeric`         DECIMAL(38, 0)  NULL,
    `new_numeric`          DECIMAL(38, 0)  NULL,

    `direction_code`       VARCHAR(16)     NULL,

    `idempotent_key`       CHAR(64)        NOT NULL,

    `schedule_instance_id` BIGINT UNSIGNED NULL,
    `plan_id`              BIGINT UNSIGNED NULL,
    `slice_id`             BIGINT UNSIGNED NULL,
    `task_id`              BIGINT UNSIGNED NULL,
    `run_id`               BIGINT UNSIGNED NULL,
    `batch_id`             BIGINT UNSIGNED NULL,
    `expr_hash`            CHAR(64)        NULL,

    -- 审计字段（事件表通常不可变；如需统一治理则保留以下字段）
    `record_remarks`       JSON            NULL,
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `ip_address`           VARBINARY(16)   NULL,
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `created_by`           BIGINT UNSIGNED NULL,
    `created_by_name`      VARCHAR(100)    NULL,
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `updated_by`           BIGINT UNSIGNED NULL,
    `updated_by_name`      VARCHAR(100)    NULL,
    `deleted`              TINYINT(1)      NOT NULL DEFAULT 0,

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
   ;

-- ======================================================================
g_outbox_message —— 通用 Outbox（任务推送/集成事件等）
-- 语义：与业务写入同一事务落库；由 Relay 扫描并投递到 MQ（如 RocketMQ）。
-- 设计要点：
el, dedup_key) 唯一，保障源端幂等；
--  - 仅扫描本表进行发布，不扫业务热表；
"（可按需扩展，但不作为独立字段）。
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_outbox_message`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `aggregate_type`   VARCHAR(32)     NOT NULL,
    `aggregate_id`     BIGINT UNSIGNED NOT NULL,
    `channel`          VARCHAR(64)     NOT NULL,
    `op_type`          VARCHAR(32)     NOT NULL,
    `partition_key`    VARCHAR(128)    NOT NULL,
    `dedup_key`        VARCHAR(128)    NOT NULL,
    `payload_json`     JSON            NOT NULL,
    `headers_json`     JSON            NULL,
    `not_before`       TIMESTAMP(6)    NULL,

    `status_code`      VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    `retry_count`      INT UNSIGNED    NOT NULL DEFAULT 0,
    `next_retry_at`    TIMESTAMP(6)    NULL,
    `error_code`       VARCHAR(64)     NULL,
    `error_msg`        VARCHAR(512)    NULL,

    `pub_lease_owner`  VARCHAR(128)    NULL,
    `pub_leased_until` TIMESTAMP(6)    NULL,
    `msg_id`           VARCHAR(128)    NULL,

    -- 审计字段
    `record_remarks`   JSON            NULL,
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0,
    `ip_address`       VARBINARY(16)   NULL,
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `created_by`       BIGINT UNSIGNED NULL,
    `created_by_name`  VARCHAR(100)    NULL,
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `updated_by`       BIGINT UNSIGNED NULL,
    `updated_by_name`  VARCHAR(100)    NULL,
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0,

    PRIMARY KEY (`id`),

    -- 源端去重：同一 channel 下 dedup_key 必须唯一
    UNIQUE KEY `uk_outbox_channel_dedup` (`channel`, `dedup_key`),
    -- 轻量扫描索引：按状态+时间游标批量发布
    KEY `idx_outbox_status_time` (`status_code`, `not_before`, `id`),
    -- 顺序/分区发布与回放（按 channel + partition_key 管控并发或保序）
    KEY `idx_outbox_partition` (`channel`, `partition_key`, `status_code`),
    -- 发布器租约回收（多 Relay 并行时用于回收过期租约）
    KEY `idx_outbox_lease` (`status_code`, `pub_leased_until`),
    -- 归档/对账便利
    KEY `idx_outbox_created` (`created_at`),
    KEY `idx_outbox_deleted_upd` (`deleted`, `updated_at`)
)
    ENGINE = InnoDB;
