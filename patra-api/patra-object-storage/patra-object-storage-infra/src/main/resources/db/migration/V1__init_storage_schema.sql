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
-- storage_file_metadata：文件存储元数据
-- ============================================================
CREATE TABLE storage_file_metadata
(
    id                BIGINT          NOT NULL,
    storage_key       VARCHAR(768)    NOT NULL,
    bucket_name       VARCHAR(128)    NOT NULL,
    object_key        VARCHAR(512)    NOT NULL,
    original_filename VARCHAR(255)    NULL,
    file_size         BIGINT          NOT NULL,
    content_type      VARCHAR(128)    NULL,
    md5_hash          VARCHAR(64)     NOT NULL,
    sha256_hash       VARCHAR(128)    NULL,
    service_name      VARCHAR(64)     NOT NULL,
    business_type     VARCHAR(64)     NOT NULL,
    business_id       VARCHAR(128)    NOT NULL,
    correlation_data  jsonb           NULL,
    provider_type     VARCHAR(32)     NOT NULL,
    file_status       VARCHAR(32)     NOT NULL,
    uploaded_at       timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at        timestamptz(6)  NULL,
    record_remarks    jsonb           NULL,
    version           BIGINT          NOT NULL DEFAULT 0,
    ip_address        bytea           NULL,
    created_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by        BIGINT          NULL,
    created_by_name   VARCHAR(100)    NULL,
    updated_at        timestamptz(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by        BIGINT          NULL,
    updated_by_name   VARCHAR(100)    NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_storage_key UNIQUE (storage_key)
);

CREATE INDEX idx_uploaded_at ON storage_file_metadata (uploaded_at);

COMMENT ON TABLE storage_file_metadata IS 'File metadata for object storage';
COMMENT ON COLUMN storage_file_metadata.id IS 'PK';
COMMENT ON COLUMN storage_file_metadata.storage_key IS 'Full storage key: bucket/objectKey';
COMMENT ON COLUMN storage_file_metadata.bucket_name IS 'Bucket name';
COMMENT ON COLUMN storage_file_metadata.object_key IS 'Object key within bucket';
COMMENT ON COLUMN storage_file_metadata.original_filename IS 'Original filename (user upload) or extracted filename (system generated)';
COMMENT ON COLUMN storage_file_metadata.file_size IS 'File size in bytes';
COMMENT ON COLUMN storage_file_metadata.content_type IS 'MIME type declared by uploader';
COMMENT ON COLUMN storage_file_metadata.md5_hash IS 'MD5 checksum';
COMMENT ON COLUMN storage_file_metadata.sha256_hash IS 'SHA-256 checksum';
COMMENT ON COLUMN storage_file_metadata.service_name IS 'Calling service name';
COMMENT ON COLUMN storage_file_metadata.business_type IS 'Business classification';
COMMENT ON COLUMN storage_file_metadata.business_id IS 'Business identifier supplied by caller';
COMMENT ON COLUMN storage_file_metadata.correlation_data IS 'Structured correlation metadata';
COMMENT ON COLUMN storage_file_metadata.provider_type IS 'Storage provider type';
COMMENT ON COLUMN storage_file_metadata.file_status IS 'Lifecycle status';
COMMENT ON COLUMN storage_file_metadata.uploaded_at IS 'Upload completion time (UTC)';
COMMENT ON COLUMN storage_file_metadata.expires_at IS 'Optional expiration time';
COMMENT ON COLUMN storage_file_metadata.record_remarks IS 'Audit remarks log';
COMMENT ON COLUMN storage_file_metadata.version IS 'Optimistic lock version number';
COMMENT ON COLUMN storage_file_metadata.ip_address IS 'Requester IP (IPv4/IPv6)';
COMMENT ON COLUMN storage_file_metadata.created_at IS 'Creation time (UTC)';
COMMENT ON COLUMN storage_file_metadata.created_by IS 'Creator ID';
COMMENT ON COLUMN storage_file_metadata.created_by_name IS 'Creator name';
COMMENT ON COLUMN storage_file_metadata.updated_at IS 'Last update time (UTC)';
COMMENT ON COLUMN storage_file_metadata.updated_by IS 'Updater ID';
COMMENT ON COLUMN storage_file_metadata.updated_by_name IS 'Updater name';

CREATE TRIGGER trg_storage_file_metadata_updated_at
    BEFORE UPDATE ON storage_file_metadata
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
