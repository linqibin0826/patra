package com.patra.objectstorage.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 文件元数据数据对象。
///
/// 表示 `storage_file_metadata` 表的数据对象(DO),用于MyBatis-Plus的ORM映射。 继承自 {@link
/// BaseDO},包含标准的审计字段(id、version、创建人、更新人、软删除标志等)。
///
/// 数据对象是持久化层的一部分,负责数据库表结构与Java对象之间的映射, 通过转换器(`FileMetadataConverter`)与领域聚合根进行转换。
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "storage_file_metadata", autoResultMap = true)
public class FileMetadataDO extends BaseDO {

  /// 规范存储键,格式为 bucket/objectKey
  @TableField("storage_key")
  private String storageKey;

  /// 托管对象的存储桶名称
  @TableField("bucket_name")
  private String bucketName;

  /// 存储桶内的对象键
  @TableField("object_key")
  private String objectKey;

  /// 物理文件大小(字节)
  @TableField("file_size")
  private Long fileSize;

  /// 上传方声明的MIME类型
  @TableField("content_type")
  private String contentType;

  /// 上传时记录的MD5摘要
  @TableField("md5_hash")
  private String md5Hash;

  /// 可选的SHA-256摘要
  @TableField("sha256_hash")
  private String sha256Hash;

  /// 调用服务名称
  @TableField("service_name")
  private String serviceName;

  /// 业务上下文类型(例如 publication_batch)
  @TableField("business_type")
  private String businessType;

  /// 调用方提供的业务标识符
  @TableField("business_id")
  private String businessId;

  /// 捕获为JSON的关联元数据
  @TableField(value = "correlation_data", typeHandler = JacksonTypeHandler.class)
  private JsonNode correlationData;

  /// 提供商类型枚举值
  @TableField("provider_type")
  private String providerType;

  /// 文件生命周期状态
  @TableField("file_status")
  private String fileStatus;

  /// 上传完成时的时间戳
  @TableField("uploaded_at")
  private Instant uploadedAt;

  /// 可选的过期时间
  @TableField("expires_at")
  private Instant expiresAt;
}
