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
    ip_address      bytea           NULL,
    created_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT          NULL,
    created_by_name VARCHAR(100)    NULL,
    updated_at      timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT          NULL,
    updated_by_name VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idn_user_external_identity_subject UNIQUE (provider, subject),
    CONSTRAINT uk_idn_user_external_identity_user_provider UNIQUE (user_id, provider),
    CONSTRAINT fk_idn_user_external_identity_user FOREIGN KEY (user_id) REFERENCES idn_user (id),
    CONSTRAINT ck_idn_user_external_identity_provider CHECK (provider IN ('GOOGLE', 'GITHUB', 'ORCID'))
);

COMMENT ON TABLE idn_user_external_identity IS 'Third-party account linked to a front-end user; deleted when the deletion is executed';
COMMENT ON COLUMN idn_user_external_identity.id IS 'PK (snowflake)';
COMMENT ON COLUMN idn_user_external_identity.user_id IS 'Owner user ID';
COMMENT ON COLUMN idn_user_external_identity.provider IS 'GOOGLE, GITHUB or ORCID';
COMMENT ON COLUMN idn_user_external_identity.subject IS 'Stable subject at the provider in canonical form (bare iD for ORCID)';
COMMENT ON COLUMN idn_user_external_identity.account_label IS 'Shown on the settings page: email for Google, login for GitHub, iD for ORCID';
COMMENT ON COLUMN idn_user_external_identity.linked_at IS 'When it was linked (UTC)';
COMMENT ON COLUMN idn_user_external_identity.last_login_at IS 'Last sign-in through it (UTC); null if never';
COMMENT ON COLUMN idn_user_external_identity.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN idn_user_external_identity.version IS 'Optimistic lock version number';
COMMENT ON COLUMN idn_user_external_identity.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN idn_user_external_identity.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN idn_user_external_identity.created_by IS 'Creator ID';
COMMENT ON COLUMN idn_user_external_identity.created_by_name IS 'Creator name';
COMMENT ON COLUMN idn_user_external_identity.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN idn_user_external_identity.updated_by IS 'Updater ID';
COMMENT ON COLUMN idn_user_external_identity.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_idn_user_external_identity_updated_at
    BEFORE UPDATE ON idn_user_external_identity
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
