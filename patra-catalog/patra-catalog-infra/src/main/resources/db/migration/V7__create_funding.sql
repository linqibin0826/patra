-- ============================================================
-- Patra Catalog — Funding PostgreSQL baseline
-- 来源: V1.5.0 (1 表)
-- ============================================================

-- ============================================================
-- 表: cat_publication_funding (文献-资助关联表)
-- ============================================================

CREATE TABLE cat_publication_funding (
    id                      BIGINT          NOT NULL,
    publication_id          BIGINT          NOT NULL,
    organization_id         BIGINT          NULL,
    grant_id                VARCHAR(200)    NULL,
    funder_name_raw         VARCHAR(500)    NULL,
    funder_acronym_raw      VARCHAR(100)    NULL,
    funder_identifier_raw   VARCHAR(200)    NULL,
    country_raw             VARCHAR(100)    NULL,
    funding_order           INTEGER         NOT NULL DEFAULT 1,
    provenance_code         VARCHAR(32)     NOT NULL,

    PRIMARY KEY (id),

    CONSTRAINT uk_pub_org_grant UNIQUE (publication_id, organization_id, grant_id)
);

CREATE INDEX idx_funding_publication  ON cat_publication_funding (publication_id);
CREATE INDEX idx_funding_organization ON cat_publication_funding (organization_id);
CREATE INDEX idx_funding_provenance   ON cat_publication_funding (provenance_code);
