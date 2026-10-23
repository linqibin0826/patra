-- set_updated_at() 触发器函数（每个服务首个脚本独立定义，CREATE OR REPLACE 保证幂等）
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS TRIGGER AS $$
BEGIN
  IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
    NEW.updated_at = now();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

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
    ip_address          bytea           NULL,
    created_at          timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          BIGINT          NULL,
    created_by_name     VARCHAR(100)    NULL,
    updated_at          timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by          BIGINT          NULL,
    updated_by_name     VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_email UNIQUE (email),
    CONSTRAINT uk_idn_user_username UNIQUE (username),
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

COMMENT ON TABLE idn_user IS 'Front-end user account (account type USER); rows are kept forever, DELETED rows are anonymized';
COMMENT ON COLUMN idn_user.id IS 'PK (snowflake), never reused';
COMMENT ON COLUMN idn_user.email IS 'Normalized email: trimmed and lower-cased; null only when DELETED';
COMMENT ON COLUMN idn_user.email_verified_at IS 'When the email was verified (UTC); null while unverified';
COMMENT ON COLUMN idn_user.username IS 'Public handle: 3-30 chars of [a-z0-9_-], unique, never released';
COMMENT ON COLUMN idn_user.username_changed_at IS 'Last username change (UTC); null if never changed';
COMMENT ON COLUMN idn_user.status IS 'ACTIVE, BANNED, PENDING_DELETION or DELETED';
COMMENT ON COLUMN idn_user.banned_at IS 'Set when BANNED, null otherwise';
COMMENT ON COLUMN idn_user.deletion_due_at IS 'Set when PENDING_DELETION: when the deletion is executed (UTC)';
COMMENT ON COLUMN idn_user.deletion_reminded_at IS 'Set when PENDING_DELETION and the pre-deletion reminder has been sent (UTC); cleared on cancel, ban and completion';
COMMENT ON COLUMN idn_user.deleted_at IS 'Set when DELETED: when the deletion was executed (UTC)';
COMMENT ON COLUMN idn_user.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user.updated_by_name IS 'Updater name';

CREATE INDEX idx_idn_user_deletion_due_at ON idn_user (deletion_due_at) WHERE status = 'PENDING_DELETION';

CREATE TRIGGER trg_idn_user_updated_at
    BEFORE UPDATE ON idn_user
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_password_credential_user UNIQUE (user_id),
    CONSTRAINT fk_idn_user_password_credential_user FOREIGN KEY (user_id) REFERENCES idn_user (id)
);

COMMENT ON TABLE idn_user_password_credential IS 'Password credential of a front-end user';
COMMENT ON COLUMN idn_user_password_credential.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_user_password_credential.user_id IS 'Owner user ID';
COMMENT ON COLUMN idn_user_password_credential.password_hash IS 'Argon2id encoded hash (PHC string)';
COMMENT ON COLUMN idn_user_password_credential.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_password_credential.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_password_credential.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_password_credential.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user_password_credential.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_password_credential.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_password_credential.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_password_credential.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_password_credential.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_password_credential_updated_at
    BEFORE UPDATE ON idn_user_password_credential
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- ============================================================
-- idn_user_login_record：前台用户的登录记录，ID 就是会话 ID
-- ============================================================
CREATE TABLE idn_user_login_record
(
    id              BIGINT          NOT NULL,
    user_id         BIGINT          NOT NULL,
    client_type     VARCHAR(16)     NOT NULL,
    device_id       VARCHAR(43)     NULL,
    user_agent      VARCHAR(512)    NULL,
    device_name     VARCHAR(64)     NULL,
    device_class    VARCHAR(16)     NOT NULL,
    client_ip       VARCHAR(45)     NULL,
    ip_region       VARCHAR(64)     NULL,
    expires_at      timestamptz(6)  NOT NULL,
    ended_at        timestamptz(6)  NULL,
    end_reason      VARCHAR(32)     NULL,
    record_remarks  jsonb           NULL,
    version         BIGINT          NOT NULL DEFAULT 0,
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_idn_user_login_record_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
    CONSTRAINT ck_idn_user_login_record_client_type CHECK (client_type IN ('WEB')),
    CONSTRAINT ck_idn_user_login_record_device_class CHECK (device_class IN ('DESKTOP', 'MOBILE', 'TABLET', 'UNKNOWN')),
    CONSTRAINT ck_idn_user_login_record_end_reason CHECK (end_reason IN ('LOGOUT', 'BANNED', 'REPLACED', 'REVOKED',
        'EMAIL_VERIFIED', 'PASSWORD_CHANGED', 'PASSWORD_RESET', 'EMAIL_REVERTED', 'DELETION_REQUESTED',
        'DELETION_COMPLETED')),
    CONSTRAINT ck_idn_user_login_record_ended CHECK ((ended_at IS NULL) = (end_reason IS NULL))
);

-- 新设备判定按账号 + 设备查（前缀也覆盖按账号查）；清理任务按「结束或过期的时刻」删
CREATE INDEX idx_idn_user_login_record_user_device ON idn_user_login_record (user_id, device_id);
CREATE INDEX idx_idn_user_login_record_retire_at ON idn_user_login_record ((COALESCE(ended_at, expires_at)));

COMMENT ON TABLE idn_user_login_record IS 'Login record of a front-end user; id equals the session id; device, user agent and IP are the values at login';
COMMENT ON COLUMN idn_user_login_record.id IS 'PK (snowflake), same as the Redis session id';
COMMENT ON COLUMN idn_user_login_record.user_id IS 'Owner user ID';
COMMENT ON COLUMN idn_user_login_record.client_type IS 'Client type: WEB';
COMMENT ON COLUMN idn_user_login_record.device_id IS 'Device identifier from the portal cookie: 43 base64url characters, optional';
COMMENT ON COLUMN idn_user_login_record.user_agent IS 'User-Agent at login, control characters removed, truncated to 512 code points';
COMMENT ON COLUMN idn_user_login_record.device_name IS 'Browser major version and OS family parsed by Yauaa, e.g. Chrome 141 · macOS; null when unknown';
COMMENT ON COLUMN idn_user_login_record.device_class IS 'DESKTOP, MOBILE, TABLET or UNKNOWN';
COMMENT ON COLUMN idn_user_login_record.client_ip IS 'Client IP at login via the trusted forwarding chain; null when not a valid literal';
COMMENT ON COLUMN idn_user_login_record.ip_region IS 'IP region from ip2region (country / province / city, country only abroad); null for private addresses or unknown';
COMMENT ON COLUMN idn_user_login_record.expires_at IS 'Absolute expiry of the session (UTC)';
COMMENT ON COLUMN idn_user_login_record.ended_at IS 'When the session ended explicitly; null while open or after a silent expiry';
COMMENT ON COLUMN idn_user_login_record.end_reason IS 'LOGOUT, BANNED, REPLACED, REVOKED (kicked from the device list), EMAIL_VERIFIED, PASSWORD_CHANGED, PASSWORD_RESET, EMAIL_REVERTED, DELETION_REQUESTED or DELETION_COMPLETED';
COMMENT ON COLUMN idn_user_login_record.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_login_record.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_login_record.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_login_record.created_at IS 'Creation time (UTC), also the login time';
COMMENT ON COLUMN idn_user_login_record.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_login_record.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_login_record.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_login_record.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_login_record.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_login_record_updated_at
    BEFORE UPDATE ON idn_user_login_record
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_profile_user UNIQUE (user_id),
    CONSTRAINT fk_idn_user_profile_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
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

COMMENT ON TABLE idn_user_profile IS 'Profile of a front-end user, one row per user';
COMMENT ON COLUMN idn_user_profile.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_user_profile.user_id IS 'Owner user ID';
COMMENT ON COLUMN idn_user_profile.display_name IS 'Display name (NFC, trimmed); null means follow the username';
COMMENT ON COLUMN idn_user_profile.bio IS 'Bio (plain text, NFC, at most 3 lines separated by LF)';
COMMENT ON COLUMN idn_user_profile.avatar_object_key IS '512-px avatar object key in bucket patra-identity-avatar; the 128-px key shares the hash; NULL means default avatar';
COMMENT ON COLUMN idn_user_profile.occupation IS 'Self-declared occupation category; null means not set';
COMMENT ON COLUMN idn_user_profile.job_title IS 'Self-declared job title (free text)';
COMMENT ON COLUMN idn_user_profile.organization_ror_id IS 'Bare ROR ID of the affiliation; null for free text or none';
COMMENT ON COLUMN idn_user_profile.organization_name IS 'Affiliation name: catalog snapshot when ror_id is set, otherwise free text';
COMMENT ON COLUMN idn_user_profile.department IS 'Self-declared department (free text)';
COMMENT ON COLUMN idn_user_profile.profile_visibility IS 'Who may see the public page: PUBLIC, SIGNED_IN or SELF';
COMMENT ON COLUMN idn_user_profile.professional_visibility IS 'Who may see the professional block: PUBLIC or SELF';
COMMENT ON COLUMN idn_user_profile.search_indexing IS 'Whether search engines may index the public page';
COMMENT ON COLUMN idn_user_profile.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_profile.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_profile.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_profile.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user_profile.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_profile.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_profile.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_profile.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_profile.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_profile_updated_at
    BEFORE UPDATE ON idn_user_profile
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_profile_link_position UNIQUE (user_id, position),
    CONSTRAINT fk_idn_user_profile_link_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
    CONSTRAINT ck_idn_user_profile_link_position CHECK (position BETWEEN 0 AND 4),
    CONSTRAINT ck_idn_user_profile_link_title CHECK (
        title IS NULL OR (octet_length(title) BETWEEN 1 AND 120 AND title = btrim(title))),
    CONSTRAINT ck_idn_user_profile_link_url CHECK (url ~* '^https?://')
);

CREATE UNIQUE INDEX uk_idn_user_profile_link_url ON idn_user_profile_link (user_id, lower(url));

COMMENT ON TABLE idn_user_profile_link IS 'Links on a user profile, ordered, at most 5';
COMMENT ON COLUMN idn_user_profile_link.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_user_profile_link.user_id IS 'Owner user ID';
COMMENT ON COLUMN idn_user_profile_link.position IS 'Order chosen by the user (0-based)';
COMMENT ON COLUMN idn_user_profile_link.title IS 'Link title; null means show the host';
COMMENT ON COLUMN idn_user_profile_link.url IS 'http(s) URL as entered (trimmed)';
COMMENT ON COLUMN idn_user_profile_link.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_profile_link.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_profile_link.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_profile_link.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user_profile_link.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_profile_link.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_profile_link.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_profile_link.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_profile_link.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_profile_link_updated_at
    BEFORE UPDATE ON idn_user_profile_link
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_research_interest_position UNIQUE (user_id, position),
    CONSTRAINT uk_idn_user_research_interest_mesh_ui UNIQUE (user_id, mesh_ui),
    CONSTRAINT fk_idn_user_research_interest_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
    CONSTRAINT ck_idn_user_research_interest_position CHECK (position BETWEEN 0 AND 9),
    CONSTRAINT ck_idn_user_research_interest_mesh_ui CHECK (mesh_ui ~ '^D[0-9]{6}([0-9]{3})?$')
);

-- 以后按主题找感兴趣的用户（推荐）
CREATE INDEX idx_idn_user_research_interest_mesh_ui ON idn_user_research_interest (mesh_ui);

COMMENT ON TABLE idn_user_research_interest IS 'Research interests of a user (MeSH descriptors), ordered, at most 10';
COMMENT ON COLUMN idn_user_research_interest.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_user_research_interest.user_id IS 'Owner user ID';
COMMENT ON COLUMN idn_user_research_interest.position IS 'Order chosen by the user (0-based)';
COMMENT ON COLUMN idn_user_research_interest.mesh_ui IS 'MeSH descriptor UI';
COMMENT ON COLUMN idn_user_research_interest.mesh_name IS 'English descriptor name snapshot from catalog';
COMMENT ON COLUMN idn_user_research_interest.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_research_interest.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_research_interest.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_research_interest.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user_research_interest.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_research_interest.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_research_interest.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_research_interest.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_research_interest.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_research_interest_updated_at
    BEFORE UPDATE ON idn_user_research_interest
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_idn_user_former_username_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
    CONSTRAINT ck_idn_user_former_username_format CHECK (
        username ~ '^[a-z0-9]([a-z0-9]|[_-][a-z0-9])*$'
        AND char_length(username) BETWEEN 3 AND 30
        AND username !~ '^[0-9]+$'),
    CONSTRAINT ck_idn_user_former_username_period CHECK (reserved_until > replaced_at)
);

CREATE INDEX idx_idn_user_former_username_username
    ON idn_user_former_username (username, reserved_until);
CREATE INDEX idx_idn_user_former_username_user_id ON idn_user_former_username (user_id);

COMMENT ON TABLE idn_user_former_username IS 'Former username left behind by a rename; reserved for its owner until reserved_until';
COMMENT ON COLUMN idn_user_former_username.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_user_former_username.user_id IS 'Former owner user ID';
COMMENT ON COLUMN idn_user_former_username.username IS 'The former username (normalized, lower-case)';
COMMENT ON COLUMN idn_user_former_username.replaced_at IS 'When the rename happened (UTC)';
COMMENT ON COLUMN idn_user_former_username.reserved_until IS 'Reserved for the former owner until this time (UTC), exclusive';
COMMENT ON COLUMN idn_user_former_username.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_former_username.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_former_username.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_former_username.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user_former_username.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_former_username.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_former_username.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_former_username.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_former_username.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_former_username_updated_at
    BEFORE UPDATE ON idn_user_former_username
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

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
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_idn_email_token_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
    CONSTRAINT uk_idn_email_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_idn_email_token_purpose CHECK (
        purpose IN ('EMAIL_VERIFY', 'PASSWORD_RESET', 'EMAIL_CHANGE_CONFIRM', 'EMAIL_CHANGE_REVERT')),
    CONSTRAINT ck_idn_email_token_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT ck_idn_email_token_hash_format CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_idn_email_token_settled_once CHECK (consumed_at IS NULL OR revoked_at IS NULL)
);

CREATE UNIQUE INDEX uk_idn_email_token_active ON idn_email_token (user_id, purpose)
    WHERE consumed_at IS NULL AND revoked_at IS NULL AND purpose <> 'EMAIL_CHANGE_REVERT';
CREATE INDEX idx_idn_email_token_user_id ON idn_email_token (user_id);
CREATE INDEX idx_idn_email_token_expires_at ON idn_email_token (expires_at);

COMMENT ON TABLE idn_email_token IS 'One-time email token (email verify, password reset, email change confirm / revert); only the SHA-256 of the secret is stored';
COMMENT ON COLUMN idn_email_token.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_email_token.user_id IS 'Owner user ID';
COMMENT ON COLUMN idn_email_token.purpose IS 'EMAIL_VERIFY (24h), PASSWORD_RESET (1h), EMAIL_CHANGE_CONFIRM (1h) or EMAIL_CHANGE_REVERT (7d)';
COMMENT ON COLUMN idn_email_token.email IS 'Address the token was sent to (lower-cased); the new address for EMAIL_CHANGE_CONFIRM, the address to restore for EMAIL_CHANGE_REVERT';
COMMENT ON COLUMN idn_email_token.token_hash IS 'SHA-256 of the secret, lower-case hex; the secret itself is never stored';
COMMENT ON COLUMN idn_email_token.expires_at IS 'Expiry (UTC), exclusive';
COMMENT ON COLUMN idn_email_token.consumed_at IS 'When the token was consumed (UTC); null while unused';
COMMENT ON COLUMN idn_email_token.revoked_at IS 'When the token was revoked (UTC); null unless revoked';
COMMENT ON COLUMN idn_email_token.requested_ip IS 'Client IP of the request that issued the token, text form; null when unknown';
COMMENT ON COLUMN idn_email_token.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_email_token.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_email_token.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_email_token.created_at IS 'Creation time (UTC), also the issue time';
COMMENT ON COLUMN idn_email_token.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_email_token.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_email_token.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_email_token.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_email_token.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_email_token_updated_at
    BEFORE UPDATE ON idn_email_token
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
