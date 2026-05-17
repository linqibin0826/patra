-- =====================================================================
-- Patra Ingest — PostgreSQL 17 baseline schema: V4 Outbox
-- 来源: V0.1.0__init_ingest_schema.sql 第 9-10 段
-- 包含: ing_outbox_message + ing_outbox_relay_log（2 表）
-- 特殊: idx_pending_relay / idx_publishing_lease 改写为 partial index
-- 已移除: AUTO_INCREMENT / ENGINE/CHARSET/COLLATE / ON UPDATE / CREATE TABLE IF NOT EXISTS / 反引号
-- =====================================================================

-- ======================================================================
-- 表: ing_outbox_message —— 通用发件箱 (任务调度/集成事件等)
-- 语义: 与业务写入在同一事务中持久化; 由 Relay 扫描并投递到 MQ (例如 RocketMQ)。
-- 设计要点:
--  - (channel, dedup_key) 唯一, 确保源端幂等;
--  - 仅扫描此表进行发布, 非业务热表;
--  - partition_key 推荐使用 "provenance:operation"。
-- ======================================================================
CREATE TABLE ing_outbox_message
(
    id               BIGINT          NOT NULL,
    aggregate_type   VARCHAR(32)     NOT NULL,
    aggregate_id     BIGINT          NOT NULL,
    channel          VARCHAR(64)     NOT NULL,
    op_type          VARCHAR(32)     NOT NULL,
    partition_key    VARCHAR(128)    NOT NULL,
    dedup_key        VARCHAR(128)    NOT NULL,
    payload_json     jsonb           NOT NULL,
    headers_json     jsonb           NULL,
    not_before       timestamptz(6)  NULL,
    published_at     timestamptz(6)  NULL,

    status_code      VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    retry_count      INTEGER         NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    next_retry_at    timestamptz(6)  NULL,
    error_code       VARCHAR(64)     NULL,
    error_msg        VARCHAR(512)    NULL,

    pub_lease_owner  VARCHAR(128)    NULL,
    pub_leased_until timestamptz(6)  NULL,

    -- 审计字段
    record_remarks   jsonb           NULL,
    version          BIGINT          NOT NULL DEFAULT 0,
    ip_address       bytea           NULL,
    created_at       timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       BIGINT          NULL,
    created_by_name  VARCHAR(100)    NULL,
    updated_at       timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       BIGINT          NULL,
    updated_by_name  VARCHAR(100)    NULL,

    PRIMARY KEY (id)
);

-- 源端去重: dedup_key 在同一通道内必须唯一
CREATE UNIQUE INDEX uk_outbox_channel_dedup ON ing_outbox_message (channel, dedup_key);
-- 轻量扫描索引: 按状态+时间游标批量发布（覆盖所有状态，保持常规 index）
CREATE INDEX idx_outbox_status_time ON ing_outbox_message (status_code, not_before, id);
-- 有序/分区发布和重放
CREATE INDEX idx_outbox_partition ON ing_outbox_message (channel, partition_key, status_code);
-- 发布者租约回收
CREATE INDEX idx_outbox_lease ON ing_outbox_message (status_code, pub_leased_until);
-- 归档/对账便利
CREATE INDEX idx_outbox_created ON ing_outbox_message (created_at);

-- UNION ALL 查询模式 partial index: 仅索引对应状态子集，体积更小、扫描更快
-- 优化 PENDING 消息中继查询（UNION ALL 第一子查询）
CREATE INDEX idx_pending_relay ON ing_outbox_message (channel, not_before, id)
  WHERE status_code = 'PENDING';

-- 优化 PUBLISHING 过期租约消息查询（UNION ALL 第二子查询）
CREATE INDEX idx_publishing_lease ON ing_outbox_message (channel, pub_leased_until, id)
  WHERE status_code = 'PUBLISHING';

CREATE TRIGGER trg_ing_outbox_message_updated_at
    BEFORE UPDATE ON ing_outbox_message
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ======================================================================
-- 表: ing_outbox_relay_log —— Outbox 中继执行日志
-- 语义: 每次中继尝试的不可变审计跟踪; 追踪发布成功/失败、时序、错误和重试调度。
-- 设计要点:
--  - 每次中继尝试一条记录 (如果发生重试, 同一消息有多次尝试);
--  - 从 ing_outbox_message 反规范化 channel, partition_key 以提高查询效率;
--  - 支持故障排除、批次级统计和监控/告警。
-- ======================================================================
CREATE TABLE ing_outbox_relay_log
(
    id               BIGINT          NOT NULL,

    -- 核心引用
    message_id       BIGINT          NOT NULL,
    relay_batch_id   VARCHAR(50)     NOT NULL,

    -- 中继上下文 (为查询效率反规范化)
    channel          VARCHAR(64)     NOT NULL,
    partition_key    VARCHAR(128)    NULL,
    lease_owner      VARCHAR(128)    NULL,
    attempt_number   INTEGER         NOT NULL CHECK (attempt_number >= 0),

    -- 中继结果
    relay_status     VARCHAR(16)     NOT NULL,

    -- 错误详情 (成功时为 NULL)
    error_code       VARCHAR(50)     NULL,
    error_message    TEXT            NULL,
    error_kind       VARCHAR(16)     NULL,

    -- 时序字段
    started_at       timestamptz(6)  NOT NULL,
    completed_at     timestamptz(6)  NOT NULL,
    duration_ms      INTEGER         NOT NULL CHECK (duration_ms >= 0),

    -- 下次重试调度 (仅用于 DEFERRED 状态)
    next_retry_at    timestamptz(6)  NULL,

    -- 标准审计字段
    record_remarks   jsonb           NULL,
    version          BIGINT          NOT NULL DEFAULT 0,
    ip_address       bytea           NULL,
    created_at       timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       BIGINT          NULL,
    created_by_name  VARCHAR(100)    NULL,
    updated_at       timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by       BIGINT          NULL,
    updated_by_name  VARCHAR(100)    NULL,

    PRIMARY KEY (id)
);

-- 按消息ID查询 (用于故障排除: "显示此消息的所有尝试")
CREATE INDEX idx_message_id ON ing_outbox_relay_log (message_id, started_at DESC);
-- 按批次ID查询 (用于批次级统计)
CREATE INDEX idx_relay_batch_id ON ing_outbox_relay_log (relay_batch_id);
-- 按通道和时间范围查询 (用于监控仪表板)
CREATE INDEX idx_channel_time ON ing_outbox_relay_log (channel, started_at);
-- 按状态查询 (用于告警: "显示最近失败")
CREATE INDEX idx_relay_status ON ing_outbox_relay_log (relay_status, started_at);
-- 归档/清理便利
CREATE INDEX idx_relay_created_at ON ing_outbox_relay_log (created_at);

CREATE TRIGGER trg_ing_outbox_relay_log_updated_at
    BEFORE UPDATE ON ing_outbox_relay_log
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
