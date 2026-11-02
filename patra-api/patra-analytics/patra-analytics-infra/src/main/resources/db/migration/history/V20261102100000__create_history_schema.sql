-- history 子域：个人阅读历史（PAP-169）。只存用户 ID 与 catalog 的文献 ID，不建跨库外键
CREATE SCHEMA history;

-- 每人每篇一行：最近一次打开的时间、是否读过全文
CREATE TABLE history.reading_history (
    user_id        BIGINT      NOT NULL,
    article_id     BIGINT      NOT NULL,
    last_read_at   TIMESTAMPTZ NOT NULL,
    fulltext_read  BOOLEAN     NOT NULL,
    PRIMARY KEY (user_id, article_id)
);
-- 翻页：按最近打开时间倒序、同一时间按文献 ID 倒序
CREATE INDEX idx_reading_history_user_time
    ON history.reading_history (user_id, last_read_at DESC, article_id DESC);
-- 过期清理：按时间扫
CREATE INDEX idx_reading_history_time ON history.reading_history (last_read_at);

-- 改过开关或清空过历史的人才有一行；没有行 = 开着、没清空过
CREATE TABLE history.recording_setting (
    user_id            BIGINT      PRIMARY KEY,
    recording_enabled  BOOLEAN     NOT NULL,
    cleared_at         TIMESTAMPTZ,              -- 最近一次清空的时刻；没清空过为 NULL
    updated_at         TIMESTAMPTZ NOT NULL
);

-- 单条删除的记录：挡住删除之前发生、删除之后才到的事件；2 天后由过期清理删掉
CREATE TABLE history.deleted_entry (
    user_id     BIGINT      NOT NULL,
    article_id  BIGINT      NOT NULL,
    deleted_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, article_id)
);
CREATE INDEX idx_deleted_entry_time ON history.deleted_entry (deleted_at);
