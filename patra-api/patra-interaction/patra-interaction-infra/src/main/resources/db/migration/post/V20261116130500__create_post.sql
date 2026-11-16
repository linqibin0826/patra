-- post 子域：帖子基表、投稿经验扩展表、修改历史
-- 本版只有「投稿经验」一种帖子类型；以后加求助帖只新增类型，不改表。
-- 只存用户 ID；subject_id 是期刊在 itr_subject 的 id，post_id 引用 itr_post.id，引用关系由应用保证，不建外键。
-- 一致性规则（首轮结果 × 最终结果）只在领域层判，这里的 CHECK 只兜底取值与日期先后。
-- 「同一作者同一期刊同一投稿日期只能有一条未删除的经验」跨两张表、只对 ACTIVE 生效，由写入路径在作者锁里查重。

-- ============================================================
-- itr_post：帖子基表，各帖子类型共用
-- ============================================================
CREATE TABLE itr_post
(
    id             BIGINT          NOT NULL,
    post_type      VARCHAR(32)     NOT NULL,
    subject_id     BIGINT          NOT NULL,
    author_user_id BIGINT          NOT NULL,
    anonymous      BOOLEAN         NOT NULL,
    body           TEXT            NULL,
    status         VARCHAR(16)     NOT NULL,
    published_at   timestamptz(6)  NOT NULL,
    revised_at     timestamptz(6)  NULL,
    deleted_at     timestamptz(6)  NULL,
    record_remarks jsonb           NULL,
    ip_address     INET            NULL,
    created_at     timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by     BIGINT          NULL,
    updated_at     timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by     BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_post_type CHECK (post_type IN ('SUBMISSION_EXPERIENCE')),
    CONSTRAINT ck_itr_post_status CHECK (status IN ('ACTIVE', 'DELETED')),
    CONSTRAINT ck_itr_post_deleted CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);
CREATE INDEX idx_itr_post_subject_active ON itr_post (subject_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_itr_post_author ON itr_post (author_user_id);

COMMENT ON TABLE itr_post IS '帖子基表，各帖子类型共用；本版只有投稿经验';
COMMENT ON COLUMN itr_post.post_type IS '帖子类型：SUBMISSION_EXPERIENCE';
COMMENT ON COLUMN itr_post.subject_id IS '帖子挂在哪个期刊下（itr_subject.id）';
COMMENT ON COLUMN itr_post.author_user_id IS '作者用户 ID';
COMMENT ON COLUMN itr_post.anonymous IS '发布时作者是否选择匿名，之后不变';
COMMENT ON COLUMN itr_post.body IS '正文 Markdown 原文，最多 2000 个字；没有为空';
COMMENT ON COLUMN itr_post.status IS '状态：ACTIVE 或 DELETED（删除后内容保留、不再展示）';
COMMENT ON COLUMN itr_post.published_at IS '发布时间，由应用写；排序、编辑宽限期、页面上的「发布于」都取它';
COMMENT ON COLUMN itr_post.revised_at IS '最近一次记了修订的编辑时间，没有修改历史为空';
COMMENT ON COLUMN itr_post.deleted_at IS '删除时间，只在 DELETED 时有值';

CREATE TRIGGER trg_itr_post_audit_times
    BEFORE INSERT OR UPDATE ON itr_post
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- itr_post_submission_experience：投稿经验扩展表，与基表一对一
-- ============================================================
CREATE TABLE itr_post_submission_experience
(
    id                   BIGINT          NOT NULL,
    post_id              BIGINT          NOT NULL,
    submitted_on         DATE            NOT NULL,
    first_decision_on    DATE            NOT NULL,
    first_decision       VARCHAR(32)     NOT NULL,
    final_outcome        VARCHAR(16)     NOT NULL,
    final_decision_on    DATE            NULL,
    article_type         VARCHAR(32)     NOT NULL,
    review_rounds        SMALLINT        NULL,
    review_quality       SMALLINT        NULL,
    editor_communication SMALLINT        NULL,
    apc_paid             BOOLEAN         NULL,
    previously_rejected  BOOLEAN         NULL,
    created_at           timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_post_submission_experience_first
        CHECK (first_decision IN ('DESK_REJECT', 'REJECT_AFTER_REVIEW', 'MAJOR_REVISION', 'MINOR_REVISION', 'ACCEPT')),
    CONSTRAINT ck_itr_post_submission_experience_final
        CHECK (final_outcome IN ('ACCEPTED', 'REJECTED', 'WITHDRAWN', 'IN_PROGRESS')),
    CONSTRAINT ck_itr_post_submission_experience_article
        CHECK (article_type IN ('ORIGINAL_RESEARCH', 'REVIEW', 'SYSTEMATIC_REVIEW', 'CASE_REPORT', 'SHORT_COMMUNICATION', 'OTHER')),
    CONSTRAINT ck_itr_post_submission_experience_rounds CHECK (review_rounds BETWEEN 1 AND 10),
    CONSTRAINT ck_itr_post_submission_experience_quality CHECK (review_quality BETWEEN 1 AND 5),
    CONSTRAINT ck_itr_post_submission_experience_communication CHECK (editor_communication BETWEEN 1 AND 5),
    CONSTRAINT ck_itr_post_submission_experience_first_order CHECK (first_decision_on >= submitted_on),
    CONSTRAINT ck_itr_post_submission_experience_final_order CHECK (final_decision_on >= first_decision_on),
    CONSTRAINT ck_itr_post_submission_experience_open CHECK (final_outcome <> 'IN_PROGRESS' OR final_decision_on IS NULL)
);
CREATE UNIQUE INDEX uk_itr_post_submission_experience_post ON itr_post_submission_experience (post_id);

COMMENT ON TABLE itr_post_submission_experience IS '投稿经验的结构化字段，每篇投稿经验帖一行；各段时长按日期现算';
COMMENT ON COLUMN itr_post_submission_experience.post_id IS '所属帖子（itr_post.id）';
COMMENT ON COLUMN itr_post_submission_experience.submitted_on IS '投稿日期';
COMMENT ON COLUMN itr_post_submission_experience.first_decision_on IS '首轮结果日期';
COMMENT ON COLUMN itr_post_submission_experience.first_decision IS '首轮结果：DESK_REJECT、REJECT_AFTER_REVIEW、MAJOR_REVISION、MINOR_REVISION、ACCEPT';
COMMENT ON COLUMN itr_post_submission_experience.final_outcome IS '最终结果：ACCEPTED、REJECTED、WITHDRAWN、IN_PROGRESS';
COMMENT ON COLUMN itr_post_submission_experience.final_decision_on IS '最终结果日期；进行中、首轮即终局或没填时为空';
COMMENT ON COLUMN itr_post_submission_experience.article_type IS '文章类型：ORIGINAL_RESEARCH、REVIEW、SYSTEMATIC_REVIEW、CASE_REPORT、SHORT_COMMUNICATION、OTHER';
COMMENT ON COLUMN itr_post_submission_experience.review_rounds IS '审稿轮数 1～10，没填为空';
COMMENT ON COLUMN itr_post_submission_experience.review_quality IS '审稿质量评分 1～5，没填为空';
COMMENT ON COLUMN itr_post_submission_experience.editor_communication IS '编辑沟通评分 1～5，没填为空';
COMMENT ON COLUMN itr_post_submission_experience.apc_paid IS '是否付了版面费，没填为空';
COMMENT ON COLUMN itr_post_submission_experience.previously_rejected IS '投这本之前是否在别处被拒过，没填为空';

CREATE TRIGGER trg_itr_post_submission_experience_audit_times
    BEFORE INSERT OR UPDATE ON itr_post_submission_experience
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- itr_post_revision：修改历史，被替换掉的旧版本；当前版本在基表 + 扩展表
-- ============================================================
CREATE TABLE itr_post_revision
(
    id          BIGINT          NOT NULL,
    post_id     BIGINT          NOT NULL,
    revision_no INTEGER         NOT NULL,
    body        TEXT            NULL,
    fields      jsonb           NOT NULL,
    written_at  timestamptz(6)  NOT NULL,
    replaced_at timestamptz(6)  NOT NULL,
    created_at  timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_post_revision_no CHECK (revision_no > 0)
);
CREATE UNIQUE INDEX uk_itr_post_revision_post_no ON itr_post_revision (post_id, revision_no);

COMMENT ON TABLE itr_post_revision IS '帖子被替换掉的旧版本（发布 5 分钟内的编辑不记）';
COMMENT ON COLUMN itr_post_revision.post_id IS '所属帖子（itr_post.id）';
COMMENT ON COLUMN itr_post_revision.revision_no IS '版本号，从 1 起按替换顺序递增';
COMMENT ON COLUMN itr_post_revision.body IS '这一版的正文 Markdown 原文，没有为空';
COMMENT ON COLUMN itr_post_revision.fields IS '这一版的结构化字段，JSON，按帖子类型解释';
COMMENT ON COLUMN itr_post_revision.written_at IS '这一版成为当前版本的时间';
COMMENT ON COLUMN itr_post_revision.replaced_at IS '这一版被替换的时间';

CREATE TRIGGER trg_itr_post_revision_audit_times
    BEFORE INSERT OR UPDATE ON itr_post_revision
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();
