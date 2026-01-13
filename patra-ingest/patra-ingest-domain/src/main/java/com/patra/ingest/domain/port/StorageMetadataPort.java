package com.patra.ingest.domain.port;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;

/// 存储元数据记录端口(六边形架构 - Domain → Infrastructure)。
///
/// **职责**: 向 patra-object-storage 服务记录上传元数据。此端口与 patra-object-storage 限界上下文(独立微服务)集成,
/// 在系统范围的存储目录中注册文件上传。目录跟踪所有上传的文件及其业务上下文、校验和和关联数据,用于审计和检索。
///
/// **实现**: 基础设施适配器通过 HTTP Interface 客户端实现此端口。
///
/// **端口语义**: 此接口是六边形架构中的 **输出端口(Output Port)**,定义在 Domain
/// 层,由基础设施层(Infrastructure)实现,抽象存储元数据服务的 RPC 细节。
public interface StorageMetadataPort {

  /// 记录已存储文件的上传元数据。
  ///
  /// **业务含义**: 在 patra-object-storage 目录中创建元数据记录。
  ///
  /// @param request 元数据请求,包含文件信息和业务上下文
  /// @return 元数据结果,包含目录记录标识符
  MetadataResult recordUpload(MetadataRequest request);

  /// 元数据请求,用于记录文件上传信息。
  ///
  /// @param storageKey 完整存储标识符(bucket/key 组合)
  /// @param bucketName 对象存储桶名称
  /// @param objectKey 桶内对象键
  /// @param fileSize 文件大小(字节)
  /// @param contentType MIME 内容类型
  /// @param md5 MD5 校验和(十六进制格式)
  /// @param sha256 SHA-256 校验和(十六进制格式)
  /// @param serviceName 发起服务名称
  /// @param businessType 业务类型分类
  /// @param businessId 业务标识符(用于关联)
  /// @param correlation 额外的关联元数据
  /// @param providerType 存储提供商类型(MINIO、S3 等)
  /// @param remarks 可选备注(用于审计)
  @Builder
  record MetadataRequest(
      String storageKey,
      String bucketName,
      String objectKey,
      long fileSize,
      String contentType,
      String md5,
      String sha256,
      String serviceName,
      String businessType,
      String businessId,
      Map<String, Object> correlation,
      String providerType,
      String remarks) {}

  /// 元数据结果,包含目录记录信息。
  ///
  /// @param metadataId patra-object-storage 的目录记录标识符
  /// @param recordedAt 元数据记录时间戳
  @Builder
  record MetadataResult(Long metadataId, Instant recordedAt) {}

  /// 存储元数据记录异常。
  ///
  /// 当与 patra-object-storage 服务通信失败时抛出。此异常定义在端口接口中,
  /// 由基础设施适配器抛出,应用层可捕获并处理。
  class StorageMetadataException extends RuntimeException {

    /// 构造存储元数据异常
    ///
    /// @param message 错误消息
    /// @param cause 原始异常
    public StorageMetadataException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
