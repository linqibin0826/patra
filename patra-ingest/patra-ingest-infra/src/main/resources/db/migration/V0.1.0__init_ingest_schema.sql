-- B. Ingest 模块 —— ing_*
-- - 统一审计字段: record_remarks / created_at/created_by/created_by_name / updated_at/updated_by/updated_by_name / version / ip_address / deleted.
-- - 无物理外键 (跨/内模块完整性由应用层保证, 必要时创建辅助索引).
-- - MySQL 8.0 · InnoDB · utf8mb4_unicode_ci
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
CREATE TABLE IF NOT EXISTS `ing_schedule_instance`
(
    `id`                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 · 调度实例ID',
    `scheduler_code`    VARCHAR(32)     NOT NULL DEFAULT 'XXL' COMMENT 'DICT CODE(type=ing_scheduler): 调度器来源',
    `scheduler_job_id`  VARCHAR(64)     NULL COMMENT '外部 JobID (例如 XXL jobId)',
    `scheduler_log_id`  VARCHAR(64)     NULL COMMENT '外部运行/日志 ID (例如 XXL logId)',
    `trigger_type_code` VARCHAR(32)     NOT NULL DEFAULT 'SCHEDULE' COMMENT 'DICT CODE(type=ing_trigger_type): 触发类型',
    `triggered_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '触发时间 (UTC)',
    `trigger_params`    JSON            NULL COMMENT '触发参数 (归一化)',
    `provenance_code`   VARCHAR(64)     NOT NULL COMMENT 'Provenance 编码: 与 reg_provenance.provenance_code 对齐, 例如 pubmed/epmc/crossref',

    -- 审计字段
    `record_remarks`    JSON            NULL COMMENT 'JSON 数组, 备注/变更日志 [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`           BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`        VARBINARY(16)   NULL COMMENT '请求者IP (二进制, 支持 IPv4/IPv6)',
    `created_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`        BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`   VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`        TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC)',
    `updated_by`        BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`   VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted_at`        TIMESTAMP(6)    NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    PRIMARY KEY (`id`),
    KEY `idx_sched_src` (`scheduler_code`, `scheduler_job_id`, `scheduler_log_id`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='调度实例: 外部触发事件 (作为此编排的根); 无物理外键';


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
CREATE TABLE IF NOT EXISTS `ing_plan`
(
    `id`                         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 · 计划ID',
    `schedule_instance_id`       BIGINT UNSIGNED NOT NULL COMMENT '关联的调度实例',
    `plan_key`                   VARCHAR(128)    NOT NULL COMMENT '人类可读/外部幂等键 (唯一)',

    `provenance_code`            VARCHAR(64)     NULL COMMENT '冗余: Provenance 编码, 与 reg_provenance.provenance_code 对齐 (便于按来源聚合)',
    `operation_code`             VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation): 采集类型 HARVEST/BACKFILL/UPDATE/METRICS',

    `expr_proto_hash`            CHAR(64)        NOT NULL COMMENT '表达式原型哈希: 从"归一化原型 AST"计算的指纹; 用于幂等/快速比较; 与 expr_proto_snapshot 一对一',
    `expr_proto_snapshot`        JSON            NULL COMMENT '表达式原型快照 (AST, JSON): "全局表达式树"无任何分片/本地化条件; 用于重放和审计 (从此原型派生多个分片)',
    `provenance_config_snapshot` JSON            NULL COMMENT 'Provenance 配置快照 (中性模型, JSON): 将 reg_prov_* 认证/分页/时间窗口/限速/重试/批处理配置编译为执行不可变快照',
    `provenance_config_hash`     CHAR(64)        NULL COMMENT 'Provenance 配置快照哈希: 从归一化 provenance_config_snapshot 计算的指纹; 用于复用判断和变更检测',
    `slice_strategy_code`        VARCHAR(32)     NOT NULL COMMENT '分片策略: TIME/DATE/ID_RANGE/CURSOR_LANDMARK/VOLUME_BUDGET/HYBRID/SINGLE 等; 决定如何从原型生成多个分片',
    `slice_params`               JSON            NULL COMMENT '分片参数: 与分片策略匹配的详细信息 (例如步长、时区、地标、预算限制等); 仅用于生成分片, 不直接参与执行',

    `window_spec`                JSON            NOT NULL COMMENT '窗口边界规范 (嵌套 JSON). TIME/DATE: {"strategy":"TIME|DATE","window":{"from":"2024-01-01T00:00:00Z","to":"2024-12-31T23:59:59Z"}}; ID_RANGE: {"strategy":"ID_RANGE","window":{"from":1000000,"to":2000000}}; CURSOR_LANDMARK: {"strategy":"CURSOR_LANDMARK","window":{"from":"token1","to":"token2"}}; VOLUME_BUDGET: {"strategy":"VOLUME_BUDGET","limit":100000,"unit":"RECORDS"}; SINGLE: {"strategy":"SINGLE"}',
    -- TIME/DATE 策略查询优化的反规范化字段 (应用层维护)
    `window_from_ts`             TIMESTAMP(6)    NULL COMMENT '反规范化: TIME/DATE 策略窗口起始 (包含, UTC). 当 slice_strategy_code=TIME 或 DATE 时由应用层填充, 用于高效时间范围查询. 非时间策略为 NULL.',
    `window_to_ts`               TIMESTAMP(6)    NULL COMMENT '反规范化: TIME/DATE 策略窗口结束 (不包含, UTC). 当 slice_strategy_code=TIME 或 DATE 时由应用层填充, 用于高效时间范围查询. 非时间策略为 NULL.',

    `status_code`                VARCHAR(32)     NOT NULL DEFAULT 'DRAFT' COMMENT 'DICT CODE(type=ing_plan_status): DRAFT/SLICING/READY/ARCHIVED',

    -- 审计字段
    `record_remarks`             JSON            NULL COMMENT 'JSON 数组, 备注/变更日志 [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`                    BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`                 VARBINARY(16)   NULL COMMENT '请求者IP (二进制, 支持 IPv4/IPv6)',
    `created_at`                 TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`                 BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`            VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`                 TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC)',
    `updated_by`                 BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`            VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted_at`                 TIMESTAMP(6)    NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_plan_key` (`plan_key`),
    KEY `idx_plan_sched` (`schedule_instance_id`),
    KEY `idx_plan_prov_op` (`provenance_code`, `operation_code`),
    KEY `idx_plan_status` (`status_code`),
    KEY `idx_plan_expr` (`expr_proto_hash`),
    KEY `idx_plan_prov_config_hash` (`provenance_config_hash`),
    KEY `idx_window_time_range` (`window_from_ts`, `window_to_ts`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='计划蓝图: 定义总体窗口和分片策略 (表达式原型, 无本地化条件); 包含 provenance 配置哈希; 无物理外键';

-- ======================================================================
-- 3) 计划分片: 通用分片 (时间/ID/令牌/预算), 并行和幂等边界
-- ======================================================================
/* ====================================================================
 * 表: ing_plan_slice —— 计划分片
 * 语义: 从计划的总体窗口和策略切割的分片; 并行和幂等的最小单元; expr_* 是本地化表达式 (带边界)。
 * 要点: 在同一计划内, (slice_no) 和 (slice_signature_hash) 均唯一; 无 FK。
 * 索引: uk_slice_unique / uk_slice_sig / idx_slice_status / idx_slice_expr.
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_plan_slice`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 · 分片ID',
    `plan_id`              BIGINT UNSIGNED NOT NULL COMMENT '关联的计划',

    `provenance_code`      VARCHAR(64)     NULL COMMENT '冗余: Provenance 编码, 与 reg_provenance.provenance_code 对齐 (加速按来源过滤)',

    `slice_no`             INT             NOT NULL COMMENT '分片序号 (0..N)',
    `slice_signature_hash` CHAR(64)        NOT NULL COMMENT '分片签名哈希: 仅在 window_spec (边界 JSON) 归一化后计算; 用于加权/去权 (同一计划下相同边界不重复生成)',
    `window_spec`          JSON            NOT NULL COMMENT '此分片的窗口边界规范 (从 plan.window_spec 缩小). TIME/DATE: {"strategy":"TIME|DATE","window":{"from":"...","to":"..."}}; ID_RANGE: {"strategy":"ID_RANGE","window":{"from":N,"to":M}}; 其他类似 plan 表',
    `expr_hash`            CHAR(64)        NOT NULL COMMENT '本地化表达式哈希: 从"归一化本地化 AST"计算的指纹; 通常与 slice_signature_hash 一起变化',

    `expr_snapshot`        JSON            NULL COMMENT '本地化表达式快照 (AST, JSON): "可直接执行的表达式树",将此分片边界条件注入计划原型后得到; 分片携带重放语义',
    `status_code`          VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT 'DICT CODE(type=ing_slice_status): PENDING/ASSIGNED/FINISHED',

    -- 审计字段
    `record_remarks`       JSON            NULL COMMENT 'JSON 数组, 备注/变更日志 [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`           VARBINARY(16)   NULL COMMENT '请求者IP (二进制, 支持 IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`           BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC)',
    `updated_by`           BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted_at`           TIMESTAMP(6)    NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_slice_unique` (`plan_id`, `slice_no`),
    UNIQUE KEY `uk_slice_sig` (`plan_id`, `slice_signature_hash`),
    KEY `idx_slice_prov_status` (`provenance_code`, `status_code`),
    KEY `idx_slice_status` (`status_code`),
    KEY `idx_slice_expr` (`expr_hash`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='计划分片: 通用分片 (时间/ID/令牌/预算), 是并行和幂等的边界; 无物理外键';


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
 * 索引: uk_task_idem / uk_task_slice / idx_task_slice_status / idx_task_src_op / idx_task_sched_at / idx_task_queue.
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_task`
(
    `id`                   BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT COMMENT '主键 · 任务ID',

    `schedule_instance_id` BIGINT UNSIGNED  NOT NULL COMMENT '冗余调度实例, 便于聚合',
    `plan_id`              BIGINT UNSIGNED  NOT NULL,
    `slice_id`             BIGINT UNSIGNED  NOT NULL,

    `provenance_code`      VARCHAR(64)      NOT NULL COMMENT 'Provenance 编码: 与 reg_provenance.provenance_code 对齐',
    `operation_code`       VARCHAR(32)      NOT NULL COMMENT 'DICT CODE(type=ing_operation): 操作类型 HARVEST/BACKFILL/UPDATE/METRICS',

    `params`               JSON             NULL COMMENT '任务参数 (归一化)',
    `idempotent_key`       CHAR(64)         NOT NULL COMMENT 'SHA256(slice_signature + expr_hash + operation + trigger + normalized(params))',
    `expr_hash`            CHAR(64)         NOT NULL COMMENT '冗余: 执行表达式哈希',

    `priority`             TINYINT UNSIGNED NOT NULL DEFAULT 5 COMMENT '1=高→9=低',

    -- 任务租约 (续租/回收)
    `lease_owner`          VARCHAR(128)     NULL COMMENT '执行期间的租约持有者 (instance#thread)',
    `leased_until`         TIMESTAMP(6)     NULL COMMENT '租约过期时间 (UTC), 过期视为可回收',
    `lease_count`          INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '累计声明/续租次数 (用于监控/熔断)',
    `last_heartbeat_at`    TIMESTAMP(6)     NULL COMMENT '执行心跳时间',
    `retry_count`          INT UNSIGNED     NOT NULL DEFAULT 0 COMMENT '重试次数',
    `last_error_code`      VARCHAR(64)      NULL COMMENT '最新错误代码',
    `last_error_msg`       VARCHAR(512)     NULL COMMENT '最新错误消息',

    `status_code`          VARCHAR(32)      NOT NULL DEFAULT 'PENDING' COMMENT 'DICT CODE(type=ing_task_status): PENDING/QUEUED/RUNNING/SUCCEEDED/FAILED',
    `scheduled_at`         TIMESTAMP(6)     NULL COMMENT '计划开始时间',
    `started_at`           TIMESTAMP(6)     NULL COMMENT '实际开始时间',
    `finished_at`          TIMESTAMP(6)     NULL COMMENT '完成时间',

    `correlation_id`       VARCHAR(64)      NULL COMMENT '追踪/CID',

    -- 审计字段
    `record_remarks`       JSON             NULL COMMENT 'JSON 数组, 备注/变更日志 [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`              BIGINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`           VARBINARY(16)    NULL COMMENT '请求者IP (二进制, 支持 IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`           BIGINT UNSIGNED  NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)     NULL COMMENT '创建人姓名',
    `updated_at`           TIMESTAMP(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC)',
    `updated_by`           BIGINT UNSIGNED  NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)     NULL COMMENT '更新人姓名',
    `deleted_at`           TIMESTAMP(6)     NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    PRIMARY KEY (`id`),

    -- 幂等性和常用检索索引
    UNIQUE KEY `uk_task_idem` (`idempotent_key`),
    UNIQUE KEY `uk_task_slice` (`slice_id`),
    KEY `idx_task_slice_status` (`slice_id`, `status_code`),
    KEY `idx_task_src_op` (`provenance_code`, `operation_code`, `status_code`),
    KEY `idx_task_sched_at` (`status_code`, `scheduled_at`),

    -- 队列检索索引: 用于批量拉取待处理任务
    -- 建议查询: WHERE status_code='QUEUED' AND (leased_until IS NULL OR leased_until < NOW(6))
    -- 公平出队: priority ASC (数值越小优先) → scheduled_at ASC → id ASC
    KEY `idx_task_queue` (`status_code`, `leased_until`, `priority`, `scheduled_at`, `id`),

    -- 审计辅助索引
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='任务: 每个分片精确生成一个任务 (1:1 关系通过 uk_task_slice 强制); 支持强幂等性和调度/执行状态; 无物理外键';

-- ======================================================================
-- 5) 任务运行 (尝试): 一次具体的尝试; 失败的重试/重放各添加新记录
-- ======================================================================
/* ====================================================================
 * 表: ing_task_run —— 任务运行 (尝试)
 * 语义: 一次具体的尝试 (首次/重试/重放); 失败不覆盖任务, 仅记录在运行中。
 * 要点: 在同一任务内, (attempt_no) 唯一; 无 FK。
 * 索引: uk_run_attempt / idx_run_task_status.
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_task_run`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 · 运行ID',
    `task_id`          BIGINT UNSIGNED NOT NULL COMMENT '关联的任务',
    `attempt_no`       INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '尝试序号 (从 1 开始)',

    `provenance_code`  VARCHAR(64)     NULL COMMENT '冗余: Provenance 编码, 与 reg_provenance.provenance_code 对齐 (用于追踪和聚合)',
    `operation_code`   VARCHAR(32)     NULL COMMENT '冗余: 操作类型 (已在 task 中), 便于直接 (source,op,status) 统计',

    `status_code`      VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT 'DICT CODE(type=ing_task_run_status): PENDING/RUNNING/PARTIAL/SUCCEEDED/FAILED',
    `checkpoint`       JSON            NULL COMMENT '运行级检查点 (例如 nextHint / resumeToken 等)',
    `stats`            JSON            NULL COMMENT '统计信息: fetched/upserted/failed/pages 等',
    `error`            TEXT            NULL COMMENT '失败原因',

    `started_at`       TIMESTAMP(6)    NULL,
    `finished_at`      TIMESTAMP(6)    NULL,
    `last_heartbeat`   TIMESTAMP(6)    NULL,

    `correlation_id`   VARCHAR(64)     NULL,

    -- 审计字段
    `record_remarks`   JSON            NULL COMMENT 'JSON 数组, 备注/变更日志 [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`       VARBINARY(16)   NULL COMMENT '请求者IP (二进制, 支持 IPv4/IPv6)',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`       BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`  VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC)',
    `updated_by`       BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`  VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted_at`       TIMESTAMP(6)    NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_run_attempt` (`task_id`, `attempt_no`),
    KEY `idx_run_prov_op_status` (`provenance_code`, `operation_code`, `status_code`),
    KEY `idx_run_task_status` (`task_id`, `status_code`, `started_at`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='任务运行 (尝试): 一次具体的尝试; 失败的重试/重放各添加新记录; 无物理外键';

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
CREATE TABLE IF NOT EXISTS `ing_task_run_batch`
(
    `id`              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 · 批次ID',
    `run_id`          BIGINT UNSIGNED NOT NULL COMMENT '关联的运行',

    `task_id`         BIGINT UNSIGNED NULL COMMENT '冗余 · 任务',
    `slice_id`        BIGINT UNSIGNED NULL COMMENT '冗余 · 分片',
    `plan_id`         BIGINT UNSIGNED NULL COMMENT '冗余 · 计划',
    `expr_hash`       CHAR(64)        NULL COMMENT '冗余 · 执行表达式哈希',

    `provenance_code` VARCHAR(64)     NULL COMMENT '冗余: Provenance 编码 (来自 task/run 链)',
    `operation_code`  VARCHAR(32)     NULL COMMENT '冗余: 操作类型 (来自 task)',

    `batch_no`        INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '批次序号 (从 1 开始, 连续)',
    `page_no`         INT UNSIGNED    NULL COMMENT '页码 (offset/limit; token 分页时为 null)',
    `page_size`       INT UNSIGNED    NULL COMMENT '页大小',

    `before_token`    VARCHAR(512)    NULL COMMENT '批次起始令牌/位置 (retstart/cursorMark 等)',
    `after_token`     VARCHAR(512)    NULL COMMENT '批次结束令牌/下一个位置',

    `idempotent_key`  CHAR(64)        NOT NULL COMMENT 'SHA256(run_id + before_token | page_no)',
    `record_count`    INT UNSIGNED    NOT NULL DEFAULT 0,
    `status_code`     VARCHAR(32)     NOT NULL DEFAULT 'RUNNING' COMMENT 'DICT CODE(type=ing_batch_status): RUNNING/SUCCEEDED/FAILED/SKIPPED',
    `committed_at`    TIMESTAMP(6)    NULL,
    `error`           TEXT            NULL,
    `storage_key`     VARCHAR(512)    NULL COMMENT '批次负载的对象存储引用',
    `stats`           JSON            NULL,

    -- 审计字段
    `record_remarks`  JSON            NULL COMMENT 'JSON 数组, 备注/变更日志 [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`      VARBINARY(16)   NULL COMMENT '请求者IP (二进制, 支持 IPv4/IPv6)',
    `created_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`      BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name` VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC)',
    `updated_by`      BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name` VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted_at`      TIMESTAMP(6)    NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

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
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='运行批次: 页/令牌步进的最小核算; 携带检查点恢复和去重; 无物理外键';

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
CREATE TABLE IF NOT EXISTS `ing_cursor`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',

    `provenance_code`      VARCHAR(64)     NOT NULL COMMENT 'Provenance 编码: 与 reg_provenance.provenance_code 对齐',
    `operation_code`       VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation): HARVEST/BACKFILL/UPDATE/METRICS',
    `cursor_key`           VARCHAR(64)     NOT NULL COMMENT '游标键: updated_at/published_at/seq_id/cursor_token 等',
    `namespace_scope_code` VARCHAR(32)     NOT NULL DEFAULT 'GLOBAL' COMMENT 'DICT CODE(type=ing_namespace_scope): GLOBAL/EXPR/CUSTOM',
    `namespace_key`        CHAR(64)        NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000'
        COMMENT '命名空间键: expr_hash 或自定义哈希; global=全零',

    `cursor_type_code`     VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_cursor_type): TIME/ID/TOKEN',
    `cursor_value`         VARCHAR(1024)   NOT NULL COMMENT '当前有效游标值 (UTC ISO-8601 / 十进制字符串 / 不透明字符串)',
    `observed_max_value`   VARCHAR(1024)   NULL COMMENT '观察到的最大边界',

    `normalized_instant`   TIMESTAMP(6)    NULL COMMENT '当 cursor_type=time 时填充 (UTC)',
    `normalized_numeric`   DECIMAL(38, 0)  NULL COMMENT '当 cursor_type=id 时填充',

    `schedule_instance_id` BIGINT UNSIGNED NULL COMMENT '最近推进的调度实例',
    `plan_id`              BIGINT UNSIGNED NULL COMMENT '最近推进关联的计划',
    `slice_id`             BIGINT UNSIGNED NULL COMMENT '最近推进关联的分片',
    `task_id`              BIGINT UNSIGNED NULL COMMENT '最近推进关联的任务',
    `last_run_id`          BIGINT UNSIGNED NULL COMMENT '最近推进的运行',
    `last_batch_id`        BIGINT UNSIGNED NULL COMMENT '最近推进的批次',
    `expr_hash`            CHAR(64)        NULL COMMENT '最近推进使用的表达式哈希',

    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号 (仅允许向前推进)',

    -- 审计字段 (统一应用于此"当前值"表)
    `record_remarks`       JSON            NULL COMMENT 'JSON 数组, 备注/变更日志 [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `ip_address`           VARBINARY(16)   NULL COMMENT '请求者IP (二进制, 支持 IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`           BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC)',
    `updated_by`           BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted_at`           TIMESTAMP(6)    NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_cursor_ns` (`provenance_code`, `operation_code`, `cursor_key`, `namespace_scope_code`,
                               `namespace_key`),
    KEY `idx_cursor_src_key` (`provenance_code`, `operation_code`, `cursor_key`),
    KEY `idx_cursor_sort_time` (`cursor_type_code`, `normalized_instant`),
    KEY `idx_cursor_sort_id` (`cursor_type_code`, `normalized_numeric`),
    KEY `idx_cursor_lineage` (`schedule_instance_id`, `plan_id`, `slice_id`, `task_id`, `last_run_id`, `last_batch_id`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='通用游标·当前值: (source_code + operation + cursor_key + namespace) 唯一; 兼容 time/id/token; 无物理外键';

-- ======================================================================
-- 8) 游标推进事件 (不可变): 每次成功推进一条事件记录; 支持重放和全链路追踪
-- ======================================================================
/* ====================================================================
 * 表: ing_cursor_event —— 游标推进事件
 * 语义: 仅追加审计事件; 每次成功推进一条记录, 支持重放和全链路追踪。
 * 要点:
 *  - 幂等: idempotent_key 唯一;
 *  - 血缘: schedule/plan/slice/task/run/batch 逻辑关联 (无 FK)。
 * 索引: uk_cur_evt_idem / idx_cur_evt_timeline / idx_cur_evt_window / idx_cur_evt_instant / idx_cur_evt_numeric / idx_cur_evt_lineage.
 * ==================================================================== */
CREATE TABLE IF NOT EXISTS `ing_cursor_event`
(
    `id`                   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',

    `provenance_code`      VARCHAR(64)     NOT NULL,
    `operation_code`       VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_operation): HARVEST/BACKFILL/UPDATE/METRICS',
    `cursor_key`           VARCHAR(64)     NOT NULL,
    `namespace_scope_code` VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_namespace_scope): GLOBAL/EXPR/CUSTOM',
    `namespace_key`        CHAR(64)        NOT NULL DEFAULT '0000000000000000000000000000000000000000000000000000000000000000'
        COMMENT '命名空间键: expr_hash 或自定义哈希; global=全零',

    `cursor_type_code`     VARCHAR(32)     NOT NULL COMMENT 'DICT CODE(type=ing_cursor_type): TIME/ID/TOKEN',
    `prev_value`           VARCHAR(1024)   NULL,
    `new_value`            VARCHAR(1024)   NOT NULL,
    `observed_max_value`   VARCHAR(1024)   NULL,

    `prev_instant`         TIMESTAMP(6)    NULL,
    `new_instant`          TIMESTAMP(6)    NULL,
    `prev_numeric`         DECIMAL(38, 0)  NULL,
    `new_numeric`          DECIMAL(38, 0)  NULL,
    `window_from`          TIMESTAMP(6)    NULL COMMENT '覆盖窗口起始 (UTC, 包含)',
    `window_to`            TIMESTAMP(6)    NULL COMMENT '覆盖窗口结束 (UTC, 不包含)',

    `direction_code`       VARCHAR(16)     NULL COMMENT 'DICT CODE(type=ing_cursor_direction): FORWARD/BACKFILL',

    `idempotent_key`       CHAR(64)        NOT NULL COMMENT '事件幂等键: SHA256(source,op,key,ns_scope,ns_key,prev->new,ingestWindow,run_id,...)',

    `schedule_instance_id` BIGINT UNSIGNED NULL,
    `plan_id`              BIGINT UNSIGNED NULL,
    `slice_id`             BIGINT UNSIGNED NULL,
    `task_id`              BIGINT UNSIGNED NULL,
    `run_id`               BIGINT UNSIGNED NULL,
    `batch_id`             BIGINT UNSIGNED NULL,
    `expr_hash`            CHAR(64)        NULL,

    -- 审计字段 (事件表通常不可变; 如需统一治理保留下列字段)
    `record_remarks`       JSON            NULL COMMENT 'JSON 数组, 备注/变更日志 [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`              BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`           VARBINARY(16)   NULL COMMENT '请求者IP (二进制, 支持 IPv4/IPv6)',
    `created_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`           BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`      VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`           TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC)',
    `updated_by`           BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`      VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted_at`           TIMESTAMP(6)    NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    PRIMARY KEY (`id`),

    UNIQUE KEY `uk_cur_evt_idem` (`idempotent_key`),

    KEY `idx_cur_evt_timeline` (`provenance_code`, `operation_code`, `cursor_key`, `namespace_scope_code`,
                                `namespace_key`),
    KEY `idx_cur_evt_instant` (`cursor_type_code`, `new_instant`),
    KEY `idx_cur_evt_numeric` (`cursor_type_code`, `new_numeric`),
    KEY `idx_cur_evt_lineage` (`schedule_instance_id`, `plan_id`, `slice_id`, `task_id`, `run_id`, `batch_id`),
    KEY `idx_deleted_at` (`deleted_at`),
    KEY `idx_audit_created_by` (`created_by`),
    KEY `idx_audit_updated_by` (`updated_by`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='游标推进事件 (不可变): 每次成功推进一条事件记录; 支持重放和全链路追踪; 无物理外键';

-- ======================================================================
-- 表: ing_outbox_message —— 通用发件箱 (任务调度/集成事件等)
-- 语义: 与业务写入在同一事务中持久化; 由 Relay 扫描并投递到 MQ (例如 RocketMQ)。
-- 设计要点:
--  - (channel, dedup_key) 唯一, 确保源端幂等;
--  - 仅扫描此表进行发布, 非业务热表;
--  - partition_key 推荐使用 "provenance:operation" (按需扩展, 但不作为独立字段)。
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_outbox_message`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 · OutboxID',
    `aggregate_type`   VARCHAR(32)     NOT NULL COMMENT '聚合类型: 例如 TASK/PLAN/...; 用于审计和重放定位',
    `aggregate_id`     BIGINT UNSIGNED NOT NULL COMMENT '聚合根ID; 任务场景=ing_task.id',
    `channel`          VARCHAR(64)     NOT NULL COMMENT '逻辑通道=目标 Topic, 例如 ingest.task',
    `op_type`          VARCHAR(32)     NOT NULL COMMENT '业务语义标签: 例如 TASK_READY / EVENT_PUBLISHED',
    `partition_key`    VARCHAR(128)    NOT NULL COMMENT '分区/排序路由键; 推荐 "provenance:operation"; 用于有序投递或分区限速, 例如 PUBMED:HARVEST',
    `dedup_key`        VARCHAR(128)    NOT NULL COMMENT '幂等键; 任务=ing_task.idempotent_key; (channel, dedup_key) 唯一',
    `payload_json`     JSON            NOT NULL COMMENT '最小必要负载 (JSON): taskId/sliceKey/planKey/provenance/operation/endpoint/priority/notBefore 等; 大字段不入队',
    `headers_json`     JSON            NULL COMMENT '扩展头 (JSON): correlationId 等',
    `not_before`       TIMESTAMP(6)    NULL COMMENT '最早可发布时间 (UTC): NULL=任何时候可发布; 用于计划/延迟发布',
    `published_at`     TIMESTAMP(6)    NULL COMMENT '成功发布时间戳 (UTC), 当状态转换为 PUBLISHED 时设置',

    `status_code`      VARCHAR(16)     NOT NULL DEFAULT 'PENDING' COMMENT '发布状态: PENDING/PUBLISHING/PUBLISHED/FAILED/DEAD',
    `retry_count`      INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '发布重试次数 (失败时递增)',
    `next_retry_at`    TIMESTAMP(6)    NULL COMMENT '下次发布尝试时间 (UTC), 配合退避曲线使用',
    `error_code`       VARCHAR(64)     NULL COMMENT '最新发布错误代码',
    `error_msg`        VARCHAR(512)    NULL COMMENT '最新发布错误详情',

    `pub_lease_owner`  VARCHAR(128)    NULL COMMENT '发布者租约持有者 (实例ID或workerId), 防止同行并发发布',
    `pub_leased_until` TIMESTAMP(6)    NULL COMMENT '发布者租约过期时间 (UTC), 过期可被其他发布者接管',

    -- 审计字段
    `record_remarks`   JSON            NULL COMMENT 'JSON 数组, 备注/变更日志 [{"time":"2025-08-18 15:00:00","by":"John Doe","note":"xxx"}]',
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`       VARBINARY(16)   NULL COMMENT '请求者IP (二进制, 支持 IPv4/IPv6)',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`       BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`  VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC)',
    `updated_by`       BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`  VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted_at`       TIMESTAMP(6)    NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    PRIMARY KEY (`id`),

    -- 源端去重: dedup_key 在同一通道内必须唯一
    UNIQUE KEY `uk_outbox_channel_dedup` (`channel`, `dedup_key`),
    -- 轻量扫描索引: 按状态+时间游标批量发布
    KEY `idx_outbox_status_time` (`status_code`, `not_before`, `id`),
    -- 有序/分区发布和重放 (通过 channel + partition_key 控制并发或排序)
    KEY `idx_outbox_partition` (`channel`, `partition_key`, `status_code`),
    -- 发布者租约回收 (用于多个 Relay 并行运行时回收过期租约)
    KEY `idx_outbox_lease` (`status_code`, `pub_leased_until`),
    -- UNION ALL 查询模式优化索引 (V2.0 增强)
    KEY `idx_pending_relay` (`channel`, `status_code`, `not_before`, `id`) COMMENT '优化 PENDING 消息中继查询 (UNION ALL 第一子查询)',
    KEY `idx_publishing_lease` (`channel`, `status_code`, `pub_leased_until`, `id`) COMMENT '优化 PUBLISHING 过期租约消息查询 (UNION ALL 第二子查询)',
    -- 归档/对账便利
    KEY `idx_outbox_created` (`created_at`),
    KEY `idx_deleted_at` (`deleted_at`)
)
    ENGINE = InnoDB COMMENT ='发件箱: 通用出站消息表 (统一管理任务调度/集成事件; 与业务写入同一事务; 由 Relay 扫描并投递到 MQ)';

-- ======================================================================
-- 表: ing_outbox_relay_log —— Outbox 中继执行日志
-- 语义: 每次中继尝试的不可变审计跟踪; 追踪发布成功/失败、时序、错误和重试调度。
-- 设计要点:
--  - 每次中继尝试一条记录 (如果发生重试, 同一消息有多次尝试);
--  - 从 ing_outbox_message 反规范化 channel, partition_key 以提高查询效率;
--  - 支持故障排除 ("显示此消息的所有尝试")、批次级统计和监控/告警。
-- ======================================================================
CREATE TABLE IF NOT EXISTS `ing_outbox_relay_log`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键 · 中继日志ID',

    -- 核心引用
    `message_id`       BIGINT UNSIGNED NOT NULL COMMENT '引用 ing_outbox_message.id (类FK, 应用层强制)',
    `relay_batch_id`   VARCHAR(50)     NOT NULL COMMENT '中继批次标识符 (格式: yyyyMMddHHmmss-xxxxxxxx), 将来自同一作业执行的日志分组',

    -- 中继上下文 (为查询效率反规范化)
    `channel`          VARCHAR(64)     NOT NULL COMMENT '消息通道 (从 ing_outbox_message 反规范化以提高查询效率)',
    `partition_key`    VARCHAR(128)    NULL COMMENT '分区键 (从 ing_outbox_message 反规范化用于分析)',
    `lease_owner`      VARCHAR(128)    NULL COMMENT '租约持有者标识符 (格式: host-jobId-threadId-uuid)',
    `attempt_number`   INT UNSIGNED    NOT NULL COMMENT '此消息的尝试编号 (从1开始, 重试时递增)',

    -- 中继结果
    `relay_status`     VARCHAR(16)     NOT NULL COMMENT '中继执行状态: PUBLISHED (成功) / DEFERRED (重试) / FAILED (永久失败) / LEASE_MISSED (冲突)',

    -- 错误详情 (成功时为 NULL)
    `error_code`       VARCHAR(50)     NULL COMMENT '中继失败时的错误代码 (例如, NETWORK_TIMEOUT, BROKER_UNAVAILABLE), 成功时为 NULL',
    `error_message`    TEXT            NULL COMMENT '中继失败时的错误详情 (截断为 512 字符), 成功时为 NULL',
    `error_kind`       VARCHAR(16)     NULL COMMENT '错误分类: FATAL (不可重试) 或 TRANSIENT (可重试), 成功时为 NULL',

    -- 时序字段
    `started_at`       TIMESTAMP(6)    NOT NULL COMMENT '中继开始时间戳 (UTC)',
    `completed_at`     TIMESTAMP(6)    NOT NULL COMMENT '中继完成时间戳 (UTC)',
    `duration_ms`      INT UNSIGNED    NOT NULL COMMENT '中继执行持续时间(毫秒) (completedAt - startedAt)',

    -- 下次重试调度 (仅用于 DEFERRED 状态)
    `next_retry_at`    TIMESTAMP(6)    NULL COMMENT '下次重试时间戳 (UTC), 仅在 DEFERRED 状态时存在',

    -- 标准审计字段
    `record_remarks`   JSON            NULL COMMENT 'JSON 数组, 备注/变更日志',
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    `ip_address`       VARBINARY(16)   NULL COMMENT '请求者IP (二进制, 支持 IPv4/IPv6)',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '创建时间 (UTC)',
    `created_by`       BIGINT UNSIGNED NULL COMMENT '创建人ID',
    `created_by_name`  VARCHAR(100)    NULL COMMENT '创建人姓名',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT '更新时间 (UTC)',
    `updated_by`       BIGINT UNSIGNED NULL COMMENT '更新人ID',
    `updated_by_name`  VARCHAR(100)    NULL COMMENT '更新人姓名',
    `deleted_at`       TIMESTAMP(6)    NULL DEFAULT NULL COMMENT '逻辑删除时间戳: NULL=活动, 有值=删除时间(UTC)',

    PRIMARY KEY (`id`),

    -- 按消息ID查询 (用于故障排除: "显示此消息的所有尝试")
    INDEX `idx_message_id` (`message_id`, `started_at` DESC)
        COMMENT '按消息查询日志 (最新优先)',

    -- 按批次ID查询 (用于批次级统计)
    INDEX `idx_batch_id` (`relay_batch_id`)
        COMMENT '按中继批次查询日志',

    -- 按通道和时间范围查询 (用于监控仪表板)
    INDEX `idx_channel_time` (`channel`, `started_at`)
        COMMENT '按通道和时间范围查询日志',

    -- 按状态查询 (用于告警: "显示最近失败")
    INDEX `idx_status` (`relay_status`, `started_at`)
        COMMENT '按状态查询日志',

    -- 归档/清理便利
    INDEX `idx_created_at` (`created_at`)
        COMMENT '按创建时间归档旧日志',

    INDEX `idx_deleted_at` (`deleted_at`)
        COMMENT '软删除支持'

    -- 注意: 故意省略外键约束以提高性能
    -- 引用完整性在应用层强制
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT ='Outbox 中继执行日志 - 每次中继尝试的不可变审计跟踪';
