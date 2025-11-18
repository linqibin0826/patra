CREATE TABLE IF NOT EXISTS `storage_file_metadata`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK',
    `storage_key`      VARCHAR(768)    NOT NULL COMMENT 'Full storage key: bucket/objectKey',
    `bucket_name`      VARCHAR(128)    NOT NULL COMMENT 'Bucket name',
    `object_key`       VARCHAR(512)    NOT NULL COMMENT 'Object key within bucket',
    `original_filename` VARCHAR(255)   NULL COMMENT 'Original filename (user upload) or extracted filename (system generated)',
    `file_size`        BIGINT          NOT NULL COMMENT 'File size in bytes',
    `content_type`     VARCHAR(128)    NULL COMMENT 'MIME type declared by uploader',
    `md5_hash`         VARCHAR(64)     NOT NULL COMMENT 'MD5 checksum',
    `sha256_hash`      VARCHAR(128)    NULL COMMENT 'SHA-256 checksum',
    `service_name`     VARCHAR(64)     NOT NULL COMMENT 'Calling service name',
    `business_type`    VARCHAR(64)     NOT NULL COMMENT 'Business classification',
    `business_id`      VARCHAR(128)    NOT NULL COMMENT 'Business identifier supplied by caller',
    `correlation_data` JSON            NULL COMMENT 'Structured correlation metadata',
    `provider_type`    VARCHAR(32)     NOT NULL COMMENT 'Storage provider type',
    `file_status`      VARCHAR(32)     NOT NULL COMMENT 'Lifecycle status',
    `uploaded_at`      TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Upload completion time (UTC)',
    `expires_at`       TIMESTAMP(6)    NULL COMMENT 'Optional expiration time',
    `deleted_at`       TIMESTAMP(6)    NULL COMMENT 'Soft delete timestamp',
    `record_remarks`   JSON            NULL COMMENT 'Audit remarks log',
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT 'Optimistic lock version number',
    `ip_address`       VARBINARY(16)   NULL COMMENT 'Requester IP (IPv4/IPv6)',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Creation time (UTC)',
    `created_by`       BIGINT UNSIGNED NULL COMMENT 'Creator ID',
    `created_by_name`  VARCHAR(100)    NULL COMMENT 'Creator name',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update time (UTC)',
    `updated_by`       BIGINT UNSIGNED NULL COMMENT 'Updater ID',
    `updated_by_name`  VARCHAR(100)    NULL COMMENT 'Updater name',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0 COMMENT 'Soft delete flag',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_storage_key` (`storage_key`),
    KEY `idx_uploaded_at` (`uploaded_at`),
    KEY `idx_deleted` (`deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'File metadata for object storage';
