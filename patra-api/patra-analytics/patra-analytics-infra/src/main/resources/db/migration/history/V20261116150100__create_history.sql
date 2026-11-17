-- history 子域：阅读历史、阅读记录设置、用户删掉的历史条目墓碑
-- 只存用户 ID；publication_id 是 catalog 的文献 ID，引用关系由写入路径保证，不建外键。

-- ============================================================
-- anl_reading_history：阅读历史，每个（用户、文献）一行
-- ============================================================
CREATE TABLE anl_reading_history
(
    id             BIGINT            NOT NULL,
    user_id        BIGINT            NOT NULL,
    publication_id BIGINT            NOT NULL,
    last_read_at   timestamptz(6)    NOT NULL,
    fulltext_read  BOOLEAN           NOT NULL,
    created_at     timestamptz(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamptz(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_anl_reading_history_user_publication ON anl_reading_history (user_id, publication_id);
CREATE INDEX idx_anl_reading_history_user_time ON anl_reading_history (user_id, last_read_at DESC, publication_id DESC);
CREATE INDEX idx_anl_reading_history_time ON anl_reading_history (last_read_at);

COMMENT ON TABLE anl_reading_history IS '阅读历史：每个（用户、文献）一行，再读时覆盖最近阅读时间';
COMMENT ON COLUMN anl_reading_history.user_id IS '用户 ID';
COMMENT ON COLUMN anl_reading_history.publication_id IS 'catalog 的文献 ID';
COMMENT ON COLUMN anl_reading_history.last_read_at IS '最近一次读到的时间（取自阅读事件）';
COMMENT ON COLUMN anl_reading_history.fulltext_read IS '是否读过全文（只要读过一次就是 true）';

CREATE TRIGGER trg_anl_reading_history_audit_times
    BEFORE INSERT OR UPDATE ON anl_reading_history
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- anl_recording_setting：阅读记录设置，每个用户一行
-- ============================================================
CREATE TABLE anl_recording_setting
(
    id                BIGINT            NOT NULL,
    user_id           BIGINT            NOT NULL,
    recording_enabled BOOLEAN           NOT NULL,
    cleared_at        timestamptz(6)    NULL,
    record_remarks    jsonb             NULL,
    ip_address        INET              NULL,
    created_at        timestamptz(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT            NULL,
    updated_at        timestamptz(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT            NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_anl_recording_setting_user ON anl_recording_setting (user_id);

COMMENT ON TABLE anl_recording_setting IS '阅读记录设置：是否记录阅读历史、最近一次清空的时间；用户本人修改';
COMMENT ON COLUMN anl_recording_setting.user_id IS '用户 ID';
COMMENT ON COLUMN anl_recording_setting.recording_enabled IS '是否记录阅读历史';
COMMENT ON COLUMN anl_recording_setting.cleared_at IS '最近一次清空历史的时间，没清空过为空；早于它的阅读事件丢弃';

CREATE TRIGGER trg_anl_recording_setting_audit_times
    BEFORE INSERT OR UPDATE ON anl_recording_setting
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- anl_reading_history_tombstone：用户删掉的历史条目，晚到的阅读事件据此丢弃
-- ============================================================
CREATE TABLE anl_reading_history_tombstone
(
    id             BIGINT            NOT NULL,
    user_id        BIGINT            NOT NULL,
    publication_id BIGINT            NOT NULL,
    removed_at     timestamptz(6)    NOT NULL,
    created_at     timestamptz(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     timestamptz(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_anl_reading_history_tombstone_user_publication ON anl_reading_history_tombstone (user_id, publication_id);
CREATE INDEX idx_anl_reading_history_tombstone_time ON anl_reading_history_tombstone (removed_at);

COMMENT ON TABLE anl_reading_history_tombstone IS '用户删掉的阅读历史条目：删除之前发生、之后才到的阅读事件据此丢弃';
COMMENT ON COLUMN anl_reading_history_tombstone.user_id IS '用户 ID';
COMMENT ON COLUMN anl_reading_history_tombstone.publication_id IS 'catalog 的文献 ID';
COMMENT ON COLUMN anl_reading_history_tombstone.removed_at IS '用户删掉这一条的时间';

CREATE TRIGGER trg_anl_reading_history_tombstone_audit_times
    BEFORE INSERT OR UPDATE ON anl_reading_history_tombstone
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();
