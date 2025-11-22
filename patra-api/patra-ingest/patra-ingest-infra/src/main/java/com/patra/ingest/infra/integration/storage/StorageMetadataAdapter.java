package com.patra.ingest.infra.integration.storage;

import com.patra.ingest.domain.port.StorageMetadataPort;
import com.patra.objectstorage.api.client.StorageClient;
import com.patra.objectstorage.api.dto.RecordUploadResponse;
import com.patra.objectstorage.api.dto.UploadRecordRequest;
import com.patra.starter.objectstorage.ObjectStorageProperties;
import feign.FeignException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/// 基础设施适配器,实现元数据记录到 patra-object-storage 服务的功能。
///
/// 此适配器为 patra-object-storage 限界上下文提供轻量级 RPC 集成层。它委托给 {@link StorageClient} Feign 客户端进行实际的服务通信。
///
/// 职责:
///
/// - 将领域元数据请求转换为 RPC DTO
///   - 通过 Feign 客户端调用 patra-object-storage 服务
///   - 将服务响应转换为领域结果
///   - 将 Feign 异常转换为领域异常
///
/// 错误处理和重试逻辑委托给应用层编排器。
@Component
@RequiredArgsConstructor
@Slf4j
public class StorageMetadataAdapter implements StorageMetadataPort {

  @Value("${spring.application.name}")
  private String serviceName;

  private final StorageClient storageClient;
  private final ObjectStorageProperties objectStorageProperties;

  @Override
  public MetadataResult recordUpload(MetadataRequest request) {
    UploadRecordRequest rpcRequest = toRpcRequest(request);

    try {
      RecordUploadResponse response = storageClient.recordUpload(rpcRequest);

      return MetadataResult.builder()
          .metadataId(response.metadataId())
          .recordedAt(response.recordedAt())
          .build();

    } catch (FeignException e) {
      // 将 Feign 错误转换为领域异常供应用层处理
      throw new StorageMetadataException("记录元数据到 patra-object-storage 失败: " + e.getMessage(), e);
    }
  }

  private UploadRecordRequest toRpcRequest(MetadataRequest request) {
    // 填充基础设施特定字段
    String actualServiceName =
        StringUtils.hasText(request.serviceName()) ? request.serviceName() : serviceName;

    String providerType = objectStorageProperties.getActiveProvider();
    String normalizedProvider =
        StringUtils.hasText(providerType) ? providerType.toUpperCase(Locale.ROOT) : "MINIO";
    String actualProviderType =
        StringUtils.hasText(request.providerType()) ? request.providerType() : normalizedProvider;

    return new UploadRecordRequest(
        request.bucketName(),
        request.objectKey(),
        request.fileSize(),
        request.contentType(),
        request.md5(),
        request.sha256(),
        actualServiceName,
        request.businessType(),
        request.businessId(),
        request.correlation(),
        actualProviderType,
        null, // expiresAt - 出版物存储不使用
        request.remarks());
  }

  /// 元数据记录异常,指示 patra-object-storage 服务通信失败。
  public static class StorageMetadataException extends RuntimeException {
    public StorageMetadataException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
