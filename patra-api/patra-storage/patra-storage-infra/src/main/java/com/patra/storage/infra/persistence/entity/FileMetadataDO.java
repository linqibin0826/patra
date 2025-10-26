package com.patra.storage.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** Data Object representing the <code>storage_file_metadata</code> table. */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "storage_file_metadata", autoResultMap = true)
public class FileMetadataDO extends BaseDO {

  /** Canonical storage key in bucket/objectKey form. */
  @TableField("storage_key")
  private String storageKey;

  /** Bucket name hosting the object. */
  @TableField("bucket_name")
  private String bucketName;

  /** Object key within the bucket. */
  @TableField("object_key")
  private String objectKey;

  /** Physical file size in bytes. */
  @TableField("file_size")
  private Long fileSize;

  /** MIME type declared by the uploader. */
  @TableField("content_type")
  private String contentType;

  /** MD5 digest recorded at upload time. */
  @TableField("md5_hash")
  private String md5Hash;

  /** Optional SHA-256 digest. */
  @TableField("sha256_hash")
  private String sha256Hash;

  /** Calling service name. */
  @TableField("service_name")
  private String serviceName;

  /** Business context type (e.g., literature_batch). */
  @TableField("business_type")
  private String businessType;

  /** Business identifier supplied by the caller. */
  @TableField("business_id")
  private String businessId;

  /** Correlation metadata captured as JSON. */
  @TableField(value = "correlation_data", typeHandler = JacksonTypeHandler.class)
  private JsonNode correlationData;

  /** Provider type enumeration value. */
  @TableField("provider_type")
  private String providerType;

  /** File lifecycle status. */
  @TableField("file_status")
  private String fileStatus;

  /** Timestamp when the upload finished. */
  @TableField("uploaded_at")
  private Instant uploadedAt;

  /** Optional expiry time. */
  @TableField("expires_at")
  private Instant expiresAt;

  /** Timestamp when the file was soft-deleted. */
  @TableField("deleted_at")
  private Instant deletedAt;
}
