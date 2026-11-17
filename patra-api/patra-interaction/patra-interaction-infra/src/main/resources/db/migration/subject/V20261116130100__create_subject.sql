-- subject 子域：对象登记与发表计数
-- 互动数据只引用 itr_subject.id（各表的 subject_id 列），引用关系由应用保证，不建外键

-- ============================================================
-- itr_subject：对象登记，每个被互动过的对象一行
-- 外部对象（文献、期刊）的身份锚定在 natural_key 上，source_id 只是 catalog 内部 ID 的缓存，每日对账刷新；
-- 本地对象（帖子）由所属子域登记、改状态，source_id 就是它在所属子域里的 ID，不对账。
-- ============================================================
CREATE TABLE itr_subject
(
    id              BIGINT          NOT NULL,
    target_type     SMALLINT        NOT NULL,
    natural_key     VARCHAR(64)     NOT NULL,
    source_id       BIGINT          NULL,
    state           VARCHAR(16)     NOT NULL,
    snapshot        jsonb           NOT NULL,
    snapshot_at     timestamptz(6)  NOT NULL,
    owner_user_id   BIGINT          NULL,
    owner_anonymous BOOLEAN         NULL,
    record_remarks  jsonb           NULL,
    ip_address      INET            NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_subject_target_type CHECK (target_type IN (1, 2, 3)),
    CONSTRAINT ck_itr_subject_state CHECK (state IN ('ACTIVE', 'UNAVAILABLE')),
    CONSTRAINT ck_itr_subject_source_present CHECK (source_id IS NOT NULL OR state = 'UNAVAILABLE'),
    CONSTRAINT ck_itr_subject_publication_key CHECK (target_type <> 1 OR natural_key ~ '^pmid:[1-9][0-9]{0,14}$'),
    CONSTRAINT ck_itr_subject_journal_key CHECK (target_type <> 2 OR natural_key ~ '^nlm:[0-9]{1,19}[A-Z]?$'),
    -- 帖子永远有 source_id（失效时也有），且锚就是 'post:' || source_id。必须显式写 source_id IS NOT NULL：
    -- 否则 source_id 为空时整个表达式是 NULL，CHECK 会放行（失效行又满足 ck_itr_subject_source_present）
    CONSTRAINT ck_itr_subject_post_key CHECK (target_type <> 3 OR (source_id IS NOT NULL AND natural_key ~ '^post:[1-9][0-9]{0,18}$' AND natural_key = 'post:' || source_id)),
    CONSTRAINT ck_itr_subject_owner_pair CHECK ((owner_user_id IS NULL) = (owner_anonymous IS NULL)),
    CONSTRAINT ck_itr_subject_owner_local CHECK ((target_type = 3) = (owner_user_id IS NOT NULL))
);
CREATE UNIQUE INDEX uk_itr_subject_natural_key ON itr_subject (target_type, natural_key);
CREATE UNIQUE INDEX uk_itr_subject_source ON itr_subject (target_type, source_id) WHERE source_id IS NOT NULL;

COMMENT ON TABLE itr_subject IS '对象登记：每个被互动过的对象一行（外部对象从 catalog 解析而来，本地对象由所属子域登记），互动数据只引用它的 id';
COMMENT ON COLUMN itr_subject.target_type IS '对象种类编码：1 文献、2 期刊、3 帖子（本地对象）；编码永不复用';
COMMENT ON COLUMN itr_subject.natural_key IS '身份锚：pmid:…、nlm:…（按字符串存，保留前导零）、post:{帖子 ID}';
COMMENT ON COLUMN itr_subject.source_id IS '外部对象：catalog 内部 ID 的缓存，对象在 catalog 消失时为空；本地对象：所属子域里的 ID';
COMMENT ON COLUMN itr_subject.state IS '状态：ACTIVE，或 UNAVAILABLE（在 catalog 被删除或消失、本地对象被删除；外部对象可恢复）';
COMMENT ON COLUMN itr_subject.snapshot IS '展示快照 {title, journal, publishedDate, abbreviation}：catalog 取不到时兜底展示、失效后展示、排序；本地对象自己填';
COMMENT ON COLUMN itr_subject.snapshot_at IS '快照取自 catalog（或本地对象写入）的时间';
COMMENT ON COLUMN itr_subject.owner_user_id IS '本地对象的所有者用户 ID，外部对象为空；登记后不变';
COMMENT ON COLUMN itr_subject.owner_anonymous IS '所有者是否选择匿名，外部对象为空；登记后不变';

CREATE TRIGGER trg_itr_subject_audit_times
    BEFORE INSERT OR UPDATE ON itr_subject
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- itr_publish_quota：发表守卫的按作者计数
-- 每个作者每种发表一行：当天（北京时间自然日）已发条数与上次发表时刻。编辑不算发表，删除内容不退还额度。
-- 只存用户 ID；注销清理按唯一索引的前导列 user_id 找人。
-- ============================================================
CREATE TABLE itr_publish_quota
(
    id                BIGINT          NOT NULL,
    user_id           BIGINT          NOT NULL,
    kind              VARCHAR(16)     NOT NULL,
    quota_day         DATE            NOT NULL,
    day_count         INTEGER         NOT NULL,
    last_published_at timestamptz(6)  NOT NULL,
    created_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT ck_itr_publish_quota_kind CHECK (kind IN ('COMMENT', 'POST', 'HELPFUL')),
    CONSTRAINT ck_itr_publish_quota_day_count CHECK (day_count > 0)
);
CREATE UNIQUE INDEX uk_itr_publish_quota_user_kind ON itr_publish_quota (user_id, kind);

COMMENT ON TABLE itr_publish_quota IS '发表守卫的计数：每个作者每种发表一行，在作者的 advisory lock 内检查频率并累加';
COMMENT ON COLUMN itr_publish_quota.user_id IS '作者用户 ID';
COMMENT ON COLUMN itr_publish_quota.kind IS '发表种类：COMMENT（每天 100 条、间隔 10 秒）、POST（每天 10 条）、HELPFUL（每天 300 次、不限间隔）';
COMMENT ON COLUMN itr_publish_quota.quota_day IS 'day_count 所属的北京时间自然日';
COMMENT ON COLUMN itr_publish_quota.day_count IS 'quota_day 当天已发条数；删除不退还';
COMMENT ON COLUMN itr_publish_quota.last_published_at IS '作者上次发表这种内容的时间';

CREATE TRIGGER trg_itr_publish_quota_audit_times
    BEFORE INSERT OR UPDATE ON itr_publish_quota
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();
