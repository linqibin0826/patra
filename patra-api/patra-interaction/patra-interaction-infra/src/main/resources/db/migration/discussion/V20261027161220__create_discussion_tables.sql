-- ============================================================
-- discussion 子域：评论与修改历史（PAP-121，release spec 2.1.1、决策 A、C）
-- 数据按树存：root_id 是所在楼的根评论（根评论等于自己），parent_id 是回复的那一条；两个自引用外键兜底树的完整性。
-- 正文存 Markdown 原文，@ 存 <@用户ID>；只存用户 ID，不存用户名、昵称、头像。按对象读、量很小，不分区。
-- subject_id 不对 subject.registry 建外键（决策 B）。
-- ============================================================

-- ------------------------------------------------------------
-- 评论：根评论与回复
-- ------------------------------------------------------------
CREATE TABLE discussion.comment
(
    comment_id          BIGINT          NOT NULL,
    subject_id          BIGINT          NOT NULL,
    root_id             BIGINT          NOT NULL,
    parent_id           BIGINT          NULL,
    author_id           BIGINT          NOT NULL,
    state               VARCHAR(16)     NOT NULL,
    body                TEXT            NULL,
    mentioned_user_ids  BIGINT[]        NOT NULL DEFAULT '{}',
    created_at          timestamptz(6)  NOT NULL,
    revised_at          timestamptz(6)  NULL,
    deleted_at          timestamptz(6)  NULL,
    PRIMARY KEY (comment_id),
    CONSTRAINT ck_comment_state CHECK (state IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_comment_tree CHECK ((parent_id IS NULL) = (root_id = comment_id)),
    CONSTRAINT ck_comment_body CHECK ((state = 'ACTIVE') = (body IS NOT NULL)),
    CONSTRAINT ck_comment_deleted_at CHECK ((state = 'DELETED') = (deleted_at IS NOT NULL)),
    CONSTRAINT fk_comment_root FOREIGN KEY (root_id) REFERENCES discussion.comment (comment_id),
    CONSTRAINT fk_comment_parent FOREIGN KEY (parent_id) REFERENCES discussion.comment (comment_id)
);

COMMENT ON TABLE discussion.comment IS 'Comments and replies on a subject, stored as a tree; shown as two levels (root + flat replies)';
COMMENT ON COLUMN discussion.comment.comment_id IS 'PK (snowflake)';
COMMENT ON COLUMN discussion.comment.subject_id IS 'subject.registry local ID; no cross-schema FK';
COMMENT ON COLUMN discussion.comment.root_id IS 'Root comment of the thread; equals comment_id for a root comment';
COMMENT ON COLUMN discussion.comment.parent_id IS 'Comment replied to; NULL for a root comment';
COMMENT ON COLUMN discussion.comment.author_id IS 'Author user ID (identity)';
COMMENT ON COLUMN discussion.comment.state IS 'ACTIVE, or DELETED (placeholder kept because it still has replies)';
COMMENT ON COLUMN discussion.comment.body IS 'Markdown source; mentions stored as <@userId>; NULL for a placeholder';
COMMENT ON COLUMN discussion.comment.mentioned_user_ids IS 'Every user this comment has ever mentioned (only grows); edits notify only first-time mentions';
COMMENT ON COLUMN discussion.comment.created_at IS 'Publish time (UTC)';
COMMENT ON COLUMN discussion.comment.revised_at IS 'When the current version came from a recorded edit (UTC); NULL means no edit history';
COMMENT ON COLUMN discussion.comment.deleted_at IS 'When it became a placeholder (UTC)';

CREATE INDEX idx_comment_roots ON discussion.comment (subject_id, created_at, comment_id) WHERE parent_id IS NULL;
CREATE INDEX idx_comment_thread ON discussion.comment (root_id, created_at, comment_id);
CREATE INDEX idx_comment_parent ON discussion.comment (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_comment_participants ON discussion.comment (subject_id, author_id, created_at) WHERE state = 'ACTIVE';

-- ------------------------------------------------------------
-- 修改历史：被替换下来的旧版本（当前版本在 comment.body）
-- ------------------------------------------------------------
CREATE TABLE discussion.comment_revision
(
    comment_id   BIGINT          NOT NULL,
    revision_no  INTEGER         NOT NULL,
    body         TEXT            NOT NULL,
    version_at   timestamptz(6)  NOT NULL,
    PRIMARY KEY (comment_id, revision_no),
    CONSTRAINT ck_comment_revision_no CHECK (revision_no > 0),
    CONSTRAINT fk_comment_revision_comment FOREIGN KEY (comment_id) REFERENCES discussion.comment (comment_id) ON DELETE CASCADE
);

COMMENT ON TABLE discussion.comment_revision IS 'Superseded versions of a comment; the current version lives in comment.body';
COMMENT ON COLUMN discussion.comment_revision.comment_id IS 'Comment; deleted together with it';
COMMENT ON COLUMN discussion.comment_revision.revision_no IS '1 is the original version, then increasing';
COMMENT ON COLUMN discussion.comment_revision.body IS 'Markdown source of this version, mentions as <@userId>';
COMMENT ON COLUMN discussion.comment_revision.version_at IS 'When this version became current: publish time for no. 1, edit time afterwards (UTC)';
