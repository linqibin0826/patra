package dev.linqibin.patra.ingest.infra.integration.storage;

import com.patra.starter.objectstorage.ObjectStorageProperties;
import dev.linqibin.commons.error.remote.RemoteCallException;
import dev.linqibin.patra.ingest.domain.port.StorageMetadataPort;
import dev.linqibin.patra.ingest.infra.integration.storage.converter.StorageMetadataRequestConverter;
import dev.linqibin.patra.ingest.infra.integration.storage.converter.StorageMetadataRequestConverter.MappingContext;
import dev.linqibin.patra.objectstorage.api.dto.RecordUploadResponse;
import dev.linqibin.patra.objectstorage.api.dto.UploadRecordRequest;
import dev.linqibin.patra.objectstorage.api.endpoint.StorageEndpoint;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/// 基础设施适配器,实现元数据记录到 patra-object-storage 服务的功能。
///
/// 此适配器为 patra-object-storage 限界上下文提供轻量级 RPC 集成层。它委托给 {@link StorageEndpoint} HTTP Interface
// 客户端进行实际的服务通信。
///
/// 职责:
///
/// - 将领域元数据请求转换为 RPC DTO
/// - 通过 HTTP Interface 客户端调用 patra-object-storage 服务
/// - 将服务响应转换为领域结果
/// - 将远程调用异常转换为领域异常
///
/// 错误处理和重试逻辑委托给应用层编排器。
@Component
@RequiredArgsConstructor
@Slf4j
public class StorageMetadataAdapter implements StorageMetadataPort {

  @Value("${spring.application.name}")
  private String serviceName;

  private final StorageEndpoint storageEndpoint;
  private final ObjectStorageProperties objectStorageProperties;
  private final StorageMetadataRequestConverter requestMapper;

  @Override
  public MetadataResult recordUpload(MetadataRequest request) {
    MappingContext context = buildMappingContext();
    UploadRecordRequest rpcRequest = requestMapper.toRpcRequest(request, context);

    try {
      RecordUploadResponse response = storageEndpoint.recordUpload(rpcRequest);

      return MetadataResult.builder()
          .metadataId(response.metadataId())
          .recordedAt(response.recordedAt())
          .build();

    } catch (RemoteCallException e) {
      // 将远程调用错误转换为端口定义的异常供应用层处理
      throw new StorageMetadataPort.StorageMetadataException(
          "记录元数据到 patra-object-storage 失败: " + e.getMessage(), e);
    }
  }

  /// 构建映射上下文，包含基础设施层特定的默认值。
  private MappingContext buildMappingContext() {
    String providerType = objectStorageProperties.getActiveProvider();
    String normalizedProvider =
        (providerType != null && !providerType.isBlank())
            ? providerType.toUpperCase(Locale.ROOT)
            : "MINIO";
    return new MappingContext(serviceName, normalizedProvider);
  }
}
