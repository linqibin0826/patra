-- discussion 子域：文献与投稿经验下的评论与回复、修改历史、讨论串统计
-- 数据按树存：root_comment_id 是所在楼的根评论（根评论等于自己），parent_comment_id 是回复的那一条。
-- 不建外键：「有回复的评论不能整条删」由应用保证——发回复与删评论都先拿楼锁，删除在锁内查有没有回复。
-- 正文存 Markdown 原文，@ 存 <@用户ID>；只存用户 ID。按对象读、量很小，不分区。

-- ============================================================
-- itr_comment：根评论与回复
-- ============================================================
CREATE TABLE itr_comment
(
    id                 BIGINT          NOT NULL,
    subject_id         BIGINT          NOT NULL,
    root_comment_id    BIGINT          NOT NULL,
    parent_comment_id  BIGINT          NULL,
    author_user_id     BIGINT          NOT NULL,
    state              VARCHAR(16)     NOT NULL,
    body               TEXT            NULL,
    mentioned_user_ids BIGINT[]        NOT NULL DEFAULT '{}',
    body_version       INTEGER         NOT NULL DEFAULT 1,
    published_at       timestamptz(6)  NOT NULL,
    revised_at         timestamptz(6)  NULL,
    deleted_at         timestamptz(6)  NULL,
    record_remarks     jsonb           NULL,
    ip_address         INET            NULL,
    created_at         timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         BIGINT          NULL,
    updated_at         timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by         BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_comment_state CHECK (state IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_itr_comment_tree CHECK ((parent_comment_id IS NULL) = (root_comment_id = id)),
    CONSTRAINT ck_itr_comment_body CHECK ((state = 'ACTIVE') = (body IS NOT NULL)),
    CONSTRAINT ck_itr_comment_deleted_at CHECK ((state = 'DELETED') = (deleted_at IS NOT NULL)),
    CONSTRAINT ck_itr_comment_body_version CHECK (body_version > 0)
);
CREATE INDEX idx_itr_comment_roots ON itr_comment (subject_id, published_at, id) WHERE parent_comment_id IS NULL;
CREATE INDEX idx_itr_comment_thread ON itr_comment (root_comment_id, published_at, id);
CREATE INDEX idx_itr_comment_parent ON itr_comment (parent_comment_id) WHERE parent_comment_id IS NOT NULL;
CREATE INDEX idx_itr_comment_participants ON itr_comment (subject_id, author_user_id, published_at) WHERE state = 'ACTIVE';

COMMENT ON TABLE itr_comment IS '对象下的评论与回复，按树存，展示成两级（根评论 + 平铺的回复）';
COMMENT ON COLUMN itr_comment.subject_id IS '评论所在的对象（itr_subject.id）';
COMMENT ON COLUMN itr_comment.root_comment_id IS '所在楼的根评论 ID，根评论等于自己的 id';
COMMENT ON COLUMN itr_comment.parent_comment_id IS '回复的那一条评论 ID，根评论为空';
COMMENT ON COLUMN itr_comment.author_user_id IS '作者用户 ID';
COMMENT ON COLUMN itr_comment.state IS '状态：ACTIVE，或 DELETED（有回复的评论删除后留下的占位）';
COMMENT ON COLUMN itr_comment.body IS '正文 Markdown 原文，@ 存成 <@用户ID>；占位为空';
COMMENT ON COLUMN itr_comment.mentioned_user_ids IS '当前正文 @ 到的用户 ID';
COMMENT ON COLUMN itr_comment.body_version IS '正文版本：发表为 1，正文每次确有变化加 1；集成事件带上它，消费方按它丢弃晚到的旧编辑';
COMMENT ON COLUMN itr_comment.published_at IS '发表时间，由应用写；排序与游标、编辑宽限期、事件与页面上的「发布于」都取它';
COMMENT ON COLUMN itr_comment.revised_at IS '最近一次记了修订的编辑时间，没有修改历史为空';
COMMENT ON COLUMN itr_comment.deleted_at IS '变成占位的时间，只在 DELETED 时有值';

CREATE TRIGGER trg_itr_comment_audit_times
    BEFORE INSERT OR UPDATE ON itr_comment
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- itr_comment_revision：修改历史，被替换下来的旧版本（当前版本在 itr_comment.body）
-- 评论整条删除或变占位时由仓储在同一事务里先删它的修改历史
-- ============================================================
CREATE TABLE itr_comment_revision
(
    id          BIGINT          NOT NULL,
    comment_id  BIGINT          NOT NULL,
    revision_no INTEGER         NOT NULL,
    body        TEXT            NOT NULL,
    version_at  timestamptz(6)  NOT NULL,
    created_at  timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_comment_revision_no CHECK (revision_no > 0)
);
CREATE UNIQUE INDEX uk_itr_comment_revision_comment_no ON itr_comment_revision (comment_id, revision_no);

COMMENT ON TABLE itr_comment_revision IS '评论被替换下来的旧版本；当前版本在 itr_comment.body';
COMMENT ON COLUMN itr_comment_revision.comment_id IS '所属评论（itr_comment.id）';
COMMENT ON COLUMN itr_comment_revision.revision_no IS '版本号，从 1 起按替换顺序递增';
COMMENT ON COLUMN itr_comment_revision.body IS '这一版的正文 Markdown 原文';
COMMENT ON COLUMN itr_comment_revision.version_at IS '这一版成为当前版本的时间';

CREATE TRIGGER trg_itr_comment_revision_audit_times
    BEFORE INSERT OR UPDATE ON itr_comment_revision
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- itr_comment_thread_stats：楼统计，一楼一行
-- 发表、删除评论的事务同步 ±1，不按明细重数覆盖；「未删除」= state = 'ACTIVE'，占位不算。
-- 第一次有回复时建行，减到 0 时保留，根评论整条去掉时删行；没有行表示没有回复
-- ============================================================
CREATE TABLE itr_comment_thread_stats
(
    id              BIGINT          NOT NULL,
    root_comment_id BIGINT          NOT NULL,
    reply_count     INTEGER         NOT NULL DEFAULT 0,
    last_reply_at   timestamptz(6)  NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_comment_thread_stats_count CHECK (reply_count >= 0),
    CONSTRAINT ck_itr_comment_thread_stats_last CHECK ((reply_count = 0) = (last_reply_at IS NULL))
);
CREATE UNIQUE INDEX uk_itr_comment_thread_stats_root ON itr_comment_thread_stats (root_comment_id);

COMMENT ON TABLE itr_comment_thread_stats IS '每个楼的回复数与最后回复时间，随评论写入同事务维护；没有行表示没有回复';
COMMENT ON COLUMN itr_comment_thread_stats.root_comment_id IS '楼的根评论（itr_comment.id）';
COMMENT ON COLUMN itr_comment_thread_stats.reply_count IS '楼里未删除的回复数';
COMMENT ON COLUMN itr_comment_thread_stats.last_reply_at IS '楼里最后一条未删除回复的发布时间；没有回复为空';

CREATE TRIGGER trg_itr_comment_thread_stats_audit_times
    BEFORE INSERT OR UPDATE ON itr_comment_thread_stats
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- itr_comment_subject_stats：对象统计，文献的讨论数与投稿经验的回复数都是它；行建了就不删
-- ============================================================
CREATE TABLE itr_comment_subject_stats
(
    id            BIGINT          NOT NULL,
    subject_id    BIGINT          NOT NULL,
    comment_count INTEGER         NOT NULL DEFAULT 0,
    created_at    timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_comment_subject_stats_count CHECK (comment_count >= 0)
);
CREATE UNIQUE INDEX uk_itr_comment_subject_stats_subject ON itr_comment_subject_stats (subject_id);

COMMENT ON TABLE itr_comment_subject_stats IS '每个对象的未删除评论数，随评论写入同事务维护；没有行表示 0';
COMMENT ON COLUMN itr_comment_subject_stats.subject_id IS '对象（itr_subject.id）';
COMMENT ON COLUMN itr_comment_subject_stats.comment_count IS '对象下未删除的评论与回复数';

CREATE TRIGGER trg_itr_comment_subject_stats_audit_times
    BEFORE INSERT OR UPDATE ON itr_comment_subject_stats
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();
