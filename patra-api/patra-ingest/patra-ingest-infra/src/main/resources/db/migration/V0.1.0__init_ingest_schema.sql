-- =====================================================================
-- B. 采集（ingest）—— ing_*
-- =====================================================================

-- ============================================================================
-- 1) 计划表：表达式哈希 + 日期范围
-- ============================================================================
CREATE TABLE IF NOT EXISTS `ing_plan`
(
    `id`                 BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键',

    `plan_key`           VARCHAR(128) NOT NULL COMMENT '人类可读或外部幂等键（唯一）',
    `name`               VARCHAR(256) NOT NULL COMMENT '计划名称',
    `description`        VARCHAR(512) NULL COMMENT '计划说明',

    -- 表达式引用（Plan 全局 expr，slice 会派生自己的）
    `expr_hash`          CHAR(64)     NOT NULL COMMENT '表达式哈希：SHA-256(normalized_json)',
    `expr_snapshot_json` JSON         NULL COMMENT '规范化 AST 快照（只读）',

    -- 日期范围
    `date_from`          DATETIME(6)  NOT NULL COMMENT '起始时间（含）',
    `date_to`            DATETIME(6)  NOT NULL COMMENT '结束时间（含）',

    -- 状态机
    `status`             ENUM ('draft','ready','active','completed','aborted')
                                      NOT NULL COMMENT '计划状态',

    -- ==========================================================================
    -- 审计字段
    -- ==========================================================================
    `record_remarks`     JSON         NULL COMMENT '备注/变更说明 [{"time":"...","by":"...","note":"..."}]',
    `version`            BIGINT       NOT NULL DEFAULT 0,
    `ip_address`         VARBINARY(16)         DEFAULT NULL,
    `created_at`         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `created_by`         BIGINT       NULL,
    `created_by_name`    VARCHAR(64)  NULL,
    `updated_at`         TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `updated_by`         BIGINT       NULL,
    `updated_by_name`    VARCHAR(64)  NULL,
    `deleted`            TINYINT(1)   NOT NULL DEFAULT 0,

    CONSTRAINT `uk_ing_plan_key` UNIQUE KEY (`plan_key`),
    KEY `idx_ing_plan_status` (`status`),
    KEY `idx_ing_plan_time` (`date_from`, `date_to`),
    KEY `idx_ing_plan_expr` (`expr_hash`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Ingest-计划：表达式以hash引用，仅限定日期范围；不含分页';


-- ============================================================================
-- 2) 计划切片表：继承 Plan，但也有自己的 expr（Plan expr + 日期子集）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `ing_plan_slice`
(
    `id`                 BIGINT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `plan_id`            BIGINT UNSIGNED NOT NULL COMMENT '计划ID',

    `slice_no`           INT             NOT NULL COMMENT '切片序号（0..N）',
    `slice_from`         DATETIME(6)     NOT NULL COMMENT '切片起始（含）',
    `slice_to`           DATETIME(6)     NOT NULL COMMENT '切片结束（含）',

    -- 每个切片派生自己的 expr（即 plan.expr + 日期条件）
    `expr_hash`          CHAR(64)        NOT NULL COMMENT '切片表达式哈希（派生+局部化）',
    `expr_snapshot_json` JSON            NULL COMMENT '切片专属 expr 快照（含局部化的时间条件）',

    -- 状态机
    `status`             ENUM ('pending','dispatched','executing','succeeded','failed','partial')
                                         NOT NULL COMMENT '切片状态',

    -- 执行期回填
    `last_job_id`        BIGINT          NULL COMMENT '最近一次Job ID（冗余）',
    `total_batches`      INT             NULL COMMENT '该切片产生的分页批次数（回填）',
    `total_hits`         BIGINT          NULL COMMENT '该切片累计命中/写入条数（回填）',
    `error_count`        INT             NULL COMMENT '失败批次数（回填）',

    -- ==========================================================================
    -- 审计字段
    -- ==========================================================================
    `record_remarks`     JSON            NULL,
    `version`            BIGINT          NOT NULL DEFAULT 0,
    `ip_address`         VARBINARY(16)            DEFAULT NULL,
    `created_at`         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    `created_by`         BIGINT          NULL,
    `created_by_name`    VARCHAR(64)     NULL,
    `updated_at`         TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    `updated_by`         BIGINT          NULL,
    `updated_by_name`    VARCHAR(64)     NULL,
    `deleted`            TINYINT(1)      NOT NULL DEFAULT 0,

    CONSTRAINT `fk_ing_slice_plan`
        FOREIGN KEY (`plan_id`) REFERENCES `ing_plan` (`id`)
            ON UPDATE RESTRICT ON DELETE RESTRICT,

    CONSTRAINT `uk_ing_slice_unique` UNIQUE KEY (`plan_id`, `slice_no`),
    KEY `idx_ing_slice_status` (`status`),
    KEY `idx_ing_slice_time` (`slice_from`, `slice_to`),
    KEY `idx_ing_slice_expr` (`expr_hash`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='Ingest-计划切片：每个切片有局部化的 expr（带时间范围），可独立执行';


CREATE TABLE IF NOT EXISTS `ing_job`
(
    -- ======================================================================
    -- 主键
    -- ======================================================================
    `id`                       BIGINT UNSIGNED  NOT NULL COMMENT 'PK · Snowflake（作业ID）',

    -- ======================================================================
    -- 计划/切片 关联（为兼容历史作业，这里不加外键；如需外键，请统一两边有/无符号）
    -- ======================================================================
    `plan_id`                  BIGINT           NULL COMMENT '计划ID · ing_plan.id（同域，便于看板聚合）',
    `slice_id`                 BIGINT           NULL COMMENT '切片ID · ing_plan_slice.id（同域，调度单元）',

    -- ======================================================================
    -- 采集来源与类型
    -- ======================================================================
    `literature_provenance_id` BIGINT UNSIGNED  NOT NULL COMMENT '来源渠道ID · reg_literature_provenance.id',
    `job_type`                 ENUM ('harvest','backfill','update','metrics')
                                                NOT NULL COMMENT '任务类型：harvest=采集, backfill=回填, update=增量/更新, metrics=外部计量',
    `trigger_type`             ENUM ('manual','schedule','replay')
                                                NOT NULL DEFAULT 'manual' COMMENT '触发来源：manual=手工, schedule=定时, replay=重放',
    `api_credential_id`        BIGINT UNSIGNED  NULL COMMENT 'API 凭据ID · conn_api_credential.id（执行期使用的密钥/账号）',

    -- ======================================================================
    -- 运行参数 / 幂等
    -- ======================================================================
    `params`                   JSON             NULL COMMENT '任务参数；例：{"since":"2024-01-01","query":"metformin"}。入幂等前需做规范化（排序/去空/标准化时间）',
    `job_key`                  VARCHAR(128)     NULL COMMENT '（兼容旧）幂等键：如 literature_provenance_id + job_type + normalized(params) 的哈希',
    `idempotent_key`           CHAR(64)         NULL COMMENT '（推荐）强幂等键：SHA256(slice_id + expr_hash + job_type + trigger_type + normalized(params))；唯一',
    `expr_hash`                CHAR(64)         NULL COMMENT '表达式指纹（冗余自切片），用于快速聚合/排查',

    -- ======================================================================
    -- 调度/执行状态
    -- ======================================================================
    `priority`                 TINYINT UNSIGNED NOT NULL DEFAULT 5 COMMENT '调度优先级（1高→9低）',
    `status`                   ENUM ('queued','running','succeeded','failed','cancelled')
                                                NOT NULL DEFAULT 'queued' COMMENT '任务状态：queued=排队, running=执行中, succeeded=成功, failed=失败, cancelled=取消',
    `scheduled_at`             TIMESTAMP(6)     NULL COMMENT '计划开始时间（被调度器安排的时间）',
    `started_at`               TIMESTAMP(6)     NULL COMMENT '实际开始时间',
    `finished_at`              TIMESTAMP(6)     NULL COMMENT '结束时间',
    `last_heartbeat`           TIMESTAMP(6)     NULL COMMENT '运行心跳（健康探测/超时判断）',

    -- ======================================================================
    -- 关联/追踪
    -- ======================================================================
    `parent_job_id`            BIGINT UNSIGNED  NULL COMMENT '父任务ID（批/重试/分解任务的父子串联）',
    `scheduler_run_id`         VARCHAR(64)      NULL COMMENT '外部调度运行ID/trace（如对接 XXL-Job 的日志ID）',
    `correlation_id`           VARCHAR(64)      NULL COMMENT '关联ID/TraceId（贯穿链路追踪）',

    -- ======================================================================
    -- 错误与备注
    -- ======================================================================
    `error`                    TEXT             NULL COMMENT '失败原因/异常堆栈（必要时截断/外溢到对象存储）',

    -- ======================================================================
    -- 审计字段（与项目统一规范保持一致）
    -- ======================================================================
    `record_remarks`           JSON             NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `ip_address`               VARBINARY(16)             DEFAULT NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `created_at`               TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`               BIGINT UNSIGNED  NULL COMMENT '创建人ID',
    `created_by_name`          VARCHAR(100)     NULL COMMENT '创建人姓名',
    `updated_at`               TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`               BIGINT UNSIGNED  NULL COMMENT '更新人ID',
    `updated_by_name`          VARCHAR(100)     NULL COMMENT '更新人姓名',
    `version`                  BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `deleted`                  TINYINT(1)       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    -- ======================================================================
    -- 约束与索引
    -- ======================================================================
    PRIMARY KEY (`id`),

    -- 幂等唯一键：先兼容旧 job_key，再引入强幂等 idempotent_key（两者均可为空，应用层控制写入其一）
    UNIQUE KEY `uk_job_key` (`job_key`),
    UNIQUE KEY `uk_idempotent_key` (`idempotent_key`),

    -- 调度/看板常用查询
    KEY `idx_plan_status` (`plan_id`, `status`, `scheduled_at`),
    KEY `idx_slice_status_started` (`slice_id`, `status`, `started_at`),
    KEY `idx_ingjob_source_status` (`literature_provenance_id`, `status`, `started_at`),
    KEY `idx_status_sched` (`status`, `scheduled_at`),

    -- 追踪与父子任务
    KEY `idx_parent` (`parent_job_id`),
    KEY `idx_expr_hash` (`expr_hash`),

    -- 凭据/来源
    KEY `idx_job_cred` (`api_credential_id`)
)
    ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    ROW_FORMAT = DYNAMIC
    COMMENT ='采集任务（与调度/看板联动；可绑定计划/切片；支持强幂等与执行追踪）';

-- 运行台账 · 窗口级（ing_run）
CREATE TABLE IF NOT EXISTS `ing_run`
(
    `id`               bigint unsigned                                                                          not null comment 'PK · Snowflake',
    `job_id`           bigint unsigned                                                                          not null comment '关联 ing_job.id',
    `cursor_key`       varchar(64)                                                                              not null comment '游标键（与窗口组合唯一标识一次运行）',
    `window_from`      TIMESTAMP(6)                                                                             not null comment '窗口起（含）',
    `window_to`        TIMESTAMP(6)                                                                             not null comment '窗口止（不含）',
    `attempt_no`       int unsigned                                                default 1                    not null comment '同一窗口第几次尝试（1 起）',

    `status`           enum ('planned','running','succeeded','failed','cancelled') default 'planned'            not null comment '运行状态',
    `checkpoint`       json                                                                                     null comment '分页/令牌检查点（retstart/cursor/ cursorMark/ resumptionToken 等）',
    `stats`            json                                                                                     null comment '统计：{"fetched":...,"upserted":...,"failed":...,"pages":...}',
    `error`            text                                                                                     null comment '失败原因/异常堆栈',

    `started_at`       TIMESTAMP(6)                                                                             null comment '开始时间',
    `finished_at`      TIMESTAMP(6)                                                                             null comment '结束时间',
    `last_heartbeat`   TIMESTAMP(6)                                                                             null comment '心跳时间（超时判定）',
    `scheduler_run_id` varchar(64)                                                                              null comment '调度运行ID/trace（对接 XXL-Job 等）',
    `correlation_id`   varchar(64)                                                                              null comment 'CID/TraceId（关联日志）',

    `record_remarks`   json                                                                                     null comment 'json数组，备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',

    -- BaseDO（与既有表保持一致）
    `created_at`       TIMESTAMP(6)                                                default CURRENT_TIMESTAMP(6) not null comment '创建时间',
    `created_by`       bigint unsigned                                                                          null comment '创建人ID',
    `created_by_name`  varchar(100)                                                                             null comment '创建人姓名',
    `updated_at`       TIMESTAMP(6)                                                default CURRENT_TIMESTAMP(6) not null on update CURRENT_TIMESTAMP(6) comment '更新时间',
    `updated_by`       bigint unsigned                                                                          null comment '更新人ID',
    `updated_by_name`  varchar(100)                                                                             null comment '更新人姓名',
    `version`          bigint unsigned                                             default 0                    not null comment '乐观锁版本号',
    `ip_address`       varbinary(16)                                                                            null comment '请求方 IP（二进制，支持 IPv4/IPv6）',
    `deleted`          tinyint(1)                                                  default 0                    not null comment '逻辑删除',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_run_job_window_attempt` (`job_id`, `cursor_key`, `window_from`, `window_to`, `attempt_no`),
    KEY `idx_run_job_status` (`job_id`, `status`, `started_at`),
    KEY `idx_run_cursor_window` (`cursor_key`, `window_from`)
)
    ENGINE = InnoDB
    ROW_FORMAT = DYNAMIC
    COMMENT ='运行台账（窗口级）：记录每个窗口的尝试、检查点与统计';
-- ======================================================================
-- 运行台账 · 批次级（ing_run_batch）
-- 说明：
--  - 支持两类分页：页码(offset/limit) 与 游标(token)
--  - 幂等：idempotent_key / (run_id,before_token) / (run_id,batch_no)
--  - 谱系冗余：plan_id/slice_id/job_id/expr_hash，便于一跳回溯
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_run_batch`
(
    `id`              BIGINT UNSIGNED                                 NOT NULL COMMENT 'PK · Snowflake',
    `run_id`          BIGINT UNSIGNED                                 NOT NULL COMMENT '关联 ing_run.id（同域）',

    -- 谱系冗余（不加外键，避免有/无符号不一致；由应用维护）
    `job_id`          BIGINT UNSIGNED                                 NULL COMMENT '关联 ing_job.id（冗余）',
    `slice_id`        BIGINT                                          NULL COMMENT '关联 ing_plan_slice.id（冗余）',
    `plan_id`         BIGINT                                          NULL COMMENT '关联 ing_plan.id（冗余）',
    `expr_hash`       CHAR(64)                                        NULL COMMENT '执行表达式哈希（冗余）',

    -- 批次/分页
    `batch_no`        INT UNSIGNED                                    NOT NULL DEFAULT 1 COMMENT '批次序号（1 起，连续）',
    `page_no`         INT UNSIGNED                                    NULL COMMENT '页码（offset/limit 分页，从 1 开始；token 分页为空）',
    `page_size`       INT UNSIGNED                                    NULL COMMENT '页大小（便于核对与统计）',

    -- 分页/令牌边界
    `before_token`    VARCHAR(512)                                   NULL COMMENT '本批开始令牌/位置（如 retstart/cursorMark 等）',
    `after_token`     VARCHAR(512)                                   NULL COMMENT '本批结束令牌/下一位置（next-cursor/nextCursorMark 等）',

    -- 幂等与统计
    `idempotent_key`  CHAR(64)                                        NULL COMMENT '批次幂等键：如 SHA256(run_id + before_token | page_no)',
    `record_count`    INT UNSIGNED                                    NOT NULL DEFAULT 0 COMMENT '本批处理记录数',
    `status`          ENUM ('running','succeeded','failed','skipped') NOT NULL DEFAULT 'running' COMMENT '批次状态',
    `committed_at`    TIMESTAMP(6)                                    NULL COMMENT '批次提交时间',
    `error`           TEXT                                            NULL COMMENT '失败原因/异常信息（必要时截断）',
    `stats`           JSON                                            NULL COMMENT '扩展统计：去重数/重试数/耗时/请求数等',

    -- 备注与审计（与你项目统一）
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

    -- 去重/幂等
    UNIQUE KEY `uk_run_batch_no` (`run_id`, `batch_no`),         -- 保证批次序号唯一
    UNIQUE KEY `uk_run_batch_before` (`run_id`, `before_token`), -- token 分页防重（before_token 为空则不冲突）
    UNIQUE KEY `uk_run_batch_idem` (`idempotent_key`),           -- 通用幂等键

    -- 恢复/观测/回溯
    KEY `idx_runbatch_after_token` (`run_id`, `after_token`),
    KEY `idx_runbatch_status_time` (`run_id`, `status`, `committed_at`),
    KEY `idx_runbatch_job` (`job_id`, `status`, `committed_at`),
    KEY `idx_runbatch_slice` (`slice_id`, `status`, `committed_at`),
    KEY `idx_runbatch_plan` (`plan_id`, `status`, `committed_at`),
    KEY `idx_runbatch_expr` (`expr_hash`)
)
    ENGINE = InnoDB
    ROW_FORMAT = DYNAMIC
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='运行台账（批次级）：分页/令牌边界、幂等与执行结果（含谱系冗余）';


CREATE TABLE IF NOT EXISTS `ing_cursor`
(
    `id`                       BIGINT UNSIGNED NOT NULL COMMENT 'PK · Snowflake',

    -- 来源与游标键
    `literature_provenance_id` BIGINT UNSIGNED NOT NULL COMMENT '来源渠道ID · reg_literature_provenance.id',
    `cursor_key`               VARCHAR(64)     NOT NULL COMMENT '游标键（例如 publish_time / cursorMark / offset 等）',

    -- 游标值与上下文
    `cursor_value`             VARCHAR(1024)   NOT NULL COMMENT '游标值（时间戳/偏移/令牌等，存储最新有效值）',
    `cursor_meta`              JSON            NULL COMMENT '附加上下文（如命中计数、lastId、窗口统计、请求快照等）',

    -- 冗余/可选（便于快速追溯）
    `job_id`                   BIGINT UNSIGNED NULL COMMENT '最后一次推进该游标的作业ID · ing_job.id',
    `plan_id`                  BIGINT          NULL COMMENT '冗余 · ing_plan.id',
    `slice_id`                 BIGINT          NULL COMMENT '冗余 · ing_plan_slice.id',
    `expr_hash`                CHAR(64)        NULL COMMENT '推进时使用的表达式哈希（冗余）',

    -- 审计字段（与你项目保持一致）
    `record_remarks`           JSON            NULL COMMENT 'json数组，备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`               TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`               BIGINT UNSIGNED          DEFAULT NULL COMMENT '创建人ID',
    `created_by_name`          VARCHAR(100)             DEFAULT NULL COMMENT '创建人姓名',
    `updated_at`               TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`               BIGINT UNSIGNED          DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name`          VARCHAR(100)             DEFAULT NULL COMMENT '更新人姓名',
    `version`                  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`               VARBINARY(16)            DEFAULT NULL COMMENT '请求方 IP(二进制,支持 IPv4/IPv6)',
    `deleted`                  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),

    -- 保证每个来源+游标键唯一，便于幂等推进
    UNIQUE KEY `uk_cursor_src_key` (`literature_provenance_id`, `cursor_key`),

    -- 常用查询索引
    KEY `idx_cursor_job` (`job_id`),
    KEY `idx_cursor_plan` (`plan_id`, `slice_id`)
) ENGINE = InnoDB
  ROW_FORMAT = DYNAMIC
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='采集游标（进度表）：记录每个来源的最新游标值；配合 ing_cursor_history 形成“当前值 + 历史轨迹”';



-- ======================================================================
-- 游标推进历史（ing_cursor_history）
-- 说明：
--  - 记录每次成功推进的前后值与覆盖窗口，便于审计/回放
--  - 惟一性提升到 (provenance, cursor_key, window_from, window_to)
--  - 冗余 plan/slice/run/batch/expr，排查直达
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_cursor_history`
(
    `id`                       BIGINT UNSIGNED             NOT NULL COMMENT 'PK · Snowflake',
    `literature_provenance_id` BIGINT UNSIGNED             NOT NULL COMMENT '来源渠道ID · reg_literature_provenance.id',
    `cursor_key`               VARCHAR(64)                 NOT NULL COMMENT '游标键（如 publish_time / cursorMark / seq_id 等）',

    `prev_value`               VARCHAR(1024)               NULL COMMENT '推进前的值',
    `new_value`                VARCHAR(1024)               NOT NULL COMMENT '推进后的值',
    `observed_max`             VARCHAR(1024)               NULL COMMENT '本次观测到的最大边界（如 max(publish_date) 或最后 cursorMark）',

    `job_id`                   BIGINT UNSIGNED             NULL COMMENT '由哪个作业推进 · ing_job.id',
    `plan_id`                  BIGINT                      NULL COMMENT '冗余 · ing_plan.id',
    `slice_id`                 BIGINT                      NULL COMMENT '冗余 · ing_plan_slice.id',
    `run_id`                   BIGINT UNSIGNED             NULL COMMENT '冗余 · ing_run.id',
    `batch_id`                 BIGINT UNSIGNED             NULL COMMENT '冗余 · ing_run_batch.id',
    `expr_hash`                CHAR(64)                    NULL COMMENT '推进时使用的表达式哈希',

    `direction`                ENUM ('forward','backfill') NULL COMMENT '推进方向：forward=前进增量, backfill=历史回灌',
    `cursor_type`              VARCHAR(64)                 NULL COMMENT '游标类型：time/id/token 等',

    `window_from`              DATETIME(6)                 NULL COMMENT '本次推进覆盖的窗口起(UTC)',
    `window_to`                DATETIME(6)                 NULL COMMENT '本次推进覆盖的窗口止(UTC)',

    -- 备注与审计（与你项目统一）
    `record_remarks`           JSON                        NULL COMMENT 'json数组,备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`               TIMESTAMP(6)                NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`               BIGINT UNSIGNED                      DEFAULT NULL COMMENT '创建人ID',
    `created_by_name`          VARCHAR(100)                         DEFAULT NULL COMMENT '创建人姓名',
    `updated_at`               TIMESTAMP(6)                NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`               BIGINT UNSIGNED                      DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name`          VARCHAR(100)                         DEFAULT NULL COMMENT '更新人姓名',
    `version`                  BIGINT UNSIGNED             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`               VARBINARY(16)                        DEFAULT NULL COMMENT '请求方IP(二进制,支持 IPv4/IPv6)',
    `deleted`                  TINYINT(1)                  NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),

    -- 同一来源、同一游标键、同一窗口的不重复推进
    UNIQUE KEY `uk_curh_src_key_window` (`literature_provenance_id`, `cursor_key`, `window_from`, `window_to`),

    -- 查询常用：按来源+键+时间倒序查看推进历史
    KEY `idx_curh_src_key_time` (`literature_provenance_id`, `cursor_key`, `created_at`)
)
    ENGINE = InnoDB
    ROW_FORMAT = DYNAMIC
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='游标推进历史：记录每次推进的前后值、覆盖窗口与谱系信息（审计/回放）';

CREATE TABLE IF NOT EXISTS `ing_field_provenance`
(
    `id`               BIGINT UNSIGNED NOT NULL COMMENT 'PK · Snowflake',

    -- 目标对象与字段
    `work_id`          BIGINT UNSIGNED NOT NULL COMMENT '· cat_work.id（被赋值的作品/记录）',
    `field_name`       VARCHAR(64)     NOT NULL COMMENT '字段名；如 title、abstract、license_info.spdx',

    -- 溯源来源
    `source_hit_id`    BIGINT UNSIGNED NOT NULL COMMENT '· ing_source_hit.id（该字段值来自哪次命中）',

    -- 值与规范化
    `value_hash`       CHAR(64)        NOT NULL COMMENT '字段值哈希（建议对“规范化后的值”计算，便于变更检测）',
    `normalize_schema` VARCHAR(32)              DEFAULT NULL COMMENT '规范化/取值规则版本（如 ast-v1 / norm-2025.09）',
    `value_json`       JSON                     DEFAULT NULL COMMENT '（可选）字段值快照：结构化/规范化后的值',
    `value_preview`    VARCHAR(256)             DEFAULT NULL COMMENT '（可选）预览/摘要，便于界面检索与对账',
    `collected_at`     TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '字段取值时间（UTC）',

    -- 执行谱系（冗余，可空；排查溯源一跳到位）
    `job_id`           BIGINT UNSIGNED          DEFAULT NULL COMMENT '· ing_job.id（执行该赋值的作业）',
    `plan_id`          BIGINT                   DEFAULT NULL COMMENT '· ing_plan.id（冗余）',
    `slice_id`         BIGINT                   DEFAULT NULL COMMENT '· ing_plan_slice.id（冗余）',
    `run_id`           BIGINT UNSIGNED          DEFAULT NULL COMMENT '· ing_run.id（冗余）',
    `batch_id`         BIGINT UNSIGNED          DEFAULT NULL COMMENT '· ing_run_batch.id（冗余）',
    `expr_hash`        CHAR(64)                 DEFAULT NULL COMMENT '执行时使用的表达式哈希（冗余）',

    -- 备注与审计（与你项目统一）
    `record_remarks`   JSON                     DEFAULT NULL COMMENT 'json数组，备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`       BIGINT UNSIGNED          DEFAULT NULL COMMENT '创建人ID',
    `created_by_name`  VARCHAR(100)             DEFAULT NULL COMMENT '创建人姓名',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`       BIGINT UNSIGNED          DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name`  VARCHAR(100)             DEFAULT NULL COMMENT '更新人姓名',
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`       VARBINARY(16)            DEFAULT NULL COMMENT '请求方 IP（二进制，支持 IPv4/IPv6）',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),

    -- 防重：同一作品+字段+来源命中+相同值哈希 不应重复
    UNIQUE KEY `uk_prov_work_field_src_hash` (`work_id`, `field_name`, `source_hit_id`, `value_hash`),

    -- 常用查询：按作品+字段查最近一次取值
    KEY `idx_prov_work_field_latest` (`work_id`, `field_name`, `collected_at`),

    -- 原有索引保留（可选保留或替换为上面的更强索引）
    KEY `idx_prov_source` (`source_hit_id`),

    -- 追溯加速
    KEY `idx_prov_job` (`job_id`),
    KEY `idx_prov_plan` (`plan_id`, `slice_id`),
    KEY `idx_prov_run` (`run_id`, `batch_id`),
    KEY `idx_prov_expr` (`expr_hash`)
)
    ENGINE = InnoDB
    ROW_FORMAT = DYNAMIC
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='字段级溯源（谁在何时给了什么值）：含谱系冗余、防重唯一键与可选值快照';

CREATE TABLE IF NOT EXISTS `ing_metric_snapshot`
(
    `id`              BIGINT UNSIGNED NOT NULL COMMENT 'PK · Snowflake',

    -- 指标对象与类型
    `work_id`         BIGINT UNSIGNED NOT NULL COMMENT '· cat_work.id',
    `metric_type`     ENUM (
        'cited_by_crossref','cited_by_opencitations','cited_by_scopus',
        'pmc_views','pmc_downloads',
        'mendeley_readers','twitter','news','policy_mentions','blogs'
        )                             NOT NULL COMMENT '指标类型',
    `source`          VARCHAR(32)     NOT NULL COMMENT '指标来源（提供方/渠道标识）',

    -- 指标值（当前快照）
    `value`           BIGINT UNSIGNED NOT NULL COMMENT '指标值（>=0，单点快照）',
    `value_delta`     BIGINT                   DEFAULT NULL COMMENT '（可选）相对上次快照的变化量（可为负/正/零）',
    `unit`            VARCHAR(16)              DEFAULT 'count' COMMENT '（可选）单位：count/reads/views 等',

    `collected_at`    TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '采集时间（快照点，UTC）',

    -- 执行谱系（冗余，可空；便于回溯排查）
    `job_id`          BIGINT UNSIGNED          DEFAULT NULL COMMENT '· ing_job.id（产生本快照的作业）',
    `plan_id`         BIGINT                   DEFAULT NULL COMMENT '· ing_plan.id（冗余）',
    `slice_id`        BIGINT                   DEFAULT NULL COMMENT '· ing_plan_slice.id（冗余）',
    `run_id`          BIGINT UNSIGNED          DEFAULT NULL COMMENT '· ing_run.id（冗余）',
    `batch_id`        BIGINT UNSIGNED          DEFAULT NULL COMMENT '· ing_run_batch.id（冗余）',
    `expr_hash`       CHAR(64)                 DEFAULT NULL COMMENT '表达式指纹（冗余）',

    -- 备注与审计
    `record_remarks`  JSON                     DEFAULT NULL COMMENT 'json数组，备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`      BIGINT UNSIGNED          DEFAULT NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100)             DEFAULT NULL COMMENT '创建人姓名',
    `updated_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`      BIGINT UNSIGNED          DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100)             DEFAULT NULL COMMENT '更新人姓名',
    `version`         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`      VARBINARY(16)            DEFAULT NULL COMMENT '请求方 IP（二进制，支持 IPv4/IPv6）',
    `deleted`         TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    -- 约束
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_ims_value_nonneg` CHECK (`value` >= 0),

    -- 唯一键：同一作品+指标类型+来源+采集时刻 只存一条快照
    UNIQUE KEY `uk_ims_work_type_src_time` (`work_id`, `metric_type`, `source`, `collected_at`),

    -- 常用查询索引：查最新/按时间范围聚合/按谱系回溯
    KEY `idx_ims_latest` (`work_id`, `metric_type`, `source`, `collected_at`),
    KEY `idx_ims_job` (`job_id`),
    KEY `idx_ims_plan` (`plan_id`, `slice_id`),
    KEY `idx_ims_run` (`run_id`, `batch_id`),
    KEY `idx_ims_expr` (`expr_hash`)
)
    ENGINE = InnoDB
    ROW_FORMAT = DYNAMIC
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='外部计量指标快照：记录某作品在某来源/类型下的单点指标值（含谱系冗余，防重复快照）';


CREATE TABLE IF NOT EXISTS `ing_source_hit`
(
    `id`                       BIGINT UNSIGNED NOT NULL COMMENT 'PK · Snowflake',

    -- 命中归并后的作品（若你有“待归并队列”，此列也可改为 NULL，待成功归并后回填）
    `work_id`                  BIGINT UNSIGNED NOT NULL COMMENT '· cat_work.id（命中归并后对应的作品）',

    -- 来源与源端标识
    `literature_provenance_id` BIGINT UNSIGNED NOT NULL COMMENT '· reg_literature_provenance.id',
    `source_specific_id`       VARCHAR(200)    NOT NULL COMMENT '源端唯一ID（PMID/DOI等）',

    -- 拉取与来源侧时间
    `retrieved_at`             TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '命中/拉取时间（本地采集时间，UTC）',
    `source_updated_at`        DATETIME(6)              DEFAULT NULL COMMENT '源端声称的更新时间（若API提供）',

    -- 原始数据与签名
    `raw_schema_version`       VARCHAR(32)              DEFAULT NULL COMMENT '原始结构/解析版本（以应对解析器升级）',
    `raw_data_json`            JSON            NULL COMMENT '原始返回或解析后 JSON（便于重放/差分）',
    `raw_data_hash`            CHAR(64)                 DEFAULT NULL COMMENT '对标准化后的原始JSON计算的SHA-256（去空白/字段排序后）',

    -- 执行谱系（冗余，可空；便于一跳回溯）
    `job_id`                   BIGINT UNSIGNED          DEFAULT NULL COMMENT '· ing_job.id（产生本命中的作业）',
    `plan_id`                  BIGINT                   DEFAULT NULL COMMENT '· ing_plan.id（冗余）',
    `slice_id`                 BIGINT                   DEFAULT NULL COMMENT '· ing_plan_slice.id（冗余）',
    `run_id`                   BIGINT UNSIGNED          DEFAULT NULL COMMENT '· ing_run.id（冗余）',
    `batch_id`                 BIGINT UNSIGNED          DEFAULT NULL COMMENT '· ing_run_batch.id（冗余）',
    `expr_hash`                CHAR(64)                 DEFAULT NULL COMMENT '表达式指纹（冗余）',

    -- BaseDO（与你项目统一）
    `record_remarks`           JSON                     DEFAULT NULL COMMENT 'json数组，备注/变更说明 [{"time":"2025-08-18 15:00:00","by":"王五","note":"xxx"}]',
    `created_at`               TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间(UTC)',
    `created_by`               BIGINT UNSIGNED          DEFAULT NULL COMMENT '创建人ID',
    `created_by_name`          VARCHAR(100)             DEFAULT NULL COMMENT '创建人姓名',
    `updated_at`               TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间(UTC)',
    `updated_by`               BIGINT UNSIGNED          DEFAULT NULL COMMENT '更新人ID',
    `updated_by_name`          VARCHAR(100)             DEFAULT NULL COMMENT '更新人姓名',
    `version`                  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`               VARBINARY(16)            DEFAULT NULL COMMENT '请求方 IP（二进制，支持 IPv4/IPv6）',
    `deleted`                  TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=未删,1=已删',

    PRIMARY KEY (`id`),

    -- 防重策略：
    --  1) 允许同一 source_specific_id 在不同时间/不同payload 产生多条历史记录；
    --  2) 对“完全相同payload”的重复写，通过 raw_data_hash 去重；
    UNIQUE KEY `uk_sh_src_id_payload` (`literature_provenance_id`, `source_specific_id`, `raw_data_hash`),

    -- 常用查询：按（来源+源端ID）看时间序列；按作品看最近命中
    KEY `idx_sh_srcid_time` (`literature_provenance_id`, `source_specific_id`, `retrieved_at`),
    KEY `idx_sh_work_time` (`work_id`, `retrieved_at`),

    -- 按谱系一跳回溯
    KEY `idx_sh_job` (`job_id`),
    KEY `idx_sh_plan` (`plan_id`, `slice_id`),
    KEY `idx_sh_run` (`run_id`, `batch_id`),
    KEY `idx_sh_expr` (`expr_hash`)
)
    ENGINE = InnoDB
    ROW_FORMAT = DYNAMIC
    DEFAULT CHARSET = utf8mb4
    COLLATE = utf8mb4_0900_ai_ci
    COMMENT ='采集命中记录：允许同源ID的历史多快照；以原始payload哈希去重，并冗余谱系信息以便回溯';

