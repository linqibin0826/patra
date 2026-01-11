package com.patra.objectstorage.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 文件元数据 JPA 实体。
///
/// 表示 `storage_file_metadata` 表的 JPA 实体，用于 Spring Data JPA 的 ORM 映射。
/// 继承自 {@link BaseJpaEntity}，包含标准的审计字段（id、version、创建人、更新人、软删除标志等）。
///
/// 实体是持久化层的一部分，负责数据库表结构与 Java 对象之间的映射，
/// 通过转换器（`FileMetadataJpaMapper`）与领域聚合根进行转换。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "storage_file_metadata",
    indexes = {
      @Index(name = "uk_storage_key", columnList = "storage_key", unique = true),
      @Index(name = "idx_uploaded_at", columnList = "uploaded_at"),
      @Index(name = "idx_business", columnList = "service_name, business_type, business_id")
    })
public class FileMetadataEntity extends BaseJpaEntity {

  /// 规范存储键，格式为 bucket/objectKey
  @Column(name = "storage_key", nullable = false, length = 768)
  private String storageKey;

  /// 托管对象的存储桶名称
  @Column(name = "bucket_name", nullable = false, length = 128)
  private String bucketName;

  /// 存储桶内的对象键
  @Column(name = "object_key", nullable = false, length = 512)
  private String objectKey;

  /// 物理文件大小（字节）
  @Column(name = "file_size", nullable = false)
  private Long fileSize;

  /// 上传方声明的 MIME 类型
  @Column(name = "content_type", length = 128)
  private String contentType;

  /// 上传时记录的 MD5 摘要
  @Column(name = "md5_hash", nullable = false, length = 64)
  private String md5Hash;

  /// 可选的 SHA-256 摘要
  @Column(name = "sha256_hash", length = 128)
  private String sha256Hash;

  /// 调用服务名称
  @Column(name = "service_name", nullable = false, length = 64)
  private String serviceName;

  /// 业务上下文类型（例如 publication_batch）
  @Column(name = "business_type", nullable = false, length = 64)
  private String businessType;

  /// 调用方提供的业务标识符
  @Column(name = "business_id", nullable = false, length = 128)
  private String businessId;

  /// 捕获为 JSON 的关联元数据
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "correlation_data", columnDefinition = "JSON")
  private JsonNode correlationData;

  /// 提供商类型枚举值
  @Column(name = "provider_type", nullable = false, length = 32)
  private String providerType;

  /// 文件生命周期状态
  @Column(name = "file_status", nullable = false, length = 32)
  private String fileStatus;

  /// 上传完成时的时间戳
  @Column(name = "uploaded_at", nullable = false)
  private Instant uploadedAt;

  /// 可选的过期时间
  @Column(name = "expires_at")
  private Instant expiresAt;
}
