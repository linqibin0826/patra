-- account 子域：用户、密码凭据、资料、链接、研究兴趣、曾用用户名、邮件令牌、第三方账号
-- 引用用户的列都是 user_id，引用关系由应用保证，不建外键

-- ============================================================
-- idn_user：前台用户
-- ============================================================
CREATE TABLE idn_user
(
    id                  BIGINT          NOT NULL,
    email               VARCHAR(254)    NULL,
    email_verified_at   timestamptz(6)  NULL,
    username            VARCHAR(30)     NOT NULL,
    username_changed_at timestamptz(6)  NULL,
    status              VARCHAR(16)     NOT NULL,
    banned_at           timestamptz(6)  NULL,
    deletion_due_at     timestamptz(6)  NULL,
    deletion_reminded_at timestamptz(6) NULL,
    deleted_at          timestamptz(6)  NULL,
    record_remarks      jsonb           NULL,
    version             BIGINT          NOT NULL DEFAULT 0,
    ip_address          INET            NULL,
    created_at          timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT          NULL,
    updated_at          timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_idn_user_email_lowercase CHECK (email IS NULL OR email = lower(email)),
    CONSTRAINT ck_idn_user_email_deleted CHECK ((status = 'DELETED') = (email IS NULL)),
    CONSTRAINT ck_idn_user_email_verified_at CHECK (email IS NOT NULL OR email_verified_at IS NULL),
    CONSTRAINT ck_idn_user_username_format CHECK (
        username ~ '^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$'
        AND char_length(username) BETWEEN 3 AND 30
        AND username !~ '^[0-9]+$'),
    CONSTRAINT ck_idn_user_status CHECK (status IN ('ACTIVE', 'BANNED', 'PENDING_DELETION', 'DELETED')),
    CONSTRAINT ck_idn_user_banned_at CHECK ((status = 'BANNED') = (banned_at IS NOT NULL)),
    CONSTRAINT ck_idn_user_deletion_due_at CHECK ((status = 'PENDING_DELETION') = (deletion_due_at IS NOT NULL)),
    CONSTRAINT ck_idn_user_deletion_reminded_at CHECK (deletion_reminded_at IS NULL OR status = 'PENDING_DELETION'),
    CONSTRAINT ck_idn_user_deleted_at CHECK ((status = 'DELETED') = (deleted_at IS NOT NULL))
);
CREATE UNIQUE INDEX uk_idn_user_email ON idn_user (email);
CREATE UNIQUE INDEX uk_idn_user_username ON idn_user (username);

COMMENT ON TABLE idn_user IS '前台用户账号；行永久保留，注销后匿名化';
COMMENT ON COLUMN idn_user.email IS '规范化后的邮箱（去空白、小写），只有已注销时为空';
COMMENT ON COLUMN idn_user.email_verified_at IS '邮箱验证时间，未验证为空';
COMMENT ON COLUMN idn_user.username IS '公开的用户名：3～30 个 [a-z0-9_-] 字符，全表唯一，改名后也不释放';
COMMENT ON COLUMN idn_user.username_changed_at IS '最近一次改名时间，没改过为空';
COMMENT ON COLUMN idn_user.status IS '状态：ACTIVE、BANNED、PENDING_DELETION、DELETED';
COMMENT ON COLUMN idn_user.banned_at IS '封禁时间，只在 BANNED 时有值';
COMMENT ON COLUMN idn_user.deletion_due_at IS '注销到期时间，只在 PENDING_DELETION 时有值';
COMMENT ON COLUMN idn_user.deletion_reminded_at IS '到期前提醒的发送时间，只在 PENDING_DELETION 时可能有值；撤销、封禁、注销完成时清空';
COMMENT ON COLUMN idn_user.deleted_at IS '注销完成时间，只在 DELETED 时有值；是账号状态的一部分，不是软删除标记';

CREATE INDEX idx_idn_user_deletion_due_at ON idn_user (deletion_due_at) WHERE status = 'PENDING_DELETION';

CREATE TRIGGER trg_idn_user_audit_times
    BEFORE INSERT OR UPDATE ON idn_user
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- idn_user_password_credential：前台用户的密码凭据
-- ============================================================
CREATE TABLE idn_user_password_credential
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    password_hash   VARCHAR(255)    NOT NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      INET            NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    PRIMARY KEY (id)
);
CREATE UNIQUE INDEX uk_idn_user_password_credential_user ON idn_user_password_credential (user_id);

COMMENT ON TABLE idn_user_password_credential IS '前台用户的密码凭据，每个用户一行';
COMMENT ON COLUMN idn_user_password_credential.user_id IS '所属用户';
COMMENT ON COLUMN idn_user_password_credential.password_hash IS 'Argon2id 哈希（PHC 字符串）';

CREATE TRIGGER trg_idn_user_password_credential_audit_times
    BEFORE INSERT OR UPDATE ON idn_user_password_credential
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- idn_user_profile：前台用户的资料与隐私设置，与用户一对一。字节上限是字素上限 × 4（字素由代码守）；
-- 机构名两种来源：有 ROR ID 时是 catalog 的快照（不受手填上限约束），没有时是手填的自由文本
-- ============================================================
CREATE TABLE idn_user_profile
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    display_name            VARCHAR(120)    NULL,
    bio                     VARCHAR(800)    NULL,
    occupation              VARCHAR(16)     NULL,
    job_title               VARCHAR(200)    NULL,
    organization_ror_id     VARCHAR(9)      NULL,
    organization_name       VARCHAR(800)    NULL,
    department              VARCHAR(400)    NULL,
    profile_visibility      VARCHAR(16)     NOT NULL DEFAULT 'PUBLIC',
    professional_visibility VARCHAR(16)     NOT NULL DEFAULT 'PUBLIC',
    search_indexing         BOOLEAN         NOT NULL DEFAULT false,
    avatar_object_key       VARCHAR(64)     NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      INET            NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_idn_user_profile_display_name CHECK (
        display_name IS NULL
        OR (octet_length(display_name) BETWEEN 2 AND 120 AND display_name = btrim(display_name))),
    CONSTRAINT ck_idn_user_profile_bio CHECK (
        bio IS NULL
        OR (octet_length(bio) BETWEEN 1 AND 800 AND bio = btrim(bio) AND position(chr(13) IN bio) = 0)),
    CONSTRAINT ck_idn_user_profile_occupation CHECK (
        occupation IS NULL
        OR occupation IN ('PHYSICIAN', 'NURSE', 'PHARMACIST', 'TECHNICIAN', 'RESEARCHER',
                          'STUDENT', 'INDUSTRY', 'OTHER')),
    CONSTRAINT ck_idn_user_profile_job_title CHECK (
        job_title IS NULL
        OR (octet_length(job_title) BETWEEN 1 AND 200 AND job_title = btrim(job_title))),
    CONSTRAINT ck_idn_user_profile_ror_id CHECK (
        organization_ror_id IS NULL OR organization_ror_id ~ '^0[a-z0-9]{8}$'),
    CONSTRAINT ck_idn_user_profile_organization CHECK (
        organization_ror_id IS NULL OR organization_name IS NOT NULL),
    CONSTRAINT ck_idn_user_profile_organization_name CHECK (
        organization_name IS NULL
        OR (organization_name <> '' AND organization_name = btrim(organization_name)
            AND (organization_ror_id IS NOT NULL OR octet_length(organization_name) <= 800))),
    CONSTRAINT ck_idn_user_profile_department CHECK (
        department IS NULL
        OR (octet_length(department) BETWEEN 1 AND 400 AND department = btrim(department))),
    CONSTRAINT ck_idn_user_profile_visibility CHECK (
        profile_visibility IN ('PUBLIC', 'SIGNED_IN', 'SELF')),
    CONSTRAINT ck_idn_user_profile_professional_visibility CHECK (
        professional_visibility IN ('PUBLIC', 'SELF')),
    CONSTRAINT ck_idn_user_profile_avatar_object_key CHECK (
        avatar_object_key IS NULL
        OR avatar_object_key ~ ('^avatar/' || user_id::text || '/[0-9a-f]{16}-512\.webp$'))
);
CREATE UNIQUE INDEX uk_idn_user_profile_user ON idn_user_profile (user_id);

COMMENT ON TABLE idn_user_profile IS '前台用户的资料，每个用户一行';
COMMENT ON COLUMN idn_user_profile.user_id IS '所属用户';
COMMENT ON COLUMN idn_user_profile.display_name IS '昵称（NFC、去首尾空白）；为空表示跟随用户名';
COMMENT ON COLUMN idn_user_profile.bio IS '简介，纯文本、NFC，最多 3 行，换行符 LF';
COMMENT ON COLUMN idn_user_profile.avatar_object_key IS '512 像素头像在 patra-identity-avatar 桶里的对象键，128 像素的共用同一哈希；为空表示默认头像';
COMMENT ON COLUMN idn_user_profile.occupation IS '自填的职业类别；为空表示没填';
COMMENT ON COLUMN idn_user_profile.job_title IS '自填的职称或职位，自由文本';
COMMENT ON COLUMN idn_user_profile.organization_ror_id IS '所在机构的 ROR ID（不带前缀）；自由填写或没填时为空';
COMMENT ON COLUMN idn_user_profile.organization_name IS '机构名：有 ROR ID 时是 catalog 的快照，否则是自由文本';
COMMENT ON COLUMN idn_user_profile.department IS '自填的科室或部门，自由文本';
COMMENT ON COLUMN idn_user_profile.profile_visibility IS '谁能看公开主页：PUBLIC、SIGNED_IN、SELF';
COMMENT ON COLUMN idn_user_profile.professional_visibility IS '谁能看职业信息：PUBLIC、SELF';
COMMENT ON COLUMN idn_user_profile.search_indexing IS '是否允许搜索引擎收录公开主页';

CREATE TRIGGER trg_idn_user_profile_audit_times
    BEFORE INSERT OR UPDATE ON idn_user_profile
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- idn_user_profile_link：资料里的链接，按用户排好的顺序，最多 5 条（position 0 ~ 4），整组替换
-- ============================================================
CREATE TABLE idn_user_profile_link
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    position        SMALLINT        NOT NULL,
    title           VARCHAR(120)    NULL,
    url             VARCHAR(300)    NOT NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      INET            NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_idn_user_profile_link_position CHECK (position BETWEEN 0 AND 4),
    CONSTRAINT ck_idn_user_profile_link_title CHECK (
        title IS NULL OR (octet_length(title) BETWEEN 1 AND 120 AND title = btrim(title))),
    CONSTRAINT ck_idn_user_profile_link_url CHECK (url ~* '^https?://')
);
CREATE UNIQUE INDEX uk_idn_user_profile_link_position ON idn_user_profile_link (user_id, position);

CREATE UNIQUE INDEX uk_idn_user_profile_link_url ON idn_user_profile_link (user_id, lower(url));

COMMENT ON TABLE idn_user_profile_link IS '资料里的链接，有顺序，最多 5 条';
COMMENT ON COLUMN idn_user_profile_link.user_id IS '所属用户';
COMMENT ON COLUMN idn_user_profile_link.position IS '用户排的顺序，从 0 开始';
COMMENT ON COLUMN idn_user_profile_link.title IS '链接标题；为空时显示域名';
COMMENT ON COLUMN idn_user_profile_link.url IS '用户填的 http(s) 地址（去首尾空白）';

CREATE TRIGGER trg_idn_user_profile_link_audit_times
    BEFORE INSERT OR UPDATE ON idn_user_profile_link
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- idn_user_research_interest：关注领域，MeSH 主题词 UI + 英文名快照，按用户排好的顺序，最多 10 个（position 0 ~ 9）
-- ============================================================
CREATE TABLE idn_user_research_interest
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    position        SMALLINT        NOT NULL,
    mesh_ui         VARCHAR(10)     NOT NULL,
    mesh_name       VARCHAR(255)    NOT NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      INET            NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_idn_user_research_interest_position CHECK (position BETWEEN 0 AND 9),
    CONSTRAINT ck_idn_user_research_interest_mesh_ui CHECK (mesh_ui ~ '^D[0-9]{6}([0-9]{3})?$')
);
CREATE UNIQUE INDEX uk_idn_user_research_interest_position ON idn_user_research_interest (user_id, position);
CREATE UNIQUE INDEX uk_idn_user_research_interest_mesh_ui ON idn_user_research_interest (user_id, mesh_ui);

-- 以后按主题找感兴趣的用户（推荐）
CREATE INDEX idx_idn_user_research_interest_mesh_ui ON idn_user_research_interest (mesh_ui);

COMMENT ON TABLE idn_user_research_interest IS '用户的研究兴趣（MeSH 主题词），有顺序，最多 10 个';
COMMENT ON COLUMN idn_user_research_interest.user_id IS '所属用户';
COMMENT ON COLUMN idn_user_research_interest.position IS '用户排的顺序，从 0 开始';
COMMENT ON COLUMN idn_user_research_interest.mesh_ui IS 'MeSH 主题词编号';
COMMENT ON COLUMN idn_user_research_interest.mesh_name IS '主题词英文名，取自 catalog 的快照';

CREATE TRIGGER trg_idn_user_research_interest_audit_times
    BEFORE INSERT OR UPDATE ON idn_user_research_interest
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- idn_user_former_username：改名留下的旧名。reserved_until 之前只有原主能改回、别人不能注册或改成它，
-- /users/旧名 跳到新名；到期后行留作审计。不加唯一约束：同一个旧名可以在不同时期属于不同的人
-- ============================================================
CREATE TABLE idn_user_former_username
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    username        VARCHAR(30)     NOT NULL,
    replaced_at     timestamptz(6)  NOT NULL,
    reserved_until  timestamptz(6)  NOT NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      INET            NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_idn_user_former_username_format CHECK (
        username ~ '^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$'
        AND char_length(username) BETWEEN 3 AND 30
        AND username !~ '^[0-9]+$'),
    CONSTRAINT ck_idn_user_former_username_period CHECK (reserved_until > replaced_at)
);

CREATE INDEX idx_idn_user_former_username_username
    ON idn_user_former_username (username, reserved_until);
CREATE INDEX idx_idn_user_former_username_user_id ON idn_user_former_username (user_id);

COMMENT ON TABLE idn_user_former_username IS '改名留下的曾用用户名；保留到 reserved_until，期间只有原主人能改回';
COMMENT ON COLUMN idn_user_former_username.user_id IS '原主人';
COMMENT ON COLUMN idn_user_former_username.username IS '曾用用户名（规范化、小写）';
COMMENT ON COLUMN idn_user_former_username.replaced_at IS '改名时间';
COMMENT ON COLUMN idn_user_former_username.reserved_until IS '保留到这个时间（不含）';

CREATE TRIGGER trg_idn_user_former_username_audit_times
    BEFORE INSERT OR UPDATE ON idn_user_former_username
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- idn_email_token：一次性邮件令牌（验证邮箱、找回密码、改邮箱确认与撤销共用）。库里只存原文的 SHA-256。
-- 同一用户同一用途只留一个有效令牌由部分唯一索引兜底；改邮箱的撤销令牌（EMAIL_CHANGE_REVERT）豁免：
-- 连续改两次邮箱时，两个旧邮箱里的「不是我」链接要各自活满 7 天
-- ============================================================
CREATE TABLE idn_email_token
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    purpose         VARCHAR(32)     NOT NULL,
    email           VARCHAR(254)    NOT NULL,
    token_hash      CHAR(64)        NOT NULL,
    expires_at      timestamptz(6)  NOT NULL,
    consumed_at     timestamptz(6)  NULL,
    revoked_at      timestamptz(6)  NULL,
    requested_ip    VARCHAR(45)     NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      INET            NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_idn_email_token_purpose CHECK (
        purpose IN ('EMAIL_VERIFY', 'PASSWORD_RESET', 'EMAIL_CHANGE_CONFIRM', 'EMAIL_CHANGE_REVERT')),
    CONSTRAINT ck_idn_email_token_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_idn_email_token_hash_format CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_idn_email_token_settled_once CHECK (consumed_at IS NULL OR revoked_at IS NULL)
);

CREATE UNIQUE INDEX uk_idn_email_token_hash ON idn_email_token (token_hash);
CREATE UNIQUE INDEX uk_idn_email_token_active ON idn_email_token (user_id, purpose)
    WHERE consumed_at IS NULL AND revoked_at IS NULL AND purpose <> 'EMAIL_CHANGE_REVERT';
CREATE INDEX idx_idn_email_token_user_id ON idn_email_token (user_id);
CREATE INDEX idx_idn_email_token_expires_at ON idn_email_token (expires_at);

COMMENT ON TABLE idn_email_token IS '一次性邮件令牌（验证邮箱、重置密码、确认或撤销换邮箱），只存密钥的 SHA-256';
COMMENT ON COLUMN idn_email_token.user_id IS '所属用户';
COMMENT ON COLUMN idn_email_token.purpose IS '用途：EMAIL_VERIFY（24 小时）、PASSWORD_RESET（1 小时）、EMAIL_CHANGE_CONFIRM（1 小时）、EMAIL_CHANGE_REVERT（7 天）';
COMMENT ON COLUMN idn_email_token.email IS '令牌发往的地址（小写）；确认换邮箱时是新地址，撤销换邮箱时是要恢复的地址';
COMMENT ON COLUMN idn_email_token.token_hash IS '密钥的 SHA-256，小写十六进制；密钥本身不存';
COMMENT ON COLUMN idn_email_token.expires_at IS '过期时间（不含）';
COMMENT ON COLUMN idn_email_token.consumed_at IS '使用时间，没用过为空';
COMMENT ON COLUMN idn_email_token.revoked_at IS '作废时间，没作废为空';
COMMENT ON COLUMN idn_email_token.requested_ip IS '签发令牌那次请求的客户端 IP，文本形式；未知为空';

CREATE TRIGGER trg_idn_email_token_audit_times
    BEFORE INSERT OR UPDATE ON idn_email_token
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- Patra Identity：外部身份（第三方登录，PAP-182）
-- 设计: docs/patra/specs/2026-11-04-identity-social-login-design.md 第 9 节
-- 不存令牌、资料快照和对方给的邮箱（v0.19 决策 C）；永不按邮箱合并账号（决策 D）
-- ============================================================
CREATE TABLE idn_user_external_identity
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    provider        VARCHAR(16)     NOT NULL,
    subject         VARCHAR(255)    NOT NULL,
    account_label   VARCHAR(255)    NOT NULL,
    linked_at       timestamptz(6)  NOT NULL,
    last_login_at   timestamptz(6)  NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      INET            NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_idn_user_external_identity_provider CHECK (provider IN ('GOOGLE', 'GITHUB', 'ORCID'))
);
CREATE UNIQUE INDEX uk_idn_user_external_identity_subject ON idn_user_external_identity (provider, subject);
CREATE UNIQUE INDEX uk_idn_user_external_identity_user_provider ON idn_user_external_identity (user_id, provider);

COMMENT ON TABLE idn_user_external_identity IS '绑定到前台用户的第三方账号；注销执行时删除';
COMMENT ON COLUMN idn_user_external_identity.user_id IS '所属用户';
COMMENT ON COLUMN idn_user_external_identity.provider IS '第三方：GOOGLE、GITHUB、ORCID';
COMMENT ON COLUMN idn_user_external_identity.subject IS '第三方那边稳定的账号标识，规范形式（ORCID 是不带前缀的 iD）';
COMMENT ON COLUMN idn_user_external_identity.account_label IS '设置页上显示的账号：Google 是邮箱、GitHub 是登录名、ORCID 是 iD';
COMMENT ON COLUMN idn_user_external_identity.linked_at IS '绑定时间';
COMMENT ON COLUMN idn_user_external_identity.last_login_at IS '最近一次用它登录的时间，没用过为空';

CREATE TRIGGER trg_idn_user_external_identity_audit_times
    BEFORE INSERT OR UPDATE ON idn_user_external_identity
    FOR EACH ROW EXECUTE FUNCTION set_audit_times();

-- ============================================================
-- Patra Identity — V2：@ 联想的用户名与昵称前缀索引（PAP-118）
-- 设计: docs/patra/specs/2026-10-27-identity-mention-support-design.md 第 4.5 节
-- ============================================================

-- 库按 postgres:17 镜像默认建库，排序规则是 en_US.utf8：连接符、下划线在第一层被忽略，前缀范围在这个
-- 顺序下不连续，按它排序的索引用不上。两个索引都按 "C"（UTF-8 字节序，即码点序）排序。
-- 查询必须写成 列 COLLATE "C" >= 前缀 AND 列 COLLATE "C" < 前缀末码点加一（UserDao），不能用 LIKE：
-- _ 是 LIKE 的通配符，参数化的 LIKE 在通用计划下也推不出索引范围。

-- ============================================================
-- 用户名：只收 ACTIVE。联想只要正常账号；查询里 status 写字面量，通用计划下也能匹配这个部分索引。
-- ============================================================
CREATE INDEX idx_idn_user_username_prefix
    ON idn_user (username COLLATE "C")
    WHERE status = 'ACTIVE';

-- ============================================================
-- 昵称：按小写昵称，比较不分大小写；lower() 按列的排序规则做大小写映射。只收已设昵称的行：没设昵称的人
-- 昵称跟随用户名，已由上一个索引覆盖。做不到只收 ACTIVE：状态在 idn_user，部分索引不能引用别的表；
-- ACTIVE 由查询联用户表、按主键回表时过滤。查询里 display_name IS NOT NULL 写字面量，匹配部分索引。
-- ============================================================
CREATE INDEX idx_idn_user_profile_display_name_prefix
    ON idn_user_profile ((lower(display_name)) COLLATE "C")
    WHERE display_name IS NOT NULL;
