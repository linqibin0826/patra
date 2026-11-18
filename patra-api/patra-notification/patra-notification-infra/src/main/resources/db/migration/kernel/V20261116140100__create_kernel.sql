-- 跨子域的框架表与在线状态：事件发布登记（Spring Modulith，表名列名固定）、消费去重（patra-tool-events，表名固定）、
-- 最后在线时间。两张框架表保持默认名，登记在 db/tables.yml 的 framework 里。

-- 事件发布登记：notification 外送集成事件的 Outbox，由 patra-tool-events 使用（Spring Modulith 2.1.2 JDBC 第二版结构）
CREATE TABLE event_publication
(
    id                     UUID                     NOT NULL PRIMARY KEY,
    listener_id            TEXT                     NOT NULL,
    event_type             TEXT                     NOT NULL,
    serialized_event       TEXT                     NOT NULL,
    publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
    completion_date        TIMESTAMP WITH TIME ZONE,
    status                 TEXT,
    completion_attempts    INT,
    last_resubmission_date TIMESTAMP WITH TIME ZONE
);
CREATE INDEX event_publication_serialized_event_hash_idx ON event_publication USING hash (serialized_event);
CREATE INDEX event_publication_by_completion_date_idx ON event_publication (completion_date);

-- 消费去重：业务事务里先登记（消费者, 事件 ID），已登记过就跳过
CREATE TABLE processed_event
(
    consumer     TEXT                     NOT NULL,
    event_id     UUID                     NOT NULL,
    processed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (consumer, event_id)
);
CREATE INDEX processed_event_processed_at_idx ON processed_event (processed_at);

-- ============================================================
-- ntf_presence：最后在线时间，每个用户一行，只往前走
-- ============================================================
CREATE TABLE ntf_presence
(
    id                BIGINT          NOT NULL,
    user_id           BIGINT          NOT NULL,
    last_seen_at      timestamptz(6)  NOT NULL,
    created_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_ntf_presence_user ON ntf_presence (user_id);

COMMENT ON TABLE ntf_presence IS '用户最后在线时间：离站邮件只发给写入信箱之后还没回来过的人';
COMMENT ON COLUMN ntf_presence.user_id IS '用户 ID';
COMMENT ON COLUMN ntf_presence.last_seen_at IS '最后一次看到这个用户的时间，只往前走';

CREATE TRIGGER trg_ntf_presence_audit_times
    BEFORE INSERT OR UPDATE ON ntf_presence
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();
