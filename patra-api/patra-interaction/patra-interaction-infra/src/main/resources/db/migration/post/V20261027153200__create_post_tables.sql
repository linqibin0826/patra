-- ============================================================
-- post 子域：帖子基表、投稿经验扩展表、修改历史（PAP-122，决策 B）
-- 本版只有「投稿经验」一种帖子类型；以后加求助帖只新增类型，不改表。
-- 只存用户 ID；subject_id 是期刊在 subject.registry 的本地 ID，不建跨 schema 外键。
-- 一致性规则（首轮结果 × 最终结果）只在领域层判，这里的 CHECK 只兜底取值与日期先后。
-- 「同一作者同一期刊同一投稿日期只能有一条未删除的经验」跨两张表、只对 ACTIVE 生效，由写入路径在作者锁里查重。
-- ============================================================

-- set_updated_at()：每个 schema 一份，schema 整体搬走时不依赖别的 schema
CREATE OR REPLACE FUNCTION post.set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- ------------------------------------------------------------
-- 帖子基表：各帖子类型共用
-- ------------------------------------------------------------
CREATE TABLE post.post
(
    post_id         BIGINT          NOT NULL,
    post_type       VARCHAR(32)     NOT NULL,
    subject_id      BIGINT          NOT NULL,
    author_user_id  BIGINT          NOT NULL,
    anonymous       BOOLEAN         NOT NULL,
    body            TEXT            NULL,
    status          VARCHAR(16)     NOT NULL,
    created_at      timestamptz(6)  NOT NULL,
    revised_at      timestamptz(6)  NULL,
    deleted_at      timestamptz(6)  NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id),
    CONSTRAINT ck_post_type CHECK (post_type IN ('SUBMISSION_EXPERIENCE')),
    CONSTRAINT ck_post_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_post_deleted CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);

COMMENT ON TABLE post.post IS 'Posts (base table shared by all post types); this release has only SUBMISSION_EXPERIENCE';
COMMENT ON COLUMN post.post.post_id IS 'PK (snowflake); also the source_id of the POST row in subject.registry';
COMMENT ON COLUMN post.post.post_type IS 'Post type: SUBMISSION_EXPERIENCE';
COMMENT ON COLUMN post.post.subject_id IS 'Subject the post hangs under (journal local ID in subject.registry); no cross-schema FK';
COMMENT ON COLUMN post.post.author_user_id IS 'Author user ID (identity)';
COMMENT ON COLUMN post.post.anonymous IS 'Whether the author chose to be anonymous at publish time; never changes';
COMMENT ON COLUMN post.post.body IS 'Markdown source, up to 2,000 graphemes; NULL when absent';
COMMENT ON COLUMN post.post.status IS 'ACTIVE or DELETED (soft delete: content kept, never shown)';
COMMENT ON COLUMN post.post.created_at IS 'Publish time (UTC)';
COMMENT ON COLUMN post.post.revised_at IS 'Last edit that recorded a revision (UTC); NULL = no edit history';
COMMENT ON COLUMN post.post.deleted_at IS 'Delete time (UTC); set iff status = DELETED';
COMMENT ON COLUMN post.post.updated_at IS 'Last change of any column (UTC)';

CREATE INDEX idx_post_subject_active ON post.post (subject_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_post_author ON post.post (author_user_id);

CREATE TRIGGER trg_post_updated_at
    BEFORE UPDATE ON post.post
    FOR EACH ROW EXECUTE FUNCTION post.set_updated_at();

-- ------------------------------------------------------------
-- 投稿经验扩展表：与基表一对一
-- ------------------------------------------------------------
CREATE TABLE post.submission_experience
(
    post_id               BIGINT       NOT NULL,
    submitted_on          DATE         NOT NULL,
    first_decision_on     DATE         NOT NULL,
    first_decision        VARCHAR(32)  NOT NULL,
    final_outcome         VARCHAR(16)  NOT NULL,
    final_decision_on     DATE         NULL,
    article_type          VARCHAR(32)  NOT NULL,
    review_rounds         SMALLINT     NULL,
    review_quality        SMALLINT     NULL,
    editor_communication  SMALLINT     NULL,
    apc_paid              BOOLEAN      NULL,
    previously_rejected   BOOLEAN      NULL,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_experience_post FOREIGN KEY (post_id) REFERENCES post.post (post_id),
    CONSTRAINT ck_experience_first_decision
        CHECK (first_decision IN ('DESK_REJECT', 'REJECT_AFTER_REVIEW', 'MAJOR_REVISION', 'MINOR_REVISION', 'ACCEPT')),
    CONSTRAINT ck_experience_final_outcome
        CHECK (final_outcome IN ('ACCEPTED', 'REJECTED', 'WITHDRAWN', 'IN_PROGRESS')),
    CONSTRAINT ck_experience_article_type
        CHECK (article_type IN ('ORIGINAL_RESEARCH', 'REVIEW', 'SYSTEMATIC_REVIEW', 'CASE_REPORT', 'SHORT_COMMUNICATION', 'OTHER')),
    CONSTRAINT ck_experience_review_rounds CHECK (review_rounds BETWEEN 1 AND 10),
    CONSTRAINT ck_experience_review_quality CHECK (review_quality BETWEEN 1 AND 5),
    CONSTRAINT ck_experience_editor_communication CHECK (editor_communication BETWEEN 1 AND 5),
    CONSTRAINT ck_experience_first_after_submission CHECK (first_decision_on >= submitted_on),
    CONSTRAINT ck_experience_final_after_first CHECK (final_decision_on >= first_decision_on),
    CONSTRAINT ck_experience_in_progress_open CHECK (final_outcome <> 'IN_PROGRESS' OR final_decision_on IS NULL)
);

COMMENT ON TABLE post.submission_experience IS 'Submission experience fields (one row per SUBMISSION_EXPERIENCE post); durations are computed from dates';
COMMENT ON COLUMN post.submission_experience.post_id IS 'PK and FK to post.post';
COMMENT ON COLUMN post.submission_experience.submitted_on IS 'Submission date';
COMMENT ON COLUMN post.submission_experience.first_decision_on IS 'First-round decision date';
COMMENT ON COLUMN post.submission_experience.first_decision IS 'First-round decision: DESK_REJECT, REJECT_AFTER_REVIEW, MAJOR_REVISION, MINOR_REVISION, ACCEPT';
COMMENT ON COLUMN post.submission_experience.final_outcome IS 'Final outcome: ACCEPTED, REJECTED, WITHDRAWN, IN_PROGRESS';
COMMENT ON COLUMN post.submission_experience.final_decision_on IS 'Final decision date; NULL when in progress, terminal at first round, or not given';
COMMENT ON COLUMN post.submission_experience.article_type IS 'Article type: ORIGINAL_RESEARCH, REVIEW, SYSTEMATIC_REVIEW, CASE_REPORT, SHORT_COMMUNICATION, OTHER';
COMMENT ON COLUMN post.submission_experience.review_rounds IS 'Review rounds 1-10; NULL when not given';
COMMENT ON COLUMN post.submission_experience.review_quality IS 'Review quality rating 1-5; NULL when not given';
COMMENT ON COLUMN post.submission_experience.editor_communication IS 'Editor communication rating 1-5; NULL when not given';
COMMENT ON COLUMN post.submission_experience.apc_paid IS 'Whether an APC was paid; NULL when not given';
COMMENT ON COLUMN post.submission_experience.previously_rejected IS 'Whether the manuscript had been rejected elsewhere before this submission; NULL when not given';

-- ------------------------------------------------------------
-- 修改历史：被替换掉的旧版本；当前版本在基表 + 扩展表
-- ------------------------------------------------------------
CREATE TABLE post.revision
(
    post_id      BIGINT          NOT NULL,
    revision_no  INTEGER         NOT NULL,
    body         TEXT            NULL,
    fields       JSONB           NOT NULL,
    written_at   timestamptz(6)  NOT NULL,
    replaced_at  timestamptz(6)  NOT NULL,
    PRIMARY KEY (post_id, revision_no),
    CONSTRAINT fk_revision_post FOREIGN KEY (post_id) REFERENCES post.post (post_id),
    CONSTRAINT ck_revision_no CHECK (revision_no > 0)
);

COMMENT ON TABLE post.revision IS 'Replaced versions of posts (edits within 5 minutes of publishing are not recorded)';
COMMENT ON COLUMN post.revision.post_id IS 'Post ID (FK to post.post)';
COMMENT ON COLUMN post.revision.revision_no IS 'Revision number starting at 1, in replacement order';
COMMENT ON COLUMN post.revision.body IS 'Markdown source of this version; NULL when absent';
COMMENT ON COLUMN post.revision.fields IS 'Structured fields of this version as JSON, interpreted by the post type';
COMMENT ON COLUMN post.revision.written_at IS 'When this version became current (UTC)';
COMMENT ON COLUMN post.revision.replaced_at IS 'When this version was replaced (UTC)';
