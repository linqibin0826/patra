-- 安全 starter 集成测试专用表：列与 BaseJpaEntity 一一对应
CREATE TABLE security_it_note
(
    id              BIGINT         NOT NULL,
    content         VARCHAR(200)   NULL,
    record_remarks  jsonb          NULL,
    created_at      timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      BIGINT         NULL,
    created_by_name VARCHAR(100)   NULL,
    updated_at      timestamptz(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by      BIGINT         NULL,
    updated_by_name VARCHAR(100)   NULL,
    version         BIGINT         NOT NULL DEFAULT 0,
    ip_address      bytea          NULL,
    PRIMARY KEY (id)
);
